<template>
  <div class="attachment">
    <div v-if="canUpload" class="att-actions">
      <el-button size="small" type="primary" :loading="uploading" @click="triggerUpload">
        上传附件
      </el-button>
      <input
        ref="fileInputRef"
        type="file"
        :multiple="multi"
        style="display: none"
        @change="handleFileChange"
      />
    </div>

    <el-table :data="list" v-loading="loading" size="small" empty-text="暂无附件">
      <el-table-column prop="fieldName" label="文件名" min-width="200" show-overflow-tooltip />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ row.fieldFileType ? '.' + row.fieldFileType : '-' }}</template>
      </el-table-column>
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.fieldFileSize) }}</template>
      </el-table-column>
      <el-table-column label="上传时间" width="170">
        <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handlePreview(row)">预览</el-button>
          <el-button link type="primary" @click="handleDownload(row)">下载</el-button>
          <el-button v-if="canDelete" link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteAttachment,
  downloadAttachment,
  queryComponentAttachmentList,
  uploadAttachment,
  viewAttachment
} from '@/api/attachment'
import { downloadBlob } from '@/utils/download'

/**
 * 附件组件：提供附件的上传、列表展示、预览、下载与删除
 * 上传/删除按钮显隐由页面自行控制（canUpload / canDelete，为文档参数表之外的补充参数）；
 * 预览与下载对所有人显示；删除执行前二次确认，最终权限由后端接口二次校验。
 */
const props = defineProps({
  /** 传递给接口的 fieldModelId 字段值（上传前需已存在文档 id） */
  fieldModelId: { type: String, default: '' },
  /** 传递给接口的 fieldModelName 字段值，必填 */
  fieldModelName: { type: String, required: true },
  /** 传递给接口的 fieldKey 字段值 */
  fieldKey: { type: String, default: '' },
  /** 是否可上传多个附件，默认 false */
  multi: { type: Boolean, default: false },
  /** 可上传的文件后缀类型，默认为空（不限制），如 'jpg,png' 或 '.jpg,.png' */
  fileType: { type: String, default: '' },
  /** 上传结束后的回调函数 callback(attachment) */
  uploadCallback: { type: Function, default: null },
  /** 确认上传文件到触发后端上传接口中间的校验函数，函数返回值必须为 true 才能继续上传 */
  uploadValidate: { type: Function, default: null },
  /** 删除结束后的回调函数 callback(attachment) */
  deleteCallback: { type: Function, default: null },
  /** 是否显示上传入口，由页面自行控制（补充参数） */
  canUpload: { type: Boolean, default: true },
  /** 是否显示删除按钮，由页面自行控制（补充参数） */
  canDelete: { type: Boolean, default: true }
})

const list = ref([])
const loading = ref(false)
const uploading = ref(false)
const fileInputRef = ref(null)

onMounted(loadList)

/** 加载当前模型下的附件列表（公共组件查询接口，登录即可） */
async function loadList() {
  loading.value = true
  try {
    const res = await queryComponentAttachmentList({
      body: {
        fieldModelName: props.fieldModelName,
        fieldModelId: props.fieldModelId,
        fieldKey: props.fieldKey
      },
      pages: { pageNo: 1, pageSize: 100, needPage: true }
    })
    list.value = res.data || []
  } catch (error) {
    console.warn('[Attachment] 附件列表加载失败:', error.message)
    list.value = []
  } finally {
    loading.value = false
  }
}

function triggerUpload() {
  if (!props.fieldModelId) {
    ElMessage.warning('请先保存单据后再上传附件')
    return
  }
  fileInputRef.value && fileInputRef.value.click()
}

async function handleFileChange(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) {
    return
  }
  const file = files[0]
  if (!checkFileType(file)) {
    ElMessage.warning(`不允许上传该文件类型，支持：${props.fileType}`)
    return
  }
  // 单附件模式：上传新文件即替换，先删除已有附件
  if (!props.multi && list.value.length) {
    try {
      await Promise.all(list.value.map((item) => deleteAttachment(item.fieldId)))
    } catch (error) {
      ElMessage.error(error.message || '旧附件删除失败')
      return
    }
  }
  // 上传前的自定义校验：函数返回值必须为 true 才能继续上传
  if (typeof props.uploadValidate === 'function') {
    const ok = await props.uploadValidate(file)
    if (ok !== true) {
      return
    }
  }
  await doUpload(file)
}

/** 校验文件后缀（fileType 为空时不限制） */
function checkFileType(file) {
  if (!props.fileType) {
    return true
  }
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  const allowed = props.fileType
    .split(',')
    .map((s) => s.trim().replace(/^\./, '').toLowerCase())
  return allowed.includes(ext)
}

async function doUpload(file) {
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('modelName', props.fieldModelName)
    formData.append('modelId', props.fieldModelId)
    formData.append('key', props.fieldKey || '')
    const res = await uploadAttachment(formData)
    ElMessage.success('上传成功')
    await loadList()
    if (typeof props.uploadCallback === 'function') {
      props.uploadCallback(res.data)
    }
  } catch (error) {
    ElMessage.error(error.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

/** 预览：以内联方式读取文件流并在新窗口打开 */
async function handlePreview(row) {
  try {
    const response = await viewAttachment(row.fieldId)
    const url = URL.createObjectURL(response.data)
    window.open(url, '_blank')
  } catch (error) {
    ElMessage.error(error.message || '预览失败')
  }
}

/** 下载：以附件原名保存文件（文件名解析与临时 <a> 保存统一走公共下载工具） */
async function handleDownload(row) {
  try {
    const response = await downloadAttachment(row.fieldId)
    downloadBlob(response, row.fieldName || 'download')
  } catch (error) {
    ElMessage.error(error.message || '下载失败')
  }
}

/** 删除：二次确认后调用删除接口（后端校验上传者本人或附件管理员） */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除附件「${row.fieldName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteAttachment(row.fieldId)
    ElMessage.success('删除成功')
    await loadList()
    if (typeof props.deleteCallback === 'function') {
      props.deleteCallback(row)
    }
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

function formatSize(size) {
  if (size === null || size === undefined || size === '') {
    return '-'
  }
  if (size < 1024) {
    return size + ' B'
  }
  if (size < 1024 * 1024) {
    return (size / 1024).toFixed(1) + ' KB'
  }
  if (size < 1024 * 1024 * 1024) {
    return (size / 1024 / 1024).toFixed(1) + ' MB'
  }
  return (size / 1024 / 1024 / 1024).toFixed(1) + ' GB'
}

function formatTime(value) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
</script>

<style scoped>
.attachment {
  width: 100%;
}

.att-actions {
  margin-bottom: 8px;
}
</style>

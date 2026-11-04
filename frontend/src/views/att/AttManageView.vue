<template>
  <div class="att-manage">
    <!-- 筛选条件区：四分栅栏排列（文件名 | 模块名 | 关键字 | 是否有效） -->
    <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="文件名">
            <el-input v-model="filters.fieldName" placeholder="请输入文件名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="模块名">
            <el-input v-model="filters.fieldModelName" placeholder="请输入模块名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="关键字">
            <el-input v-model="filters.fieldKey" placeholder="请输入关键字" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="是否有效">
            <el-select v-model="filters.fieldEnable" style="width: 100%">
              <el-option v-for="opt in ENABLE_OPTIONS" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 工具区：左侧排序 + 右侧按钮（仅查询，删除/下载/日志在行内操作） -->
    <div class="list-toolbar">
      <div class="sort-area">
        <span
          v-for="opt in SORT_OPTIONS"
          :key="opt.value"
          class="sort-item"
          :class="{ active: sortField === opt.value }"
          @click="toggleSort(opt.value)"
        >
          {{ opt.label }}
          <i v-if="sortField === opt.value" class="sort-arrow">{{ sortOrder === 'desc' ? '↓' : '↑' }}</i>
        </span>
      </div>
      <div class="btn-area">
        <el-button type="primary" @click="handleQuery">查询</el-button>
      </div>
    </div>

    <!-- 列表区：无复选框列（附件删除为单条后台删除），末列操作按钮 -->
    <el-table :data="list" v-loading="loading" border stripe row-key="fieldId">
      <template #empty>
        <el-empty description="暂无附件数据" :image-size="80" />
      </template>
      <el-table-column prop="fieldName" label="文件名" min-width="200" show-overflow-tooltip />
      <el-table-column label="文件类型" width="90">
        <template #default="{ row }">{{ row.fieldFileType ? '.' + row.fieldFileType : '-' }}</template>
      </el-table-column>
      <el-table-column prop="fieldModelName" label="模块名" width="120" show-overflow-tooltip />
      <el-table-column prop="fieldModelId" label="文档id" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fieldModelId || '-' }}</template>
      </el-table-column>
      <el-table-column prop="fieldKey" label="关键字" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fieldKey || '-' }}</template>
      </el-table-column>
      <el-table-column label="是否有效" width="90">
        <template #default="{ row }">
          <el-tag :type="row.fieldEnable ? 'success' : 'danger'" size="small">
            {{ YES_NO_TEXT[row.fieldEnable] || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上传时间" width="170" align="center">
        <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
      </el-table-column>
      <el-table-column prop="fieldDownload" label="下载次数" width="90" align="right" />
      <el-table-column prop="fieldView" label="预览次数" width="90" align="right" />
      <el-table-column label="操作" width="150" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="goOperationLog(row)">日志</el-button>
          <el-button link type="primary" @click="handleDownload(row)">下载</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="queryPage"
      v-model:page-size="queryPageSize"
      class="list-pagination"
      :total="total"
      layout="total, prev, pager, next, sizes"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="loadList"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<script setup>
import { onActivated, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminDeleteAttachment,
  adminDownloadAttachment,
  queryAttachmentList
} from '@/api/attachment'
import { getDictOptions, getDictTextMap } from '@/utils/dict'

/**
 * 附件管理页（后台管理，SYS_ATT_MAIN）：附件主表列表分页查询。
 * 筛选项为文件名/模块名/关键字/是否有效（默认「有效」）；
 * 显示项为文件名、文件类型、模块名、文档id、关键字、是否有效、上传时间、下载次数、预览次数；
 * 行内操作提供「日志（跳转该附件操作日志）/ 下载（后台下载）/ 删除（后台删除，操作日志区别于公共组件）」；
 * 无批量操作，故不设复选框列。
 * 是否有效选项与文本（dict.yes_no）由 dict 模块提供。
 */

/** 是否有效筛选（dict.yes_no，boolean 值；null=全部） */
const ENABLE_OPTIONS = ref([{ label: '不限', value: null }])
/** 是否有效文本映射（dict.yes_no） */
const YES_NO_TEXT = ref({})
const SORT_OPTIONS = [
  { value: 'createTime', label: '创建时间' },
  { value: 'download', label: '下载次数' },
  { value: 'view', label: '预览次数' }
]

const router = useRouter()

const filters = reactive({
  fieldName: '',
  fieldModelName: '',
  fieldKey: '',
  fieldEnable: true // 默认「有效」
})

const sortField = ref('createTime') // 默认创建时间
const sortOrder = ref('desc') // 默认降序

const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

/** 单选条件排序：点击已选中项切换升降序，点击未选中项选中并默认降序 */
function toggleSort(value) {
  if (sortField.value === value) {
    sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  } else {
    sortField.value = value
    sortOrder.value = 'desc'
  }
  queryPage.value = 1
  loadList()
}

function handleQuery() {
  queryPage.value = 1
  loadList()
}

function handleSizeChange() {
  queryPage.value = 1
  loadList()
}

async function loadList() {
  loading.value = true
  try {
    const res = await queryAttachmentList({
      body: {
        fieldName: filters.fieldName || null,
        fieldModelName: filters.fieldModelName || null,
        fieldKey: filters.fieldKey || null,
        fieldEnable: filters.fieldEnable
      },
      pages: { pageNo: queryPage.value, pageSize: queryPageSize.value, needPage: true },
      sortField: sortField.value,
      sortOrder: sortOrder.value
    })
    list.value = res.data || []
    total.value = (res.metadata && res.metadata.total) || list.value.length
  } catch (error) {
    ElMessage.error(error.message || '查询失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 跳转该附件的操作日志记录 */
function goOperationLog(row) {
  router.push({
    path: `/attOperation/${row.fieldId}`,
    query: { name: row.fieldName || '' }
  })
}

/** 后台下载：以附件原名保存文件 */
async function handleDownload(row) {
  try {
    const response = await adminDownloadAttachment(row.fieldId)
    saveBlob(response.data, row.fieldName || 'download')
  } catch (error) {
    ElMessage.error(error.message || '下载失败')
  }
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/** 后台删除：二次确认并提示影响范围，操作日志类型为「后台删除」 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除附件「${row.fieldName}」吗？删除后该附件及其文件记录将标记为无效，操作不可恢复。`,
      '后台删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await adminDeleteAttachment(row.fieldId)
    ElMessage.success('删除成功')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

onMounted(() => {
  loadDictOptions()
  loadList()
})
// KeepAlive 页签切换返回时刷新列表数据（保留筛选与分页状态；首次进入由 onMounted 加载）
let firstActivation = true
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  loadList()
})

/** 加载是否有效选项与文本映射（dict.yes_no）；失败时保留「全部」兜底 */
async function loadDictOptions() {
  try {
    const [options, textMap] = await Promise.all([
      getDictOptions('yes_no', { boolean: true }),
      getDictTextMap('yes_no', { boolean: true })
    ])
    ENABLE_OPTIONS.value = [{ label: '不限', value: null }, ...options]
    YES_NO_TEXT.value = textMap
  } catch {
    ENABLE_OPTIONS.value = [{ label: '不限', value: null }]
    YES_NO_TEXT.value = {}
  }
}
</script>

<script>
export default { name: 'AttManageView' }
</script>

<style scoped>
.att-manage {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */
</style>

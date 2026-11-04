<template>
  <div class="att-file-manage">
    <!-- 筛选条件区：四分栅栏排列（文件名 | 文件类型 | 是否删除） -->
    <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="文件名">
            <el-input v-model="filters.fieldName" placeholder="请输入文件名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="文件类型">
            <el-input v-model="filters.fieldFileType" placeholder="如 pdf / xlsx" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="是否删除">
            <el-select v-model="filters.fieldIsDelete" style="width: 100%">
              <el-option v-for="opt in IS_DELETE_OPTIONS" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 工具区：左侧排序 + 右侧按钮（仅查询，无任何行操作） -->
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

    <!-- 列表区：仅提供对 sys_att_file 的查询，无操作列 -->
    <el-table :data="list" v-loading="loading" border stripe row-key="fieldId">
      <el-table-column prop="fieldName" label="文件名" min-width="200" show-overflow-tooltip />
      <el-table-column label="文件类型" width="100">
        <template #default="{ row }">{{ row.fieldFileType ? '.' + row.fieldFileType : '-' }}</template>
      </el-table-column>
      <el-table-column label="文件大小" width="110" align="right">
        <template #default="{ row }">{{ formatSize(row.fieldFileSize) }}</template>
      </el-table-column>
      <el-table-column prop="fieldFilePath" label="保存路径" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fieldFilePath || '-' }}</template>
      </el-table-column>
      <el-table-column label="上传时间" width="170">
        <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
      </el-table-column>
      <el-table-column label="是否删除" width="100">
        <template #default="{ row }">
          <el-tag :type="row.fieldIsDelete ? 'danger' : 'success'" size="small">
            {{ YES_NO_TEXT[row.fieldIsDelete] || '-' }}
          </el-tag>
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
import { ElMessage } from 'element-plus'
import { queryAttFileList } from '@/api/attachment'
import { getDictOptions, getDictTextMap } from '@/utils/dict'

/**
 * 文件管理页（后台管理，SYS_ATT_MAIN）：仅提供对 sys_att_file 的查询，
 * 核心查询信息为文件类型、文件大小、保存路径、是否删除等。
 * 筛选项为文件名/文件类型/是否删除（默认「未删除」）；
 * 显示项为文件名、文件类型、文件大小、保存路径、上传时间、是否删除；
 * 无任何行操作（删除等动作由附件管理页统一承担），故不设操作列与复选框列。
 * 是否删除选项与文本（dict.yes_no）由 dict 模块提供。
 */

/** 是否删除筛选（dict.yes_no，boolean 值；null=全部） */
const IS_DELETE_OPTIONS = ref([{ label: '不限', value: null }])
/** 是否删除文本映射（dict.yes_no） */
const YES_NO_TEXT = ref({})
const SORT_OPTIONS = [
  { value: 'createTime', label: '创建时间' },
  { value: 'fileSize', label: '文件大小' }
]

const filters = reactive({
  fieldName: '',
  fieldFileType: '',
  fieldIsDelete: false // 默认「未删除」
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
    const res = await queryAttFileList({
      body: {
        fieldName: filters.fieldName || null,
        fieldFileType: filters.fieldFileType || null,
        fieldIsDelete: filters.fieldIsDelete
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

/** 加载是否删除选项与文本映射（dict.yes_no）；失败时保留「全部」兜底 */
async function loadDictOptions() {
  try {
    const [options, textMap] = await Promise.all([
      getDictOptions('yes_no', { boolean: true }),
      getDictTextMap('yes_no', { boolean: true })
    ])
    IS_DELETE_OPTIONS.value = [{ label: '不限', value: null }, ...options]
    YES_NO_TEXT.value = textMap
  } catch {
    IS_DELETE_OPTIONS.value = [{ label: '不限', value: null }]
    YES_NO_TEXT.value = {}
  }
}
</script>

<script>
export default { name: 'AttFileManageView' }
</script>

<style scoped>
.att-file-manage {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */
</style>

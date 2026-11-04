<template>
  <div class="task-manage">
    <!-- 筛选条件区：名称、状态、是否启用，四分栅栏 -->
    <el-form class="filter-form" :model="filters" label-width="90px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="名称">
            <el-input v-model="filters.fieldName" placeholder="任务名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态">
            <el-select v-model="filters.fieldStatus" style="width: 100%">
              <el-option label="不限" value="" />
              <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="是否启用">
            <el-select v-model="filters.fieldEnable" style="width: 100%">
              <el-option v-for="opt in ENABLE_OPTIONS" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 工具区：左侧排序（登记时间）+ 右侧按钮 -->
    <div class="list-toolbar">
      <div class="sort-area">
        <span class="sort-item active" @click="toggleSort">
          登记时间
          <i class="sort-arrow">{{ sortOrder === 'desc' ? '↓' : '↑' }}</i>
        </span>
      </div>
      <div class="btn-area">
        <el-button type="primary" @click="handleQuery">查询</el-button>
        <el-button type="warning" plain :disabled="!selectedIds.length" @click="handleBatchDisable">
          批量停用
        </el-button>
        <el-button type="success" plain :disabled="!selectedIds.length" @click="handleBatchEnable">
          批量启用
        </el-button>
      </div>
    </div>

    <!-- 列表区：复选框 + 任务名/cron/是否启用/状态/登记时间 + 操作列 -->
    <el-table :data="list" v-loading="loading" border stripe row-key="fieldId" @selection-change="handleSelectionChange">
      <template #empty>
        <el-empty description="暂无任务数据" :image-size="80" />
      </template>
      <el-table-column type="selection" width="50" />
      <el-table-column prop="fieldName" label="任务名" min-width="180" show-overflow-tooltip />
      <el-table-column prop="fieldCron" label="cron表达式" width="170" show-overflow-tooltip />
      <el-table-column label="是否启用" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.fieldEnable ? 'success' : 'info'" size="small">
            {{ ENABLE_TEXT[row.fieldEnable] || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.fieldStatus)" size="small">
            {{ statusText(row.fieldStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="登记时间" width="170">
        <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="goEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="handleToggleEnable(row)">{{ row.fieldEnable ? '停用' : '启用' }}</el-button>
          <el-button link type="success" @click="handleExecute(row)">执行</el-button>
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
import { batchDisableTask, batchEnableTask, disableTask, enableTask, executeTask, queryTaskList } from '@/api/task'
import { getDictOptions, getDictTextMap } from '@/utils/dict'

/**
 * 定时任务管理页（后台管理-定时任务，SYS_TASK_ADMIN）：
 * 筛选区（名称/状态/是否启用）、排序区（登记时间，唯一排序维度，默认降序）、
 * 按钮区（查询/批量停用/批量启用）、列表区（任务名/cron/是否启用/状态/登记时间，
 * 操作列：编辑、启用/停用、手动执行）。任务由系统启动扫描 @SysTask 生成，无新建入口。
 * 状态选项与文本（dict.task_status）由 dict 模块提供。
 */

/** 状态选项（dict.task_status；"全部"项在模板中提供） */
const STATUS_OPTIONS = ref([])
/** 状态文本映射（dict.task_status） */
const STATUS_TEXT = ref({})
/** 是否启用筛选（dict.yes_no，boolean 值；null=全部） */
const ENABLE_OPTIONS = ref([{ label: '不限', value: null }])
/** 是否启用文本映射（dict.yes_no） */
const ENABLE_TEXT = ref({})

const router = useRouter()

const filters = reactive({
  fieldName: '',
  fieldStatus: '',
  fieldEnable: true // 默认启用
})

const sortOrder = ref('desc') // 仅登记时间一个排序维度，默认降序
const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)
const selectedIds = ref([])

function statusText(value) {
  return STATUS_TEXT.value[value] || value || '-'
}

function statusType(value) {
  return (
    {
      WAITING: 'info',
      RUNNING: 'warning',
      FINISH: 'success',
      BLOCK: 'danger',
      FAIL: 'danger'
    }[value] || 'info'
  )
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map((row) => row.fieldId)
}

function toggleSort() {
  sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
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
    const res = await queryTaskList({
      body: {
        fieldName: filters.fieldName || null,
        fieldStatus: filters.fieldStatus || null,
        fieldEnable: filters.fieldEnable === '' ? null : filters.fieldEnable
      },
      pages: { pageNo: queryPage.value, pageSize: queryPageSize.value, needPage: true }
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

function goEdit(row) {
  router.push(`/task/edit/${row.fieldId}`)
}

/** 单条启用/停用：仅更新是否启用状态，二次确认并提示影响范围 */
async function handleToggleEnable(row) {
  const enable = !row.fieldEnable
  try {
    await ElMessageBox.confirm(
      enable ? `确定启用任务「${row.fieldName}」？启用后将按 cron 表达式自动调度执行。` : `确定停用任务「${row.fieldName}」？停用后不再按 cron 自动调度。`,
      enable ? '启用确认' : '停用确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    if (enable) {
      await enableTask(row.fieldId)
    } else {
      await disableTask(row.fieldId)
    }
    ElMessage.success(enable ? '已启用' : '已停用')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 批量停用：二次确认并明确影响范围 */
async function handleBatchDisable() {
  await confirmBatch('停用', false)
}

/** 批量启用：二次确认并明确影响范围 */
async function handleBatchEnable() {
  await confirmBatch('启用', true)
}

async function confirmBatch(action, enable) {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先选择任务')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定批量${action}选中的 ${selectedIds.value.length} 个任务？${enable ? '启用后将按各自 cron 表达式自动调度执行。' : '停用后不再按 cron 自动调度。'}`,
      `批量${action}确认`,
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    if (enable) {
      await batchEnableTask(selectedIds.value)
    } else {
      await batchDisableTask(selectedIds.value)
    }
    ElMessage.success(`已批量${action}`)
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 手动执行：立即执行一次，二次确认 */
async function handleExecute(row) {
  try {
    await ElMessageBox.confirm(
      `确定立即执行任务「${row.fieldName}」？将在 cron 调度之外立即执行一次。`,
      '执行确认',
      { type: 'warning', confirmButtonText: '执行', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await executeTask(row.fieldId)
    ElMessage.success('任务已触发执行')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '执行失败')
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

/** 加载任务状态与是否启用码值（dict.task_status / yes_no）；失败时保持兜底，不阻塞页面 */
async function loadDictOptions() {
  try {
    const [options, textMap, enableOptions, enableTextMap] = await Promise.all([
      getDictOptions('task_status'),
      getDictTextMap('task_status'),
      getDictOptions('yes_no', { boolean: true }),
      getDictTextMap('yes_no', { boolean: true })
    ])
    STATUS_OPTIONS.value = options
    STATUS_TEXT.value = textMap
    ENABLE_OPTIONS.value = [{ label: '不限', value: null }, ...enableOptions]
    ENABLE_TEXT.value = enableTextMap
  } catch {
    STATUS_OPTIONS.value = []
    STATUS_TEXT.value = {}
    ENABLE_OPTIONS.value = [{ label: '不限', value: null }]
    ENABLE_TEXT.value = {}
  }
}
</script>

<script>
export default { name: 'TaskManageView' }
</script>

<style scoped>
.task-manage {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */
</style>

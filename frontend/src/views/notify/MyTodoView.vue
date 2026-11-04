<template>
  <div class="my-todo">
    <!-- 筛选条件区：待办类型 | 是否办结 | 来源模块 | 是否星标（一行 4 个），标题 | 接收时间（2 栅栏宽） -->
    <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="待办类型">
            <el-select v-model="filters.fieldNotifyType" style="width: 100%" @change="handleQuery">
              <el-option v-for="opt in NOTIFY_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="是否办结">
            <el-select v-model="filters.fieldIsDone" style="width: 100%" @change="handleQuery">
              <el-option v-for="opt in DONE_OPTIONS" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="来源模块">
            <el-select v-model="filters.fieldModelName" placeholder="请选择来源模块" clearable style="width: 100%" @change="handleQuery">
              <el-option label="不限" value="" />
              <el-option v-for="opt in modelOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="是否星标">
            <el-select v-model="filters.fieldIsStar" style="width: 100%">
              <el-option v-for="opt in STAR_OPTIONS" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="标题">
            <el-input v-model="filters.fieldName" placeholder="请输入标题" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="接收时间">
            <DateTimePicker
              v-model="receiveTimeRange"
              calendar-type="daterange"
              date-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 工具区：左侧排序（接收时间），右侧批量操作 + 查询 -->
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
        <el-button v-if="filters.fieldIsDone !== true" :disabled="!selectedIds.length" @click="handleBatchDone">批量已办</el-button>
        <el-button v-if="filters.fieldIsDone === true" :disabled="!selectedIds.length" @click="handleBatchUndone">批量转未办</el-button>
        <el-button :disabled="!selectedIds.length" @click="handleBatchStar">批量星标</el-button>
        <el-button type="danger" plain :disabled="!selectedIds.length" @click="handleBatchDelete">批量删除</el-button>
      </div>
    </div>

    <!-- 列表区：复选框列 + 标题/来源模块/接收时间/是否星标 + 末列操作 -->
    <el-table :data="list" v-loading="loading" border stripe row-key="fieldId" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="fieldName" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="fieldModelName" label="来源模块" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fieldModelName || '-' }}</template>
      </el-table-column>
      <el-table-column label="接收时间" width="170" align="center">
        <template #default="{ row }">{{ formatTime(row.fieldReceiveTime) }}</template>
      </el-table-column>
      <el-table-column label="是否星标" width="90">
        <template #default="{ row }">
          <el-tag :type="row.fieldIsStar ? 'warning' : 'info'" size="small">
            {{ row.fieldIsStar ? '已星标' : '未星标' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleDetail(row)">详情</el-button>
          <el-button link :type="row.fieldIsDone ? 'info' : 'success'" @click="toggleDone(row)">
            {{ row.fieldIsDone ? '未办' : '已办' }}
          </el-button>
          <el-button link type="warning" @click="handleStar(row)">
            {{ row.fieldIsStar ? '取消星标' : '星标' }}
          </el-button>
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

    <!-- 详情弹窗：调 load 接口（待阅自动转已办）后展示，有链接时提供前往处理 -->
    <el-dialog v-model="detailVisible" title="待办详情" width="560px" append-to-body destroy-on-close>
      <div v-if="detail" class="detail-grid">
        <div class="detail-item is-full">
          <div class="detail-label">标题</div>
          <div class="detail-value">{{ detail.fieldName || '-' }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">发送人</div>
          <div class="detail-value">{{ detail.fieldCreatorName || '-' }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">接收时间</div>
          <div class="detail-value">{{ formatTime(detail.fieldReceiveTime) }}</div>
        </div>
        <div class="detail-item is-full">
          <div class="detail-label">内容</div>
          <div class="detail-value detail-content">{{ detail.fieldContent || '-' }}</div>
        </div>
      </div>
      <template #footer>
        <el-button v-if="detail && detail.fieldUrl" type="primary" @click="goUrl(detail.fieldUrl)">前往处理</el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onActivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchDeleteTodo,
  batchDoneTodo,
  batchStarTodo,
  batchUndoneTodo,
  deleteTodo,
  loadTodoDetail,
  markTodoDone,
  markTodoUndone,
  queryMyTodoList,
  queryModelOptions,
  toggleTodoStar
} from '@/api/notify'
import { getDictOptions, getDictTextMap } from '@/utils/dict'
import { useNotifyStore } from '@/stores/notify'
import DateTimePicker from '@/components/DateTimePicker.vue'

/**
 * 我的待办列表页（首页下「待办」菜单）：只显示当前用户接收到的待办。
 * 筛选：待办类型（默认待办，无"全部"）/ 来源模块 / 是否星标 / 是否办结（默认未办，无"全部"）
 *       / 标题（2栅栏宽）/ 接收时间（范围，限年月日，2栅栏宽）；
 * 排序：接收时间；按钮：批量已办/批量转未办（按是否办结显隐）/ 批量星标/批量删除；
 * 行操作：详情（待阅点击详情自动转已办）、已办/未办、星标、删除（软删除）。
 * 码值（待办类型 notify_type / 是否 yes_no）由 dict 模块提供。
 */
const SORT_OPTIONS = [{ value: 'receiveTime', label: '接收时间' }]

/** 待办类型下拉选项（dict.notify_type，不含"全部"） */
const NOTIFY_TYPE_OPTIONS = ref([])
/** 待办类型文本映射（dict.notify_type） */
const NOTIFY_TYPE_TEXT = ref({})
/** 是否星标筛选（dict.yes_no，boolean 值；null=全部） */
const STAR_OPTIONS = ref([{ label: '不限', value: null }])

/** 是否办结选项（默认未办，无"全部"） */
const DONE_OPTIONS = [
  { label: '未办', value: false },
  { label: '已办', value: true }
]

/** 来源模块选项（读取 model:name:map 缓存，value=MODEL_KEY，label=MODEL_NAME） */
const modelOptions = ref([])

const filters = reactive({
  fieldNotifyType: '1', // 默认待办，不可"全部"
  fieldModelName: '',
  fieldIsStar: null,
  fieldIsDone: false, // 默认未办，无"全部"
  fieldName: ''
})
const receiveTimeRange = ref([])

const sortField = ref('receiveTime') // 默认接收时间
const sortOrder = ref('desc') // 默认降序

/** 路由 query 参数：从首页统计卡片点击跳转携带 */
const route = useRoute()

const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)
const selectedIds = ref([])

const detailVisible = ref(false)
const detail = ref(null)

const notifyStore = useNotifyStore()

// 进入待办页面时重置未读计数
notifyStore.reset()

// 监听全局 SSE 推送版本号，有新待办时自动刷新列表
watch(() => notifyStore.version, () => {
  loadList()
})

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

function handleSelectionChange(rows) {
  selectedIds.value = rows.map((row) => row.fieldId)
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
    const [start, end] = receiveTimeRange.value || []
    const res = await queryMyTodoList({
      body: {
        fieldNotifyType: filters.fieldNotifyType,
        fieldModelName: filters.fieldModelName || null,
        fieldIsStar: filters.fieldIsStar,
        fieldIsDone: filters.fieldIsDone,
        fieldName: filters.fieldName || null,
        fieldReceiveTimeStart: start || null,
        fieldReceiveTimeEnd: end || null
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

/** 详情：始终打开详情弹窗；待阅类型在加载时自动转已办。 */
async function handleDetail(row) {
  try {
    const res = await loadTodoDetail(row.fieldId)
    detail.value = res.data
    detailVisible.value = true
    // 待阅类型在 load 时已自动转已办，刷新列表同步状态
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '详情加载失败')
  }
}

function goUrl(url) {
  window.open(url, '_blank')
}

/** 已办/未办切换 */
async function toggleDone(row) {
  try {
    if (row.fieldIsDone) {
      await markTodoUndone(row.fieldId)
      ElMessage.success('已转未办')
    } else {
      await markTodoDone(row.fieldId)
      ElMessage.success('已办')
    }
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 星标切换 */
async function handleStar(row) {
  try {
    await toggleTodoStar(row.fieldId)
    ElMessage.success(row.fieldIsStar ? '已取消星标' : '已星标')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 单个删除：软删除，二次确认 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除待办「${row.fieldName}」吗？删除后仅从您的待办列表隐藏，不影响其他接收人。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteTodo(row.fieldId)
    ElMessage.success('删除成功')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

/** 批量已办 */
async function handleBatchDone() {
  try {
    await batchDoneTodo(selectedIds.value)
    ElMessage.success('批量已办成功')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 批量转未办 */
async function handleBatchUndone() {
  try {
    await batchUndoneTodo(selectedIds.value)
    ElMessage.success('批量转未办成功')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 批量星标 */
async function handleBatchStar() {
  try {
    await batchStarTodo(selectedIds.value)
    ElMessage.success('批量星标成功')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 批量删除（软删除），二次确认 */
async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${selectedIds.value.length} 条待办吗？删除后仅从您的待办列表隐藏。`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await batchDeleteTodo(selectedIds.value)
    ElMessage.success('批量删除成功')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
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
  loadModelOptions()
  // 从首页统计卡片跳转携带 query 参数：预填待办类型和接收时间范围
  if (route.query.fieldNotifyType) {
    filters.fieldNotifyType = route.query.fieldNotifyType
  }
  if (route.query.receiveTimeStart && route.query.receiveTimeEnd) {
    receiveTimeRange.value = [route.query.receiveTimeStart, route.query.receiveTimeEnd]
  }
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

/** 加载码值选项（待办类型 / 是否星标）与文本映射；失败时保持空选项与「全部」兜底 */
async function loadDictOptions() {
  try {
    const [typeOptions, typeText, starOptions] = await Promise.all([
      getDictOptions('notify_type'),
      getDictTextMap('notify_type'),
      getDictOptions('yes_no', { boolean: true })
    ])
    NOTIFY_TYPE_OPTIONS.value = typeOptions
    NOTIFY_TYPE_TEXT.value = typeText
    STAR_OPTIONS.value = [{ label: '不限', value: null }, ...starOptions]
  } catch {
    // 码值加载失败不阻塞页面（筛选无选项、文本回退原值）
  }
}

/** 加载来源模块下拉选项（MODEL_KEY → MODEL_NAME） */
async function loadModelOptions() {
  try {
    const res = await queryModelOptions()
    const map = (res && res.data) || {}
    modelOptions.value = Object.entries(map).map(([value, label]) => ({ value, label }))
  } catch {
    // 选项加载失败不阻塞页面
  }
}
</script>

<script>
export default { name: 'MyTodoView' }
</script>

<style scoped>
.my-todo {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
.detail-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css；
   详情弹窗网格复用 styles/form-page.css 的 detail-grid 系列 */
</style>

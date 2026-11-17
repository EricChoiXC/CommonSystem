<template>
  <div class="ds-table">
    <!-- 数据源未命中 / options 加载失败：空态提示，不再发起取数 -->
    <el-empty v-if="notFound" :description="notFoundTip" :image-size="80" />
    <!-- 取数失败：空态提示（保留重试入口由宿主 refresh 承担） -->
    <el-empty v-else-if="loadError" :description="loadError" :image-size="80" />
    <template v-else>
      <!-- 动态列：R04 不假设固定列，列头以有效返回字段集为准（options.fields 预渲染，metadata.fields 每次查询后校正） -->
      <el-table v-loading="loading" :data="rows" border stripe :default-sort="defaultSort">
        <el-table-column
          v-for="field in columns"
          :key="field.key"
          :prop="field.key"
          :label="field.label || field.key"
          :min-width="columnMinWidth(field)"
          :sortable="field.sortable === true ? 'custom' : false"
          :sort-orders="SORT_ORDERS"
          show-overflow-tooltip
          :formatter="(row, column, cellValue) => renderCell(field, cellValue)"
        />
      </el-table>

      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        class="ds-table-pagination"
        :total="total"
        layout="total, prev, pager, next, sizes"
        :page-sizes="PAGE_SIZES"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getDatasourceOptions, queryDatasource } from '@/api/datasource'

/**
 * 数据源表格组件（DatasourceTable）
 *
 * 数据源取数消费的通用只读表格（v1.8，任务 F18-FE-005；消费方式见
 * docs/系统模块/系统模块UI设计文档.md 4.10.7 方式二）：宿主只传数据源编码，
 * 组件内部完成「options → 列头渲染 → query 取数」编排。
 *
 * 契约（docs/系统模块/前端公共组件.md 九）：
 * - props.code：数据源编码（默认或自定义，如 calendar.list），必填；
 * - props.filters：调用方增量过滤条件 { 字段key: 值 }，默认 null——固定条件由服务端
 *   强制、同名 key 服务端覆盖调用方条件，宿主只传增量；
 * - props.sortField：默认排序字段，须 ∈ 目标能力可排序白名单（sortable=true 汇总集），
 *   非法值由后端回退目标能力默认排序；表头点击排序（sortable=custom）不排前端数据，
 *   改查后端（变更后的 sortField/sortOrder 放进下一次 query）；
 * - defineExpose.refresh()：回第 1 页并重走完整编排（options 重查 + query）。
 *
 * 内置行为：
 * - 挂载后调 getDatasourceOptions() 按 code 命中条目，以 options.fields 预渲染列头；
 *   失败或未命中给出空态提示，不再请求 query；
 * - query 响应 metadata.fields 每次查询后校验，与当前列头不一致时以 metadata.fields
 *   为准渲染（R04：不假设固定列）；总数取 metadata.total；
 * - field.type 为 DATE / DATETIME 的列做时间格式化展示（YYYY-MM-DD / YYYY-MM-DD HH:mm:ss，
 *   后端 java.util.Date 序列化为毫秒时间戳）；其余类型原样展示，空值显示 '-'；
 *   码值翻译属消费方职责（TBD 裁决 1），组件不做枚举翻译。
 *
 * 权限口径：options / query 登录即可；取数越权由执行期委托目标 action 鉴权收敛
 * （UI 文档 4.10.7），组件内不做权限判断。
 */
const props = defineProps({
  /** 数据源编码（默认或自定义，如 calendar.list），必填 */
  code: { type: String, required: true },
  /** 调用方增量过滤条件 { 字段key: 值 }；固定条件由服务端强制、同名 key 覆盖调用方条件，仅传增量 */
  filters: { type: Object, default: null },
  /** 默认排序字段（须 ∈ 目标能力可排序白名单，非法值后端回退默认排序） */
  sortField: { type: String, default: '' }
})

/** 分页常量：对齐全工程列表页分页规格 */
const PAGE_SIZES = [10, 20, 50, 100]

/** 表头点击排序循环顺序：降序 → 升序 → 取消（取消后回退宿主默认排序） */
const SORT_ORDERS = ['descending', 'ascending', null]

/* ==================== 状态 ==================== */

/** 有效返回字段集（列头渲染源）：options.fields 预渲染，metadata.fields 查询后校正 */
const columns = ref([])

/** 当前行数据（动态列 Map 数组） */
const rows = ref([])

/** 满足条件的总数（metadata.total） */
const total = ref(0)

/** 当前排序字段 / 方向（asc / desc，默认 desc），初始取 props.sortField */
const currentSortField = ref(props.sortField || '')
const currentSortOrder = ref('desc')

/** 分页状态：默认第 1 页、每页 10 条 */
const pageNo = ref(1)
const pageSize = ref(10)

/** 加载中（options + query 全程） */
const loading = ref(false)

/** 数据源未命中 / options 失败空态：true 时不渲染表格与分页 */
const notFound = ref(false)
const notFoundTip = ref('')

/** 取数失败空态提示（查询报错时表格区替换为空态） */
const loadError = ref('')

/** el-table 首次渲染的默认排序列（仅当 props.sortField 在字段集中声明可排序时展示箭头） */
const defaultSort = computed(() => {
  if (!props.sortField) {
    return null
  }
  const field = columns.value.find((item) => item && item.key === props.sortField)
  return field && field.sortable === true ? { prop: props.sortField, order: 'descending' } : null
})

/* ==================== 编排 ==================== */

/** 请求序号守卫：props 变更触发的重编排使在途响应作废，防竞态回写 */
let requestSeq = 0

/**
 * 完整编排（挂载 / code / filters / sortField 变更 / refresh）：
 * options 重查 → 回第 1 页取数。
 */
async function reloadAll() {
  const seq = ++requestSeq
  loading.value = true
  try {
    const ok = await loadOption(seq)
    if (!ok) {
      return
    }
    await fetchRows(seq, 1)
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

/**
 * 拉取数据源下拉并按 props.code 命中：命中时以 options.fields 预渲染列头；
 * 失败或未命中置 notFound 空态并返回 false（不再请求 query）。
 */
async function loadOption(seq) {
  try {
    const res = await getDatasourceOptions()
    if (seq !== requestSeq) {
      return false
    }
    const list = (res && res.data) || []
    const hit = list.find((item) => item && item.code === props.code)
    if (!hit) {
      enterNotFound(`未找到数据源「${props.code}」，请确认编码或联系管理员`)
      return false
    }
    columns.value = Array.isArray(hit.fields) ? hit.fields : []
    return true
  } catch (e) {
    if (seq !== requestSeq) {
      return false
    }
    enterNotFound(`数据源信息加载失败：${(e && e.message) || '未知错误'}`)
    return false
  }
}

/**
 * 取数：body 只传调用方增量条件（props.filters，固定条件由服务端强制），
 * 总数取 metadata.total，metadata.fields 与当前列头不一致时以其为准渲染。
 */
async function fetchRows(seq, page) {
  try {
    const res = await queryDatasource({
      code: props.code,
      body: props.filters || null,
      pages: { pageNo: page, pageSize: pageSize.value, needPage: true },
      sortField: currentSortField.value,
      sortOrder: currentSortOrder.value
    })
    if (seq !== requestSeq) {
      return
    }
    const data = (res && res.data) || []
    rows.value = Array.isArray(data) ? data : []
    const metadata = (res && res.metadata) || {}
    total.value = Number(metadata.total) || 0
    applyMetadataFields(metadata.fields)
    loadError.value = ''
  } catch (e) {
    if (seq !== requestSeq) {
      return
    }
    rows.value = []
    total.value = 0
    loadError.value = `数据查询失败：${(e && e.message) || '未知错误'}`
  }
}

/** metadata.fields 校正（R04）：有效且与当前列头不一致时以 metadata.fields 为准渲染 */
function applyMetadataFields(metaFields) {
  if (!Array.isArray(metaFields) || !metaFields.length) {
    return
  }
  if (JSON.stringify(metaFields) !== JSON.stringify(columns.value)) {
    columns.value = metaFields
  }
}

/** 进入未命中 / 加载失败空态：清空数据，不再请求 query */
function enterNotFound(tip) {
  notFound.value = true
  notFoundTip.value = tip
  columns.value = []
  rows.value = []
  total.value = 0
}

/* ==================== 交互 ==================== */

/** 表头点击排序（sortable=custom）：不排前端数据，改查后端，回第 1 页 */
function handleSortChange({ prop, order }) {
  if (!order) {
    // 取消排序：回退宿主默认排序（props.sortField 非法时由后端回退默认排序）
    currentSortField.value = props.sortField || ''
    currentSortOrder.value = 'desc'
  } else {
    currentSortField.value = prop
    currentSortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  }
  pageNo.value = 1
  fetchRowsPage(1)
}

/** 翻页：仅重新取数（options 已加载，不重查） */
function handlePageChange(page) {
  fetchRowsPage(page)
}

/** 切页大小：回第 1 页取数 */
function handleSizeChange(size) {
  pageSize.value = size
  fetchRowsPage(1)
}

/** 仅取数（翻页 / 切页大小 / 排序），全程持 loading 与序号守卫 */
async function fetchRowsPage(page) {
  const seq = ++requestSeq
  loading.value = true
  try {
    await fetchRows(seq, page)
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

/* ==================== 展示辅助 ==================== */

/** 列最小宽度：DATETIME 170、DATE 120，其余 140（配合 show-overflow-tooltip 自适应） */
function columnMinWidth(field) {
  const type = String((field && field.type) || '').toUpperCase()
  if (type === 'DATETIME') {
    return 170
  }
  if (type === 'DATE') {
    return 120
  }
  return 140
}

/** 单元格渲染：空值显示 '-'；DATE / DATETIME 格式化，其余类型原样展示（码值翻译属消费方职责） */
function renderCell(field, value) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const type = String((field && field.type) || '').toUpperCase()
  if (type === 'DATE') {
    return formatDateValue(value)
  }
  if (type === 'DATETIME') {
    return formatDateTimeValue(value)
  }
  return String(value)
}

function pad(n) {
  return String(n).padStart(2, '0')
}

/** 宽松转 Date：毫秒时间戳（后端 java.util.Date 序列化口径）、ISO 字符串、yyyy-MM-dd HH:mm:ss */
function toDate(value) {
  if (value instanceof Date) {
    return value
  }
  if (typeof value === 'number' || /^\d+$/.test(String(value))) {
    const ms = Number(value)
    const fromNumber = new Date(ms)
    return Number.isNaN(fromNumber.getTime()) ? null : fromNumber
  }
  const date = new Date(String(value).replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

/** DATE 列：YYYY-MM-DD */
function formatDateValue(value) {
  const date = toDate(value)
  if (!date) {
    return String(value)
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** DATETIME 列：YYYY-MM-DD HH:mm:ss */
function formatDateTimeValue(value) {
  const date = toDate(value)
  if (!date) {
    return String(value)
  }
  return `${formatDateValue(date)} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/* ==================== 响应式与生命周期 ==================== */

/** code / filters（deep）变更：回第 1 页重走完整编排（options 重查 + query） */
watch(
  () => props.code,
  () => {
    pageNo.value = 1
    reloadAll()
  }
)

watch(
  () => props.filters,
  () => {
    pageNo.value = 1
    reloadAll()
  },
  { deep: true }
)

/** sortField 变更：同步排序态并回第 1 页重走编排（列头可能随新源变化，一并重查 options） */
watch(
  () => props.sortField,
  () => {
    currentSortField.value = props.sortField || ''
    currentSortOrder.value = 'desc'
    pageNo.value = 1
    reloadAll()
  }
)

onMounted(() => {
  reloadAll()
})

/** 宿主手动刷新：回第 1 页并重走完整编排（options 重查 + query） */
function refresh() {
  pageNo.value = 1
  reloadAll()
}

defineExpose({ refresh })
</script>

<script>
export default { name: 'DatasourceTable' }
</script>

<style scoped>
.ds-table-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>

<template>
  <div class="datasource-manage">
    <!-- 筛选条件区：数据源名称/编码（模糊）、目标能力（精确）、启用状态（默认全部），四分栅栏 -->
    <el-form class="filter-form" :model="filters" label-width="100px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="数据源名称">
            <el-input v-model="filters.fieldName" placeholder="请输入数据源名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="数据源编码">
            <el-input v-model="filters.fieldCode" placeholder="请输入数据源编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="目标能力">
            <el-select v-model="filters.fieldTarget" placeholder="请选择目标能力" clearable filterable style="width: 100%">
              <el-option v-for="item in targetOptions" :key="item.code" :label="targetOptionLabel(item)" :value="item.code" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="启用状态">
            <el-select v-model="filters.fieldEnable" style="width: 100%">
              <el-option v-for="opt in ENABLE_OPTIONS" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 工具区：左侧排序（创建时间）+ 右侧按钮 -->
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
        <el-button v-if="canEdit" type="success" plain @click="goCreate">新建</el-button>
      </div>
    </div>

    <!-- 列表区：无批量操作不加复选框，不展示主键，末列操作（详情/编辑/启停用/删除） -->
    <el-table :data="list" v-loading="loading" border stripe row-key="fieldId">
      <template #empty>
        <el-empty description="暂无数据源数据" :image-size="80" />
      </template>
      <el-table-column prop="fieldCode" label="编码" width="160" show-overflow-tooltip />
      <el-table-column prop="fieldName" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="目标能力" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ targetLabel(row) }}</span>
          <el-tag v-if="targetInvalid(row)" type="danger" size="small" class="target-invalid-tag">目标失效</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="固定条件数" width="100" align="center">
        <template #default="{ row }">{{ conditionCount(row) }}</template>
      </el-table-column>
      <el-table-column label="返回字段数" width="110" align="center">
        <template #default="{ row }">{{ fieldCount(row) }}</template>
      </el-table-column>
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.fieldEnable ? 'success' : 'info'" size="small">
            {{ row.fieldEnable ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">详情</el-button>
          <el-button v-if="canEdit" link type="primary" @click="goEdit(row)">编辑</el-button>
          <el-button v-if="canEdit" link type="warning" @click="handleToggleEnable(row)">
            {{ row.fieldEnable ? '停用' : '启用' }}
          </el-button>
          <el-button v-if="canEdit" link type="danger" @click="handleDelete(row)">删除</el-button>
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
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteDatasource, getDatasourceTargets, listDatasources, updateDatasourceEnable } from '@/api/datasource'
import { hasAuth } from '@/utils/auth'

/**
 * 数据源管理列表页（后台管理-数据源管理，路由 /datasource，meta.title 数据源管理）：
 * 三段式「筛选区（数据源名称/编码模糊、目标能力精确、启用状态默认全部）→
 * 工具区（创建时间单条件排序 + 查询/新建）→ 列表区（编码/名称/目标能力/固定条件数/
 * 返回字段数/启用 tag/创建时间，操作列：详情、编辑、启停用、删除）」。
 * 权限：新建/编辑/启停用/删除按钮按 SYS_DATASOURCE_ADMIN 显隐（前端预判，
 * 最终由后端 DatasourceResolver 兜底）；默认数据源由代码 Provider 注册不入库，
 * 不在本列表展示。
 * 失效行标记（R05）：页面加载时调一次 getDatasourceTargets() 取目标能力编码全集，
 * 行 fieldTarget 不在该全集时目标能力列追加 danger tag「目标失效」（执行将报
 * CS_DATASOURCE_TARGET_INVALID）；固定条件数/返回字段数由行数据 fieldConditions /
 * fieldFields 原文 JSON 字符串解析得出（空 = 0 /「全字段」，解析失败显示「-」）。
 * 对应任务：docs/v1_3-数据源/数据源开发任务清单.md F18-FE-002（UI 文档 4.10.4）。
 */

/** 启用状态筛选选项（静态枚举；null=全部，管理视角需同时看到停用源以便排查失效行） */
const ENABLE_OPTIONS = [
  { label: '全部', value: null },
  { label: '启用', value: true },
  { label: '停用', value: false }
]
/** 排序选项：仅创建时间（后端固定创建时间降序返回，不支持任意排序） */
const SORT_OPTIONS = [{ value: 'createTime', label: '创建时间' }]

const router = useRouter()

/* ============ 按钮权限 ============ */

/** 新建/编辑/启停用/删除所需权限：与后端 datasource.save / update / enable / delete 鉴权一致 */
const AUTH_DATASOURCE_ADMIN = ['SYS_DATASOURCE_ADMIN']

/** 新建/编辑/启停用/删除按钮可见性（预判，最终权限由后端 DatasourceResolver 兜底） */
const canEdit = computed(() => hasAuth(AUTH_DATASOURCE_ADMIN))

/* ============ 目标能力全集（筛选下拉 + 失效行标记 R05） ============ */

const targetOptions = ref([])
/** 目标能力编码全集；null = targets 加载失败，不做失效标记（避免全行误标） */
const targetCodes = ref(null)

/** 目标能力下拉 label：code（name），名称缺失时仅编码 */
function targetOptionLabel(item) {
  return item.name ? `${item.code}（${item.name}）` : item.code
}

/** 行目标能力展示：code（name），能力失效（名称缺失）时仅展示编码 */
function targetLabel(row) {
  return row.fieldTargetName ? `${row.fieldTarget}（${row.fieldTargetName}）` : row.fieldTarget
}

/** 失效行判定（R05）：行 fieldTarget 不在目标能力编码全集 */
function targetInvalid(row) {
  return Array.isArray(targetCodes.value) && !targetCodes.value.includes(row.fieldTarget)
}

/** 页面加载时调一次 targets：供筛选下拉与失效行标记共用 */
async function loadTargets() {
  try {
    const res = await getDatasourceTargets()
    targetOptions.value = res.data || []
    targetCodes.value = targetOptions.value.map((item) => item.code)
  } catch {
    targetOptions.value = []
    targetCodes.value = null
  }
}

/* ============ 筛选与分页 ============ */

const filters = reactive({
  fieldName: '',
  fieldCode: '',
  fieldTarget: null,
  fieldEnable: null // 默认「全部」
})

const sortField = ref('createTime') // 仅创建时间一个排序维度
const sortOrder = ref('desc') // 默认降序
const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

/**
 * 排序区交互（简化口径）：后端 datasource/list 固定按创建时间降序返回，
 * 本区仅维护选中项与升/降箭头的前端交互状态（点已选中项切换升/降），
 * 不改变请求参数，列表数据始终走后端默认排序（创建时间降序）。
 */
function toggleSort(value) {
  if (sortField.value === value) {
    sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  } else {
    sortField.value = value
    sortOrder.value = 'desc'
  }
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
    const res = await listDatasources({
      body: {
        fieldName: filters.fieldName || null,
        fieldCode: filters.fieldCode || null,
        fieldTarget: filters.fieldTarget || null,
        fieldEnable: filters.fieldEnable
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

/* ============ 行内计数解析（fieldConditions / fieldFields 原文 JSON 字符串） ============ */

/**
 * 解析行配置 JSON 字符串为数组：空配置返回 null；损坏 JSON / 非数组返回 undefined；
 * 正常返回数组。列表响应为原文 JSON 字符串（双形态承载的存储态）。
 */
function parseConfigArray(value) {
  if (value == null || value === '') return null
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return Array.isArray(parsed) ? parsed : undefined
  } catch {
    return undefined
  }
}

/** 固定条件数：空 = 0（无固定条件），解析失败 =「-」 */
function conditionCount(row) {
  const arr = parseConfigArray(row.fieldConditions)
  if (arr == null) return 0
  return arr === undefined ? '-' : arr.length
}

/** 返回字段数：空/NULL/空数组 =「全字段」（目标能力默认全字段），解析失败 =「-」 */
function fieldCount(row) {
  const arr = parseConfigArray(row.fieldFields)
  if (arr == null) return '全字段'
  if (arr === undefined) return '-'
  return arr.length ? arr.length : '全字段'
}

/* ============ 路由跳转 ============ */

function goCreate() {
  router.push('/datasource/create')
}

function goEdit(row) {
  router.push(`/datasource/edit/${row.fieldId}`)
}

function goDetail(row) {
  router.push(`/datasource/detail/${row.fieldId}`)
}

/* ============ 启停用 / 删除 ============ */

/** 单条启停用：二次确认；数据源启用不切换互斥（与编号规则 D1 不同），停用即阻断取数 */
async function handleToggleEnable(row) {
  const enable = !row.fieldEnable
  try {
    await ElMessageBox.confirm(
      enable
        ? `确定启用数据源「${row.fieldName}」？启用后消费方可按编码取数。`
        : `确定停用数据源「${row.fieldName}」？停用后消费方取数将报 CS_DATASOURCE_NOT_FOUND。`,
      enable ? '启用确认' : '停用确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await updateDatasourceEnable(row.fieldId, enable)
    ElMessage.success(enable ? '已启用' : '已停用')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 删除：二次确认并提示影响面（删除后消费方取数报 CS_DATASOURCE_NOT_FOUND），启用中数据源文案更强 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      row.fieldEnable
        ? `确定删除启用中的数据源「${row.fieldName}」？删除后消费方取数将报 CS_DATASOURCE_NOT_FOUND，请确认无消费方在用后再删除。`
        : `确定删除数据源「${row.fieldName}」？删除后消费方取数将报 CS_DATASOURCE_NOT_FOUND。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteDatasource(row.fieldId)
    ElMessage.success('已删除')
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
  loadTargets()
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
</script>

<script>
export default { name: 'DatasourceManageView' }
</script>

<style scoped>
.datasource-manage {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */

/* 目标能力列失效标记与能力文本的间距 */
.target-invalid-tag {
  margin-left: 6px;
}
</style>

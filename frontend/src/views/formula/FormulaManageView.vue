<template>
  <div class="formula-manage">
    <!-- 筛选条件区：公式编码/名称（模糊）、公式类型（六类枚举，默认全部）、启用状态（默认全部），四分栅栏 -->
    <el-form class="filter-form" :model="filters" label-width="100px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="公式编码">
            <el-input v-model="filters.fieldCode" placeholder="请输入公式编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="公式名称">
            <el-input v-model="filters.fieldName" placeholder="请输入公式名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="公式类型">
            <el-select v-model="filters.fieldType" style="width: 100%">
              <el-option v-for="opt in FORMULA_TYPE_OPTIONS" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
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
        <el-empty description="暂无公式数据" :image-size="80" />
      </template>
      <el-table-column prop="fieldCode" label="公式编码" width="160" show-overflow-tooltip />
      <el-table-column prop="fieldName" label="公式名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="fieldType" label="公式类型" width="120" show-overflow-tooltip />
      <el-table-column prop="fieldReturnType" label="返回类型" width="110" show-overflow-tooltip />
      <el-table-column label="变量依赖数" width="100" align="center">
        <template #default="{ row }">{{ dependencyCount(row) }}</template>
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
import { deleteFormula, enableFormula, listFormulas } from '@/api/formula'
import { hasAuth } from '@/utils/auth'

/**
 * 公式定义器管理列表页（后台管理-公式定义器管理，路由 /formula，meta.title 公式定义器管理）：
 * 维护命名公式清单——查询、新建、编辑、启停用、删除；命名公式是跨模块复用的公共公式库，
 * 内联公式不入库、不在本列表展示。页面结构与交互严格参照 DatasourceManageView 先例
 * （三段式：筛选区（编码/名称模糊、类型六类枚举、启用状态默认全部——管理视角需同时看到
 * 停用公式以便排查消费方引用）→ 工具区（创建时间单条件排序 + 查询/新建）→ 列表区
 * （编码/名称/类型/返回类型/变量依赖数/启用 tag/创建时间，操作列：详情、编辑、启停用、删除））。
 * 权限：新建/编辑/启停用/删除按钮按 SYS_FORMULA_ADMIN 显隐（前端预判，
 * 最终由后端 SysFormulaResolver 兜底）；公式类型/返回类型列均展示枚举原文，
 * 返回类型（NUMBER/STRING/BOOLEAN/DATE/ARRAY）仅展示、不做筛选条件。
 * 启停用与删除均二次确认：停用/删除后消费方按 field_code 执行将报 CS_FORMULA_NOT_FOUND。
 * 对应任务：docs/v1_4-公式定义器/公式定义器开发任务清单.md F14-FE-004（UI 文档 4.11.4）。
 */

/** 公式类型筛选选项（静态六类枚举；null=全部；仅作用途分类标记，不改变执行语义） */
const FORMULA_TYPE_OPTIONS = [
  { label: '全部', value: null },
  { label: 'FIELD', value: 'FIELD' },
  { label: 'CONDITION', value: 'CONDITION' },
  { label: 'VALIDATION', value: 'VALIDATION' },
  { label: 'VISIBILITY', value: 'VISIBILITY' },
  { label: 'WORKFLOW', value: 'WORKFLOW' },
  { label: 'CUSTOM', value: 'CUSTOM' }
]
/** 启用状态筛选选项（静态枚举；null=全部，管理视角需同时看到停用公式以便排查消费方引用） */
const ENABLE_OPTIONS = [
  { label: '全部', value: null },
  { label: '启用', value: true },
  { label: '停用', value: false }
]
/** 排序选项：仅创建时间（后端固定创建时间降序返回，不支持任意排序） */
const SORT_OPTIONS = [{ value: 'createTime', label: '创建时间' }]

const router = useRouter()

/* ============ 按钮权限 ============ */

/** 新建/编辑/启停用/删除所需权限：与后端 formula.save / update / enable / delete 鉴权一致 */
const AUTH_FORMULA_ADMIN = ['SYS_FORMULA_ADMIN']

/** 新建/编辑/启停用/删除按钮可见性（预判，最终权限由后端 SysFormulaResolver 兜底） */
const canEdit = computed(() => hasAuth(AUTH_FORMULA_ADMIN))

/* ============ 筛选与分页 ============ */

const filters = reactive({
  fieldCode: '',
  fieldName: '',
  fieldType: null,
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
 * 排序区交互（简化口径）：后端 formula/list 固定按创建时间降序返回，
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
    const res = await listFormulas({
      body: {
        fieldCode: filters.fieldCode || null,
        fieldName: filters.fieldName || null,
        fieldType: filters.fieldType || null,
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

/* ============ 行内计数解析 ============ */

/**
 * 变量依赖数：列表响应为保存期解析提取的变量码数组 fieldDependencies
 * （列表不解析、以原文 JSON 字符串承载，如 "[\"applyAmount\", \"taxRate\"]"；
 * load 详情接口才解析为数组——双形态承载），前端兼容数组与 JSON 字符串
 * 两种形态取长度；空 / 缺失 / 解析失败均显示 0。
 */
function dependencyCount(row) {
  const value = row.fieldDependencies
  if (Array.isArray(value)) {
    return value.length
  }
  if (typeof value === 'string' && value) {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed.length : 0
    } catch {
      return 0
    }
  }
  return 0
}

/* ============ 路由跳转 ============ */

function goCreate() {
  router.push('/formula/create')
}

function goEdit(row) {
  router.push(`/formula/edit/${row.fieldId}`)
}

function goDetail(row) {
  router.push(`/formula/detail/${row.fieldId}`)
}

/* ============ 启停用 / 删除 ============ */

/** 单条启停用：二次确认；停用阻断执行期引用（消费方按 field_code 执行报 CS_FORMULA_NOT_FOUND），启用恢复可见 */
async function handleToggleEnable(row) {
  const enable = !row.fieldEnable
  try {
    await ElMessageBox.confirm(
      enable
        ? `确定启用公式「${row.fieldName}」？启用恢复对执行期引用可见。`
        : `确定停用公式「${row.fieldName}」？停用后消费方按 field_code 执行将报 CS_FORMULA_NOT_FOUND。`,
      enable ? '启用确认' : '停用确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await enableFormula(row.fieldId, enable)
    ElMessage.success(enable ? '已启用' : '已停用')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 删除：二次确认并提示影响面（删除后消费方按 field_code 引用执行报 CS_FORMULA_NOT_FOUND），启用中公式文案更强 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      row.fieldEnable
        ? `确定删除启用中的公式「${row.fieldName}」？删除后消费方按 field_code 引用执行将报 CS_FORMULA_NOT_FOUND，请确认无消费方在用后再删除。`
        : `确定删除公式「${row.fieldName}」？删除后消费方按 field_code 引用执行将报 CS_FORMULA_NOT_FOUND。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteFormula(row.fieldId)
    ElMessage.success('已删除')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

/** 创建时间格式化：后端 java.util.Date 序列化为毫秒时间戳，统一展示 YYYY-MM-DD HH:mm:ss（参照工程列表页先例） */
function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

onMounted(() => {
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
export default { name: 'FormulaManageView' }
</script>

<style scoped>
.formula-manage {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */
</style>

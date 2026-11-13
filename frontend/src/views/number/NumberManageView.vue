<template>
  <div class="number-manage">
    <!-- 筛选条件区：规则名称/规则编码（模糊）、所属模型字段（精确）、启用状态（默认全部），四分栅栏 -->
    <el-form class="filter-form" :model="filters" label-width="100px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="规则名称">
            <el-input v-model="filters.fieldName" placeholder="请输入规则名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="规则编码">
            <el-input v-model="filters.fieldCode" placeholder="请输入规则编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="所属模型字段">
            <el-input v-model="filters.fieldModelField" placeholder="请输入模型字段" clearable @keyup.enter="handleQuery" />
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

    <!-- 工具区：左侧排序（创建时间/更新时间）+ 右侧按钮 -->
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
        <el-button v-if="canEdit" type="success" plain @click="goCreate">新增</el-button>
      </div>
    </div>

    <!-- 列表区：无批量操作不加复选框，不展示主键，末列操作（编辑/启停用/预览测试/删除） -->
    <el-table :data="list" v-loading="loading" border stripe row-key="fieldId">
      <template #empty>
        <el-empty description="暂无编号规则数据" :image-size="80" />
      </template>
      <el-table-column prop="fieldCode" label="规则编码" width="150" show-overflow-tooltip />
      <el-table-column prop="fieldName" label="规则名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="fieldModelName" label="所属模型" min-width="140" show-overflow-tooltip />
      <el-table-column prop="fieldModelField" label="所属模型字段" min-width="150" show-overflow-tooltip />
      <el-table-column label="是否启用" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.fieldEnable ? 'success' : 'info'" size="small">
            {{ row.fieldEnable ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ formatTime(row.fieldUpdateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-if="canEdit" link type="primary" @click="goEdit(row)">编辑</el-button>
          <el-button v-if="canEdit" link type="warning" @click="handleToggleEnable(row)">
            {{ row.fieldEnable ? '停用' : '启用' }}
          </el-button>
          <el-button link type="success" @click="openPreview(row)">预览测试</el-button>
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

    <!-- 预览测试弹窗（PAGE-SYS-016）：preview/render 顺延模拟，不推进计数器、不落库 -->
    <el-dialog
      v-model="previewVisible"
      :title="previewRow ? `预览测试 - ${previewRow.fieldName}` : '预览测试'"
      width="560px"
      @closed="handlePreviewClose"
    >
      <div v-loading="previewLoading" class="preview-body">
        <div class="preview-info">
          <span>规则编码：{{ previewRow?.fieldCode || '-' }}</span>
          <span>规则名称：{{ previewRow?.fieldName || '-' }}</span>
          <span>接入项：{{ previewRow?.fieldModelName || '-' }}</span>
        </div>
        <template v-if="!previewFailed">
          <div v-if="previewSamples.length" class="preview-samples">
            <div v-for="(sample, index) in previewSamples" :key="index" class="preview-sample">{{ sample }}</div>
          </div>
          <div v-else class="preview-empty">无样例</div>
          <template v-if="previewErrors.length">
            <div v-for="(error, index) in previewErrors" :key="index" class="preview-error">{{ error.message }}</div>
          </template>
          <div v-else class="preview-ok">规则校验通过</div>
        </template>
        <div v-else class="preview-error">{{ previewErrorMessage }}</div>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteNumberRule, enableNumberRule, queryNumberRuleList, renderNumberPreview } from '@/api/number'
import { hasAuth } from '@/utils/auth'

/**
 * 编号规则管理列表页（后台管理-编号机制-编号管理，路由 /number，meta.title 编号管理）：
 * 三段式「筛选区（规则编码/名称模糊、所属模型字段精确、启用状态默认全部）→
 * 工具区（创建时间/更新时间单条件排序 + 查询/新增）→ 列表区（规则编码/名称/所属模型/
 * 模型字段/是否启用 tag/创建/更新时间，操作列：编辑、启停用、预览测试、删除）」。
 * 权限：新增/编辑/启停用/删除按钮按 SYS_NUMBER_ADMIN / SYS_NUMBER_RULE_EDITOR 显隐
 * （前端预判，最终由后端 NumberResolver 兜底）；列表与预览测试登录即可。
 * 启用走 D1 切换式启用（同接入项下其他已启用规则自动停用）；预览测试弹窗（PAGE-SYS-016）
 * 调 preview/render 顺延模拟取 3 个样例，反馈校验失败原因与越界提示。
 * 对应任务：docs/v1_5-编号功能/00-研发任务清单.md NB4-FE-003。
 */

/** 启用状态筛选选项（number 模块无 dict.yml，静态枚举；null=全部） */
const ENABLE_OPTIONS = [
  { label: '全部', value: null },
  { label: '启用', value: true },
  { label: '停用', value: false }
]
/** 排序选项：创建时间/更新时间（默认创建时间降序） */
const SORT_OPTIONS = [
  { value: 'createTime', label: '创建时间' },
  { value: 'updateTime', label: '更新时间' }
]

const router = useRouter()

/* ============ 按钮权限 ============ */

/** 新增/编辑/启停用/删除所需权限：与后端 rule.save / update / enable / delete 鉴权一致 */
const AUTH_RULE_EDITOR = ['SYS_NUMBER_ADMIN', 'SYS_NUMBER_RULE_EDITOR']

/** 新增/编辑/启停用/删除按钮可见性（预判，最终权限由后端 NumberResolver 兜底） */
const canEdit = computed(() => hasAuth(AUTH_RULE_EDITOR))

/* ============ 筛选与分页 ============ */

const filters = reactive({
  fieldName: '',
  fieldCode: '',
  fieldModelField: '',
  fieldEnable: null // 默认「全部」
})

const sortField = ref('createTime') // 默认创建时间
const sortOrder = ref('desc') // 默认降序
const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

/**
 * 排序区交互（简化口径）：后端 rule/list 固定按创建时间降序返回，不支持任意排序，
 * 本区仅维护选中项与升/降箭头的前端交互状态（点未选中项即选中并降序，
 * 点已选中项切换升/降），不改变请求参数，列表数据始终走后端默认排序（创建时间降序）。
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
    const res = await queryNumberRuleList({
      body: {
        fieldName: filters.fieldName || null,
        fieldCode: filters.fieldCode || null,
        fieldModelField: filters.fieldModelField || null,
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

function goCreate() {
  router.push('/number/create')
}

function goEdit(row) {
  router.push(`/number/edit/${row.fieldId}`)
}

/** 单条启停用：启用走 D1 切换式启用（同接入项下其他已启用规则自动停用），停用后不再被取号命中 */
async function handleToggleEnable(row) {
  const enable = !row.fieldEnable
  try {
    await ElMessageBox.confirm(
      enable
        ? `确定启用规则「${row.fieldName}」？将自动停用同接入项下其他已启用规则（同一接入项仅保留一条启用规则）。`
        : `确定停用规则「${row.fieldName}」？停用后不再被取号命中。`,
      enable ? '启用确认' : '停用确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await enableNumberRule({ fieldId: row.fieldId, fieldEnable: enable })
    ElMessage.success(enable ? '已启用' : '已停用')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 删除规则：已产生取号明细的规则后端拒绝删除（CRM_NUMBER_RULE_IN_USE），仅可停用 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除规则「${row.fieldName}」？已产生取号明细的规则不可删除，仅可停用（CRM_NUMBER_RULE_IN_USE）。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteNumberRule(row.fieldId)
    ElMessage.success('已删除')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

/* ============ 预览测试弹窗（PAGE-SYS-016） ============ */

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewRow = ref(null)
const previewSamples = ref([])
const previewErrors = ref([])
const previewFailed = ref(false)
const previewErrorMessage = ref('')

/** 打开预览测试弹窗：按行规则 fieldId 顺延模拟取 3 个样例（不推进计数器、不落库） */
async function openPreview(row) {
  previewRow.value = row
  previewVisible.value = true
  previewLoading.value = true
  previewSamples.value = []
  previewErrors.value = []
  previewFailed.value = false
  previewErrorMessage.value = ''
  try {
    const res = await renderNumberPreview({ fieldId: row.fieldId, count: 3 })
    const data = res.data || {}
    previewSamples.value = data.samples || []
    previewErrors.value = data.errors || []
    previewFailed.value = false
  } catch (error) {
    previewFailed.value = true
    previewErrorMessage.value = error.message || '预览失败，请稍后重试'
  } finally {
    previewLoading.value = false
  }
}

/** 弹窗关闭后清空预览状态 */
function handlePreviewClose() {
  previewRow.value = null
  previewSamples.value = []
  previewErrors.value = []
  previewFailed.value = false
  previewErrorMessage.value = ''
  previewLoading.value = false
}

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
export default { name: 'NumberManageView' }
</script>

<style scoped>
.number-manage {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */

/* —— 预览测试弹窗（页面特有） —— */
.preview-body {
  min-height: 120px;
}

.preview-info {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.preview-samples {
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}

.preview-sample {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 22px;
  color: var(--el-text-color-primary);
}

.preview-empty {
  padding: 8px 12px;
  border: 1px dashed var(--el-border-color-lighter);
  border-radius: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.preview-error {
  margin-top: 8px;
  font-size: 13px;
  line-height: 20px;
  color: var(--el-color-danger);
}

.preview-ok {
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-color-success);
}
</style>

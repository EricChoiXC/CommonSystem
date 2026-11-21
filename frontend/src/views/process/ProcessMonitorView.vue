<template>
  <div class="page-card">
    <el-card shadow="never">
      <!-- 筛选条件区：四分栅栏排列 -->
      <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="流程名称">
              <el-input v-model="filters.fieldName" placeholder="请输入流程名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="流程编号">
              <el-input v-model="filters.fieldCode" placeholder="请输入流程编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="所属模块">
              <el-select v-model="filters.fieldModelName" placeholder="请选择所属模块" clearable style="width: 100%">
                <el-option label="不限" value="" />
                <el-option v-for="m in models" :key="m.fieldModelName" :label="m.name" :value="m.fieldModelName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="发起人">
              <el-input v-model="filters.fieldCreatorName" placeholder="请输入发起人" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="创建时间">
              <DateTimePicker
                v-model="createTimeRange"
                calendar-type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                datetime-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="流程状态">
              <el-select v-model="filters.fieldStatus" placeholder="请选择流程状态" clearable style="width: 100%">
                <el-option label="不限" value="" />
                <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <!-- 异常状态筛选两态（全部/仅异常）：后端 monitorList 以 exceptionOnly boolean 承载，
                 分册 §8.2 三态中的「排除异常」后端未承载，本期不落（登记偏差） -->
            <el-form-item label="异常状态">
              <el-select v-model="filters.exceptionOnly" style="width: 100%">
                <el-option label="全部" :value="false" />
                <el-option label="仅异常" :value="true" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <!-- 仅看在途开关（inFlightOnly，后端 monitorList 已备）：分册 §8.1 筛选示意未列，本页补位
                 示意的「当前节点」筛选（后端 Query 无对应字段，本期不落，登记偏差） -->
            <el-form-item label="仅看在途">
              <el-switch v-model="filters.inFlightOnly" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 工具区：左侧排序 + 右侧按钮（监控页无新建按钮，实例由业务模块创建） -->
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

      <!-- 列表区：无批量操作不设复选框列、不展示主键；末列操作列 fixed="right" -->
      <el-table :data="list" v-loading="loading" border stripe row-key="fieldId">
        <template #empty>
          <el-empty description="暂无流程实例数据" :image-size="80" />
        </template>
        <el-table-column prop="fieldName" label="流程名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="fieldCode" label="流程编号" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fieldCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="所属模块" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ modelLabel(row.fieldModelName) }}</template>
        </el-table-column>
        <el-table-column prop="fieldCreatorName" label="发起人" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fieldCreatorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
        </el-table-column>
        <el-table-column prop="currentNodeName" label="当前节点" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.currentNodeName || '-' }}</template>
        </el-table-column>
        <el-table-column label="流程状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.fieldStatus)" size="small">{{ statusText(row.fieldStatus) }}</el-tag>
          </template>
        </el-table-column>
        <!-- 异常标记：exceptionFlag=true 显示流程异常 tag；后端 monitorList 响应无错误摘要字段，
             分册 §8.2 的 title 提示本期不落（登记偏差） -->
        <el-table-column label="异常标记" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.exceptionFlag" type="danger" size="small">流程异常</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">详情</el-button>
            <el-button v-if="canAdmin" link type="danger" @click="openAdminOperate(row)">特批操作</el-button>
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
    </el-card>

    <!-- 特批操作弹窗：与实例详情页同一形态（复用 ProcessAdminOperateDialog）；提交成功后刷新当前页列表（不跳转） -->
    <ProcessAdminOperateDialog
      v-model="adminDialogVisible"
      :field-id="adminTarget.fieldId"
      :nodes="adminTarget.nodes"
      :workitems="adminTarget.workitems"
      @success="loadList"
    />
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { hasAuth } from '@/utils/auth'
import { loadProcessExample, queryProcessExampleModels, queryProcessMonitorList } from '@/api/process'
import { getDictOptions, getDictTextMap } from '@/utils/dict'
import DateTimePicker from '@/components/DateTimePicker.vue'
import ProcessAdminOperateDialog from '@/components/process/ProcessAdminOperateDialog.vue'

/**
 * 流程监控页（v1_7 P17-FE-007）：管理员级全量监控列表（POST /backend/sys/process/example/monitorList，
 * 管理员视图不做本人数据过滤），三段式「筛选区 → 工具区 → 列表区」。
 * 筛选区：流程名称/流程编号/所属模块/发起人/创建时间区间/流程状态/异常状态（两态 全部/仅异常，
 * 后端 exceptionOnly boolean 承载）/仅看在途（inFlightOnly 开关）；空值提交 null。
 * 排序区：仅创建时间单条件排序（后端 monitorList 排序归一白名单仅 createTime，
 * 分册 §8.1 示意的更新时间排序后端未承载，本期不落，登记偏差）。
 * 列表：流程名称/流程编号/所属模块（queryProcessExampleModels 映射中文名）/发起人/创建时间/
 * 当前节点/流程状态/异常标记（monitorList 行内 exceptionFlag）+ 操作列（详情 + 特批操作）。
 * 操作列：详情复用 /processExample/detail/:id 实例详情页（不另建详情页）；特批操作仅
 * hasAuth(['SYS_PROCESS_ADMIN','SYS_PROCESS_EXAMPLE_ADMIN']) 显示（预判，最终权限由后端兜底），
 * 打开前按行 loadProcessExample 取节点/工作项候选，弹窗复用 ProcessAdminOperateDialog
 * （与实例详情页同一形态），提交成功后刷新当前页列表（不跳转）。
 * 依据 docs/设计文档/sys/process/流程v1_7前端UI设计分册.md §8、
 * docs/设计文档/sys/process/流程v1_7接口清单.md §5.6。
 * 流程状态选项与文本（dict.process_example_status）由 dict 模块提供。
 */

/** 特批操作所需权限（按钮显隐预判，最终鉴权由后端 ProcessResolver 兜底） */
const AUTH_EXAMPLE_ADMIN = ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_EXAMPLE_ADMIN']

/** 排序选项：仅创建时间（后端 monitorList normalizeSortField 白名单仅 createTime） */
const SORT_OPTIONS = [{ value: 'createTime', label: '创建时间' }]

/** 流程状态筛选选项（dict.process_example_status；筛选区 clearable，管理视角默认不限） */
const STATUS_OPTIONS = ref([])
/** 流程状态文本映射（dict.process_example_status） */
const STATUS_MAP = ref({})

const router = useRouter()

/** 特批操作按钮可见性（预判，最终权限由后端 ProcessResolver 兜底） */
const canAdmin = computed(() => hasAuth(AUTH_EXAMPLE_ADMIN))

/* ============ 所属模块选项 ============ */

const models = ref([])

async function loadModels() {
  try {
    const res = await queryProcessExampleModels()
    models.value = res.data || []
  } catch (error) {
    ElMessage.error(error.message || '所属模块加载失败')
  }
}

function modelLabel(fieldModelName) {
  const found = models.value.find((m) => m.fieldModelName === fieldModelName)
  return found ? found.name : fieldModelName || '-'
}

/* ============ 筛选 / 排序 / 列表 ============ */

const filters = reactive({
  fieldName: '',
  fieldCode: '',
  fieldModelName: null,
  fieldCreatorName: '',
  fieldStatus: null,
  exceptionOnly: false,
  inFlightOnly: false
})
const createTimeRange = ref(null)

const sortField = ref('createTime')
const sortOrder = ref('desc')

const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

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
    const range = createTimeRange.value || []
    const res = await queryProcessMonitorList({
      body: {
        fieldName: filters.fieldName || null,
        fieldCode: filters.fieldCode || null,
        fieldModelName: filters.fieldModelName || null,
        fieldCreatorName: filters.fieldCreatorName || null,
        fieldCreateTimeBegin: range[0] || null,
        fieldCreateTimeEnd: range[1] || null,
        fieldStatus: filters.fieldStatus || null,
        inFlightOnly: filters.inFlightOnly === true,
        exceptionOnly: filters.exceptionOnly === true
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

/* ============ 特批操作（复用 ProcessAdminOperateDialog，与实例详情页同一形态） ============ */

const adminDialogVisible = ref(false)
/** 当前特批目标实例数据（跳转候选节点 / 换人候选工作项），打开弹窗前按行 load 取数 */
const adminTarget = ref({ fieldId: '', nodes: [], workitems: [] })

/** 特批操作：先按行加载实例详情取候选数据（跳转节点/待处理工作项），成功后打开弹窗 */
async function openAdminOperate(row) {
  try {
    const res = await loadProcessExample(row.fieldId)
    const data = res.data || {}
    adminTarget.value = { fieldId: row.fieldId, nodes: data.nodes || [], workitems: data.workitems || [] }
    adminDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '流程实例加载失败')
  }
}

/* ============ 展示工具 ============ */

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function statusText(status) {
  return STATUS_MAP.value[status] || status || '-'
}

function statusTag(status) {
  switch (status) {
    case 'PROCESSING':
      return 'warning'
    case 'FINISH':
      return 'success'
    case 'DRAFT':
      return 'info'
    case 'PAUSE':
      return 'danger'
    case 'RETURN':
      return 'danger'
    case 'ABANDON':
      return 'info'
    default:
      return 'info'
  }
}

/* ============ 路由跳转（详情复用实例详情页，不另建监控详情页） ============ */

function goDetail(row) {
  router.push(`/processExample/detail/${row.fieldId}`)
}

/* ============ 初始化 ============ */

onMounted(() => {
  loadDictOptions()
  loadModels()
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

/** 加载流程状态选项与文本映射（dict.process_example_status）；失败时保持空选项，不阻塞页面 */
async function loadDictOptions() {
  try {
    const [options, textMap] = await Promise.all([
      getDictOptions('process_example_status'),
      getDictTextMap('process_example_status')
    ])
    STATUS_OPTIONS.value = options
    STATUS_MAP.value = textMap
  } catch {
    STATUS_OPTIONS.value = []
    STATUS_MAP.value = {}
  }
}
</script>

<script>
export default { name: 'ProcessMonitorView' }
</script>

<style scoped>
.page-card {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */
</style>

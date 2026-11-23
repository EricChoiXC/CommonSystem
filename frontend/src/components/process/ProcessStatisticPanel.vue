<template>
  <div class="statistic-panel">
    <!-- 管理员筛选区：一行四列（所属模块/模板/流程状态/创建时间区间）；我的模式无筛选区（UI 分册 §10.2） -->
    <el-form v-if="isAdmin" class="filter-form" :model="filters" label-width="80px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="所属模块">
            <el-select v-model="filters.fieldModelName" placeholder="请选择所属模块" clearable style="width: 100%">
              <el-option label="不限" value="" />
              <el-option v-for="m in models" :key="m.fieldModelName" :label="m.name" :value="m.fieldModelName" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <!-- 模板下拉按模块联动：模板列表接口以 field_model_name 为固定条件，未选模块时置灰 -->
          <el-form-item label="模板">
            <el-select
              v-model="filters.fieldTemplateId"
              :disabled="!filters.fieldModelName"
              :placeholder="filters.fieldModelName ? '请选择模板' : '请先选择所属模块'"
              clearable
              style="width: 100%"
            >
              <el-option label="不限" value="" />
              <el-option v-for="t in templateOptions" :key="t.fieldId" :label="t.fieldName" :value="t.fieldId" />
            </el-select>
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
      </el-row>
    </el-form>

    <div v-loading="loading" class="stat-body">
      <!-- 指标卡区：卡片下方小字注明统计口径（UI 分册 §10.3 口径核对要求） -->
      <div class="stat-cards" :class="{ 'is-my': !isAdmin }">
        <div v-for="card in cards" :key="card.label" class="stat-card">
          <div class="stat-card-label">{{ card.label }}</div>
          <div class="stat-card-value">{{ card.value }}</div>
          <div class="stat-card-caption">{{ card.caption }}</div>
        </div>
      </div>

      <!-- 聚合表区（仅管理员）：视图切换 + 查询按钮；契约只返回数量分布，
           各表列按接口返回字段渲染（模板/模块/状态分布，人员工作量 TopN 不分页） -->
      <template v-if="isAdmin">
        <div class="stat-toolbar">
          <el-radio-group v-model="activeView">
            <el-radio-button value="template">按模板</el-radio-button>
            <el-radio-button value="status">按状态</el-radio-button>
            <el-radio-button value="model">按模块</el-radio-button>
            <el-radio-button value="handler">人员工作量 TopN</el-radio-button>
          </el-radio-group>
          <el-button type="primary" @click="loadStatistic">查询</el-button>
        </div>

        <div v-if="activeView === 'handler'" class="stat-table-caption">
          口径：已办工作量 = 通过（HANDLER_PASS）审批留痕条数（同一工作项收回后再审批按多次计）；
          平均处理耗时 = 该处理人经办已完成实例的平均办结时长
        </div>
        <el-table v-if="activeView === 'template'" :data="templateCounts" border stripe>
          <template #empty>
            <el-empty description="暂无按模板统计数据" :image-size="80" />
          </template>
          <el-table-column label="模板名称" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.fieldTemplateName || row.fieldTemplateId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="count" label="流程数量" width="140" align="right" />
        </el-table>

        <el-table v-else-if="activeView === 'status'" :data="statusCounts" border stripe>
          <template #empty>
            <el-empty description="暂无按状态统计数据" :image-size="80" />
          </template>
          <el-table-column label="流程状态" width="160">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.fieldStatus)" size="small">{{ statusText(row.fieldStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="count" label="数量" width="140" align="right" />
          <el-table-column label="占比" width="140" align="right">
            <template #default="{ row }">{{ statusShare(row.count) }}</template>
          </el-table-column>
        </el-table>

        <el-table v-else-if="activeView === 'model'" :data="modelCounts" border stripe>
          <template #empty>
            <el-empty description="暂无按模块统计数据" :image-size="80" />
          </template>
          <el-table-column label="所属模块" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ modelLabel(row.fieldModelName) }}</template>
          </el-table-column>
          <el-table-column prop="count" label="流程数量" width="140" align="right" />
        </el-table>

        <el-table v-else :data="handlerWorkloads" border stripe>
          <template #empty>
            <el-empty description="暂无人员工作量数据" :image-size="80" />
          </template>
          <el-table-column label="处理人" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.fieldHandlerName || row.fieldHandlerId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="handledCount" label="已办工作量" width="140" align="right" />
          <el-table-column label="平均处理耗时" width="180" align="right">
            <template #default="{ row }">{{ formatDuration(row.avgDurationMs) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  queryProcessExampleModels,
  queryProcessStatisticAdmin,
  queryProcessStatisticMy,
  queryProcessTemplateList
} from '@/api/process'
import { getDictOptions, getDictTextMap } from '@/utils/dict'
import DateTimePicker from '@/components/DateTimePicker.vue'

/**
 * 流程统计面板（v1_7 P17-FE-009）：流程统计页（/process/statistic）与我的流程统计页
 * （/approval/statistic）共用组件，mode 区分两页口径（UI 分册 §10 布局冻结稿）。
 *
 * 统计页不是列表页三段式，而是「筛选区 → 指标卡 → 聚合表」布局：
 * - mode=admin：筛选区（所属模块/模板按模块联动/流程状态 dict.process_example_status/
 *   创建时间区间，空值提交 null）+ 四张指标卡（流程总数/在途流程/已办结/平均办结耗时，
 *   在途与已办结按 §10.3 口径由 statusCounts 分布求和派生，仅展示不做二次聚合查询）+
 *   聚合表（按模板/按状态/按模块/人员工作量 TopN，全部来自一次 admin 统计响应，只读无行操作）；
 * - mode=my：无筛选区，五张指标卡（今日发起/我的未办结/待审/今日已审/平均耗时），
 *   口径小字注明；分册 §10.2 示意的「我的流程明细」聚合小表不落——my 契约仅返回五项
 *   标量指标、无分布数组，不虚构数据（登记偏差）。
 *
 * 耗时展示：avgDurationMs 毫秒格式化为可读时长（≥1 分 → 「N天N小时N分」，不足 1 分 → 「N 秒」，
 * 0/无数据显示「-」，对应后端「无 FINISH 实例为 0」口径）；topN 固定传后端默认 10（分册 §10.1，
 * 不设前端 TopN 控件）；T-06 定稿本期不做指标卡下钻，卡片只读。
 * 依据 docs/设计文档/sys/process/流程v1_7前端UI设计分册.md §10、
 * docs/设计文档/sys/process/流程v1_7接口清单.md §7。
 */

const props = defineProps({
  /** 统计口径：admin=流程统计页（管理员），my=我的流程统计页（全员） */
  mode: {
    type: String,
    required: true,
    validator: (value) => ['admin', 'my'].includes(value)
  }
})

const isAdmin = computed(() => props.mode === 'admin')

/** 人员工作量 TopN：固定传后端默认值 10（接口清单 §7.1，缺省 10） */
const TOP_N = 10

/** 在途口径（UI 分册 §10.3）：状态 ∈ {DRAFT, PROCESSING, RETURN, PAUSE} */
const IN_FLIGHT_STATUSES = ['DRAFT', 'PROCESSING', 'RETURN', 'PAUSE']

/* ============ 统计数据 ============ */

const loading = ref(false)
const statistic = ref(null)

async function loadStatistic() {
  loading.value = true
  try {
    const res = isAdmin.value ? await queryProcessStatisticAdmin(buildQuery()) : await queryProcessStatisticMy()
    statistic.value = res.data || null
  } catch (error) {
    ElMessage.error(error.message || '统计数据加载失败')
    statistic.value = null
  } finally {
    loading.value = false
  }
}

/** 管理员统计查询体：筛选空值提交 null，topN 固定默认 10 */
function buildQuery() {
  const range = createTimeRange.value || []
  return {
    body: {
      fieldModelName: filters.fieldModelName || null,
      fieldTemplateId: filters.fieldTemplateId || null,
      fieldStatus: filters.fieldStatus || null,
      fieldCreateTimeBegin: range[0] || null,
      fieldCreateTimeEnd: range[1] || null,
      topN: TOP_N
    }
  }
}

/* ============ 管理员筛选（所属模块 / 模板联动 / 流程状态 / 时间区间） ============ */

const filters = reactive({
  fieldModelName: null,
  fieldTemplateId: null,
  fieldStatus: null
})
const createTimeRange = ref(null)

/** 流程状态筛选选项与文本映射（dict.process_example_status） */
const STATUS_OPTIONS = ref([])
const STATUS_MAP = ref({})

/** 所属模块选项（queryProcessExampleModels，与监控页同源） */
const models = ref([])

/** 模板选项（按所选模块联动加载，queryProcessTemplateList needPage=false 返回全部） */
const templateOptions = ref([])

async function loadFilterSources() {
  try {
    const [options, textMap, modelRes] = await Promise.all([
      getDictOptions('process_example_status'),
      getDictTextMap('process_example_status'),
      queryProcessExampleModels()
    ])
    STATUS_OPTIONS.value = options
    STATUS_MAP.value = textMap
    models.value = modelRes.data || []
  } catch (error) {
    ElMessage.error(error.message || '筛选选项加载失败')
  }
}

/** 模块变更联动：清空已选模板并按新模块重载模板选项（未选模块时不加载） */
watch(
  () => filters.fieldModelName,
  () => {
    filters.fieldTemplateId = null
    templateOptions.value = []
    if (filters.fieldModelName) loadTemplateOptions()
  }
)

async function loadTemplateOptions() {
  try {
    const res = await queryProcessTemplateList({
      body: { fieldModelName: filters.fieldModelName },
      pages: { pageNo: 1, pageSize: 1, needPage: false }
    })
    templateOptions.value = res.data || []
  } catch (error) {
    ElMessage.error(error.message || '模板选项加载失败')
    templateOptions.value = []
  }
}

function modelLabel(fieldModelName) {
  const found = models.value.find((m) => m.fieldModelName === fieldModelName)
  return found ? found.name : fieldModelName || '-'
}

/* ============ 聚合表视图切换（按模板 / 按状态 / 按模块 / 人员工作量 TopN） ============ */

const activeView = ref('template')

const templateCounts = computed(() => (statistic.value && statistic.value.templateCounts) || [])
const statusCounts = computed(() => (statistic.value && statistic.value.statusCounts) || [])
const modelCounts = computed(() => (statistic.value && statistic.value.modelCounts) || [])
const handlerWorkloads = computed(() => (statistic.value && statistic.value.handlerWorkloads) || [])

/** 按状态占比：以区间内实例总数为分母（总数为 0 时显示 0%） */
function statusShare(count) {
  const total = (statistic.value && statistic.value.totalCount) || 0
  if (!total || !count) return '0%'
  return `${((count / total) * 100).toFixed(1).replace(/\.0$/, '')}%`
}

/* ============ 指标卡（口径小字注明，UI 分册 §10.3） ============ */

/** 按状态数量映射（在途/已办结卡片的派生来源） */
const statusCountMap = computed(() => {
  const map = {}
  for (const item of statusCounts.value) {
    map[item.fieldStatus] = item.count
  }
  return map
})

const cards = computed(() => {
  if (isAdmin.value) {
    const data = statistic.value || {}
    const inFlight = IN_FLIGHT_STATUSES.reduce((sum, status) => sum + (statusCountMap.value[status] || 0), 0)
    return [
      { label: '流程总数', value: formatCount(data.totalCount), caption: '按创建时间统计，随筛选条件生效' },
      { label: '在途流程', value: formatCount(inFlight), caption: '含草稿/审批中/驳回/暂停' },
      { label: '已办结', value: formatCount(statusCountMap.value.FINISH), caption: '状态为完成（FINISH）' },
      {
        label: '平均办结耗时',
        value: formatDuration(data.avgDurationMs),
        caption: '创建→完成自然时长，仅计已完成实例'
      }
    ]
  }
  const data = statistic.value || {}
  return [
    { label: '今日发起', value: formatCount(data.todayCreatedCount), caption: '本人今日创建的流程实例数' },
    {
      label: '我的未办结',
      value: formatCount(data.myProcessingCount),
      caption: '本人发起且未办结（草稿/审批中/驳回/暂停）'
    },
    { label: '待审', value: formatCount(data.pendingApproveCount), caption: '待本人处理的审批工作项数' },
    { label: '今日已审', value: formatCount(data.todayApprovedCount), caption: '本人今日办结的工作项数' },
    { label: '平均耗时', value: formatDuration(data.avgDurationMs), caption: '本人参与且已办结实例的平均时长' }
  ]
})

/* ============ 展示工具 ============ */

/** 数量千分位展示（空值按 0） */
function formatCount(value) {
  return Number(value || 0).toLocaleString()
}

/**
 * 毫秒时长可读化：≥1 分钟 → 「N天N小时N分」（高位为 0 的单位省略），
 * 不足 1 分钟 → 「N 秒」；0/空显示「-」（对应后端无已完成实例为 0 的口径）
 */
function formatDuration(ms) {
  if (ms === null || ms === undefined || ms <= 0) return '-'
  const totalMinutes = Math.floor(ms / 60000)
  if (totalMinutes < 1) return `${Math.max(1, Math.round(ms / 1000))} 秒`
  const days = Math.floor(totalMinutes / 1440)
  const hours = Math.floor((totalMinutes % 1440) / 60)
  const minutes = totalMinutes % 60
  const parts = []
  if (days) parts.push(`${days}天`)
  if (hours) parts.push(`${hours}小时`)
  if (minutes || parts.length === 0) parts.push(`${minutes}分`)
  return parts.join('')
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

/* ============ 初始化 ============ */

onMounted(() => {
  if (isAdmin.value) loadFilterSources()
  loadStatistic()
})
// KeepAlive 页签切换返回时重新拉取统计（指标随时间变化，返回即刷新；首次进入由 onMounted 加载）
let firstActivation = true
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  loadStatistic()
})
</script>

<script>
export default { name: 'ProcessStatisticPanel' }
</script>

<style scoped>
/* 统计布局为统计页特有形态（非列表页三段式），筛选区复用 list-page.css 的 .filter-form，
   指标卡/切换工具条/口径说明为本页特有样式，scoped 内定义、不与公共样式重名 */
.statistic-panel {
  width: 100%;
}

.stat-body {
  min-height: 160px;
}

/* —— 指标卡区：管理员 4 卡 / 我的 5 卡，等宽自适应栅格 —— */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-cards.is-my {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.stat-card {
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}

.stat-card-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-card-value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.stat-card-caption {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* —— 聚合表切换工具条：左侧视图切换 + 右侧【查询】（UI 分册 §10.1 冻结示意） —— */
.stat-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 10px;
}

.stat-table-caption {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 窄屏降级：指标卡两列排布，避免数值挤压 */
@media (max-width: 992px) {
  .stat-cards,
  .stat-cards.is-my {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

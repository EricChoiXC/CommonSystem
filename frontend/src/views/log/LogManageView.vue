<template>
  <div class="log-manage">
    <!-- 日志类型切换：操作日志 / 登录日志（dict.log_type），默认操作日志，不可「全部」 -->
    <div class="type-switch">
      <el-radio-group v-model="filters.fieldLogType" @change="handleTypeChange">
        <el-radio-button v-for="opt in LOG_TYPE_OPTIONS" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 筛选条件区：按日志类型动态渲染，四分栅栏 -->
    <el-form class="filter-form" :model="filters" label-width="90px" @submit.prevent>
      <el-row :gutter="12">
        <template v-if="isOperation">
          <el-col :span="6">
            <el-form-item label="模块">
              <el-select v-model="filters.fieldModule" style="width: 100%" clearable>
                <el-option label="不限" value="" />
                <el-option v-for="opt in MODULE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="动作">
              <el-select v-model="filters.fieldAction" style="width: 100%" clearable>
                <el-option label="不限" value="" />
                <el-option v-for="opt in ACTION_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="操作人">
              <el-input v-model="filters.fieldUserName" placeholder="姓名" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="业务文档ID">
              <el-input v-model="filters.fieldBusinessId" placeholder="单据ID" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
        </template>
        <template v-else>
          <el-col :span="6">
            <el-form-item label="登录名">
              <el-input v-model="filters.fieldLoginName" placeholder="登录名" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
        </template>
        <el-col :span="6">
          <el-form-item label="结果">
            <el-select v-model="filters.fieldResult" style="width: 100%">
              <el-option label="不限" value="" />
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="isOperation ? 6 : 12">
          <el-form-item label="操作时间">
            <DateTimePicker
              v-model="filters.operateTimeRange"
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

    <!-- 工具区：左侧排序（仅操作时间）+ 右侧按钮 -->
    <div class="list-toolbar">
      <div class="sort-area">
        <span class="sort-item active" @click="toggleSort">
          操作时间
          <i class="sort-arrow">{{ sortOrder === 'desc' ? '↓' : '↑' }}</i>
        </span>
      </div>
      <div class="btn-area">
        <el-button type="primary" @click="handleQuery">查询</el-button>
      </div>
    </div>

    <!-- 列表区：日志只读展示，无编辑/删除；操作列提供详情 -->
    <el-table :data="list" v-loading="loading" border stripe row-key="fieldId">
      <template #empty>
        <el-empty description="暂无日志数据" :image-size="80" />
      </template>
      <el-table-column label="操作时间" width="170" align="center">
        <template #default="{ row }">{{ formatTime(row.fieldOperateTime) }}</template>
      </el-table-column>
      <template v-if="isOperation">
        <el-table-column prop="fieldUserName" label="操作人" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fieldUserName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="fieldModuleName" label="模块" width="110">
          <template #default="{ row }">{{ row.fieldModuleName || row.fieldModule || '-' }}</template>
        </el-table-column>
        <el-table-column label="动作" width="110">
          <template #default="{ row }">{{ actionText(row.fieldAction) }}</template>
        </el-table-column>
        <el-table-column prop="fieldBusinessId" label="业务文档ID" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fieldBusinessId || '-' }}</template>
        </el-table-column>
      </template>
      <template v-else>
        <el-table-column prop="fieldLoginName" label="登录名" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fieldLoginName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="fieldUserName" label="操作人" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fieldUserName || '-' }}</template>
        </el-table-column>
      </template>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.fieldResult === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ RESULT_TEXT[row.fieldResult] || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="fieldIp" label="IP" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fieldIp || '-' }}</template>
      </el-table-column>
      <el-table-column label="备注" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ isOperation ? (row.fieldRemark || '-') : (row.fieldFailReason || '-') }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">详情</el-button>
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
import { ElMessage } from 'element-plus'
import { queryLogList } from '@/api/log'
import { getDictOptions, getDictTextMap } from '@/utils/dict'
import DateTimePicker from '@/components/DateTimePicker.vue'

/**
 * 日志管理页（后台管理，SYS_LOG_ADMIN）：操作日志 / 登录日志查看。
 * 日志类型切换（默认操作日志，不可「全部」）；筛选区按类型动态渲染
 * （操作日志：模块/动作/操作人/业务文档ID；登录日志：登录名；公共：结果、操作时间范围）；
 * 排序仅「操作时间」维度（默认降序）；列表区操作列提供详情，只读无编辑/删除。
 */

/** 模块选项：与各模块 application.yml 的 MODEL_KEY 一致（model:name:map） */
const MODULE_OPTIONS = [
  { label: '组织架构', value: 'SYS-ORG' },
  { label: '附件', value: 'SYS-ATT' },
  { label: '通知待办', value: 'SYS-NOTIFY' },
  { label: '日志', value: 'SYS-LOG' }
]

/** 动作选项：与日志表结构文档 field_action 取值约定一致（dict.log_action，不含"全部"） */
const ACTION_OPTIONS = ref([])
/** 动作文本映射（dict.log_action） */
const ACTION_TEXT = ref({})
/** 日志类型切换选项（dict.log_type，不含"全部"） */
const LOG_TYPE_OPTIONS = ref([])
/** 日志结果文本映射（dict.log_result） */
const RESULT_TEXT = ref({})

const router = useRouter()

const filters = reactive({
  fieldLogType: '1', // 默认操作日志
  fieldModule: '',
  fieldAction: '',
  fieldUserName: '',
  fieldLoginName: '',
  fieldBusinessId: '',
  fieldResult: '',
  operateTimeRange: null
})

const isOperation = computed(() => filters.fieldLogType === '1')

const sortOrder = ref('desc') // 仅操作时间一个排序维度，默认降序
const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

function actionText(value) {
  return ACTION_TEXT.value[value] || value || '-'
}

/** 切换日志类型：重置筛选与分页后重新查询 */
function handleTypeChange() {
  filters.fieldModule = ''
  filters.fieldAction = ''
  filters.fieldUserName = ''
  filters.fieldLoginName = ''
  filters.fieldBusinessId = ''
  filters.fieldResult = ''
  filters.operateTimeRange = null
  queryPage.value = 1
  loadList()
}

/** 仅操作时间一个排序维度：点击切换升降序 */
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
    const body = { fieldLogType: filters.fieldLogType }
    if (isOperation.value) {
      body.fieldModule = filters.fieldModule || null
      body.fieldAction = filters.fieldAction || null
      body.fieldUserName = filters.fieldUserName || null
      body.fieldBusinessId = filters.fieldBusinessId || null
    } else {
      body.fieldLoginName = filters.fieldLoginName || null
    }
    body.fieldResult = filters.fieldResult || null
    const range = filters.operateTimeRange
    const res = await queryLogList({
      body,
      pages: { pageNo: queryPage.value, pageSize: queryPageSize.value, needPage: true },
      sortOrder: sortOrder.value,
      operateTimeStart: range && range[0] ? range[0] : null,
      operateTimeEnd: range && range[1] ? range[1] : null
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

function goDetail(row) {
  router.push(`/log/detail/${row.fieldId}`)
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

/** 加载日志类型切换、动作与结果码值（dict.log_type / log_action / log_result）；失败时保持兜底，不阻塞页面 */
async function loadDictOptions() {
  try {
    const [logTypeOptions, actionOptions, actionTextMap, resultTextMap] = await Promise.all([
      getDictOptions('log_type'),
      getDictOptions('log_action'),
      getDictTextMap('log_action'),
      getDictTextMap('log_result')
    ])
    LOG_TYPE_OPTIONS.value = logTypeOptions
    ACTION_OPTIONS.value = actionOptions
    ACTION_TEXT.value = actionTextMap
    RESULT_TEXT.value = resultTextMap
  } catch {
    LOG_TYPE_OPTIONS.value = []
    ACTION_OPTIONS.value = []
    ACTION_TEXT.value = {}
    RESULT_TEXT.value = {}
  }
}
</script>

<script>
export default { name: 'LogManageView' }
</script>

<style scoped>
.log-manage {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */

.type-switch {
  margin-bottom: 10px;
}
</style>

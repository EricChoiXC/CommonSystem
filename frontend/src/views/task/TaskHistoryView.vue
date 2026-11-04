<template>
  <div class="task-history">
    <!-- 筛选条件区：任务名称、结果、开始时间（范围）、结束时间（范围），四分栅栏 -->
    <el-form class="filter-form" :model="filters" label-width="100px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="任务名称">
            <el-input v-model="filters.fieldTaskName" placeholder="任务名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="结果">
            <el-select v-model="filters.fieldResult" style="width: 100%">
              <el-option label="不限" value="" />
              <el-option v-for="opt in RESULT_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="开始时间">
            <DateTimePicker
              v-model="filters.startTimeRange"
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
          <el-form-item label="结束时间">
            <DateTimePicker
              v-model="filters.endTimeRange"
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

    <!-- 工具区：左侧排序（开始时间/结束时间，单条件）+ 右侧按钮 -->
    <div class="list-toolbar">
      <div class="sort-area">
        <span
          :class="['sort-item', sortField === 'startTime' ? 'active' : '']"
          @click="toggleSort('startTime')"
        >
          开始时间
          <i v-if="sortField === 'startTime'" class="sort-arrow">{{ sortOrder === 'desc' ? '↓' : '↑' }}</i>
        </span>
        <span
          :class="['sort-item', sortField === 'endTime' ? 'active' : '']"
          @click="toggleSort('endTime')"
        >
          结束时间
          <i v-if="sortField === 'endTime'" class="sort-arrow">{{ sortOrder === 'desc' ? '↓' : '↑' }}</i>
        </span>
      </div>
      <div class="btn-area">
        <el-button type="primary" @click="handleQuery">查询</el-button>
      </div>
    </div>

    <!-- 列表区：任务名/结果/开始时间/结束时间 + 操作列（详情） -->
    <el-table :data="list" v-loading="loading" border stripe row-key="fieldId">
      <el-table-column prop="fieldTaskName" label="任务名" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fieldTaskName || '-' }}</template>
      </el-table-column>
      <el-table-column label="结果" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.fieldResult === '1' ? 'success' : 'danger'" size="small">
            {{ RESULT_TEXT[row.fieldResult] || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="170">
        <template #default="{ row }">{{ formatTime(row.fieldStartTime) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" width="170">
        <template #default="{ row }">{{ formatTime(row.fieldEndTime) }}</template>
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
import { onActivated, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { queryTaskHistoryList } from '@/api/task'
import { getDictOptions, getDictTextMap } from '@/utils/dict'
import DateTimePicker from '@/components/DateTimePicker.vue'

/**
 * 执行记录页（后台管理-定时任务，SYS_TASK_ADMIN）：
 * 筛选区（任务名称/结果/开始时间范围/结束时间范围）；
 * 排序区仅 开始时间 / 结束时间 两个维度（单条件，默认开始时间降序）；
 * 列表区操作列提供详情，只读。
 * 结果选项与文本（dict.task_result）由 dict 模块提供。
 */
const router = useRouter()

/** 结果筛选选项（dict.task_result；"全部"项在模板中提供） */
const RESULT_OPTIONS = ref([])
/** 结果文本映射（dict.task_result） */
const RESULT_TEXT = ref({})

const filters = reactive({
  fieldTaskName: '',
  fieldResult: '', // '' = 全部
  startTimeRange: null,
  endTimeRange: null
})

const sortField = ref('startTime') // startTime / endTime，默认开始时间降序
const sortOrder = ref('desc')
const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

/** 单条件排序：点击未选中项选中并默认降序；点击已选中项切换升降序 */
function toggleSort(field) {
  if (sortField.value === field) {
    sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  } else {
    sortField.value = field
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
    const startRange = filters.startTimeRange
    const endRange = filters.endTimeRange
    const res = await queryTaskHistoryList({
      body: {
        fieldTaskName: filters.fieldTaskName || null,
        fieldResult: filters.fieldResult || null,
        fieldStartTimeStart: startRange && startRange[0] ? startRange[0] : null,
        fieldStartTimeEnd: startRange && startRange[1] ? startRange[1] : null,
        fieldEndTimeStart: endRange && endRange[0] ? endRange[0] : null,
        fieldEndTimeEnd: endRange && endRange[1] ? endRange[1] : null
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

function goDetail(row) {
  router.push(`/taskHistory/detail/${row.fieldId}`)
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

/** 加载执行结果选项与文本映射（dict.task_result）；失败时保持空选项，不阻塞页面 */
async function loadDictOptions() {
  try {
    const [options, textMap] = await Promise.all([
      getDictOptions('task_result'),
      getDictTextMap('task_result')
    ])
    RESULT_OPTIONS.value = options
    RESULT_TEXT.value = textMap
  } catch {
    RESULT_OPTIONS.value = []
    RESULT_TEXT.value = {}
  }
}
</script>

<script>
export default { name: 'TaskHistoryView' }
</script>

<style scoped>
.task-history {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */
</style>

<template>
  <div class="calendar-view">
    <!-- ============ 日历区 ============ -->
    <el-card shadow="never" class="calendar-card">
      <template #header>
        <div class="calendar-header">
          <span class="calendar-title">日程</span>
          <el-button type="primary" size="small" @click="goCreate">创建日程</el-button>
        </div>
      </template>
      <el-calendar v-model="currentDate" @change="handleMonthChange">
        <template #date-cell="{ data }">
          <div class="calendar-cell" @click="handleDateClick(data)">
            <span class="calendar-day">{{ data.day.split('-')[2] }}</span>
            <div v-if="monthEvents[data.day]" class="calendar-event-dot" :class="{ 'team-event': monthEvents[data.day].some(e => e.fieldType === 'TEAM') }">
              <span class="event-count">{{ monthEvents[data.day].length }} 项</span>
            </div>
          </div>
        </template>
      </el-calendar>
    </el-card>

    <!-- ============ 列表区 ============ -->
    <el-card shadow="never" class="list-card">
      <template #header>
        <span class="list-title">日程列表</span>
      </template>

      <!-- 筛选条件区 -->
      <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="标题">
              <el-input v-model="filters.fieldName" placeholder="请输入标题" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="类型">
              <el-select v-model="filters.fieldType" placeholder="全部" clearable style="width: 100%">
                <el-option label="不限" value="" />
                <el-option label="个人日程" value="PERSONAL" />
                <el-option label="团队日程" value="TEAM" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="filters.fieldType === 'TEAM'">
            <el-form-item label="是否接收">
              <el-select v-model="filters.fieldAccept" placeholder="全部" clearable style="width: 100%">
                <el-option label="不限" value="" />
                <el-option label="已接收" value="ACCEPTED" />
                <el-option label="未接收" value="REJECTED" />
                <el-option label="未确认" value="UNCONFIRMED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="开始时间">
              <DateTimePicker
                v-model="filters.fieldStartTimeStart"
                calendar-type="date"
                placeholder="选择日期"
                style="width: 100%"
                date-format="YYYY-MM-DD"
                :default-value="today"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 工具区 -->
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
          <el-button type="success" @click="goCreate">添加日程</el-button>
        </div>
      </div>

      <!-- 列表区 -->
      <el-table :data="list" v-loading="loading" border stripe row-key="fieldId">
        <el-table-column prop="fieldName" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="fieldType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.fieldType === 'TEAM' ? 'warning' : 'info'" size="small">
              {{ row.fieldType === 'TEAM' ? '团队日程' : '个人日程' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fieldStartTime" label="日程开始时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.fieldStartTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="fieldEndTime" label="日程结束时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.fieldEndTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="goDetail(row.fieldId)">详情</el-button>
            <el-button v-if="isCreator(row)" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, prev, pager, next, sizes"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onActivated, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { queryCalendarList, queryMonthCalendar, deleteCalendar } from '@/api/calendar'
import { useUserStore } from '@/stores/user'
import DateTimePicker from '@/components/DateTimePicker.vue'

const router = useRouter()
const userStore = useUserStore()

const today = new Date()
const currentDate = ref(today)
const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const sortField = ref('createTime')
const sortOrder = ref('desc')

/** 当月日程缓存 { '2026-08-15': [{fieldId, fieldName, fieldType}] } */
const monthEvents = reactive({})

const SORT_OPTIONS = [
  { label: '创建时间', value: 'createTime' },
  { label: '日程开始时间', value: 'startTime' }
]

const filters = reactive({
  fieldName: '',
  fieldType: '',
  fieldAccept: undefined,
  fieldStartTimeStart: formatDate(today),
  fieldStartTimeEnd: ''
})

/** 格式化日期为 YYYY-MM-DD */
function formatDate(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** 格式化日期时间为 YYYY-MM-DD HH:mm */
function formatDateTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${h}:${min}`
}

function isCreator(row) {
  const user = userStore.userInfo
  return user && row.fieldCreatorId === user.fieldId
}

function toggleSort(field) {
  if (sortField.value === field) {
    sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  } else {
    sortField.value = field
    sortOrder.value = 'desc'
  }
  handleQuery()
}

/** 加载当月日程到日历 */
async function loadMonthEvents(year, month) {
  try {
    const res = await queryMonthCalendar({ body: { year, month } })
    const data = res.data || []
    // 清除当月缓存
    for (const key of Object.keys(monthEvents)) {
      const parts = key.split('-')
      if (parseInt(parts[0]) === year && parseInt(parts[1]) === month) {
        delete monthEvents[key]
      }
    }
    // 按日期分组
    for (const item of data) {
      if (!item.fieldStartTime) continue
      const d = new Date(item.fieldStartTime)
      const key = formatDate(d)
      if (!monthEvents[key]) monthEvents[key] = []
      monthEvents[key].push(item)
    }
  } catch {
    // 静默失败
  }
}

function handleMonthChange(date) {
  const d = new Date(date)
  loadMonthEvents(d.getFullYear(), d.getMonth() + 1)
}

function handleDateClick(data) {
  // 点击选中日期，将日期带入日程列表查询条件并查询
  filters.fieldStartTimeStart = data.day
  handleQuery()
}

async function handleQuery() {
  pageNo.value = 1
  await fetchList()
}

async function fetchList() {
  loading.value = true
  try {
    const data = {
      body: {
        fieldName: filters.fieldName || undefined,
        fieldType: filters.fieldType || undefined,
        fieldAccept: filters.fieldAccept || undefined,
        fieldStartTimeStart: filters.fieldStartTimeStart || undefined,
        fieldStartTimeEnd: filters.fieldStartTimeEnd || undefined
      },
      pages: { pageNo: pageNo.value, pageSize: pageSize.value, needPage: true },
      sortField: sortField.value,
      sortOrder: sortOrder.value
    }
    const res = await queryCalendarList(data)
    list.value = res.data || []
    total.value = (res.metadata && res.metadata.total) || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSizeChange(size) {
  pageSize.value = size
  pageNo.value = 1
  fetchList()
}

function handlePageChange(page) {
  pageNo.value = page
  fetchList()
}

function goCreate() {
  // 从日历打开新建日程时，带出当前选中的日期自动填充开始/结束时间
  router.push({ path: '/calendar/create', query: { startDate: formatDate(new Date(currentDate.value)) } })
}

function goDetail(fieldId) {
  router.push('/calendar/detail/' + fieldId)
}

function handleDelete(row) {
  ElMessageBox.confirm('确定删除该日程吗？', '确认', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteCalendar(row.fieldId)
      ElMessage.success('删除成功')
      await fetchList()
      // 刷新日历
      const d = new Date()
      await loadMonthEvents(d.getFullYear(), d.getMonth() + 1)
    } catch (e) {
      ElMessage.error(e.message || '删除失败')
    }
  }).catch(() => {})
}

onMounted(() => {
  const d = new Date()
  loadMonthEvents(d.getFullYear(), d.getMonth() + 1)
  fetchList()
})

// KeepAlive 页签切换返回时刷新当前查看月份的日程与列表（保留月历视图状态）
let firstActivation = true
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  const d = new Date(currentDate.value)
  loadMonthEvents(d.getFullYear(), d.getMonth() + 1)
  fetchList()
})
</script>

<script>
export default { name: 'CalendarView' }
</script>

<style scoped>
.calendar-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.calendar-card {
  width: 100%;
}

.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.calendar-title {
  font-size: 15px;
  font-weight: 600;
}

.calendar-cell {
  cursor: pointer;
  text-align: center;
}

.calendar-day {
  font-size: 14px;
}

.calendar-event-dot {
  margin-top: 2px;
  font-size: 11px;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 4px;
  padding: 1px 4px;
  display: inline-block;
}

.calendar-event-dot.team-event {
  color: #e6a23c;
  background: #fdf6ec;
}

.event-count {
  white-space: nowrap;
}

.list-card {
  width: 100%;
}

.list-title {
  font-size: 15px;
  font-weight: 600;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
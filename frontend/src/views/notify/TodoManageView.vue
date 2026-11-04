<template>
  <div class="todo-manage">
    <!-- 筛选条件区：待办类型 | 来源模块（一行 2 个），标题 | 创建时间（2 栅栏宽） -->
    <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="待办类型">
            <el-select v-model="filters.fieldNotifyType" style="width: 100%">
              <el-option v-for="opt in NOTIFY_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
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
        <el-col :span="12">
          <el-form-item label="标题">
            <el-input v-model="filters.fieldName" placeholder="请输入标题" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="创建时间">
            <DateTimePicker
              v-model="createTimeRange"
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

    <!-- 工具区：左侧排序（创建时间），右侧仅查询（文档要求按钮区无批量/创建） -->
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

    <!-- 列表区：按 sys_notify_todo 表可见所有待办；无批量操作不设复选框列 -->
    <el-table :data="list" v-loading="loading" border stripe row-key="fieldId">
      <template #empty>
        <el-empty description="暂无待办数据" :image-size="80" />
      </template>
      <el-table-column prop="fieldName" label="标题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="fieldModelName" label="来源模块" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fieldModelName || '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170" align="center">
        <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
      </el-table-column>
      <el-table-column prop="fieldReceiveCount" label="接收人数" width="100" align="right" />
      <el-table-column prop="fieldDoneCount" label="已办人数" width="100" align="right" />
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
import { ElMessage } from 'element-plus'
import { queryAdminTodoList, queryModelOptions } from '@/api/notify'
import { getDictOptions } from '@/utils/dict'
import DateTimePicker from '@/components/DateTimePicker.vue'

/**
 * 待办管理页（后台管理，SYS_NOTIFY_ADMIN）：按 sys_notify_todo 表可见所有待办。
 * 筛选：待办类型（默认待办，无"全部"）/ 来源模块 / 标题（2栅栏宽）/ 创建时间（范围，限年月日，2栅栏宽）；
 * 排序：创建时间；按钮区：无（仅查询）；列表：标题、来源模块、创建时间、接收人数、已办人数。
 * 待办类型选项（dict.notify_type）由 dict 模块提供。
 */
const SORT_OPTIONS = [{ value: 'createTime', label: '创建时间' }]

/** 待办类型下拉选项（dict.notify_type，不含"全部"） */
const NOTIFY_TYPE_OPTIONS = ref([])

/** 来源模块选项（读取 model:name:map 缓存，value=MODEL_KEY，label=MODEL_NAME） */
const modelOptions = ref([])

const filters = reactive({
  fieldNotifyType: '1', // 默认待办，不可"全部"
  fieldModelName: '',
  fieldName: ''
})
const createTimeRange = ref([])

const sortField = ref('createTime') // 默认创建时间
const sortOrder = ref('desc') // 默认降序

const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

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
    const [start, end] = createTimeRange.value || []
    const res = await queryAdminTodoList({
      body: {
        fieldNotifyType: filters.fieldNotifyType,
        fieldModelName: filters.fieldModelName || null,
        fieldName: filters.fieldName || null,
        fieldCreateTimeStart: start || null,
        fieldCreateTimeEnd: end || null
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

/** 加载待办类型选项（dict.notify_type）；失败时保持空选项，不阻塞页面 */
async function loadDictOptions() {
  try {
    NOTIFY_TYPE_OPTIONS.value = await getDictOptions('notify_type')
  } catch {
    NOTIFY_TYPE_OPTIONS.value = []
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
export default { name: 'TodoManageView' }
</script>

<style scoped>
.todo-manage {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */
</style>

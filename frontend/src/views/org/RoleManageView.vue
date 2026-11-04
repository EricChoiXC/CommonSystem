<template>
  <div class="role-manage">
    <!-- 筛选条件区：四分栅栏排列（角色名 | 编码 | 是否启用） -->
    <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="角色名">
            <el-input v-model="filters.fieldName" placeholder="请输入角色名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="编码">
            <el-input v-model="filters.fieldCode" placeholder="请输入编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="是否启用">
            <el-select v-model="filters.enable" style="width: 100%">
              <el-option v-for="opt in ENABLE_OPTIONS" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 工具区：左侧排序 + 右侧按钮（无批量操作按钮） -->
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
        <el-button type="success" @click="goCreate">创建</el-button>
      </div>
    </div>

    <!-- 列表区：无复选框列（禁止批量操作），末列操作按钮 -->
    <el-table :data="list" v-loading="loading" border stripe row-key="fieldId">
      <template #empty>
        <el-empty description="暂无角色数据" :image-size="80" />
      </template>
      <el-table-column prop="fieldName" label="角色名" min-width="140" show-overflow-tooltip />
      <el-table-column prop="fieldCode" label="编码" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fieldCode || '-' }}</template>
      </el-table-column>
      <el-table-column prop="fieldCreateTime" label="创建时间" width="170" align="center">
        <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
      </el-table-column>
      <el-table-column prop="fieldUpdateTime" label="更新时间" width="170" align="center">
        <template #default="{ row }">{{ formatTime(row.fieldUpdateTime) }}</template>
      </el-table-column>
      <el-table-column prop="fieldEnable" label="是否启用" width="100">
        <template #default="{ row }">
          <el-tag :type="row.fieldEnable ? 'success' : 'danger'" size="small">
            {{ YES_NO_TEXT[row.fieldEnable] || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="goEdit(row)">编辑</el-button>
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
import { queryRoleList } from '@/api/org'
import { getDictOptions, getDictTextMap } from '@/utils/dict'

/**
 * 角色管理页：SysOrgRole 列表分页查询。
 * 筛选项为角色名/编码/是否启用（默认「是」）；显示项为角色名、编码、创建时间、更新时间、是否启用；
 * 禁止批量操作按钮（无复选框列、无批量按钮）；新建/编辑跳转独立路由
 * （/role/create、/role/edit/:id）。
 * 是否启用选项与文本（dict.yes_no）由 dict 模块提供。
 */

/** 是否启用筛选（dict.yes_no，boolean 值；null=全部） */
const ENABLE_OPTIONS = ref([{ label: '不限', value: null }])
/** 是否启用文本映射（dict.yes_no） */
const YES_NO_TEXT = ref({})
const SORT_OPTIONS = [
  { value: 'createTime', label: '创建时间' },
  { value: 'updateTime', label: '更新时间' },
  { value: 'code', label: '编码' }
]

const router = useRouter()

const filters = reactive({
  fieldName: '',
  fieldCode: '',
  enable: true // 默认「是」
})

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
    const res = await queryRoleList({
      body: {
        fieldName: filters.fieldName || null,
        fieldCode: filters.fieldCode || null,
        enable: filters.enable
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

function goCreate() {
  router.push('/role/create')
}

function goEdit(row) {
  router.push(`/role/edit/${row.fieldId}`)
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

/** 加载是否启用选项与文本映射（dict.yes_no）；失败时保留「全部」兜底 */
async function loadDictOptions() {
  try {
    const [options, textMap] = await Promise.all([
      getDictOptions('yes_no', { boolean: true }),
      getDictTextMap('yes_no', { boolean: true })
    ])
    ENABLE_OPTIONS.value = [{ label: '不限', value: null }, ...options]
    YES_NO_TEXT.value = textMap
  } catch {
    ENABLE_OPTIONS.value = [{ label: '不限', value: null }]
    YES_NO_TEXT.value = {}
  }
}
</script>

<script>
export default { name: 'RoleManageView' }
</script>

<style scoped>
.role-manage {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */
</style>

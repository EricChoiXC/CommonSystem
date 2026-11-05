<template>
  <div class="group-manage">
    <!-- 筛选条件区：四分栅栏排列（名称 | 类型 | 是否全员可见，一行四列放三项） -->
    <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="名称">
            <el-input v-model="filters.fieldName" placeholder="请输入名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="类型">
            <el-select v-model="filters.fieldType" style="width: 100%">
              <el-option label="不限" :value="null" />
              <el-option v-for="opt in TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="是否全员可见">
            <el-select v-model="filters.fieldIsAllViewer" style="width: 100%">
              <el-option v-for="opt in ALL_VIEWER_OPTIONS" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 工具区：左侧排序 + 右侧按钮（创建对所有登录用户可见；删除为批量，无选中禁用） -->
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
        <el-button type="danger" plain :disabled="!selectedRows.length" @click="handleDeleteBatch">删除</el-button>
      </div>
    </div>

    <!-- 列表区：首列复选框（批量删除）、末列操作按钮（详情始终显示，编辑/删除按 canManage 显隐） -->
    <el-table
      :data="list"
      v-loading="loading"
      border
      stripe
      row-key="fieldId"
      @selection-change="handleSelectionChange"
    >
      <template #empty>
        <el-empty description="暂无群组数据" :image-size="80" />
      </template>
      <el-table-column type="selection" width="45" />
      <el-table-column prop="fieldName" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="fieldType" label="类型" width="90">
        <template #default="{ row }">{{ TYPE_TEXT[row.fieldType] || row.fieldType || '-' }}</template>
      </el-table-column>
      <el-table-column prop="userCount" label="成员数" width="90" align="center">
        <template #default="{ row }">{{ row.userCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column prop="fieldOwnerName" label="所有人" min-width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fieldOwnerName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="fieldIsAllViewer" label="全员可见" width="90">
        <template #default="{ row }">{{ YES_NO_TEXT[row.fieldIsAllViewer] || '-' }}</template>
      </el-table-column>
      <el-table-column prop="fieldEnable" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.fieldEnable ? 'success' : 'danger'" size="small">
            {{ YES_NO_TEXT[row.fieldEnable] || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="fieldCreateTime" label="创建时间" width="170" align="center">
        <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">详情</el-button>
          <el-button v-if="canManage(row)" link type="primary" @click="goEdit(row)">编辑</el-button>
          <el-button v-if="canManage(row)" link type="danger" @click="handleDelete(row)">删除</el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteGroup, queryGroupList } from '@/api/org'
import { getDictOptions, getDictTextMap } from '@/utils/dict'
import { hasAuth } from '@/utils/auth'
import { useUserStore } from '@/stores/user'

/**
 * 群组管理列表页（/org/group，02-群组功能分析.md 3.2 / 06 清单 4.16）。
 * 三段式列表页（无左侧树）：筛选区（名称/类型/是否全员可见）→ 工具区（单条件排序 +
 * 查询/创建/批量删除）→ 列表区（复选框 + 名称/类型/成员数/所有人/全员可见/状态/创建时间 + 操作列）。
 * 列表数据范围由后端按可见性 SQL（02 文档 2.2）过滤，前端不二次过滤；
 * 行级维护权限 canManage 与后端 checkMaintainable（02 文档 2.3）同口径：
 * 管理员（SYS_ORG_GROUP_ADMIN / SYS_ORG_ADMIN，SYS_ADMIN 由 hasAuth 放行）可维护
 * 全部公共群组与自己的个人群组，普通用户仅可维护自己的个人群组；「详情」始终显示。
 * 创建按钮对所有登录用户可见（普通用户进表单后由表单页/后端锁定个人类型，列表页不做差异）；
 * 批量删除按钮对所有用户显示，仅对勾选中可管理的行生效，无权限行跳过并在确认与结果中提示。
 * 新建/编辑/查看跳转独立路由（/org/group/create、/org/group/edit/:id、/org/group/detail/:id，
 * 路由与菜单由后续任务接入）。
 */

/** 群组类型筛选（dict.org_group_type，字符串码值 COMMON/PERSONAL） */
const TYPE_OPTIONS = ref([])
/** 群组类型文本映射（dict.org_group_type） */
const TYPE_TEXT = ref({})
/** 是否全员可见筛选（dict.yes_no，boolean 值；null=不限） */
const ALL_VIEWER_OPTIONS = ref([{ label: '不限', value: null }])
/** 是否启用/全员可见文本映射（dict.yes_no） */
const YES_NO_TEXT = ref({})
const SORT_OPTIONS = [
  { value: 'createTime', label: '创建时间' },
  { value: 'updateTime', label: '更新时间' },
  { value: 'name', label: '名称' }
]

/** 管理员权限（与后端 group 鉴权一致）：群组管理员 / 模块管理员 */
const AUTH_GROUP_ADMIN = ['SYS_ORG_GROUP_ADMIN', 'SYS_ORG_ADMIN']

const router = useRouter()
const userStore = useUserStore()

const filters = reactive({
  fieldName: '',
  fieldType: null, // 默认「不限」
  fieldIsAllViewer: null // 默认「不限」
})

const sortField = ref('createTime') // 默认创建时间
const sortOrder = ref('desc') // 默认降序

const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)
const selectedRows = ref([])

/** 行级维护权限（预判，最终权限由后端 SysOrgGroupService#checkMaintainable 兜底） */
function canManage(row) {
  if (hasAuth(AUTH_GROUP_ADMIN)) return true
  const currentId = userStore.userInfo && userStore.userInfo.fieldId
  return row.fieldType === 'PERSONAL' && !!currentId && row.fieldOwnerId === currentId
}

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

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function loadList() {
  loading.value = true
  try {
    const res = await queryGroupList({
      body: {
        fieldName: filters.fieldName || null,
        fieldType: filters.fieldType,
        fieldIsAllViewer: filters.fieldIsAllViewer
      },
      pages: { pageNo: queryPage.value, pageSize: queryPageSize.value, needPage: true },
      sortField: sortField.value,
      sortOrder: sortOrder.value
    })
    list.value = res.data || []
    total.value = (res.metadata && res.metadata.total) || list.value.length
  } catch (error) {
    // 拦截器已统一提取 message；此处仅兜底提示并清空列表，不静默吞错
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

/* ============ 路由跳转（新建/编辑/查看，路由由后续任务接入） ============ */

function goCreate() {
  router.push('/org/group/create')
}

function goDetail(row) {
  router.push(`/org/group/detail/${row.fieldId}`)
}

function goEdit(row) {
  router.push(`/org/group/edit/${row.fieldId}`)
}

/* ============ 删除 / 批量删除（物理删除，二次确认） ============ */

/** 单条删除：删除按钮仅 canManage 的行渲染；成功后刷新当前页 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除群组「${row.fieldName}」？该操作不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除'
    })
  } catch {
    return
  }
  try {
    await deleteGroup(row.fieldId)
    ElMessage.success('删除成功')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

/** 批量删除：仅对勾选中 canManage 的行生效，无权限行跳过；逐条调用后统一刷新（照 OrgManageView#handleInvalid） */
async function handleDeleteBatch() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选要删除的群组')
    return
  }
  const deletableRows = selectedRows.value.filter((row) => canManage(row))
  const skippedCount = selectedRows.value.length - deletableRows.length
  if (!deletableRows.length) {
    ElMessage.warning('选中的群组均无删除权限（公共群组仅群组管理员可删，个人群组仅所有人可删）')
    return
  }
  const names = deletableRows.map((r) => r.fieldName).join('、')
  const skipNote = skippedCount ? `（另有 ${skippedCount} 条无权限记录将被跳过）` : ''
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${deletableRows.length} 条群组（${names}）？${skipNote}该操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除' }
    )
  } catch {
    return
  }
  let successCount = 0
  const failedNames = []
  for (const row of deletableRows) {
    try {
      await deleteGroup(row.fieldId)
      successCount += 1
    } catch (error) {
      failedNames.push(`${row.fieldName}（${error.message || '未知错误'}）`)
    }
  }
  selectedRows.value = []
  loadList()
  if (failedNames.length) {
    ElMessage.warning(`删除：成功 ${successCount} 条，失败 ${failedNames.length} 条（${failedNames.join('、')}）`)
  } else {
    ElMessage.success(`删除完成，共 ${successCount} 条`)
  }
}

/* ============ 初始化 ============ */

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

/** 加载码值选项与文本映射（org_group_type / yes_no）；失败时保持「不限」兜底，不阻塞页面 */
async function loadDictOptions() {
  try {
    const [typeOptions, typeText, allViewerOptions, yesNoText] = await Promise.all([
      getDictOptions('org_group_type'),
      getDictTextMap('org_group_type'),
      getDictOptions('yes_no', { boolean: true }),
      getDictTextMap('yes_no', { boolean: true })
    ])
    TYPE_OPTIONS.value = typeOptions
    TYPE_TEXT.value = typeText
    ALL_VIEWER_OPTIONS.value = [{ label: '不限', value: null }, ...allViewerOptions]
    YES_NO_TEXT.value = yesNoText
  } catch {
    // 码值加载失败：筛选保留「不限」兜底，类型文本回退原值
  }
}
</script>

<script>
export default { name: 'GroupManageView' }
</script>

<style scoped>
.group-manage {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */
</style>

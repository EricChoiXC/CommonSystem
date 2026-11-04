<template>
  <div class="org-manage" ref="containerRef">
    <!-- ============ 左侧：组织架构树（仅机构/部门节点） ============ -->
    <div class="org-left" :style="{ width: leftWidth + '%' }">
      <el-tree
        :key="treeKey"
        ref="treeRef"
        class="org-tree"
        :props="treeProps"
        lazy
        :load="loadTree"
        node-key="fieldId"
        :default-expanded-keys="defaultExpandedKeys"
        highlight-current
        @node-click="handleTreeNodeClick"
      >
        <template #default="{ data }">
          <span class="tree-node">
            <el-icon class="tree-node-icon" :class="nodeIconClass(data)">
              <component :is="nodeIcon(data)" />
            </el-icon>
            <span class="tree-node-label">{{ data.fieldName }}</span>
          </span>
        </template>
      </el-tree>
    </div>

    <!-- ============ 分栏拖拽条 ============ -->
    <div class="org-divider" @mousedown="startDrag" />

    <!-- ============ 右侧：筛选 + 排序/按钮 + 列表 ============ -->
    <div class="org-right">
      <!-- 筛选条件区：四分栅栏排列 -->
      <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="名称">
              <el-input v-model="filters.fieldName" placeholder="请输入名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="编号">
              <el-input v-model="filters.fieldCode" placeholder="请输入编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="类型">
              <el-select v-model="filters.orgType" style="width: 100%">
                <el-option label="不限" :value="null" />
                <el-option v-for="opt in ORG_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="启用状态">
              <el-select v-model="filters.enable" placeholder="全部" style="width: 100%">
                <el-option v-for="opt in ENABLE_OPTIONS" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 中间区：左侧排序 + 右侧按钮 -->
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
          <el-button v-if="canCreate" type="success" @click="goCreate">创建</el-button>
          <el-button v-if="canCreate" type="danger" plain @click="handleInvalid">置为无效</el-button>
        </div>
      </div>

      <!-- 列表区：首列复选框、末列操作按钮 -->
      <el-table
        :data="list"
        v-loading="loading"
        border
        stripe
        row-key="fieldId"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" min-width="110" show-overflow-tooltip>
          <template v-if="col.type" #default="{ row }">
            <span>{{ formatCell(col.type, row[col.prop], row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="canEdit ? 170 : 90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">详情</el-button>
            <el-button v-if="canEdit" link type="primary" @click="goEdit(row)">编辑</el-button>
            <el-button v-if="canEdit" link :type="row.fieldEnable ? 'danger' : 'success'" @click="handleToggleEnable(row)">
              {{ row.fieldEnable ? '停用' : '启用' }}
            </el-button>
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
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onActivated, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { OfficeBuilding, School, HomeFilled } from '@element-plus/icons-vue'
import { hasAuth } from '@/utils/auth'
import { getDictOptions, getDictTextMap } from '@/utils/dict'
import {
  deleteElement,
  enableElement,
  queryAddressBookTree,
  queryElementList
} from '@/api/org'

/**
 * 组织架构管理页：左侧树（机构/部门）+ 右侧下级列表维护。
 * 右侧列表按点中的树节点（根节点时 field_parent_id 为空）查询直接下级，
 * 支持名称/编号/类型/启用状态筛选、单条件排序（创建时间/更新时间/编码）与分页；
 * 列表列按查询类型动态展示，首列复选框，末列提供详情/编辑/停用启用操作；
 * 新建/编辑/查看跳转独立路由页面（/org/create、/org/edit/:id、/org/detail/:id）。
 */

/* ============ 常量 ============ */

const ROOT_NODE_ID = '__root__'
const DEFAULT_LEFT_WIDTH = 25 // 默认 25% : 75%
const MIN_LEFT_WIDTH = 15
const MAX_LEFT_WIDTH = 40

/** 启用状态筛选（dict.yes_no，boolean 值；null=全部） */
const ENABLE_OPTIONS = ref([{ label: '不限', value: null }])
/** 组织架构类型筛选（dict.org_element_type，number 值） */
const ORG_TYPE_OPTIONS = ref([])
/** 码值文本映射（dict：yes_no / org_element_type / gender / org_job_status） */
const YES_NO_TEXT = ref({})
const ORG_TYPE_TEXT = ref({})
const GENDER_TEXT = ref({})
const JOB_STATUS_TEXT = ref({})
const SORT_OPTIONS = [
  { value: 'createTime', label: '创建时间' },
  { value: 'updateTime', label: '更新时间' },
  { value: 'code', label: '编码' }
]

/** 列表列：按查询类型显示不同字段（不展示任何 id 字段） */
const COLUMN_MAP = {
  // 全部（防御兜底）：公共列
  all: [
    { prop: 'fieldName', label: '名称' },
    { prop: 'fieldCode', label: '编码' },
    { prop: 'fieldOrgType', label: '类型', type: 'orgType' },
    { prop: 'fieldEnable', label: '状态', type: 'enable' },
    { prop: 'fieldCreateTime', label: '创建时间', type: 'time' }
  ],
  // 机构
  1: [
    { prop: 'fieldName', label: '名称' },
    { prop: 'fieldCode', label: '编码' },
    { prop: 'fieldLeaderName', label: '负责人' },
    { prop: 'fieldPhone', label: '联系电话' },
    { prop: 'fieldEmail', label: '联系邮箱' },
    { prop: 'fieldAddress', label: '联系地址' },
    { prop: 'fieldEnable', label: '状态', type: 'enable' },
    { prop: 'fieldCreateTime', label: '创建时间', type: 'time' }
  ],
  // 部门
  2: [
    { prop: 'fieldName', label: '名称' },
    { prop: 'fieldCode', label: '编码' },
    { prop: 'fieldLeaderName', label: '负责人' },
    { prop: 'fieldPhone', label: '联系电话' },
    { prop: 'fieldAddress', label: '联系地址' },
    { prop: 'fieldEnable', label: '状态', type: 'enable' },
    { prop: 'fieldCreateTime', label: '创建时间', type: 'time' }
  ],
  // 人员
  3: [
    { prop: 'fieldName', label: '姓名' },
    { prop: 'fieldCode', label: '工号' },
    { prop: 'fieldLoginName', label: '登录名' },
    { prop: 'fieldGender', label: '性别', type: 'gender' },
    { prop: 'fieldPhone', label: '联系电话' },
    { prop: 'fieldEmail', label: '联系邮箱' },
    { prop: 'fieldEnable', label: '状态', type: 'enable' },
    { prop: 'fieldCreateTime', label: '创建时间', type: 'time' }
  ],
  // 岗位
  4: [
    { prop: 'fieldName', label: '名称' },
    { prop: 'fieldCode', label: '编码' },
    { prop: 'fieldEnable', label: '状态', type: 'enable' },
    { prop: 'fieldCreateTime', label: '创建时间', type: 'time' }
  ]
}

const router = useRouter()

/* ============ 按钮权限 ============ */

/** 新建/删除（置为无效）所需权限：与后端 element.save / element.delete 鉴权一致 */
const AUTH_ELEMENT_ADD = ['SYS_ORG_ADMIN', 'SYS_ORG_ELEMENT_ADMIN', 'SYS_ORG_ELEMENT_ADD']
/** 编辑/启停用所需权限：与后端 element.update / element.enable 鉴权一致 */
const AUTH_ELEMENT_EDIT = ['SYS_ORG_ADMIN', 'SYS_ORG_ELEMENT_ADMIN', 'SYS_ORG_ELEMENT_EDIT']

/** 新建/置为无效按钮可见性（预判，最终权限由后端 ElementResolver 兜底） */
const canCreate = computed(() => hasAuth(AUTH_ELEMENT_ADD))
/** 编辑/停用启用按钮可见性（预判，最终权限由后端 ElementResolver 兜底） */
const canEdit = computed(() => hasAuth(AUTH_ELEMENT_EDIT))

/* ============ 分栏 ============ */

const containerRef = ref(null)
const leftWidth = ref(DEFAULT_LEFT_WIDTH)
let dragging = false
let dragStartX = 0
let dragStartWidth = 0

function startDrag(event) {
  dragging = true
  dragStartX = event.clientX
  dragStartWidth = leftWidth.value
  document.addEventListener('mousemove', handleDragMove)
  document.addEventListener('mouseup', stopDrag)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function handleDragMove(event) {
  if (!dragging || !containerRef.value) return
  const rect = containerRef.value.getBoundingClientRect()
  if (rect.width === 0) return
  const next = dragStartWidth + ((event.clientX - dragStartX) / rect.width) * 100
  leftWidth.value = Math.min(MAX_LEFT_WIDTH, Math.max(MIN_LEFT_WIDTH, next))
}

function stopDrag() {
  dragging = false
  document.removeEventListener('mousemove', handleDragMove)
  document.removeEventListener('mouseup', stopDrag)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onBeforeUnmount(stopDrag)

/* ============ 左侧树 ============ */

const treeProps = { label: 'fieldName', children: 'children' }
const defaultExpandedKeys = [ROOT_NODE_ID]
const treeKey = ref(0)
const currentParentId = ref(null)
const currentParentName = ref('根节点')

/** 根节点虚拟入口图标；机构/部门分别用不同前置图标区分 */
function nodeIcon(data) {
  if (data.fieldId === ROOT_NODE_ID) return HomeFilled
  return data.fieldOrgType === 1 ? OfficeBuilding : School
}

function nodeIconClass(data) {
  if (data.fieldId === ROOT_NODE_ID) return 'is-root'
  return data.fieldOrgType === 1 ? 'is-org' : 'is-dept'
}

/** 树懒加载：树根仅返回"根节点"虚拟入口；展开根节点加载根级（field_parent_id 为空）机构/部门；其余节点加载其直接下级 */
async function loadTree(node, resolve) {
  if (node.level === 0) {
    resolve([{ fieldId: ROOT_NODE_ID, fieldName: '根节点', fieldOrgType: 0 }])
    return
  }
  const parentId = node.data.fieldId === ROOT_NODE_ID ? null : node.data.fieldId
  try {
    const res = await queryAddressBookTree({
      orgTypes: [1, 2],
      limitRange: [],
      parentId
    })
    resolve(res.data || [])
  } catch (error) {
    ElMessage.error(error.message || '组织架构树加载失败')
    resolve([])
  }
}

/** 树节点点击：仅将查询目标切换为点中的节点（根节点 → 根级），并刷新右侧列表 */
function handleTreeNodeClick(data) {
  if (data.fieldId === ROOT_NODE_ID) {
    currentParentId.value = null
    currentParentName.value = '根节点'
  } else {
    currentParentId.value = data.fieldId
    currentParentName.value = data.fieldName
  }
  queryPage.value = 1
  loadList()
}

/* ============ 筛选 / 排序 / 列表 ============ */

const filters = reactive({
  fieldName: '',
  fieldCode: '',
  orgType: 3, // 默认"人员"
  enable: true // 默认"启用"
})

const sortField = ref('createTime') // 默认创建时间
const sortOrder = ref('desc') // 默认降序

const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)
const selectedRows = ref([])

const columns = computed(() => COLUMN_MAP[filters.orgType] || COLUMN_MAP.all)

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
    const res = await queryElementList({
      body: {
        parentId: currentParentId.value,
        fieldName: filters.fieldName || null,
        fieldCode: filters.fieldCode || null,
        orgType: filters.orgType,
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

/** 单元格格式化：状态/类型/性别/在岗/时间（码值文本来自 dict 模块） */
function formatCell(type, value, row) {
  switch (type) {
    case 'enable':
      return enableText(value)
    case 'orgType':
      return orgTypeText(value)
    case 'gender':
      return genderText(value)
    case 'jobStatus':
      return jobStatusText(value)
    case 'time':
      return formatTime(value)
    default:
      return value == null ? '-' : value
  }
}

function enableText(value) {
  return YES_NO_TEXT.value[value] || '-'
}

function orgTypeText(type) {
  return ORG_TYPE_TEXT.value[type] || '-'
}

function genderText(value) {
  return GENDER_TEXT.value[value] ?? '-'
}

function jobStatusText(value) {
  return JOB_STATUS_TEXT.value[value] ?? '-'
}

function formatTime(value, dateOnly = false) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  const text = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  return dateOnly ? text : `${text} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/* ============ 路由跳转（新建/编辑/查看） ============ */

/** 新建：跳转独立路由，携带当前点中的树节点作为默认上级 */
function goCreate() {
  router.push({
    path: '/org/create',
    query: {
      parentId: currentParentId.value || '',
      parentName: currentParentName.value,
      orgType: filters.orgType
    }
  })
}

function goDetail(row) {
  router.push(`/org/detail/${row.fieldId}`)
}

function goEdit(row) {
  router.push(`/org/edit/${row.fieldId}`)
}

/* ============ 停用 / 启用 ============ */

async function handleToggleEnable(row) {
  const enable = !row.fieldEnable
  try {
    await ElMessageBox.confirm(
      `确认将「${row.fieldName}」${enable ? '启用' : '停用'}？${enable ? '' : '停用后该节点可能影响下级业务使用。'}`,
      '操作确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await enableElement({ fieldId: row.fieldId, fieldEnable: enable })
    ElMessage.success(enable ? '已启用' : '已停用')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

/* ============ 置为无效（批量删除） ============ */

async function handleInvalid() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选要置为无效的记录')
    return
  }
  const names = selectedRows.value.map((r) => r.fieldName).join('、')
  try {
    await ElMessageBox.confirm(
      `确认将选中的 ${selectedRows.value.length} 条记录（${names}）置为无效？该操作不可恢复。`,
      '置为无效确认',
      { type: 'warning', confirmButtonText: '确认置为无效' }
    )
  } catch {
    return
  }
  let successCount = 0
  const failedNames = []
  for (const row of selectedRows.value) {
    try {
      await deleteElement(row.fieldId)
      successCount += 1
    } catch (error) {
      failedNames.push(`${row.fieldName}（${error.message || '未知错误'}）`)
    }
  }
  selectedRows.value = []
  treeKey.value += 1
  loadList()
  if (failedNames.length) {
    ElMessage.warning(`置为无效：成功 ${successCount} 条，失败 ${failedNames.length} 条（${failedNames.join('、')}）`)
  } else {
    ElMessage.success(`置为无效完成，共 ${successCount} 条`)
  }
}

/* ============ 初始化 ============ */

onMounted(() => {
  loadDictOptions()
  // 默认点中"根节点"，按默认筛选（类型=人员、启用=启用）加载根级列表
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

/** 加载码值选项与文本映射（org_element_type / yes_no / gender / org_job_status）；失败时保持兜底，不阻塞页面 */
async function loadDictOptions() {
  try {
    const [orgTypeOptions, enableOptions, yesNoText, orgTypeTextMap, genderTextMap, jobStatusTextMap] =
      await Promise.all([
        getDictOptions('org_element_type', { number: true }),
        getDictOptions('yes_no', { boolean: true }),
        getDictTextMap('yes_no', { boolean: true }),
        getDictTextMap('org_element_type', { number: true }),
        getDictTextMap('gender', { number: true }),
        getDictTextMap('org_job_status', { number: true })
      ])
    ORG_TYPE_OPTIONS.value = orgTypeOptions
    ENABLE_OPTIONS.value = [{ label: '不限', value: null }, ...enableOptions]
    YES_NO_TEXT.value = yesNoText
    ORG_TYPE_TEXT.value = orgTypeTextMap
    GENDER_TEXT.value = genderTextMap
    JOB_STATUS_TEXT.value = jobStatusTextMap
  } catch {
    // 码值加载失败：筛选保留「全部」兜底，文本回退原值
  }
}
</script>

<script>
export default { name: 'OrgManageView' }
</script>

<style scoped>
.org-manage {
  display: flex;
  height: 100%;
  min-height: 480px;
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}

/* —— 左侧树 —— */
.org-left {
  flex-shrink: 0;
  min-width: 160px;
  overflow: auto;
  padding: 8px;
  border-right: none;
}

.org-tree {
  background: transparent;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
}

.tree-node-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.tree-node-icon.is-root {
  color: #409eff;
}

.tree-node-icon.is-org {
  color: #e6a23c;
}

.tree-node-icon.is-dept {
  color: #67c23a;
}

.tree-node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* —— 拖拽条 —— */
.org-divider {
  width: 5px;
  flex-shrink: 0;
  cursor: col-resize;
  background: var(--el-border-color-lighter);
  transition: background-color 0.2s;
}

.org-divider:hover {
  background: #409eff;
}

/* —— 右侧区域 —— */
.org-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */
</style>

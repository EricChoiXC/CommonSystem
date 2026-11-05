<template>
  <div class="address-book">
    <!-- 触发入口：只读展示当前选中值，点击打开弹窗 -->
    <el-input
      :model-value="fieldName"
      placeholder="请选择组织架构"
      readonly
      clearable
      :disabled="disabled"
      @clear="handleClear"
      @click="openDialog"
    >
      <template #append>
        <el-button :disabled="disabled" @click.stop="openDialog">选择</el-button>
      </template>
    </el-input>

    <el-dialog
      v-model="dialogVisible"
      title="地址本"
      width="920px"
      append-to-body
      destroy-on-close
    >
      <!-- 上层：唯一关键字输入框 + 查询按钮 -->
      <div class="ab-top">
        <el-input
          v-model="keyword"
          placeholder="请输入关键字"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>

      <!-- 中层：页签（组织架构树 / 查询，showRole 时附角色页签，可选类型含人员时附群组页签），数据均受 orgType 过滤 -->
      <el-tabs v-model="activeTab" class="ab-tabs" @tab-change="handleTabChange">
        <!-- 页签一：组织架构树，左侧树导航 + 右侧列表选中 -->
        <el-tab-pane label="组织架构树" name="tree">
          <div class="ab-tree-panel">
            <div class="ab-tree-left">
              <el-tree
                ref="treeRef"
                :props="treeProps"
                lazy
                :load="loadTree"
                node-key="fieldId"
                :default-expanded-keys="defaultExpandedKeys"
                highlight-current
                @node-click="handleTreeNodeClick"
              />
            </div>
            <div class="ab-tree-right">
              <el-table
                ref="treeTableRef"
                :data="rightList"
                v-loading="rightLoading"
                height="320"
                row-key="fieldId"
                :row-class-name="rowClassName"
                @row-click="handleRowClick"
                @selection-change="handleTreeSelectionChange"
              >
                <el-table-column v-if="multi" type="selection" width="45" />
                <el-table-column prop="fieldName" label="名称" min-width="150" show-overflow-tooltip />
                <el-table-column prop="fieldCode" label="编码" min-width="120" show-overflow-tooltip />
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">{{ orgTypeText(row.fieldOrgType) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <!-- 页签二：查询，仅根据上层关键字做列表分页查询 -->
        <el-tab-pane label="查询" name="query">
          <el-table
            ref="queryTableRef"
            :data="queryList"
            v-loading="queryLoading"
            height="320"
            row-key="fieldId"
            :row-class-name="rowClassName"
            @row-click="handleRowClick"
            @selection-change="handleQuerySelectionChange"
          >
            <el-table-column v-if="multi" type="selection" width="45" />
            <el-table-column prop="fieldName" label="名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="fieldCode" label="编码" min-width="120" show-overflow-tooltip />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ orgTypeText(row.fieldOrgType) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="queryPage"
            v-model:page-size="queryPageSize"
            :total="queryTotal"
            layout="total, prev, pager, next, sizes"
            :page-sizes="[10, 20, 50, 100]"
            @current-change="loadQueryList"
            @size-change="loadQueryList"
          />
        </el-tab-pane>

        <!-- 页签三：角色，仅 showRole 时渲染；角色无层级，无左侧树，仅一张分页表格 -->
        <el-tab-pane v-if="showRole" label="角色" name="role">
          <div class="ab-role-toolbar">
            <el-button @click="handleRoleRefresh">刷新</el-button>
          </div>
          <el-table
            ref="roleTableRef"
            :data="roleList"
            v-loading="roleLoading"
            height="320"
            row-key="fieldId"
            :row-class-name="rowClassName"
            @row-click="handleRowClick"
            @selection-change="handleRoleSelectionChange"
          >
            <el-table-column v-if="multi" type="selection" width="45" />
            <el-table-column prop="fieldName" label="名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="fieldCode" label="编码" min-width="120" show-overflow-tooltip />
            <el-table-column label="是否启用" width="90">
              <template #default="{ row }">
                <el-tag :type="row.fieldEnable ? 'success' : 'danger'" size="small">
                  {{ row.fieldEnable ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="rolePage"
            v-model:page-size="rolePageSize"
            :total="roleTotal"
            layout="total, prev, pager, next, sizes"
            :page-sizes="[10, 20, 50, 100]"
            @current-change="loadRoleList"
            @size-change="loadRoleList"
          />
        </el-tab-pane>

        <!-- 页签四：群组，仅 orgType 含人员(3) 时渲染；左侧可见群组列表（点击选中群组，非选值），右侧展示该群组成员人员表格 -->
        <el-tab-pane v-if="groupTabVisible" label="群组" name="group">
          <div class="ab-group-panel">
            <div class="ab-group-left">
              <div class="ab-group-toolbar">
                <el-button @click="handleGroupRefresh">刷新</el-button>
              </div>
              <div class="ab-group-list" v-loading="groupLoading">
                <template v-if="groupList.length">
                  <div
                    v-for="item in groupList"
                    :key="item.fieldId"
                    class="ab-group-item"
                    :class="{ 'ab-group-item-active': currentGroup && currentGroup.fieldId === item.fieldId }"
                    @click="handleGroupClick(item)"
                  >
                    <span class="ab-group-name">{{ item.fieldName }}</span>
                    <span class="ab-group-meta">
                      <el-tag v-if="item.fieldType === 'PERSONAL'" size="small" type="info">个人</el-tag>
                      <span v-if="item.userCount != null" class="ab-group-count">{{ item.userCount }}人</span>
                    </span>
                  </div>
                </template>
                <el-empty v-else description="暂无群组" :image-size="60" />
              </div>
              <el-pagination
                v-model:current-page="groupPage"
                v-model:page-size="groupPageSize"
                :total="groupTotal"
                layout="total, prev, pager, next, sizes"
                :page-sizes="[10, 20, 50, 100]"
                @current-change="loadGroupList"
                @size-change="loadGroupList"
              />
            </div>
            <div class="ab-group-right">
              <template v-if="currentGroup">
                <el-table
                  ref="groupTableRef"
                  :data="groupUserList"
                  v-loading="groupUserLoading"
                  height="320"
                  row-key="fieldId"
                  :row-class-name="rowClassName"
                  @row-click="handleRowClick"
                  @selection-change="handleGroupSelectionChange"
                >
                  <el-table-column v-if="multi" type="selection" width="45" />
                  <el-table-column prop="fieldName" label="名称" min-width="150" show-overflow-tooltip />
                  <el-table-column prop="fieldCode" label="编码" min-width="120" show-overflow-tooltip />
                  <el-table-column label="类型" width="90">
                    <template #default="{ row }">{{ orgTypeText(row.fieldOrgType) }}</template>
                  </el-table-column>
                </el-table>
              </template>
              <el-empty v-else description="请选择左侧群组" />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 下层：已选项展示 + 确认 / 取消 -->
      <div class="ab-bottom">
        <div class="ab-selected">
          <span class="ab-selected-label">已选：</span>
          <el-tag
            v-for="item in selected"
            :key="item.fieldId"
            closable
            @close="removeSelected(item)"
          >
            {{ item.fieldName }}
          </el-tag>
          <span v-if="!selected.length" class="ab-selected-empty">未选择</span>
        </div>
        <div class="ab-footer">
          <el-button @click="handleCancel">取消</el-button>
          <el-button type="primary" @click="handleConfirm">确认</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  queryAddressBookList,
  queryAddressBookTree,
  queryAddressBookGroupList,
  queryAddressBookGroupUsers,
  resolveOrgTypes
} from '@/api/org'
import { getDictTextMap } from '@/utils/dict'

/**
 * 地址本组件：提供组织架构选择的弹窗
 * 弹窗分上中下三层：上层关键字查询、中层组织架构树/查询（可选角色）页签、下层已选项与确认取消。
 * 查询页签与树页签右侧可选列表受 orgType / limitRange 过滤；
 * 左侧树仅作导航，固定展示机构/部门/岗位（不含人员），与当前可选类型无关；
 * 「角色」页签仅 showRole 时渲染（角色无层级，无左侧树，仅一张分页表格）；
 * 「群组」页签仅 orgType 含人员(3) 时渲染：左侧可见群组列表，点选群组后右侧加载其成员人员表格（按群组批量选人，结果为人员）。
 */
const props = defineProps({
  /** 关联的 id 值字段，必填 */
  fieldId: { type: String, default: '' },
  /** 关联的名称值字段，必填 */
  fieldName: { type: String, default: '' },
  /** 可选的组织架构类型：ALL / ORG / DEPT / ORGDEPT / PERSON / POST / PERSONPOST / ORGDEPTPOSTPERSON，默认 ALL */
  orgType: {
    type: String,
    default: 'ALL',
    validator: (value) =>
      ['ALL', 'ORG', 'DEPT', 'ORGDEPT', 'PERSON', 'POST', 'PERSONPOST', 'ORGDEPTPOSTPERSON'].includes(value)
  },
  /** 是否多选，默认 false */
  multi: { type: Boolean, default: false },
  /** 多选的分隔符，默认 ';'，要求仅限符号 */
  septarator: { type: String, default: ';' },
  /** 限制的组织架构范围：组织架构 fieldId 值数组，默认为空（不限制） */
  limitRange: { type: Array, default: () => [] },
  /** 确认选择后的回调函数 callback(fieldId, fieldName, selectedList) */
  callback: { type: Function, default: null },
  /** 是否禁用（只读展示不可打开选择弹窗），默认 false */
  disabled: { type: Boolean, default: false },
  /** 是否显示「角色」页签（角色单选/多选，结果为角色 fieldId / 名称），默认 false */
  showRole: { type: Boolean, default: false }
})
const emit = defineEmits(['update:fieldId', 'update:fieldName'])

const dialogVisible = ref(false)
const activeTab = ref('tree')
const keyword = ref('')

/** 树中"根节点"虚拟入口的 fieldId，点击后右侧展示 field_parent_id 为空的组织架构 */
const ROOT_NODE_ID = '__root__'
/** 默认展开"根节点"，使打开弹窗即展示第一层组织架构 */
const defaultExpandedKeys = [ROOT_NODE_ID]

const treeProps = { label: 'fieldName', children: 'children' }
const orgTypes = computed(() => resolveOrgTypes(props.orgType))

/** 「群组」页签是否渲染：当前 orgType 可选人员(3)时展示（按群组批量选人，无需开关） */
const groupTabVisible = computed(() => orgTypes.value.includes(3))

/**
 * 左侧树固定展示的导航类型：机构(1)/部门(2)/岗位(4)。
 * 人员(3) 作为叶子可选目标只在右侧列表展示，不作为树导航节点；
 * 树导航不随当前可选类型(orgType)变化，保证选择人员时左侧仍能浏览机构/部门/岗位。
 */
const TREE_ORG_TYPES = [1, 2, 4]

// 各页签表格实例：多选时按 fieldId 恢复/保留勾选（配合 row-key）
const treeTableRef = ref(null)
const queryTableRef = ref(null)
const roleTableRef = ref(null)
const groupTableRef = ref(null)

// 树页签：左侧树 + 右侧下级列表
const rightList = ref([])
const rightLoading = ref(false)

// 查询页签：关键字分页列表
const queryList = ref([])
const queryLoading = ref(false)
const queryPage = ref(1)
const queryPageSize = ref(20)
const queryTotal = ref(0)

// 角色页签（showRole 时）：固定 orgTypes=[5] 的分页列表
const roleList = ref([])
const roleLoading = ref(false)
const rolePage = ref(1)
const rolePageSize = ref(20)
const roleTotal = ref(0)
/** 角色页签是否已加载过：首次切到该页签时才加载，避免打开弹窗即请求角色数据 */
const roleLoaded = ref(false)
/** 角色页签固定查询的组织架构类型：5 = 角色 */
const ROLE_ORG_TYPES = [5]

// 群组页签（orgType 含人员时）：左侧可见群组分页列表 + 右侧成员人员表格
const groupList = ref([])
const groupLoading = ref(false)
const groupPage = ref(1)
const groupPageSize = ref(20)
const groupTotal = ref(0)
/** 群组页签是否已加载过：首次切到该页签时才加载，避免打开弹窗即请求群组数据 */
const groupLoaded = ref(false)
/** 当前选中的群组（点击左侧群组行后记录，右侧展示其成员；仅用于定位加载，非选值） */
const currentGroup = ref(null)
// 群组页签：右侧成员人员列表与加载状态
const groupUserList = ref([])
const groupUserLoading = ref(false)

// 已选项（下层展示）
const selected = ref([])
const selectedBackup = ref([])

/**
 * 多选模式下各页签（组织架构树/查询/角色/群组成员）各自维护勾选行；
 * 单独记录每个页签的勾选，再按 fieldId 合并到 selected，
 * 避免跨页签勾选互相覆盖（先勾树侧再勾查询/角色侧不丢前选）。
 */
const treeSelectedRows = ref([])
const querySelectedRows = ref([])
const roleSelectedRows = ref([])
const groupSelectedRows = ref([])

/**
 * 各页签表格实例 → 勾选行记录的映射表：restoreTableSelection 恢复勾选后
 * 按 tableRef 找到对应页签的勾选行记录进行同步，避免多表格分支写错串扰。
 */
const TABLE_SELECTION_REFS = new Map([
  [treeTableRef, treeSelectedRows],
  [queryTableRef, querySelectedRows],
  [roleTableRef, roleSelectedRows],
  [groupTableRef, groupSelectedRows]
])

/**
 * 表格勾选恢复保护标志：loadRightList/loadQueryList 刷新数据后按已选项恢复勾选期间，
 * 由代码主动触发 clearSelection/toggleRowSelection 产生的 selection-change
 * 不参与已选项(selected)的变更，避免「刷新数据导致已选项被清空」。
 */
let restoring = false

/** 组织架构类型文本映射（dict.org_element_type，number 值；组件挂载时异步加载，带缓存） */
const ORG_TYPE_TEXT = ref({})
getDictTextMap('org_element_type', { number: true })
  .then((map) => {
    ORG_TYPE_TEXT.value = map
  })
  .catch(() => {
    // 码值加载失败：类型列回退原值，不阻塞地址本使用
  })

function orgTypeText(type) {
  return ORG_TYPE_TEXT.value[type] || '-'
}

/** 按分隔符将 fieldId / fieldName 拆分为已选项数组，用于弹窗打开时回显 */
function splitSelected(fieldId, fieldName) {
  if (!fieldId) {
    return []
  }
  const ids = String(fieldId).split(props.septarator)
  const names = String(fieldName || '').split(props.septarator)
  return ids.map((id, index) => ({
    fieldId: id,
    fieldName: names[index] || id
  }))
}

/** 打开弹窗：回显已选项、加载树页签根级列表、刷新查询页签 */
function openDialog() {
  if (props.disabled) {
    return
  }
  selected.value = splitSelected(props.fieldId, props.fieldName)
  selectedBackup.value = selected.value.map((item) => ({ ...item }))
  treeSelectedRows.value = []
  querySelectedRows.value = []
  roleSelectedRows.value = []
  groupSelectedRows.value = []
  // 重置角色页签加载标记：每次打开弹窗后首次切到角色页签都重新加载，避免展示上次会话的旧数据
  roleLoaded.value = false
  // 重置群组页签：清空选中群组与成员列表、重置加载标记（与 roleLoaded 同模式）
  currentGroup.value = null
  groupUserList.value = []
  groupLoaded.value = false
  dialogVisible.value = true
  loadRightList(null)
  if (activeTab.value === 'query') {
    loadQueryList()
  } else if (activeTab.value === 'role') {
    loadRoleList()
  } else if (activeTab.value === 'group') {
    loadGroupList()
  }
}

/** 左侧树懒加载：树根加载"根节点"虚拟入口；展开"根节点"加载根级（field_parent_id 为空）数据；其余节点加载其直接下级。
 *  树仅作导航，固定加载机构/部门/岗位（TREE_ORG_TYPES），不随当前可选类型变化。 */
async function loadTree(node, resolve) {
  // 树根：仅返回"根节点"虚拟入口，由 default-expanded-keys 自动展开并加载根级数据
  if (node.level === 0) {
    resolve([{ fieldId: ROOT_NODE_ID, fieldName: '根节点' }])
    return
  }
  const parentId = node.data.fieldId === ROOT_NODE_ID ? null : node.data.fieldId
  try {
    const res = await queryAddressBookTree({
      orgTypes: TREE_ORG_TYPES,
      limitRange: props.limitRange,
      parentId
    })
    resolve(res.data || [])
  } catch (error) {
    ElMessage.error(error.message || '组织架构加载失败')
    resolve([])
  }
}

/** 左侧树节点点击：不选中数据，仅将查询以该数据为上级节点的数据列出在右侧区域；点击"根节点"时列出根级（field_parent_id 为空）数据 */
function handleTreeNodeClick(data) {
  loadRightList(data.fieldId === ROOT_NODE_ID ? null : data.fieldId)
}

/** 加载树页签右侧列表（parentId 为空表示根级） */
async function loadRightList(parentId) {
  rightLoading.value = true
  try {
    const res = await queryAddressBookTree({
      orgTypes: orgTypes.value,
      limitRange: props.limitRange,
      parentId
    })
    rightList.value = res.data || []
  } catch (error) {
    ElMessage.error(error.message || '下级数据加载失败')
    rightList.value = []
  } finally {
    rightLoading.value = false
    restoreTableSelection(treeTableRef, rightList.value)
  }
}

/** 查询按钮：切到查询页签并按关键字刷新 */
function handleSearch() {
  activeTab.value = 'query'
  queryPage.value = 1
  loadQueryList()
}

/** 查询页签：仅根据上层关键字输入框分页查询 */
async function loadQueryList() {
  queryLoading.value = true
  try {
    const res = await queryAddressBookList({
      body: {
        keyword: keyword.value,
        orgTypes: orgTypes.value,
        limitRange: props.limitRange
      },
      pages: {
        pageNo: queryPage.value,
        pageSize: queryPageSize.value,
        needPage: true
      }
    })
    queryList.value = res.data || []
    // 后端未返回 total 时以当前页条数兜底
    queryTotal.value = (res.metadata && res.metadata.total) || queryList.value.length
  } catch (error) {
    ElMessage.error(error.message || '查询失败')
  } finally {
    queryLoading.value = false
    restoreTableSelection(queryTableRef, queryList.value)
  }
}

/** 角色页签：固定 orgTypes=[5] 按当前关键字分页查询（复用地址本通用列表接口，方案 A） */
async function loadRoleList() {
  roleLoading.value = true
  try {
    const res = await queryAddressBookList({
      body: {
        keyword: keyword.value,
        orgTypes: ROLE_ORG_TYPES,
        limitRange: props.limitRange
      },
      pages: {
        pageNo: rolePage.value,
        pageSize: rolePageSize.value,
        needPage: true
      }
    })
    roleList.value = res.data || []
    // 后端未返回 total 时以当前页条数兜底
    roleTotal.value = (res.metadata && res.metadata.total) || roleList.value.length
    roleLoaded.value = true
  } catch (error) {
    ElMessage.error(error.message || '角色列表加载失败')
  } finally {
    roleLoading.value = false
    restoreTableSelection(roleTableRef, roleList.value)
  }
}

/** 角色页签独立刷新：回第 1 页按当前关键字重新加载（不改上层 handleSearch 的页签切换语义） */
function handleRoleRefresh() {
  rolePage.value = 1
  loadRoleList()
}

/** 群组页签独立刷新：回第 1 页按当前关键字重新加载左侧群组列表（工具条与角色页签 ab-role-toolbar 一致） */
function handleGroupRefresh() {
  groupPage.value = 1
  loadGroupList()
}

/** 群组页签：左侧可见群组分页查询（复用上层关键字输入框的关键字，可见性过滤由后端处理） */
async function loadGroupList() {
  groupLoading.value = true
  try {
    const res = await queryAddressBookGroupList({
      body: { keyword: keyword.value },
      pages: {
        pageNo: groupPage.value,
        pageSize: groupPageSize.value,
        needPage: true
      }
    })
    groupList.value = res.data || []
    // 后端未返回 total 时以当前页条数兜底
    groupTotal.value = (res.metadata && res.metadata.total) || groupList.value.length
    groupLoaded.value = true
    // 刷新后当前选中群组不在可见列表中时清除选中态（右侧随之回到空态提示）
    if (currentGroup.value && !groupList.value.some((g) => g.fieldId === currentGroup.value.fieldId)) {
      currentGroup.value = null
      groupUserList.value = []
      groupSelectedRows.value = []
    }
  } catch (error) {
    ElMessage.error(error.message || '群组列表加载失败')
  } finally {
    groupLoading.value = false
  }
}

/** 群组页签左侧群组行点击：选中该群组并加载右侧成员（不是选值） */
function handleGroupClick(item) {
  const changed = !currentGroup.value || currentGroup.value.fieldId !== item.fieldId
  currentGroup.value = item
  if (changed) {
    // 切换群组：重置成员列表与成员勾选记录（成员按 fieldId 合并进 selected，已选的不会丢，
    // 成员加载完成后由 restoreTableSelection 按已选项恢复新群组数据中的勾选）
    groupUserList.value = []
    groupSelectedRows.value = []
  }
  loadGroupUsers()
}

/** 群组页签：加载当前选中群组的启用成员人员列表（orgTypes 按当前可选类型传入，类型求交为后端语义） */
async function loadGroupUsers() {
  if (!currentGroup.value) {
    return
  }
  // 记录本次请求的群组：等待期间用户切换群组时丢弃过期响应，避免成员列表与高亮群组错位
  const groupId = currentGroup.value.fieldId
  groupUserLoading.value = true
  try {
    const res = await queryAddressBookGroupUsers({
      groupId,
      orgTypes: orgTypes.value
    })
    if (!currentGroup.value || currentGroup.value.fieldId !== groupId) {
      return
    }
    groupUserList.value = res.data || []
  } catch (error) {
    if (!currentGroup.value || currentGroup.value.fieldId !== groupId) {
      return
    }
    // 群组不可见（无权查看该群组）等后端错误统一提示
    ElMessage.error(error.message || '群组成员加载失败')
    groupUserList.value = []
  } finally {
    groupUserLoading.value = false
    restoreTableSelection(groupTableRef, groupUserList.value)
  }
}

/**
 * 多选：群组页签成员勾选变化（用户操作时），逻辑同树/查询/角色页签。
 */
function handleGroupSelectionChange(rows) {
  if (!props.multi || restoring) {
    return
  }
  const picked = rows.map((row) => pick(row))
  const nowIds = new Set(picked.map((r) => r.fieldId))
  const removed = groupSelectedRows.value.filter((r) => !nowIds.has(r.fieldId))
  groupSelectedRows.value = picked
  if (removed.length) {
    const removedIds = new Set(removed.map((r) => r.fieldId))
    selected.value = selected.value.filter((s) => !removedIds.has(s.fieldId))
  }
  mergeSelection()
}

/** 页签切换：首次进入角色/群组页签时才加载对应数据（tab-change 参数为页签 name） */
function handleTabChange(name) {
  if (name === 'role' && !roleLoaded.value) {
    loadRoleList()
  } else if (name === 'group' && !groupLoaded.value) {
    loadGroupList()
  }
}

/** 单选：点击行选中；多选：走 checkbox 勾选 */
function handleRowClick(row) {
  if (props.multi) {
    return
  }
  selected.value = [pick(row)]
}

/**
 * 多选：树页签勾选变化（用户操作时）。
 * 恢复勾选期间（restoring）忽略由代码主动触发的 selection-change；
 * 用户主动取消勾选的行从已选项中移除，其余只增不减地合并到已选项。
 */
function handleTreeSelectionChange(rows) {
  if (!props.multi || restoring) {
    return
  }
  const picked = rows.map((row) => pick(row))
  const nowIds = new Set(picked.map((r) => r.fieldId))
  // 用户主动取消勾选的行：页签勾选记录中存在、当前勾选中不存在
  const removed = treeSelectedRows.value.filter((r) => !nowIds.has(r.fieldId))
  treeSelectedRows.value = picked
  if (removed.length) {
    const removedIds = new Set(removed.map((r) => r.fieldId))
    selected.value = selected.value.filter((s) => !removedIds.has(s.fieldId))
  }
  mergeSelection()
}

/**
 * 多选：查询页签勾选变化（用户操作时），逻辑同树页签。
 */
function handleQuerySelectionChange(rows) {
  if (!props.multi || restoring) {
    return
  }
  const picked = rows.map((row) => pick(row))
  const nowIds = new Set(picked.map((r) => r.fieldId))
  const removed = querySelectedRows.value.filter((r) => !nowIds.has(r.fieldId))
  querySelectedRows.value = picked
  if (removed.length) {
    const removedIds = new Set(removed.map((r) => r.fieldId))
    selected.value = selected.value.filter((s) => !removedIds.has(s.fieldId))
  }
  mergeSelection()
}

/**
 * 多选：角色页签勾选变化（用户操作时），逻辑同树/查询页签。
 */
function handleRoleSelectionChange(rows) {
  if (!props.multi || restoring) {
    return
  }
  const picked = rows.map((row) => pick(row))
  const nowIds = new Set(picked.map((r) => r.fieldId))
  const removed = roleSelectedRows.value.filter((r) => !nowIds.has(r.fieldId))
  roleSelectedRows.value = picked
  if (removed.length) {
    const removedIds = new Set(removed.map((r) => r.fieldId))
    selected.value = selected.value.filter((s) => !removedIds.has(s.fieldId))
  }
  mergeSelection()
}

/**
 * 合并各页签（组织架构树/查询/角色/群组成员）的勾选到已选项：以现有 selected 为基础并入页签勾选（只增不减）。
 * 已选项一旦选中，不因表格数据刷新（切树节点/翻页/重新查询/切换群组）丢失；
 * 用户主动取消勾选的行由 selection-change 处理器先移出 selected 再合并。
 */
function mergeSelection() {
  const merged = new Map()
  for (const item of selected.value) {
    merged.set(item.fieldId, item)
  }
  for (const item of [
    ...treeSelectedRows.value,
    ...querySelectedRows.value,
    ...roleSelectedRows.value,
    ...groupSelectedRows.value
  ]) {
    merged.set(item.fieldId, item)
  }
  selected.value = [...merged.values()]
}

/**
 * 多选：表格数据刷新（切树节点/翻页/查询）后按已选项恢复勾选，避免勾选状态随数据重载丢失。
 * 在数据刷新后的同步阶段即进入恢复保护（restoring）：el-table 渲染新数据时会自动清理
 * 已失效的勾选行并触发 selection-change，该窗口必须忽略，否则会把已选项误判为用户取消勾选；
 * 恢复时机延迟到数据渲染完成（nextTick），保证 toggleRowSelection 对更新后的行数据生效。
 * 恢复完成后按映射表（tableRef → 勾选行记录）将该页签勾选行记录同步为当前数据中仍存在的已选项，并解除保护。
 */
function restoreTableSelection(tableRef, rows) {
  if (!props.multi) {
    return
  }
  restoring = true
  nextTick(() => {
    if (tableRef.value) {
      const selectedIds = new Set(selected.value.map((s) => s.fieldId))
      tableRef.value.clearSelection()
      ;(rows || []).forEach((row) => {
        if (selectedIds.has(row.fieldId)) {
          tableRef.value.toggleRowSelection(row, true)
        }
      })
      // 按映射表找到该页签的勾选行记录，同步为当前数据中存在的已选项（避免多表格分支写错串扰）
      const selectedRowsRef = TABLE_SELECTION_REFS.get(tableRef)
      const current = (rows || []).filter((row) => selectedIds.has(row.fieldId)).map(pick)
      if (selectedRowsRef) {
        selectedRowsRef.value = current
      }
    }
    restoring = false
  })
}

/** 单选时高亮已选行 */
function rowClassName({ row }) {
  if (!props.multi && selected.value.length && selected.value[0].fieldId === row.fieldId) {
    return 'ab-current-row'
  }
  return ''
}

function pick(row) {
  return {
    fieldId: row.fieldId,
    fieldName: row.fieldName,
    fieldOrgType: row.fieldOrgType,
    fieldCode: row.fieldCode
  }
}

function removeSelected(item) {
  selected.value = selected.value.filter((s) => s.fieldId !== item.fieldId)
  // 同步取消当前表格中的对应勾选，避免删除标签后表格仍显示勾选（触发 selection-change 幂等更新页签勾选记录）
  const uncheck = (tableRef, rows) => {
    if (!tableRef.value || !rows) {
      return
    }
    const row = rows.find((r) => r.fieldId === item.fieldId)
    if (row) {
      tableRef.value.toggleRowSelection(row, false)
    }
  }
  uncheck(treeTableRef, rightList.value)
  uncheck(queryTableRef, queryList.value)
  uncheck(roleTableRef, roleList.value)
  uncheck(groupTableRef, groupUserList.value)
}

/** 确认：回写 fieldId / fieldName 并触发回调 */
function handleConfirm() {
  const ids = selected.value.map((s) => s.fieldId).join(props.septarator)
  const names = selected.value.map((s) => s.fieldName).join(props.septarator)
  emit('update:fieldId', ids)
  emit('update:fieldName', names)
  if (typeof props.callback === 'function') {
    props.callback(ids, names, selected.value.map((s) => ({ ...s })))
  }
  dialogVisible.value = false
}

/** 取消：恢复打开弹窗前的已选项 */
function handleCancel() {
  selected.value = selectedBackup.value.map((item) => ({ ...item }))
  dialogVisible.value = false
}

/** 清空当前值 */
function handleClear() {
  emit('update:fieldId', '')
  emit('update:fieldName', '')
}
</script>

<style scoped>
.address-book {
  width: 100%;
}

.ab-top {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.ab-tabs {
  min-height: 380px;
}

.ab-tree-panel {
  display: flex;
  gap: 12px;
}

.ab-tree-left {
  width: 240px;
  max-height: 320px;
  overflow: auto;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 4px;
}

.ab-tree-right {
  flex: 1;
  min-width: 0;
}

/* 角色页签工具条：独立「刷新」按钮（不改上层 handleSearch 切查询页签的语义） */
.ab-role-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

/* 群组页签：左侧可见群组列表 + 右侧成员表格双栏（结构同树页签 ab-tree-panel） */
.ab-group-panel {
  display: flex;
  gap: 12px;
}

.ab-group-left {
  width: 280px;
  display: flex;
  flex-direction: column;
}

/* 群组页签工具条：独立「刷新」按钮（与角色页签 ab-role-toolbar 一致） */
.ab-group-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.ab-group-list {
  height: 320px;
  overflow: auto;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.ab-group-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  cursor: pointer;
}

.ab-group-item:hover {
  background-color: var(--el-fill-color-light);
}

/* 高亮当前选中的群组 */
.ab-group-item-active {
  background-color: var(--el-color-primary-light-9);
}

.ab-group-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ab-group-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.ab-group-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.ab-group-right {
  flex: 1;
  min-width: 0;
}

/* 左侧栏较窄：群组列表分页允许换行避免溢出 */
.ab-group-left :deep(.el-pagination) {
  flex-wrap: wrap;
  row-gap: 4px;
  margin-top: 8px;
}

.ab-bottom {
  margin-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 8px;
}

.ab-selected {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 32px;
}

.ab-selected-label {
  color: var(--el-text-color-secondary);
}

.ab-selected-empty {
  color: var(--el-text-color-placeholder);
}

.ab-footer {
  text-align: right;
  margin-top: 8px;
}

:deep(.ab-current-row) {
  --el-table-tr-bg-color: var(--el-color-primary-light-9);
}
</style>

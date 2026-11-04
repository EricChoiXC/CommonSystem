<template>
  <div class="role-preview" ref="containerRef">
    <!-- ============ 左侧：地址本树（机构/部门/人员/岗位/虚拟组织，除停用） ============ -->
    <div class="preview-left" :style="{ width: leftWidth + '%' }">
      <el-tree
        ref="treeRef"
        class="preview-tree"
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
    <div class="preview-divider" @mousedown="startDrag" />

    <!-- ============ 右侧：概览表格 + 权限树 ============ -->
    <div class="preview-right" v-loading="loading">
      <el-empty v-if="!preview" description="请在左侧选择用户节点，查看其全部角色与权限" :image-size="80" />
      <template v-else>
        <!-- 单字段列表格：组织架构名称 / 关联组织架构 / 角色列表 / 权限列表 / 权限树 -->
        <el-table :data="previewRows" :show-header="false" border class="preview-table">
          <el-table-column prop="label" label="字段" width="140">
            <template #default="{ row }">
              <span class="preview-label">{{ row.label }}</span>
            </template>
          </el-table-column>
          <el-table-column label="内容">
            <template #default="{ row }">
              <!-- 权限树行：懒加载树，根为选中的组织架构 -->
              <el-tree
                v-if="row.key === 'tree'"
                :key="authTreeKey"
                :props="treeProps"
                lazy
                :load="loadAuthTree"
                node-key="key"
                :default-expanded-keys="authDefaultExpanded"
                :expand-on-click-node="false"
                class="auth-tree"
              >
                <template #default="{ data }">
                  <span class="auth-tree-node">
                    <el-icon v-if="data.type === 'element'" class="auth-node-icon is-element"><component :is="ElementIcon" /></el-icon>
                    <el-icon v-else-if="data.type === 'role'" class="auth-node-icon is-role"><component :is="RoleIcon" /></el-icon>
                    <el-icon v-else class="auth-node-icon is-auth"><component :is="LockIcon" /></el-icon>
                    <span v-if="data.prefix" class="auth-prefix">{{ data.prefix }}：</span>
                    <span>{{ data.name }}</span>
                  </span>
                </template>
              </el-tree>
              <!-- 普通内容行 -->
              <span v-else-if="row.key === 'name'">{{ row.text }}</span>
              <div v-else-if="row.key === 'related'">
                <el-tag v-for="item in row.items" :key="item.fieldId" class="preview-tag" type="info" size="small">
                  {{ item.fieldName }}
                </el-tag>
                <span v-if="!row.items || !row.items.length" class="preview-empty">-</span>
              </div>
              <div v-else-if="row.key === 'roles'">
                <el-tag v-for="role in row.items" :key="role.fieldId" class="preview-tag" :type="role.fieldEnable === false ? 'info' : 'primary'" size="small">
                  {{ role.fieldName }}
                  <span v-if="role.fieldCode" class="preview-sub">{{ role.fieldCode }}</span>
                </el-tag>
                <span v-if="!row.items || !row.items.length" class="preview-empty">-</span>
              </div>
              <div v-else-if="row.key === 'auths'">
                <el-tag v-for="auth in row.items" :key="auth.fieldId" class="preview-tag" type="warning" size="small">
                  {{ auth.fieldName || auth.fieldCode }}
                  <span v-if="auth.fieldName" class="preview-sub">{{ auth.fieldCode }}</span>
                </el-tag>
                <span v-if="!row.items || !row.items.length" class="preview-empty">-</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { OfficeBuilding, School, User, Suitcase, Star, HomeFilled, Collection, Lock, Folder } from '@element-plus/icons-vue'
import { previewRole, previewTreeChildren, queryAddressBookTree } from '@/api/org'

/**
 * 权限预览页：可访问权限 SYS_ORG_ROLE_ADMIN（菜单/路由已按权限过滤，后端 RoleResolver 兜底）。
 * 左侧树形地址本（机构/部门/人员/岗位/虚拟组织，仅启用节点）；点击节点后右侧显示
 * 单字段列表格（组织架构名称 / 关联组织架构 / 角色列表 / 权限列表 / 权限树）。
 * 权限树：根为选中的组织架构，展开显示其直接关联角色（角色再展开显示直接绑定的权限）、
 * 直属上级组织架构与直接关联的虚拟组织架构，按同一逻辑层级递归展开。
 */

const ROOT_NODE_ID = '__root__'
const DEFAULT_LEFT_WIDTH = 25
const MIN_LEFT_WIDTH = 15
const MAX_LEFT_WIDTH = 40

const treeProps = { label: 'fieldName', children: 'children' }
const defaultExpandedKeys = [ROOT_NODE_ID]

/* ============ 分栏拖拽 ============ */
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

const treeRef = ref(null)
let currentElementId = null
let currentElementName = ''

const TYPE_ICON = {
  1: OfficeBuilding,
  2: School,
  3: User,
  4: Suitcase
}

function nodeIcon(data) {
  if (data.fieldId === ROOT_NODE_ID) return HomeFilled
  return data.fieldIsVirtual ? Star : (TYPE_ICON[data.fieldOrgType] || Collection)
}

function nodeIconClass(data) {
  if (data.fieldId === ROOT_NODE_ID) return 'is-root'
  if (data.fieldIsVirtual) return 'is-virtual'
  return { 1: 'is-org', 2: 'is-dept', 3: 'is-user', 4: 'is-post' }[data.fieldOrgType] || ''
}

/** 树懒加载：树根仅返回"根节点"虚拟入口；其余节点按 parentId 加载直接下级（全部类型） */
async function loadTree(node, resolve) {
  if (node.level === 0) {
    resolve([{ fieldId: ROOT_NODE_ID, fieldName: '根节点', fieldOrgType: 0 }])
    return
  }
  const parentId = node.data.fieldId === ROOT_NODE_ID ? null : node.data.fieldId
  try {
    const res = await queryAddressBookTree({
      orgTypes: [1, 2, 3, 4],
      limitRange: [],
      parentId
    })
    resolve(res.data || [])
  } catch (error) {
    ElMessage.error(error.message || '组织架构树加载失败')
    resolve([])
  }
}

async function handleTreeNodeClick(data) {
  if (data.fieldId === ROOT_NODE_ID) return
  // 仅「用户」（人员类型且非虚拟组织）节点支持权限预览，其他节点不加载/不清空右侧
  if (data.fieldOrgType !== 3 || data.fieldIsVirtual) {
    currentElementId = null
    currentElementName = ''
    preview.value = null
    return
  }
  currentElementId = data.fieldId
  currentElementName = data.fieldName
  await loadPreview(data.fieldId)
}

/* ============ 右侧概览 ============ */

const preview = ref(null)
const loading = ref(false)

const previewRows = computed(() => {
  if (!preview.value) return []
  const element = preview.value.element || {}
  return [
    { key: 'name', label: '组织架构名称', text: element.fieldName || '-' },
    { key: 'related', label: '关联组织架构', items: preview.value.relatedOrgs || [] },
    { key: 'roles', label: '角色列表', items: preview.value.roles || [] },
    { key: 'auths', label: '权限列表', items: preview.value.auths || [] },
    { key: 'tree', label: '权限树' }
  ]
})

async function loadPreview(elementId) {
  loading.value = true
  try {
    const res = await previewRole(elementId)
    preview.value = res.data
    resetAuthTree()
  } catch (error) {
    ElMessage.error(error.message || '权限预览数据加载失败')
    preview.value = null
  } finally {
    loading.value = false
  }
}

/* ============ 权限树（懒加载，根为选中的组织架构） ============ */

const authTreeKey = ref(0)
const authDefaultExpanded = ref([])

/** 重置权限树：重新挂载并默认展开根节点 */
function resetAuthTree() {
  if (!currentElementId) {
    authDefaultExpanded.value = []
    return
  }
  authTreeKey.value += 1
  authDefaultExpanded.value = [`element:${currentElementId}`]
}

/** 权限树懒加载：树根返回选中组织架构；其余按节点 type 加载子节点 */
async function loadAuthTree(node, resolve) {
  if (node.level === 0) {
    if (!currentElementId) {
      resolve([])
      return
    }
    resolve([
      {
        key: `element:${currentElementId}`,
        type: 'element',
        id: currentElementId,
        name: currentElementName,
        prefix: ''
      }
    ])
    return
  }
  const { type, id } = node.data
  try {
    const res = await previewTreeChildren({ type, id })
    const children = (res.data || []).map((child) => ({
      ...child,
      key: `${child.type}:${child.id}`
    }))
    resolve(children)
  } catch (error) {
    ElMessage.error(error.message || '权限树加载失败')
    resolve([])
  }
}

onMounted(() => {
  // 默认点中"根节点"，由用户点击具体节点后加载右侧数据
})

/* ============ 图标常量（供模板使用） ============ */
const ElementIcon = Folder
const RoleIcon = User
const LockIcon = Lock
</script>

<script>
export default { name: 'RolePreviewView' }
</script>

<style scoped>
.role-preview {
  display: flex;
  height: 100%;
  min-height: 480px;
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}

/* —— 左侧树 —— */
.preview-left {
  flex-shrink: 0;
  min-width: 160px;
  overflow: auto;
  padding: 8px;
}

.preview-tree {
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

.tree-node-icon.is-root { color: #409eff; }
.tree-node-icon.is-org { color: #e6a23c; }
.tree-node-icon.is-dept { color: #67c23a; }
.tree-node-icon.is-user { color: #409eff; }
.tree-node-icon.is-post { color: #909399; }
.tree-node-icon.is-virtual { color: #f56c6c; }

.tree-node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* —— 拖拽条 —— */
.preview-divider {
  width: 5px;
  flex-shrink: 0;
  cursor: col-resize;
  background: var(--el-border-color-lighter);
  transition: background-color 0.2s;
}

.preview-divider:hover {
  background: #409eff;
}

/* —— 右侧区域 —— */
.preview-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 12px;
  overflow: auto;
}

.preview-table {
  width: 100%;
}

.preview-label {
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.preview-tag {
  margin: 2px 6px 2px 0;
}

.preview-sub {
  margin-left: 6px;
  font-size: 12px;
  opacity: 0.75;
}

.preview-empty {
  color: var(--el-text-color-placeholder);
}

/* —— 权限树（嵌入表格单元格） —— */
.auth-tree {
  min-height: 60px;
}

.auth-tree-node {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.auth-node-icon {
  font-size: 15px;
  flex-shrink: 0;
}

.auth-node-icon.is-element { color: #409eff; }
.auth-node-icon.is-role { color: #67c23a; }
.auth-node-icon.is-auth { color: #e6a23c; }

.auth-prefix {
  color: var(--el-text-color-secondary);
}
</style>

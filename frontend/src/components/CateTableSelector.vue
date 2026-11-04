<template>
  <div class="cate-table-selector">
    <!-- 触发入口：只读展示当前选中值，点击打开弹窗 -->
    <el-input
      :model-value="fieldName"
      placeholder="请选择"
      readonly
      clearable
      @clear="handleClear"
      @click="openDialog"
    >
      <template #append>
        <el-button @click.stop="openDialog">选择</el-button>
      </template>
    </el-input>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="960px"
      append-to-body
      destroy-on-close
    >
      <div class="cts-body">
        <!-- 左侧：分类树（懒加载，选中项作为右侧主表查询条件；「全部」清除分类条件） -->
        <div class="cts-tree">
          <div class="cts-tree-title">分类</div>
          <div
            class="cts-tree-all"
            :class="{ active: !currentCateId }"
            @click="handleAllClick"
          >
            全部
          </div>
          <el-tree
            ref="treeRef"
            class="cts-tree-inner"
            :props="treeProps"
            lazy
            :load="loadTree"
            node-key="fieldId"
            highlight-current
            :expand-on-click-node="false"
            @node-click="handleTreeClick"
          />
        </div>

        <!-- 右侧：主表列表（名称过滤 + 分页 + 勾选） -->
        <div class="cts-table">
          <div class="cts-filter">
            <el-input
              v-model="keyword"
              placeholder="请输入名称"
              clearable
              @keyup.enter="handleQuery"
              @clear="handleQuery"
            >
              <template #append>
                <el-button @click="handleQuery">查询</el-button>
              </template>
            </el-input>
          </div>
          <el-table
            ref="tableRef"
            v-loading="loading"
            :data="list"
            border
            stripe
            row-key="fieldId"
            height="320"
            :highlight-current-row="!multi"
            @selection-change="handleSelectionChange"
            @row-click="handleRowClick"
          >
            <template #empty>
              <el-empty description="暂无数据" :image-size="60" />
            </template>
            <el-table-column v-if="multi" type="selection" width="42" />
            <el-table-column
              v-for="col in columns"
              :key="col.prop"
              :prop="col.prop"
              :label="col.label"
              :width="col.width"
              :min-width="col.minWidth"
              :formatter="col.formatter"
              show-overflow-tooltip
            />
          </el-table>
          <el-pagination
            v-model:current-page="queryPage"
            v-model:page-size="queryPageSize"
            class="cts-pagination"
            :total="total"
            layout="total, prev, pager, next, sizes"
            :page-sizes="[10, 20, 50, 100]"
            @current-change="loadTable"
            @size-change="handleSizeChange"
          />
        </div>
      </div>

      <!-- 已选项展示 -->
      <div class="cts-selected">
        <span class="cts-selected-label">已选：</span>
        <el-tag
          v-for="item in selected"
          :key="item.fieldId"
          closable
          @close="removeSelected(item)"
        >
          {{ item.fieldName }}
        </el-tag>
        <span v-if="!selected.length" class="cts-selected-empty">未选择</span>
      </div>

      <template #footer>
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/index'

/**
 * 分类表格选择器组件：先按树形分类筛选主表，再勾选主表记录回填。
 * 与 TreeSelector.vue（直接选择分类节点）不同：左侧分类树懒加载（请求体携带
 * fieldParentId，根级为空）查询直接下级，选中分类后作为主表指定字段（categoryField，
 * 如业务单位 fieldCateId / 项目 fieldCategoryId）的等值查询条件；「全部」清除分类条件；
 * 右侧主表列表支持名称过滤（fieldName 模糊）与分页，勾选主表记录（多选跨页累积），
 * 确认后按分隔符拼接 fieldId / fieldName 回填。当前适用：业务单位+单位分类、项目+项目分类。
 */
const props = defineProps({
  /** 关联的 id 值字段，必填 */
  fieldId: { type: String, default: '' },
  /** 关联的名称值字段，必填 */
  fieldName: { type: String, default: '' },
  /** 分类树懒加载接口 url，必填（POST，请求体 { fieldParentId, ...treeParams }） */
  treeUrl: { type: String, required: true },
  /** 主表列表接口 url，必填（POST，请求体 { body: { [categoryField], fieldName, ...tableParams }, pages, sortField, sortOrder }） */
  tableUrl: { type: String, required: true },
  /** 主表分类条件字段名（如业务单位 fieldCateId / 项目 fieldCategoryId），必填 */
  categoryField: { type: String, required: true },
  /** 右侧列表显示列配置 [{ prop, label, width?, minWidth?, formatter? }]，必填 */
  columns: { type: Array, required: true },
  /** 树节点名称显示字段，默认 fieldName */
  labelField: { type: String, default: 'fieldName' },
  /** 是否多选，默认 false（仅「其他xxx」等多值字段显式传 multi） */
  multi: { type: Boolean, default: false },
  /** 多选的分隔符，默认 ';'，要求仅限符号 */
  septarator: { type: String, default: ';' },
  /** 请求树节点时额外携带的固定参数（如 orgTypes 等），与 fieldParentId 合并进请求体 */
  treeParams: { type: Object, default: () => ({}) },
  /** 请求主表列表时额外携带的固定参数（合并进 body，如 fieldStatus 等） */
  tableParams: { type: Object, default: () => ({}) },
  /** 确认选择后的回调函数 callback(fieldId, fieldName, selectedList) */
  callback: { type: Function, default: null }
})
const emit = defineEmits(['update:fieldId', 'update:fieldName'])

const dialogVisible = ref(false)
const dialogTitle = computed(() => (props.multi ? '选择（可多选）' : '选择'))

/* ============ 左侧分类树 ============ */

const treeRef = ref(null)
/** 懒加载节点；数据中标记 isLeaf: true 时视为叶子（不显示展开箭头） */
const treeProps = computed(() => ({
  label: props.labelField,
  isLeaf: 'isLeaf'
}))

/** 当前选中分类 id（空=全部/根级） */
const currentCateId = ref('')
const currentCateName = ref('')

/** 分类树懒加载：根级按 fieldParentId 定位起始层级，子节点按 node.fieldId 查询直接下级 */
async function loadTree(node, resolve) {
  const parentId = node.level === 0 ? '' : node.data.fieldId
  try {
    const res = await request.post(props.treeUrl, {
      fieldParentId: parentId,
      ...props.treeParams
    })
    resolve(res.data || [])
    if (node.level === 0) {
      // 根级加载完成后：展开虚拟根节点，让第一层数据直接可见（懒加载节点默认折叠）
      nextTick(() => {
        expandRoot()
      })
    }
  } catch (error) {
    ElMessage.error(error.message || '分类树加载失败')
    resolve([])
  }
}

function expandRoot() {
  const root = treeRef.value && treeRef.value.store && treeRef.value.store.root
  if (root && !root.expanded) {
    treeRef.value.expandNode(root)
  }
}

/** 树节点点击：选中分类作为主表查询条件并刷新右侧列表 */
function handleTreeClick(data) {
  currentCateId.value = data.fieldId
  currentCateName.value = data.fieldName || ''
  queryPage.value = 1
  loadTable()
}

/** 「全部」入口：清除分类筛选并刷新右侧列表（返回全量主表数据） */
function handleAllClick() {
  currentCateId.value = ''
  currentCateName.value = ''
  if (treeRef.value) {
    treeRef.value.setCurrentKey(null)
  }
  queryPage.value = 1
  loadTable()
}

/* ============ 右侧主表列表 ============ */

const tableRef = ref(null)
const keyword = ref('')
const loading = ref(false)
const list = ref([])
const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)

/* 已选项：Map(id → row) 跨页累积；selected 为响应式展示列表 */
const checkedMap = new Map()
const selected = ref([])
const selectedBackup = ref([])

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

function openDialog() {
  selected.value = splitSelected(props.fieldId, props.fieldName)
  selectedBackup.value = selected.value.map((item) => ({ ...item }))
  checkedMap.clear()
  selected.value.forEach((item) => checkedMap.set(item.fieldId, item))
  keyword.value = ''
  queryPage.value = 1
  dialogVisible.value = true
  loadTable()
}

function handleQuery() {
  queryPage.value = 1
  loadTable()
}

function handleSizeChange() {
  queryPage.value = 1
  loadTable()
}

async function loadTable() {
  loading.value = true
  try {
    const body = { ...props.tableParams }
    if (currentCateId.value) {
      body[props.categoryField] = currentCateId.value
    }
    if (keyword.value) {
      body.fieldName = keyword.value
    }
    const res = await request.post(props.tableUrl, {
      body,
      pages: { pageNo: queryPage.value, pageSize: queryPageSize.value, needPage: true },
      sortField: 'createTime',
      sortOrder: 'desc'
    })
    list.value = res.data || []
    total.value = (res.metadata && res.metadata.total) || list.value.length
    nextTick(() => restoreChecked())
  } catch (error) {
    ElMessage.error(error.message || '查询失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 勾选回显：多选勾选已选行、单选高亮已选行（仅当前页已加载数据可回显，跨页数据无法定位） */
function restoreChecked() {
  if (!props.multi) {
    const row = list.value.find((r) => r.fieldId === (selected.value[0] || {}).fieldId)
    if (row && tableRef.value) {
      tableRef.value.setCurrentRow(row)
    }
    return
  }
  if (!tableRef.value) {
    return
  }
  list.value.forEach((row) => {
    if (checkedMap.has(row.fieldId)) {
      tableRef.value.toggleRowSelection(row, true)
    }
  })
}

/** 多选：勾选变化时同步已选项（先移除当前页行旧记录再写入，实现跨页累积） */
function handleSelectionChange(rows) {
  if (!props.multi) {
    return
  }
  list.value.forEach((row) => checkedMap.delete(row.fieldId))
  rows.forEach((row) => checkedMap.set(row.fieldId, row))
  selected.value = Array.from(checkedMap.values())
}

/** 单选：点击行即选中 */
function handleRowClick(row) {
  if (props.multi) {
    return
  }
  selected.value = [row]
}

function removeSelected(item) {
  checkedMap.delete(item.fieldId)
  selected.value = Array.from(checkedMap.values())
  if (props.multi && tableRef.value) {
    const row = list.value.find((r) => r.fieldId === item.fieldId)
    if (row) {
      tableRef.value.toggleRowSelection(row, false)
    }
  }
}

function handleConfirm() {
  const list = selected.value
  const ids = list.map((s) => s.fieldId).join(props.septarator)
  const names = list.map((s) => s.fieldName).join(props.septarator)
  emit('update:fieldId', ids)
  emit('update:fieldName', names)
  if (typeof props.callback === 'function') {
    props.callback(ids, names, list.map((s) => ({ ...s })))
  }
  dialogVisible.value = false
}

function handleCancel() {
  selected.value = selectedBackup.value.map((item) => ({ ...item }))
  dialogVisible.value = false
}

function handleClear() {
  emit('update:fieldId', '')
  emit('update:fieldName', '')
}
</script>

<style scoped>
.cate-table-selector {
  width: 100%;
}

.cts-body {
  display: flex;
  gap: 10px;
}

.cts-tree {
  width: 300px;
  flex-shrink: 0;
  max-height: 400px;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 6px;
}

.cts-tree-title {
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--el-text-color-primary);
}

.cts-tree-all {
  cursor: pointer;
  padding: 4px 8px;
  margin-bottom: 4px;
  border-radius: 4px;
  color: var(--el-text-color-regular);
}

.cts-tree-all:hover {
  background: var(--el-fill-color-light);
}

.cts-tree-all.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.cts-tree-inner {
  flex: 1;
  overflow: auto;
}

.cts-table {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.cts-filter {
  margin-bottom: 8px;
}

.cts-pagination {
  margin-top: 8px;
  justify-content: flex-end;
}

.cts-selected {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 32px;
  margin-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 8px;
}

.cts-selected-label {
  color: var(--el-text-color-secondary);
}

.cts-selected-empty {
  color: var(--el-text-color-placeholder);
}
</style>

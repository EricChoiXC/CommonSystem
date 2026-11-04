<template>
  <div class="tree-selector">
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
      width="560px"
      append-to-body
      destroy-on-close
    >
      <!-- 树形数据区：懒加载，无筛选区，节点按 fieldParentId 查询直接下级 -->
      <div class="ts-tree-wrap">
        <el-tree
          ref="treeRef"
          :props="treeProps"
          lazy
          :load="loadNode"
          node-key="fieldId"
          :highlight-current="!multi"
          :show-checkbox="multi"
          :check-strictly="checkStrictly"
          @node-click="handleNodeClick"
          @check="handleCheck"
        />
      </div>

      <!-- 已选项展示 -->
      <div class="ts-selected">
        <span class="ts-selected-label">已选：</span>
        <el-tag
          v-for="item in selected"
          :key="item.fieldId"
          closable
          @close="removeSelected(item)"
        >
          {{ item.fieldName }}
        </el-tag>
        <span v-if="!selected.length" class="ts-selected-empty">未选择</span>
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
 * 树形选择器组件：提供对树形数据表数据的选择
 * 在 Selector.vue 基础上改造：列表查询改为对树形数据表的懒加载查询，
 * 请求体携带 fieldParentId（上级 id，根级为空）查询直接下级；
 * 移除条件搜索区；多选时按勾选节点收集，确认后回调。
 */
const props = defineProps({
  /** 关联的 id 值字段，必填 */
  fieldId: { type: String, default: '' },
  /** 关联的名称值字段，必填 */
  fieldName: { type: String, default: '' },
  /** 树节点懒加载查询接口 url，必填（POST，请求体 { fieldParentId, ...params }） */
  url: { type: String, required: true },
  /** 上级 id 字段入参：弹窗打开时树从该上级节点的直接下级开始加载；为空则从根级（fieldParentId 为空）加载 */
  fieldParentId: { type: String, default: '' },
  /** 节点名称显示字段，默认 fieldName */
  labelField: { type: String, default: 'fieldName' },
  /** 是否多选，默认 false */
  multi: { type: Boolean, default: false },
  /** 多选的分隔符，默认 ';'，要求仅限符号 */
  septarator: { type: String, default: ';' },
  /** 多选时父子节点勾选是否联动，默认 true（严格模式：勾选父节点不影响子节点） */
  checkStrictly: { type: Boolean, default: true },
  /** 请求树节点时额外携带的固定参数（如 orgTypes 等），与 fieldParentId 合并进请求体 */
  params: { type: Object, default: () => ({}) },
  /** 确认选择后的回调函数 callback(fieldId, fieldName, selectedList) */
  callback: { type: Function, default: null }
})
const emit = defineEmits(['update:fieldId', 'update:fieldName'])

const dialogVisible = ref(false)
const dialogTitle = computed(() => (props.multi ? '选择（可多选）' : '选择'))

const treeRef = ref(null)
/** 懒加载节点；数据中标记 isLeaf: true 时视为叶子（不显示展开箭头） */
const treeProps = computed(() => ({
  label: props.labelField,
  isLeaf: 'isLeaf'
}))

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
  dialogVisible.value = true
}

/** 树节点懒加载：根级按 fieldParentId 定位起始层级，子节点按 node.fieldId 查询直接下级 */
async function loadNode(node, resolve) {
  const parentId = node.level === 0 ? props.fieldParentId || '' : node.data.fieldId
  try {
    const res = await request.post(props.url, {
      fieldParentId: parentId,
      ...props.params
    })
    const children = res.data || []
    resolve(children)
    if (node.level === 0) {
      // 根级加载完成后：默认展开第一层并回显已选项（深层未加载节点无法回显）
      nextTick(() => {
        restoreSelected()
        expandRoot()
      })
    }
  } catch (error) {
    ElMessage.error(error.message || '树节点加载失败')
    resolve([])
  }
}

/** 回显：多选勾选已选节点，单选高亮已选节点 */
function restoreSelected() {
  if (!treeRef.value) {
    return
  }
  if (props.multi) {
    selected.value.forEach((item) => treeRef.value.setChecked(item.fieldId, true, false))
  } else if (selected.value.length) {
    treeRef.value.setCurrentKey(selected.value[0].fieldId)
  }
}

/** 展开虚拟根节点，让第一层数据直接可见（懒加载节点默认折叠） */
function expandRoot() {
  const root = treeRef.value && treeRef.value.store && treeRef.value.store.root
  if (root && !root.expanded) {
    treeRef.value.expandNode(root)
  }
}

/** 单选：点击节点即选中；多选：仅展开/折叠，勾选走 checkbox */
function handleNodeClick(data) {
  if (props.multi) {
    return
  }
  selected.value = [data]
}

/** 多选：勾选/取消后实时同步已选项（checkStrictly 下无半选节点） */
function handleCheck() {
  if (!props.multi || !treeRef.value) {
    return
  }
  selected.value = treeRef.value.getCheckedNodes(false)
}

function removeSelected(item) {
  selected.value = selected.value.filter((s) => s.fieldId !== item.fieldId)
  if (props.multi && treeRef.value) {
    treeRef.value.setChecked(item.fieldId, false, false)
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
.tree-selector {
  width: 100%;
}

.ts-tree-wrap {
  max-height: 360px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 6px;
}

.ts-selected {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 32px;
  margin-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 8px;
}

.ts-selected-label {
  color: var(--el-text-color-secondary);
}

.ts-selected-empty {
  color: var(--el-text-color-placeholder);
}
</style>

<template>
  <!--
    表单设计器 · 左侧物料栏（F15-FE-008，UI 分册 §4.3）。
    按 category 分组展示物料卡片（数据 12 / 内容 4 / 布局 2，注册序即 §1.3 顺序）；
    控制类 permission 仅协议位不注册物料，物料栏不展示（MATERIAL_PANEL_CATEGORIES）。
    两种入画布方式并存：点击卡片插入画布末尾（选中容器时插入其内部末尾，由宿主
    FormDesigner 落位）+ 原生 HTML5 拖拽（dragstart 上抛宿主记录拖拽载荷，画布
    dragover/drop 解析落点）——实现期裁决采用原生 DnD，不引入 vuedraggable/SortableJS
    （R07 收口：画布 DOM 由配置态渲染器递归渲染拥有，零新依赖消除体积/兼容风险）。
    字段总数达 MAX_FIELD_COUNT 卡片置灰 + 提示（保存期服务端权威校验，画布侧交互预判）。

    设计器内部组件：仅表单模块设计器消费，随 FormDesigner 经 components/index.js
    统一出口间接编译覆盖，不单独对外导出。
  -->
  <div class="form-materials">
    <div v-for="group in materialGroups" :key="group.title" class="materials-group">
      <div class="materials-group-title">{{ group.title }}（{{ group.items.length }}）</div>
      <div
        v-for="material in group.items"
        :key="material.type"
        class="material-card"
        :class="{ 'is-disabled': maxReached }"
        :draggable="!maxReached"
        :title="maxReached ? `字段总数已达上限 ${maxFieldCount}，无法继续添加` : `点击插入画布，或拖拽至画布目标位置（${material.type}）`"
        @click="handleClick(material)"
        @dragstart="handleDragStart(material, $event)"
        @dragend="handleDragEnd"
      >
        <el-icon class="material-icon"><component :is="iconOf(material)" /></el-icon>
        <span class="material-label">{{ material.label }}</span>
        <span class="material-type">{{ material.type }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  ArrowDown, Avatar, Calendar, Collection, Connection, Document, Edit, Finished, Folder,
  Grid, Histogram, List, MagicStick, Menu, Paperclip, Picture, Switch, Tickets,
} from '@element-plus/icons-vue'
import { getMaterialsByCategory, MATERIAL_PANEL_CATEGORIES, FORM_CATEGORIES } from '../registry.js'
import { MAX_FIELD_COUNT } from './designerSupport.js'

/**
 * 物料栏 props：
 * - maxReached：字段总数达 MAX_FIELD_COUNT（宿主按画布字段数计算传入）——卡片置灰，
 *   点击与拖拽均拦截；
 */
const props = defineProps({
  maxReached: { type: Boolean, default: false },
})

const emit = defineEmits(['insert', 'drag-start', 'drag-end'])

/** 分组标题（§4.3：数据 / 内容 / 布局；CONTROL 不展示） */
const CATEGORY_TITLES = {
  [FORM_CATEGORIES.DATA]: '数据',
  [FORM_CATEGORIES.CONTENT]: '内容',
  [FORM_CATEGORIES.LAYOUT]: '布局',
}

/** 物料分组（按 registry 注册序，即功能设计 §1.3 清单顺序） */
const materialGroups = computed(() =>
  MATERIAL_PANEL_CATEGORIES.map((category) => ({
    title: CATEGORY_TITLES[category] || category,
    items: getMaterialsByCategory(category),
  })),
)

/** 图标名 → 图标组件映射（物料 icon 为字符串名，卡片渲染时解析；未知名回落 Edit） */
const ICON_MAP = {
  ArrowDown, Avatar, Calendar, Collection, Connection, Document, Edit, Finished, Folder,
  Grid, Histogram, List, MagicStick, Menu, Paperclip, Picture, Switch, Tickets,
}

function iconOf(material) {
  return ICON_MAP[material.icon] || Edit
}

const maxFieldCount = MAX_FIELD_COUNT

/** 点击插入（宿主落位：选中容器时插入其内部末尾，否则画布末尾，§4.4） */
function handleClick(material) {
  if (props.maxReached) return
  emit('insert', material.type)
}

/** 原生 HTML5 拖拽：dragstart 记录拖拽载荷（宿主持有，dragover 期 dataTransfer.getData 不可读） */
function handleDragStart(material, event) {
  if (props.maxReached) {
    event.preventDefault()
    return
  }
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'copy'
    event.dataTransfer.setData('application/json', JSON.stringify({ kind: 'new', type: material.type }))
  }
  emit('drag-start', material.type)
}

function handleDragEnd() {
  emit('drag-end')
}
</script>

<style scoped>
.form-materials {
  height: 100%;
  padding: 10px;
  overflow-y: auto;
  background: var(--el-fill-color-blank);
}

.materials-group + .materials-group {
  margin-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 10px;
}

.materials-group-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.material-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  margin-bottom: 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
  cursor: grab;
  user-select: none;
}

.material-card:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.material-card:active {
  cursor: grabbing;
}

.material-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.material-card.is-disabled:hover {
  border-color: var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.material-icon {
  flex: none;
  color: var(--el-color-primary);
}

.material-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.material-type {
  flex: none;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}
</style>

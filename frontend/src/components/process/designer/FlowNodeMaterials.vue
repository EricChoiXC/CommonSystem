<template>
  <div class="flow-materials">
    <div v-for="group in materialGroups" :key="group.title" class="materials-group">
      <div class="materials-group-title">{{ group.title }}</div>
      <div
        v-for="item in group.items"
        :key="item.nodeType"
        class="material-item"
        :class="{ 'is-draggable': item.draggable }"
        :title="item.draggable ? '按住拖入画布' : '固定节点：画布自动生成，不可拖拽/删除'"
        @mousedown="handleMouseDown(item, $event)"
      >
        <span class="material-swatch" :style="{ background: item.fill, borderColor: item.stroke }"></span>
        <span class="material-info">
          <span class="material-name">{{ item.label }}</span>
          <span class="material-type">{{ item.nodeType }}</span>
        </span>
        <el-tag v-if="!item.draggable" class="material-fixed-tag" size="small" type="info">固定</el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 流程设计器 · 左侧物料面板（v1_7 / P17-FE-002）
 *
 * 分册 §4.3 冻结口径：按「固定节点」（START/DRAFT/END，展示但不可拖拽新增，画布必有且唯一）
 * 与「可添加节点」（其余 7 类）两组渲染；物料项显示节点中文名 + 类型英文小字。
 * 拖拽走 LogicFlow dnd（lf.dnd.startDrag），本面板不持有画布实例：按下时向宿主画布抛出
 * drag-start 事件（携带物料元数据），由 FlowDesignerCanvas 分配节点键/默认名并启动 dnd。
 *
 * 设计器内部组件：不进 components/index.js 统一出口（分册 §11.2 约定）。
 */
import { computed } from 'vue'
import { FLOW_NODE_MATERIALS } from './flowNodes'

const emit = defineEmits(['drag-start'])

/** 两组物料：固定节点组 / 可添加节点组（FLOW_NODE_MATERIALS 顺序即分册 §4.3 表顺序） */
const materialGroups = computed(() => [
  { title: '固定节点', items: FLOW_NODE_MATERIALS.filter((item) => item.fixed) },
  { title: '可添加节点', items: FLOW_NODE_MATERIALS.filter((item) => !item.fixed) },
])

/**
 * 按下启动拖拽：固定节点拦截（不可拖拽）；可添加节点 preventDefault 防止文本选中后
 * 上抛 drag-start（画布侧经 lf.dnd.startDrag 接管后续指针事件）。
 */
function handleMouseDown(item, event) {
  if (!item.draggable) {
    return
  }
  event.preventDefault()
  emit('drag-start', item)
}
</script>

<style scoped>
.flow-materials {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  padding: 12px;
  overflow-y: auto;
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
}

.materials-group-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.materials-group + .materials-group {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 12px;
}

.material-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  margin-bottom: 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
  cursor: not-allowed;
  user-select: none;
}

.material-item.is-draggable {
  cursor: grab;
}

.material-item.is-draggable:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.material-item:active.is-draggable {
  cursor: grabbing;
}

.material-swatch {
  flex: none;
  width: 14px;
  height: 14px;
  border: 1px solid;
  border-radius: 3px;
}

.material-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  line-height: 1.3;
}

.material-name {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.material-type {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.material-fixed-tag {
  flex: none;
}
</style>

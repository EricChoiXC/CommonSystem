<template>
  <div class="flow-viewer">
    <!-- 图数据缺失降级提示（按节点序号横向排布，分册 §6.2 兼容口径） -->
    <el-alert
      v-if="graph.mode === 'degraded'"
      class="flow-viewer-alert"
      type="warning"
      :closable="false"
      show-icon
      title="图数据缺失，按节点序号排布"
    />

    <!-- 空态：实例无节点 -->
    <el-empty
      v-if="graph.mode === 'empty'"
      class="flow-viewer-empty"
      description="暂无流程节点信息"
      :image-size="80"
    />

    <!-- 只读画布（isSilentMode：禁拖拽/连线/锚点/文本编辑/平移缩放，双击节点弹信息提示） -->
    <div
      v-show="graph.mode !== 'empty'"
      ref="canvasRef"
      class="flow-viewer-canvas"
      :style="{ height: `${height}px` }"
    ></div>

    <!-- 状态图例（分册 §6.1：待流转灰/处理中蓝/完成绿/通过青/驳回红） -->
    <div v-if="showLegend && graph.mode !== 'empty'" class="flow-viewer-legend">
      <span v-for="item in legendItems" :key="item.status" class="legend-item">
        <span class="legend-chip" :style="{ backgroundColor: item.fill, borderColor: item.stroke }"></span>
        <span class="legend-text">{{ item.label }} {{ item.status }}</span>
      </span>
    </div>

    <!-- 双击节点信息提示（只读：不弹配置弹窗，节点名称/键/类型/状态/编号/处理人摘要） -->
    <el-dialog v-model="infoVisible" title="节点信息" width="480px" append-to-body :close-on-click-modal="false">
      <div v-if="infoNode" class="detail-grid">
        <div class="detail-item is-full">
          <div class="detail-label">节点名称</div>
          <div class="detail-value">{{ infoNode.nodeName || '-' }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">节点键</div>
          <div class="detail-value">{{ infoNode.nodeKey || '-' }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">节点类型</div>
          <div class="detail-value">{{ nodeTypeText(infoNode.nodeType) }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">节点状态</div>
          <div class="detail-value">{{ nodeStatusText(infoNode.fieldStatus) }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">节点编号</div>
          <div class="detail-value">{{ infoNode.fieldNo ?? '-' }}</div>
        </div>
        <div class="detail-item is-full">
          <div class="detail-label">处理人摘要</div>
          <div class="detail-value">{{ handlerSummary(infoNode) }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="infoVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 流程实例只读流程图组件（v1_7 / P17-FE-005）
 *
 * 承接《流程v1_7前端UI设计分册》§6.2（只读流程图渲染与状态着色表）、§11.2 组件目录规划：
 * 实例详情页（ExampleDetailView）消费；P17-FE-006 审批面板「流程」卡片复用同一组件
 * （props 预留通用性：nodes + lines + 可选状态色映射 statusColors / workitems / height /
 * showLegend）。**不进 components/index.js 统一出口**（分册 §11.2：设计器子组件仅在流程
 * 模块内部消费），宿主页面相对路径导入。
 *
 * 数据来自宿主注入（本组件不调接口——仓库前端硬约束）：
 *   - nodes：loadProcessExample 响应实例节点 VO（fieldNodeKey/fieldNodeNo/fieldNodeType/fieldStatus）；
 *   - lines：load 响应实例连线快照（可能缺省——旧实例未生成快照时按节点序号降级排布）；
 *   - workitems：load 响应工作项 VO（可选，双击节点「处理人摘要」数据源）。
 *
 * 只读实现方案（安装包源码核实结论，@logicflow/core@2.2.5）：
 *   LogicFlow v2 静默模式（isSilentMode: true）关闭拖拽/连线/锚点/文本编辑/画布平移缩放
 *   等编辑交互，但 NODE_DBCLICK 事件为无条件 emit（min 源码：双击分支不判 isSilentMode，
 *   仅 nodeTextEdit && editable 时才进入文本编辑态）——故沿用与 FlowDesignerCanvas 详情
 *   只读同一方案：「isSilentMode 静默 + 监听 node:dbclick 弹节点信息提示」，无需退化为
 *   「非静默 + editConfig 全禁 + guards 拦截」方案。
 *
 * 自定项登记（分册未冻结细节，P17-DOC-010 走查时可调）：
 *   - 双击节点信息提示用 el-dialog（append-to-body）承载（备选 el-popover 需节点屏幕坐标，
 *     LogicFlow 静默模式无公开锚点定位 API，故选 dialog）；
 *   - 处理人摘要口径：该节点 PROCESSING 工作项处理人（并行多人顿号连接）；无 PROCESSING
 *     时回退 FINISH 工作项处理人；再无显示 '-'（工作项 prop 缺省时恒为 '-'）；
 *   - 画布无工具条：渲染后自动 fitView 适应画布（分册 §7.5 面板复用口径：按容器高度
 *     自适应缩放），不提供手工缩放按钮；
 *   - 关闭画布网格与吸附线（只读展示无编辑语义，网格为设计器编辑辅助）。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import LogicFlow, { PolylineEdge, PolylineEdgeModel } from '@logicflow/core'
import { NODE_TYPE_LABELS, registerFlowNodes } from './designer/flowNodes'
import {
  DEFAULT_STATUS_STYLES,
  NODE_STATUS_LABELS,
  STATUS_LEGEND_ORDER,
  buildExampleGraphData,
} from './designer/exampleGraph'

const props = defineProps({
  /** load 响应实例节点 VO 数组（fieldId/fieldNodeNo/fieldNodeName/fieldNodeType/fieldStatus/fieldNodeKey） */
  nodes: { type: Array, default: () => [] },
  /** load 响应实例连线快照 lines[]（fieldFromKey/fieldToKey/fieldLineType/...）；缺省降级按节点序号排布 */
  lines: { type: Array, default: () => [] },
  /** 实例工作项 VO 数组（可选）：双击节点信息提示「处理人摘要」数据源 */
  workitems: { type: Array, default: () => [] },
  /** 画布高度（px），默认 320 */
  height: { type: Number, default: 320 },
  /**
   * 节点状态色映射覆盖（可选，FE-006 复用预留）：{ WAITING?: { fill, stroke }, ... }，
   * 仅覆盖已登记五态，缺省用内置色板（分册 §6.2 着色表）。
   */
  statusColors: { type: Object, default: null },
  /** 是否显示状态图例（分册 §6.1 图例说明），默认 true */
  showLegend: { type: Boolean, default: true },
})

/** node-dblclick：双击节点投影（nodeKey/nodeType/nodeName/fieldStatus/fieldNo），供宿主感知（面板复用预留） */
const emit = defineEmits(['node-dblclick'])

const canvasRef = ref(null)
let lf = null

/** 状态色板（内置五态 + 宿主 statusColors 覆盖合并） */
const statusStyles = computed(() => {
  const override = props.statusColors || {}
  const merged = {}
  STATUS_LEGEND_ORDER.forEach((status) => {
    merged[status] = { ...DEFAULT_STATUS_STYLES[status], ...(override[status] || {}) }
  })
  return merged
})

/** 图数据（empty/graph/degraded 三模式，见 exampleGraph.js） */
const graph = computed(() => buildExampleGraphData(props.nodes, props.lines, statusStyles.value))

/** 图例项（色板随 statusColors 覆盖联动） */
const legendItems = computed(() =>
  STATUS_LEGEND_ORDER.map((status) => ({
    status,
    label: NODE_STATUS_LABELS[status] || status,
    fill: statusStyles.value[status].fill,
    stroke: statusStyles.value[status].stroke,
  }))
)

/* ==================== 双击节点信息提示 ==================== */

const infoVisible = ref(false)
const infoNode = ref(null)

function openNodeInfo(data) {
  const properties = data?.properties || {}
  infoNode.value = {
    id: data?.id || '',
    nodeKey: properties.nodeKey || data?.id || '',
    nodeType: properties.nodeType || '',
    nodeName: properties.nodeName || '',
    fieldStatus: properties.fieldStatus || '',
    fieldNo: properties.fieldNo ?? null,
  }
  infoVisible.value = true
  emit('node-dblclick', { ...infoNode.value })
}

/** 处理人摘要：PROCESSING 工作项处理人优先，无则回退 FINISH 处理人，再无 '-'（自定口径） */
function handlerSummary(node) {
  const items = (props.workitems || []).filter((w) => w && w.fieldNodeId === node.id)
  if (!items.length) {
    return '-'
  }
  const nameOf = (w) => w.fieldHandlerName || w.fieldHandlerId || '-'
  const processing = items.filter((w) => w.fieldStatus === 'PROCESSING')
  if (processing.length) {
    return processing.map(nameOf).join('、')
  }
  const finished = items.filter((w) => w.fieldStatus === 'FINISH')
  return finished.length ? finished.map(nameOf).join('、') : '-'
}

function nodeTypeText(nodeType) {
  return NODE_TYPE_LABELS[nodeType] || nodeType || '-'
}

function nodeStatusText(status) {
  return NODE_STATUS_LABELS[status] || status || '-'
}

/* ==================== LogicFlow 画布（只读） ==================== */

/**
 * 只读连线 model：properties.style 覆盖连线样式（已流经实线主色 / 未流经浅灰虚线，
 * 样式值由 exampleGraph.buildExampleGraphData 按节点状态推导注入）。
 */
class FlowLineModel extends PolylineEdgeModel {
  getEdgeStyle() {
    const style = super.getEdgeStyle()
    return { ...style, ...(this.properties.style || {}) }
  }
}

function initLogicFlow() {
  if (!canvasRef.value || lf) {
    return
  }
  lf = new LogicFlow({
    container: canvasRef.value,
    grid: false,
    edgeType: 'flow-line',
    textEdit: false,
    snapline: false,
    keyboard: { enabled: false },
    // 静默只读（安装包源码核实：node:dbclick 无条件 emit，见文件头说明）
    isSilentMode: true,
  })
  // 10 类节点注册复用（与设计器同一注册面）；只读连线注册（properties.style 覆盖）
  registerFlowNodes(lf)
  lf.register({ type: 'flow-line', view: PolylineEdge, model: FlowLineModel })
  lf.on('node:dbclick', ({ data }) => openNodeInfo(data))
}

function renderGraph(g) {
  if (!lf) {
    return
  }
  lf.render({ nodes: g.lfNodes, edges: g.lfEdges })
  nextTick(() => {
    try {
      lf?.fitView()
    } catch (error) {
      // 容器尺寸异常时忽略适应画布失败（首次挂载瞬时态）
    }
  })
}

/** 确保画布就绪并渲染（容器随 v-show 恒存在；空态时不初始化） */
function ensureAndRender(g) {
  if (!g || g.mode === 'empty') {
    return
  }
  initLogicFlow()
  renderGraph(g)
}

function destroyLogicFlow() {
  if (lf) {
    lf.destroy()
    lf = null
  }
}

onMounted(() => {
  ensureAndRender(graph.value)
})

watch(graph, (g) => {
  if (g.mode === 'empty') {
    destroyLogicFlow()
  } else {
    nextTick(() => ensureAndRender(g))
  }
})

onBeforeUnmount(() => {
  destroyLogicFlow()
})

/** 适应画布（宿主如需主动触发可调用） */
function fit() {
  try {
    lf?.fitView()
  } catch (error) {
    // 容器尺寸异常时忽略
  }
}

defineExpose({ fit })
</script>

<style scoped>
.flow-viewer {
  width: 100%;
}

.flow-viewer-alert {
  margin-bottom: 8px;
}

.flow-viewer-empty {
  padding: 24px 0;
}

/* LogicFlow 容器需显式高度（height prop 注入）与边框 */
.flow-viewer-canvas {
  width: 100%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
  overflow: hidden;
}

.flow-viewer-canvas :deep(.lf-graph) {
  background: var(--el-bg-color);
}

.flow-viewer-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 8px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-chip {
  flex: none;
  width: 16px;
  height: 10px;
  border: 1px solid;
  border-radius: 2px;
}

.legend-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

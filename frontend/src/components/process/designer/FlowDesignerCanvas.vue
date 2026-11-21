<template>
  <div class="flow-designer" :class="{ 'is-readonly': readonly }">
    <!-- 左：物料面板（只读模式不挂载，分册 §4.2） -->
    <FlowNodeMaterials v-if="!readonly" class="designer-left" @drag-start="handleMaterialDragStart" />

    <!-- 中：工具条 + LogicFlow 画布 + 提示条 -->
    <div class="designer-center">
      <div class="designer-toolbar">
        <span class="toolbar-label">画布工具</span>
        <el-button size="small" :disabled="!lfReady" @click="zoomIn">放大</el-button>
        <el-button size="small" :disabled="!lfReady" @click="zoomOut">缩小</el-button>
        <el-button size="small" :disabled="!lfReady" @click="fit()">适应画布</el-button>
        <template v-if="!readonly">
          <el-button size="small" :disabled="!lfReady || !canUndo" @click="undo">撤销</el-button>
          <el-button size="small" :disabled="!lfReady || !canRedo" @click="redo">重做</el-button>
        </template>
        <span v-if="!readonly && dirty" class="dirty-flag">流程图已修改</span>
      </div>
      <div ref="canvasRef" class="designer-canvas"></div>
      <div class="designer-hints">
        <template v-if="!readonly">
          固定节点（开始/起草/结束）不可删除；从节点锚点拉出连线；双击节点或点【配置节点】打开节点配置；
          点击条件分支/并行分支节点的出边连线或【配置分支】配置分支
        </template>
        <template v-else>只读模式：画布仅支持缩放与平移查看</template>
      </div>
    </div>

    <!-- 右：属性区（选中摘要 + 配置入口） -->
    <div class="designer-right">
      <template v-if="selectedNode">
        <div class="props-title">选中节点</div>
        <div class="props-item">
          <span class="props-label">节点名称</span>
          <span class="props-value" :title="selectedNode.nodeName">{{ selectedNode.nodeName || '-' }}</span>
        </div>
        <div class="props-item">
          <span class="props-label">节点类型</span>
          <span class="props-value">
            {{ nodeTypeLabel(selectedNode.nodeType) }}（{{ selectedNode.nodeType }}）
          </span>
        </div>
        <div class="props-item">
          <span class="props-label">节点键</span>
          <span class="props-value">{{ selectedNode.nodeKey || '-' }}</span>
        </div>
        <template v-if="!readonly">
          <el-button
            v-if="!noNodeConfig"
            class="props-btn"
            type="primary"
            @click="requestNodeConfig(selectedNode)"
          >
            配置节点
          </el-button>
          <div v-else class="props-tip">该节点无节点配置</div>
        </template>
      </template>
      <template v-else-if="selectedEdge">
        <div class="props-title">选中连线</div>
        <div class="props-item">
          <span class="props-label">流转方向</span>
          <span class="props-value">
            {{ selectedEdge.sourceNodeName || selectedEdge.sourceNodeId }} →
            {{ selectedEdge.targetNodeName || selectedEdge.targetNodeId }}
          </span>
        </div>
        <div class="props-item">
          <span class="props-label">连线类型</span>
          <span class="props-value">
            {{ lineTypeLabel(selectedEdge.lineType) }}（{{ selectedEdge.lineType }}）
          </span>
        </div>
        <div class="props-item">
          <span class="props-label">分支名称</span>
          <span class="props-value">{{ selectedEdge.branchName || '-' }}</span>
        </div>
        <div class="props-item">
          <span class="props-label">分支序号</span>
          <span class="props-value">{{ selectedEdge.branchOrder ?? '-' }}</span>
        </div>
        <div class="props-item">
          <span class="props-label">默认分支</span>
          <span class="props-value">{{ selectedEdge.isDefault ? '是' : '否' }}</span>
        </div>
        <el-button
          v-if="!readonly && selectedEdge.branchEditable"
          class="props-btn"
          type="primary"
          @click="openBranchDialog(selectedEdge)"
        >
          配置分支
        </el-button>
      </template>
      <div v-else class="props-empty">
        {{ readonly ? '选中节点或连线查看摘要' : '点击画布中的节点或连线查看属性摘要；双击节点打开配置弹窗' }}
      </div>
    </div>

    <!-- 分支配置弹窗（内置本组件：CONDITION/FORK 出边连线配置，分册 §4.5） -->
    <FlowBranchConfigDialog
      v-model:visible="branchDialog.visible"
      :node-key="branchDialog.nodeKey"
      :node-name="branchDialog.nodeName"
      :node-type="branchDialog.nodeType"
      :branches="branchDialog.branches"
      :formula-vars="formulaVars"
      @confirm="applyBranchResult"
    />

    <!-- 节点配置弹窗（内置本组件：APPROVE/CC/MANUAL/ROBORT/FORK 多标签配置，分册 §5） -->
    <FlowNodeConfigDialog
      v-model:visible="nodeDialog.visible"
      :node="nodeDialog.node"
      :nodes="nodeDialog.context.nodes"
      :lines="nodeDialog.context.lines"
      :field-options="fieldOptions"
      :ops-options="opsOptions"
      :formula-vars="formulaVars"
      @confirm="applyNodeConfigResult"
    />
  </div>
</template>

<script setup>
/**
 * 流程设计器 · 画布容器（v1_7 / P17-FE-002）
 *
 * 三段横向布局（分册 §4.2）：左侧物料面板 FlowNodeMaterials + 中部 LogicFlow 画布 + 右侧属性区。
 * 供 P17-FE-004 页面（TemplateFormView create/edit/detail）整体嵌入；详情只读经 readonly
 * 复用同一组件：不挂物料面板与编辑交互、LogicFlow isSilentMode 仅渲染。
 *
 * 本组件自身不调用任何接口（数据经 props 注入、结果经 expose 取出）——接口一律由宿主页面
 * 经 src/api/process.js 具名函数发出（仓库前端硬约束）。
 *
 * 交互契约（分册 §4.4 冻结）：
 *   - 拖拽入画布：物料面板按下 → lf.dnd.startDrag（落点生成节点，分配最小可用键 N4…，
 *     默认命名「审批节点N」——序号仅展示不参与流转）；
 *   - 连线：节点四锚点按住拉出；出边数达上限拦截（START/DRAFT/APPROVE/CC/ROBORT/JOIN 为 1，
 *     提示「该节点类型只允许一条流出连线」；END 为 0，提示不允许拉出连线）；
 *   - 删除：Delete 键 / 画布删除，固定节点 N1/N2/N3 经 guards.beforeDelete 拦截，提示
 *     「开始/起草/结束节点为固定节点，不可删除」；删边自由；
 *   - 节点配置：双击节点或右侧【配置节点】→ 打开内置 FlowNodeConfigDialog（P17-FE-003，
 *     分册 §5 多标签：APPROVE 四标签 / CC 两标签 / MANUAL·ROBORT·FORK 各一标签），同时
 *     emit node-config-request（固定节点 START/DRAFT/END、CONDITION、JOIN 不触发弹窗，
 *     提示「该节点无节点配置」；事件供宿主感知与联动）；弹窗【确定】经 confirm 回抛
 *     配置字段，画布 updateNodeProperties 局部合并回写并置脏（节点其余 properties 原样保留）；
 *     候选（驳回目标/流向决策/默认流向）按打开时图上下文快照计算；
 *   - 分支配置：点击 CONDITION/FORK 出边连线或右侧【配置分支】→ 打开内置
 *     FlowBranchConfigDialog（分册 §4.5），同时 emit branch-config-request 通知宿主
 *     （弹窗为画布内置、宿主无需重复打开，事件供宿主感知与联动）；
 *   - 工具条：放大/缩小/适应画布/撤销/重做（撤销重做仅限本次编辑会话，undo 栈在每次
 *     props 重渲染时复位基线）；
 *   - 脏标记：任何图结构改动（节点/连线增删、配置回写）置 dirty，坐标拖动不落库（T-08）
 *     不置脏；expose isDirty() 供宿主页面关闭/路由离开保护。
 *
 * 自定项登记（分册未冻结细节，P17-DOC-010 走查时可调）：
 *   - 边文本 branchLineText() 规则见 graphTransform.js（默认分支追加「（默认）」）；
 *   - 两节点间重复连线、自环连线拦截（结构上无意义，保存期服务端亦会拒绝）；
 *   - END 锚点仍显示（flowNodes 未区分锚点），拉线被出边约束拦截并提示；
 *   - 撤销/重做按钮态经 LogicFlow history:change 事件驱动（History 自带 debounce 100ms）。
 */
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import LogicFlow from '@logicflow/core'
import FlowNodeMaterials from './FlowNodeMaterials.vue'
import FlowBranchConfigDialog from './FlowBranchConfigDialog.vue'
import FlowNodeConfigDialog from './FlowNodeConfigDialog.vue'
import { NODE_TYPE_LABELS, registerFlowNodes } from './flowNodes'
import {
  BRANCH_SOURCE_TYPES,
  FIXED_NODE_TYPES,
  LINE_TYPE_BY_SOURCE,
  LINE_TYPE_LABELS,
  NO_NODE_CONFIG_TYPES,
  OUT_EDGE_LIMITS,
  branchLineText,
  defaultNodeName,
  genLocalId,
  isBranchSource,
  nextNodeKey,
  normalizeBranchOrder,
  toGraphData,
  toSavePayload,
  validateGraph as validateGraphData,
} from './graphTransform'

const props = defineProps({
  /** load 响应图数据 · 节点（接口清单 §4.2 平铺 VO，无坐标，画布自动分层布局） */
  nodes: { type: Array, default: () => [] },
  /** load 响应图数据 · 连线（接口清单 §4.2 lines[]） */
  lines: { type: Array, default: () => [] },
  /** 详情只读：不挂物料面板与编辑交互，LogicFlow isSilentMode 仅渲染（分册 §4.1/4.2） */
  readonly: { type: Boolean, default: false },
  /** 公式上下文：模板所属分类（process_template 配置项 key，分册 §4.2 顶部字段口径） */
  fieldModelName: { type: String, default: '' },
  /**
   * 公式上下文：分支条件公式的变量声明（元素 { code, type?, label? }）。宿主页
   * （P17-FE-004）由分类字段字典 process_template.${fieldModelName}.fields 换算传入；
   * 缺省时 FormulaDesigner 走宽松模式，占位符合法性权威校验在后端。
   */
  formulaVars: { type: Array, default: () => [] },
  /**
   * 审批人字段字典（v1_7 / P17-FE-003 新增 prop，宿主 FE-004 传入）：分类
   * process_template.${fieldModelName}.fields（key=字段属性名，value=中文），
   * 供节点配置弹窗「审批人字段」下拉；缺省为空（下拉无可选项）。
   */
  fieldOptions: { type: Object, default: () => ({}) },
  /**
   * 操作标识字典（v1_7 / P17-FE-003 新增 prop，宿主 FE-004 传入）：分类
   * process_template.${fieldModelName}.ops（key=操作标识，value=中文），供节点配置
   * 弹窗「审批权限」标签开关键集；缺省为空（标签提示未配置操作标识）。
   */
  opsOptions: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['node-config-request', 'branch-config-request', 'graph-change'])

/* ==================== 状态 ==================== */

const canvasRef = ref(null)
const lfReady = ref(false)
const dirty = ref(false)
const canUndo = ref(false)
const canRedo = ref(false)
/** 右侧属性区：选中节点/连线摘要（非响应式 lf 实例挂 setup 普通变量） */
const selectedNode = ref(null)
const selectedEdge = ref(null)
/** 内置分支配置弹窗状态 */
const branchDialog = reactive({
  visible: false,
  nodeKey: '',
  nodeName: '',
  nodeType: 'CONDITION',
  branches: [],
})

/** 内置节点配置弹窗状态（P17-FE-003：node=节点摘要，context=打开时图上下文快照） */
const nodeDialog = reactive({
  visible: false,
  node: null,
  context: { nodes: [], lines: [] },
})

let lf = null

/** 固定节点键集合（N1/N2/N3，删除保护键兜底；类型判定以 properties.nodeType 为主） */
const FIXED_NODE_KEYS = new Set(['N1', 'N2', 'N3'])

/* ==================== 初始化与渲染 ==================== */

onMounted(() => {
  initLogicFlow()
  renderFromProps()
})

onBeforeUnmount(() => {
  if (lf) {
    lf.destroy()
    lf = null
  }
  lfReady.value = false
})

/** 构造 LogicFlow 实例：注册 10 类节点、绑定画布事件（readonly 变化时整体重建） */
function initLogicFlow() {
  if (!canvasRef.value || lf) {
    return
  }
  lf = new LogicFlow({
    container: canvasRef.value,
    grid: { size: 20, visible: true },
    keyboard: { enabled: true },
    edgeType: 'polyline',
    // 节点名称/分支文本由配置弹窗与分支弹窗统一维护，关闭画布内自由文本编辑（自定项）
    textEdit: false,
    history: true,
    snapline: true,
    isSilentMode: props.readonly,
    guards: { beforeDelete: handleBeforeDelete },
  })
  registerFlowNodes(lf)
  bindCanvasEvents()
  lfReady.value = true
}

/** props 图数据变化 → 重渲染（宿主 load 回填/保存后刷新；readonly 变化时先重建实例） */
watch(
  () => [props.nodes, props.lines],
  () => {
    renderFromProps()
  }
)

watch(
  () => props.readonly,
  () => {
    if (lf) {
      lf.destroy()
      lf = null
      lfReady.value = false
    }
    dirty.value = false
    selectedNode.value = null
    selectedEdge.value = null
    initLogicFlow()
    renderFromProps()
  }
)

/** load 数据 → 画布渲染（编辑模式空图播种固定三节点，详见 graphTransform.toGraphData） */
function renderFromProps() {
  if (!lf) {
    return
  }
  const data = toGraphData(props.nodes || [], props.lines || [], { seedFixedTrio: !props.readonly })
  lf.render({ nodes: data.nodes, edges: data.edges })
  // 撤销栈复位基线：history 无公开 reset API，重新 watch 以当前图数据为首个基线快照
  // （分册 §4.4：撤销重做仅限本次编辑会话，props 重渲染视为新会话起点）
  lf.history.undos = []
  lf.history.redos = []
  lf.history.curData = null
  lf.history.watch(lf.graphModel)
  dirty.value = false
  selectedNode.value = null
  selectedEdge.value = null
  refreshToolbarState()
  nextTick(() => {
    try {
      lf.fitView()
    } catch (error) {
      // 容器尺寸异常时忽略适应画布失败（首次挂载瞬时态）
    }
  })
}

/* ==================== 画布事件 ==================== */

function bindCanvasEvents() {
  // 物料拖入完成：节点已带预分配键与默认名（见 handleMaterialDragStart）
  lf.on('node:dnd-add', () => {
    markDirty()
  })
  // 锚点拉出新连线：出边约束/自环/重复拦截 + 初始化连线业务属性
  lf.on('edge:add', ({ data }) => {
    handleEdgeAdd(data)
  })
  lf.on('node:delete', () => {
    clearStaleSelection()
    markDirty()
  })
  lf.on('edge:delete', () => {
    clearStaleSelection()
    markDirty()
  })
  // 右侧属性区选中摘要
  lf.on('node:click', ({ data }) => {
    selectedNode.value = projectNode(data)
    selectedEdge.value = null
  })
  lf.on('edge:click', ({ data }) => {
    const edge = projectEdge(data)
    selectedEdge.value = edge
    selectedNode.value = null
    // 分支连线点击即开配置（分册 §4.4：点击条件分支/并行分支节点的出边连线打开分支配置弹窗）
    if (!props.readonly && isBranchSource(edge.sourceNodeType)) {
      openBranchDialog(edge)
    }
  })
  lf.on('blank:click', () => {
    selectedNode.value = null
    selectedEdge.value = null
  })
  // 节点配置入口：双击节点（固定节点/CONDITION/JOIN 拦截提示；其余打开内置节点配置弹窗）
  lf.on('node:dbclick', ({ data }) => {
    const node = projectNode(data)
    selectedNode.value = node
    selectedEdge.value = null
    openNodeConfigDialog(node)
  })
  // 撤销/重做按钮态（History 自带 100ms debounce 后触发）
  lf.on('history:change', ({ data }) => {
    canUndo.value = data?.undoAble === true
    canRedo.value = data?.redoAble === true
  })
  // 节点/连线拖动仅改坐标（不落库，T-08）不置脏，仅历史栈变化需同步按钮态
  lf.on('node:drag', () => refreshToolbarState())
  lf.on('node:drop', () => refreshToolbarState())
}

/** 删除守卫：固定节点 N1/N2/N3 不可删（分册 §4.4 冻结文案） */
function handleBeforeDelete(data) {
  const nodeType = data?.properties?.nodeType
  if (FIXED_NODE_TYPES.includes(nodeType) || FIXED_NODE_KEYS.has(data?.id)) {
    ElMessage.warning('开始/起草/结束节点为固定节点，不可删除')
    return false
  }
  return true
}

/** 新连线落地：自环/重复拦截（自定项）→ 出边数上限拦截（分册 §4.4）→ 初始化业务属性 */
function handleEdgeAdd(data) {
  if (props.readonly) {
    return
  }
  const removeNewEdge = (message) => {
    lf.deleteEdge(data.id)
    if (message) {
      ElMessage.warning(message)
    }
  }
  if (data.sourceNodeId === data.targetNodeId) {
    removeNewEdge('不允许连接到节点自身')
    return
  }
  const duplicated = lf.graphModel.edges.some(
    (edge) =>
      edge.id !== data.id &&
      edge.sourceNodeId === data.sourceNodeId &&
      edge.targetNodeId === data.targetNodeId
  )
  if (duplicated) {
    removeNewEdge('两个节点之间已存在连线')
    return
  }
  const sourceType = nodeTypeById(data.sourceNodeId)
  const limit = OUT_EDGE_LIMITS[sourceType] ?? 'N'
  const outCount = lf.graphModel.getNodeOutgoingEdge(data.sourceNodeId).length
  if (limit !== 'N' && outCount > limit) {
    removeNewEdge(limit === 0 ? '该节点不允许拉出连线' : '该节点类型只允许一条流出连线')
    return
  }
  lf.setProperties(data.id, {
    lineId: genLocalId(),
    lineType: LINE_TYPE_BY_SOURCE[sourceType] || 'NORMAL',
    branchOrder: null,
    branchName: '',
    condition: '',
    isDefault: false,
  })
  markDirty()
}

/* ==================== 物料拖拽 ==================== */

/** 物料面板按下 → dnd 拖拽（预分配最小可用键与默认命名，序号仅展示） */
function handleMaterialDragStart(material) {
  if (props.readonly || !lf) {
    return
  }
  const graphData = lf.getGraphRawData()
  const sameTypeCount = graphData.nodes.filter(
    (node) => node.properties?.nodeType === material.nodeType
  ).length
  const key = nextNodeKey(graphData)
  lf.dnd.startDrag({
    type: material.flowType,
    id: key,
    properties: {
      nodeKey: key,
      nodeType: material.nodeType,
      nodeName: defaultNodeName(material.nodeType, sameTypeCount),
      fieldId: '',
      fieldNo: null,
    },
  })
}

/* ==================== 节点 / 分支配置 ==================== */

/**
 * 节点配置入口（双击节点与右侧【配置节点】共用；固定节点/条件分支/并行结束无配置）：
 * 打开内置 FlowNodeConfigDialog（与分支弹窗同构），node-config-request 保留为宿主感知事件。
 */
function requestNodeConfig(node) {
  openNodeConfigDialog(node)
}

/** 打开内置节点配置弹窗：只读拦截 → 无配置类型拦截提示 → 快照图上下文 + emit 宿主感知事件 */
function openNodeConfigDialog(node) {
  if (props.readonly) {
    return
  }
  if (!node || NO_NODE_CONFIG_TYPES.includes(node.nodeType)) {
    ElMessage.info('该节点无节点配置')
    return
  }
  nodeDialog.node = {
    nodeKey: node.nodeKey || node.id || '',
    fieldNodeType: node.nodeType || '',
    nodeName: node.nodeName || '',
    properties: { ...(node.properties || {}) },
  }
  nodeDialog.context = graphContextSnapshot()
  nodeDialog.visible = true
  emit('node-config-request', node)
}

/**
 * 当前画布图上下文快照（节点配置弹窗候选计算数据源，打开弹窗时采集）：
 * nodes 元素 { nodeKey, nodeType, nodeName }（nodeKey 以节点 properties 为准、兜底画布 id）、
 * lines 元素 { fromKey, toKey }。
 */
function graphContextSnapshot() {
  if (!lf) {
    return { nodes: [], lines: [] }
  }
  const raw = lf.getGraphRawData()
  return {
    nodes: raw.nodes.map((node) => ({
      nodeKey: node.properties?.nodeKey || node.id || '',
      nodeType: node.properties?.nodeType || '',
      nodeName: node.properties?.nodeName || '',
    })),
    lines: raw.edges.map((edge) => ({
      fromKey: edge.sourceNodeId,
      toKey: edge.targetNodeId,
    })),
  }
}

/** 节点配置弹窗【确定】回写：仅弹窗渲染的配置字段经 updateNodeProperties 局部合并（其余 properties 原样保留）并置脏 */
function applyNodeConfigResult(nodeKey, configFields) {
  updateNodeProperties(nodeKey, configFields || {})
}

/**
 * 节点配置回写（供 P17-FE-003 节点配置弹窗 / P17-FE-004 宿主调用）：
 * 合并 properties（配置平铺字段），nodeName 变化同步节点文本渲染。
 */
function updateNodeProperties(nodeId, partial) {
  if (!lf || !nodeId) {
    return
  }
  lf.setProperties(nodeId, partial || {})
  if (partial && typeof partial.nodeName === 'string') {
    lf.updateText(nodeId, partial.nodeName)
  }
  if (selectedNode.value?.id === nodeId) {
    selectedNode.value = projectNode(lf.getNodeModelById(nodeId)?.getData())
  }
  markDirty()
}

/** 点击分支连线 / 右侧【配置分支】 → 收集该节点全部出边并打开内置弹窗 */
function openBranchDialog(edge) {
  if (!lf || !edge?.sourceNodeId) {
    return
  }
  branchDialog.nodeKey = edge.sourceNodeId
  branchDialog.nodeName = edge.sourceNodeName || ''
  branchDialog.nodeType = edge.sourceNodeType
  branchDialog.branches = collectBranches(edge.sourceNodeId)
  branchDialog.visible = true
  emit('branch-config-request', edge)
}

/** 收集分支源节点的全部出边（分支弹窗工作副本数据源，edgeId 对应画布边） */
function collectBranches(sourceNodeId) {
  const edges = lf.graphModel.getNodeOutgoingEdge(sourceNodeId) || []
  return edges.map((edge) => ({
    edgeId: edge.id,
    branchName: edge.properties?.branchName || '',
    branchOrder: normalizeBranchOrder(edge.properties?.branchOrder),
    condition: edge.properties?.condition || '',
    isDefault: edge.properties?.isDefault === true,
  }))
}

/** 分支弹窗【确定】回写：删边 + 分支属性/边文本更新（序号以上移下移结果为准） */
function applyBranchResult({ branches, removedEdgeIds }) {
  if (!lf) {
    return
  }
  ;(removedEdgeIds || []).forEach((edgeId) => {
    if (lf.getEdgeModelById(edgeId)) {
      lf.deleteEdge(edgeId)
    }
  })
  ;(branches || []).forEach((branch) => {
    if (!lf.getEdgeModelById(branch.edgeId)) {
      return
    }
    lf.setProperties(branch.edgeId, {
      branchOrder: branch.branchOrder,
      branchName: branch.branchName,
      condition: branch.condition,
      isDefault: branch.isDefault === true,
    })
    lf.updateText(branch.edgeId, branchLineText(branch))
  })
  if (selectedEdge.value && !lf.getEdgeModelById(selectedEdge.value.id)) {
    selectedEdge.value = null
  } else if (selectedEdge.value) {
    selectedEdge.value = projectEdge(lf.getEdgeModelById(selectedEdge.value.id).getData())
  }
  markDirty()
}

/* ==================== 工具条 ==================== */

function zoomIn() {
  lf?.zoom(true)
}

function zoomOut() {
  lf?.zoom(false)
}

function undo() {
  if (props.readonly || !lf) {
    return
  }
  lf.undo()
  selectedNode.value = null
  selectedEdge.value = null
  markDirty()
}

function redo() {
  if (props.readonly || !lf) {
    return
  }
  lf.redo()
  selectedNode.value = null
  selectedEdge.value = null
  markDirty()
}

function refreshToolbarState() {
  if (!lf) {
    return
  }
  canUndo.value = lf.history.undoAble()
  canRedo.value = lf.history.redoAble()
}

/* ==================== expose 面与内部工具 ==================== */

/** 组装保存请求体 nodes + lines（接口清单 §4.3 契约；坐标不提交） */
function getSavePayload() {
  if (!lf) {
    return { nodes: [], lines: [] }
  }
  const raw = lf.getGraphRawData()
  return toSavePayload({ nodes: raw.nodes, edges: raw.edges })
}

/** 客户端图校验（G1~G7 可前端判定子集），返回中文错误消息数组 */
function validateGraph() {
  if (!lf) {
    return ['流程画布未初始化']
  }
  const raw = lf.getGraphRawData()
  return validateGraphData({ nodes: raw.nodes, edges: raw.edges })
}

/** 适应画布 */
function fit() {
  lf?.fitView()
}

/** 脏标记查询（宿主页面关闭/路由离开保护用） */
function isDirty() {
  return dirty.value === true
}

/** 保存成功后由宿主调用复位脏标记 */
function resetDirty() {
  dirty.value = false
}

function markDirty() {
  if (props.readonly) {
    return
  }
  dirty.value = true
  emit('graph-change', getSavePayload())
}

defineExpose({
  getSavePayload,
  validateGraph,
  fit,
  isDirty,
  resetDirty,
  updateNodeProperties,
})

/* ==================== 投影与展示辅助 ==================== */

function nodeTypeById(nodeId) {
  const model = lf?.getNodeModelById(nodeId)
  return model?.properties?.nodeType || ''
}

/** 节点 → 右侧摘要 / node-config-request 载荷 */
function projectNode(data) {
  if (!data) {
    return null
  }
  return {
    id: data.id,
    nodeKey: data.properties?.nodeKey || data.id || '',
    nodeType: data.properties?.nodeType || '',
    nodeName: data.properties?.nodeName || '',
    properties: { ...(data.properties || {}) },
  }
}

/** 连线 → 右侧摘要 / branch-config-request 载荷 */
function projectEdge(data) {
  if (!data) {
    return null
  }
  const sourceModel = lf?.getNodeModelById(data.sourceNodeId)
  const targetModel = lf?.getNodeModelById(data.targetNodeId)
  const sourceNodeType = sourceModel?.properties?.nodeType || ''
  return {
    id: data.id,
    sourceNodeId: data.sourceNodeId,
    targetNodeId: data.targetNodeId,
    sourceNodeKey: sourceModel?.properties?.nodeKey || data.sourceNodeId,
    sourceNodeName: sourceModel?.properties?.nodeName || '',
    sourceNodeType,
    targetNodeName: targetModel?.properties?.nodeName || '',
    lineType: data.properties?.lineType || LINE_TYPE_BY_SOURCE[sourceNodeType] || 'NORMAL',
    branchName: data.properties?.branchName || '',
    branchOrder: normalizeBranchOrder(data.properties?.branchOrder),
    condition: data.properties?.condition || '',
    isDefault: data.properties?.isDefault === true,
    branchEditable: isBranchSource(sourceNodeType),
    properties: { ...(data.properties || {}) },
  }
}

/** 删除后的选中摘要清理（节点/连线已不存在时置空） */
function clearStaleSelection() {
  if (selectedNode.value && !lf?.getNodeModelById(selectedNode.value.id)) {
    selectedNode.value = null
  }
  if (selectedEdge.value && !lf?.getEdgeModelById(selectedEdge.value.id)) {
    selectedEdge.value = null
  }
}

function nodeTypeLabel(nodeType) {
  return NODE_TYPE_LABELS[nodeType] || nodeType || '-'
}

function lineTypeLabel(lineType) {
  return LINE_TYPE_LABELS[lineType] || lineType || '-'
}
</script>

<style scoped>
.flow-designer {
  display: flex;
  width: 100%;
  height: 560px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
  overflow: hidden;
}

.designer-left {
  flex: none;
  width: 168px;
}

.designer-center {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.designer-toolbar {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
}

.toolbar-label {
  margin-right: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.dirty-flag {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-color-warning);
}

.designer-canvas {
  flex: 1;
  min-height: 0;
}

/* LogicFlow 容器需显式高度（画布随 flex 撑满中部剩余空间） */
.designer-canvas :deep(.lf-graph) {
  background: var(--el-bg-color);
}

.designer-hints {
  flex: none;
  padding: 5px 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.designer-right {
  flex: none;
  width: 232px;
  padding: 12px;
  border-left: 1px solid var(--el-border-color-lighter);
  overflow-y: auto;
  background: var(--el-fill-color-blank);
}

.props-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.props-item {
  display: flex;
  margin-bottom: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.props-label {
  flex: none;
  width: 60px;
  color: var(--el-text-color-secondary);
}

.props-value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-primary);
}

.props-btn {
  width: 100%;
  margin-top: 8px;
}

.props-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.props-empty {
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
</style>

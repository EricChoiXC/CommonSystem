import '@logicflow/core/lib/style/index.css'
import { h, RectNode, RectNodeModel, DiamondNode, DiamondNodeModel } from '@logicflow/core'

/**
 * 流程设计器 —— 10 类节点自定义注册与物料元数据（v1_7 / P17-FE-001）
 *
 * 契约来源：《流程v1_7前端UI设计分册》§4.3（节点图形冻结）、§11.2（组件目录规划）。
 * 本模块只落「依赖接入 + 节点注册 + 物料元数据」；画布交互（拖拽/连线/删除/graph 转换）
 * 由 P17-FE-002 在 FlowDesignerCanvas.vue / FlowNodeMaterials.vue 中消费本模块导出面实现。
 *
 * 依赖版本（frontend/package.json，v1_7 新增，实施时 npm 最新稳定 2.x 线）：
 *   - @logicflow/core@2.2.5
 *   - @logicflow/extension@2.3.1（本模块暂不消费；FE-002 按需引入具体扩展，不整包注册）
 *
 * 样式引入（LogicFlow v2 实际目录结构，安装版本实测 node_modules 内存在该路径）：
 *   `@logicflow/core/lib/style/index.css` —— 由本模块顶部直接引入。本模块仅被设计器 /
 *   只读流程图组件经路由懒加载链路引入，样式随设计器 chunk 加载，不进主包
 *   （风险 R03 对策：按需引入 + 路由懒加载）；禁止在 main.js / App.vue / HomeView.vue
 *   全局引入本模块或该样式。
 *
 * 注册机制（LogicFlow v2）：lf.register({ type, view, model })，见 registerFlowNodes(lf)。
 * 节点类型 node_type 由 FE-002 组装 graph 数据时写入节点 properties
 * （properties: { nodeKey, nodeType, nodeName, nodeConfig }），序列化时直接取用；
 * 本模块的节点文本渲染也按同一优先级取名称：properties.nodeName > model.text.value。
 *
 * 视觉约定（分册 §4.3 冻结）：
 *   - 节点内两行文本：节点名称（上） + 类型中文名（下），避免仅靠形状区分审批/抄送/机器人；
 *   - 名称超宽按字宽估算省略号截断，SVG <title> 悬浮展示全名；
 *   - 菱形 = 正方形旋转 45°（对角线 96 → rx = ry = 48）；上/下半圆以 path 绘制（弧 + 直边闭合）；
 *   - 底色/描边按节点类型区分（Element Plus 语义色系浅底深边，设计未冻结项，FE-002 走查时可调；
 *     单节点覆盖走 properties.style，优先级最高）。
 *
 * 渲染实现说明（ LogicFlow v2 + preact 实测口径）：
 *   - preact 对 SVG 属性不做 camelCase → kebab-case 转换，故本文件自绘 SVG 一律写
 *     kebab-case 属性（'font-size' / 'text-anchor' / 'dominant-baseline' / 'stroke-width'）；
 *   - LogicFlow v2 节点渲染树外层 transform 为恒等矩阵（rotate=0 时 translate(-x,-y)+
 *     translate(x,y) 抵消），自绘 getShape / getText 一律使用**画布绝对坐标**：以
 *     model.x / model.y 为节点中心（v1 的「节点中心相对坐标」口径在 v2 下会把文本
 *     与自绘形状画到画布原点附近，v1_7 走查已复现，勿回退）。
 *
 * ⚠ ROBORT 为存量历史拼写（存量代码/数据/文档一致沿用），任何代码、注释、文案禁止写成 ROBOT。
 */

/** 节点类型 → 类型中文名映射（分册 §4.3 冻结口径） */
export const NODE_TYPE_LABELS = {
  START: '开始节点',
  DRAFT: '起草节点',
  END: '结束节点',
  APPROVE: '审批节点',
  CC: '抄送节点',
  CONDITION: '条件分支',
  FORK: '并行开始',
  JOIN: '并行结束',
  ROBORT: '机器人节点',
  MANUAL: '人工决策',
}

/** 节点类型 → LogicFlow 自定义节点注册名映射 */
export const NODE_TYPE_TO_FLOW_TYPE = {
  START: 'flow-start',
  DRAFT: 'flow-draft',
  END: 'flow-end',
  APPROVE: 'flow-approve',
  CC: 'flow-cc',
  CONDITION: 'flow-condition',
  FORK: 'flow-fork',
  JOIN: 'flow-join',
  ROBORT: 'flow-robort',
  MANUAL: 'flow-manual',
}

// ===== 视觉常量 =====
const NODE_NAME_COLOR = '#303133' // 节点名称文字色（Element Plus 主文本色）
const NODE_TYPE_COLOR = '#909399' // 类型中文名文字色（Element Plus 常规文本色）
const NODE_STROKE_WIDTH = 1.2
// ASCII 字符按字高的 0.62 倍估宽（仅用于超宽截断估算，非精确排版）
const ASCII_WIDTH_RATIO = 0.62

// ===== 文本工具 =====

function measureTextWidth(text, fontSize) {
  let width = 0
  for (const ch of text) {
    width += ch.charCodeAt(0) < 128 ? fontSize * ASCII_WIDTH_RATIO : fontSize
  }
  return width
}

/** 超宽省略号截断（预留一个省略号宽度） */
function truncateNodeText(text, maxWidth, fontSize) {
  if (!text || measureTextWidth(text, fontSize) <= maxWidth) {
    return text || ''
  }
  let result = ''
  let width = 0
  for (const ch of text) {
    const charWidth = ch.charCodeAt(0) < 128 ? fontSize * ASCII_WIDTH_RATIO : fontSize
    if (width + charWidth > maxWidth - fontSize * 1.2) {
      break
    }
    result += ch
    width += charWidth
  }
  return `${result}…`
}

/**
 * 节点内两行文本：节点名称（上） + 类型中文名（下）。
 * 名称超宽省略号截断，两行各挂 SVG <title> 悬浮展示全名。
 * 坐标为画布绝对坐标（v2 口径，见文件头说明）：以 model.x / model.y（节点中心）为基准，
 * nameDy / typeDy 为相对中心的纵向偏移。
 */
function renderTwoLineText(model, layout) {
  const { fontSize, nameMaxWidth, nameDy, typeDy } = layout
  const properties = model.properties || {}
  const nodeName = properties.nodeName || (model.text && model.text.value) || ''
  const typeLabel = NODE_TYPE_LABELS[properties.nodeType] || ''
  const displayName = truncateNodeText(nodeName, nameMaxWidth, fontSize)
  return h('g', { class: 'flow-node-text' }, [
    h(
      'text',
      {
        x: model.x,
        y: model.y + nameDy,
        'text-anchor': 'middle',
        'dominant-baseline': 'central',
        'font-size': `${fontSize}px`,
        'font-weight': 600,
        fill: NODE_NAME_COLOR,
      },
      [displayName, h('title', null, nodeName)]
    ),
    h(
      'text',
      {
        x: model.x,
        y: model.y + typeDy,
        'text-anchor': 'middle',
        'dominant-baseline': 'central',
        'font-size': `${fontSize - 1}px`,
        fill: NODE_TYPE_COLOR,
      },
      [typeLabel, h('title', null, typeLabel ? `${nodeName}（${typeLabel}）` : nodeName)]
    ),
  ])
}

/** 按形状推导节点文本两行的字号 / 最大宽 / 纵向偏移（相对节点中心） */
function buildTextLayout(def) {
  const width = def.defaultSize.width
  switch (def.shapeKind) {
    case 'diamond':
      // 菱形（对角线 96）内可用宽度窄，字号略小、文本更紧凑
      return { fontSize: 11, nameMaxWidth: 58, nameDy: -9, typeDy: 10 }
    case 'half-circle-top':
      // 上半圆：弧顶在上、直边在下，文本置于弧内下半部（该高度处弧内宽度约 ≥ 110）
      return { fontSize: 12, nameMaxWidth: 92, nameDy: 8, typeDy: 22 }
    case 'half-circle-bottom':
      // 下半圆：直边在上、弧底在下，文本置于弧内上半部
      return { fontSize: 12, nameMaxWidth: 92, nameDy: -22, typeDy: -8 }
    default:
      return { fontSize: 12, nameMaxWidth: width - 20, nameDy: -8, typeDy: 10 }
  }
}

// ===== 三类形状工厂 =====
// 各节点类型每次调用工厂生成独立的 view/model 类对（尺寸、圆角、配色闭包互不影响）。
// 默认尺寸/圆角/底色的既有优先级保留：工厂默认值 < properties < LogicFlow 主题（styleRadius）；
// 单节点样式覆盖走 properties.style（在 getNodeStyle 中最高优先级展开）。

/**
 * 矩形节点工厂：START/DRAFT/END 圆角矩形 120×48（圆角 12/0/12）、
 * APPROVE/CC/ROBORT 直角矩形 140×52。锚点为上/右/下/左四向中点。
 */
function createRectFlowNode(def) {
  const { width, height } = def.defaultSize
  const textLayout = buildTextLayout(def)
  class FlowRectModel extends RectNodeModel {
    setAttributes() {
      this.width = width
      this.height = height
      this.radius = def.radius
      super.setAttributes()
    }

    getNodeStyle() {
      const style = super.getNodeStyle()
      return {
        ...style,
        fill: def.fill,
        stroke: def.stroke,
        strokeWidth: NODE_STROKE_WIDTH,
        ...(this.properties.style || {}),
      }
    }
  }
  class FlowRectView extends RectNode {
    getText() {
      return renderTwoLineText(this.props.model, textLayout)
    }
  }
  return { view: FlowRectView, model: FlowRectModel }
}

/**
 * 菱形节点工厂：CONDITION/MANUAL，正方形旋转 45°（对角线 96 → rx = ry = 48）。
 * DiamondNodeModel 的 width/height 为由 rx/ry 推导的只读 getter，仅设 rx/ry；
 * 锚点为菱形四顶点（上/右/下/左）。
 */
function createDiamondFlowNode(def) {
  const rx = def.defaultSize.width / 2
  const ry = def.defaultSize.height / 2
  const textLayout = buildTextLayout(def)
  class FlowDiamondModel extends DiamondNodeModel {
    setAttributes() {
      this.rx = rx
      this.ry = ry
      super.setAttributes()
    }

    getNodeStyle() {
      const style = super.getNodeStyle()
      return {
        ...style,
        fill: def.fill,
        stroke: def.stroke,
        strokeWidth: NODE_STROKE_WIDTH,
        ...(this.properties.style || {}),
      }
    }
  }
  class FlowDiamondView extends DiamondNode {
    getText() {
      return renderTwoLineText(this.props.model, textLayout)
    }
  }
  return { view: FlowDiamondView, model: FlowDiamondModel }
}

/**
 * 上/下半圆节点工厂：FORK（上半圆）/ JOIN（下半圆），宽 120 × 高 60。
 * LogicFlow 无半圆 base：view 重写 getShape 以 path 绘制「半圆弧 + 直边闭合」；
 * model 继承 RectNodeModel，保留 bounding box 120×60 与四向锚点
 * （上锚点 = 弧顶或直边中点、下锚点相对，左右锚点 = 直边端点）。
 * path 以画布绝对坐标绘制（v2 口径，见文件头说明）：以 model.x / model.y 为节点中心，
 * 上半圆 sweep=1（弧经顶部 y=model.y-30，直边 y=model.y+30），
 * 下半圆 sweep=0（弧经底部 y=model.y+30，直边 y=model.y-30）。
 */
function createHalfCircleFlowNode(def) {
  const { width, height } = def.defaultSize
  const radius = width / 2
  const halfHeight = height / 2
  const top = def.shapeKind === 'half-circle-top'
  const textLayout = buildTextLayout(def)
  class FlowHalfCircleModel extends RectNodeModel {
    setAttributes() {
      this.width = width
      this.height = height
      this.radius = 0
      super.setAttributes()
    }

    getNodeStyle() {
      const style = super.getNodeStyle()
      return {
        ...style,
        fill: def.fill,
        stroke: def.stroke,
        strokeWidth: NODE_STROKE_WIDTH,
        ...(this.properties.style || {}),
      }
    }
  }
  class FlowHalfCircleView extends RectNode {
    getShape() {
      const model = this.props.model
      const style = model.getNodeStyle()
      const shapePath = top
        ? `M ${model.x - radius} ${model.y + halfHeight} A ${radius} ${radius} 0 0 1 ${model.x + radius} ${model.y + halfHeight} Z`
        : `M ${model.x - radius} ${model.y - halfHeight} A ${radius} ${radius} 0 0 0 ${model.x + radius} ${model.y - halfHeight} Z`
      return h('path', {
        class: 'lf-basic-shape',
        d: shapePath,
        fill: style.fill,
        stroke: style.stroke,
        'stroke-width': style.strokeWidth,
      })
    }

    getText() {
      return renderTwoLineText(this.props.model, textLayout)
    }
  }
  return { view: FlowHalfCircleView, model: FlowHalfCircleModel }
}

const SHAPE_FACTORIES = {
  rect: createRectFlowNode,
  diamond: createDiamondFlowNode,
  'half-circle-top': createHalfCircleFlowNode,
  'half-circle-bottom': createHalfCircleFlowNode,
}

/**
 * 10 类节点定义（分册 §4.3 冻结表逐行落定，顺序与表一致）：
 *   - 物料面板分组口径（供 FE-002 消费）：fixed=true 为固定节点组（START/DRAFT/END，
 *     画布上必有且唯一，展示但不可拖拽新增）；其余 7 类为可添加节点组。
 *   - deletable / outEdges 对齐分册「是否可删除」「出边数」两列（outEdges 用 'N' 表示不限），
 *     供 FE-002 删除保护（N1/N2/N3 不可删）与拉线出边数约束消费。
 *   - defaultSize：矩形/半圆为 bounding box 宽高，菱形为对角线宽高（96×96）；
 *     radius 仅矩形类有圆角语义（菱形/半圆不设）。
 *   - view/model 由上方工厂按 shapeKind 生成后挂载（导出物料投影时剔除）。
 */
const FLOW_NODE_DEFS = [
  {
    nodeType: 'START',
    label: '开始节点',
    shapeKind: 'rect',
    shape: '圆角矩形',
    shapeDesc: '圆角矩形 120×48，圆角 12',
    fixed: true,
    draggable: false,
    deletable: false,
    outEdges: 1,
    defaultSize: { width: 120, height: 48 },
    radius: 12,
    fill: '#e1f3d8',
    stroke: '#67C23A',
  },
  {
    nodeType: 'DRAFT',
    label: '起草节点',
    shapeKind: 'rect',
    shape: '直角矩形',
    shapeDesc: '直角矩形 120×48，圆角 0',
    fixed: true,
    draggable: false,
    deletable: false,
    outEdges: 1,
    defaultSize: { width: 120, height: 48 },
    radius: 0,
    fill: '#d9ecff',
    stroke: '#409EFF',
  },
  {
    nodeType: 'END',
    label: '结束节点',
    shapeKind: 'rect',
    shape: '圆角矩形',
    shapeDesc: '圆角矩形 120×48，圆角 12',
    fixed: true,
    draggable: false,
    deletable: false,
    outEdges: 0,
    defaultSize: { width: 120, height: 48 },
    radius: 12,
    fill: '#fde2e2',
    stroke: '#F56C6C',
  },
  {
    nodeType: 'APPROVE',
    label: '审批节点',
    shapeKind: 'rect',
    shape: '直角矩形',
    shapeDesc: '直角矩形 140×52，圆角 0',
    fixed: false,
    draggable: true,
    deletable: true,
    outEdges: 1,
    defaultSize: { width: 140, height: 52 },
    radius: 0,
    fill: '#faecd8',
    stroke: '#E6A23C',
  },
  {
    nodeType: 'CC',
    label: '抄送节点',
    shapeKind: 'rect',
    shape: '直角矩形',
    shapeDesc: '直角矩形 140×52，圆角 0',
    fixed: false,
    draggable: true,
    deletable: true,
    outEdges: 1,
    defaultSize: { width: 140, height: 52 },
    radius: 0,
    fill: '#d6f2ef',
    stroke: '#26B3A2',
  },
  {
    nodeType: 'CONDITION',
    label: '条件分支',
    shapeKind: 'diamond',
    shape: '菱形',
    shapeDesc: '菱形（正方形旋转 45°），对角线 96',
    fixed: false,
    draggable: true,
    deletable: true,
    outEdges: 'N',
    defaultSize: { width: 96, height: 96 },
    fill: '#ece3ff',
    stroke: '#8F5BE8',
  },
  {
    nodeType: 'FORK',
    label: '并行开始',
    shapeKind: 'half-circle-top',
    shape: '上半圆',
    shapeDesc: '上半圆 120×60，顶部半圆弧、底部直边',
    fixed: false,
    draggable: true,
    deletable: true,
    outEdges: 'N',
    defaultSize: { width: 120, height: 60 },
    fill: '#dbe6fb',
    stroke: '#4A6FD4',
  },
  {
    nodeType: 'JOIN',
    label: '并行结束',
    shapeKind: 'half-circle-bottom',
    shape: '下半圆',
    shapeDesc: '下半圆 120×60，底部半圆弧、顶部直边',
    fixed: false,
    draggable: true,
    deletable: true,
    outEdges: 1,
    defaultSize: { width: 120, height: 60 },
    fill: '#dbe6fb',
    stroke: '#4A6FD4',
  },
  {
    nodeType: 'ROBORT',
    label: '机器人节点',
    shapeKind: 'rect',
    shape: '直角矩形',
    shapeDesc: '直角矩形 140×52，圆角 0',
    fixed: false,
    draggable: true,
    deletable: true,
    outEdges: 1,
    defaultSize: { width: 140, height: 52 },
    radius: 0,
    fill: '#e2e8f5',
    stroke: '#55679C',
  },
  {
    nodeType: 'MANUAL',
    label: '人工决策',
    shapeKind: 'diamond',
    shape: '菱形',
    shapeDesc: '菱形（正方形旋转 45°），对角线 96',
    fixed: false,
    draggable: true,
    deletable: true,
    outEdges: 'N',
    defaultSize: { width: 96, height: 96 },
    fill: '#fce1ef',
    stroke: '#D45E9A',
  },
]

// 按定义逐类生成 view/model 类对（同类型形状各自独立类，闭包互不影响）
FLOW_NODE_DEFS.forEach((def) => {
  const factory = SHAPE_FACTORIES[def.shapeKind]
  if (!factory) {
    throw new Error(`流程节点 ${def.nodeType} 的形状工厂未注册：${def.shapeKind}`)
  }
  const { view, model } = factory(def)
  def.view = view
  def.model = model
})

/**
 * 节点物料元数据数组（供 FE-002 物料面板消费）：
 * nodeType / label（类型中文名，亦是拖入画布时的默认节点名）/ shapeKind / shape / shapeDesc /
 * fixed / draggable / deletable / outEdges / defaultSize / radius / fill / stroke / flowType。
 */
export const FLOW_NODE_MATERIALS = FLOW_NODE_DEFS.map(({ view, model, ...material }) => ({
  ...material,
  flowType: NODE_TYPE_TO_FLOW_TYPE[material.nodeType],
}))

/**
 * 向 LogicFlow 实例注册 10 类自定义节点（v2 register({ type, view, model })）。
 * 在 FE-002 画布组件 new LogicFlow(options) 之后调用；重复注册为同 type 覆盖语义，可安全重入。
 */
export function registerFlowNodes(lf) {
  if (!lf || typeof lf.register !== 'function') {
    throw new Error('registerFlowNodes(lf) 需传入 LogicFlow 实例（v2）')
  }
  FLOW_NODE_DEFS.forEach((def) => {
    lf.register({
      type: NODE_TYPE_TO_FLOW_TYPE[def.nodeType],
      view: def.view,
      model: def.model,
    })
  })
}

/**
 * 流程实例只读流程图 · 实例 VO → LogicFlow 图数据适配（v1_7 / P17-FE-005）
 *
 * 纯 JS 模块：不 import @logicflow/core（与 graphTransform.js 同约束，node 冒烟自测可运行）。
 * 供 ProcessFlowViewer.vue（实例详情页 / P17-FE-006 审批面板「流程」卡片复用）消费。
 *
 * 数据契约（流程v1_7接口清单 §5.3 load 响应）：
 *   - nodes[]：实例节点 VO —— fieldId / fieldNodeNo / fieldNodeName / fieldNodeType /
 *     fieldStatus / fieldNodeKey（实现字段名，分册记 nodeKey 以实现为准）；
 *   - lines[]：实例连线快照（字段同模板 lines，fieldExampleId 替代 fieldTemplateId）。
 * 与模板 load VO 的差异：实例节点序号字段为 fieldNodeNo（模板为 fieldNo）、
 * 节点键字段为 fieldNodeKey（模板为 nodeKey）——本模块统一适配为 graphTransform 消费的
 * nodeKey / fieldNo 形态，不改 graphTransform 既有函数签名。
 *
 * 三种渲染模式：
 *   - empty：nodes 为空 → 宿主渲染 el-empty「暂无流程节点信息」；
 *   - graph：连线可解析 → toGraphData 拓扑自动分层布局（复用模板画布同一布局函数）；
 *   - degraded：无连线数据（旧实例未生成连线快照 / 连线键无法对齐节点）→ 按
 *     fieldNodeNo 顺序横向排布 + 宿主提示「图数据缺失，按节点序号排布」（分册 §6.2 兼容口径）。
 *
 * 状态着色（分册 §6.2 冻结，纯前端展示语义，不改变后端状态值）：
 *   WAITING 灰(info) / PROCESSING 蓝(primary) / FINISH 绿(success) / PASS 青(success 浅) /
 *   REJECT 红(danger)；经节点 properties.style 覆盖 flowNodes 默认配色（优先级最高），
 *   宿主可经 statusColors 覆盖（FE-006 复用预留）。
 *
 * 自定项登记（分册未冻结细节，P17-DOC-010 走查时可调）：
 *   - 连线「已流经」口径：源节点状态 ∈ {FINISH, PASS}（已随出边继续流转）且目标节点
 *     已被到达（状态非空且非 WAITING）；REJECT 源节点的出边按未流经展示（驳回未沿出边
 *     继续流转）、CONDITION/FORK 未走分支的目标 WAITING 自然为未流经——仅视觉推导，
 *     不改变流转语义；
 *   - 已流经连线 = 实线主色（#409EFF），未流经 = 浅灰虚线（#C0C4CC, 6,4）；
 *   - 未登记节点状态（异常数据）按 WAITING 灰色展示。
 */

import { LAYOUT, NODE_TYPE_TO_FLOW_TYPE, toGraphData } from './graphTransform'

/** 节点状态 → 展示名（分册 §6.2 冻结五态；图例与双击节点信息提示共用） */
export const NODE_STATUS_LABELS = {
  WAITING: '待流转',
  PROCESSING: '处理中',
  FINISH: '完成',
  PASS: '通过',
  REJECT: '驳回',
}

/**
 * 节点状态 → 画布填充/描边色（分册 §6.2 着色表；Element Plus 语义色浅底深边，
 * 与 flowNodes.js 节点默认配色同一色系）。宿主 statusColors 按键覆盖。
 */
export const DEFAULT_STATUS_STYLES = {
  WAITING: { fill: '#f4f4f5', stroke: '#909399' },
  PROCESSING: { fill: '#d9ecff', stroke: '#409EFF' },
  FINISH: { fill: '#e1f3d8', stroke: '#67C23A' },
  PASS: { fill: '#d6f2ef', stroke: '#26B3A2' },
  REJECT: { fill: '#fde2e2', stroke: '#F56C6C' },
}

/** 未登记状态的兜底样式（按 WAITING 灰展示） */
export const UNKNOWN_STATUS_STYLE = DEFAULT_STATUS_STYLES.WAITING

/** 图例展示顺序（分册 §6.1：待流转灰/处理中蓝/完成绿/通过青/驳回红） */
export const STATUS_LEGEND_ORDER = ['WAITING', 'PROCESSING', 'FINISH', 'PASS', 'REJECT']

/** 连线「已流经」的源节点状态集合（自定口径，见文件头登记） */
export const TRAVERSED_SOURCE_STATUS = ['FINISH', 'PASS']

/** 已流经连线样式：实线主色（经自定义连线 model 的 properties.style 覆盖） */
export const EDGE_TRAVERSED_STYLE = { stroke: '#409EFF', strokeWidth: 1.6 }

/** 未流经连线样式：浅灰虚线（仅视觉，不改变流转语义） */
export const EDGE_PENDING_STYLE = { stroke: '#C0C4CC', strokeWidth: 1, strokeDasharray: '6,4' }

/**
 * 连线是否「已流经」（仅视觉推导，自定口径见文件头登记）。
 * @param {string} fromStatus 源节点状态
 * @param {string} toStatus 目标节点状态
 */
export function isLineTraversed(fromStatus, toStatus) {
  return TRAVERSED_SOURCE_STATUS.includes(fromStatus) && Boolean(toStatus) && toStatus !== 'WAITING'
}

/**
 * 实例节点 VO 适配：fieldNodeKey/fieldNodeNo（实例侧字段名）→ nodeKey/fieldNo
 * （graphTransform 侧口径），其余字段原样透传（fieldStatus 等由本模块消费）。
 * nodeKey 兜底 fieldId（防旧数据缺键；连线键若同样缺失则自然落入 degraded 模式）。
 */
export function adaptExampleNodes(nodes) {
  const list = Array.isArray(nodes) ? nodes.filter(Boolean) : []
  return list.map((node) => ({
    ...node,
    nodeKey: node.fieldNodeKey || node.nodeKey || node.fieldId || '',
    fieldNo: Number.isFinite(Number(node.fieldNodeNo))
      ? Number(node.fieldNodeNo)
      : Number.isFinite(Number(node.fieldNo))
        ? Number(node.fieldNo)
        : null,
  }))
}

/** degraded 模式节点列表：按 fieldNodeNo 升序（缺省按 nodeKey 数值感知字典序）横向排布 */
function buildLinearNodes(nodeList, statusStyles) {
  const sorted = nodeList
    .slice()
    .sort(
      (a, b) =>
        (Number(a.fieldNodeNo) || 0) - (Number(b.fieldNodeNo) || 0) ||
        String(a.nodeKey).localeCompare(String(b.nodeKey), undefined, { numeric: true })
    )
  return sorted.map((node, index) => {
    const status = node.fieldStatus || ''
    return {
      id: node.nodeKey,
      type: NODE_TYPE_TO_FLOW_TYPE[node.fieldNodeType] || 'flow-approve',
      x: LAYOUT.ORIGIN_X + index * LAYOUT.COL_GAP,
      y: LAYOUT.ORIGIN_Y,
      properties: {
        nodeKey: node.nodeKey,
        nodeType: node.fieldNodeType || '',
        nodeName: node.fieldNodeName || '',
        fieldId: node.fieldId || '',
        fieldNo: Number.isFinite(Number(node.fieldNodeNo)) ? Number(node.fieldNodeNo) : null,
        fieldStatus: status,
        style: { ...(statusStyles[status] || UNKNOWN_STATUS_STYLE) },
      },
    }
  })
}

/**
 * 实例 load VO → LogicFlow 图数据（含状态着色与连线态样式注入）。
 * @param nodes load 响应 nodes[]（实例节点 VO）
 * @param lines load 响应 lines[]（实例连线快照；可能缺省——旧后端/异常实例）
 * @param statusStyles 节点状态色映射（缺省 DEFAULT_STATUS_STYLES；宿主可覆盖）
 * @param edgeType 渲染边 LogicFlow 注册类型（缺省 'polyline'；ProcessFlowViewer 传入
 *   其自注册的 'flow-line'，保证 properties.style 覆盖连线样式生效）
 * @returns {{ mode: 'empty'|'graph'|'degraded', lfNodes: Array, lfEdges: Array }}
 */
export function buildExampleGraphData(nodes, lines, statusStyles = DEFAULT_STATUS_STYLES, edgeType = 'polyline') {
  const adapted = adaptExampleNodes(nodes)
  if (adapted.length === 0) {
    return { mode: 'empty', lfNodes: [], lfEdges: [] }
  }
  const lineList = Array.isArray(lines) ? lines.filter(Boolean) : []
  const statusByKey = new Map(adapted.map((node) => [node.nodeKey, node.fieldStatus || '']))
  const graph = toGraphData(adapted, lineList, { seedFixedTrio: false, edgeType })
  if (graph.edges.length === 0) {
    // 无连线数据（旧实例未生成快照 / 连线键无法对齐节点）→ 按 fieldNodeNo 横向排布降级
    return { mode: 'degraded', lfNodes: buildLinearNodes(adapted, statusStyles), lfEdges: [] }
  }
  graph.nodes.forEach((node) => {
    const status = statusByKey.get(node.id) || ''
    node.properties.fieldStatus = status
    node.properties.style = { ...(statusStyles[status] || UNKNOWN_STATUS_STYLE) }
  })
  const traversedByKey = new Map(
    lineList.map((line) => [
      `${line.fieldFromKey}->${line.fieldToKey}`,
      isLineTraversed(statusByKey.get(line.fieldFromKey) || '', statusByKey.get(line.fieldToKey) || ''),
    ])
  )
  graph.edges.forEach((edge) => {
    const traversed = traversedByKey.get(`${edge.sourceNodeId}->${edge.targetNodeId}`) === true
    edge.properties.style = traversed ? { ...EDGE_TRAVERSED_STYLE } : { ...EDGE_PENDING_STYLE }
  })
  return { mode: 'graph', lfNodes: graph.nodes, lfEdges: graph.edges }
}

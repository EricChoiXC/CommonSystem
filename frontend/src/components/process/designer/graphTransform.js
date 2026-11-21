/**
 * 流程设计器 graph ⇄ nodes + lines 双向转换、保存数据组装与客户端图校验（v1_7 / P17-FE-002）
 *
 * 纯 JS 模块：不 import @logicflow/core 与 flowNodes.js（后者引 LogicFlow 核心，node 冒烟
 * 自测无法运行），故本文件内置与 flowNodes.js 同值的 NODE_TYPE_TO_FLOW_TYPE / 类型中文名
 * 映射 —— 两处必须保持同步（flowNodes.js 侧变更时同步改此处，任务清单 §4.31 有登记）。
 *
 * 契约来源（冲突时以接口清单为准）：
 *   - 《流程v1_7接口清单》§4.2（load 响应 nodes[]/lines[] 全表）、§4.3（save 请求契约与节点键归一）、
 *     §4.4（服务端图校验 G1~G10）—— 保存数据字段的唯一权威；
 *   - 《流程v1_7前端UI设计分册》§4.4（画布交互）、§4.6（graph ⇄ nodes+lines 转换示意，
 *     其示意 JSON 与接口清单不一致处按接口清单执行：配置字段平铺在节点对象上、坐标不提交）；
 *   - T-08 定稿：节点坐标不落库 —— load 响应 nodes 无 x/y，画布按拓扑自动分层布局
 *     （自上而下、自左而右），保存不提交坐标。
 *
 * 节点键归一口径（接口清单 §4.3，客户端与服务端同口径以保证「nodeKey 对照」通过）：
 *   - START=N1、DRAFT=N2、END=N3、中间节点按「画布出现顺序」自 N4 起顺延；
 *   - 画布出现顺序 = 自上而下（按 y 换算层桶）自左而右（按 x），见 layerIndexOf()；
 *   - toSavePayload 输出 nodes 数组按 [N1, N2, 中间…, N3] 排布 —— 服务端按请求 nodes 数组
 *     顺序归一，两侧结果一致；
 *   - field_no 与键无关：开始 1、起草 2、中间 3…n、结束 n+1。
 *
 * 连线字段口径（接口清单 §4.2 lines[] 元素）：fieldId / fieldFromKey / fieldToKey /
 * fieldLineType / fieldBranchOrder / fieldBranchName / fieldCondition / fieldIsDefault；
 * fieldTemplateId 由服务端按请求上下文回填，客户端不提交。
 *
 * 分支序号归一：CONDITION/FORK 的出边按 properties.branchOrder 升序（空值排后、保持稳定）
 * 重排后 1..n 回写 —— 分支配置弹窗的上移/下移只改弹窗内工作副本，画布回写与保存组装统一
 * 走本模块归一，保证 branch_order 与界面顺序一致。
 *
 * 自定项登记（分册未冻结细节，P17-DOC-010 走查时可调）：
 *   - 边文本 branchLineText()：有分支名称显示名称，默认分支追加「（默认）」；无名称的默认
 *     分支显示「默认」；其余不显示文本；
 *   - 布局常量 LAYOUT（层距/列距/原点）：保证菱形(96)与上下半圆(60)不重叠、边文本有空间；
 *   - 自动布局层内排序按 fieldNo 升序（缺省按 nodeKey 字典序），保证同数据回显稳定。
 */

/** 固定节点类型（画布必有且唯一，不可删/不可拖入） */
export const FIXED_NODE_TYPES = ['START', 'DRAFT', 'END']

/**
 * 节点类型 → LogicFlow 自定义节点注册名映射。
 * 与 flowNodes.js 的 NODE_TYPE_TO_FLOW_TYPE 保持一致（本模块不 import LogicFlow，故内置同值表）。
 */
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

/** LogicFlow 注册名 → 节点类型（beforeDelete 守卫按 properties.nodeType 判定，此处兜底用） */
export const FLOW_TYPE_TO_NODE_TYPE = Object.fromEntries(
  Object.entries(NODE_TYPE_TO_FLOW_TYPE).map(([nodeType, flowType]) => [flowType, nodeType])
)

/** 类型中文名（与 flowNodes.js NODE_TYPE_LABELS 同值；分册 §4.3 冻结口径） */
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

/** 连线类型中文（右侧属性区展示用，随画布组件消费） */
export const LINE_TYPE_LABELS = {
  NORMAL: '普通流转',
  CONDITION: '条件分支',
  PARALLEL: '并行分支',
  MANUAL: '人工决策',
}

/**
 * 各类型出边数上限（分册 §4.3「出边数」列）：数字 = 固定上限，'N' = 不限。
 * START=1（固定指 N2）、DRAFT=1、END=0、APPROVE/CC/ROBORT/JOIN=1、CONDITION/FORK/MANUAL≥1。
 */
export const OUT_EDGE_LIMITS = {
  START: 1,
  DRAFT: 1,
  END: 0,
  APPROVE: 1,
  CC: 1,
  ROBORT: 1,
  JOIN: 1,
  CONDITION: 'N',
  FORK: 'N',
  MANUAL: 'N',
}

/** 连线类型由源节点类型决定（接口清单 §4.2：前端只读推导、不手工选择），其余 NORMAL */
export const LINE_TYPE_BY_SOURCE = {
  CONDITION: 'CONDITION',
  FORK: 'PARALLEL',
  MANUAL: 'MANUAL',
}

/** 分支源节点类型（出边按分支配置：名称/序号/条件公式/默认标记） */
export const BRANCH_SOURCE_TYPES = ['CONDITION', 'FORK']

/**
 * 无节点配置弹窗的节点类型（分册 §5.1：固定节点 START/DRAFT/END、条件分支 CONDITION、
 * 并行结束 JOIN；FORK 有「汇聚配置」，不在此列）。
 */
export const NO_NODE_CONFIG_TYPES = ['START', 'DRAFT', 'END', 'CONDITION', 'JOIN']

/**
 * 节点 save 元素按类型透传的配置平铺字段（接口清单 §4.2 nodes[] 全表；固定节点与
 * CONDITION/JOIN 无配置字段）。skipMode 旧值 yes/no 由后端读取归一，前端透传不改写。
 */
export const NODE_CONFIG_KEYS = {
  APPROVE: [
    'handlerSource', 'handlerIds', 'handlerNames', 'handlerField', 'handlerFormula',
    'handlerResolveMode', 'approveMode', 'skipMode', 'autoContinueSkip', 'emptyHandlerMode',
    'rejectTargets', 'operations', 'ops', 'tasks',
  ],
  CC: ['ccSource', 'ccIds', 'ccNames', 'ccFormula', 'tasks'],
  ROBORT: ['tasks'],
  MANUAL: ['flowDecisions', 'defaultFlowKey'],
  FORK: ['joinMode'],
}

/** 自动分层布局常量（自定项，见文件头登记）：节点中心坐标，层距需容纳菱形 96 与边文本 */
export const LAYOUT = {
  ORIGIN_X: 240,
  ORIGIN_Y: 120,
  COL_GAP: 200,
  ROW_GAP: 130,
}

/** 由画布 y 坐标换算层桶序号（toSavePayload 出现顺序与拖入布局共用的归一口径） */
export function layerIndexOf(y) {
  const index = Math.round((Number(y) - LAYOUT.ORIGIN_Y) / LAYOUT.ROW_GAP)
  return Number.isFinite(index) ? Math.max(0, index) : 0
}

/**
 * 生成 32 位本地 ID（与后端 IDUtil.initID 同构：毫秒时间戳 + 随机十六进制，共 32 位）。
 * 画布组件不得调接口，故新增节点/连线在保存组装时经此补 ID（VARCHAR(32) 安全）。
 */
export function genLocalId() {
  const timestamp = String(Date.now())
  let random = ''
  while (random.length < 32) {
    random += Math.floor(Math.random() * 16).toString(16)
  }
  return `${timestamp}${random}`.slice(0, 32)
}

/** 拖入新节点的默认命名：类型中文名 + 同类型已有数 + 1（序号仅展示，不参与流转，分册 §4.3-4） */
export function defaultNodeName(nodeType, sameTypeCount = 0) {
  const label = NODE_TYPE_LABELS[nodeType] || nodeType || '未命名节点'
  return FIXED_NODE_TYPES.includes(nodeType) ? label : `${label}${sameTypeCount + 1}`
}

/** 边文本规则（自定项，见文件头登记） */
export function branchLineText(lineProps = {}) {
  const name = String(lineProps.branchName || '').trim()
  if (lineProps.isDefault === true) {
    return name ? `${name}（默认）` : '默认'
  }
  return name
}

/** 是否分支源节点（其出边按分支配置） */
export function isBranchSource(nodeType) {
  return BRANCH_SOURCE_TYPES.includes(nodeType)
}

/**
 * 空画布固定三节点种子（新建模板 load 无节点时画布可用：开始 → 起草 → 结束）。
 * 节点名与 FE-001 物料/分册口径一致（开始节点/起草节点/结束节点）。
 */
function seedFixedTrioNodes() {
  return [
    { fieldId: '', fieldNo: 1, nodeKey: 'N1', fieldNodeName: '开始节点', fieldNodeType: 'START' },
    { fieldId: '', fieldNo: 2, nodeKey: 'N2', fieldNodeName: '起草节点', fieldNodeType: 'DRAFT' },
    { fieldId: '', fieldNo: 3, nodeKey: 'N3', fieldNodeName: '结束节点', fieldNodeType: 'END' },
  ]
}

function seedFixedTrioLines() {
  return [
    { fieldId: '', fieldFromKey: 'N1', fieldToKey: 'N2', fieldLineType: 'NORMAL' },
    { fieldId: '', fieldFromKey: 'N2', fieldToKey: 'N3', fieldLineType: 'NORMAL' },
  ]
}

/** load VO 元数据取值（nodeKey 兜底 id，防旧数据缺键） */
function nodeKeyOf(node) {
  return node.nodeKey ?? node.id ?? ''
}

function nodeTypeOf(node) {
  return node.fieldNodeType || node.nodeType || ''
}

function nodeNameOf(node) {
  return node.fieldNodeName ?? node.nodeName ?? ''
}

/**
 * 拓扑自动分层布局（T-08：坐标不落库，每次 load 重新计算）：
 *   - 深度 = 沿连线自 START（深度 0）传播的最长路深度（松弛法，模板规模小 O(V·E) 可接受；
 *     含环/无 START 的畸形数据仍给出有限深度、不崩溃，畸形图由保存校验拒绝）；
 *   - START 固定第 0 层，END 固定最后一层；
 *   - 层内按 fieldNo 升序（缺省按 nodeKey 字典序）自左而右。
 * 返回 Map<nodeKey, { x, y }>（节点中心坐标）。
 */
function computeLayout(nodeList, lineList) {
  const keys = nodeList.map(nodeKeyOf).filter(Boolean)
  const valid = new Set(keys)
  const depth = new Map(keys.map((key) => [key, Infinity]))
  const startKey = keys.find((key) => nodeTypeOf(nodeList.find((n) => nodeKeyOf(n) === key)) === 'START')
  const endKey = keys.find((key) => nodeTypeOf(nodeList.find((n) => nodeKeyOf(n) === key)) === 'END')
  if (startKey) {
    depth.set(startKey, 0)
  } else if (keys.length) {
    depth.set(keys[0], 0)
  }
  for (let pass = 0; pass < keys.length; pass += 1) {
    let changed = false
    for (const line of lineList) {
      const from = line.fieldFromKey
      const to = line.fieldToKey
      const depthFrom = depth.get(from)
      if (!valid.has(from) || !valid.has(to) || depthFrom === undefined || depthFrom === Infinity) {
        continue
      }
      const candidate = depthFrom + 1
      const depthTo = depth.get(to)
      if (depthTo === Infinity || candidate > depthTo) {
        depth.set(to, candidate)
        changed = true
      }
    }
    if (!changed) {
      break
    }
  }
  let maxDepth = 0
  depth.forEach((value) => {
    if (value !== Infinity && value > maxDepth) {
      maxDepth = value
    }
  })
  // 从 START 不可达的节点放最深一层之下（畸形数据兜底；END 最后强制到最深层）
  depth.forEach((value, key) => {
    if (value === Infinity) {
      depth.set(key, maxDepth + 1)
      maxDepth = maxDepth + 1
    }
  })
  if (endKey) {
    depth.set(endKey, Math.max(maxDepth, depth.get(endKey) === Infinity ? 0 : depth.get(endKey) || 0))
    maxDepth = Math.max(maxDepth, depth.get(endKey))
  }
  if (startKey) {
    depth.set(startKey, 0)
  }
  // 分层 → 层内排序 → 坐标
  const rows = new Map()
  nodeList.forEach((node) => {
    const layer = depth.get(nodeKeyOf(node)) || 0
    if (!rows.has(layer)) {
      rows.set(layer, [])
    }
    rows.get(layer).push(node)
  })
  const positions = new Map()
  Array.from(rows.keys())
    .sort((a, b) => a - b)
    .forEach((layer) => {
      const row = rows
        .get(layer)
        .slice()
        .sort(
          (a, b) =>
            (Number(a.fieldNo) || 0) - (Number(b.fieldNo) || 0) ||
            String(nodeKeyOf(a)).localeCompare(String(nodeKeyOf(b)))
        )
      row.forEach((node, col) => {
        positions.set(nodeKeyOf(node), {
          x: LAYOUT.ORIGIN_X + col * LAYOUT.COL_GAP,
          y: LAYOUT.ORIGIN_Y + layer * LAYOUT.ROW_GAP,
        })
      })
    })
  return positions
}

/** 按类型挑出节点配置平铺字段（undefined 剔除） */
function pickConfigFields(nodeType, props) {
  const keys = NODE_CONFIG_KEYS[nodeType] || []
  const result = {}
  keys.forEach((key) => {
    if (props[key] !== undefined) {
      result[key] = props[key]
    }
  })
  return result
}

/** load 连线 VO → LogicFlow 边 properties（fieldLineType 由源节点类型推导优先，见 toSavePayload 同口径） */
function buildEdgeProperties(line, sourceNodeType) {
  return {
    lineId: line.fieldId || '',
    lineType: LINE_TYPE_BY_SOURCE[sourceNodeType] || line.fieldLineType || 'NORMAL',
    branchOrder: line.fieldBranchOrder !== null && line.fieldBranchOrder !== undefined && Number.isFinite(Number(line.fieldBranchOrder))
      ? Number(line.fieldBranchOrder)
      : null,
    branchName: line.fieldBranchName || '',
    condition: line.fieldCondition || '',
    isDefault: line.fieldIsDefault === true,
  }
}

/** 分支序号取值：null/undefined/非法值统一为 null（Number(null)=0 需显式排除；画布侧收集分支数据共用） */
export function normalizeBranchOrder(value) {
  return value !== null && value !== undefined && Number.isFinite(Number(value)) ? Number(value) : null
}

/**
 * load 数据 → LogicFlow graph 数据。
 * @param nodes  load 响应 nodes[]（接口清单 §4.2 平铺 VO）
 * @param lines  load 响应 lines[]
 * @param options.seedFixedTrio 空图时是否播种固定三节点（编辑模式 true，只读渲染 false）
 * @returns {{ nodes: Array, edges: Array }} 可直接传 lf.render() 的图数据
 */
export function toGraphData(nodes, lines, options = {}) {
  let nodeList = Array.isArray(nodes) ? nodes.filter(Boolean) : []
  let lineList = Array.isArray(lines) ? lines.filter(Boolean) : []
  if (nodeList.length === 0 && options.seedFixedTrio === true) {
    nodeList = seedFixedTrioNodes()
    lineList = seedFixedTrioLines()
  }
  const positions = computeLayout(nodeList, lineList)
  const typeByKey = new Map(nodeList.map((node) => [nodeKeyOf(node), nodeTypeOf(node)]))
  const lfNodes = nodeList.map((node) => {
    const key = nodeKeyOf(node)
    const nodeType = nodeTypeOf(node)
    const position = positions.get(key) || { x: LAYOUT.ORIGIN_X, y: LAYOUT.ORIGIN_Y }
    return {
      id: key,
      type: NODE_TYPE_TO_FLOW_TYPE[nodeType] || 'flow-approve',
      x: position.x,
      y: position.y,
      properties: {
        nodeKey: key,
        nodeType,
        nodeName: nodeNameOf(node),
        fieldId: node.fieldId || '',
        fieldNo: Number.isFinite(Number(node.fieldNo)) ? Number(node.fieldNo) : null,
        ...pickConfigFields(nodeType, node),
      },
    }
  })
  const existKeys = new Set(lfNodes.map((node) => node.id))
  const lfEdges = lineList
    .filter((line) => existKeys.has(line.fieldFromKey) && existKeys.has(line.fieldToKey))
    .map((line) => {
      const props = buildEdgeProperties(line, typeByKey.get(line.fieldFromKey))
      return {
        id: props.lineId || genLocalId(),
        sourceNodeId: line.fieldFromKey,
        targetNodeId: line.fieldToKey,
        text: branchLineText(props) || undefined,
        properties: props,
      }
    })
  return { nodes: lfNodes, edges: lfEdges }
}

/**
 * 当前画布最小可用中间节点键（拖入新节点分配，N4、N5…顺延；接口清单 §4.3）。
 * @param graphData lf.getGraphRawData() 形态（或 toGraphData 形态）
 */
export function nextNodeKey(graphData) {
  const used = new Set((graphData?.nodes || []).map((node) => node.id))
  let index = 4
  while (used.has(`N${index}`)) {
    index += 1
  }
  return `N${index}`
}

/**
 * LogicFlow graph → save/update 请求体 nodes + lines（接口清单 §4.3 契约）：
 *   - 节点键/序号归一：START=N1(1)、DRAFT=N2(2)、中间按画布出现顺序 N4…(3…n)、END=N3(n+1)；
 *   - nodes 数组按 [N1, N2, 中间…, N3] 排布，与服务端「按请求 nodes 数组顺序归一」一致；
 *   - 连线 fieldLineType 由源节点类型推导；分支序号按源节点分组 branchOrder 升序重排 1..n；
 *   - 节点配置平铺字段按类型白名单透传（skipMode 旧值不改写）；坐标不提交（T-08）。
 * @param graphData lf.getGraphRawData() 形态：{ nodes: [{ id, x, y, properties }], edges: [{ id, sourceNodeId, targetNodeId, properties }] }
 */
export function toSavePayload(graphData) {
  const rawNodes = graphData?.nodes || []
  const rawEdges = graphData?.edges || []
  // 出现顺序：自上而下（层桶）自左而右（x），同位次按 id 稳定排序（缺坐标按 0 兜底）
  const ordered = rawNodes
    .slice()
    .sort(
      (a, b) =>
        layerIndexOf(a.y) - layerIndexOf(b.y) ||
        (Number(a.x) || 0) - (Number(b.x) || 0) ||
        String(a.id).localeCompare(String(b.id))
    )
  const typeById = new Map()
  ordered.forEach((node) => {
    typeById.set(node.id, resolveNodeType(node))
  })
  // 键/序号归一：固定三节点前置/末置，中间节点按出现顺序
  const startNode = ordered.find((node) => typeById.get(node.id) === 'START')
  const draftNode = ordered.find((node) => typeById.get(node.id) === 'DRAFT')
  const endNode = ordered.find((node) => typeById.get(node.id) === 'END')
  const middleNodes = ordered.filter(
    (node) => !FIXED_NODE_TYPES.includes(typeById.get(node.id))
  )
  const finalList = [startNode, draftNode, ...middleNodes, endNode].filter(Boolean)
  const keyMap = new Map()
  const noMap = new Map()
  let currentNo = 3
  let middleIndex = 0
  finalList.forEach((node) => {
    const nodeType = typeById.get(node.id)
    if (nodeType === 'START') {
      keyMap.set(node.id, 'N1')
      noMap.set(node.id, 1)
    } else if (nodeType === 'DRAFT') {
      keyMap.set(node.id, 'N2')
      noMap.set(node.id, 2)
    } else if (nodeType === 'END') {
      keyMap.set(node.id, 'N3')
      noMap.set(node.id, currentNo)
    } else {
      keyMap.set(node.id, `N${4 + middleIndex}`)
      noMap.set(node.id, currentNo)
      currentNo += 1
      middleIndex += 1
    }
  })
  // 节点 save 元素（配置平铺、不提交坐标）
  const saveNodes = finalList.map((node) => {
    const props = node.properties || {}
    const nodeType = typeById.get(node.id)
    return {
      fieldId: props.fieldId || genLocalId(),
      nodeKey: keyMap.get(node.id),
      fieldNo: noMap.get(node.id),
      fieldNodeName: props.nodeName || '',
      fieldNodeType: nodeType,
      ...pickConfigFields(nodeType, props),
    }
  })
  // 分支序号归一：按源节点分组，branchOrder 升序（空值排后、保持原序）重排 1..n
  const branchOrderMap = new Map()
  const branchGroups = new Map()
  rawEdges.forEach((edge, index) => {
    const sourceType = typeById.get(edge.sourceNodeId)
    if (!BRANCH_SOURCE_TYPES.includes(sourceType)) {
      return
    }
    if (!branchGroups.has(edge.sourceNodeId)) {
      branchGroups.set(edge.sourceNodeId, [])
    }
    branchGroups.get(edge.sourceNodeId).push({ edge, index })
  })
  branchGroups.forEach((group) => {
    group
      .slice()
      .sort(
        (a, b) =>
          (normalizeBranchOrder(a.edge.properties?.branchOrder) ?? Number.MAX_SAFE_INTEGER) -
            (normalizeBranchOrder(b.edge.properties?.branchOrder) ?? Number.MAX_SAFE_INTEGER) ||
          a.index - b.index
      )
      .forEach((item, position) => {
        branchOrderMap.set(item.edge.id, position + 1)
      })
  })
  // 连线 save 元素（fieldTemplateId 服务端回填，不提交）
  const saveLines = rawEdges
    .map((edge) => {
      const props = edge.properties || {}
      const fromKey = keyMap.get(edge.sourceNodeId)
      const toKey = keyMap.get(edge.targetNodeId)
      if (!fromKey || !toKey) {
        return null
      }
      const sourceType = typeById.get(edge.sourceNodeId)
      const line = {
        fieldId: props.lineId || genLocalId(),
        fieldFromKey: fromKey,
        fieldToKey: toKey,
        // fieldLineType 由源节点类型决定（接口清单 §4.2「前端只读推导、不手工选择」）：
        // 推导优先，边属性遗留值兜底（防旧数据线型与源类型不一致）
        fieldLineType: LINE_TYPE_BY_SOURCE[sourceType] || props.lineType || 'NORMAL',
      }
      if (BRANCH_SOURCE_TYPES.includes(sourceType)) {
        line.fieldBranchOrder = branchOrderMap.get(edge.id) ?? 1
        line.fieldBranchName = props.branchName || ''
        line.fieldCondition = props.condition || ''
        line.fieldIsDefault = props.isDefault === true
      }
      return line
    })
    .filter(Boolean)
  return { nodes: saveNodes, lines: saveLines }
}

/** 节点类型取值兜底（properties 缺失时按 LogicFlow 注册名反查） */
function resolveNodeType(node) {
  return node.properties?.nodeType || FLOW_TYPE_TO_NODE_TYPE[node.type] || ''
}

/**
 * 客户端图校验（接口清单 §4.4 G1~G7 的可前端判定子集，G8~G10 交后端兜底）：
 *   G1 N1 沿连线可达 N3；G2 无孤立节点；G3 出边数符合类型约束；G4 条件分支默认边唯一；
 *   G5 FORK/JOIN 成对（客户端子集：FORK 存在则 JOIN 存在、分支可达 JOIN、不嵌套）；
 *   G6 MANUAL 默认流向必须是实际出边目标之一（未指定亦提示）；G7 APPROVE operations
 *   必含且仅含一个 handler_pass。
 * @param graphData lf.getGraphRawData() 形态
 * @returns {string[]} 中文错误消息数组（空数组 = 通过）
 */
export function validateGraph(graphData) {
  const errors = []
  const rawNodes = graphData?.nodes || []
  const rawEdges = graphData?.edges || []
  const items = rawNodes.map((node) => ({
    id: node.id,
    key: node.properties?.nodeKey || node.id || '',
    type: resolveNodeType(node),
    name: node.properties?.nodeName || '',
    props: node.properties || {},
  }))
  if (rawNodes.length === 0) {
    return ['流程图为空：缺少开始/起草/结束固定节点']
  }
  const byId = new Map(items.map((item) => [item.id, item]))
  const outs = new Map(items.map((item) => [item.id, []]))
  const inCount = new Map(items.map((item) => [item.id, 0]))
  rawEdges.forEach((edge) => {
    if (outs.has(edge.sourceNodeId) && byId.has(edge.targetNodeId)) {
      outs.get(edge.sourceNodeId).push(edge)
      inCount.set(edge.targetNodeId, (inCount.get(edge.targetNodeId) || 0) + 1)
    }
  })
  const start = items.find((item) => item.type === 'START')
  const end = items.find((item) => item.type === 'END')
  if (!start || !end) {
    errors.push('流程图缺少开始/结束固定节点')
    return errors
  }
  const identity = (item) => `「${item.name || NODE_TYPE_LABELS[item.type] || item.type}」(${item.key})`
  // G1：N1 沿连线可达 N3
  const reached = new Set([start.id])
  const queue = [start.id]
  while (queue.length) {
    const current = queue.shift()
    outs.get(current).forEach((edge) => {
      if (!reached.has(edge.targetNodeId)) {
        reached.add(edge.targetNodeId)
        queue.push(edge.targetNodeId)
      }
    })
  }
  if (!reached.has(end.id)) {
    errors.push('首尾不连通：开始节点沿连线无法到达结束节点')
  }
  // G2：无孤立节点（每个节点至少一条入边或出边；N1/N3 由各自入/出边天然满足）
  const isolated = items.filter((item) => outs.get(item.id).length === 0 && (inCount.get(item.id) || 0) === 0)
  if (isolated.length) {
    errors.push(`存在孤立节点：${isolated.map(identity).join('、')}`)
  }
  // G3：出边数符合类型约束
  items.forEach((item) => {
    const limit = OUT_EDGE_LIMITS[item.type] ?? 'N'
    const count = outs.get(item.id).length
    const label = NODE_TYPE_LABELS[item.type] || item.type
    if (limit === 0 && count > 0) {
      errors.push(`${identity(item)}为${label}，不允许有流出连线`)
    } else if (limit === 1 && count === 0) {
      errors.push(`${identity(item)}缺少流出连线`)
    } else if (limit === 1 && count > 1) {
      errors.push(`${identity(item)}只允许一条流出连线`)
    } else if (limit === 'N' && count === 0) {
      errors.push(`${identity(item)}至少需要一条流出连线`)
    }
  })
  // G4：条件分支默认边有且仅有一条
  items
    .filter((item) => item.type === 'CONDITION')
    .forEach((item) => {
      const defaultCount = outs.get(item.id).filter((edge) => edge.properties?.isDefault === true).length
      if (defaultCount !== 1) {
        errors.push(`条件分支${identity(item)}必须指定且仅指定一条默认分支`)
      }
    })
  // G5：FORK/JOIN 成对（客户端可判定子集）
  const forks = items.filter((item) => item.type === 'FORK')
  const hasJoin = items.some((item) => item.type === 'JOIN')
  if (forks.length > 0 && !hasJoin) {
    errors.push('存在并行开始节点但缺少并行结束节点（并行开始与并行结束必须成对使用）')
  }
  // 分支沿不经过其他 FORK 的路径必须可达某个 JOIN；路径中出现 FORK = 嵌套并行
  const canReachJoin = (startId) => {
    const visited = new Set()
    const stack = [startId]
    while (stack.length) {
      const current = stack.pop()
      if (visited.has(current)) {
        continue
      }
      visited.add(current)
      const item = byId.get(current)
      if (!item) {
        continue
      }
      if (item.type === 'JOIN') {
        return true
      }
      if (item.type === 'FORK') {
        continue
      }
      outs.get(current).forEach((edge) => stack.push(edge.targetNodeId))
    }
    return false
  }
  forks.forEach((fork) => {
    let nested = false
    const unjoined = []
    outs.get(fork.id).forEach((edge) => {
      const visited = new Set()
      const stack = [edge.targetNodeId]
      while (stack.length) {
        const current = stack.pop()
        if (visited.has(current)) {
          continue
        }
        visited.add(current)
        const item = byId.get(current)
        if (!item) {
          continue
        }
        if (item.type === 'JOIN') {
          continue
        }
        if (item.type === 'FORK') {
          nested = true
          break
        }
        outs.get(current).forEach((next) => stack.push(next.targetNodeId))
      }
      if (!nested && !canReachJoin(edge.targetNodeId)) {
        unjoined.push(edge)
      }
    })
    if (nested) {
      errors.push(`不允许嵌套并行分支：${identity(fork)}的并行分支路径中出现了另一个并行开始节点`)
    } else if (unjoined.length > 0) {
      errors.push(`并行分支必须汇聚到并行结束节点：${identity(fork)}存在未汇聚到并行结束节点的分支`)
    }
  })
  // G6：MANUAL 默认流向必须是实际出边目标之一
  items
    .filter((item) => item.type === 'MANUAL')
    .forEach((item) => {
      const targets = new Set(outs.get(item.id).map((edge) => edge.targetNodeId))
      const defaultFlowKey = item.props.defaultFlowKey
      if (!defaultFlowKey) {
        errors.push(`人工决策${identity(item)}未指定默认流向`)
      } else if (!targets.has(defaultFlowKey)) {
        errors.push(`人工决策${identity(item)}的默认流向必须是其实际出边目标之一`)
      }
    })
  // G7：APPROVE operations 必含且仅含一个 handler_pass
  items
    .filter((item) => item.type === 'APPROVE')
    .forEach((item) => {
      const operations = Array.isArray(item.props.operations) ? item.props.operations : []
      const passCount = operations.filter((op) => op && op.operateType === 'handler_pass').length
      if (passCount !== 1) {
        errors.push(`审批节点${identity(item)}的审批操作必须包含且仅包含一个「通过」操作`)
      }
    })
  return errors
}

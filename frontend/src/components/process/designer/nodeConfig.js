/**
 * 流程设计器 · 节点配置弹窗纯逻辑模块（v1_7 / P17-FE-003）
 *
 * 纯 JS 模块：不 import Vue / Element Plus / LogicFlow，可被 node 冒烟脚本直接断言。
 * 承载 FlowNodeConfigDialog 的字段规格、旧模板回显归一、skipMode 旧值兼容、审批操作
 * 三段明细拆分合并与 G7 校验、流程任务明细行与 params JSON 预检、配置字段回写组装；
 * 并导出汇聚模式/无可流转分支行为枚举（JOIN_MODE_OPTIONS / NO_BRANCH_MODE_OPTIONS，
 * V1.12 起由 FlowForkNodeConfigDialog 消费——FORK 已移出节点配置弹窗）。
 *
 * 契约来源（冲突时以接口清单为准）：
 *   - 《流程v1_7接口清单》§4.2 nodes[] 节点 VO 全字段（配置字段平铺）；
 *   - 《流程v1_7前端UI设计分册》§5.1（标签结构）/ §5.2（审批节点基本标签）/
 *     §5.3（CC·MANUAL·ROBORT 字段；FORK 汇聚配置自 V1.12 起落并行分支节点配置弹窗
 *     §4.5-2，joinMode「无可流转分支行为」键名 noBranchMode 为 T-09 定稿）/ §5.5（流程任务明细表）；
 *   - 《流程核心业务.md》「审批操作矩阵」14 类操作标识——可选操作类型按段取
 *     drafter_* / handler_* / processed_* 前缀共 11 类，admin_* 为特权操作不入节点配置；
 *   - 后端 `SysProcessTemplateNodeConfigDo`（Do v2）：字段一一对应；params 为
 *     Map<String,Object>（JSON 对象）；operations 元素 {operateType, operateName, defaultContent}。
 *
 * 自定项登记（分册未冻结细节，P17-DOC-010 走查时可调）：
 *   - 下拉枚举（approveMode/skipMode/autoContinueSkip/emptyHandlerMode；joinMode/noBranchMode
 *     由并行分支节点配置弹窗消费）按分册与接口清单冻结值内置本模块（设计器组件不依赖运行期码值字典）；
 *   - 审批操作中非三段前缀（drafter_/handler_/processed_）的行（如 admin_*）不进弹窗、
 *     回写时丢弃（admin_* 不入节点配置为矩阵冻结口径）；
 *   - skipMode 回写口径：未改动保留原值（含旧值 yes/no 与缺省→system），改动才写新枚举值；
 *   - params JSON 预检按 Do 的 Map<String,Object> 口径要求为 JSON 对象（数组/标量视为非法）。
 */

/** 审批源选项（分册 §5.2：org 默认 / field 沿用 / formula v1_7 新增） */
export const HANDLER_SOURCE_OPTIONS = [
  { value: 'org', label: '组织架构（地址本）' },
  { value: 'field', label: '表单字段' },
  { value: 'formula', label: '公式定义器' },
]

/** 抄送源选项（分册 §5.3：org / formula） */
export const CC_SOURCE_OPTIONS = [
  { value: 'org', label: '组织架构（地址本）' },
  { value: 'formula', label: '公式定义器' },
]

/** 审批方式选项（接口清单 §4.2：single 单签默认 / serial 串行 v1_7 新增 / countersign 会签） */
export const APPROVE_MODE_OPTIONS = [
  { value: 'single', label: '单签' },
  { value: 'serial', label: '串行' },
  { value: 'countersign', label: '会签' },
]

/** 相同审批人是否跳过（分册 §5.2：system/none/prev/any，旧值 yes/no 前端映射显示） */
export const SKIP_MODE_OPTIONS = [
  { value: 'system', label: '同系统配置' },
  { value: 'none', label: '不跳过' },
  { value: 'prev', label: '上一审批节点审批人相同时跳过' },
  { value: 'any', label: '已审批人相同时跳过' },
]

/** 是否允许连续跳过（沿用现状：system/yes/no，默认 system） */
export const AUTO_CONTINUE_SKIP_OPTIONS = [
  { value: 'system', label: '同系统配置' },
  { value: 'yes', label: '是' },
  { value: 'no', label: '否' },
]

/** 无审批人时是否跳过（分册 §5.2：system 默认 / skip / error） */
export const EMPTY_HANDLER_MODE_OPTIONS = [
  { value: 'system', label: '同系统配置（默认跳过）' },
  { value: 'skip', label: '跳过该节点' },
  { value: 'error', label: '不跳过（转流程异常）' },
]

/** 汇聚模式（分册 §5.3：all 默认 / any；V1.12 起由并行分支节点配置弹窗 FlowForkNodeConfigDialog 消费） */
export const JOIN_MODE_OPTIONS = [
  { value: 'all', label: '全部激活分支到达后流出' },
  { value: 'any', label: '任一激活分支到达即流出' },
]

/** 无可流转分支时行为（T-09 定稿键名 noBranchMode：error 默认 / continue；V1.12 起由 FlowForkNodeConfigDialog 消费） */
export const NO_BRANCH_MODE_OPTIONS = [
  { value: 'error', label: '报错（转流程异常）' },
  { value: 'continue', label: '继续（全部出边分支均激活）' },
]

/** 审批人解析方式（分册 §5.2：leader 负责人默认 / member 成员；仅对部门/岗位生效） */
export const RESOLVE_MODE_OPTIONS = [
  { value: 'leader', label: '负责人' },
  { value: 'member', label: '成员' },
]

/**
 * 审批操作三段分组与各段合法操作类型（《流程核心业务》「审批操作矩阵」；
 * admin_* 为特权操作不入节点配置，故每段类型为矩阵的 11 类非管理员子集）。
 */
export const OPERATION_SEGMENTS = [
  {
    key: 'drafter',
    label: '起草人操作',
    prefix: 'drafter_',
    types: [
      { value: 'drafter_submit', label: '起草人提交' },
      { value: 'drafter_cancel', label: '起草人取消' },
      { value: 'drafter_retract', label: '起草人收回' },
      { value: 'drafter_expedite', label: '起草人催办' },
    ],
  },
  {
    key: 'handler',
    label: '审批人操作',
    prefix: 'handler_',
    types: [
      { value: 'handler_pass', label: '通过' },
      { value: 'handler_reject', label: '拒绝' },
      { value: 'handler_cc', label: '抄送' },
      { value: 'handler_transfer', label: '转办' },
      { value: 'handler_transfer_retract', label: '转办收回' },
    ],
  },
  {
    key: 'processed',
    label: '已处理人操作',
    prefix: 'processed_',
    types: [
      { value: 'processed_retract', label: '已审批收回' },
      { value: 'processed_expedite', label: '已审批人催办' },
    ],
  },
]

/** 全部合法操作类型 → 中文名（操作名称留空时回填） */
export const OPERATE_TYPE_LABELS = Object.fromEntries(
  OPERATION_SEGMENTS.flatMap((segment) => segment.types.map((type) => [type.value, type.label]))
)

/** 执行步骤下拉（ProcessTaskType 八枚举，分册 §5.5 冻结中文对照） */
export const PROCESS_TASK_TYPE_OPTIONS = [
  { value: 'CreateProcess', label: '创建实例' },
  { value: 'StartProcess', label: '流程发起' },
  { value: 'StartNode', label: '节点进入' },
  { value: 'StartWorkitem', label: '工作项开始' },
  { value: 'SendTodo', label: '发送待办' },
  { value: 'EndWorkitem', label: '工作项结束' },
  { value: 'EndNode', label: '节点结束' },
  { value: 'EndProcess', label: '流程结束' },
]

/** 任务参数 JSON 预检失败文案（分册 §5.5 冻结提示） */
export const PARAMS_ERROR_TEXT = '任务参数必须是合法 JSON'

/** skipMode 旧值映射（分册 §5.2：yes→prev / no→none，仅显示归一，不改写存量 JSON） */
export const SKIP_LEGACY_MAP = { yes: 'prev', no: 'none' }

/** skipMode 显示归一：旧值 yes/no 映射为 prev/none，空/缺省取 system（Do v2 读取同口径） */
export function normalizeSkipModeDisplay(value) {
  if (value === null || value === undefined || String(value).trim() === '') {
    return 'system'
  }
  const trimmed = String(value).trim()
  return SKIP_LEGACY_MAP[trimmed] || trimmed
}

/** 新审批节点的默认审批操作（G7 最小满足：仅一条「通过」；后端 defaultOperationsIfEmpty 同口径） */
export function defaultOperations() {
  return [{ operateType: 'handler_pass', operateName: '通过', defaultContent: '' }]
}

/** 旧模板回显归一：operations 缺失/为空时按默认「通过」操作回显，不因缺字段报错 */
export function normalizeOperationsEcho(operations) {
  return Array.isArray(operations) && operations.length > 0 ? operations : defaultOperations()
}

/**
 * 平铺 operations 数组 → 三段分组工作副本（行序保持原序）。
 * 非三段前缀的行（admin_* 等不入节点配置的标识）不进弹窗、回写时丢弃（见文件头登记）。
 * 行内附加 _autoName 标记操作名称是否为自动回填（回写前剔除该标记）。
 */
export function splitOperationsBySegment(operations) {
  const segments = { drafter: [], handler: [], processed: [] }
  ;(Array.isArray(operations) ? operations : []).forEach((row) => {
    if (!row) {
      return
    }
    const type = String(row.operateType || '')
    const segment = OPERATION_SEGMENTS.find((item) => type.startsWith(item.prefix))
    if (!segment) {
      return
    }
    segments[segment.key].push({
      operateType: type,
      operateName: String(row.operateName || ''),
      defaultContent: String(row.defaultContent || ''),
      _autoName: false,
    })
  })
  return segments
}

/** 三段分组 → 平铺 operations 数组（段序 drafter → handler → processed，段内保持行序；未选类型的行丢弃） */
export function mergeOperationSegments(segments) {
  const result = []
  OPERATION_SEGMENTS.forEach((segment) => {
    ;(segments?.[segment.key] || []).forEach((row) => {
      if (!row || !row.operateType) {
        return
      }
      result.push({
        operateType: row.operateType,
        operateName: String(row.operateName || '').trim(),
        defaultContent: String(row.defaultContent || ''),
      })
    })
  })
  return result
}

/**
 * 审批操作客户端校验（G7 前端子集）：
 *   - 存在已填内容但未选操作类型的行 → 提示补全或删除；
 *   - 全表 handler_pass 数量 ≠ 1 → 提示（与 graphTransform.validateGraph G7 文案同口径）。
 * @returns {string|null} 错误提示（null = 通过）
 */
export function validateOperations(segments) {
  const flat = OPERATION_SEGMENTS.flatMap((segment) => segments?.[segment.key] || [])
  const untyped = flat.some(
    (row) => !row.operateType && (String(row.operateName || '').trim() || String(row.defaultContent || '').trim())
  )
  if (untyped) {
    return '存在未选择操作类型的审批操作行，请补全或删除'
  }
  const passCount = flat.filter((row) => row.operateType === 'handler_pass').length
  if (passCount !== 1) {
    return '审批操作必须包含且仅包含一个「通过」(handler_pass) 操作'
  }
  return null
}

/** 操作名称留空自动回填类型中文名（分册 §5.1/核心业务「操作名称可编辑，默认操作类型名」） */
export function fillOperationNames(segments) {
  OPERATION_SEGMENTS.forEach((segment) => {
    ;(segments?.[segment.key] || []).forEach((row) => {
      if (row && row.operateType && !String(row.operateName || '').trim()) {
        row.operateName = OPERATE_TYPE_LABELS[row.operateType] || row.operateType
      }
    })
  })
}

/**
 * 任务参数 JSON 预检（Do v2 的 params 为 Map<String,Object>）：
 * 可空；非空时须为 JSON 对象（数组/标量/非法 JSON 均视为不合法）。
 */
export function isValidParamsJson(text) {
  const trimmed = String(text || '').trim()
  if (!trimmed) {
    return true
  }
  try {
    const parsed = JSON.parse(trimmed)
    return parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)
  } catch {
    return false
  }
}

/** params 对象 → 编辑用 JSON 文本（null/非法输入返回空串；两空格缩进便于编辑） */
export function paramsToText(params) {
  if (params && typeof params === 'object' && !Array.isArray(params)) {
    try {
      return JSON.stringify(params, null, 2)
    } catch {
      return ''
    }
  }
  return ''
}

/** 编辑用 JSON 文本 → params 对象（可空；非法/非对象返回 null，配合 isValidParamsJson 预检使用） */
export function paramsFromText(text) {
  const trimmed = String(text || '').trim()
  if (!trimmed) {
    return null
  }
  try {
    const parsed = JSON.parse(trimmed)
    if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed
    }
  } catch {
    // 非法 JSON 返回 null（预检已在先，此处兜底）
  }
  return null
}

/** VO tasks[] → 弹窗明细行（params 对象转编辑文本，缺字段补默认值不报错） */
export function taskRowsFromVo(tasks) {
  return (Array.isArray(tasks) ? tasks.filter(Boolean) : []).map((task) => ({
    taskName: String(task.taskName || ''),
    taskBean: String(task.taskBean || ''),
    taskType: String(task.taskType || ''),
    paramsText: paramsToText(task.params),
  }))
}

/** 弹窗明细行 → VO tasks[]（行序即执行顺序，不加排序字段；params 经预检文本还原对象） */
export function taskRowsToVo(rows) {
  return (Array.isArray(rows) ? rows : []).map((row) => ({
    taskName: String(row.taskName || '').trim(),
    taskBean: String(row.taskBean || ''),
    taskType: String(row.taskType || ''),
    params: paramsFromText(row.paramsText),
  }))
}

/** 任务明细行客户端校验（分册 §5.5：任务名称必填 / 执行步骤必填 / params JSON 预检） */
export function validateTaskRows(rows) {
  const errors = []
  ;(Array.isArray(rows) ? rows : []).forEach((row, index) => {
    const no = index + 1
    if (!String(row.taskName || '').trim()) {
      errors.push(`第 ${no} 行任务名称不能为空`)
    }
    if (!String(row.taskType || '').trim()) {
      errors.push(`第 ${no} 行执行步骤不能为空`)
    }
    if (!isValidParamsJson(row.paramsText)) {
      errors.push(`第 ${no} 行${PARAMS_ERROR_TEXT}`)
    }
  })
  return errors
}

/**
 * 候选选项并入当前值（回显兜底）：当前值不在候选集（图已改动导致）时追加展示，
 * 保证旧模板回显不丢值；合法性最终由服务端 G9/G10/G6 兜底。
 */
export function withCurrentValue(options, values) {
  const known = new Set((options || []).map((option) => option.nodeKey))
  const merged = [...(options || [])]
  ;(Array.isArray(values) ? values : values ? [values] : []).forEach((key) => {
    if (key && !known.has(key)) {
      merged.push({ nodeKey: key, nodeName: '' })
    }
  })
  return merged
}

/** 候选项展示文本：「节点名称（node_key）」（无名称回退 node_key） */
export function candidateLabel(candidate) {
  if (!candidate) {
    return ''
  }
  return candidate.nodeName ? `${candidate.nodeName}（${candidate.nodeKey}）` : String(candidate.nodeKey)
}

/**
 * 弹窗表单回显状态（旧模板缺字段按默认值回显，不报错）：
 * 节点入参 { nodeKey, fieldNodeType, nodeName, properties }（properties 为配置平铺字段）。
 * ops 开关回显依赖运行期分类字典（弹窗内按 opsOptions 构建），不在本函数内。
 */
export function echoFormState(node, opsEcho = {}) {
  const props = (node && node.properties) || {}
  return {
    // 节点名称（全部类型）
    nodeName: String((node && node.nodeName) || props.nodeName || ''),
    // APPROVE · 基本
    handlerSource: props.handlerSource || 'org',
    handlerIds: props.handlerIds || '',
    handlerNames: props.handlerNames || '',
    handlerResolveMode: props.handlerResolveMode === 'member' ? 'member' : 'leader',
    handlerField: props.handlerField || '',
    handlerFormula: props.handlerFormula || '',
    approveMode: props.approveMode || 'single',
    skipModeRaw: props.skipMode,
    skipMode: normalizeSkipModeDisplay(props.skipMode),
    skipModeTouched: false,
    autoContinueSkip: props.autoContinueSkip || 'system',
    emptyHandlerMode: props.emptyHandlerMode || 'system',
    rejectTargets: Array.isArray(props.rejectTargets) ? props.rejectTargets.filter(Boolean) : [],
    // CC · 基本
    ccSource: props.ccSource || 'org',
    ccIds: props.ccIds || '',
    ccNames: props.ccNames || '',
    ccFormula: props.ccFormula || '',
    // MANUAL · 基本
    flowDecisions: Array.isArray(props.flowDecisions) ? props.flowDecisions.filter(Boolean) : [],
    defaultFlowKey: props.defaultFlowKey || '',
    // APPROVE · 审批操作（三段分组工作副本）
    operations: splitOperationsBySegment(normalizeOperationsEcho(props.operations)),
    // 流程任务（弹窗明细行）
    tasks: taskRowsFromVo(props.tasks),
    // 审批权限开关（弹窗内按 opsOptions 字典 ∩ opsEcho 构建开关集）
    opsEcho: opsEcho && typeof opsEcho === 'object' ? opsEcho : {},
    // 解析方式可编辑性（会话内经地址本确认后置位；回显场景未知=false，见弹窗展示口径）
    resolveKnown: false,
    selectedOrgTypes: [],
  }
}

/** ops 开关集构建：仅字典键参与（现状 confirmConfig 同口径），回显值取布尔 */
export function buildOpsSwitches(opsOptions, opsEcho) {
  const switches = {}
  Object.keys(opsOptions || {}).forEach((key) => {
    switches[key] = !!(opsEcho && opsEcho[key])
  })
  return switches
}

/** ops 回写：仅保留开启项（false/未开启等同不允许，后端默认不允许语义兜底；现状 confirmConfig 同口径） */
export function buildEnabledOps(switches) {
  const enabled = {}
  Object.entries(switches || {}).forEach(([key, value]) => {
    if (key && value === true) {
      enabled[key] = true
    }
  })
  return enabled
}

/**
 * 配置字段回写组装（【确定】只做客户端校验并回写，不调接口）：
 *   - 仅产出弹窗渲染的字段；节点其余 properties（存量 triggerType/condition 等遗留字段）
 *     由画布 updateNodeProperties 局部合并原样保留；
 *   - 切换审批源/抄送源后按来源保留对应明细（现状 confirmConfig 同口径），
 *     非当前来源字段写 '' 清空防残留提交；
 *   - skipMode 未改动保留原值（含旧值 yes/no）、改动才写新枚举值。
 * 返回值直接作为画布 updateNodeProperties 的 partial 入参（nodeName 由画布同步节点文本）。
 */
export function buildConfigFields(nodeType, form) {
  switch (nodeType) {
    case 'APPROVE':
      return {
        nodeName: String(form.nodeName || '').trim(),
        handlerSource: form.handlerSource || 'org',
        handlerIds: form.handlerSource === 'org' ? String(form.handlerIds || '').trim() : '',
        handlerNames: form.handlerSource === 'org' ? String(form.handlerNames || '').trim() : '',
        handlerField: form.handlerSource === 'field' ? String(form.handlerField || '').trim() : '',
        handlerFormula: form.handlerSource === 'formula' ? String(form.handlerFormula || '').trim() : '',
        handlerResolveMode: form.handlerResolveMode || 'leader',
        approveMode: form.approveMode || 'single',
        skipMode: form.skipModeTouched ? form.skipMode : String(form.skipModeRaw || 'system'),
        autoContinueSkip: form.autoContinueSkip || 'system',
        emptyHandlerMode: form.emptyHandlerMode || 'system',
        rejectTargets: [...(form.rejectTargets || [])],
        operations: mergeOperationSegments(form.operations),
        ops: buildEnabledOps(form.ops),
        tasks: taskRowsToVo(form.tasks),
      }
    case 'CC':
      return {
        nodeName: String(form.nodeName || '').trim(),
        ccSource: form.ccSource || 'org',
        ccIds: form.ccSource === 'org' ? String(form.ccIds || '').trim() : '',
        ccNames: form.ccSource === 'org' ? String(form.ccNames || '').trim() : '',
        ccFormula: form.ccSource === 'formula' ? String(form.ccFormula || '').trim() : '',
        tasks: taskRowsToVo(form.tasks),
      }
    case 'MANUAL':
      return {
        nodeName: String(form.nodeName || '').trim(),
        flowDecisions: [...(form.flowDecisions || [])],
        defaultFlowKey: String(form.defaultFlowKey || '').trim(),
      }
    case 'ROBORT':
      return {
        nodeName: String(form.nodeName || '').trim(),
        tasks: taskRowsToVo(form.tasks),
      }
    default:
      return { nodeName: String(form.nodeName || '').trim() }
  }
}

/**
 * 自定义表单规则引擎——条件求值与动作执行器（F15-FE-006）
 *
 * 口径依据：docs/v1_5-自定义表单/自定义表单功能详细设计.md
 * - §3.1 统一 DSL（规则随触发字段存放于字段对象 rules[]，condition 可省略=无条件规则）；
 * - §3.2 operator 全集（10 个，一期定稿）：equal/notEqual/gt/gte/lt/lte/in/notIn/empty/notEmpty；
 * - §3.3 动作类型全集（6 个，一期定稿）：show/hide/setValue/clearValue/setRequired/setValidate；
 * - §3.4 事件清单（8 个）：onSubmit/onValidate 禁止配置 setValue/clearValue（防提交竞态）；
 * - §3.5 循环防护口径（幂等短路的值相等判定由本模块 valueEquals 承载）；
 * - §3.6 隐藏字段值保留策略（ADR-08 渲染层：hide 仅切可见性、绝不清值——本模块 hide
 *   动作只写状态载体 hidden 集合，不触碰数据通道）。
 *
 * 【纯 JS 约束】本文件零 import（连 Vue 也不引），可被 node 冒烟脚本直接加载；
 * 与 rendererSupport.js 同口径。取值 / 写值 / 遍历行等有状态操作全部经调用方注入的
 * 访问器（getFieldValue / writeValue / forEachRow）完成——引擎逻辑与数据通道解耦。
 *
 * 【与后端同语义】条件求值 / 引用解析 / 值相等判定 / 空值判定逐条对齐后端
 * F15-BE-004 FormValidationEngine 的 conditionMatches / resolveReference /
 * valueEquals / isEmptyValue（R09：前端求值是交互预演，服务端复算是权威裁决，
 * 两侧共用同一 DSL 语义保证结果一致）；operator 全集与 materials/common.js
 * CONDITION_OPERATORS 同值登记（§3.2 十项，冒烟脚本断言两侧相等防漂移）。
 */

/* ==================== 规则协议常量（功能设计 §3） ==================== */

/** 条件 operator 全集（§3.2 十项；与 materials/common.js CONDITION_OPERATORS 同值） */
export const RULE_OPERATORS = ['equal', 'notEqual', 'gt', 'gte', 'lt', 'lte', 'in', 'notIn', 'empty', 'notEmpty']

/** 动作类型全集（§3.3 六项，一期定稿；script 类动作 R02 一期不开放） */
export const RULE_ACTION_TYPES = ['show', 'hide', 'setValue', 'clearValue', 'setRequired', 'setValidate']

/**
 * 禁止配置值类动作的事件（§3.4：onSubmit/onValidate 仅支持 setRequired/setValidate/
 * show/hide；setValue/clearValue 遇配置跳过并告警，防提交竞态）。
 */
export const VALUE_FORBIDDEN_EVENTS = ['onSubmit', 'onValidate']

/**
 * setValue 联动赋值的 ${字段码} 引用形态（§3.3：value 为字面量或 ${字段码} 引用；
 * 整值精确引用，非整值引用形态按字面量处理——与后端 FormValidationEngine.
 * FIELD_REFERENCE 同一正则语义，字段码约束 §2.1 对齐 F14 变量码）。
 */
export const FIELD_REFERENCE_PATTERN = /^\$\{([a-zA-Z_][a-zA-Z0-9_]*)\}$/

/* ==================== 条件求值原语（§3.2，对齐后端 conditionMatches） ==================== */

/**
 * DSL 空值判定（empty/notEmpty 用）：null / undefined / 空白串 / 空数组 / 空对象为空。
 * 与后端 FormValidationEngine.isEmptyValue 同口径（空白串视为无值）；注意与
 * materials/common.js isEmptyValue（物料校验器口径，含地址本双形态特判）是两套语义——
 * DSL 条件求值对齐服务端复算口径，地址本空值以载荷 {ids:[],names:[]} 的对象形态
 * 按「非空对象」处理（与后端 Map.isEmpty 一致）。
 * @param {*} value 字段值
 * @returns {boolean} 是否为空
 */
export function isEmptyConditionValue(value) {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  if (typeof value === 'object') return Object.keys(value).length === 0
  return false
}

/**
 * 标量精确比对（§3.2 equal/notEqual：字符串/数值/布尔精确匹配）。
 * 数值按值域比对（容忍同一数值的 JSON 表示漂移，如 1 与 1.0——JS 中 1===1.0 天然成立），
 * 其余类型严格相等（与后端 scalarEquals 同语义）。
 * @param {*} actual 实际值（触发字段当前值）
 * @param {*} expected 期望值（condition.value）
 * @returns {boolean}
 */
export function scalarEquals(actual, expected) {
  if (typeof actual === 'number' && typeof expected === 'number') {
    return Number.isNaN(actual) && Number.isNaN(expected) ? true : actual === expected
  }
  return actual === expected
}

/**
 * 大小比较（§3.2 gt/gte/lt/lte：NUMBER 数值比较；date 按 same-format 字符串字典序
 * 比较）。空值 / 类型不配（number↔string 等）不可比较，返回 null（一律不匹配，
 * 与后端 compare 同语义）。
 * @param {*} actual 实际值
 * @param {*} expected 期望值
 * @returns {number|null} 负数/0/正数；不可比较为 null
 */
export function compareValues(actual, expected) {
  if (typeof actual === 'number' && typeof expected === 'number') {
    return actual === expected ? 0 : actual < expected ? -1 : 1
  }
  if (typeof actual === 'string' && typeof expected === 'string') {
    return actual < expected ? -1 : actual === expected ? 0 : 1
  }
  return null
}

/** in / notIn 集合属于判定：expected 必须为数组（结构校验器保证；防御性非数组判不属于） */
function containsCandidate(expected, actual) {
  if (!Array.isArray(expected)) return false
  return expected.some((candidate) => scalarEquals(actual, candidate))
}

/**
 * 条件求值（§3.1/§3.2）：condition 为 null/undefined 视为无条件规则（事件即触发）；
 * value 为触发字段当前值（主表字段值 / 明细行数组 / 行内字段值随宿主层级）。
 * 非法 operator 防御性不匹配（定义期结构校验器已按全集拦截）。
 *
 * @param {object|null} condition 规则条件 { operator, value? }（可省略）
 * @param {*} value 触发字段当前值
 * @returns {boolean} 条件是否匹配
 */
export function conditionMatches(condition, value) {
  if (condition === null || condition === undefined) return true
  if (typeof condition !== 'object') return false
  const operator = condition.operator
  const expected = condition.value
  switch (operator) {
    case 'equal':
      return scalarEquals(value, expected)
    case 'notEqual':
      return !scalarEquals(value, expected)
    case 'gt':
    case 'gte':
    case 'lt':
    case 'lte': {
      const order = compareValues(value, expected)
      if (order === null) return false
      if (operator === 'gt') return order > 0
      if (operator === 'gte') return order >= 0
      if (operator === 'lt') return order < 0
      return order <= 0
    }
    case 'in':
      return containsCandidate(expected, value)
    case 'notIn':
      return !containsCandidate(expected, value)
    case 'empty':
      return isEmptyConditionValue(value)
    case 'notEmpty':
      return !isEmptyConditionValue(value)
    default:
      return false
  }
}

/**
 * 值相等判定（幂等短路用，§3.5 第 3 层）：数值按值域比对，其余严格相等。
 * 与后端 FormValidationEngine.valueEquals 同语义。
 * @param {*} current 当前值
 * @param {*} resolved 目标值
 * @returns {boolean}
 */
export function valueEquals(current, resolved) {
  if (typeof current === 'number' && typeof resolved === 'number') {
    return Number.isNaN(current) && Number.isNaN(resolved) ? true : current === resolved
  }
  return current === resolved
}

/**
 * ${字段码} 整值引用解析（§3.3 setValue 联动赋值）：value 为 `${字段码}` 形态的字符串
 * 时按 getFieldValue 取该字段当前值（行上下文优先、回落主表层级由注入的取值函数
 * 决定），其余形态按字面量原样返回。与后端 resolveReference 同语义。
 *
 * @param {*} raw 动作 value 原始值
 * @param {(code: string) => *} getFieldValue 注入的取值函数（引擎按上下文装配）
 * @returns {*} 解析后的赋值目标值
 */
export function resolveReferenceValue(raw, getFieldValue) {
  if (typeof raw === 'string') {
    const matched = raw.match(FIELD_REFERENCE_PATTERN)
    if (matched && typeof getFieldValue === 'function') {
      return getFieldValue(matched[1])
    }
  }
  return raw
}

/* ==================== 动作执行器（§3.3 六类动作，对动态状态载体纯函数应用） ==================== */

/**
 * 创建动态状态载体（与后端 F15-BE-004 FormDynamicState 同语义的最小前端形态）：
 * - hidden：规则显隐终态——hide 命中的字段码集合（hide 不删值，ADR-08 渲染层口径①，
 *   仅切可见性、绝不清值）；
 * - shown：规则显隐终态——show 命中的字段码集合（show 在 props.hidden 静态态之上切换，
 *   可恢复静态初始隐藏字段的可见性；与 hidden 互斥，由动作执行器维护）；
 * - required：动态必填覆盖 Map<字段码, { value, ruleId }>（后写覆盖先写，ruleId 溯源）；
 * - validate：动态校验覆盖 Map<字段码, 覆盖项>——覆盖项逐键承载（min/max/pattern/message
 *   各自独立覆盖并保留触发规则 ID，同后端 DynamicValidate 逐键口径；仅覆盖所列项，
 *   其余保持字段静态校验器，§3.3）。
 *
 * 主表层级与明细行层级载体同构（行子载体由引擎按行上下文装配，行内动作只作用当前行）。
 * @returns {{ hidden: Set<string>, shown: Set<string>, required: Map<string, object>, validate: Map<string, object> }}
 */
export function createDynamicStateCarrier() {
  return {
    hidden: new Set(),
    shown: new Set(),
    required: new Map(),
    validate: new Map(),
  }
}

/**
 * 应用单条动作到动态状态载体（§3.3 六类动作的纯函数应用核心）：
 * - show/hide：切换载体显隐终态（hidden/shown 互斥集合），不触碰数据通道——hide 不删值
 *   （ADR-08 ①），需要清值必须显式配置 clearValue（语义严格分离）；
 * - setValue：value 经 ${字段码} 引用解析（注入 getFieldValue）后交注入的 writeValue
 *   写值——幂等短路（目标值与当前值相等不回写、不触发后续链）由 writeValue 实现方承载；
 * - clearValue：等价 setValue(target, null)（写 null 即「无值」形态）；
 * - setRequired：required.set(target, { value: value===true, ruleId })——Boolean 归一
 *   与后端 Boolean.TRUE.equals 同口径（仅 true 为必填）；
 * - setValidate：validate 覆盖项逐键合并（仅覆盖所列项），逐键登记触发规则 ID。
 *
 * @param {object} action 动作对象 { type, target, value?, validate? }
 * @param {{ hidden: Set, shown: Set, required: Map, validate: Map }} carrier 动态状态载体（主表或行子载体）
 * @param {{ ruleId: string|null, getFieldValue: (code: string) => *, writeValue: (target: string, resolved: *) => boolean }} options
 *        注入访问器：ruleId 触发规则 ID（溯源）；getFieldValue ${字段码} 引用取值；
 *        writeValue 值写入通道（返回是否实际变更，幂等短路在其内承载）
 * @returns {boolean} 值类动作（setValue/clearValue）返回是否实际变更；状态类动作恒 false
 */
export function applyRuleAction(action, carrier, options) {
  if (!action || typeof action !== 'object') return false
  const target = action.target
  if (typeof target !== 'string' || target === '') return false
  const opts = options || {}
  switch (action.type) {
    case 'show':
      // 恢复目标字段渲染可见性：在 props.hidden 静态态之上切换（§3.3）——shown 终态优先
      carrier.hidden.delete(target)
      carrier.shown.add(target)
      return false
    case 'hide':
      // 隐藏目标字段（不删值，§3.6 ADR-08 渲染层①）：仅切可见性，不清值、不动 required/validate
      carrier.shown.delete(target)
      carrier.hidden.add(target)
      return false
    case 'setValue':
      // 联动赋值：${字段码} 引用解析（行上下文优先、回落主表，由注入取值函数决定）
      return typeof opts.writeValue === 'function'
        ? opts.writeValue(target, resolveReferenceValue(action.value, opts.getFieldValue))
        : false
    case 'clearValue':
      // 显式清值动作：与 hide 语义严格分离（§3.3）；写 null 即「无值」形态
      return typeof opts.writeValue === 'function' ? opts.writeValue(target, null) : false
    case 'setRequired':
      // 动态必填切换：后写覆盖先写，ruleId 溯源触发规则（错误项 ruleId 溯源消费）
      carrier.required.set(target, { value: action.value === true, ruleId: opts.ruleId ?? null })
      return false
    case 'setValidate': {
      // 动态校验覆盖（§3.3：仅覆盖所列项 min/max/pattern/message，其余键保持字段静态校验器）
      const payload = action.validate
      if (!payload || typeof payload !== 'object') return false
      const entry = carrier.validate.get(target) || {}
      if (payload.min !== null && payload.min !== undefined) {
        entry.min = payload.min
        entry.minRuleId = opts.ruleId ?? null
      }
      if (payload.max !== null && payload.max !== undefined) {
        entry.max = payload.max
        entry.maxRuleId = opts.ruleId ?? null
      }
      if (typeof payload.pattern === 'string' && payload.pattern.trim() !== '') {
        entry.pattern = payload.pattern
        entry.patternRuleId = opts.ruleId ?? null
      }
      if (typeof payload.message === 'string' && payload.message.trim() !== '') {
        entry.message = payload.message
        entry.messageRuleId = opts.ruleId ?? null
      }
      carrier.validate.set(target, entry)
      return false
    }
    default:
      // 未知动作防御性跳过（结构校验器已按全集 6 个拦截，对齐后端 default 分支）
      return false
  }
}

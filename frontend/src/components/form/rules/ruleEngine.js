/**
 * 自定义表单规则引擎（F15-FE-006）——register / execute / validate 统一接口
 *
 * 口径依据：docs/v1_5-自定义表单/自定义表单功能详细设计.md
 * - §3.1 统一 DSL：规则随触发字段存放于字段对象 rules[]（含容器 children / 明细行内，
 *   行内规则带行上下文归属标记）；triggerField 显式保留；前端求值是交互预演，
 *   服务端（F15-BE-004 FormValidationEngine）按同一 DSL 复算是权威裁决；
 * - §3.3 动作六类经 rules/evaluators.js 执行器应用；
 * - §3.4 事件清单（8 个）：onLoad（初始化联动）/ onChange（主力）/ onAdd / onDelete
 *   （行上下文，FE-007 明细表接线）/ onSubmit / onValidate（提交段，禁 setValue/clearValue）；
 *   onFocus / onBlur 一期不开放规则配置（引擎登记期防御性忽略）；
 * - §3.5 循环防护三层（常量对齐后端 SysFormConstants，§2.2）：
 *   ① 次数熔断：单次交互风暴动作执行计数达 MAX_RULE_EXECUTIONS_PER_CYCLE（50）→
 *      剩余规则跳过 + console.warn + 渲染器状态位提示（不阻断表单）；
 *   ② 深度熔断：级联深度达 MAX_RULE_CHAIN_DEPTH（5）→ 熔断该分支；
 *   ③ 幂等短路：setValue 目标值与当前值相等不回写、不触发后续规则链；
 *   熔断状态位在下次风暴（顶层 execute）开始时复位（§3.5「渲染器提示，不阻断表单」）；
 * - §3.6 隐藏字段值保留策略（ADR-08）：hide 仅切可见性绝不清值（渲染层口径①）；
 *   被规则隐藏（终态 hidden ∪ 静态 hidden）的主表字段不参与必填校验（对齐后端
 *   FormDataBasicValidator 合并裁决）；明细行内显隐一期为渲染层语义、行内校验全量执行。
 *
 * 【纯 JS 约束】核心无 Vue 依赖：数据读写经注入访问器（readField/writeField/readRowField/
 * writeRowField/forEachRow）对接宿主数据通道，物料知识经 isDataField 访问器注入，
 * 可被 node 冒烟脚本直接加载测试；引擎不发任何请求、无 eval（架构自查项）。
 *
 * 【与后端同语义】规则平铺索引（主表 → 明细宿主 → 明细行）与固定点重放模型对齐后端
 * FormValidationEngine.ReplaySession；回显/初始化走值驱动全量重放（onChange/onSubmit/
 * onValidate 条件只依赖字段值，按当前载荷重建显隐与覆盖终态；onLoad 规则单轮初始化联动，
 * 对齐 §3.4 一期消费范围），交互事件走事件驱动风暴 + 级联深度 +1。
 */

import { flattenFields } from '../rendererSupport.js'
import {
  VALUE_FORBIDDEN_EVENTS,
  applyRuleAction,
  conditionMatches,
  createDynamicStateCarrier,
  isEmptyConditionValue,
  valueEquals,
} from './evaluators.js'

/** 单次交互（一次事件引发的规则风暴）动作执行次数上限（等值常量，对齐后端 SysFormConstants.MAX_RULE_EXECUTIONS_PER_CYCLE = 50，功能设计 §2.2/§3.5①） */
export const MAX_RULE_EXECUTIONS_PER_CYCLE = 50

/** 规则链级联深度上限（等值常量，对齐后端 SysFormConstants.MAX_RULE_CHAIN_DEPTH = 5，功能设计 §2.2/§3.5②） */
export const MAX_RULE_CHAIN_DEPTH = 5

/** 单表单规则总数上限（等值常量，对齐后端 SysFormConstants.MAX_RULE_COUNT = 100，功能设计 §2.2；超出仅告警不阻断——服务端结构校验为权威） */
export const MAX_RULE_COUNT = 100

/**
 * 值驱动重放事件域（§3.5「条件只依赖字段值」+ §3.4）：onLoad 重放时除 onLoad 初始化
 * 联动单轮外，按当前载荷对 onChange / onSubmit / onValidate 三类事件规则做固定点迭代，
 * 重建显隐与覆盖终态（对齐后端 SERVER_EVALUATED_EVENTS 复算口径；onAdd/onDelete 为
 * 交互历史事件不重放——动作效果已由载荷承载）。
 */
const REPLAY_EVENTS = ['onChange', 'onSubmit', 'onValidate']

/** 一期不开放规则配置的焦点类事件（§3.4：仅透传给物料组件，登记期防御性忽略） */
const FOCUS_EVENTS = ['onFocus', 'onBlur']

/**
 * 创建规则引擎实例。
 *
 * @param {object} [accessors] 注入访问器（渲染器桥接数据通道；node 冒烟注入普通对象）：
 *   - readField: (code) => value                          主表字段值读取（条件求值/幂等短路取当前值）
 *   - writeField: (code, value) => void                   主表字段值写入（渲染器数据通道回写）
 *   - readRowField: (detailCode, rowIndex, code) => value 明细行内字段值读取（缺省按行数组推导）
 *   - writeRowField: (detailCode, rowIndex, code, value) => void 行内字段值写入
 *   - forEachRow: (detailCode, callback(row, rowIndex)) => void  明细行遍历（宿主口径全行动作）
 *   - isDataField: (type, props) => boolean               数据类字段判定（物料 dataType 注入，
 *                                                         缺省恒 true——动态校验增量仅对数据类字段生效）
 * @returns {object} 引擎实例（register / execute / validate / 快照与查询接口）
 */
export function createRuleEngine(accessors = {}) {
  const opts = accessors || {}

  const readField = typeof opts.readField === 'function' ? opts.readField : () => undefined
  const writeField = typeof opts.writeField === 'function' ? opts.writeField : () => {}
  const readRowField =
    typeof opts.readRowField === 'function'
      ? opts.readRowField
      : (detailCode, rowIndex, code) => {
          const row = readRowObject(detailCode, rowIndex)
          return row ? row[code] : undefined
        }
  const writeRowField =
    typeof opts.writeRowField === 'function'
      ? opts.writeRowField
      : (detailCode, rowIndex, code, value) => {
          // 缺省行内写值：直接变更 readField 取得的行对象（宿主可注入专用行通道覆写——
          // 渲染器注入 emitData / change 外发桥，保证引擎联动值经既有数据通道外发）
          const row = readRowObject(detailCode, rowIndex)
          if (row) row[code] = value
        }
  const forEachRow =
    typeof opts.forEachRow === 'function'
      ? opts.forEachRow
      : (detailCode, callback) => {
          const rows = readField(detailCode)
          if (!Array.isArray(rows)) return
          rows.forEach((row, index) => {
            if (row && typeof row === 'object' && !Array.isArray(row)) callback(row, index)
          })
        }
  const isDataField = typeof opts.isDataField === 'function' ? opts.isDataField : () => true

  /* ==================== 规则索引（register 产物） ==================== */

  /** 全量规则条目（登记序）：{ rule, event, triggerField, scope: 'main'|'detail'|'row', hostField, detailCode } */
  let entries = []
  /** 主表层级字段元数据：字段码 → { field, type, label, props, container, parentField }（含明细表宿主） */
  let mainFieldMeta = new Map()
  /** 明细行层级字段元数据：明细表字段码 → Map<行内字段码, 元数据>（行上下文隔离） */
  let rowFieldMeta = new Map()
  /** 触发索引（主表/明细宿主）：`${event}#${triggerField}` → 条目数组 */
  let triggerIndex = new Map()
  /** 触发索引（明细行内）：`${event}#${detailCode}#${triggerField}` → 条目数组 */
  let rowTriggerIndex = new Map()
  /** 事件索引：event → 条目数组（onLoad 全量重放消费） */
  let byEventIndex = new Map()

  /* ==================== 动态状态（与后端 FormDynamicState 同语义） ==================== */

  /** 主表层级动态状态载体 { hidden, shown, required, validate }（evaluators.createDynamicStateCarrier） */
  let mainState = createDynamicStateCarrier()
  /** 明细行层级动态状态：明细表字段码 → 行号 → 行子载体（与主表载体同构，行上下文隔离） */
  let rowStates = new Map()

  /* ==================== 单次交互风暴运行时（§3.5 循环防护） ==================== */

  /** 当前风暴：{ active, executions, writes, broken, reentryWarned }；null=空闲 */
  let storm = null

  /** 动态校验正则编译缓存（pattern → RegExp|null；null=编译失败已告警，跳过该项校验） */
  let patternCache = new Map()

  /* ==================== 统一接口一：register（Schema 规则平铺收集 + 索引构建） ==================== */

  /**
   * 注册版本 Schema（§3.1：规则随触发字段存放于字段对象 rules[]）：平铺收集全部规则
   * （含容器 children / 明细行内——行内规则带行上下文归属标记），构建 triggerField+event
   * 索引；Schema 替换即整体重建（动态状态一并复位，不残留上一次交互终态）。
   *
   * @param {object|null} schema 版本 Schema 对象（null = 清空挂载——design 形态引擎不挂载，§4.1）
   * @returns {{ ruleCount: number }} 注册摘要
   */
  function register(schema) {
    entries = []
    mainFieldMeta = new Map()
    rowFieldMeta = new Map()
    triggerIndex = new Map()
    rowTriggerIndex = new Map()
    byEventIndex = new Map()
    rowStates = new Map()
    mainState = createDynamicStateCarrier()
    patternCache = new Map()

    const fields = schema && Array.isArray(schema.fields) ? schema.fields : []
    for (const node of flattenFields(fields)) {
      const meta = {
        field: node.field,
        type: node.type,
        label: node.label,
        props: node.props || {},
        container: node.container,
        parentField: node.parentField,
      }
      if (node.container === 'detailTable') {
        // 明细行内字段：行上下文归属标记（行内规则带 detailCode，只作用当前行，§3.1）
        const detailCode = node.parentField
        if (!rowFieldMeta.has(detailCode)) rowFieldMeta.set(detailCode, new Map())
        const scope = rowFieldMeta.get(detailCode)
        if (!scope.has(node.field)) scope.set(node.field, meta)
        addRules(node.rules, { scope: 'row', hostField: node.field, detailCode: node.parentField })
      } else {
        // 主表层级数据字段与明细表宿主：detailTable 宿主规则条件对行数组求值（§3.1）
        if (!mainFieldMeta.has(node.field)) mainFieldMeta.set(node.field, meta)
        const isDetailHost = node.type === 'detailTable'
        addRules(node.rules, {
          scope: isDetailHost ? 'detail' : 'main',
          hostField: node.field,
          detailCode: isDetailHost ? node.field : null,
        })
      }
    }
    if (entries.length > MAX_RULE_COUNT) {
      console.warn(
        `[FormRuleEngine] 规则总数 ${entries.length} 超过上限 ${MAX_RULE_COUNT}` +
          `（对齐后端 SysFormConstants.MAX_RULE_COUNT，功能设计 §2.2），仍按登记序执行（发布校验以服务端为准）`,
      )
    }
    return { ruleCount: entries.length }
  }

  /** 登记一个字段对象的规则清单（结构合法性由定义期结构校验器保证，此处防御性过滤） */
  function addRules(rules, context) {
    if (!Array.isArray(rules)) return
    for (const rule of rules) {
      if (!rule || typeof rule !== 'object') continue
      const event = rule.event
      if (typeof event !== 'string' || event === '') continue
      // 一期不开放焦点类规则配置（§3.4）：防御性跳过，不告警刷屏（事件仍透传物料组件）
      if (FOCUS_EVENTS.includes(event)) continue
      const triggerField =
        typeof rule.triggerField === 'string' && rule.triggerField !== '' ? rule.triggerField : context.hostField
      const entry = {
        rule,
        event,
        triggerField,
        scope: context.scope,
        hostField: context.hostField,
        detailCode: context.detailCode,
      }
      entries.push(entry)
      if (context.scope === 'row') {
        const key = `${event}#${context.detailCode}#${triggerField}`
        if (!rowTriggerIndex.has(key)) rowTriggerIndex.set(key, [])
        rowTriggerIndex.get(key).push(entry)
      } else {
        const key = `${event}#${triggerField}`
        if (!triggerIndex.has(key)) triggerIndex.set(key, [])
        triggerIndex.get(key).push(entry)
      }
      if (!byEventIndex.has(event)) byEventIndex.set(event, [])
      byEventIndex.get(event).push(entry)
    }
  }

  /* ==================== 统一接口二：execute（事件 → 匹配 → 求值 → 动作 → 级联） ==================== */

  /**
   * 执行事件（§3.5 事件链：字段值变化 → 事件总线 → 规则匹配 → 条件求值 → 动作执行 →
   * 状态更新 → 级联触发后续规则）。
   *
   * - event=onLoad：初始化重放——动态状态复位后，onLoad 规则单轮（初始化联动）+
   *   值驱动固定点迭代（REPLAY_EVENTS，仅载荷值变更驱动下一轮），重建显隐/覆盖终态
   *   （挂载 / Schema 替换 / 回显完成后的正确形态，对齐 §4.1 detail「显隐规则照常求值」）；
   * - 其余事件：事件驱动风暴——按 triggerField+event 索引匹配规则，动作引发的值变更
   *   级联触发目标字段 onChange 规则链（深度 +1，达上限熔断）。
   *
   * 三层循环防护（§3.5）在风暴内生效；熔断不抛错——console.warn + broken 状态位
   * （渲染器提示，不阻断已渲染表单），下次风暴开始复位。
   *
   * @param {string} event 渲染器事件（RENDERER_EVENTS 全集）
   * @param {object} [payload] 事件载荷：{ field?, value?, oldValue?, data?, row?, rowIndex?, detailCode? }
   * @returns {{ changed: boolean, broken: boolean, executions: number }} changed=本次风暴发生载荷值变更；
   *          broken=是否触发熔断；executions=动作执行次数
   */
  function execute(event, payload) {
    if (typeof event !== 'string' || event === '') {
      return { changed: false, broken: false, executions: 0 }
    }
    if (storm && storm.active) {
      // 重入防御兜底：引擎写值经宿主数据通道回写、不回派总线事件，正常不重入；
      // 订阅方在风暴内再派发事件时忽略并告警一次（防重入风暴，循环防护兜底）
      if (!storm.reentryWarned) {
        storm.reentryWarned = true
        console.warn(`[FormRuleEngine] 事件 ${event} 在规则风暴执行中重入，已忽略（防重入兜底，§3.5）`)
      }
      return { changed: false, broken: storm.broken, executions: storm.executions }
    }
    storm = { active: true, executions: 0, writes: 0, broken: false, reentryWarned: false }
    try {
      if (event === 'onLoad') {
        replayOnLoad()
      } else {
        runEventRules(event, payload || {}, 0)
      }
      return { changed: storm.writes > 0, broken: storm.broken, executions: storm.executions }
    } finally {
      storm.active = false
    }
  }

  /**
   * onLoad 初始化重放（§3.4 onLoad「表单初始化/回显完成后」+ §3.5 值驱动近似）：
   * ① 动态状态复位（对齐后端 replay 每次构建 FormDynamicState.empty()——按当前载荷
   *    重建终态，不残留上一次交互的显隐/覆盖状态）；
   * ② onLoad 规则单轮（初始化联动，§3.4 一期消费范围）；
   * ③ 值驱动固定点迭代（REPLAY_EVENTS）：条件只依赖字段值，仅载荷值变更驱动下一轮，
   *    级联轮次超 MAX_RULE_CHAIN_DEPTH 熔断（告警 + 状态位，前端预演不阻断；
   *    服务端复算同场景抛 CS_FORM_RULE_CYCLE_EXCEEDED 权威裁决）。
   */
  function replayOnLoad() {
    mainState = createDynamicStateCarrier()
    rowStates = new Map()
    runPass(['onLoad'], 0)
    let rounds = 0
    while (true) {
      const writesBefore = storm.writes
      runPass(REPLAY_EVENTS, rounds + 1)
      if (storm.writes === writesBefore) break
      rounds++
      if (rounds > MAX_RULE_CHAIN_DEPTH) {
        storm.broken = true
        console.warn(
          `[FormRuleEngine] 初始化重放级联轮次超过上限 ${MAX_RULE_CHAIN_DEPTH}，已熔断` +
            `（§3.5 深度熔断；对齐后端 CS_FORM_RULE_CYCLE_EXCEEDED 语义）`,
        )
        break
      }
    }
  }

  /**
   * 平铺求值一轮（Schema 登记序：主表规则 → 明细表宿主规则 → 明细行规则逐行，对齐后端
   * ReplaySession.runPass 层级序）。重放轮次内写值不即时级联——由固定点下一轮统一驱动
   * （与后端同构，避免双重求值）；事件驱动路径（cascade=true）写值即时级联。
   */
  function runPass(events, depth) {
    for (const entry of entries) {
      if (storm.broken) return
      if (!events.includes(entry.event)) continue
      applyEntry(entry, { depth, cascade: false, rowIndex: null })
    }
  }

  /**
   * 事件驱动求值：按 triggerField+event 索引匹配规则（triggerField=payload.field）。
   * 行上下文载荷（payload 含 row/rowIndex，FE-007 明细行事件接线）只匹配行内规则且
   * 只求值当前行；无行上下文匹配主表/明细宿主规则。
   */
  function runEventRules(event, payload, depth) {
    if (storm.broken) return
    if (depth > MAX_RULE_CHAIN_DEPTH) {
      storm.broken = true
      console.warn(
        `[FormRuleEngine] 规则链级联深度超过上限 ${MAX_RULE_CHAIN_DEPTH}，已熔断` +
          `（§3.5 深度熔断；对齐后端 CS_FORM_RULE_CYCLE_EXCEEDED 语义）`,
      )
      return
    }
    const field = payload && typeof payload.field === 'string' && payload.field !== '' ? payload.field : null
    if (!field) return
    const hasRowContext =
      payload && payload.detailCode && (payload.rowIndex !== undefined || payload.row !== undefined)
    if (hasRowContext) {
      // 明细行事件：行内规则只求值当前行（行上下文隔离，§3.1）
      for (const entry of rowTriggerIndex.get(`${event}#${payload.detailCode}#${field}`) || []) {
        if (storm.broken) return
        if (entry.event !== event) continue
        applyRowScope(entry, payload.detailCode, payload.rowIndex, depth, true)
      }
      return
    }
    for (const entry of triggerIndex.get(`${event}#${field}`) || []) {
      if (storm.broken) return
      if (entry.event !== event) continue
      // 条件对触发字段当前值求值（明细宿主触发字段值=行数组，§3.1）
      const value = readField(entry.triggerField)
      if (!conditionMatches(entry.rule.condition, value)) continue
      executeActions(entry, {
        depth,
        cascade: true,
        rowIndex: null,
        detailCode: entry.scope === 'detail' ? entry.detailCode : null,
      })
    }
  }

  /**
   * 应用单条规则条目（条件求值 → 动作执行）：
   * - scope=main：条件对主表触发字段当前值求值，动作作用于主表层级；
   * - scope=detail（明细表宿主规则，无行上下文）：条件对行数组求值、动作按行上下文
   *   作用于全部行（对齐服务端 F15-BE-004 applyDetailRule 语义，§3.1）；
   * - scope=row（明细行内规则）：条件逐行对当前行字段值求值、动作只作用于当前行
   *   （行上下文隔离，行内规则不影响其他行，§3.1/FE-007）；context.rowIndex 指明
   *   行事件行号，null 时逐行求值（初始化重放）。
   */
  function applyEntry(entry, context) {
    if (entry.scope === 'row') {
      const detailCode = context.detailCode || entry.detailCode
      if (!detailCode) return
      if (context.rowIndex !== null && context.rowIndex !== undefined) {
        applyRowScope(entry, detailCode, context.rowIndex, context.depth, context.cascade)
      } else {
        forEachRow(detailCode, (row, rowIndex) => {
          if (storm.broken) return
          applyRowScope(entry, detailCode, rowIndex, context.depth, context.cascade)
        })
      }
      return
    }
    const value = readField(entry.triggerField)
    if (!conditionMatches(entry.rule.condition, value)) return
    executeActions(entry, {
      depth: context.depth,
      cascade: context.cascade,
      rowIndex: null,
      detailCode: entry.scope === 'detail' ? entry.detailCode : null,
    })
  }

  /** 行内规则单行求值：条件对当前行字段值求值，动作只作用于本行 */
  function applyRowScope(entry, detailCode, rowIndex, depth, cascade) {
    const value = readRowField(detailCode, rowIndex, entry.triggerField)
    if (!conditionMatches(entry.rule.condition, value)) return
    executeActions(entry, { depth, cascade, detailCode, rowIndex })
  }

  /**
   * 按序执行规则动作数组：计数熔断（§3.5①）逐动作检查；onSubmit/onValidate 事件上的
   * setValue/clearValue 遇配置跳过并告警（§3.4 提交段禁令：仅支持 setRequired/setValidate/
   * show/hide，防提交竞态）。
   */
  function executeActions(entry, ctx) {
    const actions = Array.isArray(entry.rule.actions) ? entry.rule.actions : []
    const valueForbidden = VALUE_FORBIDDEN_EVENTS.includes(entry.event)
    for (const action of actions) {
      if (storm.broken) return
      if (!action || typeof action !== 'object') continue
      if (valueForbidden && (action.type === 'setValue' || action.type === 'clearValue')) {
        console.warn(
          `[FormRuleEngine] 事件 ${entry.event} 禁止配置 ${action.type} 动作，已跳过` +
            `（§3.4 提交段载荷约束；规则 ${entry.rule.ruleId || '未命名'}）`,
        )
        continue
      }
      if (!beginAction()) return
      dispatchAction(entry, action, ctx)
    }
  }

  /** 动作执行计数与次数熔断（§3.5①：计数达上限即熔断本次风暴，剩余规则跳过） */
  function beginAction() {
    if (storm.executions >= MAX_RULE_EXECUTIONS_PER_CYCLE) {
      storm.broken = true
      console.warn(
        `[FormRuleEngine] 单次交互动作执行次数达上限 ${MAX_RULE_EXECUTIONS_PER_CYCLE}，剩余规则已跳过` +
          `（§3.5 次数熔断；对齐后端 SysFormConstants.MAX_RULE_EXECUTIONS_PER_CYCLE）`,
      )
      return false
    }
    storm.executions++
    return true
  }

  /**
   * 动作分发（六类动作经 evaluators.applyRuleAction 应用到对应层级的动态状态载体）：
   * - 主表动作：${字段码} 引用只解析主表层级（对齐后端 resolveReference(value, null)）；
   * - 行内动作（scope=row / scope=detail 全行）：引用行上下文优先、回落主表层级
   *   （对齐后端 resolveReference(value, rowMap)），写值经行内数据通道。
   */
  function dispatchAction(entry, action, ctx) {
    const ruleId = entry.rule.ruleId ?? null
    if (entry.scope === 'row') {
      const carrier = rowStateOf(ctx.detailCode, ctx.rowIndex)
      applyRuleAction(action, carrier, {
        ruleId,
        getFieldValue: (code) => resolveContextValue(ctx.detailCode, ctx.rowIndex, code),
        writeValue: (target, resolved) => writeRowValue(ctx.detailCode, ctx.rowIndex, target, resolved, ctx),
      })
      return
    }
    if (entry.scope === 'detail') {
      // 明细表宿主规则：动作作用于全部行（宿主口径，对齐服务端 applyDetailRule；§3.1）
      forEachRow(entry.detailCode, (row, rowIndex) => {
        if (storm.broken) return
        const carrier = rowStateOf(entry.detailCode, rowIndex)
        applyRuleAction(action, carrier, {
          ruleId,
          getFieldValue: (code) => resolveContextValue(entry.detailCode, rowIndex, code),
          writeValue: (target, resolved) => writeRowValue(entry.detailCode, rowIndex, target, resolved, ctx),
        })
      })
      return
    }
    applyRuleAction(action, mainState, {
      ruleId,
      getFieldValue: (code) => readField(code),
      writeValue: (target, resolved) => writeMainValue(target, resolved, ctx),
    })
  }

  /* ==================== 值写入与级联（幂等短路 §3.5③） ==================== */

  /** 主表层级写值：目标值与当前值相等不回写不级联（幂等短路）；null/undefined=「无值」形态 */
  function writeMainValue(target, resolved, ctx) {
    const current = readField(target)
    if (resolved === null || resolved === undefined) {
      if (current === null || current === undefined) return false
      writeField(target, null)
      storm.writes++
      cascadeWrite(target, null, current, ctx)
      return true
    }
    if (valueEquals(current, resolved)) return false
    writeField(target, resolved)
    storm.writes++
    cascadeWrite(target, resolved, current, ctx)
    return true
  }

  /** 明细行内写值（行上下文隔离；行不存在防御性跳过——已删除/越界行不写） */
  function writeRowValue(detailCode, rowIndex, target, resolved, ctx) {
    if (!readRowObject(detailCode, rowIndex)) return false
    const current = readRowField(detailCode, rowIndex, target)
    if (resolved === null || resolved === undefined) {
      if (current === null || current === undefined) return false
      writeRowField(detailCode, rowIndex, target, null)
      storm.writes++
      cascadeRowWrite(detailCode, rowIndex, target, null, current, ctx)
      return true
    }
    if (valueEquals(current, resolved)) return false
    writeRowField(detailCode, rowIndex, target, resolved)
    storm.writes++
    cascadeRowWrite(detailCode, rowIndex, target, resolved, current, ctx)
    return true
  }

  /** 事件驱动路径：值变更即时级联目标字段 onChange 规则链（深度 +1，达上限熔断 §3.5②） */
  function cascadeWrite(target, value, oldValue, ctx) {
    if (!ctx.cascade) return
    runEventRules('onChange', { field: target, value, oldValue }, ctx.depth + 1)
  }

  /** 行内值变更级联：行上下文（detailCode/rowIndex/row）随载荷注入，只触发行内规则链 */
  function cascadeRowWrite(detailCode, rowIndex, target, value, oldValue, ctx) {
    if (!ctx.cascade) return
    runEventRules(
      'onChange',
      { field: target, value, oldValue, detailCode, rowIndex, row: readRowObject(detailCode, rowIndex) },
      ctx.depth + 1,
    )
  }

  /* ==================== 工具（行对象 / 行状态 / 引用解析） ==================== */

  /** 读明细行对象（非对象行防御为 null——行结构违规由基础校验承载） */
  function readRowObject(detailCode, rowIndex) {
    const rows = readField(detailCode)
    const row = Array.isArray(rows) ? rows[rowIndex] : null
    return row && typeof row === 'object' && !Array.isArray(row) ? row : null
  }

  /** 行子载体懒创建（行上下文隔离：hidden/shown/required/validate 均按行存放） */
  function rowStateOf(detailCode, rowIndex) {
    let byRow = rowStates.get(detailCode)
    if (!byRow) {
      byRow = new Map()
      rowStates.set(detailCode, byRow)
    }
    let carrier = byRow.get(rowIndex)
    if (!carrier) {
      carrier = createDynamicStateCarrier()
      byRow.set(rowIndex, carrier)
    }
    return carrier
  }

  /** ${字段码} 引用解析取值：行上下文优先、回落主表层级（对齐后端 resolveReference 口径） */
  function resolveContextValue(detailCode, rowIndex, code) {
    if (detailCode !== null && detailCode !== undefined) {
      const row = readRowObject(detailCode, rowIndex)
      if (row && code in row) return row[code]
    }
    return readField(code)
  }

  /* ==================== 统一接口三：validate（动态校验增量，ruleId 溯源） ==================== */

  /**
   * 动态校验增量（§3.1 动态校验语义 + §3.3 setValidate 覆盖）：基于当前累积的动态状态
   * （交互风暴/初始化重放的终态）对载荷求值，输出规则侧错误增量——静态基线
   * （props.required/物料校验器）由渲染器 validateFormData 承载，合并去重在渲染器层完成。
   *
   * 口径（对齐后端 FormDataBasicValidator 合并裁决）：
   * - setRequired 覆盖终态 value=true 且值为空 → 必填错误（ruleId 溯源触发规则）；
   *   value=false 为明确豁免（覆盖静态基线，无错误）；
   * - 被规则隐藏（终态 hidden ∪ 静态 props.hidden）的主表字段不参与必填校验（§3.6——
   *   值将被提交裁剪，要求必填会阻断合法提交；仅主表层级豁免）；
   * - setValidate 条件范围（min/max 数值比较）/条件正则（pattern 全串匹配）并入，
   *   错误文案优先 validate.message、缺省内置默认文案，ruleId 取被违反键的触发规则
   *   （对齐后端 validateDynamicOverride；正则与后端 String.matches 同为全串匹配）；
   * - 明细行增量：行内 setRequired/setValidate 按行上下文逐行求值、只作用于本行
   *   （行内必填不因行内显隐豁免——一期行内显隐为渲染层语义、行内校验全量执行，§3.6 一期边界）；
   * - 非数据类字段（isDataField=false，如内容/布局物料）的覆盖防御性跳过（结构校验器
   *   已限定动作 target 为数据类字段）。
   *
   * @param {object|null} data 当前载荷数据对象（主表键 + 明细行数组键）
   * @returns {Array<{field: string, ruleId: string|null, message: string}>} 错误增量数组
   */
  function validate(data) {
    const source = data && typeof data === 'object' && !Array.isArray(data) ? data : {}
    const errors = []
    const hiddenUnion = collectHiddenUnion()
    // ① 主表动态必填增量（隐藏豁免：终态 hidden ∪ 静态 hidden，§3.6）
    for (const [code, override] of mainState.required) {
      if (!override || override.value !== true) continue
      const meta = mainFieldMeta.get(code)
      if (!meta || !isDataField(meta.type, meta.props)) continue
      if (hiddenUnion.has(code)) continue
      if (isEmptyConditionValue(source[code])) {
        errors.push({ field: code, ruleId: override.ruleId ?? null, message: `${meta.label || code}为必填项` })
      }
    }
    // ② 主表动态校验覆盖增量（setValidate 条件范围/条件正则）
    for (const [code, entry] of mainState.validate) {
      const meta = mainFieldMeta.get(code)
      if (!meta || !isDataField(meta.type, meta.props)) continue
      for (const failure of dynamicValidateFailures(entry, source[code])) {
        errors.push({ field: code, ruleId: failure.ruleId, message: `${meta.label || code}${failure.message}` })
      }
    }
    // ③ 明细行增量（行上下文隔离：行内必填/校验覆盖只作用本行；错误文案带「第N行：」定位，
    //    与渲染器 validateFormData 行校验前缀格式一致）
    for (const [detailCode, byRow] of rowStates) {
      const rowScope = rowFieldMeta.get(detailCode)
      if (!rowScope) continue
      const rows = Array.isArray(source[detailCode]) ? source[detailCode] : []
      for (const [rowIndex, carrier] of byRow) {
        const row = rows[rowIndex]
        const rowData = row && typeof row === 'object' && !Array.isArray(row) ? row : {}
        for (const [code, override] of carrier.required) {
          if (!override || override.value !== true) continue
          const meta = rowScope.get(code)
          if (!meta || !isDataField(meta.type, meta.props)) continue
          if (isEmptyConditionValue(rowData[code])) {
            errors.push({
              field: code,
              ruleId: override.ruleId ?? null,
              message: `第${rowIndex + 1}行：${meta.label || code}为必填项`,
            })
          }
        }
        for (const [code, entry] of carrier.validate) {
          const meta = rowScope.get(code)
          if (!meta || !isDataField(meta.type, meta.props)) continue
          for (const failure of dynamicValidateFailures(entry, rowData[code])) {
            errors.push({
              field: code,
              ruleId: failure.ruleId,
              message: `第${rowIndex + 1}行：${meta.label || code}${failure.message}`,
            })
          }
        }
      }
    }
    return errors
  }

  /** setValidate 覆盖项检查（min/max 数值比较、pattern 全串匹配；文案优先 validate.message） */
  function dynamicValidateFailures(entry, value) {
    const failures = []
    if (entry.min !== undefined && entry.min !== null && typeof value === 'number' && value < entry.min) {
      failures.push({ ruleId: entry.minRuleId ?? null, message: entry.message || `不能小于 ${entry.min}` })
    }
    if (entry.max !== undefined && entry.max !== null && typeof value === 'number' && value > entry.max) {
      failures.push({ ruleId: entry.maxRuleId ?? null, message: entry.message || `不能大于 ${entry.max}` })
    }
    if (entry.pattern && typeof value === 'string' && !matchesPattern(entry.pattern, value)) {
      failures.push({ ruleId: entry.patternRuleId ?? null, message: entry.message || '格式不正确' })
    }
    return failures
  }

  /** 条件正则全串匹配（与后端 String.matches 同语义；非法正则告警一次并跳过该项校验） */
  function matchesPattern(pattern, text) {
    let matcher = patternCache.get(pattern)
    if (matcher === undefined) {
      try {
        matcher = new RegExp(`^(?:${pattern})$`)
      } catch (error) {
        matcher = null
        console.warn(`[FormRuleEngine] 动态校验正则表达式非法，已跳过该项校验：${pattern}`)
      }
      patternCache.set(pattern, matcher)
    }
    return matcher ? matcher.test(text) : true
  }

  /** 隐藏联合集（终态 hidden ∪ 静态 props.hidden，主表层级；必填豁免共用） */
  function collectHiddenUnion() {
    const union = new Set(mainState.hidden)
    for (const [code, meta] of mainFieldMeta) {
      if (meta.props && meta.props.hidden === true) union.add(code)
    }
    return union
  }

  /* ==================== 动态状态快照与查询（渲染器状态桥消费） ==================== */

  /** 规则显隐终态快照：Map<字段码, true|false>（shown=true / hidden=false；无覆盖键=回落静态态） */
  function snapshotVisibility() {
    const snapshot = new Map()
    for (const code of mainState.shown) snapshot.set(code, true)
    for (const code of mainState.hidden) snapshot.set(code, false)
    return snapshot
  }

  /** 规则隐藏字段码快照（Set 副本；validate 基线折算隐藏豁免消费） */
  function snapshotHiddenCodes() {
    return new Set(mainState.hidden)
  }

  /** 主表动态必填覆盖快照：Map<字段码, { value, ruleId }>（浅拷贝） */
  function snapshotRequired() {
    return new Map(mainState.required)
  }

  /** 主表动态校验覆盖快照：Map<字段码, 覆盖项>（浅拷贝） */
  function snapshotValidate() {
    return new Map(mainState.validate)
  }

  /** 单字段查询：规则显隐终态（true=规则显示 / false=规则隐藏 / undefined=无覆盖） */
  function getVisibilityOverride(code) {
    if (mainState.shown.has(code)) return true
    if (mainState.hidden.has(code)) return false
    return undefined
  }

  /** 单字段查询：是否被规则隐藏（hide 终态；不含静态 hidden——静态初值由渲染器承载） */
  function isHidden(code) {
    return mainState.hidden.has(code)
  }

  /** 单字段查询：主表动态必填覆盖（undefined=无覆盖，回落静态基线） */
  function getRequiredOverride(code) {
    return mainState.required.get(code)
  }

  /** 单字段查询：主表动态校验覆盖（undefined=无覆盖） */
  function getValidateOverride(code) {
    return mainState.validate.get(code)
  }

  /** 行内查询：行显隐终态（true/false/undefined；FE-007 明细表行内渲染消费） */
  function getRowVisibilityOverride(detailCode, rowIndex, code) {
    const carrier = rowStates.get(detailCode)?.get(rowIndex)
    if (!carrier) return undefined
    if (carrier.shown.has(code)) return true
    if (carrier.hidden.has(code)) return false
    return undefined
  }

  /** 行内查询：行动态必填覆盖（undefined=无覆盖，回落静态基线） */
  function getRowRequiredOverride(detailCode, rowIndex, code) {
    return rowStates.get(detailCode)?.get(rowIndex)?.required.get(code)
  }

  /** 上次风暴是否触发熔断（渲染器状态位提示消费；下次风暴开始复位） */
  function isBroken() {
    return storm ? storm.broken : false
  }

  /** 已登记规则总数（MAX_RULE_COUNT 上限告警口径） */
  function getRuleCount() {
    return entries.length
  }

  return {
    register,
    execute,
    validate,
    snapshotVisibility,
    snapshotHiddenCodes,
    snapshotRequired,
    snapshotValidate,
    getVisibilityOverride,
    isHidden,
    getRequiredOverride,
    getValidateOverride,
    getRowVisibilityOverride,
    getRowRequiredOverride,
    isBroken,
    getRuleCount,
  }
}

/* ==================== 渲染器校验基线折算（静态通道 × 动态覆盖合并） ==================== */

/**
 * 构建静态校验基线字段树（渲染器 validate() 消费）：返回新树（不改写 Schema），
 * 对主表数据类字段的 props.required 折算动态覆盖与隐藏豁免——
 * - 被规则隐藏（ruleHiddenCodes）或静态隐藏（props.hidden===true）→ required=false
 *   （§3.6：隐藏字段值将被提交裁剪，不参与必填校验；含静态隐藏——对齐后端
 *   initialVisibility→hiddenMain→required=false 口径）；
 * - setRequired 覆盖存在（无论 value 真假）→ required=false（动态必填错误由引擎
 *   validate 增量承载并带 ruleId 溯源，避免静态通道以 ruleId=null 重复报错）；
 * - 其余字段保持静态基线；明细表宿主与其行内字段不折算（宿主必填=rowMin 行数语义、
 *   行内校验全量执行，§3.6 一期边界）。
 *
 * @param {Array} fields Schema 字段对象数组（原树）
 * @param {Map<string, {value: boolean, ruleId: string|null}>|null} requiredOverrides 引擎动态必填覆盖快照
 * @param {Set<string>|null} ruleHiddenCodes 引擎规则隐藏字段码快照
 * @returns {Array} 折算后的新字段树（未命中的节点保持原引用）
 */
export function buildRequiredBaselineFields(fields, requiredOverrides, ruleHiddenCodes) {
  if (!Array.isArray(fields)) return fields
  const overrides = requiredOverrides && typeof requiredOverrides.get === 'function' ? requiredOverrides : null
  const hiddenCodes = ruleHiddenCodes && typeof ruleHiddenCodes.has === 'function' ? ruleHiddenCodes : null

  function transform(list) {
    return list.map((node) => {
      if (!node || typeof node !== 'object' || !node.field || !node.type) return node
      if (node.type === 'group' && Array.isArray(node.children)) {
        return { ...node, children: transform(node.children) }
      }
      if (node.type === 'grid' && Array.isArray(node.children)) {
        return {
          ...node,
          children: node.children.map((column) =>
            column && Array.isArray(column.fields) ? { ...column, fields: transform(column.fields) } : column,
          ),
        }
      }
      if (node.type === 'detailTable') {
        // 明细表宿主不折算（宿主必填=rowMin 行数语义）；行内字段不折算（行内校验全量执行）
        return node
      }
      const props = node.props && typeof node.props === 'object' ? node.props : {}
      const staticHidden = props.hidden === true
      const ruleHidden = hiddenCodes ? hiddenCodes.has(node.field) : false
      const overridden = overrides ? overrides.has(node.field) : false
      if (staticHidden || ruleHidden || overridden) {
        return { ...node, props: { ...props, required: false } }
      }
      return node
    })
  }
  return transform(fields)
}

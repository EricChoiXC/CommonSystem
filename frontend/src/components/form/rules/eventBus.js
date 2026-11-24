/**
 * 自定义表单规则引擎——渲染器事件总线订阅入口（F15-FE-006）
 *
 * 口径依据：docs/v1_5-自定义表单/自定义表单功能详细设计.md
 * - §4.3 第 5 条事件统一分发：字段组件只 emit 原始事件，统一进入渲染器内置事件总线
 *   （F15-FE-005 createRendererEventBus），规则引擎订阅同一事件流完成规则匹配与动作
 *   执行——本文件即「订阅同一事件流」的绑定点，不另起第二套总线（复用 FE-005 总线
 *   on/off/dispatch 语义，必要时经此薄封装转出口径）；
 * - §3.4 一期消费范围：onLoad（初始化联动，引擎做值驱动全量重放重建显隐/覆盖终态）/
 *   onChange（主力事件）/ onAdd / onDelete（明细行生命周期，行上下文注入，FE-007 明细表
 *   接线后生效）/ onSubmit / onValidate（提交段，setValue/clearValue 禁配由引擎跳过并
 *   告警）；onFocus / onBlur 仅透传给物料组件、声明式规则一期不开放配置——收到即忽略
 *   且不告警刷屏（显式订阅空处理器固化口径）。
 *
 * 【纯 JS 约束】零 import：总线与引擎实例均由调用方注入（渲染器装配），可被 node
 * 冒烟脚本直接加载测试。
 */

/**
 * 绑定规则引擎到渲染器事件总线（F15-FE-006 挂载入口）。
 *
 * @param {object} bus 渲染器事件总线（rendererSupport.createRendererEventBus 产物，
 *        on/off/dispatch 接口）
 * @param {object} engine 规则引擎（rules/ruleEngine.js createRuleEngine 产物，execute 接口）
 * @param {object} [options]
 * @param {(event: string, payload: object, result: {changed: boolean, broken: boolean, executions: number}) => void} [options.onAfterExecute]
 *        风暴结束后回调：引擎动态状态为普通 JS 载体，渲染器据此自增动态状态版本号，
 *        驱动显隐/必填/熔断提示 computed 重算（有动作执行或熔断时回调）
 * @returns {() => void} 解绑函数（逐事件 bus.off；渲染器随组件销毁自然失效，常规无需调用）
 */
export function bindRuleEngine(bus, engine, options = {}) {
  if (!bus || typeof bus.on !== 'function' || typeof bus.off !== 'function') {
    throw new Error('[FormRuleEngine] bindRuleEngine 需要渲染器事件总线（createRendererEventBus 产物）')
  }
  if (!engine || typeof engine.execute !== 'function') {
    throw new Error('[FormRuleEngine] bindRuleEngine 需要规则引擎（createRuleEngine 产物）')
  }
  const onAfterExecute = typeof options.onAfterExecute === 'function' ? options.onAfterExecute : null

  const bound = []

  /**
   * 订阅一个事件到引擎：execute 同步执行完整风暴（含级联），引擎写值经注入访问器
   * 走渲染器数据通道（update:modelValue 全量外发 + change 明细，source='rule' 来源
   * 标记）；风暴结束后按需通知渲染器回写渲染决策。
   */
  function subscribe(event) {
    const handler = (payload) => {
      const result = engine.execute(event, payload)
      if (onAfterExecute && (result.executions > 0 || result.broken)) {
        onAfterExecute(event, payload, result)
      }
    }
    bus.on(event, handler)
    bound.push([event, handler])
  }

  // 一期消费范围事件（§3.4）：onLoad 初始化联动 / onChange 主力 / onSubmit·onValidate
  // 提交段（值类动作禁配跳过）/ onAdd·onDelete 明细行生命周期（FE-007 接线后消费——
  // 行上下文载荷 { detailCode, row, rowIndex } 注入引擎，行内规则只作用当前行）
  for (const event of ['onLoad', 'onChange', 'onSubmit', 'onValidate', 'onAdd', 'onDelete']) {
    subscribe(event)
  }
  // onFocus / onBlur：声明式规则一期不开放焦点类配置（§3.4，防规则风暴）——收到即忽略、
  // 不进引擎、不告警刷屏；显式订阅空处理器固化「仅透传物料组件」口径，二期开放焦点类
  // 规则时仅改此处。
  for (const event of ['onFocus', 'onBlur']) {
    const handler = () => {}
    bus.on(event, handler)
    bound.push([event, handler])
  }

  return function unbindRuleEngine() {
    for (const [event, handler] of bound) {
      bus.off(event, handler)
    }
    bound.length = 0
  }
}

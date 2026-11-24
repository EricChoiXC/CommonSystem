/**
 * FormRenderer 渲染器纯逻辑辅助模块（F15-FE-005）
 *
 * 口径依据：docs/v1_5-自定义表单/自定义表单功能详细设计.md
 * - §2.1 Schema JSON 结构与字段对象（field/props/children）；
 * - §3.6 隐藏字段值保留策略（ADR-08）与一期边界（明细行内显隐为渲染层语义）；
 * - §4.2 props / v-model / 事件契约、§4.3 渲染行为定稿。
 *
 * 【纯 JS 约束】本文件零 import（连 Vue 也不引），可被 node 冒烟脚本直接加载；
 * 物料读取一律经调用方注入的 getMaterial(type) 访问器（FormRenderer 传入
 * registry.js 的 getComponentMaterial），物料注册入口（materials/index.js）由
 * 组件文件负责 import，本文件不感知。
 *
 * 【渲染器零业务规则】本模块只承载 Schema 解释类通用逻辑（扁平化 / 权限合并 /
 * 默认值 / 校验聚合 / 载荷裁剪 / availableVars 派生 / 事件总线），不含任何具体
 * 业务规则与规则引擎求值（架构原则 2：显隐/联动/校验逻辑全部归 F15-FE-006）。
 */

/* ==================== 渲染器形态与事件（§4.1 / §4.2 / §3.4） ==================== */

/** 渲染器三形态取值域（§4.1：design / edit / detail，mode 非法时回落 edit） */
export const RENDERER_MODES = ['design', 'edit', 'detail']

/**
 * 渲染器内置事件清单（§3.4 事件全集 8 个，§4.3 事件统一分发）。
 * 规则引擎（F15-FE-006）经 createRendererEventBus 订阅同一事件流；
 * 本任务（FE-005）实际派发 onLoad / onChange / onFocus / onBlur / onValidate，
 * onAdd / onDelete 由明细表 widget（F15-FE-007）经总线派发，onSubmit 由宿主
 * 提交编排时派发——四者为语义占位，渲染器不自行触发（§4.3 第 5 条）。
 */
export const RENDERER_EVENTS = ['onLoad', 'onChange', 'onFocus', 'onBlur', 'onAdd', 'onDelete', 'onSubmit', 'onValidate']

/**
 * 创建渲染器内置轻量事件总线（§4.3 第 5 条「轻量内部事件派发点」）。
 * 为 F15-FE-006 规则引擎预留同接口挂载点：引擎持 on(event, handler) 订阅，
 * 渲染器在行为节点 dispatch；订阅方异常只告警不中断渲染器（不阻断整表渲染，
 * 对齐 ADR-10 的渲染期容错口径）。
 */
export function createRendererEventBus() {
  const listeners = new Map()
  function assertEvent(event) {
    if (!RENDERER_EVENTS.includes(event)) {
      throw new Error(`[FormRenderer] 未知的渲染器事件：${event}（事件全集见 RENDERER_EVENTS）`)
    }
  }
  return {
    /** 订阅事件（F15-FE-006 规则引擎挂载入口） */
    on(event, handler) {
      assertEvent(event)
      if (typeof handler !== 'function') return
      if (!listeners.has(event)) listeners.set(event, new Set())
      listeners.get(event).add(handler)
    },
    /** 取消订阅 */
    off(event, handler) {
      assertEvent(event)
      const set = listeners.get(event)
      if (set) set.delete(handler)
    },
    /** 派发事件（渲染器行为节点调用；订阅方异常吞掉并告警，不阻断渲染） */
    dispatch(event, payload) {
      assertEvent(event)
      const set = listeners.get(event)
      if (!set) return
      for (const handler of set) {
        try {
          handler(payload)
        } catch (error) {
          console.error(`[FormRenderer] 事件 ${event} 订阅方执行异常（已吞掉，不阻断渲染）`, error)
        }
      }
    },
  }
}

/* ==================== Schema 字段树扁平化（§2.1 字段对象结构） ==================== */

/**
 * 将 Schema 字段树扁平化为渲染/校验共用的节点清单（先序遍历，保持 Schema 声明序）。
 *
 * - 节点：{ field, type, label, props, parentField, depth, container, children }；
 *   parentField = 宿主容器字段码（顶层 null，明细行内字段为其明细表字段码）；
 *   depth 顶层 = 1；container = null | 'group' | 'grid' | 'detailTable'；
 * - grid 的 children 为列对象数组 { span, fields[] }（§2.1），列本身不是字段节点，
 *   仅递归其 fields；
 * - 递归深度由后端 Schema 校验器（MAX_NEST_DEPTH）前置保证，此处不重复设限（§4.3）；
 * - 脏项（非对象 / 缺 field / 缺 type）防御性跳过，不抛异常。
 *
 * @param {Array} fields Schema 字段对象数组
 * @param {string|null} parentField 宿主容器字段码（递归内部参数）
 * @param {number} depth 当前深度（递归内部参数）
 * @param {string|null} container 宿主容器类型（递归内部参数）
 * @param {Array} out 输出数组（递归内部参数）
 * @returns {Array} 扁平字段节点数组
 */
export function flattenFields(fields, parentField = null, depth = 1, container = null, out = []) {
  const list = Array.isArray(fields) ? fields : []
  for (const raw of list) {
    if (!raw || typeof raw !== 'object' || !raw.field || !raw.type) continue
    out.push({
      field: raw.field,
      type: raw.type,
      label: raw.label,
      props: raw.props && typeof raw.props === 'object' ? raw.props : {},
      parentField,
      depth,
      container,
      children: raw.children,
    })
    if (raw.type === 'group' && Array.isArray(raw.children)) {
      // 分组容器：children 为字段对象数组（§2.1）
      flattenFields(raw.children, raw.field, depth + 1, 'group', out)
    } else if (raw.type === 'grid' && Array.isArray(raw.children)) {
      // 栅格容器：children 为列对象数组 { span, fields[] }（§2.1），列不是字段节点
      for (const column of raw.children) {
        const columnFields = column && Array.isArray(column.fields) ? column.fields : []
        flattenFields(columnFields, raw.field, depth + 1, 'grid', out)
      }
    } else if (raw.type === 'detailTable' && Array.isArray(raw.children)) {
      // 明细表（R04 一层）：children 为行内字段对象数组；行内显隐一期为渲染层语义（§3.6 一期边界）
      flattenFields(raw.children, raw.field, depth + 1, 'detailTable', out)
    }
  }
  return out
}

/** 容器类 type（渲染器壳容器 group/grid 由渲染器自身渲染外壳，§4.3 第 2 条） */
export const LAYOUT_CONTAINER_TYPES = ['group', 'grid']

/** 是否布局容器 type（group/grid；detailTable 为 widget，不在其列） */
export function isLayoutContainerType(type) {
  return LAYOUT_CONTAINER_TYPES.includes(type)
}

/** 解析物料 dataType（支持 string / (props)=>string / null 三形态，F15-FE-002 注册协议） */
export function resolveDataType(material, props) {
  if (!material) return null
  const dataType = material.dataType
  if (typeof dataType === 'function') return dataType(props || {})
  return dataType === undefined ? null : dataType
}

/* ==================== 字段级权限合并（§4.2 fieldPermissions + §2.1 props.permission） ==================== */

/** 字段级权限取值域（§2.1 permission 元数据） */
export const FIELD_PERMISSION_VALUES = ['editable', 'readonly', 'hidden']

/**
 * 解析字段生效权限：外部覆盖映射（props.fieldPermissions）优先于 Schema 内
 * props.permission（§4.2「合并时外部覆盖优先」），二者皆无/非法回落 editable。
 *
 * @param {object} props 字段 props（§2.1）
 * @param {string} fieldCode 字段码
 * @param {object|null} fieldPermissions 外部覆盖映射 { 字段码: editable/readonly/hidden }
 * @returns {'editable'|'readonly'|'hidden'} 生效权限
 */
export function resolveFieldPermission(props, fieldCode, fieldPermissions) {
  if (fieldPermissions && typeof fieldPermissions === 'object') {
    const external = fieldPermissions[fieldCode]
    if (FIELD_PERMISSION_VALUES.includes(external)) return external
  }
  const schemaPermission = props ? props.permission : null
  if (FIELD_PERMISSION_VALUES.includes(schemaPermission)) return schemaPermission
  return 'editable'
}

/* ==================== 控件属性透传净化（§4.3 第 1 条） ==================== */

/**
 * 管理键清单：渲染器层承载的固定属性项 / 权限元数据，透传控件前剔除——
 * widget 只渲染控件本体，不感知 label/field/required/tooltip/defaultValue/
 * permission/hidden 等管理键（F15-FE-003/004 widget 统一契约头注同口径）。
 */
const MANAGEMENT_KEYS = ['label', 'field', 'required', 'tooltip', 'defaultValue', 'permission', 'hidden']

/**
 * 由字段 props 提炼控件透传属性（剔除管理键，其余扩展属性项原样透传）。
 *
 * @param {object} props 字段 props
 * @returns {object} 控件 props（不含 v-model 值与控制位，二者由渲染器单独注入）
 */
export function pickWidgetProps(props) {
  const result = {}
  if (!props || typeof props !== 'object') return result
  for (const key of Object.keys(props)) {
    if (!MANAGEMENT_KEYS.includes(key)) result[key] = props[key]
  }
  return result
}

/* ==================== 默认值初始化（§4.3 第 3 条） ==================== */

/**
 * 计算单个字段默认值：props.defaultValue 配置优先（留空/空串视为未配置），
 * 兜底执行物料 defaultValueGenerator（FE-002 签名 (props)=>value，dataType 为
 * 函数的物料由生成器/校验器自行按 props 求值）；生成器产出的组件值空间（如
 * addressbook 双绑定态 {fieldId,fieldName}）经物料 serializer 归一为载荷值空间，
 * 保证数据对象恒为提交载荷形态（§4.2 modelValue 载荷形态值空间）。
 */
function computeDefaultValue(material, props) {
  const configured = props ? props.defaultValue : null
  if (configured !== null && configured !== undefined && configured !== '') return configured
  return material.serializer(material.defaultValueGenerator(props || {}))
}

/**
 * 以 baseData（宿主 v-model 数据）为底稿构建含默认值的全量数据对象：
 * 对「undefined 字段」（键缺失）执行默认值补齐——编辑回显以 modelValue 优先
 * （§4.3 第 3 条），既有键（含显式 null）不覆盖；明细行内字段的默认值由明细表
 * 物料/行初始化承载（F15-FE-007），不在顶层补键。
 *
 * @param {Array} fields Schema 字段对象数组
 * @param {object|null} baseData 宿主数据（v-model）
 * @param {(type: string) => object|undefined} getMaterial 物料读取访问器
 * @returns {object} 补齐默认值后的全量数据对象（新对象，不改 baseData）
 */
export function buildInitialData(fields, baseData, getMaterial) {
  const base = baseData && typeof baseData === 'object' ? baseData : {}
  const result = { ...base }
  for (const node of flattenFields(fields)) {
    if (node.container === 'detailTable') continue
    const material = getMaterial(node.type)
    if (!material || resolveDataType(material, node.props) === null) continue
    if (result[node.field] !== undefined) continue
    result[node.field] = computeDefaultValue(material, node.props)
  }
  return result
}

/* ==================== availableVars 派生（§4.3 第 8 条） ==================== */

/**
 * 从 Schema 派生 FormulaWidget 可用变量声明（FormulaDesigner F14 契约
 * { code, type?, label? }）：全部数据类字段码声明 { code, type, label }，type 取
 * 物料 dataType 解析结果（多态物料按该字段 props 求值，如 formula 随
 * expectedReturnType）；明细表字段即数组变量 { code: 明细表字段码, type: 'ARRAY' }，
 * 支持主表公式 ${list.field} 投影声明；行内数据字段一并列出（行上下文公式
 * ${rowField} 消费，精确裁剪如后续设计器有诉求由 Wave 4 调整）。
 *
 * @param {Array} fields Schema 字段对象数组
 * @param {(type: string) => object|undefined} getMaterial 物料读取访问器
 * @returns {Array<{code: string, type: string, label: string}>} 变量声明数组
 */
export function deriveAvailableVars(fields, getMaterial) {
  const vars = []
  for (const node of flattenFields(fields)) {
    const material = getMaterial(node.type)
    if (!material) continue
    const dataType = resolveDataType(material, node.props)
    if (dataType === null) continue
    vars.push({ code: node.field, type: dataType, label: node.label || node.field })
  }
  return vars
}

/* ==================== 提交载荷裁剪（§4.3 第 4 条 + §3.6 ADR-08 前端半边） ==================== */

/**
 * 裁剪提交载荷：剔除「permission=hidden」字段（§4.3 第 4 条：静态可知、前端一致
 * 裁剪、后端兜底，§3.6 ②③层）；「props.hidden=true」的静态隐藏字段值**保留**
 * （§3.6 渲染层口径①：隐藏不删值，隐藏字段值原样保留在载荷中由服务端复算裁决）。
 * 两类隐藏的载荷语义差异即在此——本函数只执行 permission=hidden 裁剪。
 *
 * 一期边界（§3.6）：明细行内字段不做权限裁剪（行数据按行原样保留，行级裁剪随
 * 二期评估）；未知 type 与非数据类（dataType=null）字段不持有载荷键、无需处理；
 * 宿主数据中的额外键原样保留（渲染器只裁剪 Schema 已知字段）。
 *
 * @param {Array} fields Schema 字段对象数组
 * @param {object|null} data 当前数据对象（内部含全部字段值）
 * @param {object|null} fieldPermissions 外部权限覆盖映射
 * @param {(type: string) => object|undefined} getMaterial 物料读取访问器
 * @returns {object} 裁剪后的载荷副本（浅拷贝，不改动内部数据对象）
 */
export function cropPayloadData(fields, data, fieldPermissions, getMaterial) {
  const source = data && typeof data === 'object' ? data : {}
  const result = { ...source }
  for (const node of flattenFields(fields)) {
    if (node.container === 'detailTable') continue
    const material = getMaterial(node.type)
    if (!material || resolveDataType(material, node.props) === null) continue
    if (resolveFieldPermission(node.props, node.field, fieldPermissions) === 'hidden') {
      delete result[node.field]
    }
  }
  return result
}

/* ==================== 校验聚合（§4.2 expose.validate；一期 ruleId 恒 null） ==================== */

/**
 * 基础校验 + 物料校验器聚合（动态校验由 F15-FE-006 规则引擎并入后合并）。
 *
 * 口径：
 * - 仅校验数据类字段（物料 dataType 解析非 null）；未知 type 无校验器、跳过
 *   （渲染占位 + 服务端结构校验兜底，ADR-10）；
 * - permission=hidden 字段被载荷裁剪（§4.3 第 4 条），宿主无法填写，跳过校验；
 *   props.hidden=true 的静态隐藏字段值保留在载荷中，照常参与校验（其必填若
 *   同配属 Schema 配置矛盾，报错即暴露配置问题；动态必填随显隐联动由 FE-006
 *   合并裁决）；
 * - 明细表（detailTable）：宿主物料校验器管行数组类型与行数语义（rowMin/rowMax），
 *   行内字段对每行逐字段执行物料校验器，行上下文 { field, label, rowIndex, rowData }
 *   （rowIndex 0 起始，对齐 materials/common.js 校验器约定），错误消息加「第N行：」
 *   前缀定位；非对象行由宿主明细表物料节点报一条确定性错误；
 * - 返回 { field, ruleId: null, message } 数组（ruleId 一期恒 null，规则动态校验
 *   由 FE-006 并入后回填），message 全中文。
 *
 * @param {Array} fields Schema 字段对象数组
 * @param {object|null} data 当前数据对象
 * @param {object|null} fieldPermissions 外部权限覆盖映射
 * @param {(type: string) => object|undefined} getMaterial 物料读取访问器
 * @returns {Array<{field: string, ruleId: null, message: string}>} 错误数组
 */
export function validateFormData(fields, data, fieldPermissions, getMaterial) {
  const errors = []
  const source = data && typeof data === 'object' ? data : {}
  for (const node of flattenFields(fields)) {
    const material = getMaterial(node.type)
    if (!material) continue
    if (node.container === 'detailTable') {
      // 明细行内字段：逐行逐字段校验（行上下文注入，materials/common.js 校验器约定）
      const rows = Array.isArray(source[node.parentField]) ? source[node.parentField] : null
      if (!rows) continue
      rows.forEach((row, rowIndex) => {
        if (!row || typeof row !== 'object' || Array.isArray(row)) return
        const context = { field: node.field, label: node.label, rowIndex, rowData: row }
        for (const message of material.validator(row[node.field], node.props, context)) {
          errors.push({ field: node.field, ruleId: null, message: `第${rowIndex + 1}行：${message}` })
        }
      })
      continue
    }
    if (resolveDataType(material, node.props) === null) continue
    if (resolveFieldPermission(node.props, node.field, fieldPermissions) === 'hidden') continue
    const context = { field: node.field, label: node.label }
    for (const message of material.validator(source[node.field], node.props, context)) {
      errors.push({ field: node.field, ruleId: null, message })
    }
    if (node.type === 'detailTable') {
      // 行形态兜底：非对象行报一条确定性错误（行内字段校验跳过该行，见上）
      const rows = Array.isArray(source[node.field]) ? source[node.field] : []
      rows.forEach((row, rowIndex) => {
        if (!row || typeof row !== 'object' || Array.isArray(row)) {
          errors.push({ field: node.field, ruleId: null, message: `${node.label || node.field}第${rowIndex + 1}行必须为对象` })
        }
      })
    }
  }
  return errors
}

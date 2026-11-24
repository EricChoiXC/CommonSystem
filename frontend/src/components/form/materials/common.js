/**
 * 自定义表单物料库共享常量与工具（F15-FE-002）
 *
 * 口径依据：docs/v1_5-自定义表单/自定义表单功能详细设计.md §1.1（固定属性项按类别裁剪）、
 * §1.3（逐组件扩展属性项与值形态）、§2.2（资源限制常量）、§4.5（UI 设计分册属性面板
 * 字段规格）。本文件只放三类物料（data/content/layout）共用的常量、propSchema 固定项
 * 模板与校验/序列化助手，物料本体见同目录 dataMaterials.js / contentMaterials.js /
 * layoutMaterials.js。
 */

import { FORM_CATEGORIES, DATA_TYPES } from '../registry.js'

export { FORM_CATEGORIES, DATA_TYPES }

/* ==================== 资源限制常量（前端等值落位，对齐后端 SysFormConstants） ==================== */

/** 单行/多行文本 maxlength 应用层上限（等值常量，对齐后端 SysFormConstants.MAX_TEXT_LENGTH = 2000，功能设计 §2.2） */
export const MAX_TEXT_LENGTH = 2000

/** 单明细表行数上限，props.rowMax 不得超过该值（等值常量，对齐后端 SysFormConstants.MAX_DETAIL_ROWS = 200，功能设计 §2.2） */
export const MAX_DETAIL_ROWS = 200

/** 字段码约束（功能设计 §2.1，对齐后端 SysFormConstants.FIELD_CODE_REGEX；JS 侧加锚点做整串匹配） */
export const FIELD_CODE_PATTERN = '^[a-zA-Z_][a-zA-Z0-9_]*$'

/** 地址本组件多选拼接分隔符（对齐 components/AddressBook.vue 契约 septarator 默认 ';'） */
export const ADDRESSBOOK_SEPARATOR = ';'

/** 地址本可选类型全集（对齐 components/AddressBook.vue 契约 orgType 取值域，UI 分册 §4.5.2） */
export const ADDRESSBOOK_ORG_TYPES = [
  { label: '全部（机构/部门/岗位/人员）', value: 'ALL' },
  { label: '机构', value: 'ORG' },
  { label: '部门', value: 'DEPT' },
  { label: '机构或部门', value: 'ORGDEPT' },
  { label: '人员', value: 'PERSON' },
  { label: '岗位', value: 'POST' },
  { label: '人员或岗位', value: 'PERSONPOST' },
  { label: '机构/部门/岗位/人员', value: 'ORGDEPTPOSTPERSON' },
]

/** 级联选项显隐条件 operator 全集（功能设计 §3.2 十项，R08 条件 DSL） */
export const CONDITION_OPERATORS = ['equal', 'notEqual', 'gt', 'gte', 'lt', 'lte', 'in', 'notIn', 'empty', 'notEmpty']

/* ==================== editor 类型约定（propSchema 驱动属性面板动态渲染，Wave 4 消费） ==================== */

/**
 * propSchema 元素（属性项描述）约定结构：
 * {
 *   key:      string                  // 属性键；target='props' 时绑定 field.props[key]，
 *                                     //   'field' 时绑定字段对象顶层（label/field），
 *                                     //   'children' 时绑定字段对象 children（仅 grid 列配置）
 *   label:    string                  // 属性面板显示名（中文）
 *   editor:   string                  // 控件类型（EDITOR_TYPES 之一）
 *   target:   'props'|'field'|'children'  // 绑定目标，缺省 'props'
 *   options:  [{label, value}]        // select/radio 选项（静态）
 *   context:  string                  // contextSelect 选项来源声明：
 *                                     //   'dataFields'        = 表单内已配置数据类字段码（级联 parentField，排除自身）
 *                                     //   'detailNumericFields' = 本明细表行内数值类字段码（summary 统计字段）
 *   columns:  [{key, label, editor, options?}]  // table 明细编辑器的列描述
 *   min/max/precision: number         // number 编辑器取值域
 *   tip:      string                  // 面板提示文案
 * }
 * 属性项初始值统一取物料 defaultProps[key]（propSchema 不重复存默认值）。
 */
export const EDITOR_TYPES = [
  'input', // el-input 单行文本
  'textarea', // 多行文本域
  'number', // el-input-number（可带 min/max/precision）
  'switch', // el-switch
  'select', // el-select（静态 options；选项随 dateType 联动过滤等联动行为由面板实现）
  'radio', // el-radio-group（choice 排列方式 横排/竖排）
  'date', // 日期选择（dataType=DATE 的默认值专用）
  'json', // JSON 文本域（前端做 JSON 合法性预检；ARRAY/OBJECT 一期承载形态）
  'table', // 明细表编辑器（columns 声明列，行内增删）
  'fieldCode', // el-input + 字段码格式与同层级唯一性校验（UI 分册 §4.5.1）
  'formula', // 只读输入框 +【设计公式】按钮 → FormulaDesigner 弹窗（F14 契约，availableVars=表单字段码声明）
  'contextSelect', // el-select，选项由设计器画布上下文提供（context 声明来源）
  'datasource', // el-select，选项经 api/datasource.js#getDatasourceOptions() 异步加载（存编码不存快照）
  'condition', // 显隐条件编辑（operator 下拉 CONDITION_OPERATORS 十项 + value 输入，可空）
  'gridColumns', // 栅格列数与各列 span 维护（target='children'，24 栅格制合计超 24 提示）
  'none', // 不渲染输入控件（addressbook/attachment 默认值一期留空，由 defaultValueGenerator 兜底）
]

/* ==================== 固定属性项模板（功能设计 §1.1 按类别裁剪，UI 分册 §4.5.1） ==================== */

/** DATA 全部 12 组件固定项：名称 / ID / 是否必填 / 说明文本 / 默认值（defaultValue 编辑器随物料 dataType 选型传入） */
export function dataFixedPropSchema(defaultValueEditor) {
  return [
    { key: 'label', label: '名称', editor: 'input', target: 'field' },
    { key: 'field', label: 'ID（字段码）', editor: 'fieldCode', target: 'field', tip: '字母/下划线起始，仅字母数字下划线；同层级唯一' },
    { key: 'required', label: '是否必填', editor: 'switch' },
    { key: 'tooltip', label: '说明文本', editor: 'input' },
    { key: 'defaultValue', label: '默认值', editor: defaultValueEditor, tip: '留空时由默认值生成器兜底' },
  ]
}

/** CONTENT 内容类（除明细表）固定项：名称 / 说明文本（不承载数据，无 ID/必填/默认值） */
export function contentFixedPropSchema() {
  return [
    { key: 'label', label: '名称', editor: 'input', target: 'field' },
    { key: 'tooltip', label: '说明文本', editor: 'input' },
  ]
}

/** LAYOUT 布局类固定项：仅名称（分组即区块标题；无 ID/必填/说明/默认值） */
export function layoutFixedPropSchema() {
  return [{ key: 'label', label: '名称', editor: 'input', target: 'field' }]
}

/** detailTable（数据容器特例）固定项：名称 / ID / 是否必填 / 说明文本（必填语义=至少一行按 rowMin；默认值不适用，行由用户增删） */
export function detailTableFixedPropSchema() {
  return [
    { key: 'label', label: '名称', editor: 'input', target: 'field' },
    { key: 'field', label: 'ID（字段码）', editor: 'fieldCode', target: 'field', tip: '字母/下划线起始，仅字母数字下划线；同层级唯一' },
    { key: 'required', label: '是否必填', editor: 'switch', tip: '明细表语义 = 至少一行（按行数下限 rowMin）' },
    { key: 'tooltip', label: '说明文本', editor: 'input' },
  ]
}

/* ==================== defaultProps 模板（§2.1 props 约定项默认值 + §1.1 裁剪） ==================== */

/**
 * defaultProps 统一携带 §2.1 props 约定项默认值：hidden（静态初始可见性）与 permission
 * （字段级权限元数据 editable/readonly/hidden，v1.8 BPM 消费位）全类别保留；required /
 * tooltip / defaultValue 按 §1.1 固定属性项裁剪表出现（DATA 全有；detailTable 无
 * defaultValue；CONTENT 除明细表无 required/defaultValue；LAYOUT 仅 hidden/permission）。
 */
export function dataDefaultProps(ext) {
  return { required: false, hidden: false, permission: 'editable', tooltip: '', defaultValue: null, ...ext }
}

/** detailTable defaultProps（无 defaultValue——行由用户增删） */
export function detailTableDefaultProps(ext) {
  return { required: false, hidden: false, permission: 'editable', tooltip: '', ...ext }
}

/** CONTENT（除明细表）/ LAYOUT defaultProps（不承载数据，无 required/tooltip/defaultValue） */
export function plainDefaultProps(ext) {
  return { hidden: false, permission: 'editable', ...ext }
}

/* ==================== 校验器助手（validator： (value, props, context) => 中文错误消息数组） ==================== */

/**
 * 校验器统一约定：
 * - 入参 (value, props, context)：value 为字段值；props 为 field.props；context 由渲染器注入
 *   （一期约定 { field, label, rowIndex?, rowData? }，label 供错误文案）；
 * - 返回中文错误消息字符串数组（无错误返回空数组）；渲染器（F15-FE-005）负责包装为
 *   { field, ruleId, message } 结构与后端 errors 口径对齐；
 * - 空值不参与类型/长度/范围/枚举校验（必填校验独立负责），与后端 FormDataBasicValidator 口径一致；
 * - switch=false 为有义值（后端 BE-003 同口径），不判空。
 */

/** 空值判定：null/undefined/空白串/空数组/空对象为空；地址本兼容双形态（载荷 {ids,names} / 组件双绑定 {fieldId,fieldName}） */
export function isEmptyValue(value) {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  if (typeof value === 'object') {
    if (Array.isArray(value.ids)) return value.ids.length === 0 && (!Array.isArray(value.names) || value.names.length === 0)
    // 载荷 {ids,names} 双键形态：键存在但非数组（null 等脏形态）→ 按拆分后空态判定，
    // 不落入「对象有键即非空」的兜底分支（F15-FE-005 修正：原实现对 {ids:null} 误判为非空）
    if (value.ids !== undefined || value.names !== undefined) {
      return splitJoined(value.ids).length === 0 && splitJoined(value.names).length === 0
    }
    // 组件双绑定 {fieldId,fieldName} 形态：null/undefined 同空串处理（AddressBook 契约空串=未选，
    // F15-FE-005 修正：原实现 String(null)='null' 对 {fieldId:null} 误判为非空）
    if (value.fieldId !== undefined || value.fieldName !== undefined) {
      const fieldId = value.fieldId === null || value.fieldId === undefined ? '' : String(value.fieldId)
      return fieldId.trim() === ''
    }
    return Object.keys(value).length === 0
  }
  return false
}

/** 校验上下文字段名（错误文案用；context 未注入 label 时兜底「该字段」） */
export function labelOf(context) {
  return (context && context.label) || '该字段'
}

/** 必填校验（props.required） */
export function checkRequired(value, props, context) {
  if (props && props.required && isEmptyValue(value)) {
    return [`${labelOf(context)}为必填项`]
  }
  return []
}

/** 文本类型校验（input/textarea/date 以格式化字符串落库） */
export function checkStringType(value, context) {
  if (isEmptyValue(value)) return []
  if (typeof value !== 'string') return [`${labelOf(context)}必须为文本`]
  return []
}

/** 数组类型校验（multi 组件/明细表/附件） */
export function checkArrayType(value, context) {
  if (isEmptyValue(value)) return []
  if (!Array.isArray(value)) return [`${labelOf(context)}值必须为数组`]
  return []
}

/** 文本长度校验（input/textarea，props.maxlength；应用层上限 MAX_TEXT_LENGTH 由属性面板 number 编辑器 max 约束） */
export function checkMaxLength(value, props, context) {
  const maxlength = props ? props.maxlength : null
  if (typeof value === 'string' && maxlength !== null && maxlength !== undefined && value.length > Number(maxlength)) {
    return [`${labelOf(context)}长度不能超过${maxlength}个字符`]
  }
  return []
}

/**
 * 数字类型/范围/精度校验（number 组件）：容忍数值字符串形态漂移（对齐后端 BE-003）；
 * INTEGER 整数、allowNegative 非负、min/max 范围、precision 小数位上限。
 */
export function checkNumber(value, props, context) {
  if (isEmptyValue(value)) return []
  const label = labelOf(context)
  let num = value
  if (typeof num === 'string' && num.trim() !== '') {
    num = Number(num)
  }
  if (typeof num !== 'number' || Number.isNaN(num)) {
    return [`${label}必须为数字`]
  }
  const errors = []
  if (props && props.numberType === 'INTEGER' && !Number.isInteger(num)) {
    errors.push(`${label}必须为整数`)
  }
  if (props && props.allowNegative === false && num < 0) {
    errors.push(`${label}不允许为负数`)
  }
  if (props && props.min !== null && props.min !== undefined && num < Number(props.min)) {
    errors.push(`${label}不能小于${props.min}`)
  }
  if (props && props.max !== null && props.max !== undefined && num > Number(props.max)) {
    errors.push(`${label}不能大于${props.max}`)
  }
  if (props && props.precision !== null && props.precision !== undefined) {
    const text = String(num)
    const dotIndex = text.indexOf('.')
    if (dotIndex >= 0 && text.length - dotIndex - 1 > Number(props.precision)) {
      errors.push(`${label}小数位不能超过${props.precision}位`)
    }
  }
  return errors
}

/** 宽松相等：枚举值比对容忍数值字符串形态漂移（对齐后端 BE-003「容忍数值字符串形态漂移」） */
export function looseEquals(a, b) {
  if (a === b) return true
  if (a === null || a === undefined || b === null || b === undefined) return false
  return String(a) === String(b)
}

/**
 * 静态枚举选项值校验（choice/select/cascade：提交值 ∈ options 实际值集；
 * multi 多选逐元素比对。datasource 选项运行时取数、不适用静态比对；
 * 级联「父项条件过滤后的动态可选集」由渲染器本地过滤 + 服务端复核（R08），不在静态校验内）。
 */
export function checkEnumOptions(value, props, context) {
  const options = props && Array.isArray(props.options) ? props.options : []
  if (options.length === 0 || isEmptyValue(value)) return []
  const validValues = options.map((item) => (item && item.value !== undefined ? item.value : item))
  const label = labelOf(context)
  if (props && props.multi) {
    if (!Array.isArray(value)) return [`${label}值必须为数组`]
    const invalid = value.filter((item) => !validValues.some((valid) => looseEquals(item, valid)))
    if (invalid.length > 0) return [`${label}的值「${invalid.join('、')}」不在可选范围内`]
    return []
  }
  if (!validValues.some((valid) => looseEquals(value, valid))) {
    return [`${label}的值「${value}」不在可选范围内`]
  }
  return []
}

/* ==================== 序列化助手（serializer：组件值 → 提交载荷；deserializer：回显数据 → 组件值） ==================== */

/** 恒等序列化/反序列化（基础类型组件值即载荷形态） */
export const serializeIdentity = (value) => value
export const deserializeIdentity = (value) => value

/** 分隔串/数组统一拆为数组（地址本双绑定值适配用，'a;b' → ['a','b']） */
function splitJoined(value) {
  if (value === null || value === undefined || value === '') return []
  if (Array.isArray(value)) return value
  return String(value)
    .split(ADDRESSBOOK_SEPARATOR)
    .map((item) => item.trim())
    .filter((item) => item !== '')
}

/**
 * 地址本值适配（§1.3/§6.5：复用 AddressBook.vue，组件为 fieldId/fieldName 双绑定——
 * 多选经分隔符拼接字符串；载荷形态为 OBJECT { ids: [], names: [] }）。
 * serializer 双向兼容双绑定形态与已拆分数组形态，统一归一为 { ids, names }。
 */
export const serializeAddressBook = (value) => {
  if (value === null || value === undefined) return null
  return {
    ids: splitJoined(value.ids !== undefined ? value.ids : value.fieldId),
    names: splitJoined(value.names !== undefined ? value.names : value.fieldName),
  }
}

/** 地址本反序列化：载荷 { ids, names } → 组件双绑定 { fieldId, fieldName }（空值给空绑定态） */
export const deserializeAddressBook = (value) => {
  const empty = { fieldId: '', fieldName: '' }
  if (value === null || value === undefined) return empty
  const ids = splitJoined(value.ids !== undefined ? value.ids : value.fieldId)
  const names = splitJoined(value.names !== undefined ? value.names : value.fieldName)
  return { fieldId: ids.join(ADDRESSBOOK_SEPARATOR), fieldName: names.join(ADDRESSBOOK_SEPARATOR) }
}

/**
 * 附件值适配（§1.3/§6.5：复用 Attachment.vue，附件实体落 sys/att，表单数据仅存引用
 * ARRAY [{ attId, fileName }]）。serializer 剥离元素多余字段统一为 { attId, fileName }，
 * 脏形态归一为空数组（类型错误交由校验器前置报出）。
 */
export const serializeAttachment = (value) => {
  if (value === null || value === undefined) return null
  if (!Array.isArray(value)) return []
  return value
    .filter((item) => item !== null && typeof item === 'object')
    .map((item) => ({ attId: item.attId, fileName: item.fileName }))
}

/** 附件反序列化：回显引用数组归一为 [{ attId, fileName }]（空值给空数组） */
export const deserializeAttachment = (value) => {
  if (value === null || value === undefined) return []
  if (!Array.isArray(value)) return []
  return value
    .filter((item) => item !== null && typeof item === 'object')
    .map((item) => ({ attId: item.attId, fileName: item.fileName }))
}

/**
 * 自定义表单组件注册协议（F15-FE-002）
 *
 * 口径依据：docs/v1_5-自定义表单/自定义表单功能详细设计.md §1.1（统一组件元数据结构）、
 * §1.2（前端物料注册协议，要点文档第 3 步注册表形态）、§1.3（组件全集定稿清单 R03）、
 * §4.3（渲染行为定稿——渲染器经 componentRegistry 取物料，未知 type 兜底占位）。
 *
 * 【协议先于实现】渲染器（F15-FE-005 FormRenderer）与设计器（F15-FE-008）只经本文件
 * 的读取接口取物料，任何组件不得直接 import 其他组件的运行时实现；新增组件只需注册
 * 物料、不改渲染器与规则引擎（开闭原则）。
 *
 * 【注册时序】物料由 ./materials/index.js 聚合注册（18 个：数据类 12 + 内容类 4 + 布局类 2）；
 * 运行时 Vue 组件（material.component）按后续任务分批补注册——F15-FE-003 基础数据七件、
 * F15-FE-004 业务数据四件、F15-FE-007 内容与明细、F15-FE-010 级联。本任务 component
 * 一律为 null：null = 运行时组件待分批注册，渲染器对 component 为 null 的 type 按未知
 * type 兜底占位处理（ADR-10：渲染期不抛异常、不阻断整表渲染；保存期由后端结构校验器
 * 按 type 白名单拒绝脏 Schema，双保险）。
 *
 * 【CONTROL 协议位】控制类（type=permission 权限组件）一期仅登记协议位：不注册物料、
 * registry 无其条目、物料栏不展示、不进 Schema fields（后端 SysFormConstants.REJECTED_TYPES
 * 同口径拒绝）。其消费形态（审批人 / 创建人 / 可阅读者在审批与阅读时的字段可见性与
 * 可编辑性）随 v1.8 BPM 由流程侧下发字段权限覆盖——协议载体即 Schema 字段对象
 * props.permission 字段级权限元数据（功能设计 §2.1/§6.6）+ 渲染器 props.fieldPermissions
 * 外部覆盖映射（§4.2），本 registry 不为其预留条目，v1.8 接入时按同一 registerComponent
 * 协议注册即可，无需改本文件。
 */

/** 组件类别（对齐后端 SysFormComponentCategory 枚举：DATA/CONTENT/LAYOUT/CONTROL） */
export const FORM_CATEGORIES = {
  DATA: 'DATA',
  CONTENT: 'CONTENT',
  LAYOUT: 'LAYOUT',
  CONTROL: 'CONTROL',
}

/** 合法类别取值域（registerComponent 断言用） */
export const FORM_CATEGORY_VALUES = Object.values(FORM_CATEGORIES)

/** 物料栏分组展示序（数据 / 内容 / 布局）；CONTROL 仅协议位，物料栏不展示 */
export const MATERIAL_PANEL_CATEGORIES = [FORM_CATEGORIES.DATA, FORM_CATEGORIES.CONTENT, FORM_CATEGORIES.LAYOUT]

/** 数据类型取值域（功能设计 §1.1 dataType；§1.3 逐组件取值见 materials） */
export const DATA_TYPES = ['STRING', 'NUMBER', 'BOOLEAN', 'DATE', 'ARRAY', 'OBJECT']

/** 七件套键（registerComponent 完整性断言用，功能设计 §1.1） */
export const REQUIRED_MATERIAL_KEYS = [
  'propSchema',
  'component',
  'defaultValueGenerator',
  'dataType',
  'validator',
  'serializer',
  'deserializer',
]

/** 组件注册表（type → material）。外部只读消费（get/has/遍历），注册必须经 registerComponent 走 fail-fast 断言 */
export const componentRegistry = new Map()

/**
 * 断言辅助：条件不成立即抛错（fail-fast，注册期拦截脏物料，绝不带病注册）
 * @param {boolean} condition 断言条件
 * @param {string} message 中文错误消息
 */
function assertMaterial(condition, message) {
  if (!condition) {
    throw new Error(`[FormRegistry] ${message}`)
  }
}

/**
 * 注册组件物料（功能设计 §1.2 定稿形态）。
 *
 * fail-fast 断言口径（任一不满足即抛错，阻断注册）：
 * - type/name/label/icon：非空字符串；type 为 registry 查重键，重复注册抛错；
 * - category：必须是 DATA/CONTENT/LAYOUT/CONTROL 之一（对齐后端枚举）；
 * - defaultProps：普通对象（固定属性项 + 扩展属性项的初始值，§1.1）；
 * - 七件套 propSchema/component/defaultValueGenerator/dataType/validator/serializer/deserializer
 *   键缺失即抛错：
 *   · propSchema 必须为数组（属性面板动态渲染描述，元素结构约定见 materials/common.js）；
 *   · component 允许 null（= 运行时组件待 F15-FE-003/004/007/010 分批注册，
 *     渲染器对 null 按未知 type 兜底占位），但键必须存在且不得为 undefined；
 *   · defaultValueGenerator/validator/serializer/deserializer 必须为函数；
 *   · dataType 必须为 DATA_TYPES 之一的字符串、或 (props) => dataType 函数
 *     （多态类型：choice/select/cascade/datasource 按 props.multi、formula 按
 *     props.expectedReturnType，§1.3），或 null（仅 CONTENT/LAYOUT 不承载数据的组件允许）。
 *
 * 注册成功的物料被 shallow 冻结（防运行时误改元数据）；返回该物料便于测试断言。
 * @param {object} material 组件物料 = 元数据 + 七件套（结构见功能设计 §1.1）
 * @returns {object} 冻结后的物料
 */
export function registerComponent(material) {
  assertMaterial(material !== null && typeof material === 'object' && !Array.isArray(material), '物料必须为对象')

  const { type, name, label, category, icon, defaultProps } = material

  assertMaterial(typeof type === 'string' && type !== '', '物料 type 必须为非空字符串')
  assertMaterial(!componentRegistry.has(type), `组件 type「${type}」重复注册，同一 type 只允许注册一次`)
  assertMaterial(typeof name === 'string' && name !== '', `物料「${type}」name 必须为非空字符串`)
  assertMaterial(typeof label === 'string' && label !== '', `物料「${type}」label 必须为非空字符串`)
  assertMaterial(FORM_CATEGORY_VALUES.includes(category), `物料「${type}」category 必须是 ${FORM_CATEGORY_VALUES.join('/')} 之一`)
  assertMaterial(typeof icon === 'string' && icon !== '', `物料「${type}」icon 必须为非空字符串（物料栏图标名）`)
  assertMaterial(defaultProps !== null && typeof defaultProps === 'object' && !Array.isArray(defaultProps), `物料「${type}」defaultProps 必须为普通对象`)

  for (const key of REQUIRED_MATERIAL_KEYS) {
    assertMaterial(key in material, `物料「${type}」缺少七件套之一：${key}`)
  }
  assertMaterial(Array.isArray(material.propSchema), `物料「${type}」propSchema 必须为数组（属性面板动态渲染描述）`)
  assertMaterial(material.component !== undefined, `物料「${type}」component 键必须存在（一期允许 null=运行时组件待分批注册）`)
  assertMaterial(typeof material.defaultValueGenerator === 'function', `物料「${type}」defaultValueGenerator 必须为函数`)
  assertMaterial(typeof material.validator === 'function', `物料「${type}」validator 必须为函数`)
  assertMaterial(typeof material.serializer === 'function', `物料「${type}」serializer 必须为函数`)
  assertMaterial(typeof material.deserializer === 'function', `物料「${type}」deserializer 必须为函数`)

  const dataType = material.dataType
  const dataTypeOk =
    (typeof dataType === 'string' && DATA_TYPES.includes(dataType)) ||
    typeof dataType === 'function' ||
    (dataType === null && (category === FORM_CATEGORIES.CONTENT || category === FORM_CATEGORIES.LAYOUT))
  assertMaterial(
    dataTypeOk,
    `物料「${type}」dataType 必须为 ${DATA_TYPES.join('/')} 之一的字符串、(props)=>dataType 函数或 null（null 仅 CONTENT/LAYOUT 不承载数据的组件允许）`,
  )

  const frozen = Object.freeze(material)
  componentRegistry.set(type, frozen)
  return frozen
}

/** 按组件 type 取物料；未注册返回 undefined（渲染器据此走「组件未注册」兜底占位，ADR-10） */
export function getComponentMaterial(type) {
  return componentRegistry.get(type)
}

/** 判断组件 type 是否已注册 */
export function hasComponentMaterial(type) {
  return componentRegistry.has(type)
}

/** 按类别取物料数组（注册序）；物料栏按此分组展示（§1.2 分类分组） */
export function getMaterialsByCategory(category) {
  const result = []
  for (const material of componentRegistry.values()) {
    if (material.category === category) {
      result.push(material)
    }
  }
  return result
}

/** 全部已注册物料数组（注册序） */
export function listMaterials() {
  return Array.from(componentRegistry.values())
}

/** 全部已注册组件 type 数组（对齐后端 SysFormConstants.ALLOWED_FIELD_TYPES 白名单语义的前端侧） */
export function getComponentTypes() {
  return Array.from(componentRegistry.keys())
}

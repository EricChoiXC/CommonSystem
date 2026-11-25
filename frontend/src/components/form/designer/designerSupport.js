/**
 * 表单设计器纯逻辑辅助模块（F15-FE-008）
 *
 * 口径依据：docs/v1_5-自定义表单/自定义表单UI设计分册.md
 * - §4.4 画布交互（快照栈撤销重做 / 复制重分配字段码 / 容器嵌套放置约束）；
 * - §4.6 规则配置面板（事件六类 / operator 十项 / 动作六类 / 循环防护上限提示文案）；
 * - §4.8 Schema 导入导出（轻量结构校验器 + 资源上限 + 草稿 version=null）；
 * 与 docs/v1_5-自定义表单/自定义表单功能详细设计.md §2.1（Schema 结构）、§2.2（资源
 * 限制常量）、§3（规则协议 DSL）、R04（一期一层明细表）。
 *
 * 【纯 JS 约束】本文件零 import（连 Vue 与物料库也不引，先例 rendererSupport.js），
 * 可被 node 冒烟脚本直接加载；物料知识经调用方注入的 getMaterial(type) 访问器访问
 * （FormDesigner 传入 registry.js 的 getComponentMaterial），本文件不感知物料实现。
 *
 * 【设计器零业务规则】本模块只承载 Schema 编辑类通用逻辑（快照栈 / 扁平索引 / 字段码
 * 生成 / 放置约束 / 导入校验 / 摘要文案），不含任何具体业务字段语义与规则求值
 * （规则求值在 rules/evaluators.js，渲染在 FormRenderer）。
 */

/* ==================== 资源限制常量（前端等值落位，对齐后端 SysFormConstants 权威值） ==================== */

/**
 * 单表单字段总数上限（含容器内字段与明细行内字段）。
 * 等值常量，对齐后端 SysFormConstants.MAX_FIELD_COUNT = 200（功能设计 §2.2）；
 * 达上限物料卡片置灰 + 拦截插入（保存期服务端权威校验，画布侧为交互预判）。
 */
export const MAX_FIELD_COUNT = 200

/**
 * 字段最大嵌套深度（顶层字段 = 1，容器内字段 = 容器链深 + 1；R04：明细表内禁容器）。
 * 等值常量，对齐后端 SysFormConstants.MAX_NEST_DEPTH = 3（功能设计 §2.2）；
 * 超限拖拽/插入拦截提示。
 */
export const MAX_NEST_DEPTH = 3

/**
 * field_schema_json 序列化体积上限（字符数 512 KB）。
 * 等值常量，对齐后端 SysFormConstants.MAX_SCHEMA_JSON_LENGTH = 524288（功能设计 §2.2）；
 * 导入/导出应用层预检，与保存期校验同常量。
 */
export const MAX_SCHEMA_JSON_LENGTH = 524288

/**
 * 单表单规则总数上限。等值常量，对齐后端 SysFormConstants.MAX_RULE_COUNT = 100
 * （功能设计 §2.2）；达上限【添加规则】禁用 + 提示（规则引擎登记期同口径告警）。
 */
export const MAX_RULE_COUNT = 100

/**
 * 规则链深度上限。等值常量，对齐后端 SysFormConstants.MAX_RULE_CHAIN_DEPTH = 5
 * （功能设计 §2.2）；面板底部固定提示文案消费。
 */
export const MAX_RULE_CHAIN_DEPTH = 5

/**
 * 单次交互动作执行次数上限。等值常量，对齐后端
 * SysFormConstants.MAX_RULE_EXECUTIONS_PER_CYCLE = 50（功能设计 §2.2）；
 * 面板底部固定提示文案消费（求值与熔断实现归 F15-FE-006 规则引擎）。
 */
export const MAX_RULE_EXECUTIONS_PER_CYCLE = 50

/**
 * Schema 快照栈深度上限（撤销重做内存策略，UI 分册 §11 T-05 实现期定值 50 步）：
 * 仅作用于本次设计会话的草稿 Schema，超出丢弃最旧快照；发布产生的不可变版本不进
 * 快照栈、离开页面快照丢弃（§4.4 撤销重做边界）。
 */
export const SNAPSHOT_STACK_LIMIT = 50

/** 面板底部固定循环防护上限提示文案（§4.6 定稿文案，常量随上文等值常量联动） */
export const RULE_LIMIT_TIP =
  `单表单规则总数上限 ${MAX_RULE_COUNT}（MAX_RULE_COUNT）；规则链深度上限 ${MAX_RULE_CHAIN_DEPTH} 层` +
  `（MAX_RULE_CHAIN_DEPTH）；单次交互动作执行上限 ${MAX_RULE_EXECUTIONS_PER_CYCLE} 次` +
  `（MAX_RULE_EXECUTIONS_PER_CYCLE），超限熔断并在控制台告警`

/**
 * 字段码约束（功能设计 §2.1，对齐后端 SysFormConstants.FIELD_CODE_REGEX；
 * JS 侧加锚点做整串匹配，与 materials/common.js FIELD_CODE_PATTERN 同值登记）。
 */
export const FIELD_CODE_PATTERN = '^[a-zA-Z_][a-zA-Z0-9_]*$'

/** 容器类 type（布局容器 group/grid + 数据容器 detailTable，§2.1 children 承载） */
export const CONTAINER_TYPES = ['group', 'grid', 'detailTable']

/** 是否容器类 type（group/grid/detailTable） */
export function isContainerType(type) {
  return CONTAINER_TYPES.includes(type)
}

/* ==================== 规则协议展示常量（§3.2/§3.3/§3.4，协议权威在 rules/evaluators.js） ==================== */

/**
 * 规则可配置事件（六类，§3.4：onChange 主力 / onLoad / onAdd / onDelete / onSubmit /
 * onValidate；onFocus/onBlur 一期不开放配置——仅事件透传，防规则风暴）。
 */
export const RULE_EVENT_OPTIONS = [
  { value: 'onChange', label: '值变化时（onChange）' },
  { value: 'onLoad', label: '表单加载时（onLoad）' },
  { value: 'onAdd', label: '明细新增行时（onAdd）' },
  { value: 'onDelete', label: '明细删除行时（onDelete）' },
  { value: 'onSubmit', label: '提交前（onSubmit）' },
  { value: 'onValidate', label: '校验阶段（onValidate）' },
]

/** 条件 operator 中文标签（§3.2 十项全集；协议值与 rules/evaluators.js RULE_OPERATORS 同源） */
export const RULE_OPERATOR_LABELS = {
  equal: '等于',
  notEqual: '不等于',
  gt: '大于',
  gte: '大于等于',
  lt: '小于',
  lte: '小于等于',
  in: '属于',
  notIn: '不属于',
  empty: '为空',
  notEmpty: '不为空',
}

/** operator 下拉选项（十项，按 RULE_OPERATOR_LABELS 序） */
export const RULE_OPERATOR_OPTIONS = Object.entries(RULE_OPERATOR_LABELS).map(([value, label]) => ({ value, label }))

/** 标量比较 operator（value 控件 = 标量输入） */
export const SCALAR_OPERATORS = ['equal', 'notEqual', 'gt', 'gte', 'lt', 'lte']

/** 多值 operator（value 控件 = 标签式多值输入） */
export const MULTI_VALUE_OPERATORS = ['in', 'notIn']

/** 无值 operator（隐藏 value 控件） */
export const NO_VALUE_OPERATORS = ['empty', 'notEmpty']

/** 动作类型中文标签（§3.3 六类全集；协议值与 rules/evaluators.js RULE_ACTION_TYPES 同源） */
export const RULE_ACTION_LABELS = {
  show: '显示',
  hide: '隐藏',
  setValue: '赋值',
  clearValue: '清空值',
  setRequired: '动态必填',
  setValidate: '动态校验覆盖',
}

/** 动作类型下拉选项（六项） */
export const RULE_ACTION_OPTIONS = Object.entries(RULE_ACTION_LABELS).map(([value, label]) => ({ value, label }))

/** 禁止配置值类动作的事件（§3.4：onSubmit/onValidate 仅支持 setRequired/setValidate/show/hide；与 rules/evaluators.js VALUE_FORBIDDEN_EVENTS 同值登记） */
export const VALUE_FORBIDDEN_EVENT_VALUES = ['onSubmit', 'onValidate']

/** 提交段事件下值类动作禁用提示文案（§4.6 规则编辑表单约束） */
export const VALUE_FORBIDDEN_TIP = '提交段事件（onSubmit/onValidate）禁止配置值类动作（setValue/clearValue），已配置的值类动作将被规则引擎跳过'

/* ==================== 基础工具 ==================== */

/** 深拷贝（JSON 序列化口径，Schema 为纯 JSON 结构；undefined 键丢弃属预期） */
export function deepClone(value) {
  if (value === null || value === undefined) return value
  return JSON.parse(JSON.stringify(value))
}

/** 解析物料 dataType（string / (props)=>string / null 三形态，与 rendererSupport.resolveDataType 同口径的本模块等值实现） */
export function resolveMaterialDataType(material, props) {
  if (!material) return null
  const dataType = material.dataType
  if (typeof dataType === 'function') return dataType(props || {})
  return dataType === undefined ? null : dataType
}

/** 统计字段树字段总数（含容器内与明细行内字段，防御脏项跳过） */
export function countFields(fields) {
  let count = 0
  const list = Array.isArray(fields) ? fields : []
  for (const node of list) {
    if (!node || typeof node !== 'object' || !node.field || !node.type) continue
    count += 1
    if (node.type === 'group' && Array.isArray(node.children)) count += countFields(node.children)
    else if (node.type === 'grid' && Array.isArray(node.children)) {
      for (const column of node.children) {
        if (column && Array.isArray(column.fields)) count += countFields(column.fields)
      }
    } else if (node.type === 'detailTable' && Array.isArray(node.children)) count += countFields(node.children)
  }
  return count
}

/** 统计字段树规则总数（规则随触发字段存放于字段对象 rules[]，§3.1） */
export function countRules(fields) {
  let count = 0
  const list = Array.isArray(fields) ? fields : []
  for (const node of list) {
    if (!node || typeof node !== 'object' || !node.field || !node.type) continue
    count += Array.isArray(node.rules) ? node.rules.length : 0
    if (node.type === 'group' && Array.isArray(node.children)) count += countRules(node.children)
    else if (node.type === 'grid' && Array.isArray(node.children)) {
      for (const column of node.children) {
        if (column && Array.isArray(column.fields)) count += countRules(column.fields)
      }
    } else if (node.type === 'detailTable' && Array.isArray(node.children)) count += countRules(node.children)
  }
  return count
}

/**
 * 统计单个字段对象子树规模（容器级联删除确认文案消费，§4.4 删除交互）：
 * { fields: 子树字段总数（含自身）, rules: 子树规则总数 }
 */
export function countSubtree(node) {
  if (!node || typeof node !== 'object') return { fields: 0, rules: 0 }
  const fields = Array.isArray(node.children) ? node.children : []
  const list = node.type === 'grid' ? fields : fields
  let fieldCount = 1
  let ruleCount = Array.isArray(node.rules) ? node.rules.length : 0
  if (node.type === 'grid') {
    for (const column of list) {
      if (column && Array.isArray(column.fields)) {
        const counted = countSubtreeList(column.fields)
        fieldCount += counted.fields
        ruleCount += counted.rules
      }
    }
  } else {
    const counted = countSubtreeList(list)
    fieldCount += counted.fields
    ruleCount += counted.rules
  }
  return { fields: fieldCount, rules: ruleCount }
}

function countSubtreeList(list) {
  let fields = 0
  let rules = 0
  for (const child of Array.isArray(list) ? list : []) {
    if (!child || typeof child !== 'object' || !child.field || !child.type) continue
    const counted = countSubtree(child)
    fields += counted.fields
    rules += counted.rules
  }
  return { fields, rules }
}

/** 统计某名称在字段树中的出现次数（默认名称序号生成用，§4.3「组件中文名 + 序号」） */
export function countLabelOccurrences(fields, label) {
  let count = 0
  const list = Array.isArray(fields) ? fields : []
  for (const node of list) {
    if (!node || typeof node !== 'object' || !node.field || !node.type) continue
    if (node.label === label) count += 1
    if (node.type === 'group' && Array.isArray(node.children)) count += countLabelOccurrences(node.children, label)
    else if (node.type === 'grid' && Array.isArray(node.children)) {
      for (const column of node.children) {
        if (column && Array.isArray(column.fields)) count += countLabelOccurrences(column.fields, label)
      }
    } else if (node.type === 'detailTable' && Array.isArray(node.children)) count += countLabelOccurrences(node.children, label)
  }
  return count
}

/* ==================== Schema 快照栈（§4.4 撤销重做，仅草稿会话，T-05 栈深 50） ==================== */

/**
 * 创建 Schema 快照栈（内存栈，栈内持有各状态深快照）：
 * - push(state)：压入新状态（截断 redo 侧；超 SNAPSHOT_STACK_LIMIT 丢弃最旧快照）；
 * - undo()/redo()：返回目标状态快照（调用方须 deepClone 后使用，避免外部改动污染栈内快照）；
 * - reset(state)：以初始状态重建（加载 / 发布刷新等会话边界处调用）。
 */
export function createSchemaHistory(initialState, options = {}) {
  const maxDepth = Number.isFinite(options.maxDepth) && options.maxDepth > 0 ? options.maxDepth : SNAPSHOT_STACK_LIMIT
  let stack = []
  let cursor = -1

  function reset(state) {
    stack = state === null || state === undefined ? [] : [deepClone(state)]
    cursor = stack.length - 1
  }

  function push(state) {
    if (state === null || state === undefined) return
    stack = stack.slice(0, cursor + 1)
    stack.push(deepClone(state))
    if (stack.length > maxDepth) {
      stack.shift()
    }
    cursor = stack.length - 1
  }

  function undo() {
    if (cursor <= 0) return null
    cursor -= 1
    return deepClone(stack[cursor])
  }

  function redo() {
    if (cursor >= stack.length - 1) return null
    cursor += 1
    return deepClone(stack[cursor])
  }

  function canUndo() {
    return cursor > 0
  }

  function canRedo() {
    return cursor < stack.length - 1
  }

  function current() {
    return cursor >= 0 && cursor < stack.length ? deepClone(stack[cursor]) : null
  }

  reset(initialState)
  return { push, undo, redo, canUndo, canRedo, reset, current, get size() { return stack.length } }
}

/* ==================== 扁平字段索引（fieldCode → 节点引用 / 父列表 / 下标 / 路径 / 深度） ==================== */

/**
 * 构建字段扁平索引（先序遍历，含 group/grid children 与明细表行内）：
 * entry = {
 *   node            字段对象引用（mutations 直接作用于所在 Schema 克隆），
 *   field/type/label/props/rules  常用读取位（props/rules 为 node 上的引用），
 *   parentList      父字段对象数组引用（grid 列 fields / group·detail children / 根 fields），
 *   parentIndex     在父列表中的下标，
 *   parentEntry     父容器条目（根级 null），
 *   containerType   所在列表种类：'root' | 'group' | 'grid' | 'detailTable'，
 *   parentField     宿主容器字段码（根级 null），
 *   columnIndex     grid 列下标（非 grid 列内为 null），
 *   depth           字段嵌套深度（顶层 = 1，容器内 = 容器链深 + 1，§2.2 口径），
 *   path            位置路径（如 fields[2].children[0].fields[1]，与 §2.1 children 结构
 *                   逐段对应：group·明细 children 为字段数组、grid children 为列对象数组
 *                   { span, fields[] }——列段 children[c]、列内字段段 fields[j]），
 * }
 */
export function buildFieldIndex(fields) {
  const entries = []
  walkList(Array.isArray(fields) ? fields : [], null, 'root', [], 1, null, 'fields')
  return { entries, byPath: new Map(entries.map((entry) => [entry.path, entry])) }

  function walkList(list, parentEntry, containerType, segs, depth, columnIndex, listKey) {
    if (!Array.isArray(list)) return
    list.forEach((node, index) => {
      if (!node || typeof node !== 'object' || !node.field || !node.type) return
      const nextSegs = segs.concat([{ key: listKey, index }])
      const path = segsToPath(nextSegs)
      const entry = {
        node,
        field: node.field,
        type: node.type,
        label: node.label,
        props: node.props && typeof node.props === 'object' ? node.props : {},
        rules: Array.isArray(node.rules) ? node.rules : [],
        parentList: list,
        parentIndex: index,
        parentEntry: parentEntry || null,
        containerType,
        parentField: parentEntry ? parentEntry.field : null,
        columnIndex: columnIndex === undefined || columnIndex === null ? null : columnIndex,
        depth,
        path,
      }
      entries.push(entry)
      if (node.type === 'group' && Array.isArray(node.children)) {
        walkList(node.children, entry, 'group', nextSegs, depth + 1, null, 'children')
      } else if (node.type === 'grid' && Array.isArray(node.children)) {
        node.children.forEach((column, colIndex) => {
          if (!column || !Array.isArray(column.fields)) return
          walkList(column.fields, entry, 'grid', nextSegs.concat([{ key: 'children', index: colIndex }]), depth + 1, colIndex, 'fields')
        })
      } else if (node.type === 'detailTable' && Array.isArray(node.children)) {
        walkList(node.children, entry, 'detailTable', nextSegs, depth + 1, null, 'children')
      }
    })
  }
}

/** 路径段 → 路径串（fields[0].children[1].fields[2]） */
function segsToPath(segs) {
  return segs.map((seg) => `${seg.key}[${seg.index}]`).join('.')
}

/**
 * 按路径解析字段对象（设计器选中态恢复 / 属性回写定位）：
 * 路径段形如 fields[0] / children[1]，逐段属性 + 数组下标行进；解析失败返回 null。
 */
export function resolvePathNode(schema, path) {
  if (!schema || typeof path !== 'string' || path === '') return null
  let cursor = schema
  for (const token of path.split('.')) {
    const matched = /^([a-zA-Z]+)\[(\d+)\]$/.exec(token)
    if (!matched) return null
    const next = cursor[matched[1]]
    if (!Array.isArray(next)) return null
    const index = Number(matched[2])
    if (!Number.isInteger(index) || index < 0 || index >= next.length) return null
    cursor = next[index]
    if (!cursor || typeof cursor !== 'object') return null
  }
  return cursor
}

/**
 * 按「字段码链」解析索引条目（画布 DOM → 索引条目的反向定位）：
 * 画布以 data-field=字段码 标注字段元素，行内字段码可与主表同码（§2.1），故按
 * 「由外到内字段码链」（外层容器码 … 内层字段码）唯一解析——codes 为由外到内顺序。
 */
export function resolveEntryByCodeChain(entries, codes) {
  if (!Array.isArray(codes) || codes.length === 0) return null
  const innerCode = codes[codes.length - 1]
  const outerCodes = codes.slice(0, -1)
  for (const entry of entries) {
    if (entry.field !== innerCode) continue
    if (matchParentChain(entry.parentEntry, [...outerCodes].reverse())) return entry
  }
  return null
}

/** 父链匹配：innermost-first 的父条目链字段码与 reversed 外层码逐一相等 */
function matchParentChain(parentEntry, expectedInnerFirst) {
  let cursor = parentEntry
  for (const code of expectedInnerFirst) {
    if (!cursor || cursor.field !== code) return false
    cursor = cursor.parentEntry
  }
  return cursor === null || cursor === undefined
}

/* ==================== 字段码 / 默认名称生成（§4.3：{type}_{序号}，同层级唯一） ==================== */

/**
 * 生成默认字段码：`{type}_{序号}`（如 input_1），在同层级既有码集合上取最小可用序号；
 * 码形态由 type（物料注册键，[a-zA-Z_] 起始）保证天然满足 FIELD_CODE_PATTERN。
 * @param {string} type 组件 type
 * @param {string[]} usedCodes 同层级既有字段码集合（含自身层级外码不参与——明细行内可与主表同码）
 */
export function generateFieldCode(type, usedCodes) {
  const used = new Set(Array.isArray(usedCodes) ? usedCodes : [])
  let index = 1
  let code = `${type}_${index}`
  while (used.has(code)) {
    index += 1
    code = `${type}_${index}`
  }
  return code
}

/**
 * 创建新字段对象（物料卡点击插入 / 拖拽落点共用）：
 * defaultProps 深拷贝为初始 props；默认名称 = 组件中文名 + 全表同类序号（§4.3）；
 * 默认字段码 = {type}_{序号}（同层唯一，§4.3）；容器初始化 children——group/detailTable
 * 为空数组，grid 为两列 span=12（实现期定值：物料 defaultProps 未定义 children 初值，
 * 栅格入画布即两列布局便于嵌套放置，列数与 span 经属性面板 gridColumns 编辑器调整）。
 *
 * @returns {{ node?: object, error?: string }}
 */
export function createFieldNode(type, options = {}) {
  const getMaterial = typeof options.getMaterial === 'function' ? options.getMaterial : () => undefined
  const material = getMaterial(type)
  if (!material) return { error: `组件「${type}」未注册，无法创建` }
  const siblingCodes = Array.isArray(options.siblingCodes) ? options.siblingCodes : []
  const allFields = Array.isArray(options.allFields) ? options.allFields : []
  const node = {
    field: generateFieldCode(type, siblingCodes),
    type,
    label: `${material.label}${countLabelOccurrences(allFields, material.label) + 1}`,
    props: deepClone(material.defaultProps) || {},
    rules: [],
  }
  if (type === 'group' || type === 'detailTable') {
    node.children = []
  } else if (type === 'grid') {
    node.children = [
      { span: 12, fields: [] },
      { span: 12, fields: [] },
    ]
  }
  return { node }
}

/**
 * 复制深拷贝并重分配字段码（§4.4 复制交互）：整棵子树深拷贝（含 props/rules/children），
 * 顶层字段码按目标层级既有码重新生成（避免同层唯一冲突）；子级列表整体随拷贝迁移、
 * 作用域为全新数组，层级内唯一性天然保持（明细行内可与主表同码的隔离口径不变）。
 */
export function cloneFieldForCopy(node, targetSiblingCodes) {
  const clone = deepClone(node)
  if (clone && typeof clone === 'object') {
    clone.field = generateFieldCode(clone.type, targetSiblingCodes)
  }
  return clone
}

/* ==================== 放置约束（§4.4 容器嵌套放置 + R04 明细行内仅数据类） ==================== */

/**
 * 校验放置合法性，返回 null（合法）或中文拦截原因。
 *
 * 约束口径：
 * - 明细表行内（targetKind='detailTable'）仅允许数据类组件（material.category==='DATA'，
 *   含行内 formula；R04 一期一层明细表）——拖入内容 / 布局 / 明细表组件拦截提示
 *   「明细表行内仅支持数据类组件」；
 * - 嵌套深度：新字段深度 = 父容器深度 + 1 ≤ MAX_NEST_DEPTH（顶层字段 = 1；分组 / 栅格
 *   相互嵌套一层受同一条约束，超限拦截提示）；
 * - 根级（targetKind='root'）无容器约束。
 */
export function validatePlacement(options = {}) {
  const { material, targetKind, parentEntry } = options
  if (!material) return '组件未注册，无法放置'
  if (targetKind === 'detailTable' && material.category !== 'DATA') {
    return '明细表行内仅支持数据类组件（R04：一期一层明细表）'
  }
  const parentDepth = parentEntry && Number.isFinite(parentEntry.depth) ? parentEntry.depth : 0
  if (parentDepth + 1 > MAX_NEST_DEPTH) {
    return `嵌套深度超过上限 ${MAX_NEST_DEPTH}（MAX_NEST_DEPTH），无法在该层级放置`
  }
  return null
}

/* ==================== 规则摘要文案（§4.6 规则列表「事件 + 条件摘要 + 动作摘要」） ==================== */

/** 条件值展示（数组 → 顿号串；字符串加引号；其余 String 化） */
function displayConditionValue(value) {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.map((item) => String(item)).join('、')
  if (typeof value === 'string') return `「${value}」`
  return String(value)
}

/** 条件摘要（§4.6）：null = 无条件规则；operator 十项中文标签 + value 展示 */
export function summarizeCondition(condition) {
  if (!condition || typeof condition !== 'object' || !condition.operator) return '无条件'
  const label = RULE_OPERATOR_LABELS[condition.operator] || condition.operator
  if (NO_VALUE_OPERATORS.includes(condition.operator)) return label
  return `${label} ${displayConditionValue(condition.value)}`
}

/** 动作摘要（§4.6）：动作类型中文标签 + 目标字段（值类动作附值形态） */
export function summarizeAction(action, targetLabels = {}) {
  if (!action || typeof action !== 'object') return ''
  const label = RULE_ACTION_LABELS[action.type] || action.type
  const target = action.target ? targetLabels[action.target] || action.target : '-'
  if (action.type === 'setValue') {
    const value = displayConditionValue(action.value)
    return value === '' ? `${label} → ${target}` : `${label} ${value} → ${target}`
  }
  if (action.type === 'setRequired') return `${label}（${action.value === true ? '必填' : '非必填'}）→ ${target}`
  if (action.type === 'setValidate') {
    const payload = action.validate && typeof action.validate === 'object' ? action.validate : {}
    const parts = []
    if (payload.min !== null && payload.min !== undefined) parts.push(`min=${payload.min}`)
    if (payload.max !== null && payload.max !== undefined) parts.push(`max=${payload.max}`)
    if (payload.pattern) parts.push(`pattern=${payload.pattern}`)
    if (payload.message) parts.push(`message=${payload.message}`)
    return parts.length > 0 ? `${label}（${parts.join('，')}）→ ${target}` : `${label} → ${target}`
  }
  return `${label} → ${target}`
}

/** 规则整行摘要：「事件 · 条件摘要 · 动作摘要1；动作摘要2」 */
export function summarizeRule(rule, targetLabels = {}) {
  if (!rule || typeof rule !== 'object') return ''
  const eventLabel = (RULE_EVENT_OPTIONS.find((item) => item.value === rule.event) || {}).label || rule.event
  const conditionText = summarizeCondition(rule.condition)
  const actions = Array.isArray(rule.actions) ? rule.actions : []
  const actionText = actions.map((action) => summarizeAction(action, targetLabels)).join('；')
  return `${eventLabel} · ${conditionText} · ${actionText || '（无动作）'}`
}

/* ==================== 画布上下文选项派生（属性面板 / 规则面板下拉） ==================== */

/**
 * 主表层级数据类字段选项（级联 parentField / 规则动作目标共用）：
 * 主表层级（containerType !== 'detailTable'）且物料 category=DATA（不含明细表容器，
 * 对齐 SysFormConstants.DATA_FIELD_TYPES 注释口径）；excludeCode 排除自身。
 */
export function buildDataFieldOptions(entries, getMaterial, excludeCode = null) {
  const options = []
  for (const entry of entries) {
    if (entry.containerType === 'detailTable') continue
    if (excludeCode !== null && entry.field === excludeCode) continue
    const material = getMaterial(entry.type)
    if (!material || material.category !== 'DATA') continue
    options.push({ code: entry.field, label: entry.label || entry.field })
  }
  return options
}

/**
 * 明细行内数值类字段选项（detailTable 统计行 summary.field，context='detailNumericFields'）：
 * 指定明细表行内 dataType=NUMBER 的字段码。
 */
export function buildDetailNumericOptions(entries, getMaterial, detailCode) {
  const options = []
  for (const entry of entries) {
    if (entry.containerType !== 'detailTable' || entry.parentField !== detailCode) continue
    const material = getMaterial(entry.type)
    if (!material || resolveMaterialDataType(material, entry.props) !== 'NUMBER') continue
    options.push({ code: entry.field, label: entry.label || entry.field })
  }
  return options
}

/**
 * 规则动作目标字段选项（§4.6：目标字段下拉——明细表触发源（onAdd/onDelete/明细行规则）
 * 的动作目标限行内字段码，其余取主表层级数据类字段）：
 * - selectedEntry 为明细行内字段（行内规则）→ 其宿主明细表行内数据类字段；
 * - selectedEntry 为明细表宿主（onAdd/onDelete 触发源）→ 其行内数据类字段；
 * - 其余 → 主表层级数据类字段。
 */
export function buildActionTargetOptions(entries, getMaterial, selectedEntry) {
  if (!selectedEntry) return []
  if (selectedEntry.containerType === 'detailTable') {
    return buildDetailRowDataOptions(entries, getMaterial, selectedEntry.parentField)
  }
  if (selectedEntry.type === 'detailTable') {
    return buildDetailRowDataOptions(entries, getMaterial, selectedEntry.field)
  }
  return buildDataFieldOptions(entries, getMaterial, selectedEntry.field)
}

/** 指定明细表行内数据类字段选项（R04：行内仅数据类组件） */
function buildDetailRowDataOptions(entries, getMaterial, detailCode) {
  const options = []
  for (const entry of entries) {
    if (entry.containerType !== 'detailTable' || entry.parentField !== detailCode) continue
    const material = getMaterial(entry.type)
    if (!material || material.category !== 'DATA') continue
    options.push({ code: entry.field, label: entry.label || entry.field })
  }
  return options
}

/** 全表规则 ID 集合（新增规则 ruleId 生成去重用，§3.1 ruleId 表单内唯一） */
export function collectRuleIds(entries) {
  const ids = []
  for (const entry of entries) {
    for (const rule of entry.rules) {
      if (rule && typeof rule === 'object' && typeof rule.ruleId === 'string' && rule.ruleId !== '') {
        ids.push(rule.ruleId)
      }
    }
  }
  return ids
}

/** 生成表单内唯一的规则 ID：rule_{序号} */
export function generateRuleId(existingIds) {
  const used = new Set(Array.isArray(existingIds) ? existingIds : [])
  let index = 1
  let id = `rule_${index}`
  while (used.has(id)) {
    index += 1
    id = `rule_${index}`
  }
  return id
}

/* ==================== Schema 导入导出（§4.8，自研轻量结构校验器，不引入 Ajv） ==================== */

/**
 * 构建导出载荷（§4.8 导出行）：草稿导出协议头 version 置 null（版本号仅发布动作分配），
 * 文件名 {formCode}.json；Schema 体积超 MAX_SCHEMA_JSON_LENGTH 提示（应用层口径，
 * 与保存期校验同常量）。
 *
 * @returns {{ payload?: object, fileName?: string, error?: string }}
 */
export function buildExportPayload(schema, formCode) {
  if (!schema || typeof schema !== 'object' || !Array.isArray(schema.fields)) {
    return { error: '画布 Schema 为空，无法导出' }
  }
  const payload = { ...schema, version: null }
  const text = JSON.stringify(payload)
  if (text.length > MAX_SCHEMA_JSON_LENGTH) {
    return {
      error: `Schema 序列化体积超过上限（${MAX_SCHEMA_JSON_LENGTH} 字符 / 512KB，MAX_SCHEMA_JSON_LENGTH），无法导出`,
    }
  }
  return { payload, fileName: `${formCode || 'form'}.json` }
}

/**
 * 导入轻量结构校验器（§4.8：JSON.parse + 协议头核对 + fields 递归 + type 白名单 +
 * 资源上限，逐条中文原因数组，对齐 CS_FORM_SCHEMA_INVALID 逐条原因形态）：
 * - 协议头：对象；formId 允许缺省（导入即新表单场景）；version 为整数或 null（草稿口径）；
 * - 字段：field 码格式与同层唯一、type 白名单（经注入 getMaterial 判定）、props 对象、
 *   rules 数组（event 六类 / operator 十项 / 动作六类 / 提交段禁值类动作）；
 * - 容器：group/detailTable children 为字段对象数组；grid children 为列对象
 *   { span, fields[] }；R04——明细表内禁任何容器与明细表、行内仅数据类组件；
 * - 资源上限：字段数 MAX_FIELD_COUNT / 嵌套深度 MAX_NEST_DEPTH / 规则数 MAX_RULE_COUNT /
 *   Schema 体积 MAX_SCHEMA_JSON_LENGTH。
 *
 * @param {string} text 粘贴的 JSON 文本
 * @param {{ getMaterial?: (type: string) => object|undefined }} options 物料访问器注入
 * @returns {{ ok: boolean, schema: object|null, errors: string[] }}
 */
export function validateSchemaImport(text, options = {}) {
  const errors = []
  const getMaterial = typeof options.getMaterial === 'function' ? options.getMaterial : () => undefined
  if (typeof text !== 'string' || text.trim() === '') {
    return { ok: false, schema: null, errors: ['导入内容为空，请粘贴 Schema JSON 文本'] }
  }
  if (text.length > MAX_SCHEMA_JSON_LENGTH) {
    errors.push(`Schema 体积超过上限 ${MAX_SCHEMA_JSON_LENGTH} 字符 / 512KB（MAX_SCHEMA_JSON_LENGTH）`)
  }
  let parsed
  try {
    parsed = JSON.parse(text)
  } catch (error) {
    return { ok: false, schema: null, errors: [`JSON 解析失败：${error && error.message ? error.message : '非法 JSON'}`] }
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return { ok: false, schema: null, errors: ['Schema 必须为 JSON 对象（协议头 + fields 字段数组）'] }
  }
  if (parsed.formId !== undefined && parsed.formId !== null && typeof parsed.formId !== 'string') {
    errors.push('协议头 formId 必须为字符串或缺省（导入即新表单场景允许缺省）')
  }
  for (const key of ['formCode', 'formName']) {
    if (parsed[key] !== undefined && parsed[key] !== null && typeof parsed[key] !== 'string') {
      errors.push(`协议头 ${key} 必须为字符串`)
    }
  }
  if (parsed.version !== undefined && parsed.version !== null && !Number.isInteger(parsed.version)) {
    errors.push('协议头 version 必须为整数版本号或 null（草稿导出口径）')
  }
  if (!Array.isArray(parsed.fields)) {
    errors.push('fields 必须为字段对象数组')
    return { ok: false, schema: null, errors }
  }

  const fieldCount = countFields(parsed.fields)
  if (fieldCount > MAX_FIELD_COUNT) {
    errors.push(`字段总数 ${fieldCount} 超过上限 ${MAX_FIELD_COUNT}（MAX_FIELD_COUNT）`)
  }
  const ruleCount = countRules(parsed.fields)
  if (ruleCount > MAX_RULE_COUNT) {
    errors.push(`规则总数 ${ruleCount} 超过上限 ${MAX_RULE_COUNT}（MAX_RULE_COUNT）`)
  }

  walkValidateList(parsed.fields, 'fields', 1, 'root', null, errors, getMaterial)
  return { ok: errors.length === 0, schema: parsed, errors }
}

/** 递归校验一层字段列表（同层字段码唯一 + 逐字段结构） */
function walkValidateList(list, pathBase, depth, containerKind, parentEntry, errors, getMaterial) {
  const usedCodes = new Set()
  const listArray = Array.isArray(list) ? list : []
  listArray.forEach((node, index) => {
    const path = `${pathBase}[${index}]`
    if (!node || typeof node !== 'object' || Array.isArray(node)) {
      errors.push(`${path}：字段必须为对象`)
      return
    }
    if (typeof node.field !== 'string' || !new RegExp(FIELD_CODE_PATTERN).test(node.field)) {
      errors.push(`${path}：字段码「${node.field === undefined ? '（缺失）' : node.field}」不符合约束（[a-zA-Z_][a-zA-Z0-9_]*）`)
    } else if (usedCodes.has(node.field)) {
      errors.push(`${path}：字段码「${node.field}」与同层级既有字段码重复（同一层级内必须唯一）`)
    } else {
      usedCodes.add(node.field)
    }
    const material = typeof node.type === 'string' ? getMaterial(node.type) : undefined
    if (!material) {
      errors.push(`${path}：组件 type「${node.type === undefined ? '（缺失）' : node.type}」不在组件白名单内（未注册物料）`)
      return
    }
    if (depth > MAX_NEST_DEPTH) {
      errors.push(`${path}：嵌套深度 ${depth} 超过上限 ${MAX_NEST_DEPTH}（MAX_NEST_DEPTH）`)
    }
    if (containerKind === 'detailTable') {
      if (isContainerType(node.type)) {
        errors.push(`${path}：明细表内禁止嵌套${node.type === 'detailTable' ? '明细表' : '容器组件'}（R04：一期一层明细表）`)
        return
      }
      if (material.category !== 'DATA') {
        errors.push(`${path}：明细表行内仅支持数据类组件（R04），「${node.type}」为 ${material.category} 类`)
      }
    }
    if (node.props !== undefined && node.props !== null && (typeof node.props !== 'object' || Array.isArray(node.props))) {
      errors.push(`${path}：props 必须为对象`)
    }
    if (node.rules !== undefined && !Array.isArray(node.rules)) {
      errors.push(`${path}：rules 必须为规则数组`)
    }
    if (Array.isArray(node.rules)) {
      node.rules.forEach((rule, ruleIndex) => {
        validateRuleImport(rule, `${path}.rules[${ruleIndex}]`, errors)
      })
    }
    if (node.type === 'group' || node.type === 'detailTable') {
      if (node.children !== undefined && !Array.isArray(node.children)) {
        errors.push(`${path}：${node.type === 'group' ? '分组' : '明细表'} children 必须为字段对象数组`)
        return
      }
      walkValidateList(Array.isArray(node.children) ? node.children : [], `${path}.children`, depth + 1, node.type, node, errors, getMaterial)
    } else if (node.type === 'grid') {
      if (node.children !== undefined && !Array.isArray(node.children)) {
        errors.push(`${path}：栅格 children 必须为列对象数组（{ span, fields[] }）`)
        return
      }
      ;(Array.isArray(node.children) ? node.children : []).forEach((column, columnIndex) => {
        const columnPath = `${path}.children[${columnIndex}]`
        if (!column || typeof column !== 'object' || Array.isArray(column)) {
          errors.push(`${columnPath}：栅格列必须为对象（{ span, fields[] }）`)
          return
        }
        if (column.span !== undefined && !Number.isFinite(Number(column.span))) {
          errors.push(`${columnPath}：栅格列 span 必须为数值（24 栅格制）`)
        }
        if (column.fields !== undefined && !Array.isArray(column.fields)) {
          errors.push(`${columnPath}：栅格列 fields 必须为字段对象数组`)
          return
        }
        walkValidateList(Array.isArray(column.fields) ? column.fields : [], `${columnPath}.fields`, depth + 1, 'grid', node, errors, getMaterial)
      })
    } else if (node.children !== undefined && node.children !== null) {
      errors.push(`${path}：非容器组件「${node.type}」不应携带 children`)
    }
  })
}

/** 单条规则轻量校验（事件六类 / 条件 operator 十项与 value 形态 / 动作六类 / 提交段禁值类动作） */
function validateRuleImport(rule, path, errors) {
  if (!rule || typeof rule !== 'object' || Array.isArray(rule)) {
    errors.push(`${path}：规则必须为对象`)
    return
  }
  const event = rule.event
  if (!RULE_EVENT_OPTIONS.some((item) => item.value === event)) {
    errors.push(`${path}：事件「${event === undefined ? '（缺失）' : event}」不在可配置事件全集内（onFocus/onBlur 一期不开放规则配置）`)
  }
  if (rule.condition !== undefined && rule.condition !== null) {
    const condition = rule.condition
    if (typeof condition !== 'object' || Array.isArray(condition) || !RULE_OPERATOR_LABELS[condition.operator]) {
      errors.push(`${path}：条件 operator 必须为十项全集之一（equal/notEqual/gt/gte/lt/lte/in/notIn/empty/notEmpty）`)
    } else if (MULTI_VALUE_OPERATORS.includes(condition.operator) && !Array.isArray(condition.value)) {
      errors.push(`${path}：operator「${condition.operator}」的 value 必须为数组`)
    } else if (['gt', 'gte', 'lt', 'lte'].includes(condition.operator) && condition.value !== null && condition.value !== undefined
      && typeof condition.value !== 'number' && typeof condition.value !== 'string') {
      errors.push(`${path}：operator「${condition.operator}」的 value 必须为数值或日期字符串`)
    }
  }
  if (!Array.isArray(rule.actions)) {
    errors.push(`${path}：actions 必须为动作数组`)
    return
  }
  if (rule.actions.length === 0) {
    errors.push(`${path}：actions 不能为空（规则至少配置一个动作）`)
  }
  const valueForbidden = VALUE_FORBIDDEN_EVENT_VALUES.includes(event)
  rule.actions.forEach((action, actionIndex) => {
    const actionPath = `${path}.actions[${actionIndex}]`
    if (!action || typeof action !== 'object' || Array.isArray(action)) {
      errors.push(`${actionPath}：动作必须为对象`)
      return
    }
    if (!RULE_ACTION_LABELS[action.type]) {
      errors.push(`${actionPath}：动作类型「${action.type === undefined ? '（缺失）' : action.type}」不在六类动作全集内（script 类动作 R02 一期不开放）`)
      return
    }
    if (valueForbidden && (action.type === 'setValue' || action.type === 'clearValue')) {
      errors.push(`${actionPath}：事件「${event}」禁止配置值类动作（setValue/clearValue，§3.4 提交段载荷约束）`)
    }
    if (typeof action.target !== 'string' || action.target === '') {
      errors.push(`${actionPath}：动作目标字段 target 必须为非空字符串`)
    }
    if (action.type === 'setValidate' && action.validate !== undefined && action.validate !== null
      && (typeof action.validate !== 'object' || Array.isArray(action.validate))) {
      errors.push(`${actionPath}：setValidate 的 validate 覆盖项必须为对象（min/max/pattern/message）`)
    }
  })
}

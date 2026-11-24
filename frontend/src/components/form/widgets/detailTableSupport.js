/**
 * 明细表组件纯逻辑辅助模块（F15-FE-007）
 *
 * 口径依据：docs/v1_5-自定义表单/自定义表单功能详细设计.md
 * - §2.1 明细表行结构（R04 一层）：载荷形态 = 扁平行对象数组 [{ 行内字段码: 值 }]，
 *   行内仅数据类组件（含行内 formula）；
 * - §1.3 detailTable 行：行数下限 rowMin / 行数上限 rowMax（缺省 MAX_DETAIL_ROWS=200）、
 *   统计行 summary[{field, fn: sum/avg/count}]；
 * - §3.4 onDelete：合计/统计刷新由统计行内置（响应式即时计算），不走规则引擎；
 * - §6.3（ADR-09）：行内公式预演值写入行数据仅供统计行与提交预览一致，保存时以
 *   服务端重算结果覆盖。
 *
 * 【纯 JS 约束】零 .vue import、零 Vue 依赖，可被 node 冒烟脚本直接加载断言；
 * 物料知识经入参注入的 getMaterial(type) 访问器（DetailTableWidget 传入
 * registry.js 的 getComponentMaterial）。
 */

/** 统计方式取值域（§1.3 detailTable summary.fn；与 materials/common.js propSchema options 同值） */
export const SUMMARY_FN_VALUES = ['sum', 'avg', 'count']

/** 统计方式中文标签（统计行展示用） */
const SUMMARY_FN_LABELS = { sum: '合计', avg: '平均', count: '计数' }

/** 统计值展示占位（无有效数值时） */
export const SUMMARY_EMPTY_MARK = '—'

/**
 * 行数组归一（脏形态容错）：非数组 → []；数组内仅保留普通对象行（非对象行由
 * 渲染器 validateFormData 行形态兜底报错，不在此抛异常）。
 * @param {*} value 明细表字段值
 * @returns {Array<object>} 归一后的行数组
 */
export function normalizeDetailRows(value) {
  if (!Array.isArray(value)) return []
  return value.filter((row) => row !== null && typeof row === 'object' && !Array.isArray(row))
}

/**
 * 生成空行对象（添加行 / rowMin 补行用）：对行内数据类子字段逐个执行物料
 * defaultValueGenerator 生成组件值，并经物料 serializer 归一为载荷形态（如
 * addressbook 双绑定 {fieldId,fieldName} → {ids,names}，§2.1 扁平行对象口径）。
 *
 * R04：行内仅数据类组件——非数据类子字段（内容/布局/嵌套明细表，结构校验器前置拒绝，
 * 此处渲染兜底）不产生行内键；type=formula 子字段生成 null（值以服务端重算为准，
 * ADR-09，前端预演值仅展示）。
 *
 * @param {Array} children 行内字段定义数组（Schema children）
 * @param {(type: string) => object|undefined} getMaterial 物料读取访问器
 * @returns {object} 空行对象 { 行内字段码: 载荷形态默认值 }
 */
export function buildDetailRow(children, getMaterial) {
  const row = {}
  const list = Array.isArray(children) ? children : []
  for (const child of list) {
    if (!child || typeof child !== 'object' || !child.field || !child.type) continue
    const material = typeof getMaterial === 'function' ? getMaterial(child.type) : null
    // R04 数据类判定：category=DATA 且 dataType 解析非 null（与渲染器 isDataField 同口径）
    if (!material || material.category !== 'DATA' || resolveMaterialDataType(material, child.props) === null) continue
    const generated = material.defaultValueGenerator(child.props && typeof child.props === 'object' ? child.props : {})
    row[child.field] = material.serializer(generated)
  }
  return row
}

/** 解析物料 dataType（string / (props)=>string / null 三形态；与 rendererSupport.resolveDataType 同语义） */
function resolveMaterialDataType(material, props) {
  const dataType = material.dataType
  if (typeof dataType === 'function') return dataType(props || {})
  return dataType === undefined ? null : dataType
}

/**
 * 行数下限补行：行数不足 rowMin 时补空行至 rowMin（初始化/回显时由 DetailTableWidget
 * 调用，对齐服务端 S2 行数下限校验语义——不足 rowMin 的载荷保存会被
 * FormDataBasicValidator 拒绝，前端补行使初始形态即可合法提交）。
 * 行数已达标时原数组原样返回（不产生新引用）。
 * @param {Array} rows 当前行数组
 * @param {number} rowMin 行数下限
 * @param {() => object} buildRow 空行工厂（buildDetailRow 包装）
 * @returns {Array} 补行后的行数组（未补行时返回原引用）
 */
export function padDetailRows(rows, rowMin, buildRow) {
  const list = Array.isArray(rows) ? rows : []
  const min = Number(rowMin)
  if (!Number.isFinite(min) || min <= 0 || list.length >= min) return list
  const padded = [...list]
  while (padded.length < min) {
    padded.push(typeof buildRow === 'function' ? buildRow() : {})
  }
  return padded
}

/**
 * 统计计算（统计行响应式消费，即时刷新不走规则引擎，§3.4 onDelete 口径）：
 * - sum/avg：仅累计有限数值（数值字符串容忍形态漂移、非数值/空值跳过）；无有效数值
 *   返回 null（展示层回落占位「—」）；
 * - count：计行数（行内值是否为空不影响计数）。
 * @param {Array} rows 当前行数组（扁平行对象数组）
 * @param {string} field 统计字段码（行内字段）
 * @param {string} fn 统计方式 sum/avg/count
 * @returns {number|null} 统计值（sum/avg 无有效数值为 null；count 恒为行数）
 */
export function calculateSummary(rows, field, fn) {
  const list = Array.isArray(rows) ? rows : []
  if (fn === 'count') return list.length
  if (fn !== 'sum' && fn !== 'avg') return null
  let total = 0
  let count = 0
  for (const row of list) {
    if (!row || typeof row !== 'object' || Array.isArray(row)) continue
    const value = row[field]
    let num = null
    if (typeof value === 'number' && Number.isFinite(value)) num = value
    else if (typeof value === 'string' && value.trim() !== '' && Number.isFinite(Number(value))) num = Number(value)
    if (num === null) continue
    total += num
    count++
  }
  if (count === 0) return null
  return fn === 'sum' ? total : total / count
}

/**
 * 统计值展示文本：消浮点噪声（二进制浮点求和尾巴，如 0.1+0.2）按 10 位小数截断后
 * 取最简形态；null/非有限值展示占位「—」。
 * @param {number|null} value 统计值
 * @returns {string} 展示文本
 */
export function formatSummaryValue(value) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return SUMMARY_EMPTY_MARK
  return String(Number(value.toFixed(10)))
}

/** 统计方式中文标签（未知方式回落原文） */
export function resolveSummaryLabel(fn) {
  return SUMMARY_FN_LABELS[fn] || String(fn || '')
}

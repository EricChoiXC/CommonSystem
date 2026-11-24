/**
 * 数字组件格式化/解析/钳制纯函数模块（F15-FE-003）
 *
 * 口径依据：docs/v1_5-自定义表单/自定义表单功能详细设计.md §1.3 number 行——
 * 数字类型 numberType（INTEGER/DECIMAL/PERCENT/AMOUNT）、精度 precision、最大值 max、
 * 最小值 min、允许负数 allowNegative、分隔符 separator（NONE/THOUSAND/TEN_THOUSAND/
 * HUNDRED_MILLION）；值形态 NUMBER（数值落库，格式化仅显示层）。
 *
 * 纯 JS 模块（无 Vue / 无 .vue import）：package.json type=module，可被 node 直接
 * import 冒烟断言；NumberWidget.vue 只做薄封装，格式化/解析/收口逻辑全部收口本文件。
 *
 * 语义定稿（§1.3 未细化处的实现口径，随任务报告登记）：
 * - 分隔符仅作用于整数部分的分组宽度：THOUSAND 每 3 位（千分位）、TEN_THOUSAND 每 4 位
 *   （万分位）、HUNDRED_MILLION 每 8 位（亿分位），分隔符字符统一为英文逗号「,」；
 * - PERCENT 百分号语义：值本身即百分数值（50 表示 50%），展示层补「%」后缀，
 *   解析时剥离「%」后缀，不做 ×100 换算（与 min/max/precision 校验同一数值基准）；
 * - AMOUNT 金额语义：展示层补人民币符号「¥」前缀，解析时剥离「¥」；
 * - 精度归一：numberType=INTEGER 恒按 0 位小数取整；其余按 precision 四舍五入
 *   （Math.round 语义，指数法规避二进制浮点误差，如 1.005 归一到 1.01）；
 * - 收口顺序：先精度归一、后 min/max/负数钳制（配置边界值为权威，允许边界值本身
 *   超出精度位）。
 */

/** 数字类型取值域（§1.3 number 行 numberType） */
export const NUMBER_TYPES = ['INTEGER', 'DECIMAL', 'PERCENT', 'AMOUNT']

/** 分隔符取值域（§1.3 number 行 separator） */
export const NUMBER_SEPARATORS = ['NONE', 'THOUSAND', 'TEN_THOUSAND', 'HUNDRED_MILLION']

/**
 * 生效精度：INTEGER 恒 0 位；precision 未配置（null/undefined/空串/非法值）返回 null
 * 表示不归一；其余按 Number 取非负整数（UI 分册 §4.5.2 精度取值域 0~6，属性面板约束）。
 * @param {object} props 数字组件 props（numberType/precision）
 * @returns {number|null} 生效小数位
 */
export function effectivePrecision(props) {
  const p = props || {}
  if (p.numberType === 'INTEGER') return 0
  const precision = p.precision
  if (precision === null || precision === undefined || precision === '') return null
  const num = Number(precision)
  if (!Number.isFinite(num) || num < 0) return null
  return Math.floor(num)
}

/**
 * 按精度四舍五入（Math.round 语义；指数法规避二进制浮点乘法误差）。
 * @param {number} num 数值
 * @param {number|null} precision 小数位（null 不处理）
 * @returns {number}
 */
export function roundToPrecision(num, precision) {
  if (typeof num !== 'number' || !Number.isFinite(num)) return num
  if (precision === null || precision === undefined) return num
  const p = Math.max(0, Math.floor(precision))
  if (p === 0) return Math.round(num)
  // 指数法：String(num) 形态不可用（如 1e+21，超出浮点可表达小数位）或结果溢出时，
  // 该量级下小数位本就无意义，原值返回
  const shifted = Number(`${num}e${p}`)
  if (!Number.isFinite(shifted)) return num
  const rounded = Number(`${Math.round(shifted)}e-${p}`)
  return Number.isFinite(rounded) ? rounded : num
}

/**
 * 按 min/max/allowNegative 钳制（§1.3 越界/负数收口）：
 * - 允许负数时下界 = min（未配置不设下界）；不允许负数时下界 = max(min, 0)
 *   （min 未配置按 0，min 为负时以 0 兜底——负数禁止优先于配置下界）；
 * - 上界 = max（未配置不设上界）；下界 > 上界（配置颠倒）时以后应用者（max）为准；
 * - 非有限数值原样返回（非法值处置归 normalizeNumber）。
 * @param {number} num 数值
 * @param {object} props 数字组件 props（min/max/allowNegative，容忍字符串数值形态）
 * @returns {number}
 */
export function clampToRange(num, props) {
  if (typeof num !== 'number' || !Number.isFinite(num)) return num
  const p = props || {}
  let lower = null
  if (p.min !== null && p.min !== undefined && p.min !== '') lower = Number(p.min)
  if (!p.allowNegative) lower = lower === null ? 0 : Math.max(lower, 0)
  let upper = null
  if (p.max !== null && p.max !== undefined && p.max !== '') upper = Number(p.max)
  let out = num
  if (lower !== null && out < lower) out = lower
  if (upper !== null && out > upper) out = upper
  return out
}

/**
 * 规范值归一：解析/原值 → 精度归一 → 钳制 → 消除 -0。
 * 空值（null/undefined/空串）与非数值形态统一归 null（值形态契约：空值恒 null）。
 * @param {number|string|null} value 字段值
 * @param {object} props 数字组件 props
 * @returns {number|null} 规范 NUMBER 值
 */
export function normalizeNumber(value, props) {
  const num = typeof value === 'number' ? value : parseNumber(value)
  if (num === null || !Number.isFinite(num)) return null
  let out = roundToPrecision(num, effectivePrecision(props))
  out = clampToRange(out, props)
  if (Object.is(out, -0)) out = 0
  return out
}

/**
 * 整数部分分组（自右起按 size 位插入逗号）：THOUSAND=3、TEN_THOUSAND=4、HUNDRED_MILLION=8。
 * @param {string} digits 无符号纯数字串
 * @param {number} size 分组宽度（0/负数不分组）
 * @returns {string}
 */
function groupDigits(digits, size) {
  if (!size || size <= 0 || digits.length <= size) return digits
  let out = ''
  let count = 0
  for (let i = digits.length - 1; i >= 0; i--) {
    out = digits[i] + out
    count++
    if (count % size === 0 && i > 0) out = ',' + out
  }
  return out
}

/**
 * 显示层格式化（§1.3：显示层格式化与输入解析分离，值形态恒 NUMBER）：
 * 规范值 → 小数位（精度未配置取最简形态）→ 整数分组 → PERCENT 补「%」/ AMOUNT 补「¥」。
 * 空值/非法值返回空串（输入框显示占位提示）。
 * @param {number|string|null} value 字段值
 * @param {object} props 数字组件 props（numberType/precision/separator）
 * @returns {string} 展示文本
 */
export function formatNumber(value, props) {
  const canonical = normalizeNumber(value, props)
  if (canonical === null) return ''
  const p = props || {}
  const precision = effectivePrecision(p)
  let text = precision === null ? String(canonical) : canonical.toFixed(precision)
  const separator = p.separator
  if (separator === 'TEN_THOUSAND' || separator === 'HUNDRED_MILLION' || separator === 'THOUSAND') {
    const size = separator === 'TEN_THOUSAND' ? 4 : separator === 'HUNDRED_MILLION' ? 8 : 3
    const dotIndex = text.indexOf('.')
    const intPart = dotIndex >= 0 ? text.slice(0, dotIndex) : text
    const decimalPart = dotIndex >= 0 ? text.slice(dotIndex) : ''
    const negative = intPart.startsWith('-')
    const digits = negative ? intPart.slice(1) : intPart
    text = (negative ? '-' : '') + groupDigits(digits, size) + decimalPart
  }
  if (p.numberType === 'PERCENT') text += '%'
  if (p.numberType === 'AMOUNT') text = '¥' + text
  return text
}

/**
 * 输入解析（脏输入容错，§1.3：去分隔符/修饰符回写数值）：剥离金额符号（¥/￥）、
 * 百分号（%/％）、分组逗号与空白，全角数字/小数点/正负号归一后按严格小数形态
 * 校验；不可解析返回 null（不抛异常）。只做「文本 → 数值」还原，不做钳制与精度
 * 归一（编辑中途不收口，失焦时经 normalizeNumber 收口）。
 * @param {string|number|null} text 输入文本
 * @returns {number|null} 解析结果
 */
export function parseNumber(text) {
  if (text === null || text === undefined) return null
  if (typeof text === 'number') return Number.isFinite(text) ? text : null
  let s = String(text).trim()
  if (s === '') return null
  // 全角数字（０-９）、全角小数点（．）、全角正负号（＋－）归一（0xFF01 起按 0xFEE0 平移）
  s = s.replace(/[０-９．＋－]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) - 0xfee0))
  // 剥离展示层修饰：金额符号 / 百分号 / 分组分隔符（含全角逗号）/ 空白
  s = s.replace(/[¥￥%％]/g, '').replace(/[,，]/g, '').replace(/\s+/g, '')
  if (s === '' || s === '+' || s === '-' || s === '.') return null
  // 严格小数形态（不支持科学计数法——表单手输不出现，按脏输入拒绝）
  if (!/^[+-]?(\d+(\.\d*)?|\.\d+)$/.test(s)) return null
  const num = Number(s)
  return Number.isFinite(num) ? num : null
}

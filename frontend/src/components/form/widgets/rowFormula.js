/**
 * 明细行内公式本地预演纯函数模块（F15-FE-007）
 *
 * 口径依据：docs/v1_5-自定义表单/自定义表单功能详细设计.md
 * - §6.3 公式前后端分工（ADR-09）：前端没有 AviatorScript，无法复算含 COLLECTION /
 *   IF / DS_QUERY 等函数的公式——**行内四则可本地预演、主表合计等无法保证、服务端权威**；
 *   预演值仅展示性质，保存时以服务端重算结果覆盖落库（FormFormulaEvaluator S6），
 *   前端差额不报错；
 * - §2.1 字段码约束：`${code}` 变量引用形态，行内字段码与主表字段码可同码——
 *   行上下文求值时**行内同码优先、主表回落**；
 * - §1.3 formula 行：表达式 `${code}` 形态 + 期望返回类型（本模块仅预演 NUMBER 语义）。
 *
 * 【能力边界（一期定稿）】仅支持行内四则本地预演：
 *   - 数字字面量、+ - * / 四则与括号、一元正负号；
 *   - ROUND(x[, digits]) 内置函数（四舍五入语义与 widgets/numberFormat.js 精度归一
 *     一致：Math.round 指数法，规避 1.005 二进制浮点误差）；
 *   - `${行内字段码}` 行内引用与 `${主表字段码}` 主表引用（数值/数值字符串，脏值容错）；
 *   - 其余函数（SUM/COLLECTION/DS_QUERY/IF 等）一律不求值——返回不可求值结果，由
 *     单元格展示占位提示「以服务端重算为准」（FormulaWidget 空值文案承载）。
 *
 * 【安全红线（要点文档安全建议 / 功能设计 §3.7 二期红线同源）】本模块不使用 eval、
 *   不使用 new Function 等任何动态代码执行通道——表达式经自研词法分析 + 递归下降
 *   解析求值，不可解析即失败回落，绝不执行任意文本。
 *
 * 【纯 JS 约束】零 .vue import、零 Vue 依赖，可被 node 冒烟脚本直接加载断言；
 * 上下文经入参注入（rowValues 行字段值对象 + getMainValue 主表字段取值函数）。
 */

/** 表达式长度上限（防御性，超长表达式视为不可预演；正常行内公式远小于该值） */
export const ROW_FORMULA_MAX_LENGTH = 500

/** 本地预演支持的内置函数白名单（一期仅 ROUND；其余函数回落服务端重算） */
export const SUPPORTED_ROW_FUNCTIONS = ['ROUND']

/** `${字段码}` 引用形态（与 rules/evaluators.js FIELD_REFERENCE_PATTERN 同语义，行内整值引用；仅起始锚定——词法按消费位置切片匹配，引用可出现在表达式任意位置） */
const REFERENCE_PATTERN = /^\$\{([a-zA-Z_][a-zA-Z0-9_]*)\}/

/* ==================== 词法分析 ==================== */

const TOKEN_NUMBER = 'number'
const TOKEN_IDENT = 'ident'
const TOKEN_REFERENCE = 'reference'
const TOKEN_OPERATOR = 'operator'
const TOKEN_LPAREN = 'lparen'
const TOKEN_RPAREN = 'rparen'
const TOKEN_COMMA = 'comma'

/**
 * 词法分析：表达式 → token 数组；遇到非法字符返回 null（不可预演）。
 * 支持：数字字面量（整数/小数，不支持科学计数法——表单公式不出现）、
 * 标识符（函数名）、`${字段码}` 引用、+ - * / ( ) , 与空白。
 */
function tokenize(expression) {
  const tokens = []
  let index = 0
  const length = expression.length
  while (index < length) {
    const char = expression[index]
    if (char === ' ' || char === '\t' || char === '\n' || char === '\r') {
      index++
      continue
    }
    // ${字段码} 引用（先于其他分支判定，避免 $ 落入非法字符；rest 以 $ 起始，起始锚定取首个引用）
    if (char === '$') {
      const rest = expression.slice(index)
      const matched = REFERENCE_PATTERN.exec(rest)
      if (!matched) return null
      tokens.push({ type: TOKEN_REFERENCE, code: matched[1] })
      index += matched[0].length
      continue
    }
    // 数字字面量（不支持科学计数法与正负号前缀——符号按一元运算符解析）
    if ((char >= '0' && char <= '9') || char === '.') {
      const numberPattern = /^(\d+(\.\d*)?|\.\d+)/
      const matched = expression.slice(index).match(numberPattern)
      if (!matched) return null
      tokens.push({ type: TOKEN_NUMBER, value: Number(matched[0]) })
      index += matched[0].length
      continue
    }
    // 标识符（函数名）
    if (/[a-zA-Z_]/.test(char)) {
      const identPattern = /^[a-zA-Z_][a-zA-Z0-9_]*/
      const matched = expression.slice(index).match(identPattern)
      if (!matched) return null
      tokens.push({ type: TOKEN_IDENT, name: matched[0] })
      index += matched[0].length
      continue
    }
    if (char === '(') {
      tokens.push({ type: TOKEN_LPAREN })
      index++
      continue
    }
    if (char === ')') {
      tokens.push({ type: TOKEN_RPAREN })
      index++
      continue
    }
    if (char === ',') {
      tokens.push({ type: TOKEN_COMMA })
      index++
      continue
    }
    if (char === '+' || char === '-' || char === '*' || char === '/') {
      tokens.push({ type: TOKEN_OPERATOR, operator: char })
      index++
      continue
    }
    // 非法字符（含 ${ 形态不完整、中文、其他符号）→ 不可预演
    return null
  }
  return tokens
}

/* ==================== 递归下降解析 + 求值 ==================== */

/**
 * 求值上下文：
 * - rowValues：当前行字段值对象 { 行内字段码: 值 }（扁平行对象，§2.1）；
 * - getMainValue：(主表字段码) => 值，主表字段取值函数（明细表 widget 经渲染器注入）。
 */
function createContext(rowValues, getMainValue) {
  return {
    row: rowValues && typeof rowValues === 'object' && !Array.isArray(rowValues) ? rowValues : {},
    getMain: typeof getMainValue === 'function' ? getMainValue : () => undefined,
  }
}

/**
 * 引用取值：行内同码优先、主表回落（§2.1 行上下文隔离 + §6.3 行内公式变量表）。
 * 值仅接受有限数值与数值字符串（number 组件容忍形态漂移同口径）；其余（含布尔、
 * 文本、对象、空值）按不可求值处理返回 null。
 */
function resolveReferenceValue(code, context) {
  let value
  if (Object.prototype.hasOwnProperty.call(context.row, code)) {
    value = context.row[code]
  } else {
    value = context.getMain(code)
  }
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value === 'string' && value.trim() !== '') {
    const num = Number(value)
    return Number.isFinite(num) ? num : null
  }
  return null
}

/** ROUND 四舍五入（与 widgets/numberFormat.js roundToPrecision 同语义：Math.round 指数法） */
function roundHalfUp(value, digits) {
  const p = Math.max(0, Math.floor(digits))
  if (p === 0) return Math.round(value)
  const shifted = Number(`${value}e${p}`)
  if (!Number.isFinite(shifted)) return value
  const rounded = Number(`${Math.round(shifted)}e-${p}`)
  return Number.isFinite(rounded) ? rounded : value
}

/** 内置函数求值（白名单外返回 null=不可预演）；参数个数/类型不配亦不可预演 */
function callFunction(name, args) {
  if (name === 'ROUND') {
    if (args.length !== 1 && args.length !== 2) return null
    const value = args[0]
    const digits = args.length === 2 ? args[1] : 0
    if (value === null || digits === null) return null
    if (digits < 0 || digits > 10) return null
    return roundHalfUp(value, digits)
  }
  // 白名单外函数（SUM/COLLECTION/DS_QUERY/IF 等）：本地预演不支持，回落服务端重算
  return null
}

/**
 * 解析并求值（递归下降，Grammar）：
 *   expr   := term (('+'|'-') term)*
 *   term   := factor (('*'|'/') factor)*
 *   factor := NUMBER | REFERENCE | IDENT '(' args? ')' | '(' expr ')' | ('+'|'-') factor
 * 求值失败（语法错误 / 不可求值引用 / 白名单外函数 / 除零）返回 null。
 */
function evaluateTokens(tokens, context) {
  let pos = 0

  function peek() {
    return tokens[pos]
  }

  function parseExpr() {
    let value = parseTerm()
    if (value === null) return null
    while (peek() && peek().type === TOKEN_OPERATOR && (peek().operator === '+' || peek().operator === '-')) {
      const operator = tokens[pos++].operator
      const right = parseTerm()
      if (right === null) return null
      value = operator === '+' ? value + right : value - right
    }
    return value
  }

  function parseTerm() {
    let value = parseFactor()
    if (value === null) return null
    while (peek() && peek().type === TOKEN_OPERATOR && (peek().operator === '*' || peek().operator === '/')) {
      const operator = tokens[pos++].operator
      const right = parseFactor()
      if (right === null) return null
      if (operator === '/') {
        // 除零不可预演（服务端 AviatorScript 语义同为错误，回落重算）
        if (right === 0) return null
        value = value / right
      } else {
        value = value * right
      }
    }
    return value
  }

  function parseFactor() {
    const token = peek()
    if (!token) return null
    if (token.type === TOKEN_NUMBER) {
      pos++
      return token.value
    }
    if (token.type === TOKEN_REFERENCE) {
      pos++
      return resolveReferenceValue(token.code, context)
    }
    if (token.type === TOKEN_IDENT) {
      pos++
      if (!peek() || peek().type !== TOKEN_LPAREN) return null
      pos++
      const args = []
      // 空参数表：ROUND() 视为参数个数不配（求值失败）
      if (peek() && peek().type === TOKEN_RPAREN) {
        pos++
        return callFunction(token.name, args)
      }
      while (true) {
        const arg = parseExpr()
        if (arg === null) return null
        args.push(arg)
        if (peek() && peek().type === TOKEN_COMMA) {
          pos++
          continue
        }
        break
      }
      if (!peek() || peek().type !== TOKEN_RPAREN) return null
      pos++
      return callFunction(token.name, args)
    }
    if (token.type === TOKEN_LPAREN) {
      pos++
      const value = parseExpr()
      if (value === null) return null
      if (!peek() || peek().type !== TOKEN_RPAREN) return null
      pos++
      return value
    }
    // 一元正负号
    if (token.type === TOKEN_OPERATOR && (token.operator === '-' || token.operator === '+')) {
      pos++
      const value = parseFactor()
      if (value === null) return null
      return token.operator === '-' ? -value : value
    }
    return null
  }

  const result = parseExpr()
  if (result === null) return null
  // 表达式必须整体消费完毕（残留 token 视为语法错误）
  if (pos !== tokens.length) return null
  if (!Number.isFinite(result)) return null
  return result
}

/**
 * 行内公式本地预演（显示专用，ADR-09 预演性质）。
 *
 * @param {string} expression 公式表达式（`${code}` 形态，取自行内 formula 子字段 props.expression）
 * @param {object} context 求值上下文 { rowValues: 当前行字段值对象, getMainValue: (主表字段码) => 值 }
 * @returns {{ ok: boolean, value: number|null, reason: string|null }}
 *          ok=true 时 value 为预演数值；ok=false 时 value=null、reason 为中文原因
 *          （调用方展示占位「以服务端重算为准」，不阻断渲染、不报错）。
 */
export function evaluateRowFormula(expression, context) {
  if (typeof expression !== 'string' || expression.trim() === '') {
    return { ok: false, value: null, reason: '未配置公式' }
  }
  if (expression.length > ROW_FORMULA_MAX_LENGTH) {
    return { ok: false, value: null, reason: '公式过长，仅支持行内四则本地预演，以服务端重算为准' }
  }
  const tokens = tokenize(expression)
  if (!tokens || tokens.length === 0) {
    return { ok: false, value: null, reason: '公式语法不可解析，以服务端重算为准' }
  }
  const value = evaluateTokens(tokens, createContext(context && context.rowValues, context && context.getMainValue))
  if (value === null) {
    return {
      ok: false,
      value: null,
      reason: '公式含行内四则之外的函数或不可求值引用，仅支持行内四则与 ROUND 本地预演，以服务端重算为准',
    }
  }
  return { ok: true, value, reason: null }
}

/**
 * 行内公式预演便捷入口（DetailTableWidget 消费形态）：返回预演数值，不可求值返回 null
 * （单元格回落 FormulaWidget 空值占位「暂无计算值，以服务端重算为准」）。
 *
 * @param {string} expression 公式表达式
 * @param {object} row 当前行字段值对象（扁平行对象，§2.1）
 * @param {(code: string) => *} [getMainValue] 主表字段取值函数（渲染器注入；缺省仅行内上下文）
 * @returns {number|null} 预演值（不可求值为 null）
 */
export function resolveRowFormulaValue(expression, row, getMainValue) {
  const result = evaluateRowFormula(expression, { rowValues: row, getMainValue })
  return result.ok ? result.value : null
}

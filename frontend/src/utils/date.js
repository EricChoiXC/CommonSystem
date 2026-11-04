/**
 * 日期展示工具
 *
 * 后端 java.util.Date 字段序列化为毫秒时间戳，直接展示会带出时间信息。
 * 本工具将日期值统一格式化为仅含日期的 YYYY-MM-DD，供「日期」类字段使用。
 */

/**
 * 格式化为 YYYY-MM-DD（仅日期，不展示时间）。
 * 支持毫秒时间戳、ISO 字符串、YYYY-MM-DD 等日期字符串；空值返回 '-'。
 *
 * @param {number|string|Date} value 日期值
 * @returns {string} YYYY-MM-DD 或 '-'（无值）/ 原值（无法解析）
 */
export function formatDate(value) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  // 纯日期字符串（含 YYYY-MM-DD 前缀）直接截取，避免时区偏移导致跨天
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value))
  if (match) {
    return `${match[1]}-${match[2]}-${match[3]}`
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

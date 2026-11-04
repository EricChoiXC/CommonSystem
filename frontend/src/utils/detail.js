/**
 * 详情页网格字段构建工具
 *
 * 详情网格（styles/form-page.css 的 .detail-grid）为一行两列 flex 布局：
 * - 普通字段占 50%（一行两个，label:内容 = 2:3）
 * - 长内容字段标记 full，占 100% 整行（label:内容 = 2:8）
 *
 * 当普通字段数量为奇数、其后的整行字段换行时，会出现「孤立半格 + 整行字段」
 * 的排列，导致该行右侧边框断裂、网格显示错位。本函数在整行字段之前
 * （以及网格结尾）自动补齐空单元格，保证边框连续。
 *
 * @param {Array<{ label: string, value: any, full?: boolean, tag?: boolean, tagType?: string }>} fields 详情字段数组
 * @returns {Array} 补位后的字段数组；补位项为 { placeholder: true }
 */
export function buildDetailGridFields(fields) {
  const result = []
  let halfCount = 0
  for (const field of fields) {
    if (field.full) {
      if (halfCount % 2 === 1) {
        // 整行字段前存在孤立半格：补一个空单元格
        result.push({ placeholder: true })
      }
      halfCount = 0
    } else {
      halfCount++
    }
    result.push(field)
  }
  if (halfCount % 2 === 1) {
    // 网格结尾存在孤立半格：同样补位
    result.push({ placeholder: true })
  }
  return result
}

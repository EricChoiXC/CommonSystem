/**
 * 公共 blob 下载工具
 *
 * 对应 docs/v1_1/04-导入导出/导入导出公共方案设计.md 第 8.3 节：
 * 抽取 Attachment.vue#saveBlob 与 I18nTranslationManageView.vue#handleExport
 * 的重复实现，统一「解析文件名 + 临时 <a> 触发保存」的公共下载口径。
 */

/**
 * 从 AxiosResponse 响应头解析下载文件名（Content-Disposition）
 *
 * 解析顺序（RFC 6266 / RFC 5987 口径）：
 * 1. filename*=UTF-8''xxx —— URL 编码的 UTF-8 文件名，需 decodeURIComponent 解码；
 * 2. filename="xxx" —— 普通 filename，原样取出（去掉包裹引号）；
 * 3. 均解析不到时回退 fallbackName。
 *
 * @param {string} disposition Content-Disposition 响应头原文
 * @param {string} fallbackName 解析不到时的回退文件名
 * @returns {string} 下载文件名
 */
function resolveFilename(disposition, fallbackName) {
  if (!disposition) {
    return fallbackName
  }
  // filename*=UTF-8''xxx（RFC 5987：编码语言集''URL 编码值）——优先级最高
  const utf8Match = /filename\*\s*=\s*([^;]+)/i.exec(disposition)
  if (utf8Match) {
    const value = utf8Match[1].trim()
    // 形如 UTF-8''%E5%AF%BC%E5%87%BA.xlsx，取 '' 之后的 URL 编码部分解码
    const encoded = value.includes("''") ? value.slice(value.indexOf("''") + 2) : value
    try {
      const decoded = decodeURIComponent(encoded.replace(/^\"|\"$/g, '').trim())
      if (decoded) {
        return decoded
      }
    } catch (e) {
      // URI 序列非法时跳过 filename*，继续尝试普通 filename
    }
  }
  // filename="xxx" —— 普通引号包裹形式；也兼容无引号裸值
  const plainMatch = /filename\s*=\s*"?([^";]+)"?/i.exec(disposition)
  if (plainMatch) {
    const name = plainMatch[1].trim()
    if (name) {
      return name
    }
  }
  return fallbackName
}

/**
 * 触发浏览器保存 Blob 文件（导入导出 / 附件下载等文件流响应用）
 *
 * 从 response.headers 的 Content-Disposition 解析后端指定的下载文件名，
 * 解析不到时回退 fallbackName；创建临时 <a> 触发保存，保存后
 * URL.revokeObjectURL 释放对象 URL。
 *
 * @param {import('axios').AxiosResponse} response 文件流响应（response.data 为 Blob，
 *        Content-Disposition 由后端按 4.2 节契约返回 filename*=UTF-8''xxx 形式）
 * @param {string} fallbackName Content-Disposition 解析不到文件名时的回退名称
 */
export function downloadBlob(response, fallbackName) {
  const disposition =
    (response && response.headers && (response.headers['content-disposition'] || response.headers['Content-Disposition'])) || ''
  const filename = resolveFilename(disposition, fallbackName || 'download')
  const blob = response && response.data
  if (!(blob instanceof Blob)) {
    return
  }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

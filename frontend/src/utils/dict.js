import { queryDictItemsByCode } from '@/api/dict'

/**
 * 码值获取工具
 *
 * 统一从 dict 模块（/backend/sys/dict/items，按 typeCode 获取启用码值项）加载
 * 前端各页面/组件所需的码值下拉选项与文本映射，替代原先散落在各页面与 api 文件中的
 * 硬编码码值（详见 artificial/docs/设计文档/sys/dict/码值功能核心业务.md）。
 *
 * - 带 Promise 缓存：同一 typeCode 只请求一次，并发调用共享同一请求；
 * - 请求失败会清除缓存允许下次重试，页面侧自行降级（空选项 / 原值回退）；
 * - 码值 value 在库中为字符串；数字字段（如 org_element_type）传 { number: true }，
 *   布尔字段（如 field_enable）传 { boolean: true }（'1' → true）。
 */

/** 码值选项缓存：cacheKey -> Promise<{ value, label }[]> */
const OPTIONS_CACHE = new Map()

/**
 * 加载码值下拉选项（带 Promise 缓存）。
 *
 * @param {string} typeCode 码值类型编号（sys_dict_type.field_code，如 notify_type / yes_no）
 * @param {Object} [opts] { number: true } value 转 Number；{ boolean: true } value 转 Boolean
 * @returns {Promise<Array<{ value: string|number|boolean, label: string }>>} 选项数组；接口失败返回 []
 */
export function getDictOptions(typeCode, opts = {}) {
  const cacheKey = `${typeCode}|${opts.number ? 'n' : ''}${opts.boolean ? 'b' : ''}`
  if (!OPTIONS_CACHE.has(cacheKey)) {
    OPTIONS_CACHE.set(cacheKey, fetchOptions(typeCode, opts, cacheKey))
  }
  return OPTIONS_CACHE.get(cacheKey)
}

/**
 * 加载码值文本映射（带 Promise 缓存）：{ value: label }，供行内状态文本展示。
 *
 * @param {string} typeCode 码值类型编号
 * @param {Object} [opts] 同 getDictOptions
 * @returns {Promise<Object>} 文本映射；接口失败返回 {}
 */
export function getDictTextMap(typeCode, opts = {}) {
  return getDictOptions(typeCode, opts).then((list) => {
    const map = {}
    list.forEach((item) => {
      map[item.value] = item.label
    })
    return map
  })
}

/**
 * 码值文本渲染：按 typeCode 翻译单个值，未命中或空值回退原值（无值显示 '-'）。
 * 供列表行/详情页文本展示使用；映射加载失败时同样回退原值，不阻塞展示。
 *
 * @param {string} typeCode 码值类型编号
 * @param {*} value 码值（字符串/数字/布尔）
 * @param {Object} [opts] 同 getDictOptions
 * @returns {Promise<*>} 翻译后的文本
 */
export async function dictText(typeCode, value, opts = {}) {
  if (value == null || value === '') {
    return '-'
  }
  const map = await getDictTextMap(typeCode, opts)
  return map[value] ?? value
}

/**
 * 请求码值项并转换为选项数组。
 * <p>请求失败时清除缓存（允许下次重新请求）并返回空数组，
 * 页面侧无需 catch 即可安全使用空选项。</p>
 */
function fetchOptions(typeCode, opts, cacheKey) {
  return queryDictItemsByCode(typeCode)
    .then((res) => {
      const items = (res && res.data) || []
      return items.map((item) => ({
        value: convertValue(item.fieldValue, opts),
        label: item.fieldName
      }))
    })
    .catch(() => {
      OPTIONS_CACHE.delete(cacheKey)
      return []
    })
}

/**
 * 按 opts 转换码值类型：number / boolean，默认保持库中字符串原样。
 */
function convertValue(raw, opts) {
  if (opts.boolean) {
    return raw === '1' || raw === 1 || raw === true
  }
  if (opts.number) {
    const num = Number(raw)
    return Number.isNaN(num) ? raw : num
  }
  return raw
}

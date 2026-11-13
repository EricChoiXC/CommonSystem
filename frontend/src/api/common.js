import request from './index'

/**
 * 通用接口封装
 *
 * 遵循 docs/开发规范.md「前端新建页/弹窗 fieldId 初始化规范」：
 * 新建页面/新建弹窗打开时必须先调用 getInitID() 获取 32 位主键 ID，
 * 并赋值到表单对象的 id 字段上，后续 save 请求将该 ID 提交给后端。
 */

/**
 * 预取 32 位主键 ID
 * @returns {Promise<Object>} BaseResponse，data 为 32 位 ID 字符串
 */
export function getInitID() {
  return request.get('/backend/common/initID')
}

/**
 * 读取系统初始化信息（应用名称等公共展示数据）
 * 应用名称取后端 application.properties 的 application.name，为空时后端回退默认应用名。
 * @returns {Promise<Object>} BaseResponse，data 为 { applicationName }
 */
export function getInitInfo() {
  return request.get('/backend/common/init')
}

// ==================== 导入导出公共接口 ====================
// 对应 docs/v1_1/04-导入导出/导入导出公共方案设计.md 第 8.1 节：
// 页面只 import 本文件具名函数并传完整路径（如 /backend/sys/dict/export），
// 不写裸 axios / 裸 URL；blob 请求走主实例（拦截器已做文件流透传分支）。

// 主 axios 实例的模块内引用别名：exportExcel / importTemplate 的第二参数名固定为 request
// （BaseRequest 包装体，契约见方案设计 8.1），为避免参数遮蔽主实例，新函数统一经 http 调用
const http = request

/**
 * 导出 Excel（文件流）
 *
 * POST {path}，请求体为 BaseRequest 包装 { body, params, pages }（条件与 /list 同构，
 * 导出恒为全量，pages 可省略）；responseType: 'blob'，响应为 xlsx 文件流，
 * 下载请配合 utils/download.js#downloadBlob 解析 Content-Disposition 文件名后保存。
 * 对应方案设计 8.1 节「导出」与 4.2 节报文契约。
 *
 * @param {string} path 导出接口完整路径（如 /backend/sys/dict/export）
 * @param {Object} request BaseRequest 包装请求体 { body, params, pages }
 * @returns {Promise<import('axios').AxiosResponse>} 完整 AxiosResponse，response.data 为 Blob
 */
export function exportExcel(path, request) {
  return http.post(path, request, { responseType: 'blob' })
}

/**
 * 导入模板下载（文件流）
 *
 * POST {path}/template，请求体 { body, params }（与 /export 同构，模板可按筛选条件
 * 附带值域提示行，默认仅标题行）；responseType: 'blob'，响应为仅含标题行的空模板
 * xlsx 文件流，下载请配合 utils/download.js#downloadBlob 保存。
 * 对应方案设计 8.1 节「导入模板下载」与 4.3 节报文契约。
 *
 * @param {string} path 导出接口完整路径（模板接口为 {path}/template，由本函数拼接）
 * @param {Object} request BaseRequest 包装请求体 { body, params }
 * @returns {Promise<import('axios').AxiosResponse>} 完整 AxiosResponse，response.data 为 Blob
 */
export function importTemplate(path, request) {
  return http.post(`${path}/template`, request, { responseType: 'blob' })
}

/**
 * Excel 导入（multipart/form-data）
 *
 * FormData 字段：file（导入 xlsx 文件，必填）+ params（导入配置 JSON 字符串，可选，
 * 公共开关 ignoreError / fullUpdate 及业务自定义键，见方案设计 6.3 节）。
 * 响应为 BaseResponse<ImportResultVo>（data 含 total / success / failed / errors），
 * 由主实例统一解包，调用方直接消费 res.data。
 * 对应方案设计 8.1 节「导入」与 4.1 节报文契约。
 *
 * @param {string} path 导入接口完整路径（如 /backend/sys/dict/import）
 * @param {File} file 导入的 xlsx 文件（已由前端校验扩展名与大小）
 * @param {Object} [params] 导入配置对象（忽略错误 / 全量更新 / 业务自定义键），JSON 序列化后提交
 * @returns {Promise<Object>} BaseResponse，data 为 ImportResultVo { total, success, failed, errors }
 */
export function importExcel(path, file, params) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('params', JSON.stringify(params || {}))
  return http.post(path, formData)
}

// ==================== 模块组接口开关 ====================
// 对应 docs/v1_1-common/05-增加接口开关/接口开关机制UI设计分册.md 第 1、6、8 节：
// 管理员可整体启停一组接口；命中已关闭模块组的请求由响应拦截器统一提示 MODULE_DISABLED。

/**
 * 模块组列表
 * POST /backend/common/module/list，list 通道请求体 { body, pages }；模块组量极小
 * 默认全量返回（后端按 field_key 排序），metadata 不强制携带 total。
 * @returns {Promise<Object>} BaseResponse，data 为模块组列表
 *   （CommonModule：fieldId / fieldKey / fieldName / fieldEnable / fieldRemark / fieldUpdateTime）
 */
export function listModules() {
  return request.post('/backend/common/module/list', {
    body: {},
    pages: { pageNo: 1, pageSize: 100, needPage: false }
  })
}

/**
 * 启停用模块组
 * PATCH /backend/common/module/enable，请求体 { fieldId, fieldEnable }；
 * 仅 SYS_MODULE_ADMIN（模块组管理员）可调用，SYS_ADMIN 自动放行。
 * @param {Object} data { fieldId, fieldEnable }，fieldEnable 为目标状态 true 开启 / false 关闭
 * @returns {Promise<Object>} BaseResponse
 */
export function updateModuleEnable(data) {
  return request.patch('/backend/common/module/enable', data)
}

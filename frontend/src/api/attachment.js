import request, { handleUnauthorized } from './index'
import axios from 'axios'

/**
 * 系统模块 - 附件接口封装
 *
 * 对应后端 SysAttMainController：/backend/sys/att/main/**
 * - upload / download / view / delete 为公共附件组件接口（后端已实现）；
 * - list 当前为后台管理接口（需 SYS_ATT_MAIN 权限），公共组件所需的
 *   按 modelId / modelName / key 查询接口待后端补充，路径先复用 /list。
 */

// 登录凭证存储键（与 stores/user.js 保持一致）
const TOKEN_KEY = 'common_token'

/** 文件流请求实例：返回 Blob，不做 BaseResponse 解包（下载/预览用） */
const blobRequest = axios.create({
  timeout: 60000,
  responseType: 'blob'
})
blobRequest.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers['X-Login-Token'] = token
  }
  return config
})
// 响应拦截：凭证失效（HTTP 401 / 业务码 UNAUTHORIZED）时复用统一处理，自动跳转登录页
blobRequest.interceptors.response.use(
  (response) => {
    const data = response.data
    // responseType=blob 时后端业务失败（HTTP 200 + JSON）也以 Blob 返回，按 Content-Type 识别
    if (data instanceof Blob && data.type && data.type.includes('application/json')) {
      return data
        .text()
        .then((text) => {
          let res = null
          try {
            res = JSON.parse(text)
          } catch (e) {
            // 非 JSON 内容，按普通文件返回
            return response
          }
          if (res && res.code === 'UNAUTHORIZED') {
            handleUnauthorized(res.message)
            return Promise.reject(new Error(res.message || '登录已过期，请重新登录'))
          }
          return response
        })
    }
    return response
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      handleUnauthorized()
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }
    return Promise.reject(error)
  }
)

/**
 * 附件列表查询（后台管理）：按模块名 + 文档 id + 关键字 + 有效性过滤，
 * 支持单条件排序（createTime / download / view）。
 * @param {Object} data { body: { fieldName, fieldModelName, fieldModelId, fieldKey, fieldEnable }, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }
 * @returns {Promise<Object>} BaseResponse，data 为附件记录数组，总数经 metadata.total 返回
 */
export function queryAttachmentList(data) {
  return request.post('/backend/sys/att/main/list', data)
}

/**
 * 附件列表查询（公共组件）：按模块名 + 文档 id + 关键字查询启用附件，
 * 登录且账号启用即可访问（不要求 SYS_ATT_MAIN），固定按创建时间升序。
 * @param {Object} data { body: { fieldModelName, fieldModelId, fieldKey }, pages: { pageNo, pageSize, needPage } }
 * @returns {Promise<Object>} BaseResponse，data 为附件记录数组，总数经 metadata.total 返回
 */
export function queryComponentAttachmentList(data) {
  return request.post('/backend/sys/att/main/componentList', data)
}

/**
 * 文件列表查询（后台管理）：对 sys_att_file 查询，核心信息为文件类型、文件大小、保存路径、是否删除，
 * 支持单条件排序（createTime / fileSize）。
 * @param {Object} data { body: { fieldName, fieldFileType, fieldFilePath, fieldIsDelete }, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }
 * @returns {Promise<Object>} BaseResponse，data 为文件记录数组，总数经 metadata.total 返回
 */
export function queryAttFileList(data) {
  return request.post('/backend/sys/att/file/list', data)
}

/**
 * 操作日志列表查询（后台管理）：按附件 id 查询该附件的操作日志记录。
 * @param {Object} data { body: { fieldAttId }, pages: { pageNo, pageSize, needPage }, sortOrder }
 * @returns {Promise<Object>} BaseResponse，data 为操作日志数组，总数经 metadata.total 返回
 */
export function queryAttOperationList(data) {
  return request.post('/backend/sys/att/operation/list', data)
}

/**
 * 上传附件（multipart）
 * @param {FormData} formData 含 file、modelName、modelId、key 字段
 * @returns {Promise<Object>} BaseResponse，data 为上传后的附件记录
 */
export function uploadAttachment(formData) {
  return request.post('/backend/sys/att/main/upload', formData)
}

/**
 * 下载附件：返回 Blob（attachment 方式）
 * @param {string} fieldId 附件 id
 * @returns {Promise<import('axios').AxiosResponse>} response.data 为 Blob
 */
export function downloadAttachment(fieldId) {
  return blobRequest.get(`/backend/sys/att/main/download?fieldId=${fieldId}`)
}

/**
 * 预览附件：返回 Blob（inline 方式），用于浏览器内联打开
 * @param {string} fieldId 附件 id
 * @returns {Promise<import('axios').AxiosResponse>} response.data 为 Blob
 */
export function viewAttachment(fieldId) {
  return blobRequest.get(`/backend/sys/att/main/view?fieldId=${fieldId}`)
}

/**
 * 删除附件（公共组件）：仅上传者本人或附件管理员
 * @param {string} fieldId 附件 id
 */
export function deleteAttachment(fieldId) {
  return request.delete(`/backend/sys/att/main/delete?fieldId=${fieldId}`)
}

/**
 * 后台管理 - 删除附件：仅 SYS_ATT_MAIN，操作日志类型区别于公共组件删除（4=后台删除）
 * @param {string} fieldId 附件 id
 */
export function adminDeleteAttachment(fieldId) {
  return request.delete(`/backend/sys/att/main/adminDelete?fieldId=${fieldId}`)
}

/**
 * 后台管理 - 下载附件：仅 SYS_ATT_MAIN，返回 Blob（attachment 方式）
 * @param {string} fieldId 附件 id
 * @returns {Promise<import('axios').AxiosResponse>} response.data 为 Blob
 */
export function adminDownloadAttachment(fieldId) {
  return blobRequest.get(`/backend/sys/att/main/adminDownload?fieldId=${fieldId}`)
}

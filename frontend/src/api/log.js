import request from './index'

/**
 * 系统模块 - 日志接口封装
 *
 * 对应后端 SysLogOperationController：/backend/sys/log/operation/**
 * 仅 SYS_LOG_ADMIN 权限可见「后台操作 → 日志管理」菜单与接口（后端 LogResolver 兜底）。
 */

/**
 * 日志列表查询（后台管理）：按日志类型区分操作日志/登录日志，
 * 支持模块、动作、操作人（姓名/登录名）、业务文档 ID、结果、操作时间范围筛选，
 * 仅按操作时间排序（asc / desc）。
 * @param {Object} data { body: { fieldLogType, fieldModule, fieldAction, fieldUserName, fieldLoginName, fieldBusinessId, fieldResult }, pages: { pageNo, pageSize, needPage }, sortOrder, operateTimeStart, operateTimeEnd }
 * @returns {Promise<Object>} BaseResponse，data 为日志记录数组，总数经 metadata.total 返回
 */
export function queryLogList(data) {
  return request.post('/backend/sys/log/operation/list', data)
}

/**
 * 日志详情：返回含失败原因与变更内容（before / after）
 * @param {string} fieldId 日志 id
 * @returns {Promise<Object>} BaseResponse，data 为日志记录
 */
export function getLogDetail(fieldId) {
  return request.get(`/backend/sys/log/operation/load?fieldId=${fieldId}`)
}

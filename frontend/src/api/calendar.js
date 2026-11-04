import request from './index'

/**
 * 系统模块 - 日程接口封装
 *
 * 对应后端 SysCalendarMainController：/backend/sys/calendar/**
 */

/**
 * 新增日程
 * @param {Object} body { fieldName, fieldStartTime, fieldEndTime, fieldType, fieldContent, personIds }
 * @returns {Promise<Object>} BaseResponse
 */
export function saveCalendar(body) {
  return request.post('/backend/sys/calendar/save', { body })
}

/**
 * 编辑日程
 * @param {Object} body { fieldId, fieldName, fieldStartTime, fieldEndTime, fieldType, fieldContent, personIds }
 * @returns {Promise<Object>} BaseResponse
 */
export function updateCalendar(body) {
  return request.patch('/backend/sys/calendar/update', { body })
}

/**
 * 删除日程
 * @param {string} fieldId 日程id
 * @returns {Promise<Object>} BaseResponse
 */
export function deleteCalendar(fieldId) {
  return request.delete('/backend/sys/calendar/delete', { params: { fieldId } })
}

/**
 * 日程详情
 * @param {string} fieldId 日程id
 * @returns {Promise<Object>} BaseResponse
 */
export function loadCalendar(fieldId) {
  return request.get('/backend/sys/calendar/load', { params: { fieldId } })
}

/**
 * 日程列表
 * @param {Object} data { body: { fieldName, fieldType, fieldAccept, fieldStartTimeStart, fieldStartTimeEnd }, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }
 * @returns {Promise<Object>} BaseResponse
 */
export function queryCalendarList(data) {
  return request.post('/backend/sys/calendar/list', data)
}

/**
 * 当月日程（日历视图）
 * @param {Object} data { body: { year, month } }
 * @returns {Promise<Object>} BaseResponse
 */
export function queryMonthCalendar(data) {
  return request.post('/backend/sys/calendar/monthList', data)
}

/**
 * 今日日程（顶部弹窗）
 * @returns {Promise<Object>} BaseResponse
 */
export function queryTodayCalendar() {
  return request.get('/backend/sys/calendar/todayList')
}

/**
 * 接收/拒绝日程
 * @param {string} fieldId 日程id
 * @param {boolean} accept true=接收 false=拒绝
 * @returns {Promise<Object>} BaseResponse
 */
export function acceptCalendar(fieldId, accept) {
  return request.patch('/backend/sys/calendar/accept', null, { params: { fieldId, accept } })
}
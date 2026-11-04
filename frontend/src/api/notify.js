import request from './index'

/**
 * 系统模块 - 通知待办接口封装
 *
 * 对应后端 SysNotifyTodoController：/backend/sys/notify/todo/**
 * - send / adminList 为后台管理接口（需 SYS_NOTIFY_ADMIN 权限）；
 * - myList / load / done / undone / star / delete / batch* 为「我的待办」接口（登录即可，按本人过滤）。
 * 待办类型（notify_type）等码值选项由 dict 模块提供，见 src/utils/dict.js。
 */
/**
 * 我的待办列表：只显示当前用户接收到的待办（已删除的过滤）。
 * @param {Object} data { body: { fieldNotifyType, fieldModelName, fieldIsStar, fieldName, fieldReceiveTimeStart, fieldReceiveTimeEnd }, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }
 * @returns {Promise<Object>} BaseResponse，data 为待办数组，总数经 metadata.total 返回
 */
export function queryMyTodoList(data) {
  return request.post('/backend/sys/notify/todo/myList', data)
}

/**
 * 待办详情：待阅类型点击详情触发跳转时，系统自动将该待阅转已办。
 * @param {string} fieldId 待办接收记录 id（sys_notify_todo_target.field_id）
 * @returns {Promise<Object>} BaseResponse，data 为待办详情对象
 */
export function loadTodoDetail(fieldId) {
  return request.get('/backend/sys/notify/todo/load', { params: { fieldId } })
}

/**
 * 转已办
 * @param {string} fieldId 待办接收记录 id
 */
export function markTodoDone(fieldId) {
  return request.patch('/backend/sys/notify/todo/done', null, { params: { fieldId } })
}

/**
 * 转未办
 * @param {string} fieldId 待办接收记录 id
 */
export function markTodoUndone(fieldId) {
  return request.patch('/backend/sys/notify/todo/undone', null, { params: { fieldId } })
}

/**
 * 星标（切换）：未星标则置星标，已星标则取消
 * @param {string} fieldId 待办接收记录 id
 */
export function toggleTodoStar(fieldId) {
  return request.patch('/backend/sys/notify/todo/star', null, { params: { fieldId } })
}

/**
 * 删除（软删除）：对 sys_notify_todo_target.field_is_del 置 true，不是真的删除记录
 * @param {string} fieldId 待办接收记录 id
 */
export function deleteTodo(fieldId) {
  return request.delete('/backend/sys/notify/todo/delete', { params: { fieldId } })
}

/**
 * 批量已办
 * @param {string[]} ids 待办接收记录 id 列表
 */
export function batchDoneTodo(ids) {
  return request.post('/backend/sys/notify/todo/batchDone', { ids })
}

/**
 * 批量转未办
 * @param {string[]} ids 待办接收记录 id 列表
 */
export function batchUndoneTodo(ids) {
  return request.post('/backend/sys/notify/todo/batchUndone', { ids })
}

/**
 * 批量星标
 * @param {string[]} ids 待办接收记录 id 列表
 */
export function batchStarTodo(ids) {
  return request.post('/backend/sys/notify/todo/batchStar', { ids })
}

/**
 * 批量删除（软删除）
 * @param {string[]} ids 待办接收记录 id 列表
 */
export function batchDeleteTodo(ids) {
  return request.post('/backend/sys/notify/todo/batchDelete', { ids })
}

/**
 * 后台待办列表（按 sys_notify_todo 表可见所有待办，含接收人数/已办人数）。
 * @param {Object} data { body: { fieldNotifyType, fieldModelName, fieldName, fieldCreateTimeStart, fieldCreateTimeEnd }, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }
 * @returns {Promise<Object>} BaseResponse，data 为待办数组，总数经 metadata.total 返回
 */
export function queryAdminTodoList(data) {
  return request.post('/backend/sys/notify/todo/adminList', data)
}

/**
 * 来源模块选项：读取 model:name:map 缓存，返回 MODEL_KEY → MODEL_NAME 映射，
 * 供发送待办页与列表筛选区的「来源模块」下拉使用；登录即可访问。
 * @returns {Promise<Object>} BaseResponse，data 为 { MODEL_KEY: MODEL_NAME } 对象
 */
export function queryModelOptions() {
  return request.get('/backend/sys/notify/todo/modelOptions')
}

/**
 * 发送待办：仅 SYS_NOTIFY_ADMIN 权限账号调用。
 * @param {Object} body { fieldId, fieldName, fieldNotifyType, fieldModelName, fieldModelId, fieldUrl }
 * @param {string[]} userIds 接收人 id 列表（多选）
 * @returns {Promise<Object>} BaseResponse，data 为保存后的待办对象
 */
export function sendTodo(body, userIds) {
  return request.post('/backend/sys/notify/todo/send', { body, userIds })
}

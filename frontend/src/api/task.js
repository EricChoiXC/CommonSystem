import request from './index'

/** 定时任务列表：{ body: { fieldName, fieldStatus, fieldEnable }, pages: { pageNo, pageSize, needPage } } */
export function queryTaskList(data) {
  return request.post('/backend/sys/task/main/list', data)
}

/** 任务详情（编辑回填）：GET ?fieldId= */
export function loadTask(fieldId) {
  return request.get('/backend/sys/task/main/load', { params: { fieldId } })
}

/** 编辑任务：{ fieldId, fieldName, fieldCron, fieldParams }（fieldParams 为 JSON 对象字符串） */
export function updateTask(data) {
  return request.patch('/backend/sys/task/main/update', data)
}

/** 启用：PATCH ?fieldId= */
export function enableTask(fieldId) {
  return request.patch('/backend/sys/task/main/enable', null, { params: { fieldId } })
}

/** 停用：PATCH ?fieldId= */
export function disableTask(fieldId) {
  return request.patch('/backend/sys/task/main/disable', null, { params: { fieldId } })
}

/** 批量启用：{ ids } */
export function batchEnableTask(ids) {
  return request.post('/backend/sys/task/main/batchEnable', { ids })
}

/** 批量停用：{ ids } */
export function batchDisableTask(ids) {
  return request.post('/backend/sys/task/main/batchDisable', { ids })
}

/** 手动执行：POST ?fieldId= */
export function executeTask(fieldId) {
  return request.post('/backend/sys/task/main/execute', null, { params: { fieldId } })
}

/** 中止当前任务：POST ?fieldId= */
export function stopTask(fieldId) {
  return request.post('/backend/sys/task/main/stop', null, { params: { fieldId } })
}

/** 执行记录列表：{ body: { fieldTaskName, fieldResult, fieldStartTimeStart, fieldStartTimeEnd, fieldEndTimeStart, fieldEndTimeEnd }, pages, sortField, sortOrder } */
export function queryTaskHistoryList(data) {
  return request.post('/backend/sys/task/history/list', data)
}

/** 执行记录详情：GET ?fieldId= */
export function loadTaskHistoryDetail(fieldId) {
  return request.get('/backend/sys/task/history/load', { params: { fieldId } })
}

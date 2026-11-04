import request from './index'

/**
 * 系统模块 - 流程接口封装
 *
 * 对应后端 SysProcessSettingController：/backend/sys/process/setting/**
 * 仅 SYS_PROCESS_SETTING_ADMIN / SYS_PROCESS_ADMIN 权限账号调用（后端最终鉴权）。
 */

/**
 * 流程公共配置列表：返回本模块公共配置项（中文描述 + 值 + 默认值）。
 * @returns {Promise<Object>} BaseResponse，data 为配置项数组（含 fieldDesc / fieldValue / fieldDefaultValue）
 */
export function queryProcessSettingList() {
  return request.get('/backend/sys/process/setting/list')
}

/**
 * 提交流程公共配置：仅将有更新（或重置）的配置项提交保存。
 * @param {Array<{ fieldId: string, fieldValue: string }>} items 有变更的配置项列表
 * @returns {Promise<Object>} BaseResponse
 */
export function saveProcessSettings(items) {
  return request.post('/backend/sys/process/setting/save', { items })
}

/**
 * 重置流程公共配置：将单个配置项还原为默认值。
 * @param {string} fieldId 配置项 id
 * @returns {Promise<Object>} BaseResponse
 */
export function resetProcessSetting(fieldId) {
  return request.post('/backend/sys/process/setting/reset', null, { params: { fieldId } })
}

/* ==================== 流程模板（module） ==================== */

/**
 * 模板分类列表：读取 process_template 配置项（模板管理页左侧分类）。
 * @returns {Promise<Object>} BaseResponse，data 为分类数组（fieldModelName / name / className / desc）
 */
export function queryProcessTemplateModels() {
  return request.get('/backend/sys/process/module/models')
}

/**
 * 模板列表分页查询：field_model_name 为左侧分类固定条件，
 * 支持模板名称/编号模糊、启用状态筛选与单条件排序（createTime / updateTime）。
 * @param {Object} data { body: { fieldModelName, fieldName, fieldCode, enable }, pages, sortField, sortOrder }
 * @returns {Promise<Object>} BaseResponse，data 为模板数组，总数经 metadata.total 返回
 */
export function queryProcessTemplateList(data) {
  return request.post('/backend/sys/process/module/list', data)
}

/**
 * 模板详情读取：返回模板基本字段 + 节点列表（含审批节点配置）。
 * @param {string} fieldId 模板 id
 * @returns {Promise<Object>} BaseResponse，data 为模板对象（含 nodes）
 */
export function loadProcessTemplate(fieldId) {
  return request.get('/backend/sys/process/module/load', { params: { fieldId } })
}

/**
 * 模板新建：请求体为模板对象（含 nodes 节点列表），初始版本号 1。
 * @param {Object} body 模板对象
 * @returns {Promise<Object>} BaseResponse，data 为保存后的模板详情
 */
export function saveProcessTemplate(body) {
  return request.post('/backend/sys/process/module/save', body)
}

/**
 * 模板编辑：请求体为模板对象（必须携带 fieldId）；历史版本归档，现版本号 +1。
 * @param {Object} body 模板对象
 * @returns {Promise<Object>} BaseResponse，data 为保存后的模板详情
 */
export function updateProcessTemplate(body) {
  return request.patch('/backend/sys/process/module/update', body)
}

/**
 * 模板启停用。
 * @param {Object} body { fieldId, fieldEnable }
 * @returns {Promise<Object>} BaseResponse
 */
export function enableProcessTemplate(body) {
  return request.patch('/backend/sys/process/module/enable', body)
}

/**
 * 置为默认模板：替换同分类现有默认模板（每分类最多一个默认模板）。
 * @param {Object} body { fieldId }
 * @returns {Promise<Object>} BaseResponse
 */
export function setProcessTemplateDefault(body) {
  return request.patch('/backend/sys/process/module/setDefault', body)
}

/* ==================== 流程实例（example） ==================== */

/**
 * 流程实例所属模块选项：读取 process_template 配置项（流程实例列表筛选区的「所属模块」下拉）。
 * @returns {Promise<Object>} BaseResponse，data 为分类数组（fieldModelName / name）
 */
export function queryProcessExampleModels() {
  return request.get('/backend/sys/process/example/models')
}

/**
 * 流程实例列表分页查询：支持流程名称/流程编号模糊、所属模块固定、
 * 发起人名称模糊、创建时间区间与流程状态筛选；仅创建时间单条件排序。
 * @param {Object} data { body: { fieldName, fieldCode, fieldModelName, fieldCreatorName,
 *   fieldCreateTimeBegin, fieldCreateTimeEnd, fieldStatus }, pages, sortField, sortOrder }
 * @returns {Promise<Object>} BaseResponse，data 为流程实例数组，总数经 metadata.total 返回
 */
export function queryProcessExampleList(data) {
  return request.post('/backend/sys/process/example/list', data)
}

/**
 * 流程实例详情读取：返回流程实例基本字段 + 流程实例节点 + 工作项 + 审批记录。
 * @param {string} fieldId 流程实例 id
 * @returns {Promise<Object>} BaseResponse，data 为流程实例详情
 */
export function loadProcessExample(fieldId) {
  return request.get('/backend/sys/process/example/load', { params: { fieldId } })
}

/**
 * 流程审批面板数据：返回流程基本信息 + 节点 + 审批记录 + 当前用户可执行审批操作
 * （供业务文档页嵌入审批组件展示，面板不直接调用接口，由业务模块后端提交）。
 * @param {string} fieldId 流程实例 id
 * @returns {Promise<Object>} BaseResponse，data 为面板数据
 */
export function loadProcessExamplePanel(fieldId) {
  return request.get('/backend/sys/process/example/panel', { params: { fieldId } })
}

/**
 * 流程实例特权操作：跳转/终审通过/作废/暂停/恢复（仅 SYS_PROCESS_EXAMPLE_ADMIN）。
 * @param {Object} body { fieldId, operateType, targetNodeId, content }
 * @returns {Promise<Object>} BaseResponse
 */
export function adminOperateProcessExample(body) {
  return request.post('/backend/sys/process/example/adminOperate', body)
}

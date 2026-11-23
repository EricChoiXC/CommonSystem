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
 * 模板详情读取：返回模板基本字段 + 节点列表 + 连线列表。
 * v1_7 契约（接口清单 §4.2）：data.nodes 元素含 nodeKey（固定节点 N1/N2/N3、
 * 中间节点 N4+ 顺延）与节点配置平铺字段（handlerSource/handlerFormula/skipMode/
 * operations/tasks/joinMode 等）；data.lines 为连线数组（fieldId/fieldFromKey/fieldToKey/
 * fieldLineType/fieldBranchOrder/fieldBranchName/fieldCondition/fieldIsDefault）。
 * 节点坐标不落库：响应不含 x/y，画布按拓扑自动分层布局（T-08 定稿）。
 * @param {string} fieldId 模板 id
 * @returns {Promise<Object>} BaseResponse，data 为模板对象（含 nodes + lines）
 */
export function loadProcessTemplate(fieldId) {
  return request.get('/backend/sys/process/module/load', { params: { fieldId } })
}

/**
 * 模板新建：请求体为模板对象（含 nodes + lines 全量），初始版本号 1。
 * v1_7 契约（接口清单 §4.3）：nodes 元素 { fieldId, nodeKey, fieldNo, fieldNodeName,
 * fieldNodeType, ...配置平铺字段 }，lines 元素 { fieldId, fieldFromKey, fieldToKey,
 * fieldLineType, ...分支字段 }；节点键由服务端按类型归一（START=N1/DRAFT=N2/END=N3/
 * 中间节点按请求 nodes 数组顺序自 N4 顺延，N3 保留给结束节点），连线全量删除重建；
 * 服务端执行 G1~G10 图校验，非法图拒绝保存并返回中文提示（前端按 message 展示）。
 * @param {Object} body 模板对象
 * @returns {Promise<Object>} BaseResponse，data 为保存后的模板详情
 */
export function saveProcessTemplate(body) {
  return request.post('/backend/sys/process/module/save', body)
}

/**
 * 模板编辑：请求体为模板对象（必须携带 fieldId，nodes + lines 全量提交，契约同 save）；
 * 历史版本归档扩展为含连线快照，现版本号 +1。
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
 * v1_7 契约（接口清单 §5.3）：data.nodes 元素含 fieldNodeKey（实例节点业务键）与
 * fieldNodeNo（实例侧序号字段名，模板为 fieldNo）；data.lines 为实例连线快照
 * （字段同模板 lines，fieldExampleId 替代 fieldTemplateId）——旧实例迁移已生成线性
 * 连线快照，后端尚未返回 lines 时前端按节点序号降级排布（只读流程图渲染口径）。
 * @param {string} fieldId 流程实例 id
 * @returns {Promise<Object>} BaseResponse，data 为流程实例详情（example/nodes/workitems/auditNotes/lines）
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
 * 流程监控列表分页查询（v1_7 新增，接口清单 §5.6）：管理员全量视图（不做本人数据过滤），
 * 支持在途筛选（inFlightOnly：PROCESSING/RETURN/PAUSE）与异常筛选（exceptionOnly：
 * 存在 SYSTEM_ERROR 留痕或状态 PAUSE）；行内额外透出 exceptionFlag（是否异常实例）与
 * currentNodeName（当前节点名称）；仅创建时间单条件排序。
 * @param {Object} data { body: { fieldName, fieldCode, fieldModelName, fieldCreatorName,
 *   fieldCreateTimeBegin, fieldCreateTimeEnd, fieldStatus, inFlightOnly, exceptionOnly },
 *   pages, sortField, sortOrder }
 * @returns {Promise<Object>} BaseResponse，data 为流程实例数组（含 exceptionFlag），总数经 metadata.total 返回
 */
export function queryProcessMonitorList(data) {
  return request.post('/backend/sys/process/example/monitorList', data)
}

/**
 * 流程实例特权操作：跳转/终审通过/作废/暂停/恢复/更换审批人（仅 SYS_PROCESS_EXAMPLE_ADMIN）。
 * v1_7 契约（接口清单 §5.5）：operateType 取 JUMP/FINISH/ABANDON/PAUSE/RESUME/CHANGE_HANDLER；
 * JUMP 目标节点扩展为起草/审批/人工决策节点（targetNodeId 与 targetNodeKey 二者其一，
 * 现状提交 targetNodeId）；CHANGE_HANDLER 携带 workitemId（待换人 PROCESSING 工作项）与
 * changeToId（目标处理人用户 id），意见 content 必填。
 * @param {Object} body { fieldId, operateType, targetNodeId?, workitemId?, changeToId?, content }
 * @returns {Promise<Object>} BaseResponse
 */
export function adminOperateProcessExample(body) {
  return request.post('/backend/sys/process/example/adminOperate', body)
}

/* ==================== 流程统计（statistic） ==================== */

/**
 * 流程统计（管理员）：区间内实例总数、按状态/模块/模板的数量分布、平均办结耗时与
 * 人员工作量 TopN（v1_7 新增，接口清单 §7.1；仅 SYS_PROCESS_EXAMPLE_ADMIN）。
 * avgDurationMs 只计已完成（FINISH）实例（field_finish_time - field_create_time 自然时长毫秒，
 * 无完成实例为 0）；handlerWorkloads 按已办工作量降序取前 topN（缺省 10）。
 * @param {Object} [data] { body: { fieldModelName?, fieldTemplateId?, fieldStatus?,
 *   fieldCreateTimeBegin?, fieldCreateTimeEnd?, topN? } }（body 可整体缺省，缺省按全量统计）
 * @returns {Promise<Object>} BaseResponse，data 为管理员统计结果
 *   （totalCount / statusCounts / modelCounts / templateCounts / avgDurationMs / handlerWorkloads）
 */
export function queryProcessStatisticAdmin(data) {
  return request.post('/backend/sys/process/statistic/admin', data || {})
}

/**
 * 我的流程统计：今日发起 / 我的未办结 / 待审 / 今日已审 / 平均耗时五项指标
 * （v1_7 新增，接口清单 §7.2；登录即可）。无请求体，统计对象由会话定位
 * （后端不接收任何 userId 参数，防越权）；avgDurationMs 为本人参与且已办结
 * 实例的平均办结时长（毫秒，无办结实例为 0）。
 * @returns {Promise<Object>} BaseResponse，data 为我的统计五项指标
 */
export function queryProcessStatisticMy() {
  return request.post('/backend/sys/process/statistic/my')
}

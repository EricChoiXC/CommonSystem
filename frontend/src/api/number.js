import request from './index'

/**
 * 编号机制接口封装
 *
 * 对应 docs/v1_5-编号功能/编号机制实现方案.md §3（接口总表与契约明细）、§7.3：
 * 编号规则维护（rule）、预览渲染（preview）、业务取号（generate）、
 * 取号明细与特权管理（manage），以及 W5 补充的接入项下拉（rule/modelOptions）。
 * 页面只 import 本文件具名函数，不直接写 axios / 裸 URL。
 */

/** 编号规则列表：{ body: { fieldName, fieldCode, fieldModelField, fieldEnable }, pages: { pageNo, pageSize, needPage } }；鉴权动作 sys.number.rule.list（登录即可） */
export function queryNumberRuleList(data) {
  return request.post('/backend/sys/number/rule/list', data)
}

/** 编号规则详情（编辑回填，fieldRule 已解析为规则配置对象 { items: [...] }）：GET ?fieldId=；鉴权动作 sys.number.rule.load（SYS_NUMBER_ADMIN 或 SYS_NUMBER_RULE_EDITOR） */
export function loadNumberRule(fieldId) {
  return request.get('/backend/sys/number/rule/load', { params: { fieldId } })
}

/** 新建编号规则（默认启用，同事务自动停用同接入项原启用规则）：data 为规则对象 { fieldId, fieldCode, fieldName, fieldModelId, fieldModelName, fieldModelField, fieldRule }（fieldRule 为对象）；鉴权动作 sys.number.rule.save（SYS_NUMBER_ADMIN 或 SYS_NUMBER_RULE_EDITOR） */
export function saveNumberRule(data) {
  return request.post('/backend/sys/number/rule/save', data)
}

/** 编辑编号规则（须携带 fieldId，另校验 seq 项 id 锁定）：data 同 save；鉴权动作 sys.number.rule.update（SYS_NUMBER_ADMIN 或 SYS_NUMBER_RULE_EDITOR） */
export function updateNumberRule(data) {
  return request.patch('/backend/sys/number/rule/update', data)
}

/** 启停用编号规则（启用时自动停用同接入项其他已启用规则）：data 仅 { fieldId, fieldEnable }（请求体）；鉴权动作 sys.number.rule.enable（SYS_NUMBER_ADMIN 或 SYS_NUMBER_RULE_EDITOR） */
export function enableNumberRule(data) {
  return request.patch('/backend/sys/number/rule/enable', data)
}

/** 删除编号规则（已产生取号明细时被拒）：DELETE ?fieldId=；鉴权动作 sys.number.rule.delete（SYS_NUMBER_ADMIN 或 SYS_NUMBER_RULE_EDITOR） */
export function deleteNumberRule(fieldId) {
  return request.delete('/backend/sys/number/rule/delete', { params: { fieldId } })
}

/** 编号预览渲染（顺延模拟，不推进计数器、不落库）：data = { fieldId?, rule?, count? }（二选一，count 缺省 3 上限 10），响应 data 为 { samples, errors }；鉴权动作 sys.number.preview.render（登录即可） */
export function renderNumberPreview(data) {
  return request.post('/backend/sys/number/preview/render', data)
}

/** 业务取号（幂等，reused=true 表示命中已有编号）：data = { modelName, modelField, modelId }，响应 data 为 { code, recordDetailId, reused }；鉴权动作 sys.number.generate.acquire（登录即可，业务单据权限由调用方完成） */
export function acquireNumber(data) {
  return request.post('/backend/sys/number/generate/acquire', data)
}

/** 取号明细列表：{ body: { fieldScopeKey, fieldRuleId, fieldPeriod, voidFlag, recordTimeBegin, recordTimeEnd }, pages: { pageNo, pageSize, needPage } }；鉴权动作 sys.number.manage.recordList（仅 SYS_NUMBER_ADMIN） */
export function queryNumberRecordList(data) {
  return request.post('/backend/sys/number/manage/recordList', data)
}

/** 重置计数器（封存当前行，下次取号从 start 重开，会造成重号）：data = { fieldRuleId?, fieldScopeKey?, fieldSerialId?, fieldPeriod? }，响应 data 为封存行数；鉴权动作 sys.number.manage.reset（仅 SYS_NUMBER_ADMIN） */
export function resetNumberRecord(data) {
  return request.post('/backend/sys/number/manage/reset', data)
}

/** 作废取号明细（不回退计数器，重复作废幂等）：data = { fieldId, fieldVoidReason }（请求体）；鉴权动作 sys.number.manage.void（仅 SYS_NUMBER_ADMIN） */
export function voidNumberRecord(data) {
  return request.patch('/backend/sys/number/manage/void', data)
}

/** 补号（对已作废明细按计数器顺延取新号，旧号留审计）：请求体 { fieldId }（非 query param）；鉴权动作 sys.number.manage.reissue（仅 SYS_NUMBER_ADMIN） */
export function reissueNumber(fieldId) {
  return request.post('/backend/sys/number/manage/reissue', { fieldId })
}

/** 接入项下拉数据源（W5 补充接口）：GET，响应 data 为接入项列表 [{ key, name, modelName, modelField }]；登录即可 */
export function queryNumberModelOptions() {
  return request.get('/backend/sys/number/rule/modelOptions')
}

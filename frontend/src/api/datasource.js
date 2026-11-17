import request from './index'

/**
 * 系统模块 - 数据源接口封装
 *
 * 对应后端 SysDatasourceController：/backend/sys/datasource/**
 * - list / load / save / update / enable / delete 仅 SYS_DATASOURCE_ADMIN 可调用；
 * - options / targets / query 登录即可，query 取数越权由执行期委托目标 action 鉴权收敛；
 * - fieldConditions / fieldFields 双形态：列表为原文 JSON 字符串，load / save / update 为解析后对象。
 */

/** 数据源统一执行（消费方取数入口）：data = { code, body, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }，响应 data 为投影后行列表、metadata = { total, fields }；鉴权动作 sys.datasource.query（登录即可，执行期委托目标 action 鉴权） */
export function queryDatasource(data) {
  return request.post('/backend/sys/datasource/query', data)
}

/** 可用数据源下拉：默认数据源（CODE）+ 启用的自定义数据源（CUSTOM），data 为 [{ code, name, sourceType, target, targetPath, fields, conditionCount }]，不暴露固定条件原文；鉴权动作 sys.datasource.options（登录即可） */
export function getDatasourceOptions() {
  return request.get('/backend/sys/datasource/options')
}

/** 目标查询能力清单（配置表单选能力、编排固定条件、勾选返回字段）：data 为 [{ code, name, targetAction, targetPath, conditionFields, resultFields }]，code 即 fieldTarget 取值；鉴权动作 sys.datasource.targets（登录即可） */
export function getDatasourceTargets() {
  return request.get('/backend/sys/datasource/targets')
}

/** 自定义数据源管理列表：{ body: { fieldName, fieldCode, fieldTarget, fieldEnable }, pages: { pageNo, pageSize, needPage } }，总数走 metadata.total，fieldConditions / fieldFields 原文 JSON 字符串返回；鉴权动作 sys.datasource.list（仅 SYS_DATASOURCE_ADMIN） */
export function listDatasources(data) {
  return request.post('/backend/sys/datasource/list', data)
}

/** 数据源详情（fieldConditions 解析为 [{ key, op, value }]、fieldFields 解析为字段名字符串数组，NULL=默认全字段）：GET ?fieldId=；鉴权动作 sys.datasource.load（仅 SYS_DATASOURCE_ADMIN） */
export function loadDatasource(fieldId) {
  return request.get('/backend/sys/datasource/load', { params: { fieldId } })
}

/** 新建自定义数据源：body = { fieldId, fieldCode, fieldName, fieldTarget, fieldConditions, fieldFields, fieldRemark }（fieldId 经 /backend/common/initID 预取），请求体 { body }；鉴权动作 sys.datasource.save（仅 SYS_DATASOURCE_ADMIN） */
export function saveDatasource(body) {
  return request.post('/backend/sys/datasource/save', { body })
}

/** 编辑自定义数据源（fieldId 必填，fieldTarget 保存后锁定不受理变更）：body 同 save（无 fieldTarget），请求体 { body }；鉴权动作 sys.datasource.update（仅 SYS_DATASOURCE_ADMIN） */
export function updateDatasource(body) {
  return request.patch('/backend/sys/datasource/update', { body })
}

/** 启停用自定义数据源：请求体仅 { fieldId, fieldEnable } 两字段（不包 body 层，对齐全工程 /enable 通道）；鉴权动作 sys.datasource.enable（仅 SYS_DATASOURCE_ADMIN） */
export function updateDatasourceEnable(fieldId, fieldEnable) {
  return request.patch('/backend/sys/datasource/enable', { fieldId, fieldEnable })
}

/** 删除自定义数据源（启用中的删除须前端二次确认）：DELETE ?fieldId=；鉴权动作 sys.datasource.delete（仅 SYS_DATASOURCE_ADMIN） */
export function deleteDatasource(fieldId) {
  return request.delete('/backend/sys/datasource/delete', { params: { fieldId } })
}

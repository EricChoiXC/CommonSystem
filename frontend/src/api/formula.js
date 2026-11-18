import request from './index'

/**
 * 系统模块 - 公式定义器接口封装
 *
 * 对应后端 SysFormulaController（管理六动作：list / load / save / update / enable / delete）
 * 与 SysFormulaDesignerController（组件四动作：options / functions / validate / simulate）：
 * - 管理六动作仅 SYS_FORMULA_ADMIN 可调用；
 * - 组件四接口登录即可，options 只暴露公式条目元信息、不暴露表达式原文；
 * - validate 的 vars 为声明式数组 [{ code, type }]，simulate 的 vars 为赋值式对象 { code: value }，两者不可混用。
 */

/** 公式管理列表：{ body: { fieldCode, fieldName, fieldType, fieldEnable }, pages: { pageNo, pageSize, needPage } }，总数走 metadata.total；鉴权动作 sys.formula.list（仅 SYS_FORMULA_ADMIN） */
export function listFormulas(data) {
  return request.post('/backend/sys/formula/list', data)
}

/** 公式详情（含表达式原文与后端解析出的 fieldDependencies）：GET ?fieldId=；鉴权动作 sys.formula.load（仅 SYS_FORMULA_ADMIN） */
export function loadFormula(fieldId) {
  return request.get('/backend/sys/formula/load', { params: { fieldId } })
}

/** 新建公式：body = { fieldId, fieldCode, fieldName, fieldType, fieldExpression, fieldReturnType, fieldRemark }（fieldId 经 /backend/common/initID 预取；fieldDependencies 由后端保存期解析落库，前端不传），请求体 { body }；鉴权动作 sys.formula.save（仅 SYS_FORMULA_ADMIN） */
export function saveFormula(body) {
  return request.post('/backend/sys/formula/save', { body })
}

/** 编辑公式（fieldId 必填）：body 同 save，请求体 { body }；鉴权动作 sys.formula.update（仅 SYS_FORMULA_ADMIN） */
export function updateFormula(body) {
  return request.patch('/backend/sys/formula/update', { body })
}

/** 启停用公式：请求体仅 { fieldId, fieldEnable } 两字段（不包 body 层，对齐全工程 /enable 通道）；鉴权动作 sys.formula.enable（仅 SYS_FORMULA_ADMIN） */
export function enableFormula(fieldId, fieldEnable) {
  return request.patch('/backend/sys/formula/enable', { fieldId, fieldEnable })
}

/** 删除公式（启用中的删除须前端二次确认）：DELETE ?fieldId=；鉴权动作 sys.formula.delete（仅 SYS_FORMULA_ADMIN） */
export function deleteFormula(fieldId) {
  return request.delete('/backend/sys/formula/delete', { params: { fieldId } })
}

/** 可用命名公式下拉（公式设计器引用变量来源）：data 为启用中公式条目 [{ fieldCode, fieldName, fieldType, fieldReturnType }]，不暴露表达式原文；鉴权动作 sys.formula.options（登录即可） */
export function getFormulaOptions() {
  return request.get('/backend/sys/formula/options')
}

/** 公式函数元数据清单（设计器函数面板）：data 为 [{ functionCode, functionName, category, description, parameters: [{ name, type, required, defaultValue }], returnType }]；鉴权动作 sys.formula.functions（登录即可） */
export function getFormulaFunctions() {
  return request.get('/backend/sys/formula/functions')
}

/** 公式静态校验：请求体平铺 { expression, expectedReturnType, vars }，vars 为声明式数组 [{ code, type }]（缺省=宽松模式）；data 为 { syntax, returnType, dependencies, errors: [{ code, message }] }，校验失败 HTTP 仍 200 由 data.errors 承载；鉴权动作 sys.formula.validate（登录即可） */
export function validateFormula(data) {
  return request.post('/backend/sys/formula/validate', data)
}

/** 公式模拟执行：请求体平铺 { expression, vars, expectedReturnType }，vars 为赋值式键值对象（如 { applyAmount: 1000 }，与 validate 的数组形态不同）；data 为 { success, result, resultType } 或 { success: false, errors: [{ code, message }] }，失败 HTTP 仍 200；鉴权动作 sys.formula.simulate（登录即可） */
export function simulateFormula(data) {
  return request.post('/backend/sys/formula/simulate', data)
}

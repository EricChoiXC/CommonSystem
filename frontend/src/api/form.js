import request from './index'

/**
 * 系统模块 - 自定义表单接口封装
 *
 * 对应后端 SysFormDefineController（定义面：表单定义与版本管理九动作，
 * /backend/sys/form/define/**）与 SysFormDataController（数据面：表单数据引擎六动作，
 * /backend/sys/form/data/**）——两个 Controller 尚未落地（Wave 2 任务 F15-BE-006 交付），
 * 接口契约以 docs/v1_5-自定义表单/自定义表单API接口设计分册.md 为准（§2.2 请求/响应包装、
 * §2.5 接口总览、§3 定义面、§4 数据面）。
 * - 准入口径：定义面九动作仅 SYS_FORM_ADMIN 可调用（SYS_ADMIN 由后端 BaseResolver 自动放行）；
 * - 数据面六动作登录即可，数据归属（本人 / 管理员全量）由后端 Service 层按会话强制判定与过滤，
 *   不信任请求体传入的用户标识（分册 §4.8；先例流程 scope=myCreated 强制注入）；
 * - 业务失败以异常 message 前缀承载 CS_FORM_* 错误码（BaseResponse.code = BAD_REQUEST，
 *   分册 §7.1），前端按 message 前缀识别；数据面 validate 的字段级校验结果为正常业务结果，
 *   经 data.errors 结构化承载、HTTP 仍 200。
 */

/** 表单定义列表（管理页三段式列表区）：{ body: { fieldFormCode, fieldFormName, fieldStatus, fieldEnable, fieldCreateTimeBegin, fieldCreateTimeEnd }, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }，单条件排序默认创建时间降序，总数走 metadata.total；data 行字段含 fieldId / fieldFormCode / fieldFormName / fieldStatus（DRAFT 从未发布 / PUBLISHED 存在当前发布版本）/ fieldEnable / fieldCurrentVersionId / fieldCurrentVersion / hasDraft（设计/发布按钮预判），不携带 Schema 内容（分册 §3.2）；鉴权动作 sys.form.define.list（仅 SYS_FORM_ADMIN） */
export function listForms(data) {
  return request.post('/backend/sys/form/define/list', data)
}

/** 表单详情（元数据表单回填与设计器画布初始化）：GET ?fieldId=，data = SysFormVo——§3.2 全部行字段 + 两个可空 Schema 载荷：draftSchema（DRAFT 版本行内容，无草稿行为 null）与 publishedSchema（当前发布版本行内容，从未发布为 null）；设计器以 draftSchema 为编辑对象，为 null（再编辑场景）时以 publishedSchema 初始化画布（分册 §3.4）；鉴权动作 sys.form.define.load（仅 SYS_FORM_ADMIN） */
export function loadForm(fieldId) {
  return request.get('/backend/sys/form/define/load', { params: { fieldId } })
}

/** 新建表单（主档 + 初始草稿行）：body = { fieldId, fieldFormCode, fieldFormName, schema }（fieldId 经 /backend/common/initID 预生成；fieldFormCode 全局唯一、保存期查重重复报 CS_FORM_CODE_DUPLICATED；schema 可空——设计器已产出时随建随存，缺省由服务端以空白草稿初始化），请求体 { body }，data = SysFormVo（含初始化后的 draftSchema）（分册 §3.3）；鉴权动作 sys.form.define.save（仅 SYS_FORM_ADMIN） */
export function saveForm(body) {
  return request.post('/backend/sys/form/define/save', { body })
}

/** 更新表单（元数据 + 草稿 Schema）：body = { fieldId, fieldFormName, schema }（fieldId 必填；fieldFormName / schema 至少携带一项、不携带即不更新，两字段全空拒绝；编码不可变更——本接口不含 fieldFormCode；schema 为草稿 Schema 全量替换、不做差量，服务端只写 DRAFT 版本行，PUBLISHED 无草稿行时自动建行承载即「再编辑」入口），请求体 { body }，data = SysFormVo（更新后元数据 + draftSchema + publishedSchema，设计器以服务端归一后的 Schema 刷新画布）（分册 §3.5）；鉴权动作 sys.form.define.update（仅 SYS_FORM_ADMIN） */
export function updateForm(body) {
  return request.patch('/backend/sys/form/define/update', { body })
}

/** 启停用表单：请求体仅 { fieldId, fieldEnable } 两字段（不包 body 层，对齐 formula.js enableFormula 先例，全工程 /enable 通道）；只更新主档自身一行（自定义表单无子级）；停用关闭填写入口与数据提交（数据面 schema / save / validate 拒绝 CS_FORM_DISABLED），定义面与数据查询保留（分册 §3.6）；鉴权动作 sys.form.define.enable（仅 SYS_FORM_ADMIN） */
export function enableForm(fieldId, fieldEnable) {
  return request.patch('/backend/sys/form/define/enable', { fieldId, fieldEnable })
}

/** 删除表单（存在任意实例数据后端拒绝 CS_FORM_HAS_DATA——不分版本不分启用状态、不提供级联删除，「不再使用」走 /enable 停用；启用中的删除须前端二次确认）：DELETE ?fieldId=，data = Void，通过校验后主档行与全部版本行一并删除（分册 §3.7）；鉴权动作 sys.form.define.delete（仅 SYS_FORM_ADMIN） */
export function deleteForm(fieldId) {
  return request.delete('/backend/sys/form/define/delete', { params: { fieldId } })
}

/** 发布表单（草稿行原地冻结为不可变版本行，发布校验链 P1~P4 全过方可发布：无草稿行 CS_FORM_NO_DRAFT、结构校验 CS_FORM_SCHEMA_INVALID、资源上限 CS_FORM_SCHEMA_LIMIT_EXCEEDED、公式静态校验复用 CS_FORMULA_*）：请求体 { fieldId }，data = { fieldVersionId, fieldVersion }（新发布版本行 ID 与整数版本号）（分册 §3.8）；鉴权动作 sys.form.define.publish（仅 SYS_FORM_ADMIN） */
export function publishForm(fieldId) {
  return request.post('/backend/sys/form/define/publish', { fieldId })
}

/** 表单版本列表（管理页行【版本】与设计器页【版本】共用，按 fieldVersion 降序最新在前）：GET ?fieldId=，data = List<SysFormVersionVo>——fieldId（版本行 ID，回滚目标）/ fieldVersion / fieldStatus（DRAFT 草稿至多一行 / PUBLISHED 当前发布 / DEPRECATED 被新版本替代）/ fieldPublishedAt（草稿行为 null），不返回 Schema 内容（分册 §3.9）；鉴权动作 sys.form.define.versions（仅 SYS_FORM_ADMIN） */
export function getFormVersions(fieldId) {
  return request.get('/backend/sys/form/define/versions', { params: { fieldId } })
}

/** 回滚版本（复制目标版本内容产生新版本行重发布，不经草稿态一步发布、不产生指针回拨，不影响既有草稿行）：请求体 { fieldId, fieldVersionId }（目标须为 PUBLISHED / DEPRECATED 行，DRAFT 目标拒绝 CS_FORM_VERSION_STATE_INVALID、不存在报 CS_FORM_VERSION_NOT_FOUND），data = { fieldVersionId, fieldVersion }（回滚产生的新版本行 ID 与版本号）（分册 §3.10）；鉴权动作 sys.form.define.rollback（仅 SYS_FORM_ADMIN） */
export function rollbackForm(fieldId, fieldVersionId) {
  return request.post('/backend/sys/form/define/rollback', { fieldId, fieldVersionId })
}

/* ==================== 表单数据（数据面 /backend/sys/form/data/**） ==================== */

/** 获取填写用当前发布版本 Schema（填写页渲染与业务系统内嵌；数据详情页不经本接口取 Schema，经 loadFormData 取实例锁定版本 Schema）：GET ?formId=（表单主档 ID），data = SysFormSchemaDo（field_current_version_id 指向版本行的完整 Schema，协议头 version 回填发布版本号）；前置拒绝：表单不存在 CS_FORM_NOT_FOUND、停用 CS_FORM_DISABLED、无已发布版本 CS_FORM_NO_PUBLISHED_VERSION（分册 §4.2）；鉴权动作 sys.form.data.schema（登录即可）。注：本函数为任务卡 F15-FE-001 函数清单未列、按 API 分册 §4.2 契约补齐，函数名取分册 §4.2 登记的 getFillSchema */
export function getFillSchema(formId) {
  return request.get('/backend/sys/form/data/schema', { params: { formId } })
}

/** 保存表单数据（一期仅新增，无实例更新接口）：请求体 { fieldId, formId, businessId, formData }（fieldId 为数据行 ID 经 /backend/common/initID 预生成；formId 为表单主档 ID，服务端锁定 field_current_version_id 指向版本 Schema 求值——formData 不含 formVersionId，显式版本提交随 v1.8 BPM 登记；businessId 为外部业务对象弱关联 ID 可空；formData 为 { 字段码: 值 } 载荷，明细表字段值为扁平行对象数组），data = { fieldId, formData }——formData 为服务端最终落库形态（公式重算值回填、隐藏字段裁剪后），消费方以此刷新展示（分册 §4.3）；鉴权动作 sys.form.data.save（登录即可，归属=创建人由服务端提交期绑定） */
export function saveFormData(data) {
  return request.post('/backend/sys/form/data/save', data)
}

/** 表单数据独立校验入口（填写页提交前预检与业务集成预检，与 save 共用同一校验引擎与校验链、不落库）：请求体 { formId, businessId, formData }（与 save 同构、无 fieldId），data = { pass, errors, formData }——pass=true 时 errors 为空数组、formData 回带服务端重算/裁剪后最终数据预览；errors 元素 { fieldCode, rowIndex, ruleId, message }（rowIndex 仅明细行内错误携带 0 起始、基础校验 ruleId 为 null）；字段级失败 HTTP 仍 200 由 data.errors 承载，资源与确定性拒绝仍以 CS_FORM_* / CS_FORMULA_* 错误码异常返回（分册 §4.4）；鉴权动作 sys.form.data.validate（登录即可） */
export function validateFormData(data) {
  return request.post('/backend/sys/form/data/validate', data)
}

/** 表单数据详情 / 回显：GET ?fieldId=（数据行 ID），data = SysFormDataDetailVo——fieldId / fieldFormId / fieldFormVersionId（实例锁定版本行 ID，原则 2「在途实例用原版本」不随主档当前版本漂移）/ fieldBusinessId / 提交人公共字段 / formData（读取层裁剪后形态：提交时刻被裁剪的隐藏字段与 permission=hidden 字段不下发）/ schema（实例锁定版本 Schema，渲染器详情态复用）（分册 §4.5）；鉴权动作 sys.form.data.load（登录即可 + 数据归属，非本人非管理员 CS_FORM_DATA_FORBIDDEN） */
export function loadFormData(fieldId) {
  return request.get('/backend/sys/form/data/load', { params: { fieldId } })
}

/** 表单数据列表（业务集成消费通道，一期无独立数据列表页）：{ body: { fieldFormId（必填，数据列表在单一表单维度）, fieldBusinessId, fieldProcessInstanceId（v1.8 预留筛选，一期恒空）, fieldCreatorId（仅管理员生效，非管理员请求忽略该条件）, fieldCreateTimeBegin, fieldCreateTimeEnd }, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }，总数走 metadata.total；归属过滤（本人 / 管理员全量）由后端 Service 层强制注入、不信任请求体用户标识；行字段不含 formData 字段值内容（分册 §4.6）；鉴权动作 sys.form.data.list（登录即可 + 归属强制过滤） */
export function listFormData(data) {
  return request.post('/backend/sys/form/data/list', data)
}

/** 删除表单数据（创建人或管理员）：DELETE ?fieldId=（数据行 ID），data = Void；非创建人且非管理员 CS_FORM_DATA_FORBIDDEN（前端按 fieldCreatorId 与角色预判按钮显隐，不替代后端归属判定）（分册 §4.7）；鉴权动作 sys.form.data.delete（登录即可 + 数据归属） */
export function deleteFormData(fieldId) {
  return request.delete('/backend/sys/form/data/delete', { params: { fieldId } })
}

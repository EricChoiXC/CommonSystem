import request from './index'

/**
 * 系统模块 - 多语言管理（i18n）接口封装
 *
 * 接口路径遵循 /backend/sys/i18n/** 与 /locales/**（语言包动态接口，D2 决策），
 * 后端 Controller 见 com.common.system.sys.i18n.controller。
 * 权限码：SYS_I18N_ADMIN（多语言管理员）。
 */

/* ==================== 语言管理（language） ==================== */

/**
 * 语言列表（免鉴权：登录页语言下拉复用）
 * @param {Object} [params] { fieldName, fieldCode, fieldEnable }，均可空；fieldEnable=true 仅取启用语言
 * @returns {Promise<Object>} BaseResponse，data 为语言数组（field_order 升序、field_code 升序）
 */
export function listLanguages(params) {
  return request.get('/backend/sys/i18n/language/list', { params })
}

/**
 * 新增语言
 * @param {Object} body { fieldId, fieldCode, fieldName, fieldEnable?, fieldOrder? }
 * @returns {Promise<Object>} BaseResponse，data 为保存后的语言行
 */
export function saveLanguage(body) {
  return request.post('/backend/sys/i18n/language/save', { body })
}

/**
 * 编辑语言（fieldCode 与 field_default 不可修改）
 * @param {Object} body { fieldId, fieldName?, fieldEnable?, fieldOrder? }
 * @returns {Promise<Object>} BaseResponse，data 为更新后的语言行
 */
export function updateLanguage(body) {
  return request.patch('/backend/sys/i18n/language/update', { body })
}

/**
 * 删除语言（默认语言禁止删除；级联删除该语言全部翻译、用户语言登记改绑默认语言）
 * @param {string} fieldId 语言 ID
 * @returns {Promise<Object>} BaseResponse
 */
export function deleteLanguage(fieldId) {
  return request.delete('/backend/sys/i18n/language/delete', { params: { fieldId } })
}

/**
 * 设默认语言（互斥切换：先清旧默认再设新默认）
 * @param {string} fieldId 目标语言 ID
 * @returns {Promise<Object>} BaseResponse，data 为切换后的默认语言行
 */
export function setDefaultLanguage(fieldId) {
  return request.patch('/backend/sys/i18n/language/setDefault', { body: { fieldId } })
}

/* ==================== 翻译维护（translation） ==================== */

/**
 * 模块聚合列表（登录即可）：翻译矩阵页「所属模块」下拉
 * @returns {Promise<Object>} BaseResponse，data 为 { fieldModel, fieldModelName } 数组
 */
export function listModels() {
  return request.get('/backend/sys/i18n/model/list')
}

/**
 * 翻译矩阵查询
 * @param {string} fieldModel 模块名（相对 i18n/ 的资源子目录层级名）
 * @returns {Promise<Object>} BaseResponse，data 为 { fieldModel, keys, languages, matrix }
 *   keys: [{ fieldId, fieldKey, fieldModel }]（field_key 升序）；
 *   languages: [{ fieldCode, fieldName }]（启用语言，field_order 升序）；
 *   matrix: { key → { 语言码 → 文本 } }（无翻译的格缺省）
 */
export function listTranslationMatrix(fieldModel) {
  return request.post('/backend/sys/i18n/translation/list', { fieldModel })
}

/**
 * 批量更新翻译（仅提交本次 diff 出的改动项）
 * @param {Array<{fieldId: string, fieldMessage: string}>} details 改动明细
 * @returns {Promise<Object>} BaseResponse，data 为更新条数
 */
export function updateTranslations(details) {
  return request.patch('/backend/sys/i18n/translation/update', { details })
}

/**
 * 重置翻译（按资源行 × 语言）：清人工编辑标记并回写资源文件当前值；
 * 该语言无对应资源文件（或文件中无此 key）→ 直接删除该翻译记录
 * @param {string} fieldKey 资源 key
 * @param {string} fieldLanguage 语言码
 * @returns {Promise<Object>} BaseResponse
 */
export function resetTranslation(fieldKey, fieldLanguage) {
  return request.patch('/backend/sys/i18n/translation/reset', { fieldKey, fieldLanguage })
}

/**
 * 导出翻译（该模块全量 xlsx 文件流；blobRequest 由调用方处理，见 views/i18n 页面）
 * @param {string} fieldModel 模块名
 * @returns {Promise<import('axios').AxiosResponse>} response.data 为 Blob
 */
export function exportTranslations(fieldModel) {
  return request.post(
    '/backend/sys/i18n/translation/export',
    { fieldModel },
    { responseType: 'blob' }
  )
}

/**
 * 导入翻译（multipart 上传 xlsx，固定覆盖导入）
 * @param {File} file 翻译 xlsx 文件（列头与导出一致）
 * @returns {Promise<Object>} BaseResponse，data 为 { updated, skippedKeys, skippedLanguages }
 */
export function importTranslations(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/backend/sys/i18n/translation/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/* ==================== 部署（deploy） ==================== */

/**
 * 执行部署：手动触发与启动同规则的「资源 → DB 同步」→ 刷新后端缓存
 * @returns {Promise<Object>} BaseResponse，data 为
 *   { syncedModelCount, syncedKeyCount, languageCounts: { 语言码 → 条数 } }
 */
export function runDeploy() {
  return request.post('/backend/sys/i18n/deploy/run')
}

/* ==================== 用户语言（user） ==================== */

/**
 * 当前登录用户语言更新（登录即可；顶部栏切换不调本接口、不落库）
 * @param {string} fieldLanguage 语言码
 * @returns {Promise<Object>} BaseResponse
 */
export function updateUserLanguage(fieldLanguage) {
  return request.patch('/backend/sys/i18n/user/language/update', { fieldLanguage })
}

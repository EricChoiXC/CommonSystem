import request from './index'

/**
 * 系统模块 - 码值接口封装
 *
 * 对应后端 SysDictController：/backend/sys/dict/**
 * - typeList / itemList / items 登录即可访问（码值管理页面全员可见）；
 * - itemSave / itemUpdate / itemDelete 仅 SYS_DICT_ADMIN（或超管 SYS_ADMIN）可调用；
 * - field_system = true 的系统码值不可维护（后端校验）。
 */

/**
 * 码值类型列表：按 field_code 升序返回全部码值类型（页面左侧列表，前端按关键字过滤名称/编号）。
 * @returns {Promise<Object>} BaseResponse，data 为码值类型数组
 */
export function queryDictTypeList() {
  return request.get('/backend/sys/dict/typeList')
}

/**
 * 码值项列表：返回指定类型下的码值项（固定按 field_value 升序）。
 * @param {string} fieldTypeId 码值类型 id
 * @returns {Promise<Object>} BaseResponse，data 为码值项数组
 */
export function queryDictItemList(fieldTypeId) {
  return request.get('/backend/sys/dict/itemList', { params: { fieldTypeId } })
}

/**
 * 新增码值项：仅 SYS_DICT_ADMIN；人工新增的码值固定 field_system = false。
 * @param {Object} body { fieldTypeId, fieldValue, fieldName, fieldEnable }
 * @returns {Promise<Object>} BaseResponse，data 为保存后的码值项
 */
export function saveDictItem(body) {
  return request.post('/backend/sys/dict/itemSave', body)
}

/**
 * 编辑码值项：仅 SYS_DICT_ADMIN；field_system = true 的系统码值不可维护。
 * @param {Object} body { fieldId, fieldTypeId?, fieldValue?, fieldName?, fieldEnable? }
 * @returns {Promise<Object>} BaseResponse，data 为更新后的码值项
 */
export function updateDictItem(body) {
  return request.patch('/backend/sys/dict/itemUpdate', body)
}

/**
 * 删除码值项：仅 SYS_DICT_ADMIN；field_system = true 的系统码值不可删除。
 * @param {string} fieldId 码值项 id
 */
export function deleteDictItem(fieldId) {
  return request.delete('/backend/sys/dict/itemDelete', { params: { fieldId } })
}

/**
 * 通过类型编号获取该类型下已启用的码值项（供下拉与其他模块使用）。
 * @param {string} typeCode 码值类型编号（如 yes_no）
 * @returns {Promise<Object>} BaseResponse，data 为启用码值项数组
 */
export function queryDictItemsByCode(typeCode) {
  return request.post('/backend/sys/dict/items', { typeCode })
}

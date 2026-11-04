import request from './index'

/**
 * 系统模块 - 组织架构（地址本）接口封装
 *
 * 接口路径遵循 docs/系统模块/系统模块API接口设计文档.md 中「地址本选择 addressbook」
 * 分组约定（树选择、列表查询、目标回填数据），后端 Controller 已实现。
 * 另含组织架构管理（element）、角色管理（role）与权限预览（preview）接口。
 * 组织架构类型（org_element_type）、在岗状态（org_job_status）等码值由 dict 模块提供，
 * 见 src/utils/dict.js。
 */
/**
 * 将地址本组件的 orgType 枚举转换为后端可用的类型数字数组
 * @param {string} orgType ALL | ORG | DEPT | ORGDEPT | PERSON | POST | PERSONPOST
 * @returns {number[]}
 */
export function resolveOrgTypes(orgType) {
  const map = {
    ALL: [1, 2, 3, 4],
    ORG: [1],
    DEPT: [2],
    ORGDEPT: [1, 2],
    PERSON: [3],
    POST: [4],
    PERSONPOST: [3, 4]
  }
  return map[orgType] || map.ALL
}

/**
 * 地址本 - 组织架构树查询（左侧树展开与右侧下级列表共用）
 * @param {Object} data { orgTypes, limitRange, parentId }
 * @returns {Promise<Object>} BaseResponse，data 为组织架构元素数组
 */
export function queryAddressBookTree(data) {
  return request.post('/backend/sys/org/addressbook/tree', data)
}

/**
 * 地址本 - 关键字列表分页查询（查询页签，仅根据关键字查询）
 * @param {Object} data { body: { keyword, orgTypes, limitRange }, pages: { pageNo, pageSize, needPage } }
 * @returns {Promise<Object>} BaseResponse，data 为组织架构元素数组
 */
export function queryAddressBookList(data) {
  return request.post('/backend/sys/org/addressbook/list', data)
}

/**
 * 地址本 - 目标回填：根据 id 列表读取名称等展示信息
 * @param {Object} data { body: { ids: string[] } }
 * @returns {Promise<Object>} BaseResponse，data 为组织架构元素数组
 */
export function loadAddressBook(data) {
  return request.post('/backend/sys/org/addressbook/load', data)
}

/* ==================== 组织架构管理（element） ==================== */

/**
 * 组织架构管理 - 列表分页查询
 * 按点中的树节点（parentId 为空表示根级）查询直接下级，支持名称/编码/类型/启用状态筛选
 * 与单条件排序（createTime / updateTime / code + asc / desc）。
 * @param {Object} data { body: { parentId, fieldName, fieldCode, orgType, enable }, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }
 * @returns {Promise<Object>} BaseResponse，data 为组织架构元素数组，总数经 metadata.total 返回
 */
export function queryElementList(data) {
  return request.post('/backend/sys/org/element/list', data)
}

/**
 * 组织架构管理 - 详情读取
 * @param {string} fieldId 组织架构元素 ID
 * @returns {Promise<Object>} BaseResponse，data 为组织架构元素对象
 */
export function loadElement(fieldId) {
  return request.get('/backend/sys/org/element/load', { params: { fieldId } })
}

/**
 * 组织架构管理 - 新增
 * @param {Object} body 组织架构元素对象（含公共字段与类型专有字段）
 * @returns {Promise<Object>} BaseResponse，data 为保存后的组织架构元素对象
 */
export function saveElement(body) {
  return request.post('/backend/sys/org/element/save', body)
}

/**
 * 组织架构管理 - 编辑
 * @param {Object} body 组织架构元素对象（必须携带 fieldId）
 * @returns {Promise<Object>} BaseResponse，data 为保存后的组织架构元素对象
 */
export function updateElement(body) {
  return request.patch('/backend/sys/org/element/update', body)
}

/**
 * 组织架构管理 - 启停用
 * @param {Object} body { fieldId, fieldEnable }
 * @returns {Promise<Object>} BaseResponse
 */
export function enableElement(body) {
  return request.patch('/backend/sys/org/element/enable', body)
}

/**
 * 组织架构管理 - 删除（置为无效）
 * @param {string} fieldId 组织架构元素 ID
 * @returns {Promise<Object>} BaseResponse
 */
export function deleteElement(fieldId) {
  return request.delete('/backend/sys/org/element/delete', { params: { fieldId } })
}

/* ==================== 用户维护（user） ==================== */

/**
 * 用户维护 - 修改密码
 * 仅更新密码与最后修改密码时间，不影响用户其他字段；
 * 鉴权与用户编辑一致：SYS_ORG_ADMIN / SYS_ORG_USER_MANAGER / 本人。
 * @param {string} fieldId 用户 ID（sys_org_user.field_id）
 * @param {string} fieldPassword 明文新密码
 * @returns {Promise<Object>} BaseResponse
 */
export function changePwd(fieldId, fieldPassword) {
  return request.patch('/backend/sys/org/user/changePwd', { body: { fieldId, fieldPassword } })
}

/* ==================== 角色管理（role） ==================== */

/**
 * 角色管理 - 列表分页查询
 * 筛选项：角色名、编码、是否启用（默认「是」）；仅返回基本字段，无批量操作。
 * @param {Object} data { body: { fieldName, fieldCode, enable }, pages: { pageNo, pageSize, needPage } }
 * @returns {Promise<Object>} BaseResponse，data 为角色数组，总数经 metadata.total 返回
 */
export function queryRoleList(data) {
  return request.post('/backend/sys/org/role/list', data)
}

/**
 * 角色管理 - 详情读取（含关联组织架构 elementIds/elementNames 与已选权限 authIds）
 * @param {string} fieldId 角色 ID
 * @returns {Promise<Object>} BaseResponse，data 为角色对象
 */
export function loadRole(fieldId) {
  return request.get('/backend/sys/org/role/load', { params: { fieldId } })
}

/**
 * 角色管理 - 新增（含关联组织架构与权限，关联表全量写入）
 * @param {Object} body { fieldId, fieldName, fieldCode, fieldEnable, elementIds, authIds }
 * @returns {Promise<Object>} BaseResponse
 */
export function saveRole(body) {
  return request.post('/backend/sys/org/role/save', body)
}

/**
 * 角色管理 - 编辑（关联表先删后插全量替换）
 * @param {Object} body 角色对象（必须携带 fieldId）
 * @returns {Promise<Object>} BaseResponse
 */
export function updateRole(body) {
  return request.patch('/backend/sys/org/role/update', body)
}

/**
 * 角色管理 - 权限可选项
 * 按 sys_org_auth.field_model 分组，分组标题优先取 application.yml 的 MODEL_NAME。
 * @returns {Promise<Object>} BaseResponse，data 为 AuthGroupVo 数组
 */
export function queryAuthOptions() {
  return request.get('/backend/sys/org/role/authOptions')
}

/* ==================== 权限预览（preview） ==================== */

/**
 * 权限预览 - 选中组织架构的概览数据
 * @param {string} elementId 组织架构元素 ID
 * @returns {Promise<Object>} BaseResponse，data 为 { element, relatedOrgs, roles, auths }
 */
export function previewRole(elementId) {
  return request.post('/backend/sys/org/preview/preview', { elementId })
}

/**
 * 权限预览 - 权限树子节点懒加载
 * @param {Object} body { type: 'element' | 'role', id }
 * @returns {Promise<Object>} BaseResponse，data 为 PreviewTreeChildVo 数组
 */
export function previewTreeChildren(body) {
  return request.post('/backend/sys/org/preview/treeChildren', body)
}

import request from './index'

/**
 * 通用接口封装
 *
 * 遵循 docs/开发规范.md「前端新建页/弹窗 fieldId 初始化规范」：
 * 新建页面/新建弹窗打开时必须先调用 getInitID() 获取 32 位主键 ID，
 * 并赋值到表单对象的 id 字段上，后续 save 请求将该 ID 提交给后端。
 */

/**
 * 预取 32 位主键 ID
 * @returns {Promise<Object>} BaseResponse，data 为 32 位 ID 字符串
 */
export function getInitID() {
  return request.get('/backend/common/initID')
}

/**
 * 读取系统初始化信息（应用名称等公共展示数据）
 * 应用名称取后端 application.properties 的 application.name，为空时后端回退默认应用名。
 * @returns {Promise<Object>} BaseResponse，data 为 { applicationName }
 */
export function getInitInfo() {
  return request.get('/backend/common/init')
}

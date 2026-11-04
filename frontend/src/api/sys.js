import request from './index'

// 系统模块接口封装：与后端 /backend/sys/** 对应

/**
 * 登录
 * @param {Object} data 请求体，如 { body: { fieldLoginName, fieldPassword } }
 * @returns {Promise<Object>} BaseResponse，data 为登录用户（不含密码），metadata 携带会话 token
 */
export function login(data) {
  return request.post('/backend/sys/org/user/login', data)
}

/**
 * 登出：注销当前会话 token
 */
export function logout() {
  return request.post('/backend/sys/org/user/logout')
}

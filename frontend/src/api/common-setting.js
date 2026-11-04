import request from './index'

/**
 * 公共配置接口封装
 * 对应后端 /backend/common/setting/**
 */

/**
 * 读取系统配置
 * @returns {Promise<Object>} BaseResponse，data 为 { fieldKey: fieldValue } 映射
 */
export function loadSetting() {
  return request.get('/backend/common/setting/load')
}

/**
 * 更新单个配置项
 * @param {string} fieldKey 配置字段
 * @param {string} fieldValue 配置值
 * @returns {Promise<Object>} BaseResponse
 */
export function updateSetting(fieldKey, fieldValue) {
  return request.patch('/backend/common/setting/update', { fieldKey, fieldValue })
}
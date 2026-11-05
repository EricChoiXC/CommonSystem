import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/stores/user'
import { TABS_STORAGE_KEY } from '@/utils/tabs'

// 登录凭证存储键（与 stores/user.js 保持一致）
const TOKEN_KEY = 'common_token'

// 统一 axios 实例：接口使用相对路径 /backend/...，由 Vite 代理转发到后端
const request = axios.create({
  timeout: 15000
})

// 请求拦截：自动携带登录凭证，后端 CurrentUserFilter 据此还原当前用户
request.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers['X-Login-Token'] = token
  }
  return config
})

// —— token 失效处理 ——
// 后端对「未登录 / 凭证无效 / 凭证过期」统一返回业务码 UNAUTHORIZED（HTTP 200），
// 对「已登录但权限不足」返回 FORBIDDEN（仅提示，不跳转）；HTTP 401 作为兜底信号。

/** 上次触发跳转的时间戳，冷却期内忽略后续失效响应，避免并发请求重复提示/跳转 */
let lastRedirectAt = 0
const REDIRECT_COOLDOWN = 2000

/**
 * 凭证失效统一处理：清理本地登录态、提示并跳转登录页。
 * 供主请求实例与附件 Blob 实例共用；带冷却期去抖。
 */
export function handleUnauthorized(message = '登录已过期，请重新登录') {
  const now = Date.now()
  if (now - lastRedirectAt < REDIRECT_COOLDOWN) {
    return
  }
  lastRedirectAt = now
  // 先清理本地登录态，再跳转，避免路由守卫将已登出用户再次重定向回业务页
  const userStore = useUserStore()
  userStore.logout()
  // 同步清空页签与 KeepAlive 缓存，避免下一个用户登录后看到上一用户残留的页签
  window.sessionStorage.removeItem(TABS_STORAGE_KEY)
  ElMessage.warning(message)
  if (router.currentRoute.value.path !== '/login') {
    router.replace('/login')
  }
}

// 响应拦截：统一解包 BaseResponse，code 非 SUCCESS 视为业务失败；
// 凭证失效（UNAUTHORIZED / HTTP 401）时自动清理登录态并跳转登录页
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && res.code === 'UNAUTHORIZED') {
      handleUnauthorized(res.message)
      return Promise.reject(new Error(res.message || '登录已过期，请重新登录'))
    }
    if (res && res.code === 'SUCCESS') {
      return res
    }
    const message = (res && res.message) || '请求失败'
    return Promise.reject(new Error(message))
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      const message =
        (error.response.data && error.response.data.message) || '登录已过期，请重新登录'
      handleUnauthorized(message)
      return Promise.reject(new Error(message))
    }
    const message =
      (error.response && error.response.data && error.response.data.message) ||
      error.message ||
      '网络错误'
    return Promise.reject(new Error(message))
  }
)

export default request

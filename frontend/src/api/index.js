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

/** 上次弹出模块未开启提示的时间戳，冷却期内忽略后续提示，避免并发请求重复弹消息 */
let lastModuleTipAt = 0

/**
 * MODULE_DISABLED 统一提示：仅提示、不跳转、不清登录态（区别于 UNAUTHORIZED）。
 * 拦截器提示后仍向调用方抛出失败结果，页面可按需再做局部处理。
 */
function showModuleDisabledTip(message = '该模块未开启，请联系管理员') {
  const now = Date.now()
  if (now - lastModuleTipAt < REDIRECT_COOLDOWN) {
    return
  }
  lastModuleTipAt = now
  ElMessage.warning(message)
}

// 响应拦截：统一解包 BaseResponse，code 非 SUCCESS 视为业务失败；
// 凭证失效（UNAUTHORIZED / HTTP 401）时自动清理登录态并跳转登录页；
// 模块组关闭（MODULE_DISABLED）仅统一提示不跳转；
// blob 请求（responseType === 'blob'）按响应 Content-Type 区分文件流透传与 Blob 内 JSON 业务失败
request.interceptors.response.use(
  async (response) => {
    // blob 请求分支：导出等文件下载走此通道。判定依据：HTTP 200 时若响应头 Content-Type
    // 含 application/json，说明后端把业务失败 BaseResponse（JSON）包装在 Blob 里返回，
    // 需解出 code / message 后复用与普通请求一致的统一处理；否则视为文件流。
    if (response.config && response.config.responseType === 'blob') {
      const contentType =
        (response.headers && (response.headers['content-type'] || response.headers['Content-Type'])) || ''
      if (String(contentType).includes('application/json')) {
        // HTTP 200 + JSON：业务失败包在 Blob 里，解析后按既有 code 逻辑处理；
        // Blob -> JSON 解析必须容错，解析失败按空响应处理走「请求失败」兜底
        let res = null
        try {
          res = JSON.parse(await response.data.text())
        } catch (e) {
          res = null
        }
        if (res && res.code === 'UNAUTHORIZED') {
          handleUnauthorized(res.message)
          return Promise.reject(new Error(res.message || '登录已过期，请重新登录'))
        }
        if (res && res.code === 'MODULE_DISABLED') {
          showModuleDisabledTip(res.message)
          return Promise.reject(new Error(res.message || '该模块未开启，请联系管理员'))
        }
        if (res && res.code === 'SUCCESS') {
          return res
        }
        const message = (res && res.message) || '请求失败'
        return Promise.reject(new Error(message))
      }
      // 文件流：原样透传完整 AxiosResponse（不解包），调用方消费 response.data（Blob）
      return response
    }
    const res = response.data
    if (res && res.code === 'UNAUTHORIZED') {
      handleUnauthorized(res.message)
      return Promise.reject(new Error(res.message || '登录已过期，请重新登录'))
    }
    if (res && res.code === 'MODULE_DISABLED') {
      showModuleDisabledTip(res.message)
      return Promise.reject(new Error(res.message || '该模块未开启，请联系管理员'))
    }
    if (res && res.code === 'SUCCESS') {
      return res
    }
    const message = (res && res.message) || '请求失败'
    return Promise.reject(new Error(message))
  },
  async (error) => {
    // blob 请求出错时 error.response.data 可能是 Blob（HTTP 401 等错误体被 axios 按 blob 解析），
    // 先把 Blob 还原为文本并尝试解析出 BaseResponse 的 message；
    // 解析失败（非 JSON）回退为空，走下方既有兜底链，不影响普通请求的错误提示
    let blobMessage = ''
    if (error.response && error.response.data instanceof Blob) {
      try {
        const parsed = JSON.parse(await error.response.data.text())
        if (parsed && typeof parsed.message === 'string') {
          blobMessage = parsed.message
        }
      } catch (e) {
        blobMessage = ''
      }
    }
    if (error.response && error.response.status === 401) {
      const message =
        blobMessage ||
        (error.response.data && error.response.data.message) ||
        '登录已过期，请重新登录'
      handleUnauthorized(message)
      return Promise.reject(new Error(message))
    }
    const message =
      blobMessage ||
      (error.response && error.response.data && error.response.data.message) ||
      error.message ||
      '网络错误'
    return Promise.reject(new Error(message))
  }
)

export default request

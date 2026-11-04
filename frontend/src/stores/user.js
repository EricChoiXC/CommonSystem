import { defineStore } from 'pinia'

// 登录态本地存储键：刷新页面后保持登录
const USER_KEY = 'common_user'
const TOKEN_KEY = 'common_token'

/**
 * 用户登录态 store
 * 登录成功后将用户信息与会话 token 写入 state 与 localStorage；登出时清除。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    userInfo: JSON.parse(localStorage.getItem(USER_KEY) || 'null'),
    token: localStorage.getItem(TOKEN_KEY) || ''
  }),
  getters: {
    isLoggedIn: (state) => !!state.userInfo && !!state.userInfo.fieldId && !!state.token
  },
  actions: {
    setLogin(userInfo, token) {
      this.userInfo = userInfo
      this.token = token || ''
      localStorage.setItem(USER_KEY, JSON.stringify(userInfo))
      if (this.token) {
        localStorage.setItem(TOKEN_KEY, this.token)
      }
    },
    logout() {
      this.userInfo = null
      this.token = ''
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem(TOKEN_KEY)
    }
  }
})

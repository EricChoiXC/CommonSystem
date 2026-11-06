import { defineStore } from 'pinia'
import i18n from '@/locales'
import { updateUserLanguage } from '@/api/i18n'

/**
 * 当前语言 store：语言包加载、切换与 localStorage 记忆
 *
 * 顶部栏切换仅改前端 locale（不调用户语言接口、不落库）；
 * 仅登录页选语言登录时经后端登录契约（fieldLanguage）落库。
 * localStorage 键与 stores/user.js 风格一致，登录前亦可记忆选择。
 */
const LANGUAGE_STORAGE_KEY = 'common_language'

/** 从 localStorage 读取记忆语言（无则回退默认 zh_CN） */
function readStoredLanguage() {
  return localStorage.getItem(LANGUAGE_STORAGE_KEY) || i18n.global.fallbackLocale.value || 'zh_CN'
}

export const useLanguageStore = defineStore('language', {
  state: () => ({
    /** 当前语言码（如 zh_CN）；初始化时自 localStorage 记忆值回填 */
    current: readStoredLanguage(),
    /** 已加载语言包缓存：语言码 → 扁平 {key: message}（避免重复请求） */
    loaded: {}
  }),
  getters: {
    /** 该语言包是否已加载（供顶部栏下拉展示加载态等场景） */
    isLoaded: (state) => (code) => !!state.loaded[code]
  },
  actions: {
    /**
     * 初始化：应用启动时按记忆语言拉取语言包并应用。
     * 语言包接口异常（后端未就绪 / 语言码未启用）时静默回退 vue-i18n 内置空包，
     * 页面文案按 key 展示，不阻塞应用启动。
     */
    async init() {
      await this.loadPackage(this.current)
    },
    /**
     * 拉取并应用语言包（幂等：已加载直接返回）
     * @param {string} code 语言码（如 zh_CN）
     */
    async loadPackage(code) {
      if (!code || this.loaded[code]) {
        if (code) this.applyLocale(code)
        return
      }
      // 免鉴权动态接口 /locales/{code}.json：fetch 直连，不走 axios 实例
      // （登录前无 token；响应为扁平 {key: message}，非 BaseResponse 包装）
      const response = await fetch(`/locales/${encodeURIComponent(code)}.json`)
      if (!response.ok) {
        throw new Error(`语言包加载失败: ${code} (${response.status})`)
      }
      const messages = await response.json()
      this.loaded[code] = messages
      i18n.global.setLocaleMessage(code, messages)
      this.applyLocale(code)
    },
    /**
     * 切换语言：懒加载对应语言包 → 更新 vue-i18n locale → localStorage 记忆。
     * Element Plus locale 由根组件按 current 联动（App.vue）。
     * @param {string} code 语言码
     */
    async setLanguage(code) {
      if (!code || code === this.current) return
      await this.loadPackage(code)
    },
    /** 内部：应用 locale 与本地记忆 */
    applyLocale(code) {
      this.current = code
      i18n.global.locale.value = code
      localStorage.setItem(LANGUAGE_STORAGE_KEY, code)
    },
    /**
     * 登录页选语言登录后调用：将所选语言登记到用户语言表（后端登录契约已随登录 upsert，
     * 此处仅用于已登录用户在登录页之外重新登记的补充场景；失败静默，不影响前端 locale）
     * @param {string} code 语言码
     */
    async registerUserLanguage(code) {
      if (!code) return
      try {
        await updateUserLanguage(code)
      } catch {
        // 落库失败不影响前端语言切换（顶部栏切换本身也不落库）
      }
    }
  }
})

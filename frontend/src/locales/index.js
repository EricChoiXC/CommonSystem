import { createI18n } from 'vue-i18n'

/**
 * vue-i18n 实例（前端国际化基建，任务清单 I18N6-FE-001）
 *
 * - 语言包不在构建期打包，运行时经免鉴权动态接口 `GET /locales/{code}.json`
 *   拉取（D2 决策），由语言包加载器（stores/language.js）调用 setLocaleMessage 注入；
 * - 默认语言 zh_CN（与后端 I18nService.DEFAULT_LANGUAGE 一致）、回退语言同为 zh_CN；
 * - 首次进入（登录页）时语言包尚未注入，页面文案短暂按 key 展示属预期，
 *   语言包加载器会在应用挂载前完成加载（main.js 启动时序保证）；
 * - legacy: false 使用组合式 API 模式，配合 <script setup> 全局 $t 可用。
 */
const i18n = createI18n({
  legacy: false,
  locale: 'zh_CN',
  fallbackLocale: 'zh_CN',
  // 生产环境关闭运行时告警（缺 key 回退 key 本身由 vue-i18n 默认行为保证）
  missingWarn: false,
  fallbackWarn: false,
  messages: {}
})

export default i18n

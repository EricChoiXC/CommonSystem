import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

import App from './App.vue'
import router from './router'
import i18n from './locales'
import { useLanguageStore } from '@/stores/language'
import './styles/base.css'
// 页面公共样式：列表查询页三段式结构 / 新建编辑查看页公共布局
import './styles/list-page.css'
import './styles/form-page.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')

// 启动时按 localStorage 记忆语言（无则 zh_CN）拉取语言包并应用；
// 放在 mount 之后异步执行：加载失败不阻塞应用启动（页面文案按 key 展示的兜底见语言包加载器）
const languageStore = useLanguageStore()
languageStore.init()

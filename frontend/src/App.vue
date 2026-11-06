<template>
  <el-config-provider :locale="elementLocale">
    <RouterView />
  </el-config-provider>
</template>

<script setup>
import { computed } from 'vue'
import { RouterView } from 'vue-router'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import enUs from 'element-plus/dist/locale/en.mjs'
import { useLanguageStore } from '@/stores/language'

// Element Plus 组件文案随当前语言联动（分页、弹窗按钮、日期选择器等）；
// 未覆盖的语言码回退 zh_CN（与 vue-i18n fallbackLocale 一致）
const languageStore = useLanguageStore()
const ELEMENT_LOCALES = {
  zh_CN: zhCn,
  en_US: enUs
}
const elementLocale = computed(() => ELEMENT_LOCALES[languageStore.current] || zhCn)
</script>

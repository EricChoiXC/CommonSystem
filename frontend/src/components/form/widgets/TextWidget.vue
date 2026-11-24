<template>
  <!--
    静态说明文字（F15-FE-007，§1.3 text 行：content 静态说明文字；不承载数据）。
    detail/design 形态同展示；design 形态（经 disabled 控制位识别，FormRenderer
    controlDisabled=design）内容为空时展示占位，便于画布识别组件占位范围。
  -->
  <p v-if="hasContent" class="text-widget-content">{{ content }}</p>
  <p v-else-if="disabled" class="text-widget-placeholder">静态文本（未配置内容）</p>
  <p v-else class="text-widget-empty"></p>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 文本 widget（type=text，功能详细设计 §1.3 内容类 text 行）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明——内容类不承载数据、无数据绑定语义，保留声明
 *   对齐统一契约、不触发回写）+ 扩展属性项 content + 统一控制位 disabled/readonly
 *   （静态内容无交互，控制位仅契约保留；design 形态经 disabled 识别展示空内容占位）；
 * - emits：update:modelValue / change / focus / blur（无交互时机，保留声明、不触发）；
 * - 值形态：不进数据载荷（物料 dataType=null，§1.3）；
 * - 固定属性项由渲染器层承载，本组件只渲染内容本体，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值（内容类不承载数据，保留声明对齐统一契约） */
  modelValue: { type: null, default: null },
  /** 文本内容（静态说明文字，支持换行——white-space: pre-wrap 保留） */
  content: { type: String, default: '' },
  /** 统一控制位：禁用（design 形态经此识别，空内容展示占位） */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（静态内容无交互，契约保留） */
  readonly: { type: Boolean, default: false },
})

// 统一契约四事件保留声明；静态内容无交互时机，不触发
const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])
void emit

const hasContent = computed(() => props.content !== null && props.content !== undefined && String(props.content) !== '')
</script>

<style scoped>
.text-widget-content {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.text-widget-placeholder,
.text-widget-empty {
  margin: 0;
  color: var(--el-text-color-placeholder);
  font-size: 14px;
  line-height: 1.6;
}
</style>

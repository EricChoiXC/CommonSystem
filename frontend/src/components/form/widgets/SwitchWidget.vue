<template>
  <!-- 开关控件（F15-FE-003，§1.3 switch 行：onText【开】显示文本 / offText【关】显示文本） -->
  <div ref="wrapperRef" class="form-switch-widget" @focusin="handleFocusIn" @focusout="handleFocusOut">
    <el-switch
      :model-value="booleanValue"
      :active-text="onText"
      :inactive-text="offText"
      :disabled="disabled || readonly"
      @update:model-value="handleUpdate"
      @change="handleChange"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

/**
 * 开关 widget（type=switch，功能详细设计 §1.3 switch 行）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明）+ 扩展属性项 onText/offText + 统一控制位
 *   disabled/readonly（switch 无只读半态，readonly 按禁用置灰处理）；
 * - emits：update:modelValue（BOOLEAN 规范值）/ change（新值）/ focus / blur（switch
 *   原生不冒泡 focus，经容器 focusin/focusout 捕获转发）；
 * - 值形态 BOOLEAN：switch=false 是有义值（后端 BE-003 同口径，不按空值处理），
 *   非布尔脏值按 false 展示、切换后回写严格布尔；
 * - 固定属性项由渲染器层承载，本组件只渲染控件本体，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值（BOOLEAN，false 为有义值） */
  modelValue: { type: Boolean, default: false },
  /** 【开】显示文本（materials defaultProps 默认「开」） */
  onText: { type: String, default: '开' },
  /** 【关】显示文本（materials defaultProps 默认「关」） */
  offText: { type: String, default: '关' },
  /** 统一控制位：禁用置灰 */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（switch 无只读半态，按禁用置灰处理） */
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

const wrapperRef = ref(null)

/** 展示态归一：非布尔脏值按 false 展示（切换后回写严格布尔，值形态 BOOLEAN） */
const booleanValue = computed(() => props.modelValue === true)

function handleUpdate(value) {
  emit('update:modelValue', value === true)
}

function handleChange(value) {
  emit('change', value === true)
}

/** switch 内部 input 获得焦点（原生 focus 不冒泡，focusin 捕获） */
function handleFocusIn(event) {
  emit('focus', event)
}

/** 焦点移出开关容器 */
function handleFocusOut(event) {
  if (wrapperRef.value && event.relatedTarget && wrapperRef.value.contains(event.relatedTarget)) return
  emit('blur', event)
}
</script>

<style scoped>
/* 容器仅承载焦点捕获，行内铺排不改变表单布局 */
.form-switch-widget {
  display: inline-flex;
  align-items: center;
}
</style>

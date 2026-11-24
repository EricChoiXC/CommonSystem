<template>
  <!-- 多行文本控件（F15-FE-003，§1.3 textarea 行：maxlength / 自适应行高 autosize） -->
  <el-input
    type="textarea"
    :model-value="textValue"
    :maxlength="maxlength > 0 ? maxlength : undefined"
    :show-word-limit="maxlength > 0"
    :autosize="{ minRows: 3, maxRows: 8 }"
    :placeholder="placeholder || '请输入'"
    :disabled="disabled"
    :readonly="readonly"
    @update:model-value="handleInput"
    @change="handleChange"
    @focus="handleFocus"
    @blur="handleBlur"
  />
</template>

<script setup>
import { computed } from 'vue'

/**
 * 多行文本 widget（type=textarea，功能详细设计 §1.3 textarea 行）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明）+ 扩展属性项 maxlength + 统一控制位
 *   disabled/readonly（readonly 传 el-input 只读属性）；
 * - emits：update:modelValue（规范值，空串归 null）/ change（新值，失焦或回车时
 *   触发）/ focus / blur；
 * - 值形态 STRING，空值统一 null；
 * - maxlength 默认 200（对齐 materials defaultProps 与 UI 分册 §4.5.2 textarea 行
 *   「默认 200」，应用层上限 MAX_TEXT_LENGTH=2000 由属性面板约束）；
 * - 固定属性项由渲染器层承载，本组件只渲染控件本体，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值（STRING，空值 null） */
  modelValue: { type: String, default: null },
  /** 最大长度（materials defaultProps 默认 200） */
  maxlength: { type: Number, default: 200 },
  /** 占位提示 */
  placeholder: { type: String, default: '' },
  /** 统一控制位：禁用置灰 */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读 */
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

/** 回显展示：null/undefined 归空串 */
const textValue = computed(() => (props.modelValue === null || props.modelValue === undefined ? '' : String(props.modelValue)))

/** 空串归 null（值形态契约：空值统一 null） */
function canonical(value) {
  return value === '' || value === null || value === undefined ? null : value
}

/** 输入中：实时回写规范值（空串归 null），不触发 change */
function handleInput(value) {
  emit('update:modelValue', canonical(value))
}

/** 提交（失焦/回车）：change 事件统一分发新值 */
function handleChange(value) {
  emit('change', canonical(value))
}

function handleFocus(event) {
  emit('focus', event)
}

function handleBlur(event) {
  emit('blur', event)
}
</script>

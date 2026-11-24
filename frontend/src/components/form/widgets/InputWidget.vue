<template>
  <!-- 单行文本控件（F15-FE-003，§1.3 input 行：maxlength 默认 200 / password 密码并入本属性） -->
  <el-input
    :model-value="textValue"
    :maxlength="maxlength > 0 ? maxlength : undefined"
    :show-word-limit="maxlength > 0"
    :show-password="password"
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
 * 单行文本 widget（type=input，功能详细设计 §1.3 input 行）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明）+ 扩展属性项 maxlength/password + 统一控制位
 *   disabled/readonly（readonly 传 el-input 只读属性，文本可选中不可改）；
 * - emits：update:modelValue（规范值，空串归 null）/ change（新值，el-input 失焦或
 *   回车时触发，渲染器据此分发事件总线）/ focus / blur；
 * - 值形态 STRING，空值统一 null（不出现 undefined / 空串残留）；
 * - 固定属性项（label/field/required/tooltip/defaultValue）由渲染器层承载，本组件
 *   只渲染控件本体、不渲染 form-item，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值（STRING，空值 null） */
  modelValue: { type: String, default: null },
  /** 最大长度（§1.3 默认 200，应用层上限 MAX_TEXT_LENGTH=2000 由属性面板约束） */
  maxlength: { type: Number, default: 200 },
  /** 是否密码（R03：密码并入单行文本属性，true 时掩码输入 show-password） */
  password: { type: Boolean, default: false },
  /** 占位提示（materials defaultProps 未配置时用控件默认） */
  placeholder: { type: String, default: '' },
  /** 统一控制位：禁用置灰 */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（字段权限 readonly / 详情态注入） */
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

/** 回显展示：null/undefined 归空串，避免 undefined 残留进 el-input */
const textValue = computed(() => (props.modelValue === null || props.modelValue === undefined ? '' : String(props.modelValue)))

/** 空串归 null（值形态契约：空值统一 null） */
function canonical(value) {
  return value === '' || value === null || value === undefined ? null : value
}

/** 输入中：实时回写规范值（空串归 null），不触发 change */
function handleInput(value) {
  emit('update:modelValue', canonical(value))
}

/** 提交（失焦/回车，值有变更时才触发）：change 事件统一分发新值 */
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

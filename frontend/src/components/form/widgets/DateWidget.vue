<template>
  <!--
    日期/时间控件（F15-FE-003，§1.3 date 行：dateType DATE 日期 / TIME 时间 /
    DATETIME 日期时间，format 显示格式）。format 与 value-format 同值（props.format），
    值以格式化字符串落库，避免时区歧义（§1.3 值形态口径）。
  -->
  <el-date-picker
    v-if="dateType === 'DATETIME'"
    :model-value="modelValue"
    type="datetime"
    :format="format"
    :value-format="format"
    :placeholder="placeholder || '请选择日期时间'"
    :disabled="disabled"
    :readonly="readonly"
    @update:model-value="handleUpdate"
    @change="handleChange"
    @focus="handleFocus"
    @blur="handleBlur"
  />
  <el-time-picker
    v-else-if="dateType === 'TIME'"
    :model-value="modelValue"
    :format="format"
    :value-format="format"
    :placeholder="placeholder || '请选择时间'"
    :disabled="disabled"
    :readonly="readonly"
    @update:model-value="handleUpdate"
    @change="handleChange"
    @focus="handleFocus"
    @blur="handleBlur"
  />
  <el-date-picker
    v-else
    :model-value="modelValue"
    type="date"
    :format="format"
    :value-format="format"
    :placeholder="placeholder || '请选择日期'"
    :disabled="disabled"
    :readonly="readonly"
    @update:model-value="handleUpdate"
    @change="handleChange"
    @focus="handleFocus"
    @blur="handleBlur"
  />
</template>

<script setup>
import { computed } from 'vue'

/**
 * 日期 widget（type=date，功能详细设计 §1.3 date 行）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明）+ 扩展属性项 dateType/format + 统一控制位
 *   disabled/readonly（date-picker/time-picker 原生支持 readonly 只读形态）；
 * - emits：update:modelValue（规范值，清空归 null）/ change（新值）/ focus / blur；
 * - 值形态 STRING（props.format 格式化字符串，经 Element Plus value-format 实现，
 *   避免时区歧义）；format 缺省时按 dateType 取默认格式（对齐公共组件
 *   DateTimePicker 口径：YYYY-MM-DD / HH:mm:ss / YYYY-MM-DD HH:mm:ss，dayjs token）；
 * - 固定属性项由渲染器层承载，本组件只渲染控件本体，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值（props.format 格式化字符串，空值 null） */
  modelValue: { type: String, default: null },
  /** 类型：DATE 日期 / TIME 时间 / DATETIME 日期时间 */
  dateType: { type: String, default: 'DATE' },
  /** 显示格式（dayjs token；format 与 value-format 同值，值以格式化字符串落库） */
  format: { type: String, default: '' },
  /** 占位提示 */
  placeholder: { type: String, default: '' },
  /** 统一控制位：禁用置灰 */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读 */
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

/** 默认格式按 dateType 联动（对齐公共 DateTimePicker 与 UI 分册 §4.5.2 date 行可选集） */
const DEFAULT_FORMATS = {
  DATE: 'YYYY-MM-DD',
  TIME: 'HH:mm:ss',
  DATETIME: 'YYYY-MM-DD HH:mm:ss',
}

/** 生效格式：props.format 优先，缺省回落 dateType 对应默认格式 */
const format = computed(() => props.format || DEFAULT_FORMATS[props.dateType] || DEFAULT_FORMATS.DATE)

/** 空值归 null（EP 清空回 null，防御性归一空串） */
function canonical(value) {
  return value === '' || value === null || value === undefined ? null : value
}

function handleUpdate(value) {
  emit('update:modelValue', canonical(value))
}

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

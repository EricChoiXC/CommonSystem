<template>
  <!-- 下拉选择控件（F15-FE-003，§1.3 select 行：multi 单多选 / options[{label, value}]） -->
  <el-select
    :model-value="selectValue"
    :multiple="multi"
    :collapse-tags="multi"
    :collapse-tags-tooltip="multi"
    clearable
    :placeholder="placeholder || '请选择'"
    :disabled="disabled || readonly"
    @update:model-value="handleUpdate"
    @change="handleChange"
    @focus="handleFocus"
    @blur="handleBlur"
  >
    <el-option v-for="option in options" :key="String(option.value)" :label="option.label" :value="option.value" />
  </el-select>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 下拉选择器 widget（type=select，功能详细设计 §1.3 select 行；一期选项静态配置，
 * optionsSource=dict 协议位二期开放）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明）+ 扩展属性项 multi/options + 统一控制位
 *   disabled/readonly（el-select 无只读形态，readonly 按禁用置灰处理）；
 * - emits：update:modelValue（单选 STRING、多选 ARRAY，空值单选 null / 多选 []）/
 *   change（新值）/ focus / blur；
 * - 值形态：单选 STRING、多选 ARRAY（§1.3 dataType 随 props.multi 多态）；多选
 *   multiple + collapse-tags（任务卡口径），单选可清空（清空归 null）；
 * - 固定属性项由渲染器层承载，本组件只渲染控件本体，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值（单选 STRING / 多选 ARRAY，空值 null / []） */
  modelValue: { type: [String, Number, Array], default: null },
  /** 单多选（true 多选，值为数组） */
  multi: { type: Boolean, default: false },
  /** 选择项 [{label 显示值, value 实际值}]（一期静态配置） */
  options: { type: Array, default: () => [] },
  /** 占位提示 */
  placeholder: { type: String, default: '' },
  /** 统一控制位：禁用置灰 */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（el-select 无只读形态，按禁用置灰处理） */
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

/** 回显归一：单选 null/空串归 null；多选容忍脏形态归一数组（标量兜底包一层） */
const selectValue = computed(() => {
  if (props.multi) {
    return toArray(props.modelValue)
  }
  if (props.modelValue === '' || props.modelValue === null || props.modelValue === undefined) return null
  return props.modelValue
})

/** 空值归一：null/undefined/空串 → []；标量兜底包一层数组 */
function toArray(value) {
  if (Array.isArray(value)) return value
  if (value === null || value === undefined || value === '') return []
  return [value]
}

/** 单选空值归 null（值形态契约：空值统一 null，EP 清空回 '' 也归 null） */
function canonicalSingle(value) {
  if (value === '' || value === null || value === undefined) return null
  return value
}

function handleUpdate(value) {
  emit('update:modelValue', props.multi ? toArray(value) : canonicalSingle(value))
}

function handleChange(value) {
  emit('change', props.multi ? toArray(value) : canonicalSingle(value))
}

function handleFocus(event) {
  emit('focus', event)
}

function handleBlur(event) {
  emit('blur', event)
}
</script>

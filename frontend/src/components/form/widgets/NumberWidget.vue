<template>
  <!--
    数字控件（F15-FE-003，§1.3 number 行：numberType INTEGER/DECIMAL/PERCENT/AMOUNT、
    precision、max/min、allowNegative、separator NONE/THOUSAND/TEN_THOUSAND/HUNDRED_MILLION）。
    显示态（失焦）展示格式化文本（分组/百分号/金额符号）；编辑态（聚焦）展示原始数值
    文本便于修改；失焦/回车时经 normalizeNumber 精度归一 + 范围钳制后回写规范值。
  -->
  <el-input
    :model-value="displayText"
    :placeholder="placeholder || '请输入数字'"
    :disabled="disabled"
    :readonly="readonly"
    @update:model-value="handleInput"
    @focus="handleFocus"
    @blur="handleBlur"
    @keydown.enter="handleEnter"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import { formatNumber, parseNumber, normalizeNumber } from './numberFormat.js'

/**
 * 数字 widget（type=number，功能详细设计 §1.3 number 行）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明，NUMBER 数值）+ 扩展属性项
 *   numberType/precision/max/min/allowNegative/separator + 统一控制位 disabled/readonly；
 * - emits：update:modelValue（规范值；输入中回写解析值、失焦/回车回写钳制归一值，
 *   空值恒 null）/ change（失焦或回车且值有变更时的新值）/ focus / blur；
 * - 值形态 NUMBER；格式化/解析/钳制逻辑收口 numberFormat.js 纯函数（node 直测），
 *   本组件只做薄封装；
 * - 固定属性项由渲染器层承载，本组件只渲染控件本体，不感知规则（架构原则 2）。
 *
 * 收口时序（§1.3「失焦或变更时按 precision 归一」）：编辑中途不收口（保留「12.」
 * 「-」等中间态），失焦/回车时 parseNumber → normalizeNumber（先精度归一、后
 * min/max/负数钳制）统一收口。
 */
const props = defineProps({
  /** 字段值（NUMBER 数值，空值 null；容忍回显数值字符串形态） */
  modelValue: { type: [Number, String], default: null },
  /** 数字类型：INTEGER 整数 / DECIMAL 小数 / PERCENT 百分比 / AMOUNT 金额 */
  numberType: { type: String, default: 'DECIMAL' },
  /** 精度（小数位，INTEGER 恒 0 位；未配置不归一） */
  precision: { type: [Number, String], default: null },
  /** 最大值（未配置不设上界） */
  max: { type: [Number, String], default: null },
  /** 最小值（未配置不设下界；allowNegative=false 时负值一律收口 0） */
  min: { type: [Number, String], default: null },
  /** 允许负数（false 时负值失焦收口 0） */
  allowNegative: { type: Boolean, default: false },
  /** 分隔符：NONE 无 / THOUSAND 千分位 / TEN_THOUSAND 万分位 / HUNDRED_MILLION 亿分位 */
  separator: { type: String, default: 'NONE' },
  /** 占位提示 */
  placeholder: { type: String, default: '' },
  /** 统一控制位：禁用置灰 */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（展示格式化文本、不可编辑） */
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

/** 是否处于编辑态（编辑态输入框保留用户原始输入，失焦后展示格式化文本） */
const focused = ref(false)
/** 编辑态原始输入文本 */
const editingText = ref('')

/** 展示文本：编辑态显示原始输入，其余（含 readonly/disabled）显示格式化文本 */
const displayText = computed(() => {
  if (focused.value && !props.readonly && !props.disabled) {
    return editingText.value
  }
  return formatNumber(props.modelValue, props)
})

/** 编辑态初始文本：null 归空串，其余展示原始数值形态（不带分隔符/%/¥） */
function toEditableText(value) {
  if (value === null || value === undefined || value === '') return ''
  return String(value)
}

/** 失焦/回车收口：解析 → 精度归一 → 钳制；值有变更时回写规范值并分发 change */
function commit() {
  const normalized = normalizeNumber(parseNumber(editingText.value), props)
  if (!Object.is(normalized, props.modelValue)) {
    emit('update:modelValue', normalized)
    emit('change', normalized)
  }
  return normalized
}

/** 输入中：实时回写解析值（脏输入容错，不可解析回写 null），不触发 change、不收口 */
function handleInput(value) {
  editingText.value = value
  emit('update:modelValue', parseNumber(value))
}

/** 聚焦：切换编辑态文本；readonly 只读展示格式化文本、不进入编辑态 */
function handleFocus(event) {
  emit('focus', event)
  if (props.readonly || props.disabled) return
  focused.value = true
  editingText.value = toEditableText(props.modelValue)
}

/** 失焦：收口规范值后切回展示态 */
function handleBlur(event) {
  commit()
  focused.value = false
  emit('blur', event)
}

/** 回车：立即收口并把编辑文本归一为规范值形态（保持聚焦继续编辑） */
function handleEnter() {
  const normalized = commit()
  editingText.value = toEditableText(normalized)
}
</script>

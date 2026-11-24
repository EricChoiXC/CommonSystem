<template>
  <!--
    选择框控件（F15-FE-003，§1.3 choice 行：multi 单多选 / layout 横竖排 /
    options[{label, value}] 显示值与实际值）。单选 radio-group、多选 checkbox-group，
    按 layout 排布。
  -->
  <div
    ref="wrapperRef"
    class="form-choice-widget"
    :class="layout === 'vertical' ? 'is-vertical' : 'is-horizontal'"
    @focusin="handleFocusIn"
    @focusout="handleFocusOut"
  >
    <el-radio-group
      v-if="!multi"
      :model-value="singleValue"
      :disabled="disabled || readonly"
      @update:model-value="handleSingleUpdate"
      @change="handleSingleChange"
    >
      <el-radio v-for="option in options" :key="String(option.value)" :value="option.value">
        {{ option.label }}
      </el-radio>
    </el-radio-group>
    <el-checkbox-group
      v-else
      :model-value="arrayValue"
      :disabled="disabled || readonly"
      @update:model-value="handleMultiUpdate"
      @change="handleMultiChange"
    >
      <el-checkbox v-for="option in options" :key="String(option.value)" :value="option.value">
        {{ option.label }}
      </el-checkbox>
    </el-checkbox-group>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

/**
 * 选择框 widget（type=choice，功能详细设计 §1.3 choice 行；一期选项静态配置，
 * optionsSource=dict 协议位二期开放）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明）+ 扩展属性项 multi/layout/options + 统一控制位
 *   disabled/readonly（radio/checkbox 无只读形态，readonly 按禁用置灰处理）；
 * - emits：update:modelValue（单选 STRING、多选 ARRAY，空值单选 null / 多选 []）/
 *   change（新值）/ focus / blur（radio/checkbox 原生不冒泡 focus，经容器
 *   focusin/focusout 捕获转发）；
 * - 值形态：单选 STRING、多选 ARRAY（§1.3 dataType 随 props.multi 多态）；
 * - 固定属性项由渲染器层承载，本组件只渲染控件本体，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值（单选 STRING / 多选 ARRAY，空值 null / []） */
  modelValue: { type: [String, Number, Array], default: null },
  /** 单多选（true 多选，值为数组） */
  multi: { type: Boolean, default: false },
  /** 排列方式：horizontal 横排 / vertical 竖排 */
  layout: { type: String, default: 'horizontal' },
  /** 选择项 [{label 显示值, value 实际值}]（一期静态配置） */
  options: { type: Array, default: () => [] },
  /** 统一控制位：禁用置灰 */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（radio/checkbox 无只读形态，按禁用置灰处理） */
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

const wrapperRef = ref(null)

/** 单选回显：null/undefined 原样透传（radio-group 无选中态） */
const singleValue = computed(() => {
  if (props.modelValue === null || props.modelValue === undefined || props.modelValue === '') return null
  return props.modelValue
})

/** 多选回显：容忍脏形态归一数组（标量包一层数组，EP checkbox-group 要求数组） */
const arrayValue = computed(() => toArray(props.modelValue))

/** 空值归一：null/undefined/空串 → []；标量兜底包一层数组 */
function toArray(value) {
  if (Array.isArray(value)) return value
  if (value === null || value === undefined || value === '') return []
  return [value]
}

/** 单选空值归 null（值形态契约：空值统一 null，不出现空串/undefined 残留） */
function canonicalSingle(value) {
  if (value === '' || value === null || value === undefined) return null
  return value
}

function handleSingleUpdate(value) {
  emit('update:modelValue', canonicalSingle(value))
}

function handleSingleChange(value) {
  emit('change', canonicalSingle(value))
}

function handleMultiUpdate(value) {
  emit('update:modelValue', toArray(value))
}

function handleMultiChange(value) {
  emit('change', toArray(value))
}

/** 容器内控件获得焦点（radio/checkbox 原生 focus 不冒泡，focusin 捕获） */
function handleFocusIn(event) {
  emit('focus', event)
}

/** 容器内焦点移出（focusout 冒泡；焦点仍在容器内切换选项时不误发 blur） */
function handleFocusOut(event) {
  if (wrapperRef.value && event.relatedTarget && wrapperRef.value.contains(event.relatedTarget)) return
  emit('blur', event)
}
</script>

<style scoped>
/* 横排：inline 换行铺开；竖排：纵向堆叠（§1.3 layout 横排/竖排） */
.form-choice-widget.is-horizontal {
  display: flex;
  flex-wrap: wrap;
  row-gap: 4px;
}

.form-choice-widget.is-vertical {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  row-gap: 4px;
}

/* 竖排时去掉 EP 单选/复选默认右间距 */
.form-choice-widget.is-vertical :deep(.el-radio),
.form-choice-widget.is-vertical :deep(.el-checkbox) {
  margin-right: 0;
}
</style>

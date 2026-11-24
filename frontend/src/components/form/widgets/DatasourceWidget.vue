<template>
  <!-- 只读态：展示所选 label 文本（选项未取到时回落展示原始值） -->
  <span v-if="readonly" class="ds-readonly">{{ readonlyText }}</span>
  <!--
    编辑态：el-select 渲染（multi → multiple + collapse-tags），选项经
    api/datasource.js#queryDatasource 运行时取数（登录即可，执行期鉴权由服务端委托
    目标 action，功能详细设计 §6.5）；组件存编码不存快照（不缓存行数据进值）。
  -->
  <el-select
    v-else
    :model-value="selectValue"
    :multiple="multi"
    :collapse-tags="multi"
    :collapse-tags-tooltip="multi"
    clearable
    :loading="loading"
    placeholder="请选择"
    :disabled="disabled"
    @update:model-value="handleUpdate"
    @change="handleChange"
    @focus="handleFocus"
    @blur="handleBlur"
  >
    <el-option v-for="option in options" :key="String(option.value)" :label="option.label" :value="option.value" />
  </el-select>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { queryDatasource } from '../../../api/datasource'

/**
 * 数据源 widget（type=datasource，功能详细设计 §1.3 datasource 行；§6.5 复用口径）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明）+ 扩展属性项 datasourceCode/multi + 统一控制位
 *   disabled/readonly（readonly 展示所选 label 文本，disabled 禁用置灰）；
 * - emits：update:modelValue（单选 STRING、多选 ARRAY，空值单选 null / 多选 []）/
 *   change（新值，含 datasourceCode 变化触发的清空）/ focus / blur；
 * - 值形态：单选 STRING、多选 ARRAY（§1.3 dataType 随 props.multi 多态）；值即选项
 *   编码本身，存编码不存快照（不把行数据缓存进值，§6.5）；
 * - 取数：经 queryDatasource({ code, body: {}, pages: { pageNo: 1, pageSize: 100,
 *   needPage: false }, sortField: '' }) 取选项行；失败仅 ElMessage 警告 + 空态，
 *   不抛异常、不阻断整表（ADR-10 同语义）；datasourceCode 变化时重取并清空已选值
 *   （防跨源脏值；初始回显加载不清值——值来自落库、先于选项就绪）；
 * - label/value 映射（设计文档未定稿，实现期细化并注释登记）：缺省取行对象第一个
 *   字段值（label = value = 首字段值）；可选运行时 props labelField/valueField 指定
 *   取值字段（缺省 null → 首字段），不登记进 propSchema（不动冻结的属性项清单），
 *   留作后续走查如需配置化再回写设计文档；
 * - 固定属性项由渲染器层承载，本组件只渲染控件本体，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值（所选选项编码；单选 STRING / 多选 ARRAY，空值 null / []） */
  modelValue: { type: [String, Number, Array], default: null },
  /** 数据源编码（props 存编码不存快照；空 = 未配置，不取数） */
  datasourceCode: { type: String, default: '' },
  /** 单多选（true 多选，值为数组） */
  multi: { type: Boolean, default: false },
  /** 选项 label 取值字段（缺省 null → 行对象第一个字段值；运行时扩展，不入 propSchema） */
  labelField: { type: String, default: null },
  /** 选项 value 取值字段（缺省 null → 行对象第一个字段值；运行时扩展，不入 propSchema） */
  valueField: { type: String, default: null },
  /** 统一控制位：禁用置灰 */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（展示所选 label 文本） */
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

/** 选项行 [{ label, value }]（运行时取数，非静态配置） */
const options = ref([])
const loading = ref(false)

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

/** 规范值归一：单选 STRING / 多选 ARRAY */
function canonical(value) {
  return props.multi ? toArray(value) : canonicalSingle(value)
}

/** 只读展示文本：所选值映射 label（同值比对容忍形态漂移），未取到回落原始值，'、' 连接 */
const readonlyText = computed(() => {
  const values = toArray(props.modelValue)
  if (values.length === 0) return '—'
  return values
    .map((value) => {
      const hit = options.value.find((option) => String(option.value) === String(value))
      return hit ? hit.label : String(value)
    })
    .join('、')
})

/**
 * 行对象取字段值：key 为 null 时取第一个字段（对象键序即服务端投影序）；
 * key 指定但行内缺失 / 值为 null → undefined（展示回落 ''，value 落 ''，见 mapOption）。
 */
function pickField(row, key) {
  if (!row || typeof row !== 'object') return undefined
  const field = key || Object.keys(row)[0]
  if (!field) return undefined
  return row[field]
}

/** 行 → 选项：label = value = 首字段值（缺省映射口径，见头注）；null/undefined 归 '' */
function mapOption(row) {
  const value = pickField(row, props.valueField)
  const label = pickField(row, props.labelField)
  const normalizedValue = value === null || value === undefined ? '' : value
  return {
    label: label === null || label === undefined ? String(normalizedValue) : String(label),
    value: normalizedValue,
  }
}

/** 运行时取数：失败仅警告 + 空态，不抛异常、不阻断整表 */
async function loadOptions() {
  if (!props.datasourceCode) {
    options.value = []
    return
  }
  loading.value = true
  try {
    const res = await queryDatasource({
      code: props.datasourceCode,
      body: {},
      pages: { pageNo: 1, pageSize: 100, needPage: false },
      sortField: '',
    })
    options.value = (Array.isArray(res.data) ? res.data : []).map(mapOption)
  } catch (error) {
    console.warn('[DatasourceWidget] 数据源取数失败:', error && error.message ? error.message : error)
    ElMessage.warning('数据源取数失败，请稍后重试')
    options.value = []
  } finally {
    loading.value = false
  }
}

/** 清空已选值（datasourceCode 变化防跨源脏值；change 同步上抛供渲染器事件总线联动） */
function clearSelection() {
  const value = props.multi ? [] : null
  emit('update:modelValue', value)
  emit('change', value)
}

/**
 * 取数时序：挂载即取（immediate，此时不清值——回显值来自落库、先于选项就绪）；
 * 之后 datasourceCode 每次变化重取并清空已选值。
 */
watch(
  () => props.datasourceCode,
  (next, prev) => {
    loadOptions()
    if (prev !== undefined && next !== prev) {
      clearSelection()
    }
  },
  { immediate: true },
)

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

<style scoped>
.ds-readonly {
  color: var(--el-text-color-regular);
}
</style>

<template>
  <!--
    动态表单渲染器（F15-FE-005，功能详细设计 §4 渲染器契约；UI 分册 §5 三形态消费方式）。
    三形态复用同一渲染器：design（设计器画布内部复用）/ edit（填写）/ detail（只读回显）；
    渲染器只解释 Schema、不感知表单元数据接口、不发任何请求（§4.2）。
  -->
  <el-form
    v-if="hasFields"
    class="form-renderer"
    :class="`form-renderer--${rendererMode}`"
    label-position="right"
    label-width="140px"
    @submit.prevent
  >
    <FormFieldNode
      v-for="node in rootFields"
      :key="node.field"
      :node="node"
      :mode="rendererMode"
      :data="innerData"
      :field-permissions="fieldPermissions"
      :data-id="dataId"
      :available-vars="availableVars"
      @field-input="handleFieldInput"
      @field-commit="handleFieldCommit"
      @field-focus="handleFieldFocus"
      @field-blur="handleFieldBlur"
      @field-expression="handleFieldExpression"
    />
  </el-form>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import FormFieldNode from './FormFieldNode.vue'
// 物料注册入口（materials/index.js 模块加载即幂等注册全部物料，渲染器入口处 import 一次）
import './materials/index.js'
import { getComponentMaterial } from './registry.js'
import {
  RENDERER_MODES,
  buildInitialData,
  createRendererEventBus,
  cropPayloadData,
  deriveAvailableVars,
  validateFormData,
} from './rendererSupport.js'

/**
 * 自定义表单动态渲染器（功能详细设计 §4.2 props / v-model / 事件契约）：
 *
 * - props.schema：版本 Schema 对象（§2.1）——渲染器只解释 Schema，不发请求；
 * - props.mode：design / edit / detail（缺省 edit，非法值回落 edit）；
 * - props.modelValue：表单数据对象（v-model 受控组件）——载荷形态值空间（如
 *   addressbook 字段为 {ids,names}），任意字段值变化经 update:modelValue 回传全量对象；
 * - props.fieldPermissions：字段级权限外部覆盖映射 { 字段码: editable/readonly/hidden }，
 *   缺省 null；与 Schema props.permission 合并时外部覆盖优先（§4.2）；
 * - props.dataId：数据行 ID（缺省 ''）——注入 AttachmentWidget 的 modelId
 *   （填写页经 getInitID 预生成后方可上传）；附件 fieldKey=field.field、modelName
 *   走 widget 缺省 'SYS-FORM'（§4.2/§4.3 第 1 条）。
 *
 * emits：
 * - update:modelValue：全量数据对象（permission=hidden 字段已剔除——值不进入
 *   提交载荷，§4.3 第 4 条；props.hidden=true 的静态隐藏字段值保留，§3.6 ①）；
 * - change：单字段明细 { field, value, oldValue }（value/oldValue 均为载荷形态）。
 *
 * expose：validate() / reset() / getData()（§4.2 表，见 defineExpose 注释）。
 */
const props = defineProps({
  /** 版本 Schema 对象（§2.1） */
  schema: { type: Object, default: null },
  /** 渲染形态：design / edit / detail（缺省 edit） */
  mode: { type: String, default: 'edit' },
  /** 表单数据对象（v-model，载荷形态值空间） */
  modelValue: { type: Object, default: null },
  /** 字段级权限外部覆盖映射 { 字段码: editable/readonly/hidden }，缺省 null */
  fieldPermissions: { type: Object, default: null },
  /** 数据行 ID（注入 AttachmentWidget.modelId，缺省 ''） */
  dataId: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'change'])

/* ==================== 渲染形态与 Schema 视图 ==================== */

/** 形态归一：非法值回落 edit（§4.1 三形态） */
const rendererMode = computed(() => (RENDERER_MODES.includes(props.mode) ? props.mode : 'edit'))

/** Schema 字段树（脏 Schema 防御为空数组，渲染空表单不抛异常） */
const schemaFields = computed(() => {
  const schema = props.schema
  return schema && Array.isArray(schema.fields) ? schema.fields : []
})

const hasFields = computed(() => schemaFields.value.length > 0)

/** 顶层字段节点（FormFieldNode 递归单元消费） */
const rootFields = computed(() => schemaFields.value)

/**
 * FormulaWidget 可用变量声明（§4.3 第 8 条）：全部数据类字段码 { code, type, label }，
 * 明细表字段以数组变量支持主表公式 ${list.field} 投影声明；派生逻辑在
 * rendererSupport.deriveAvailableVars（纯函数，Wave 4 有更精确诉求时调整）。
 */
const availableVars = computed(() => deriveAvailableVars(schemaFields.value, getComponentMaterial))

/* ==================== 数据状态与事件总线 ==================== */

/** 渲染器内部数据对象（载荷值空间；design 形态为影子数据，不外发） */
const innerData = ref({})

/** 最近一次外发的数据对象（宿主 v-model 回环拦截标记） */
let lastEmitted = null

/** change 明细旧值暂存（widget update:modelValue 先行回写、change 后至，取该交互段原始旧值） */
const pendingOldValues = new Map()

/**
 * 渲染器内置事件总线（§4.3 第 5 条轻量内部事件派发点）。
 * F15-FE-006 规则引擎挂载预留点：引擎经 events.on(event, handler) 订阅同一事件流。
 * 当前派发点——onLoad：挂载完成 / Schema 替换 / 外部数据回显完成后；
 * onChange：字段提交值变更（handleFieldCommit）；onFocus/onBlur：widget 焦点透传；
 * onValidate：validate() 执行时。onAdd/onDelete 待明细表 widget（FE-007）经总线
 * 派发、onSubmit 待宿主提交编排派发——语义占位，渲染器不自行触发。
 */
const events = createRendererEventBus()

/* ==================== 数据初始化 / 外发 / 裁剪（§4.3 第 3/4 条） ==================== */

/**
 * 提交载荷：当前数据对象剔除 permission=hidden 字段（§4.3 第 4 条前端一致裁剪；
 * props.hidden=true 静态隐藏字段值保留，§3.6 ①）。
 */
function currentPayload() {
  return cropPayloadData(schemaFields.value, innerData.value, props.fieldPermissions, getComponentMaterial)
}

/** 外发全量数据对象（design 形态数据绑定旁路：不外发，影子数据仅画布占位消费） */
function emitData() {
  if (rendererMode.value === 'design') return
  const payload = currentPayload()
  lastEmitted = payload
  emit('update:modelValue', payload)
}

/**
 * 数据初始化：以宿主 v-model 数据为底稿，对「undefined 字段」执行物料
 * defaultValueGenerator 补齐默认值（props.defaultValue 配置优先，§4.3 第 3 条）。
 * 编辑回显缺失字段补默认值后回传一次全量对象（防宿主数据缺键）；
 * detail 形态只本地补齐不回传（只读回显）；design 形态构建影子数据不回传。
 */
function syncData() {
  const base = props.modelValue && typeof props.modelValue === 'object' ? props.modelValue : {}
  const filled = buildInitialData(schemaFields.value, base, getComponentMaterial)
  innerData.value = filled
  const addedDefaults = Object.keys(filled).some((key) => !(key in base))
  if (addedDefaults && rendererMode.value === 'edit') {
    emitData()
  }
}

/* ==================== 字段事件统一处理（§4.3 第 5 条） ==================== */

/**
 * widget 输入过程值（update:modelValue）：载荷值空间写入内部数据并回传全量对象。
 * design 形态数据绑定旁路——影子数据不回写、不外发（§4.3 第 7 条）。
 */
function handleFieldInput({ field, value }) {
  if (rendererMode.value === 'design') return
  const oldValue = innerData.value[field]
  innerData.value[field] = value
  if (!pendingOldValues.has(field)) {
    pendingOldValues.set(field, oldValue)
  }
  emitData()
}

/**
 * widget 提交值（change）：回传单字段明细 change { field, value, oldValue } 并派发
 * 总线 onChange（规则引擎 F15-FE-006 消费）。旧值取该交互段首次输入前的原始值
 * （输入过程 update:modelValue 已先行回写，见 handleFieldInput）。
 */
function handleFieldCommit({ field, value }) {
  if (rendererMode.value === 'design') return
  const oldValue = pendingOldValues.has(field) ? pendingOldValues.get(field) : innerData.value[field]
  pendingOldValues.delete(field)
  if (innerData.value[field] !== value) {
    innerData.value[field] = value
    emitData()
  }
  emit('change', { field, value, oldValue })
  events.dispatch('onChange', { field, value, oldValue })
}

/** widget 焦点透传：派发总线 onFocus（声明式规则一期不开放配置，§3.4） */
function handleFieldFocus({ field, event }) {
  events.dispatch('onFocus', { field, event })
}

/** widget 失焦透传：派发总线 onBlur（同上） */
function handleFieldBlur({ field, event }) {
  events.dispatch('onBlur', { field, event })
}

/**
 * FormulaWidget design 态 update:expression：画布数据绑定旁路——渲染器不回写
 * field.props.expression，由 Wave 4 设计器消费该事件决定是否回写（§4.3 第 7 条）；
 * edit/detail 态 FormulaWidget 不触发该事件（值来源仅服务端重算回写，ADR-09）。
 */
function handleFieldExpression() {
  // 画布数据绑定旁路：有意不处理（设计器 Wave 4 挂载点）
}

/* ==================== 初始化与回显（§4.3 第 3 条） ==================== */

onMounted(() => {
  syncData()
  // onLoad：表单初始化完成后派发（§3.4，规则引擎初始化联动挂载点）
  events.dispatch('onLoad', { data: currentPayload(), mode: rendererMode.value })
})

// Schema 替换（含版本切换 / 画布编辑）：重建数据并重新派发 onLoad
watch(
  () => props.schema,
  () => {
    pendingOldValues.clear()
    syncData()
    events.dispatch('onLoad', { data: currentPayload(), mode: rendererMode.value })
  },
)

// 宿主数据回显（v-model 外部换新对象，如 loadFormData 响应回填）：补默认值后回传一次
// （lastEmitted 同引用拦截自身回传，防 v-model 回环）
watch(
  () => props.modelValue,
  (value) => {
    if (value === lastEmitted) return
    pendingOldValues.clear()
    syncData()
    // onLoad：回显完成后派发（§3.4「表单初始化/回显完成后」）
    events.dispatch('onLoad', { data: currentPayload(), mode: rendererMode.value })
  },
)

// 外部权限覆盖变化：重新外发裁剪后的载荷（permission=hidden 集合可能变化）
watch(
  () => props.fieldPermissions,
  () => {
    emitData()
  },
  { deep: true },
)

/* ==================== 对外契约（§4.2 expose） ==================== */

/**
 * 全量校验（§4.2 expose.validate）：基础校验 + 物料校验器聚合（rendererSupport.
 * validateFormData），返回 { pass, errors: [{ field, ruleId, message }] }——一期
 * ruleId 恒 null，规则动态校验由 F15-FE-006 并入后合并；errors.message 全中文。
 * design 形态数据绑定旁路，直接通过（画布无真实数据）。
 */
function validate() {
  if (rendererMode.value === 'design') {
    return { pass: true, errors: [] }
  }
  const errors = validateFormData(schemaFields.value, innerData.value, props.fieldPermissions, getComponentMaterial)
  // onValidate：校验阶段派发（§3.4 语义占位，规则引擎/宿主校验链消费）
  events.dispatch('onValidate', { errors: errors.map((error) => ({ ...error })) })
  return { pass: errors.length === 0, errors }
}

/**
 * 重置（§4.2 expose.reset）：清空后按物料 defaultValueGenerator 重新生成默认值
 * （props.defaultValue 配置优先）——而非回快照；重置结果经 update:modelValue 回传。
 */
function reset() {
  pendingOldValues.clear()
  innerData.value = buildInitialData(schemaFields.value, {}, getComponentMaterial)
  emitData()
}

/** 取当前数据对象（§4.2 expose.getData）：permission=hidden 字段已剔除的载荷副本 */
function getData() {
  return currentPayload()
}

defineExpose({ validate, reset, getData })
</script>

<style scoped>
.form-renderer {
  width: 100%;
}
</style>

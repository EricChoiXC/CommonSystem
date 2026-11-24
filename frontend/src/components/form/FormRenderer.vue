<template>
  <!--
    动态表单渲染器（F15-FE-005，功能详细设计 §4 渲染器契约；UI 分册 §5 三形态消费方式）。
    三形态复用同一渲染器：design（设计器画布内部复用）/ edit（填写）/ detail（只读回显）；
    渲染器只解释 Schema、不感知表单元数据接口、不发任何请求（§4.2）。
    F15-FE-006 集成：规则引擎经 rules/eventBus.js 绑定既有内置事件总线（不另起第二套
    总线）；动态状态桥——显隐终态/动态必填覆盖回写渲染决策（FormFieldNode）；熔断
    状态位（§3.5：规则风暴触发上限时顶部轻量提示，下次风暴复位，不阻断表单）。
    F15-FE-007 集成：明细表行事件经 FormFieldNode field-row-* 冒泡通道进入本组件，
    统一向既有总线派发 onAdd/onDelete/onChange 行上下文载荷（不另起总线）；引擎行内
    覆盖与主表字段值经 provide（FORM_RENDERER_CONTEXT_KEY）注入 DetailTableWidget，
    行内显隐/必填覆盖回写行内单元格渲染（§3.1/§3.6 一期边界/§6.3）。
  -->
  <div class="form-renderer-root">
    <el-alert
      v-if="ruleAlertVisible"
      class="form-renderer-rule-alert"
      type="warning"
      :closable="false"
      show-icon
      title="规则触发次数超限，部分联动已暂停"
    />
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
        :rule-visibility-map="engineVisibilityMap"
        :rule-required-map="engineRequiredMap"
        @field-input="handleFieldInput"
        @field-commit="handleFieldCommit"
        @field-focus="handleFieldFocus"
        @field-blur="handleFieldBlur"
        @field-expression="handleFieldExpression"
        @field-row-add="handleRowAdd"
        @field-row-delete="handleRowDelete"
        @field-row-change="handleRowChange"
      />
    </el-form>
  </div>
</template>

<script setup>
import { computed, onMounted, provide, ref, watch } from 'vue'
import FormFieldNode from './FormFieldNode.vue'
// 物料注册入口（materials/index.js 模块加载即幂等注册全部物料，渲染器入口处 import 一次）
import './materials/index.js'
import { getComponentMaterial } from './registry.js'
import {
  FORM_RENDERER_CONTEXT_KEY,
  RENDERER_MODES,
  buildInitialData,
  createRendererEventBus,
  cropPayloadData,
  deriveAvailableVars,
  resolveDataType,
  validateFormData,
} from './rendererSupport.js'
// 规则引擎（F15-FE-006）：条件求值/动作执行在 rules/evaluators.js，register/execute/
// validate 统一接口在 rules/ruleEngine.js，总线绑定入口在 rules/eventBus.js——
// 渲染器零业务规则（显隐/联动/校验逻辑全部来自 Schema rules[]，架构原则 2）
import { buildRequiredBaselineFields, createRuleEngine } from './rules/ruleEngine.js'
import { bindRuleEngine } from './rules/eventBus.js'

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
 * - change：单字段明细 { field, value, oldValue }（value/oldValue 均为载荷形态；
 *   规则引擎联动写值（F15-FE-006 setValue/clearValue）同样经此通道外发并附带
 *   source='rule' 来源标记——宿主可区分规则联动与用户交互，明细行内联动附
 *   detailCode/rowIndex 行定位）。
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
 * F15-FE-006 规则引擎挂载点：引擎经 events.on(event, handler) 订阅同一事件流。
 * 当前派发点——onLoad：挂载完成 / Schema 替换 / 外部数据回显完成后；
 * onChange：字段提交值变更（handleFieldCommit）+ 明细行内值变更（handleRowChange，
 * 行上下文载荷）；onFocus/onBlur：widget 焦点透传；onValidate：validate() 执行时；
 * onAdd/onDelete：明细表行生命周期（F15-FE-007 DetailTableWidget 经 field-row-add/
 * field-row-delete 冒泡通道 → handleRowAdd/handleRowDelete 派发）；onSubmit 待宿主
 * 提交编排派发——语义占位，渲染器不自行触发。
 */
const events = createRendererEventBus()

/* ==================== 规则引擎集成（F15-FE-006，§3 规则协议 / §4.3 第 5 条） ==================== */

/** 动态状态版本号：引擎动态状态为普通 JS 载体（零 Vue 依赖），风暴结束后自增驱动渲染决策 computed 重算 */
const ruleStateVersion = ref(0)

/**
 * 引擎值写入桥（setValue/clearValue 经渲染器既有数据通道回写）：
 * 写内部数据 → update:modelValue 全量外发（lastEmitted 同引用拦截 v-model 回环）→
 * change 明细外发（source='rule' 引擎来源标记，宿主可区分规则联动与用户交互）。
 * 引擎写值不回派总线 onChange——级联由引擎内部深度 +1 驱动（§3.5②），lastEmitted
 * 同引用拦截 + 来源标记共同构成防重入风暴兜底。
 */
function engineWriteField(code, value) {
  const oldValue = innerData.value[code]
  innerData.value[code] = value
  emitData()
  emit('change', { field: code, value, oldValue, source: 'rule' })
}

/** 引擎行内值写入桥（明细行内联动，行上下文隔离；载荷通道同上，change 附行定位） */
function engineWriteRowField(detailCode, rowIndex, code, value) {
  const rows = innerData.value[detailCode]
  const row = Array.isArray(rows) ? rows[rowIndex] : null
  if (!row || typeof row !== 'object' || Array.isArray(row)) return
  const oldValue = row[code]
  row[code] = value
  emitData()
  emit('change', { field: code, value, oldValue, detailCode, rowIndex, source: 'rule' })
}

/**
 * 规则引擎实例（rules/ruleEngine.js）：数据读写经注入访问器对接渲染器内部数据对象
 * （innerData 载荷值空间），物料知识经 isDataField 访问器注入（引擎不感知 registry）。
 */
const ruleEngine = createRuleEngine({
  readField: (code) => innerData.value[code],
  writeField: engineWriteField,
  readRowField: (detailCode, rowIndex, code) => {
    const rows = innerData.value[detailCode]
    const row = Array.isArray(rows) ? rows[rowIndex] : null
    return row && typeof row === 'object' && !Array.isArray(row) ? row[code] : undefined
  },
  writeRowField: engineWriteRowField,
  forEachRow: (detailCode, callback) => {
    const rows = innerData.value[detailCode]
    if (!Array.isArray(rows)) return
    rows.forEach((row, index) => {
      if (row && typeof row === 'object' && !Array.isArray(row)) callback(row, index)
    })
  },
  isDataField: (type, fieldProps) => {
    const material = getComponentMaterial(type)
    return !!material && resolveDataType(material, fieldProps) !== null
  },
})

/**
 * 总线绑定（rules/eventBus.js，复用 FE-005 总线不另起第二套）：onLoad/onChange/onSubmit/
 * onValidate/onAdd/onDelete 进引擎，onFocus/onBlur 忽略（§3.4 一期不开放焦点类配置）。
 * onAfterExecute：有动作执行或熔断时自增动态状态版本号（显隐/必填/熔断提示重算）。
 */
bindRuleEngine(events, ruleEngine, {
  onAfterExecute: () => {
    ruleStateVersion.value++
  },
})

/**
 * 明细表行上下文注入（F15-FE-007，provide/inject，FORM_RENDERER_CONTEXT_KEY）。
 * DetailTableWidget 据此消费：
 * - stateVersion：规则风暴版本号（ref）——bindRuleEngine.onAfterExecute 在风暴有动作
 *   执行/熔断时统一自增，行内单元格 computed 消费该版本号驱动重算（行内覆盖渲染
 *   刷新由既有机制承载，行事件处理器无需重复递增）；
 * - getRowVisibilityOverride / getRowRequiredOverride：引擎行内覆盖查询（行内显隐
 *   一期为渲染层语义，§3.6 一期边界；行内规则只作用当前行，§3.1）；
 * - getMainValue：主表字段值读取（行内公式本地预演上下文——行内同码优先、主表回落，
 *   §6.3 ADR-09；FE-008 设计器接入位：主表变量注入随设计器装配同通道）。
 * 行事件派发不经此注入——走既有 @field-* 冒泡通道（field-row-add/delete/change）。
 */
provide(FORM_RENDERER_CONTEXT_KEY, {
  stateVersion: ruleStateVersion,
  getRowVisibilityOverride: (detailCode, rowIndex, code) => ruleEngine.getRowVisibilityOverride(detailCode, rowIndex, code),
  getRowRequiredOverride: (detailCode, rowIndex, code) => ruleEngine.getRowRequiredOverride(detailCode, rowIndex, code),
  getMainValue: (code) => innerData.value[code],
})

/** 引擎显隐终态快照（Map<字段码, true|false>；FormFieldNode 可见性裁决消费——引擎覆盖优先于静态初值） */
const engineVisibilityMap = computed(() => {
  ruleStateVersion.value
  return ruleEngine.snapshotVisibility()
})

/** 引擎动态必填覆盖快照（Map<字段码, {value, ruleId}>；必填星号与 validate 基线折算消费） */
const engineRequiredMap = computed(() => {
  ruleStateVersion.value
  return ruleEngine.snapshotRequired()
})

/** 引擎规则隐藏字段码快照（Set；validate 基线折算隐藏豁免消费） */
const engineHiddenSet = computed(() => {
  ruleStateVersion.value
  return ruleEngine.snapshotHiddenCodes()
})

/** 熔断状态位提示（§3.5：熔断仅告警不阻断表单；下次风暴开始即复位） */
const ruleAlertVisible = computed(() => {
  ruleStateVersion.value
  return rendererMode.value !== 'design' && ruleEngine.isBroken()
})

/**
 * 规则引擎挂载：Schema 就绪/替换（与形态切换）后重建规则索引。design 形态引擎不挂载
 * （画布数据绑定旁路，§4.1）——清空索引即等效旁路；初始化联动由既有 onLoad 总线派发点
 * 经 eventBus 订阅入口进引擎（onLoad 规则单轮 + 值驱动全量重放重建显隐/覆盖终态）。
 */
function setupRuleEngine() {
  if (rendererMode.value === 'design') {
    ruleEngine.register(null)
    return
  }
  ruleEngine.register(props.schema)
}

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

/* ==================== 明细表行事件总线派发（F15-FE-007，§3.1/§3.4 行上下文） ==================== */

/**
 * 行事件载荷契约（与 rules/ruleEngine.execute 行上下文口径一致）：
 * { field: 行内字段码, value, oldValue, detailCode: 明细表字段码, rowIndex, row: 当前行对象 }。
 * onAdd/onDelete 载荷 { field: 明细表字段码, detailCode, rowIndex, row }（行生命周期
 * 事件无单值新旧语义，field 记宿主明细表字段码，§3.4）。
 */

/** 明细行新增（DetailTableWidget.row-add）：派发总线 onAdd——行初始化动作，行上下文注入，行内规则只作用当前行 */
function handleRowAdd({ detailCode, rowIndex, row }) {
  events.dispatch('onAdd', { field: detailCode, detailCode, rowIndex, row })
}

/** 明细行删除（DetailTableWidget.row-delete）：派发总线 onDelete——行后处理；统计行即时刷新由 widget 内置，无需规则 */
function handleRowDelete({ detailCode, rowIndex, row }) {
  events.dispatch('onDelete', { field: detailCode, detailCode, rowIndex, row })
}

/**
 * 行内值变更（DetailTableWidget.row-change）：派发总线 onChange（行上下文载荷，
 * triggerField=行内字段码的行内规则匹配并只作用当前行，§3.1）；行值变更（含公式
 * 预演回写、引擎联动写值）经 update:modelValue 数据通道外发，触发字段规则的
 * triggerIndex 索引重放由引擎固定点迭代消费。行事件处理器不重复自增 stateVersion——
 * 风暴结束由 bindRuleEngine.onAfterExecute 统一驱动行内覆盖渲染重算。
 */
function handleRowChange({ detailCode, field, value, oldValue, rowIndex, row }) {
  events.dispatch('onChange', { field, value, oldValue, detailCode, rowIndex, row })
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
  // 引擎挂载先于数据初始化（onLoad 派发时规则索引已就绪，F15-FE-006）
  setupRuleEngine()
  syncData()
  // onLoad：表单初始化完成后派发（§3.4，规则引擎初始化联动挂载点）
  events.dispatch('onLoad', { data: currentPayload(), mode: rendererMode.value })
})

// Schema 替换（含版本切换 / 画布编辑）：重建引擎索引与数据并重新派发 onLoad
watch(
  () => props.schema,
  () => {
    pendingOldValues.clear()
    setupRuleEngine()
    syncData()
    events.dispatch('onLoad', { data: currentPayload(), mode: rendererMode.value })
  },
)

// 渲染形态切换：重建引擎挂载状态（design 卸载索引 / 其余形态重建——挂载后动态切换形态的兜底）
watch(rendererMode, () => {
  setupRuleEngine()
})

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
 * 全量校验（§4.2 expose.validate）：静态基线（基础校验 + 物料校验器聚合，rendererSupport.
 * validateFormData）+ 规则引擎动态校验增量（F15-FE-006）合并去重，返回
 * { pass, errors: [{ field, ruleId, message }] }——动态错误 ruleId 溯源触发规则。
 * design 形态数据绑定旁路，直接通过（画布无真实数据）。
 *
 * 流程（§3.1 动态校验语义 / §3.4 提交段事件段位）：
 * ① 静态基线折算——被规则隐藏（终态 hidden ∪ 静态 hidden）与 setRequired 覆盖字段的
 *    required 置 false（隐藏字段值将被提交裁剪不参与必填校验，§3.6；动态必填错误由
 *    引擎增量承载并带 ruleId，避免静态通道重复报错）；
 * ② onValidate 派发——onValidate/onSubmit 类规则（setRequired/setValidate/show/hide）
 *    在此更新动态状态，效果并入本次动态校验增量；setValue/clearValue 禁配由引擎跳过
 *    并告警（§3.4）；派发载荷为静态基线错误（动态增量随后合并返回）；
 * ③ 引擎动态增量——动态必填 + setValidate 条件范围/条件正则（ruleId 溯源）；detail
 *    形态动态校验不执行（§4.1 detail 语义：显隐规则照常求值、动态校验不执行）。
 */
function validate() {
  if (rendererMode.value === 'design') {
    return { pass: true, errors: [] }
  }
  const effectiveFields = buildRequiredBaselineFields(schemaFields.value, engineRequiredMap.value, engineHiddenSet.value)
  const staticErrors = validateFormData(effectiveFields, innerData.value, props.fieldPermissions, getComponentMaterial)
  events.dispatch('onValidate', { errors: staticErrors.map((error) => ({ ...error })) })
  const dynamicErrors = rendererMode.value === 'detail' ? [] : ruleEngine.validate(innerData.value)
  const errors = mergeValidationErrors(staticErrors, dynamicErrors)
  return { pass: errors.length === 0, errors }
}

/**
 * 校验错误合并去重：静态基线（ruleId=null）在前 + 引擎动态增量（ruleId 溯源）在后，
 * 按 field/ruleId/message 三元组去重（必填双报已由基线折算避免，此处为合并兜底）。
 */
function mergeValidationErrors(staticErrors, dynamicErrors) {
  const merged = []
  const seen = new Set()
  for (const error of [...staticErrors, ...dynamicErrors]) {
    const key = `${error.field}\u0000${error.ruleId || ''}\u0000${error.message}`
    if (seen.has(key)) continue
    seen.add(key)
    merged.push(error)
  }
  return merged
}

/**
 * 重置（§4.2 expose.reset）：清空后按物料 defaultValueGenerator 重新生成默认值
 * （props.defaultValue 配置优先）——而非回快照；重置结果经 update:modelValue 回传。
 * 重置后按初始化口径派发 onLoad（F15-FE-006：引擎全量重放按默认值重建显隐/必填/
 * 校验覆盖终态，避免残留上次交互的规则状态）。
 */
function reset() {
  pendingOldValues.clear()
  innerData.value = buildInitialData(schemaFields.value, {}, getComponentMaterial)
  emitData()
  events.dispatch('onLoad', { data: currentPayload(), mode: rendererMode.value })
}

/** 取当前数据对象（§4.2 expose.getData）：permission=hidden 字段已剔除的载荷副本 */
function getData() {
  return currentPayload()
}

defineExpose({ validate, reset, getData })
</script>

<style scoped>
.form-renderer-root {
  width: 100%;
}

/* 熔断状态位提示（§3.5：规则风暴触发上限轻量提示，不阻断已渲染表单） */
.form-renderer-rule-alert {
  margin-bottom: 12px;
}

.form-renderer {
  width: 100%;
}
</style>

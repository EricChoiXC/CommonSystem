<template>
  <!--
    字段递归渲染单元（F15-FE-005，FormRenderer 内部子组件，不进 components/index.js 统一出口）。
    单文件模板递归必须由独立组件单元承载，渲染器把本组件作为唯一递归出口消费。

    渲染分支（功能详细设计 §4.3）：
    - permission=hidden、规则显隐终态=false 或 props.hidden=true → 不渲染（不占布局位，
      UI 分册 §5.3）。三类隐藏的【值语义】差异由渲染器数据层承载：permission=hidden 值经
      cropPayloadData 从 validate()/getData() 输出剔除（§4.3 第 4 条，ADR-08 ②③）；
      规则 hide 与 props.hidden=true 仅渲染层隐藏、值保留（§3.6 ①隐藏不删值，由服务端
      复算裁决）——规则显隐终态（F15-FE-006 引擎 show/hide）在 props.hidden 静态态之上
      切换且覆盖优先（§3.3，show 可恢复静态初始隐藏字段）；
    - group（区块标题 = label）/ grid（children 为列对象 {span, fields[]}，24 栅格
      el-row/el-col）→ 渲染器自身渲染容器外壳并递归 children（§4.3 第 2 条；
      二物料 component=null 属预期，不走未知 type 占位）；
    - 已注册且 component 存在 → el-form-item 包裹 + <component :is> 加载控件
      （§4.3 第 1 条：label/tooltip 问号提示/required 星号由渲染器统一渲染，
      widget 只渲染控件本体；明细表 F15-FE-007 经 FormFieldNode 注入 mode 三态 /
      detailCode 字段码 / children 行内字段定义——Schema children 不走字段 props）；
    - 其余（registry 未注册或 material.component=null，ADR-10）→「组件未注册」
      占位块（type 文案 + 告警样式），不抛异常、不阻断整表渲染。
  -->
  <template v-if="hidden">
    <!-- 静态隐藏 / 权限隐藏：不渲染（值语义差异见头注与 rendererSupport.js） -->
  </template>
  <section v-else-if="isGroup" class="renderer-group renderer-field-item" :data-field="node.field">
    <div class="renderer-group-title">{{ node.label }}</div>
    <FormFieldNode
      v-for="child in groupChildren"
      :key="child.field"
      :node="child"
      :mode="mode"
      :data="data"
      :field-permissions="fieldPermissions"
      :data-id="dataId"
      :available-vars="availableVars"
      :rule-visibility-map="ruleVisibilityMap"
      :rule-required-map="ruleRequiredMap"
      @field-input="forwardInput"
      @field-commit="forwardCommit"
      @field-focus="forwardFocus"
      @field-blur="forwardBlur"
      @field-expression="forwardExpression"
      @field-row-add="forwardRowAdd"
      @field-row-delete="forwardRowDelete"
      @field-row-change="forwardRowChange"
    />
  </section>
  <div v-else-if="isGrid" class="renderer-grid renderer-field-item" :data-field="node.field">
    <el-row :gutter="16">
      <el-col v-for="(column, colIndex) in gridColumns" :key="colIndex" :span="columnSpan(column)">
        <FormFieldNode
          v-for="child in columnFields(column)"
          :key="child.field"
          :node="child"
          :mode="mode"
          :data="data"
          :field-permissions="fieldPermissions"
          :data-id="dataId"
          :available-vars="availableVars"
          :rule-visibility-map="ruleVisibilityMap"
          :rule-required-map="ruleRequiredMap"
          @field-input="forwardInput"
          @field-commit="forwardCommit"
          @field-focus="forwardFocus"
          @field-blur="forwardBlur"
          @field-expression="forwardExpression"
          @field-row-add="forwardRowAdd"
          @field-row-delete="forwardRowDelete"
          @field-row-change="forwardRowChange"
        />
      </el-col>
    </el-row>
  </div>
  <el-form-item
    v-else-if="materialUsable"
    class="renderer-field-item"
    :data-field="node.field"
    :required="showRequired"
  >
    <template #label>
      <span class="renderer-field-label">
        {{ node.label }}
        <el-tooltip v-if="tooltipText" :content="tooltipText" placement="top">
          <el-icon class="renderer-field-tooltip"><QuestionFilled /></el-icon>
        </el-tooltip>
      </span>
    </template>
    <!--
      控件加载（§4.3 第 1 条）：props 透传经 pickWidgetProps 净化的扩展属性项，
      v-model 值经物料 deserializer（载荷值→组件值）绑定；组件值经 serializer
      （组件值→载荷值）随 field-input/field-commit 上抛渲染器。每字段项根元素带
      .renderer-field-item 稳定 class 与 data-field 属性——Wave 4 设计器画布
      选中/拖拽定位消费的结构钩子（§4.3 第 7 条）。
    -->
    <component
      :is="material.component"
      :model-value="componentValue"
      v-bind="widgetProps"
      :disabled="controlDisabled"
      :readonly="controlReadonly"
      @update:model-value="onWidgetInput"
      @change="onWidgetChange"
      @focus="onWidgetFocus"
      @blur="onWidgetBlur"
      @update:expression="onWidgetExpression"
      @row-add="onWidgetRowAdd"
      @row-delete="onWidgetRowDelete"
      @row-change="onWidgetRowChange"
    />
  </el-form-item>
  <div v-else class="renderer-unregistered renderer-field-item" :data-field="node.field">
    <el-icon><WarningFilled /></el-icon>
    <span>组件未注册：{{ node.type }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { QuestionFilled, WarningFilled } from '@element-plus/icons-vue'
import { getComponentMaterial } from './registry.js'
import { pickWidgetProps, resolveFieldPermission } from './rendererSupport.js'

/**
 * 字段递归渲染单元 props（FormRenderer 内部契约，非 §4.2 对外契约）：
 * - node：Schema 字段对象（§2.1）；
 * - mode：渲染形态 design/edit/detail（透传自 FormRenderer）；
 * - data：渲染器内部数据对象（载荷值空间），容器递归共享同一份数据；
 * - fieldPermissions：字段级权限外部覆盖映射（§4.2，透传）；
 * - dataId：数据行 ID（注入 AttachmentWidget 的 modelId，§4.2）；
 * - availableVars：FormulaWidget 变量声明（渲染器从 Schema 派生，§4.3 第 8 条）；
 * - ruleVisibilityMap / ruleRequiredMap：规则引擎动态状态桥（F15-FE-006）——显隐终态
 *   覆盖 Map<字段码, true|false> 与动态必填覆盖 Map<字段码, {value, ruleId}>，
 *   容器递归透传同一份快照。
 */
const props = defineProps({
  /** Schema 字段对象（§2.1） */
  node: { type: Object, required: true },
  /** 渲染形态 design/edit/detail */
  mode: { type: String, default: 'edit' },
  /** 渲染器内部数据对象（载荷值空间） */
  data: { type: Object, default: null },
  /** 字段级权限外部覆盖映射 { 字段码: editable/readonly/hidden } */
  fieldPermissions: { type: Object, default: null },
  /** 数据行 ID（注入 AttachmentWidget.modelId） */
  dataId: { type: String, default: '' },
  /** FormulaWidget 变量声明 [{ code, type, label }] */
  availableVars: { type: Array, default: () => [] },
  /** 规则引擎显隐终态覆盖 Map<字段码, true|false>（F15-FE-006 show/hide，引擎覆盖优先于静态初值） */
  ruleVisibilityMap: { type: Map, default: null },
  /** 规则引擎动态必填覆盖 Map<字段码, { value, ruleId }>（F15-FE-006 setRequired 终态覆盖静态基线） */
  ruleRequiredMap: { type: Map, default: null },
})

/**
 * 向上转发事件（逐层冒泡至 FormRenderer 统一处理）：
 * - field-input：widget update:modelValue（输入过程值，载荷值空间）；
 * - field-commit：widget change（提交值，载荷值空间）——渲染器据此 emit change
 *   明细与总线 onChange（§4.3 第 5 条）；
 * - field-focus / field-blur：焦点事件透传（渲染器派发总线 onFocus/onBlur）；
 * - field-expression：FormulaWidget design 态 update:expression（画布数据绑定
 *   旁路，渲染器不回写，Wave 4 设计器决定是否回写 field.props.expression）；
 * - field-row-add / field-row-delete / field-row-change：明细表行事件派发桥
 *   （F15-FE-007，§3.1/§3.4 行上下文）——渲染器据以向既有总线派发 onAdd/onDelete/
 *   onChange 行上下文载荷（不另起总线）。
 */
const emit = defineEmits([
  'field-input',
  'field-commit',
  'field-focus',
  'field-blur',
  'field-expression',
  'field-row-add',
  'field-row-delete',
  'field-row-change',
])

/* ==================== 容器与物料判定 ==================== */

/** 物料（经 registry 读取接口取，渲染器不直接 import 其他组件运行时实现） */
const material = computed(() => getComponentMaterial(props.node.type))

/** 是否布局容器（group/grid：渲染器自身渲染外壳，component=null 属预期不走占位） */
const isGroup = computed(() => props.node.type === 'group')
const isGrid = computed(() => props.node.type === 'grid')

/** group children = 字段对象数组（§2.1） */
const groupChildren = computed(() => (Array.isArray(props.node.children) ? props.node.children : []))

/** grid children = 列对象数组 { span, fields[] }（§2.1） */
const gridColumns = computed(() => (Array.isArray(props.node.children) ? props.node.children : []))

/** 列跨度（24 栅格制，缺省占满 24） */
function columnSpan(column) {
  const span = column && column.span !== undefined && column.span !== null ? Number(column.span) : 24
  return Number.isFinite(span) && span > 0 ? span : 24
}

/** 列内字段数组（脏列防御为空数组） */
function columnFields(column) {
  return column && Array.isArray(column.fields) ? column.fields : []
}

/** 物料可渲染（已注册且运行时组件已接入；group/grid 已在容器分支消费不会到达此处） */
const materialUsable = computed(() => !!(material.value && material.value.component))

/* ==================== 显隐与权限（§4.3 第 4 条） ==================== */

/** 字段 props 归一（Schema 字段对象 props 可缺省，防御 undefined 解引用） */
const fieldProps = computed(() => (props.node.props && typeof props.node.props === 'object' ? props.node.props : {}))

/** 生效权限：外部覆盖（fieldPermissions）优先于 Schema props.permission（§4.2） */
const effectivePermission = computed(() => resolveFieldPermission(fieldProps.value, props.node.field, props.fieldPermissions))

/**
 * 规则引擎显隐终态覆盖（F15-FE-006）：true=规则显示（show 命中，可恢复 props.hidden
 * 静态初值隐藏的字段，§3.3「在 props.hidden 静态态之上切换」）；false=规则隐藏
 * （hide 命中，隐藏不删值 §3.6 ①）；undefined=无规则覆盖（回落静态态）。
 */
const ruleVisibility = computed(() =>
  props.ruleVisibilityMap && typeof props.ruleVisibilityMap.get === 'function'
    ? props.ruleVisibilityMap.get(props.node.field)
    : undefined,
)

/**
 * 是否不渲染（动态状态桥，F15-FE-006）：
 * - permission=hidden → 恒隐且值不进提交载荷（渲染器数据层经 cropPayloadData 裁剪，
 *   §4.3 第 4 条 / §3.6 ②③层）；
 * - 规则显隐终态覆盖优先：false=规则隐藏（§3.6 ①渲染层隐藏不删值）、true=规则显示；
 * - 无覆盖时回落 props.hidden=true 静态初始隐藏（值保留，§3.6 ①）。
 * 容器（group/grid）生效隐藏时整块不渲染（子树随之不可见）。
 */
const hidden = computed(() => {
  if (effectivePermission.value === 'hidden') return true
  if (ruleVisibility.value === false) return true
  if (ruleVisibility.value === true) return false
  return fieldProps.value.hidden === true
})

/* ==================== 控件绑定（§4.3 第 1 条） ==================== */

/** 控件值：载荷值 → 组件值（物料 deserializer 值空间转换，identity 物料等价直通） */
const componentValue = computed(() => {
  const raw = props.data && typeof props.data === 'object' ? props.data[props.node.field] : undefined
  return material.value ? material.value.deserializer(raw) : raw
})

/**
 * 控件透传属性合集：剔除管理键的字段扩展属性项（pickWidgetProps）+ 渲染器注入的
 * 控件扩展属性（不走字段 props）：
 * - formula：三态 mode + 可用变量声明（§4.3 第 5/7/8 条）；
 * - attachment：modelId=数据行 ID（填写页经 getInitID 预生成后方可上传）、
 *   fieldKey=字段码（§4.2 dataId 注入口径；modelName 走 widget 缺省 'SYS-FORM'）；
 * - detailTable（F15-FE-007）：mode 三态（edit/detail/design，§4.1）+ detailCode=
 *   明细表字段码（行事件派发与行内规则覆盖查询的行上下文键）+ children=行内字段
 *   定义（Schema 字段对象 children，§2.1——不走字段 props）。
 */
const widgetProps = computed(() => ({
  ...pickWidgetProps(fieldProps.value),
  ...(props.node.type === 'formula'
    ? {
        mode: props.mode === 'design' ? 'design' : props.mode === 'detail' ? 'detail' : 'edit',
        availableVars: props.availableVars,
      }
    : {}),
  ...(props.node.type === 'attachment' ? { modelId: props.dataId, fieldKey: props.node.field } : {}),
  ...(props.node.type === 'detailTable'
    ? {
        mode: props.mode === 'design' ? 'design' : props.mode === 'detail' ? 'detail' : 'edit',
        detailCode: props.node.field,
        children: Array.isArray(props.node.children) ? props.node.children : [],
      }
    : {}),
}))

/** 禁用位：design 形态按 disabled 占位渲染（数据绑定旁路；formula design 态配置入口不受其影响） */
const controlDisabled = computed(() => props.mode === 'design')

/** 只读位：detail 形态全部只读回显；permission=readonly 组件置灰只读（值照常回显） */
const controlReadonly = computed(() => props.mode === 'detail' || effectivePermission.value === 'readonly')

/** 规则引擎动态必填覆盖（F15-FE-006 setRequired：undefined=无覆盖，回落静态基线） */
const requiredOverride = computed(() =>
  props.ruleRequiredMap && typeof props.ruleRequiredMap.get === 'function'
    ? props.ruleRequiredMap.get(props.node.field)
    : undefined,
)

/**
 * required 星号：动态必填覆盖终态优先于静态 props.required（§3.1 动态校验语义——
 * setRequired 覆盖静态基线）；detail 形态只读回显不展示。
 */
const showRequired = computed(() => {
  const base = requiredOverride.value ? requiredOverride.value.value === true : fieldProps.value.required === true
  return base && props.mode !== 'detail'
})

/** 说明文本（el-form-item label 问号提示） */
const tooltipText = computed(() => {
  const tooltip = fieldProps.value.tooltip
  return tooltip === null || tooltip === undefined ? '' : String(tooltip)
})

/* ==================== 事件上抛（§4.3 第 5 条：字段组件只 emit 原始事件） ==================== */

/** 组件值 → 载荷值（物料 serializer 值空间转换；未知物料防御直通） */
function serializeValue(value) {
  return material.value ? material.value.serializer(value) : value
}

function onWidgetInput(value) {
  emit('field-input', { field: props.node.field, value: serializeValue(value) })
}

function onWidgetChange(value) {
  emit('field-commit', { field: props.node.field, value: serializeValue(value) })
}

function onWidgetFocus(event) {
  emit('field-focus', { field: props.node.field, event })
}

function onWidgetBlur(event) {
  emit('field-blur', { field: props.node.field, event })
}

/** FormulaWidget design 态 confirm 回带表达式：上抛渲染器（画布旁路不回写，Wave 4 消费） */
function onWidgetExpression(expression) {
  emit('field-expression', { field: props.node.field, expression })
}

/**
 * 明细表行事件派发桥（F15-FE-007，§3.1/§3.4 行上下文）：附明细表字段码（detailCode）
 * 后逐层上抛至渲染器，渲染器据以向既有总线派发 onAdd/onDelete/onChange 行上下文
 * 载荷（行内规则只作用当前行）。
 */
function onWidgetRowAdd(payload) {
  emit('field-row-add', { detailCode: props.node.field, ...(payload || {}) })
}

function onWidgetRowDelete(payload) {
  emit('field-row-delete', { detailCode: props.node.field, ...(payload || {}) })
}

function onWidgetRowChange(payload) {
  emit('field-row-change', { detailCode: props.node.field, ...(payload || {}) })
}

/* 递归层事件转发（子节点 → 逐层上抛，载荷原样透传） */
function forwardInput(payload) {
  emit('field-input', payload)
}

function forwardCommit(payload) {
  emit('field-commit', payload)
}

function forwardFocus(payload) {
  emit('field-focus', payload)
}

function forwardBlur(payload) {
  emit('field-blur', payload)
}

function forwardExpression(payload) {
  emit('field-expression', payload)
}

function forwardRowAdd(payload) {
  emit('field-row-add', payload)
}

function forwardRowDelete(payload) {
  emit('field-row-delete', payload)
}

function forwardRowChange(payload) {
  emit('field-row-change', payload)
}
</script>

<style scoped>
/* 分组容器外壳（区块标题 = label，§4.3 第 2 条） */
.renderer-group {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px 16px 0;
  margin-bottom: 18px;
}

.renderer-group-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}

/* 栅格容器外壳（列内字段间距交由 el-col 内部 form-item 外边距承载） */
.renderer-grid {
  margin-bottom: 0;
}

/* label + 说明问号提示（§4.3 第 1 条） */
.renderer-field-label {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.renderer-field-tooltip {
  color: var(--el-text-color-placeholder);
  cursor: help;
}

/* 未知 type / 未注册运行时组件兜底占位（ADR-10：不抛异常、不阻断整表渲染） */
.renderer-unregistered {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px dashed var(--el-color-warning);
  border-radius: 4px;
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
  font-size: 13px;
}
</style>

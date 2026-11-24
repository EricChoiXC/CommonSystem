<template>
  <!--
    明细表组件（F15-FE-007，type=detailTable，数据容器特例）。
    口径依据：§2.1 明细表行结构（R04 一层：children 为行内字段对象数组、载荷 = 扁平行
    对象数组）、§3.1 明细行事件规则（行上下文注入，行内规则只作用当前行）、§3.6 一期
    边界（行内显隐为渲染层语义——隐藏不删值，行数据按行原样保留）、§6.3 ADR-09
    （行内公式本地预演仅展示，保存以服务端重算为准）。

    三态 mode（FormFieldNode 注入）：
    - edit：可增删行 + 行内编辑 + 行内规则接线 + 行内公式预演 + 统计行；
    - detail：只读回显 + 统计行，无操作列与添加按钮（回显存储值，不做本地预演）；
    - design：设计器画布配置态——占位骨架 + 行结构示意，数据绑定旁路（§4.1）。

    事件通道（经 FormFieldNode 冒泡至 FormRenderer 派发既有总线，不另起总线）：
    - row-add / row-delete / row-change：行事件派发桥，渲染器据以向总线派发 onAdd /
      onDelete / onChange（载荷 {field, value, oldValue, detailCode, rowIndex, row}）；
    - update:modelValue / change / focus / blur：统一契约四事件（值 = 扁平行对象数组）。
  -->
  <div class="detail-table-widget">
    <!-- design：画布配置态占位骨架 + 行结构示意（数据绑定旁路，§4.1 design 语义） -->
    <div v-if="rendererMode === 'design'" class="detail-table-design">
      <div class="detail-table-design-head">
        <span v-for="child in dataChildren" :key="child.field" class="detail-table-design-col">
          {{ child.label || child.field }}
        </span>
        <span class="detail-table-design-col detail-table-design-op">操作</span>
      </div>
      <div class="detail-table-design-row">
        <span v-for="child in dataChildren" :key="child.field" class="detail-table-design-cell"></span>
        <span class="detail-table-design-cell detail-table-design-op"></span>
      </div>
      <div class="detail-table-design-hint">
        明细表（设计态占位）：行内字段 {{ dataChildren.length }} 个；运行时可增删行、行内编辑、行内规则联动与统计行
      </div>
    </div>
    <template v-else>
      <el-table
        class="detail-table"
        :data="rows"
        border
        :show-summary="summaryVisible"
        :summary-method="summaryMethod"
        :empty-text="rendererMode === 'detail' ? '暂无明细行' : '暂无明细行，点击下方「添加行」新增'"
      >
        <el-table-column
          v-for="child in dataChildren"
          :key="child.field"
          :prop="child.field"
          :label="child.label || child.field"
          :min-width="150"
        >
          <template #default="{ row, $index }">
            <!-- R04 兜底：行内仅数据类组件（结构校验器前置拒绝，渲染层遇非数据类 child 占位） -->
            <div v-if="!isDataChild(child)" class="detail-cell-unsupported">不支持行内组件：{{ child.type }}</div>
            <!-- 行内显隐一期为渲染层语义（§3.6 一期边界）：隐藏不删值，行数据按行原样保留 -->
            <div v-else-if="isCellHidden(child, $index)" class="detail-cell-hidden">—</div>
            <div v-else class="detail-cell">
              <span v-if="isCellRequired(child, $index)" class="detail-cell-required">*</span>
              <!--
                单元格经 registry 解析行内字段物料组件渲染（与渲染器同口径：deserializer
                载荷值→组件值绑定、serializer 组件值→载荷值回写）；type=formula 单元格
                渲染 FormulaWidget 只读展示行内预演值（写入行数据，ADR-09 以服务端重算为准）。
              -->
              <component
                :is="childMaterialOf(child).component"
                :model-value="childDisplayValue(row, child)"
                v-bind="childWidgetProps(child)"
                :disabled="disabled"
                :readonly="readonly"
                @update:model-value="(value) => onCellInput($index, child, value)"
                @change="(value) => onCellCommit($index, child, value)"
                @focus="onCellFocus"
                @blur="onCellBlur"
              />
            </div>
          </template>
        </el-table-column>
        <!-- 操作列：仅编辑态渲染；行内轻量操作删除免二次确认，事后统计行即时刷新 -->
        <el-table-column v-if="rendererMode === 'edit'" label="操作" :width="90" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" :disabled="!editable" @click="removeRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <!-- 添加行：空行按行内字段 defaultValueGenerator 生成（经 serializer 归一载荷形态）；达 rowMax 禁用+提示 -->
      <div v-if="rendererMode === 'edit'" class="detail-table-toolbar">
        <el-button type="primary" plain size="small" :disabled="!editable || addDisabled" @click="addRow">添加行</el-button>
        <span v-if="addDisabled" class="detail-table-limit-tip">已达行数上限 {{ effectiveRowMax }}（超出部分不再支持新增）</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, inject, ref, watch } from 'vue'
import { getComponentMaterial } from '../registry.js'
import { FORM_RENDERER_CONTEXT_KEY, pickWidgetProps, resolveDataType } from '../rendererSupport.js'
import { MAX_DETAIL_ROWS } from '../materials/common.js'
import { evaluateRowFormula } from './rowFormula.js'
import {
  SUMMARY_FN_VALUES,
  buildDetailRow,
  calculateSummary,
  formatSummaryValue,
  normalizeDetailRows,
  padDetailRows,
  resolveSummaryLabel,
} from './detailTableSupport.js'

/**
 * 明细表 widget（type=detailTable，功能详细设计 §1.3 内容类 detailTable 行 + §2.1 行结构）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明，值 = 扁平行对象数组 [{ 行内字段码: 值 }]，
 *   空值 []）+ 扩展属性项 rowMin/rowMax/summary + 渲染器注入位（mode 三态 / detailCode
 *   字段码 / children 行内字段定义——Schema 字段对象 children 不走字段 props）+ 统一
 *   控制位 disabled/readonly（design 形态数据绑定旁路；readonly/detail 态只读回显）；
 * - emits：update:modelValue（行数组）/ change（行数组，添加/删除/行内提交时上抛 →
 *   渲染器派发宿主级 onChange，triggerField=明细表字段码的宿主规则由此触发）/ focus /
 *   blur（单元格焦点透传）/ row-add / row-delete / row-change（行事件派发桥，渲染器
 *   据以派发总线 onAdd/onDelete/onChange 行上下文载荷，§3.1/§3.4）；
 * - 容器复用口径：单元格经 registry 解析行内物料组件（getComponentMaterial），不 import
 *   其他 widget 文件；行内值变化经派发桥进 FE-006 规则引擎（行内规则只作用当前行），
 *   引擎行内覆盖经 getRowVisibilityOverride/getRowRequiredOverride 回写单元格渲染
 *   （provide/inject 注入，FORM_RENDERER_CONTEXT_KEY）；
 * - 行内公式本地预演（§6.3 ADR-09）：行内 type=formula 子字段经 rowFormula.js 轻量
 *   安全求值器本地预演（仅行内四则与 ROUND；SUM/COLLECTION/DS_QUERY 等服务端函数
 *   不求值，单元格回落「以服务端重算为准」占位）。预演值写入行数据，使统计行与提交
 *   预览口径一致——预演值仅展示，保存时以服务端重算结果覆盖落库（前端差额不报错）；
 *   主表字段值经渲染器注入 getMainValue（FE-008 设计器接入位：主表变量注入随设计器
 *   装配同通道）；
 * - 行数边界：初始化/回显行数不足 rowMin 时补空行至 rowMin（对齐服务端 S2 行数下限
 *   校验语义）；添加行达 rowMax 上限（缺省 MAX_DETAIL_ROWS=200）禁用+提示；删除行
 *   不做下限阻断（低于 rowMin 由校验器与保存校验 S2 报错承载）；
 * - 行内显隐一期为渲染层语义（§3.6 一期边界）：行内 hide 仅隐藏单元格渲染，行数据
 *   按行原样保留（不删值、不裁剪），服务端行内校验全量执行；
 * - 统计行：props.summary[{field, fn: sum/avg/count}] 对当前行数组响应式即时计算
 *   （数值容错：非数值跳过；count 计行数；空数组/无配置不渲染统计行），不走规则引擎；
 * - 固定属性项由渲染器层承载（form-item 外壳由渲染器承载，单元格列头即标签、无需
 *   form-item），本组件不感知具体业务规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值：扁平行对象数组 [{ 行内字段码: 值 }]（§2.1），空值 [] */
  modelValue: { type: Array, default: () => [] },
  /** 渲染形态三态：edit（可增删改）/ detail（只读回显 + 统计行）/ design（画布占位，数据绑定旁路） */
  mode: { type: String, default: 'edit' },
  /** 明细表字段码（FormFieldNode 注入）——行事件派发与行内规则覆盖查询的行上下文键 */
  detailCode: { type: String, default: '' },
  /** 行内字段定义（Schema children，FormFieldNode 注入；R04 一层、仅数据类组件） */
  children: { type: Array, default: () => [] },
  /** 行数下限（初始化/回显不足时补空行至该值，对齐服务端 S2 行数下限校验语义） */
  rowMin: { type: [Number, String], default: 0 },
  /** 行数上限（缺省 MAX_DETAIL_ROWS=200；添加行达上限禁用+提示） */
  rowMax: { type: [Number, String], default: null },
  /** 统计行 [{ field, fn: sum/avg/count }]（对当前行数组即时计算，不走规则引擎） */
  summary: { type: Array, default: () => [] },
  /** 统一控制位：禁用（design 形态由 FormRenderer 经此注入占位信号） */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（detail 形态/字段权限 readonly 注入，单元格只读回显） */
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur', 'row-add', 'row-delete', 'row-change'])

/* ==================== 渲染形态与渲染器注入（行上下文桥） ==================== */

/** 形态归一：非法值回落 edit（§4.1 三形态；与 FormRenderer.rendererMode 同口径） */
const rendererMode = computed(() => (['edit', 'detail', 'design'].includes(props.mode) ? props.mode : 'edit'))

/** 是否可编辑（edit 且未被禁用/只读；detail/design 不产生数据写入） */
const editable = computed(() => rendererMode.value === 'edit' && !props.disabled && !props.readonly)

/**
 * 渲染器注入上下文（provide/inject，不另起总线）：
 * - stateVersion：规则风暴版本号（Ref）——引擎行内覆盖为普通 JS 载体，风暴结束后自增，
 *   单元格显隐/必填 computed 消费该版本号驱动重算（F15-FE-006 状态桥）；
 * - getRowVisibilityOverride / getRowRequiredOverride：引擎行内覆盖查询（§3.1 行上下文）；
 * - getMainValue：主表字段值读取（行内公式预演上下文——行内同码优先、主表回落，§6.3）。
 * 独立使用（无渲染器挂载）时为 null：无规则覆盖、无主表变量，组件仍可渲染。
 */
const rowContext = inject(FORM_RENDERER_CONTEXT_KEY, null)
const stateVersion = rowContext && rowContext.stateVersion ? rowContext.stateVersion : null

/** 消费规则状态版本号（渲染/求值过程中读取，风暴后驱动单元格重算） */
function trackRowStateVersion() {
  if (stateVersion) {
    void stateVersion.value
  }
}

/* ==================== 行内字段与物料解析（registry 口径，与渲染器同源） ==================== */

/** 行内字段定义（脏项防御过滤；R04 一层——children 为行内字段对象数组） */
const dataChildren = computed(() =>
  (Array.isArray(props.children) ? props.children : []).filter((child) => child && typeof child === 'object' && child.field && child.type),
)

/** 行内子字段物料（经 registry 读取接口取，不 import 其他 widget 运行时文件） */
function childMaterialOf(child) {
  return getComponentMaterial(child.type)
}

/**
 * 行内子字段是否数据类（R04：行内仅数据类组件）——category=DATA 且 dataType 解析
 * 非 null（与规则引擎 isDataField 访问器同口径）；内容/布局/嵌套明细表由 Schema
 * 结构校验器前置拒绝，此处渲染层占位兜底。
 */
function isDataChild(child) {
  const material = childMaterialOf(child)
  return !!material && material.category === 'DATA' && resolveDataType(material, child.props) !== null
}

/** 单元格透传属性（剔除管理键的扩展属性项，与 FormFieldNode.pickWidgetProps 同口径） */
function childWidgetProps(child) {
  return pickWidgetProps(child.props && typeof child.props === 'object' ? child.props : {})
}

/** 单元格显示值：行载荷值 → 组件值（物料 deserializer 值空间转换，identity 等价直通） */
function childDisplayValue(row, child) {
  const material = childMaterialOf(child)
  const raw = row && typeof row === 'object' && !Array.isArray(row) ? row[child.field] : undefined
  if (!material) return raw === undefined ? null : raw
  return material.deserializer(raw)
}

/** 单元格组件值 → 行载荷值（物料 serializer 值空间转换；未知物料防御直通） */
function serializeCellValue(child, value) {
  const material = childMaterialOf(child)
  return material ? material.serializer(value) : value
}

/* ==================== 行数据工作副本（clone-on-write，回环拦截） ==================== */

/** 行数组工作副本（props.modelValue 为权威源，编辑过程 clone-on-write 后回写） */
const rows = ref([])

/** 最近一次外发的行数组（v-model 回环拦截标记——identity 序列化下同引用回传不重同步） */
let lastEmittedRows = null

/** 行内值交互段旧值缓存（cellKey → 交互段首次写入前的载荷值；change 时消费作 oldValue） */
const pendingCellOldValues = new Map()

function cellKey(rowIndex, field) {
  return `${rowIndex}\u0000${field}`
}

function toNullish(value) {
  return value === undefined ? null : value
}

/** 行浅克隆（clone-on-write：旧数组引用保持原样，供交互段 oldValue 取原始值） */
function cloneRows(rowsList) {
  return (Array.isArray(rowsList) ? rowsList : []).map((row) =>
    row && typeof row === 'object' && !Array.isArray(row) ? { ...row } : row,
  )
}

/** 外发行数组（更新工作副本 → 回环标记 → update:modelValue 上抛载荷形态值） */
function commitRows(nextRows) {
  rows.value = nextRows
  lastEmittedRows = nextRows
  emit('update:modelValue', nextRows)
}

/** 生效行数下限（非法值回落 0） */
const rowMinNum = computed(() => {
  const num = Number(props.rowMin)
  return Number.isFinite(num) && num > 0 ? Math.floor(num) : 0
})

/** 生效行数上限：props.rowMax 为权威、防御性不超过 MAX_DETAIL_ROWS（§2.2 资源限制） */
const effectiveRowMax = computed(() => {
  const num = Number(props.rowMax)
  const configured = Number.isFinite(num) && num > 0 ? Math.floor(num) : MAX_DETAIL_ROWS
  return Math.min(configured, MAX_DETAIL_ROWS)
})

const addDisabled = computed(() => rows.value.length >= effectiveRowMax.value)

/**
 * 数据同步（初始化 / 回显 / 引擎或宿主换新数据）：props.modelValue → 工作副本；
 * 行数不足 rowMin 时补空行至 rowMin（§2.1 行数下限对齐服务端 S2 校验语义）——
 * edit 态补行结果回写载荷一次（detail 只读回显仅本地补足展示，不回写）；
 * design 态数据绑定旁路（骨架示意，不消费行数据）。
 */
function syncRows() {
  if (rendererMode.value === 'design') return
  const incoming = normalizeDetailRows(props.modelValue)
  const padded = rowMinNum.value > 0 && incoming.length < rowMinNum.value
    ? padDetailRows(incoming, rowMinNum.value, () => buildDetailRow(dataChildren.value, getComponentMaterial))
    : incoming
  rows.value = padded
  if (padded !== incoming && rendererMode.value === 'edit') {
    commitRows(padded)
  }
}

watch(
  () => props.modelValue,
  (value) => {
    if (value === lastEmittedRows) return
    pendingCellOldValues.clear()
    syncRows()
  },
  { immediate: true },
)

// 形态切换（edit/detail 互切等）：行数补足语义随形态变化，重同步一次
watch(rendererMode, () => {
  pendingCellOldValues.clear()
  syncRows()
})

/* ==================== 行内公式本地预演（§6.3 ADR-09，显示专用） ==================== */

/**
 * 对行数组执行行内公式预演并把预演值写入行数据（统计行/提交预览口径一致）：
 * - 仅 edit 态执行（detail 只读回显存储值不做本地计算；design 旁路）；
 * - 按 children 声明序单趟求值——公式间依赖仅支持前向引用，交错/反向依赖以服务端
 *   重算为准（FormFormulaEvaluator S6 依赖图拓扑序为权威）；
 * - 不可求值（脏输入/白名单外函数）写 null，单元格回落 FormulaWidget 空值占位
 *   「暂无计算值，以服务端重算为准」；
 * - 预演值会被服务端重算覆盖（ADR-09：保存时以服务端重算结果为准落库，前端差额不报错）。
 */
function applyFormulaPreview(nextRows) {
  if (rendererMode.value !== 'edit') return nextRows
  const formulaChildren = dataChildren.value.filter((child) => child.type === 'formula')
  if (formulaChildren.length === 0) return nextRows
  const getMainValue = rowContext && typeof rowContext.getMainValue === 'function' ? rowContext.getMainValue : () => undefined
  for (const row of nextRows) {
    if (!row || typeof row !== 'object' || Array.isArray(row)) continue
    for (const child of formulaChildren) {
      const childProps = child.props && typeof child.props === 'object' ? child.props : {}
      const expression = childProps.expression
      if (typeof expression !== 'string' || expression.trim() === '') continue
      const result = evaluateRowFormula(expression, { rowValues: row, getMainValue })
      const value = result && result.ok ? result.value : null
      if (row[child.field] !== value) row[child.field] = value
    }
  }
  return nextRows
}

/* ==================== 单元格编辑（输入过程值 / 提交值） ==================== */

/**
 * 单元格输入过程值（update:modelValue）：clone-on-write 写入工作副本 → 行内公式预演
 * → update:modelValue 外发行数组（渲染器写入内部数据并回传全量载荷）。
 * 交互段首次写入前缓存旧值（change 时作 oldValue，与渲染器 pendingOldValues 同语义）。
 */
function onCellInput(rowIndex, child, rawValue) {
  if (!editable.value) return
  const currentRow = rows.value[rowIndex]
  if (!currentRow || typeof currentRow !== 'object' || Array.isArray(currentRow)) return
  const key = cellKey(rowIndex, child.field)
  if (!pendingCellOldValues.has(key)) {
    pendingCellOldValues.set(key, toNullish(currentRow[child.field]))
  }
  const next = cloneRows(rows.value)
  const row = next[rowIndex]
  if (!row || typeof row !== 'object') return
  row[child.field] = serializeCellValue(child, rawValue)
  applyFormulaPreview(next)
  commitRows(next)
}

/**
 * 单元格提交值（change）：失焦收口值与输入过程值不同（如 number 钳制）时补写；
 * 随后经派发桥上抛——row-change（渲染器派发总线 onChange 行上下文载荷，行内规则
 * 只作用当前行 §3.1）+ 统一契约 change（行数组，渲染器派发宿主级 onChange，
 * triggerField=明细表字段码的宿主规则由此触发）。
 */
function onCellCommit(rowIndex, child, rawValue) {
  if (rendererMode.value === 'design') return
  const value = serializeCellValue(child, rawValue)
  const key = cellKey(rowIndex, child.field)
  const currentRow = rows.value[rowIndex]
  const before = currentRow && typeof currentRow === 'object' && !Array.isArray(currentRow) ? toNullish(currentRow[child.field]) : null
  if (editable.value && (!currentRow || currentRow[child.field] !== value)) {
    if (!pendingCellOldValues.has(key)) pendingCellOldValues.set(key, before)
    const next = cloneRows(rows.value)
    const row = next[rowIndex]
    if (row && typeof row === 'object' && !Array.isArray(row)) {
      row[child.field] = value
      applyFormulaPreview(next)
      commitRows(next)
    }
  }
  const oldValue = pendingCellOldValues.has(key) ? pendingCellOldValues.get(key) : before
  pendingCellOldValues.delete(key)
  const committedRow = rows.value[rowIndex] || null
  emit('row-change', { field: child.field, value, oldValue, rowIndex, row: committedRow })
  if (rendererMode.value === 'edit') {
    emit('change', rows.value)
  }
}

function onCellFocus(event) {
  emit('focus', event)
}

function onCellBlur(event) {
  emit('blur', event)
}

/* ==================== 增删行（§3.4 onAdd 行初始化 / onDelete 行后处理） ==================== */

/**
 * 添加行：按行内字段 defaultValueGenerator 生成空行对象（经 serializer 归一载荷形态）；
 * 达 rowMax 上限禁用（addDisabled）。行事件经派发桥上抛：row-add（渲染器派发总线
 * onAdd——行初始化动作，行上下文注入，行内规则只作用当前行 §3.4）+ 统一契约 change
 * （宿主级 onChange）。行内公式预演对新行同步执行（行内引用为空值时回落 null 占位）。
 */
function addRow() {
  if (!editable.value || addDisabled.value) return
  const next = [...cloneRows(rows.value), buildDetailRow(dataChildren.value, getComponentMaterial)]
  applyFormulaPreview(next)
  commitRows(next)
  const rowIndex = next.length - 1
  emit('row-add', { rowIndex, row: next[rowIndex] })
  emit('change', next)
}

/**
 * 删除行：行内轻量操作，二次确认可免（直接删）；统计行经响应式计算事后即时刷新
 * （§3.4 onDelete「合计刷新由统计行内置，无需规则」）。行事件经派发桥上抛：row-delete
 * （渲染器派发总线 onDelete，行上下文承载被删行记录）+ 统一契约 change（宿主级
 * onChange）。行号重排后行内动态状态（引擎按行号索引）不重排——受影响行再次编辑时
 * 由行规则重放自愈，行级状态重排随二期评估（§3.6 一期边界）。
 */
function removeRow(rowIndex) {
  if (!editable.value) return
  if (rowIndex === null || rowIndex === undefined || rowIndex < 0 || rowIndex >= rows.value.length) return
  const removedRow = rows.value[rowIndex] || null
  const next = cloneRows(rows.value).filter((_, index) => index !== rowIndex)
  pendingCellOldValues.clear()
  applyFormulaPreview(next)
  commitRows(next)
  emit('row-delete', { rowIndex, row: removedRow })
  emit('change', next)
}

/* ==================== 统计行（响应式即时计算，不走规则引擎） ==================== */

/** 统计配置（脏项过滤：field 非空 + fn ∈ sum/avg/count 取值域） */
const summaryItems = computed(() =>
  (Array.isArray(props.summary) ? props.summary : []).filter(
    (item) => item && typeof item === 'object' && item.field && SUMMARY_FN_VALUES.includes(item.fn),
  ),
)

/** 统计行显隐：无配置或空数组不渲染（§1.3 summary 语义） */
const summaryVisible = computed(() => summaryItems.value.length > 0 && rows.value.length > 0)

/**
 * el-table 统计行（summary-method）：首列「统计」标签 + 命中 summary 配置的列展示
 * 「方式：值」。sum/avg 数值容错（非数值跳过、无有效数值展示「—」）、count 计行数；
 * 消费 rows 响应式依赖——增删行/行内编辑（含公式预演写值）后即时刷新。
 */
function summaryMethod({ columns }) {
  const columnList = Array.isArray(columns) ? columns : []
  return columnList.map((column, index) => {
    if (index === 0) return '统计'
    const item = summaryItems.value.find((entry) => entry.field === column.property)
    if (!item) return ''
    const value = calculateSummary(rows.value, item.field, item.fn)
    return `${resolveSummaryLabel(item.fn)}：${formatSummaryValue(value)}`
  })
}

/* ==================== 行内规则覆盖回写（FE-006 引擎 → 单元格渲染） ==================== */

/**
 * 单元格是否隐藏（行内显隐一期为渲染层语义，§3.6 一期边界）：
 * - 引擎行内覆盖（getRowVisibilityOverride）优先：false=hide 命中（隐藏不删值，行数据
 *   按行原样保留）、true=show 命中（可恢复行内静态初始隐藏）；
 * - 无覆盖回落行内子字段静态 props.hidden。
 * 消费引擎状态版本号（trackRowStateVersion）驱动风暴后重算。
 */
function isCellHidden(child, rowIndex) {
  if (!isDataChild(child)) return false
  const childProps = child.props && typeof child.props === 'object' ? child.props : {}
  if (rowContext && typeof rowContext.getRowVisibilityOverride === 'function' && props.detailCode) {
    trackRowStateVersion()
    const override = rowContext.getRowVisibilityOverride(props.detailCode, rowIndex, child.field)
    if (override === false) return true
    if (override === true) return false
  }
  return childProps.hidden === true
}

/**
 * 单元格是否必填标记（行内动态必填覆盖优先于行内静态 props.required，§3.1 动态校验
 * 语义；明细表单元格列头即标签，必填星号随单元格渲染）。
 */
function isCellRequired(child, rowIndex) {
  const childProps = child.props && typeof child.props === 'object' ? child.props : {}
  if (childProps.required === true) return true
  if (rowContext && typeof rowContext.getRowRequiredOverride === 'function' && props.detailCode) {
    trackRowStateVersion()
    const override = rowContext.getRowRequiredOverride(props.detailCode, rowIndex, child.field)
    if (override && override.value === true) return true
  }
  return false
}
</script>

<style scoped>
.detail-table-widget {
  width: 100%;
}

.detail-table {
  width: 100%;
}

/* 单元格（控件本体 + 必填星号；列头即标签，无需 form-item 外壳） */
.detail-cell {
  display: flex;
  align-items: center;
  gap: 2px;
  width: 100%;
}

.detail-cell :deep(.el-input-number),
.detail-cell :deep(.el-select),
.detail-cell :deep(.el-date-editor),
.detail-cell :deep(.el-cascader) {
  width: 100%;
}

.detail-cell-required {
  color: var(--el-color-danger);
  font-size: 14px;
  line-height: 1;
  flex-shrink: 0;
}

/* 行内显隐占位（渲染层语义：隐藏不删值，行数据按行原样保留，§3.6 一期边界） */
.detail-cell-hidden {
  color: var(--el-text-color-placeholder);
  text-align: center;
}

/* R04 兜底占位（非数据类行内组件，结构校验器前置拒绝后的渲染层兜底） */
.detail-cell-unsupported {
  padding: 4px 8px;
  border: 1px dashed var(--el-color-warning);
  border-radius: 4px;
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
  font-size: 12px;
}

/* 添加行工具条（达 rowMax 上限时提示文案） */
.detail-table-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.detail-table-limit-tip {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

/* design 态占位骨架（画布配置态，数据绑定旁路） */
.detail-table-design {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 10px 12px;
}

.detail-table-design-head {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.detail-table-design-col {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 600;
}

.detail-table-design-op {
  flex: 0 0 72px;
  text-align: center;
}

.detail-table-design-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.detail-table-design-cell {
  flex: 1;
  height: 28px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}

.detail-table-design-cell.detail-table-design-op {
  flex: 0 0 72px;
}

.detail-table-design-hint {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>

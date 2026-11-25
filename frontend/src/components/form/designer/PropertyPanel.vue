<template>
  <!--
    表单设计器 · 右侧属性面板（F15-FE-008，UI 分册 §4.5）。
    按选中物料的 propSchema（物料七件套之一）动态渲染属性表单：
    - 固定属性项按类别裁剪（materials/common.js dataFixedPropSchema 等模板，§4.5.1）；
    - 扩展属性项按 §4.5.2 登记表逐项渲染（EDITOR_TYPES 全部编辑器类型支持）；
    - 未选中回落画布级摘要（表单名称/编码/状态只读 + 引导文案，§4.5）。
    绑定目标：target='props' → field.props[key]；'field' → 字段对象顶层（label/field）；
    'children' → 字段对象 children（grid 列配置）。全部经 emit 上抛宿主 FormDesigner
    统一落 Schema（本面板不直接改 props 入参——索引条目属宿主 Schema 状态）。
    公式 expression：只读输入框 +【设计公式】按钮打开 FormulaDesigner 公式弹窗
    （F14 契约复用：availableVars=表单字段码声明、expectedReturnType 随字段 props，
    §4.5.2 formula 行——不允许在输入框直接手改）。
    设计器内部组件：仅 FormDesigner 消费，不单独对外导出。
  -->
  <div class="property-panel">
    <template v-if="entry">
      <div class="panel-caption">
        选中组件：<b>{{ entry.label || entry.field }}</b>
        <span class="panel-caption-sub">{{ entry.type }} · {{ entry.path }}</span>
      </div>
      <el-form class="panel-form" label-width="92px" size="small" @submit.prevent>
        <template v-for="item in renderedItems" :key="item._key">
          <div v-if="item._divider" class="props-divider">── 扩展属性 ──</div>
          <el-form-item v-else :label="item.label">
            <!-- input：单行文本 -->
            <el-input
              v-if="item.editor === 'input'"
              :model-value="valueOf(item)"
              :placeholder="item.key === 'tooltip' ? '运行态渲染为字段旁问号提示' : ''"
              @update:model-value="(value) => setValue(item, value)"
            />
            <!-- textarea：多行文本域 -->
            <el-input
              v-else-if="item.editor === 'textarea'"
              :model-value="valueOf(item)"
              type="textarea"
              :rows="3"
              @update:model-value="(value) => setValue(item, value)"
            />
            <!-- number：el-input-number（min/max/precision 随 propSchema） -->
            <el-input-number
              v-else-if="item.editor === 'number'"
              :model-value="valueOf(item)"
              :min="item.min"
              :max="item.max"
              :precision="item.precision"
              controls-position="right"
              @update:model-value="(value) => setValue(item, value)"
            />
            <!-- switch -->
            <el-switch v-else-if="item.editor === 'switch'" :model-value="valueOf(item) === true" @update:model-value="(value) => setValue(item, value)" />
            <!-- select：静态 options -->
            <el-select
              v-else-if="item.editor === 'select'"
              :model-value="valueOf(item)"
              class="panel-select"
              @update:model-value="(value) => setValue(item, value)"
            >
              <el-option v-for="option in item.options || []" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <!-- radio：排列方式等选项组 -->
            <el-radio-group v-else-if="item.editor === 'radio'" :model-value="valueOf(item)" @update:model-value="(value) => setValue(item, value)">
              <el-radio v-for="option in item.options || []" :key="option.value" :value="option.value">{{ option.label }}</el-radio>
            </el-radio-group>
            <!-- date：dataType=DATE 默认值专用（值以格式化字符串落库） -->
            <el-date-picker
              v-else-if="item.editor === 'date'"
              :model-value="valueOf(item)"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              @update:model-value="(value) => setValue(item, value)"
            />
            <!-- json：JSON 文本域（前端 JSON 合法性预检；ARRAY/OBJECT 一期承载形态） -->
            <template v-else-if="item.editor === 'json'">
              <el-input
                :model-value="jsonDraft.text"
                type="textarea"
                :rows="3"
                placeholder='JSON 文本，如 ["a","b"] 或 {"key":1}；留空表示无默认值'
                @update:model-value="(value) => handleJsonInput(item, value)"
              />
              <div v-if="jsonDraft.error" class="props-error">{{ jsonDraft.error }}</div>
            </template>
            <!-- fieldCode：字段码格式与同层唯一性校验（§4.5.1） -->
            <template v-else-if="item.editor === 'fieldCode'">
              <el-input :model-value="valueOf(item)" @update:model-value="(value) => setValue(item, value)" />
              <div v-if="fieldCodeError" class="props-error">{{ fieldCodeError }}</div>
            </template>
            <!-- formula：只读输入框 +【设计公式】按钮 → FormulaDesigner 弹窗 -->
            <template v-else-if="item.editor === 'formula'">
              <el-input :model-value="valueOf(item) || ''" readonly placeholder="未配置公式">
                <template #append>
                  <el-button @click="formulaDesignerVisible = true">设计公式</el-button>
                </template>
              </el-input>
            </template>
            <!-- contextSelect：选项由画布上下文提供（context 声明来源） -->
            <el-select
              v-else-if="item.editor === 'contextSelect'"
              :model-value="valueOf(item) || ''"
              class="panel-select"
              clearable
              placeholder="选择字段"
              @update:model-value="(value) => setValue(item, value || '')"
            >
              <el-option
                v-for="option in contextOptionsFor(item)"
                :key="option.code"
                :label="`${option.label}（${option.code}）`"
                :value="option.code"
              />
            </el-select>
            <!-- datasource：启用数据源清单（api/datasource.js#getDatasourceOptions，存编码不存快照） -->
            <el-select
              v-else-if="item.editor === 'datasource'"
              :model-value="valueOf(item) || ''"
              class="panel-select"
              filterable
              placeholder="选择数据源"
              @update:model-value="(value) => setValue(item, value || '')"
            >
              <el-option v-for="option in datasourceOptions" :key="option.code" :label="`${option.name}（${option.code}）`" :value="option.code" />
            </el-select>
            <!-- condition：显隐条件编辑（operator 十项 + value，可空） -->
            <div v-else-if="item.editor === 'condition'" class="condition-editor">
              <el-select :model-value="conditionOf(item).operator || ''" class="panel-select" clearable placeholder="无条件" @update:model-value="(value) => setConditionOperator(item, value)">
                <el-option v-for="option in ruleOperatorOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <el-input
                v-if="conditionOf(item).operator && !isNoValueOperator(conditionOf(item).operator)"
                :model-value="conditionValueText(conditionOf(item))"
                placeholder="条件值"
                @update:model-value="(value) => setConditionValue(item, value)"
              />
            </div>
            <!-- gridColumns：栅格列数与各列 span（target='children'，24 栅格制合计超 24 提示） -->
            <div v-else-if="item.editor === 'gridColumns'" class="grid-columns-editor">
              <div class="grid-columns-toolbar">
                <span class="grid-columns-label">列数</span>
                <el-input-number :model-value="gridColumns.length" :min="1" :max="12" controls-position="right" @update:model-value="handleColumnCount" />
                <span class="grid-span-sum" :class="{ 'is-over': gridSpanSum > 24 }">span 合计 {{ gridSpanSum }}/24</span>
              </div>
              <div v-for="(column, columnIndex) in gridColumns" :key="columnIndex" class="grid-column-row">
                <span class="grid-columns-label">第{{ columnIndex + 1 }}列</span>
                <el-input-number :model-value="column.span" :min="1" :max="24" controls-position="right" @update:model-value="(value) => handleColumnSpan(columnIndex, value)" />
                <span class="grid-column-fields">字段 {{ (column.fields || []).length }} 个</span>
              </div>
              <div v-if="gridColumnMessage" class="props-error">{{ gridColumnMessage }}</div>
              <div class="props-tip">列内字段经画布嵌套放置承载（拖拽组件至对应列）</div>
            </div>
            <!-- none：不渲染输入控件（defaultValue 由默认值生成器兜底） -->
            <span v-else-if="item.editor === 'none'" class="props-none">由默认值生成器兜底</span>
            <!-- table：明细表编辑器（columns 声明列，行内增删） -->
            <div v-else-if="item.editor === 'table'" class="table-editor">
              <el-table :data="tableRowsOf(item)" size="small" border empty-text="暂无数据，点击下方「添加」新增">
                <el-table-column v-for="column in item.columns || []" :key="column.key" :label="column.label" :min-width="columnEditorWidth(column)">
                  <template #default="{ row }">
                    <el-input
                      v-if="column.editor === 'input'"
                      :model-value="row[column.key]"
                      @update:model-value="(value) => setTableCell(item, row, column, value)"
                    />
                    <el-input-number
                      v-else-if="column.editor === 'number'"
                      :model-value="row[column.key]"
                      controls-position="right"
                      @update:model-value="(value) => setTableCell(item, row, column, value)"
                    />
                    <el-select v-else-if="column.editor === 'select'" :model-value="row[column.key]" @update:model-value="(value) => setTableCell(item, row, column, value)">
                      <el-option v-for="option in column.options || []" :key="option.value" :label="option.label" :value="option.value" />
                    </el-select>
                    <el-select
                      v-else-if="column.editor === 'contextSelect'"
                      :model-value="row[column.key] || ''"
                      clearable
                      placeholder="选择字段"
                      @update:model-value="(value) => setTableCell(item, row, column, value || '')"
                    >
                      <el-option
                        v-for="option in contextOptionsFor(column)"
                        :key="option.code"
                        :label="`${option.label}（${option.code}）`"
                        :value="option.code"
                      />
                    </el-select>
                    <!-- condition 单元格编辑器（cascade 选择项显隐条件 showCondition，R08 条件 DSL） -->
                    <div v-else-if="column.editor === 'condition'" class="condition-editor">
                      <el-select
                        :model-value="(row[column.key] && row[column.key].operator) || ''"
                        clearable
                        placeholder="无条件（恒显示）"
                        @update:model-value="(value) => setTableCellConditionOperator(item, row, column, value)"
                      >
                        <el-option v-for="option in ruleOperatorOptions" :key="option.value" :label="option.label" :value="option.value" />
                      </el-select>
                      <el-input
                        v-if="row[column.key] && row[column.key].operator && !isNoValueOperator(row[column.key].operator)"
                        :model-value="conditionValueText(row[column.key])"
                        placeholder="条件值"
                        @update:model-value="(value) => setTableCellConditionValue(item, row, column, value)"
                      />
                    </div>
                    <span v-else>{{ row[column.key] === null || row[column.key] === undefined ? '—' : row[column.key] }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="64" fixed="right">
                  <template #default="{ $index }">
                    <el-button link type="danger" size="small" @click="removeTableRow(item, $index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="table-editor-toolbar">
                <el-button size="small" @click="addTableRow(item)">添加</el-button>
              </div>
            </div>
            <div v-if="item.tip" class="props-tip">{{ item.tip }}</div>
          </el-form-item>
        </template>
      </el-form>
      <!-- FormulaDesigner 公式弹窗（F14 契约：visible / availableVars / expectedReturnType / expression） -->
      <FormulaDesigner
        :visible="formulaDesignerVisible"
        :expression="String(entry.props.expression || '')"
        :expected-return-type="String(entry.props.expectedReturnType || '')"
        :available-vars="availableVars"
        @update:visible="formulaDesignerVisible = $event"
        @confirm="handleFormulaConfirm"
      />
    </template>
    <!-- 未选中回落画布级摘要（§4.5） -->
    <div v-else class="panel-empty">
      <div class="panel-caption">画布摘要</div>
      <div class="summary-item"><span class="summary-label">表单名称</span><span class="summary-value">{{ canvasSummary.formName || '未命名表单' }}</span></div>
      <div class="summary-item"><span class="summary-label">表单编码</span><span class="summary-value">{{ canvasSummary.formCode || '-' }}</span></div>
      <div class="summary-item"><span class="summary-label">字段总数</span><span class="summary-value">{{ canvasSummary.fieldCount }} / {{ maxFieldCount }}</span></div>
      <div class="summary-item"><span class="summary-label">规则总数</span><span class="summary-value">{{ canvasSummary.ruleCount }} / {{ maxRuleCount }}</span></div>
      <div class="summary-hint">请从左侧物料栏添加组件，或点击画布中的组件配置属性与规则</div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import FormulaDesigner from '../../formula/FormulaDesigner.vue'
import { FIELD_CODE_PATTERN, MAX_FIELD_COUNT, MAX_RULE_COUNT, MULTI_VALUE_OPERATORS, NO_VALUE_OPERATORS, RULE_OPERATOR_OPTIONS } from './designerSupport.js'

/**
 * 属性面板 props：
 * - entry：选中字段的扁平索引条目（designerSupport.buildFieldIndex 产物元素，含 node/
 *   props/parentList/path）；null = 未选中（画布级摘要回落）；
 * - siblingCodes：同层级既有字段码集合（排除选中字段自身，fieldCode 唯一性校验消费）；
 * - dataFieldOptions：主表层级数据类字段选项（级联 parentField，context='dataFields'）；
 * - detailNumericOptions：宿主明细表行内数值类字段选项（summary 统计字段，
 *   context='detailNumericFields'；非明细行内上下文为空数组）；
 * - availableVars：FormulaDesigner 变量声明（宿主从 Schema 派生，rendererSupport.
 *   deriveAvailableVars，含明细数组变量 ${list.field} 投影）；
 * - datasourceOptions：启用数据源清单（宿主经 api/datasource.js#getDatasourceOptions
 *   异步加载，元素 { code, name }）；
 * - canvasSummary：画布级摘要（未选中时展示）。
 */
const props = defineProps({
  entry: { type: Object, default: null },
  siblingCodes: { type: Array, default: () => [] },
  dataFieldOptions: { type: Array, default: () => [] },
  detailNumericOptions: { type: Array, default: () => [] },
  availableVars: { type: Array, default: () => [] },
  datasourceOptions: { type: Array, default: () => [] },
  canvasSummary: {
    type: Object,
    default: () => ({ formName: '', formCode: '', fieldCount: 0, ruleCount: 0 }),
  },
})

const emit = defineEmits(['prop-change', 'field-change', 'children-change'])

/** 固定属性项键（§4.5.1：名称/ID/必填/说明/默认值；首个非固定项前渲染「扩展属性」分隔） */
const FIXED_KEYS = ['label', 'field', 'required', 'tooltip', 'defaultValue']

const maxFieldCount = MAX_FIELD_COUNT
const maxRuleCount = MAX_RULE_COUNT

/** 当前选中物料的 propSchema（经 registry 注册的物料元数据，设计器不写死物料知识） */
const propSchema = computed(() => (props.entry && props.entry.material && Array.isArray(props.entry.material.propSchema) ? props.entry.material.propSchema : []))

/**
 * 渲染项：propSchema 顺序 + 首个非固定属性项前插入「扩展属性」分隔标记；
 * _key 组合序号防同名 key（grid 列配置 key=columns 与 table 组件列重名场景）。
 */
const renderedItems = computed(() => {
  const items = []
  let dividerDone = false
  propSchema.value.forEach((item, index) => {
    if (!item || typeof item !== 'object' || !item.key) return
    if (!dividerDone && !FIXED_KEYS.includes(item.key)) {
      items.push({ _divider: true, _key: `__divider_${index}` })
      dividerDone = true
    }
    items.push({ ...item, _key: `${item.key}__${index}` })
  })
  return items
})

/* ==================== 值读写（target 三形态绑定） ==================== */

/** 读取属性项当前值（target='props' → props[key]；'field' → 字段对象顶层；'children' → grid 列数组） */
function valueOf(item) {
  if (!props.entry) return null
  const target = item.target || 'props'
  if (target === 'field') return props.entry.node[item.key]
  if (target === 'children') return props.entry.node.children
  const propsValue = props.entry.props[item.key]
  return propsValue === undefined ? null : propsValue
}

/** 写属性项（emit 上抛宿主统一落 Schema） */
function setValue(item, value) {
  if (!props.entry) return
  const target = item.target || 'props'
  if (target === 'field') {
    emit('field-change', { path: props.entry.path, key: item.key, value })
  } else if (target === 'children') {
    emit('children-change', { path: props.entry.path, children: value })
  } else {
    emit('prop-change', { path: props.entry.path, key: item.key, value })
  }
}

/* ==================== 字段码校验（§4.5.1：格式 + 同层唯一） ==================== */

const fieldCodeError = computed(() => {
  if (!props.entry || props.entry.node.field === undefined) return ''
  const code = String(props.entry.node.field)
  if (!new RegExp(FIELD_CODE_PATTERN).test(code)) {
    return '字段码必须为字母/下划线起始，仅字母数字下划线'
  }
  if (props.siblingCodes.includes(code)) {
    return '字段码与同层级既有字段码重复（同一层级内必须唯一）'
  }
  return ''
})

/* ==================== json 编辑器（JSON 合法性预检，§4.5.1 ARRAY/OBJECT 承载） ==================== */

const jsonDraft = reactive({ key: '', text: '', error: '' })

const jsonItem = computed(() => renderedItems.value.find((item) => !item._divider && item.editor === 'json') || null)

// 选中态切换（或 json 项变化）时以当前值同步草稿文本（外部 undo/导入回填后文本一致）
watch(
  [() => props.entry && props.entry.path, jsonItem],
  () => {
    const item = jsonItem.value
    if (!item || !props.entry) {
      jsonDraft.key = ''
      jsonDraft.text = ''
      jsonDraft.error = ''
      return
    }
    const key = `${props.entry.path}::${item.key}`
    if (jsonDraft.key !== key) {
      jsonDraft.key = key
      const value = valueOf(item)
      jsonDraft.text = value === null || value === undefined ? '' : JSON.stringify(value)
      jsonDraft.error = ''
    }
  },
  { immediate: true },
)

function handleJsonInput(item, text) {
  jsonDraft.text = text
  if (text === null || text.trim() === '') {
    jsonDraft.error = ''
    setValue(item, null)
    return
  }
  try {
    const parsed = JSON.parse(text)
    jsonDraft.error = ''
    setValue(item, parsed)
  } catch (error) {
    jsonDraft.error = `JSON 格式不正确（${error && error.message ? error.message : '解析失败'}），未应用修改`
  }
}

/* ==================== condition 编辑器（顶层 + 明细表单元格共用逻辑） ==================== */

const ruleOperatorOptions = RULE_OPERATOR_OPTIONS

function isNoValueOperator(operator) {
  return NO_VALUE_OPERATORS.includes(operator)
}

function isMultiValueOperator(operator) {
  return MULTI_VALUE_OPERATORS.includes(operator)
}

/** 条件值展示（多值数组 → 逗号串文本；其余 String 化；空为 ''） */
function conditionValueText(condition) {
  const value = condition && condition.value
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.map((item) => String(item)).join(',')
  return String(value)
}

function conditionOf(item) {
  const value = valueOf(item)
  return value && typeof value === 'object' ? value : {}
}

function setConditionOperator(item, operator) {
  if (!operator) {
    setValue(item, null)
    return
  }
  const next = { operator }
  if (!isNoValueOperator(operator)) next.value = isMultiValueOperator(operator) ? [] : ''
  setValue(item, next)
}

function setConditionValue(item, text) {
  const current = conditionOf(item)
  const operator = current.operator
  if (!operator) return
  const next = { operator }
  if (isMultiValueOperator(operator)) {
    next.value = String(text)
      .split(',')
      .map((part) => part.trim())
      .filter((part) => part !== '')
  } else {
    next.value = text
  }
  setValue(item, next)
}

/** 明细表 condition 单元格（cascade options[].showCondition，R08） */
function setTableCellConditionOperator(item, row, column, operator) {
  if (!operator) {
    setTableCell(item, row, column, null)
    return
  }
  const next = { operator }
  if (!isNoValueOperator(operator)) next.value = isMultiValueOperator(operator) ? [] : ''
  setTableCell(item, row, column, next)
}

function setTableCellConditionValue(item, row, column, text) {
  const current = row[column.key] && typeof row[column.key] === 'object' ? row[column.key] : {}
  const operator = current.operator
  if (!operator) return
  const next = { operator }
  if (isMultiValueOperator(operator)) {
    next.value = String(text)
      .split(',')
      .map((part) => part.trim())
      .filter((part) => part !== '')
  } else {
    next.value = text
  }
  setTableCell(item, row, column, next)
}

/* ==================== 上下文选项（contextSelect：dataFields / detailNumericFields） ==================== */

function contextOptionsFor(item) {
  if (item.context === 'detailNumericFields') return props.detailNumericOptions
  return props.dataFieldOptions
}

/* ==================== table 明细表编辑器（选项 / 列定义 / 统计行共用） ==================== */

function tableRowsOf(item) {
  const value = valueOf(item)
  return Array.isArray(value) ? value : []
}

/** 明细表单元格列宽（condition 编辑器较宽，其余均分） */
function columnEditorWidth(column) {
  return column && column.editor === 'condition' ? 190 : 150
}

/** 新行默认值：按列编辑器给中性初值（select 取首项，condition 为 null=无条件） */
function buildEmptyRow(item) {
  const row = {}
  for (const column of item.columns || []) {
    if (column.editor === 'select') {
      row[column.key] = column.options && column.options.length > 0 ? column.options[0].value : null
    } else if (column.editor === 'condition') {
      row[column.key] = null
    } else if (column.editor === 'number') {
      row[column.key] = null
    } else {
      row[column.key] = ''
    }
  }
  return row
}

function setTableCell(item, row, column, value) {
  const rows = tableRowsOf(item).map((source) => ({ ...source }))
  const target = rows.find((candidate) => candidate === row)
  if (target) target[column.key] = value
  setValue(item, rows)
}

function addTableRow(item) {
  const rows = tableRowsOf(item).map((source) => ({ ...source }))
  rows.push(buildEmptyRow(item))
  setValue(item, rows)
}

function removeTableRow(item, index) {
  const rows = tableRowsOf(item).filter((_, rowIndex) => rowIndex !== index)
  setValue(item, rows)
}

/* ==================== gridColumns 编辑器（栅格列数与 span，target='children'） ==================== */

const gridColumns = computed(() => {
  const value = valueOf({ target: 'children' })
  return Array.isArray(value) ? value : []
})

const gridSpanSum = computed(() => gridColumns.value.reduce((sum, column) => sum + (Number.isFinite(Number(column.span)) ? Number(column.span) : 0), 0))

const gridColumnMessage = ref('')

/** 调整列数：减少时若被裁剪列仍含字段则拦截（字段经画布嵌套承载，不静默丢弃） */
function handleColumnCount(count) {
  gridColumnMessage.value = ''
  const columns = gridColumns.value
  const target = Number(count)
  if (!Number.isFinite(target) || target < 1) return
  if (target === columns.length) return
  if (target < columns.length) {
    const surplus = columns.slice(target)
    if (surplus.some((column) => Array.isArray(column.fields) && column.fields.length > 0)) {
      gridColumnMessage.value = '被裁剪列内仍有字段，请先移出字段后再减少列数'
      return
    }
  }
  const next = [...columns]
  if (target > columns.length) {
    for (let index = columns.length; index < target; index += 1) {
      next.push({ span: 12, fields: [] })
    }
  } else {
    next.length = target
  }
  emit('children-change', { path: props.entry.path, children: next })
}

function handleColumnSpan(columnIndex, span) {
  const columns = gridColumns.value.map((column, index) => (index === columnIndex ? { ...column, span } : column))
  gridColumnMessage.value = ''
  emit('children-change', { path: props.entry.path, children: columns })
}

/* ==================== 公式设计弹窗（F14 FormulaDesigner 契约复用） ==================== */

const formulaDesignerVisible = ref(false)

/** confirm 载荷 = 最终表达式文本 → 回写 props.expression（与画布 FormulaWidget 同通道） */
function handleFormulaConfirm(expression) {
  if (!props.entry) return
  emit('prop-change', { path: props.entry.path, key: 'expression', value: expression })
  formulaDesignerVisible.value = false
}
</script>

<style scoped>
.property-panel {
  height: 100%;
  overflow-y: auto;
  padding: 10px 12px;
}

.panel-caption {
  font-size: 13px;
  color: var(--el-text-color-primary);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 8px;
}

.panel-caption-sub {
  margin-left: 6px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.panel-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.props-divider {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  text-align: center;
  margin: 4px 0 10px;
}

.props-tip {
  font-size: 11px;
  line-height: 1.5;
  color: var(--el-text-color-placeholder);
  width: 100%;
  margin-top: 2px;
}

.props-error {
  font-size: 11px;
  line-height: 1.5;
  color: var(--el-color-danger);
  width: 100%;
  margin-top: 2px;
}

.panel-select {
  width: 100%;
}

.condition-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.condition-editor .panel-select {
  width: 100%;
}

.props-none {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* gridColumns 编辑器 */
.grid-columns-editor {
  width: 100%;
}

.grid-columns-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.grid-columns-label {
  flex: none;
  width: 44px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.grid-span-sum {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.grid-span-sum.is-over {
  color: var(--el-color-danger);
}

.grid-column-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.grid-column-fields {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

/* table 明细表编辑器 */
.table-editor {
  width: 100%;
}

.table-editor-toolbar {
  margin-top: 6px;
}

/* 未选中画布级摘要 */
.panel-empty {
  font-size: 13px;
}

.summary-item {
  display: flex;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.summary-label {
  flex: none;
  width: 72px;
  color: var(--el-text-color-secondary);
}

.summary-value {
  color: var(--el-text-color-primary);
}

.summary-hint {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-placeholder);
}
</style>

<template>
  <!--
    表单设计器 · 右侧规则面板（F15-FE-008，UI 分册 §4.6）。
    以当前选中字段为触发源（triggerField）的规则列表——规则宿主存储于字段对象 rules[]
    （随版本内嵌，ADR-06）；四类规则统一 DSL 可视化配置：
    - 显隐 show/hide、联动 setValue/clearValue、动态校验 setRequired/setValidate；
    - 响应函数（占位）不提供配置入口（R02 一期不落地 JS 执行引擎，面板无代码编辑区）。
    规则编辑弹窗：事件六类下拉（onFocus/onBlur 一期不开放）、触发字段只读、
    「无条件」开关 + operator 十项（value 控件随 operator：标量/多值/无值）、
    动作明细表六类动作（目标字段下拉、值列随动作类型、setValidate 子表单）；
    onSubmit/onValidate 下 setValue/clearValue 禁用提示（§3.4 提交段载荷约束）；
    规则数达 MAX_RULE_COUNT 禁用添加；面板底部固定循环防护上限提示文案。
    设计器内部组件：仅 FormDesigner 消费，不单独对外导出。
  -->
  <div class="rule-panel">
    <template v-if="entry">
      <div class="panel-caption">
        触发字段：<b>{{ entry.label || entry.field }}</b>
        <span class="panel-caption-sub">{{ entry.field }} · {{ containerScopeLabel }}</span>
      </div>
      <div class="rule-toolbar">
        <el-tooltip :disabled="!addDisabledReason" :content="addDisabledReason" placement="top">
          <span>
            <el-button type="primary" size="small" :disabled="!!addDisabledReason" @click="openCreate">添加规则</el-button>
          </span>
        </el-tooltip>
        <span v-if="addDisabledReason" class="rule-limit-reason">{{ addDisabledReason }}</span>
      </div>
      <div v-if="rules.length === 0" class="rule-empty">该字段暂无规则——显隐 / 联动 / 动态校验逻辑经规则 DSL 配置，规则随触发字段存放于字段对象 rules[]</div>
      <div v-else class="rule-list">
        <div v-for="(rule, index) in rules" :key="rule.ruleId || index" class="rule-item">
          <div class="rule-item-title">
            <el-tag size="small" type="info">{{ rule.ruleId }}</el-tag>
            <span class="rule-item-summary">{{ summarizeRule(rule, fieldLabelMap) }}</span>
          </div>
          <div class="rule-item-actions">
            <el-button link type="primary" size="small" @click="openEdit(rule)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(rule)">删除</el-button>
          </div>
        </div>
      </div>
    </template>
    <div v-else class="rule-empty">请先在画布中选中字段——规则面板展示以选中字段为触发源（triggerField）的规则列表</div>

    <!-- 面板底部固定循环防护上限提示（§4.6 定稿文案） -->
    <div class="rule-limit-tip">{{ ruleLimitTip }}</div>

    <!-- 规则编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isNew ? '添加规则' : '编辑规则'" width="720px" destroy-on-close @closed="resetEditing">
      <el-form label-width="92px" size="small" @submit.prevent>
        <el-form-item label="触发事件">
          <el-select v-model="editing.event" class="rule-select" @update:model-value="handleEventChange">
            <el-option v-for="option in ruleEventOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <div class="props-tip">onFocus / onBlur 一期不开放规则配置（仅事件透传，防规则风暴）</div>
        </el-form-item>
        <el-form-item label="触发字段">
          <el-input :model-value="triggerFieldText" readonly />
        </el-form-item>
        <el-form-item label="触发条件">
          <div class="condition-block">
            <div class="condition-switch">
              <el-switch :model-value="editing.condition === null" @update:model-value="(value) => setUnconditional(value)" />
              <span class="condition-switch-label">无条件（事件即触发，不校验字段值）</span>
            </div>
            <template v-if="editing.condition !== null">
              <el-select v-model="editing.condition.operator" class="rule-select" @update:model-value="handleOperatorChange">
                <el-option v-for="option in ruleOperatorOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <!-- value 控件随 operator：标量输入 / 多值编辑 / 无值控件（empty/notEmpty） -->
              <el-input-number
                v-if="isScalarOperator && triggerDataType === 'NUMBER'"
                v-model="editing.condition.value"
                class="condition-value"
                controls-position="right"
                placeholder="比较值"
              />
              <el-input
                v-else-if="isScalarOperator"
                v-model="editing.condition.value"
                class="condition-value"
                :placeholder="triggerDataType === 'DATE' ? '日期字符串（YYYY-MM-DD）' : '比较值'"
              />
              <el-select
                v-else-if="isMultiValueOperator"
                :model-value="conditionTags"
                class="condition-value"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="输入后回车添加候选值（标签式多值）"
                @update:model-value="handleConditionTags"
              />
              <div v-else class="props-tip">「{{ operatorLabel }}」无比较值</div>
            </template>
          </div>
        </el-form-item>
        <el-form-item label="动作">
          <div class="actions-block">
            <el-alert
              v-if="valueForbidden"
              class="actions-alert"
              type="warning"
              :closable="false"
              show-icon
              :title="valueForbiddenTip"
            />
            <el-table :data="editing.actions" size="small" border empty-text="暂无动作，点击下方「添加动作」新增">
              <el-table-column label="动作类型" width="150">
                <template #default="{ row }">
                  <el-select v-model="row.type" size="small" @update:model-value="() => handleActionTypeChange(row)">
                    <el-option v-for="option in actionTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="目标字段" width="180">
                <template #default="{ row }">
                  <el-select v-model="row.target" size="small" filterable placeholder="选择目标字段">
                    <el-option v-for="option in actionTargetOptions" :key="option.code" :label="`${option.label}（${option.code}）`" :value="option.code" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="值" min-width="220">
                <template #default="{ row }">
                  <!-- 值列随动作类型：show/hide/clearValue 无值；setRequired 开关；setValue 文本 + 字段码引用带入；setValidate 子表单 -->
                  <span v-if="row.type === 'show' || row.type === 'hide' || row.type === 'clearValue'" class="action-none">—</span>
                  <el-switch v-else-if="row.type === 'setRequired'" :model-value="row.value === true" @update:model-value="(value) => (row.value = value)" />
                  <div v-else-if="row.type === 'setValue'" class="action-value-set">
                    <el-input v-model="row.value" size="small" placeholder="字面量或 ${字段码} 引用" />
                    <el-select
                      class="action-ref-select"
                      :model-value="''"
                      size="small"
                      placeholder="插入引用"
                      @update:model-value="(code) => (row.value = `\${${code}}`)"
                    >
                      <el-option v-for="option in actionTargetOptions" :key="option.code" :label="option.code" :value="option.code" />
                    </el-select>
                  </div>
                  <div v-else-if="row.type === 'setValidate'" class="action-validate">
                    <div class="action-validate-row">
                      <el-input-number v-model="row.validate.min" size="small" controls-position="right" placeholder="min" />
                      <el-input-number v-model="row.validate.max" size="small" controls-position="right" placeholder="max" />
                    </div>
                    <el-input v-model="row.validate.pattern" size="small" placeholder="正则（全串匹配，可空）" />
                    <el-input v-model="row.validate.message" size="small" placeholder="违规提示文案（可空）" />
                  </div>
                  <span v-else class="action-none">—</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="64" fixed="right">
                <template #default="{ $index }">
                  <el-button link type="danger" size="small" @click="removeAction($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="actions-toolbar">
              <el-button size="small" :disabled="actionTypeOptions.length === 0" @click="addAction">添加动作</el-button>
              <span class="props-tip">动作按序执行；动作类型六项全集（script 类动作 R02 一期不开放）</span>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  MAX_RULE_COUNT,
  RULE_ACTION_LABELS,
  RULE_ACTION_OPTIONS,
  RULE_EVENT_OPTIONS,
  RULE_LIMIT_TIP,
  RULE_OPERATOR_LABELS,
  RULE_OPERATOR_OPTIONS,
  MULTI_VALUE_OPERATORS,
  NO_VALUE_OPERATORS,
  SCALAR_OPERATORS,
  VALUE_FORBIDDEN_EVENT_VALUES,
  VALUE_FORBIDDEN_TIP,
  generateRuleId,
  summarizeRule,
} from './designerSupport.js'

/**
 * 规则面板 props：
 * - entry：选中字段索引条目（规则宿主即触发源字段对象，null = 未选中引导文案）；
 * - rules：选中字段 rules[]（宿主从索引条目 node.rules 传入，只读展示源）；
 * - actionTargetOptions：动作目标字段选项（§4.6：明细表触发源的动作目标限行内字段码，
 *   其余为主表层级数据类字段——宿主按选中条目上下文装配）；
 * - fieldLabelMap：全表字段码 → 名称映射（规则摘要文案消费）；
 * - totalRuleCount：全表规则总数（MAX_RULE_COUNT 上限禁用添加）；
 * - existingRuleIds：全表既有规则 ID（新增 ruleId 去重）；
 * - triggerDataType：触发字段解析 dataType（条件值控件选型：NUMBER 数字 / 其余文本）。
 */
const props = defineProps({
  entry: { type: Object, default: null },
  rules: { type: Array, default: () => [] },
  actionTargetOptions: { type: Array, default: () => [] },
  fieldLabelMap: { type: Object, default: () => ({}) },
  totalRuleCount: { type: Number, default: 0 },
  existingRuleIds: { type: Array, default: () => [] },
  triggerDataType: { type: String, default: '' },
})

const emit = defineEmits(['rule-add', 'rule-update', 'rule-delete'])

const ruleEventOptions = RULE_EVENT_OPTIONS
const ruleOperatorOptions = RULE_OPERATOR_OPTIONS
const ruleLimitTip = RULE_LIMIT_TIP
const valueForbiddenTip = VALUE_FORBIDDEN_TIP

/* ==================== 规则列表 ==================== */

const containerScopeLabel = computed(() => {
  if (!props.entry) return ''
  if (props.entry.containerType === 'detailTable') return '明细行内规则（只作用当前行）'
  if (props.entry.type === 'detailTable') return '明细表宿主规则（onAdd/onDelete，动作作用于全部行）'
  return '主表规则'
})

/** 达 MAX_RULE_COUNT 禁用【添加规则】（§4.6） */
const addDisabledReason = computed(() => {
  if (!props.entry) return '请先选中触发字段'
  if (props.totalRuleCount >= MAX_RULE_COUNT) {
    return `规则总数已达上限 ${MAX_RULE_COUNT}（MAX_RULE_COUNT），无法继续添加`
  }
  return ''
})

/* ==================== 规则编辑弹窗 ==================== */

const dialogVisible = ref(false)
const isNew = ref(false)
const editingIndex = ref(-1)

/** 编辑中的规则深拷贝（actions 行内 v-model 直改，确认时统一校验上抛） */
const editing = reactive({ rule: null })

const valueForbidden = computed(() => editing.rule && VALUE_FORBIDDEN_EVENT_VALUES.includes(editing.rule.event))

/** 动作类型选项：提交段事件下 setValue/clearValue 禁用（选项剔除 + 告警提示，§3.4） */
const actionTypeOptions = computed(() =>
  RULE_ACTION_OPTIONS.filter((option) => !(valueForbidden.value && (option.value === 'setValue' || option.value === 'clearValue'))),
)

const isScalarOperator = computed(() => editing.rule && editing.rule.condition !== null && SCALAR_OPERATORS.includes(editing.rule.condition.operator))
const isMultiValueOperator = computed(() => editing.rule && editing.rule.condition !== null && MULTI_VALUE_OPERATORS.includes(editing.rule.condition.operator))
const operatorLabel = computed(() => {
  const operator = editing.rule && editing.rule.condition ? editing.rule.condition.operator : ''
  return RULE_OPERATOR_LABELS[operator] || operator
})

/** in/notIn 标签式多值输入（NUMBER 触发字段把数值标签转为数值，保持 DSL 标量比对语义） */
const conditionTags = computed(() => {
  const value = editing.rule && editing.rule.condition ? editing.rule.condition.value : null
  return Array.isArray(value) ? value.map((item) => String(item)) : []
})

function handleConditionTags(tags) {
  if (!editing.rule || !editing.rule.condition) return
  editing.rule.condition.value = tags.map((tag) => {
    if (props.triggerDataType === 'NUMBER' && tag !== '' && Number.isFinite(Number(tag))) return Number(tag)
    return tag
  })
}

const triggerFieldText = computed(() => {
  if (!props.entry) return ''
  const scope = props.entry.containerType === 'detailTable' ? '（明细行内）' : ''
  return `${props.entry.label || props.entry.field} · ${props.entry.field}${scope}`
})

/** 打开新建：默认事件 onChange、无条件、空动作集 */
function openCreate() {
  if (addDisabledReason.value) return
  isNew.value = true
  editingIndex.value = -1
  editing.rule = {
    ruleId: generateRuleId(props.existingRuleIds),
    event: 'onChange',
    triggerField: props.entry ? props.entry.field : '',
    condition: null,
    actions: [],
  }
  dialogVisible.value = true
}

/** 打开编辑：深拷贝规则（setValidate 补齐 validate 子对象四键，行内编辑不触碰原对象） */
function openEdit(rule) {
  isNew.value = false
  editingIndex.value = props.rules.indexOf(rule)
  const cloned = JSON.parse(JSON.stringify(rule || {}))
  cloned.actions = Array.isArray(cloned.actions) ? cloned.actions : []
  for (const action of cloned.actions) {
    if (action.type === 'setValidate' && !(action.validate && typeof action.validate === 'object')) {
      action.validate = { min: null, max: null, pattern: '', message: '' }
    }
  }
  editing.rule = cloned
  dialogVisible.value = true
}

function resetEditing() {
  editing.rule = null
}

/** 事件切换：提交段事件与值类动作互斥提示（既有值类动作保留展示、引擎跳过并告警） */
function handleEventChange() {
  // 提示由 actions-block 内 el-alert 响应式承载，无需额外处理
}

/** operator 切换：value 控件随 operator 重建（标量 / 多值数组 / 无值删除 value 键） */
function handleOperatorChange(operator) {
  if (!editing.rule.condition) return
  if (NO_VALUE_OPERATORS.includes(operator)) {
    delete editing.rule.condition.value
  } else if (MULTI_VALUE_OPERATORS.includes(operator)) {
    editing.rule.condition.value = Array.isArray(editing.rule.condition.value) ? editing.rule.condition.value : []
  } else if (typeof editing.rule.condition.value === 'object' || editing.rule.condition.value === undefined) {
    editing.rule.condition.value = props.triggerDataType === 'NUMBER' ? null : ''
  }
}

/** 新增动作：默认 show + 首个可选目标 */
function addAction() {
  if (!editing.rule) return
  editing.rule.actions.push({
    type: 'show',
    target: props.actionTargetOptions.length > 0 ? props.actionTargetOptions[0].code : '',
    value: null,
    validate: null,
  })
}

function removeAction(index) {
  if (!editing.rule) return
  editing.rule.actions.splice(index, 1)
}

/** 动作类型切换：重建该行值载荷形态（value / validate） */
function handleActionTypeChange(row) {
  if (row.type === 'setValidate') {
    row.validate = row.validate && typeof row.validate === 'object' ? row.validate : { min: null, max: null, pattern: '', message: '' }
    row.value = null
  } else if (row.type === 'setValue' || row.type === 'setRequired') {
    row.value = row.type === 'setRequired' ? false : ''
    row.validate = null
  } else {
    row.value = null
    row.validate = null
  }
}

/** 确认：至少一个有目标字段的动作；normalize 后上抛宿主落 Schema */
function handleConfirm() {
  if (!editing.rule || !props.entry) return
  const rule = editing.rule
  const actions = (rule.actions || []).filter((action) => action && action.type && action.target)
  if (actions.length === 0) {
    ElMessage.warning('请至少配置一个动作（动作类型 + 目标字段）')
    return
  }
  const normalized = {
    ruleId: rule.ruleId,
    event: rule.event,
    triggerField: props.entry.field,
    condition: rule.condition && rule.condition.operator ? { operator: rule.condition.operator, ...(rule.condition.value !== undefined ? { value: rule.condition.value } : {}) } : null,
    actions,
  }
  if (isNew.value) {
    emit('rule-add', { path: props.entry.path, rule: normalized })
  } else {
    emit('rule-update', { path: props.entry.path, ruleId: rule.ruleId, rule: normalized, index: editingIndex.value })
  }
  dialogVisible.value = false
}

/** 删除规则：二次确认（规则宿主字段删除时 rules[] 随级联删除，§4.6） */
function handleDelete(rule) {
  ElMessage.closeAll()
  ElMessageBox.confirm(`确定删除规则 ${rule.ruleId || ''}？`, '删除规则', { type: 'warning' })
    .then(() => {
      if (props.entry) emit('rule-delete', { path: props.entry.path, ruleId: rule.ruleId })
    })
    .catch(() => {})
}
</script>

<style scoped>
.rule-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
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

.rule-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.rule-limit-reason {
  font-size: 11px;
  color: var(--el-color-warning);
}

.rule-empty {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-placeholder);
  padding: 12px 0;
}

.rule-list {
  flex: 1;
}

.rule-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
  margin-bottom: 8px;
}

.rule-item-title {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.rule-item-summary {
  flex: 1;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.rule-item-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

.rule-limit-tip {
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color-lighter);
  font-size: 11px;
  line-height: 1.6;
  color: var(--el-text-color-placeholder);
}

.rule-select {
  width: 100%;
}

.props-tip {
  font-size: 11px;
  line-height: 1.5;
  color: var(--el-text-color-placeholder);
  width: 100%;
  margin-top: 2px;
}

/* 条件编辑 */
.condition-block {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.condition-switch {
  display: flex;
  align-items: center;
  gap: 8px;
}

.condition-switch-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.condition-value {
  width: 100%;
}

/* 动作明细表 */
.actions-block {
  width: 100%;
}

.actions-alert {
  margin-bottom: 8px;
}

.actions-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.action-none {
  color: var(--el-text-color-placeholder);
}

.action-value-set {
  display: flex;
  gap: 6px;
  width: 100%;
}

.action-ref-select {
  flex: none;
  width: 108px;
}

.action-validate {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}

.action-validate-row {
  display: flex;
  gap: 6px;
}
</style>

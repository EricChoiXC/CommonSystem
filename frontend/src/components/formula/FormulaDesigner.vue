<template>
  <el-dialog
    :model-value="visible"
    title="公式设计"
    width="1000px"
    :close-on-click-modal="false"
    @update:model-value="handleVisibleChange"
    @open="handleOpen"
  >
    <div class="designer-body">
      <div class="designer-main">
        <!-- 左栏：四组节点树（变量 / 业务模型 / 函数分类 / 命名公式），双击或【插入】写入编辑区 -->
        <div class="designer-left">
          <el-tree
            :data="treeData"
            :props="treeProps"
            node-key="key"
            :default-expanded-keys="DEFAULT_EXPANDED_KEYS"
            :expand-on-click-node="false"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <span
                  class="tree-node-label"
                  :class="'is-' + data.type"
                  :title="data.label"
                  @dblclick="handleInsert(data)"
                >
                  {{ data.label }}
                </span>
                <span v-if="data.desc" class="tree-node-desc">{{ data.desc }}</span>
                <el-button
                  v-if="data.insert"
                  class="tree-node-insert"
                  link
                  type="primary"
                  size="small"
                  @click="handleInsert(data)"
                >
                  插入
                </el-button>
              </div>
            </template>
          </el-tree>
        </div>

        <!-- 右栏：编辑区（FormulaCodeEditor 底座 + 校验状态区）与函数面板 -->
        <div class="designer-right">
          <div class="designer-editor">
            <FormulaCodeEditor
              ref="editorRef"
              v-model="editorText"
              class="designer-cm"
              :variable-names="completionVariableNames"
              :function-names="completionFunctionNames"
              placeholder="编写公式，如 ROUND(${amount} * 1.13, 2)；可双击左侧节点插入"
            />
            <div class="validate-status">
              <template v-if="!hasExpression">
                <span class="validate-tip">输入公式内容后自动校验（权威校验在后端 /validate）</span>
              </template>
              <template v-else>
                <div class="validate-head">
                  <span v-if="validating" class="validate-running">校验中…</span>
                  <template v-else-if="validateResult">
                    <span :class="validateResult.syntax ? 'validate-ok' : 'validate-bad'">
                      {{ validateResult.syntax ? '语法校验通过' : '语法校验未通过' }}
                    </span>
                    <span v-if="validateResult.returnType" class="validate-return">
                      返回类型：{{ validateResult.returnType }}
                    </span>
                  </template>
                  <span v-else class="validate-tip">待校验</span>
                </div>
                <div v-if="validateErrors.length" class="validate-errors">
                  <div v-for="(err, index) in validateErrors" :key="index" class="validate-error">
                    {{ err.code }}: {{ err.message }}
                  </div>
                </div>
                <div v-if="dependencyList.length" class="validate-deps">
                  <span class="validate-deps-label">变量依赖</span>
                  <el-tag v-for="dep in dependencyList" :key="dep" size="small" class="validate-dep-tag">
                    {{ dep }}
                  </el-tag>
                </div>
              </template>
            </div>
          </div>

          <div class="designer-functions">
            <div class="panel-head">
              <span class="panel-title">函数面板</span>
              <span v-if="functionFiltered" class="panel-tip">已按传入分类过滤</span>
            </div>
            <div v-if="functionGroups.length" class="fn-scroll">
              <el-collapse class="fn-collapse">
                <el-collapse-item v-for="group in functionGroups" :key="group.key" :name="group.key">
                  <template #title>
                    <span class="fn-group-title">{{ group.label }}</span>
                    <span class="fn-group-count">{{ group.children.length }}</span>
                  </template>
                  <div v-for="fn in group.children" :key="fn.functionCode" class="fn-row">
                    <span class="fn-code">{{ fn.functionCode }}</span>
                    <span class="fn-name">{{ fn.functionName }}</span>
                    <span class="fn-sign">{{ paramSignature(fn) }}</span>
                    <span class="fn-desc" :title="fn.description">{{ fn.description }}</span>
                    <el-button link type="primary" size="small" @click="insertFunction(fn)">插入</el-button>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
            <div v-else class="panel-empty">{{ functionsError || (loaded ? '暂无函数' : '清单加载中…') }}</div>
          </div>
        </div>
      </div>

      <!-- 模拟区：横跨全宽，变量赋值行（控件随 type）+ 试算 + 结果/错误展示 -->
      <div class="designer-simulate">
        <div class="panel-head">
          <span class="panel-title">模拟试算</span>
          <span class="panel-tip">系统变量（SYS_CURRENT_*）由服务端注入，不生成赋值行</span>
          <el-button
            type="primary"
            size="small"
            class="simulate-btn"
            :disabled="!hasExpression"
            :loading="simulating"
            @click="handleSimulate"
          >
            试算
          </el-button>
        </div>
        <div v-if="assignRows.length" class="assign-rows">
          <div v-for="row in assignRows" :key="row.code" class="assign-row" :class="{ 'has-error': row.error }">
            <span class="assign-code" :title="row.code">{{ row.code }}</span>
            <el-select v-model="row.type" class="assign-type" size="small" @change="handleRowTypeChange(row)">
              <el-option v-for="type in VAR_TYPES" :key="type" :label="type" :value="type" />
            </el-select>
            <el-input-number
              v-if="row.type === 'NUMBER'"
              v-model="row.value"
              class="assign-value"
              :controls="false"
              placeholder="数值"
            />
            <el-switch v-else-if="row.type === 'BOOLEAN'" v-model="row.value" />
            <el-date-picker
              v-else-if="row.type === 'DATE'"
              v-model="row.value"
              type="date"
              value-format="YYYY-MM-DD"
              class="assign-value"
              placeholder="日期"
            />
            <el-input
              v-else-if="row.type === 'ARRAY'"
              v-model="row.value"
              class="assign-value"
              placeholder='JSON 数组文本，如 ["a", "b"]'
            />
            <el-input v-else v-model="row.value" class="assign-value" placeholder="文本" />
            <span v-if="row.error" class="assign-error">{{ row.error }}</span>
          </div>
        </div>
        <div v-else class="panel-empty">
          暂无待赋值变量：表达式引用变量或外部传入 availableVars 后自动生成赋值行
        </div>
        <div class="simulate-result">
          <template v-if="simulateResult">
            <div v-if="simulateResult.success" class="simulate-ok">
              <span class="simulate-ok-label">试算成功</span>
              <span class="simulate-value">{{ formatResultValue(simulateResult.result) }}</span>
              <el-tag v-if="simulateResult.resultType" size="small">{{ simulateResult.resultType }}</el-tag>
            </div>
            <div v-else class="simulate-errors">
              <div v-for="(err, index) in simulateResult.errors" :key="index" class="simulate-error">
                {{ err.code }}: {{ err.message }}
              </div>
            </div>
          </template>
          <span v-else class="simulate-tip">为变量赋值后点击【试算】查看结果；失败不阻断编辑，可调整后重试</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确认回写</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import FormulaCodeEditor from './FormulaCodeEditor.vue'
import { getFormulaFunctions, getFormulaOptions, simulateFormula, validateFormula } from '@/api/formula'
import { getDatasourceTargets } from '@/api/datasource'

/**
 * 公式设计弹窗组件（FormulaDesigner，公式定义器 v1.4 任务 F14-FE-003）
 *
 * 需求「a-model 弹窗」在本仓库 Element Plus 体系落地为 el-dialog 公式设计弹窗
 * （docs/系统模块/系统模块UI设计文档.md 4.11.7 为权威规格）：左树（四组节点）+
 * 右上编辑区（FormulaCodeEditor 底座 + /validate 校验联动）+ 右中函数面板 +
 * 底部模拟区（变量赋值 + /simulate 试算）。
 *
 * 契约：
 * - props.visible：弹窗显隐（关闭时 emit update:visible）；
 * - props.availableVars：外部追加变量声明，元素 { code, type?, label? }（type ∈
 *   NUMBER/STRING/BOOLEAN/DATE/ARRAY），并入左树变量组、validate 的 vars 入参
 *   与模拟区赋值行；
 * - props.functionCategories：函数分类过滤（如 ['MATH','LOGIC']），仅作用于函数
 *   分类组与函数面板，缺省 = 全部九类；
 * - props.expectedReturnType：期望返回类型，作为 validate / simulate 的
 *   expectedReturnType 入参；
 * - props.expression：公式文本（v-model 语义）；
 * - emits：update:visible、update:expression（编辑文本回写）、confirm（载荷为
 *   最终表达式文本；与 update:expression 同步发生，随后关闭弹窗）。
 *
 * 边界（方案 A1）：编辑器与校验状态只做输入辅助，表达式合法性权威裁决在后端
 * /validate；校验失败只展示、不阻断继续编辑；试算失败（success=false）与接口
 * 异常同样仅提示、不阻断弹窗操作。前端不做语法解析裁决——引用码粗提取仅用于
 * 装配 vars 入参（见 buildValidateVars 注释）。
 *
 * 数据加载：弹窗首次打开时并行拉取 options / functions / targets 三接口
 * （loaded 置位后不重复拉取），单个接口失败仅 ElMessage 警告、对应分组展示
 * 空态，不阻断弹窗打开；每次打开以 props.expression 重新同步编辑文本。
 */
const props = defineProps({
  /** 弹窗显隐控制 */
  visible: { type: Boolean, default: false },
  /** 外部追加变量声明：元素 { code, type?, label? }，type 缺省按 STRING 处理 */
  availableVars: { type: Array, default: () => [] },
  /** 函数分类过滤（如 ['MATH','LOGIC']），空/缺省 = 全部九类 */
  functionCategories: { type: Array, default: () => [] },
  /** 期望返回类型（NUMBER/STRING/BOOLEAN/DATE/ARRAY），空 = 不传跳过比对 */
  expectedReturnType: { type: String, default: '' },
  /** 公式文本（v-model 语义，${} 形态原样存取） */
  expression: { type: String, default: '' }
})

const emit = defineEmits(['update:visible', 'update:expression', 'confirm'])

/* ==================== 常量 ==================== */

/** 变量类型枚举面（对齐 SysFormulaReturnType，validate vars 声明与模拟赋值控件共用） */
const VAR_TYPES = ['NUMBER', 'STRING', 'BOOLEAN', 'DATE', 'ARRAY']

/**
 * 固定系统变量（服务端注入语义，出自 FormulaContext 会话上下文）：
 * 类型按服务端取值形态声明（用户/部门 ID 为字符串、角色编码集合为逗号分隔字符串）；
 * 模拟区不为其生成赋值行——后端不信任前端传入的 SYS_CURRENT_* 覆盖
 */
const SYSTEM_VARIABLES = [
  { code: 'SYS_CURRENT_USER_ID', type: 'STRING', label: '当前用户ID' },
  { code: 'SYS_CURRENT_DEPT_ID', type: 'STRING', label: '当前部门ID' },
  { code: 'SYS_CURRENT_ROLE', type: 'STRING', label: '当前角色编码集合' }
]

/** 函数分类展示顺序与中文组名（对齐 SysFormulaFunctionCategory 九类） */
const CATEGORY_ORDER = ['MATH', 'LOGIC', 'COMPARE', 'STRING', 'DATE', 'COLLECTION', 'CONVERT', 'SYSTEM', 'DATA']
const CATEGORY_LABELS = {
  MATH: '数学',
  LOGIC: '逻辑',
  COMPARE: '比较',
  STRING: '字符串',
  DATE: '日期',
  COLLECTION: '集合',
  CONVERT: '转换',
  SYSTEM: '系统',
  DATA: '取数'
}

/** 左树默认展开的一级分组（组内节点收起，由用户按需展开） */
const DEFAULT_EXPANDED_KEYS = ['group-var', 'group-model', 'group-func', 'group-formula']

/** 编辑防抖毫秒数（输入停顿后触发 /validate） */
const VALIDATE_DEBOUNCE = 500

/** el-tree 字段映射（节点数据自带 children / label） */
const treeProps = { children: 'children', label: 'label' }

/* ==================== 状态 ==================== */

const editorRef = ref(null)
/** 编辑区文本（打开时以 props.expression 同步；经 FormulaCodeEditor v-model 双向绑定） */
const editorText = ref('')
const validating = ref(false)
/** /validate 响应 { syntax, returnType, dependencies, errors } */
const validateResult = ref(null)
const simulating = ref(false)
/** /simulate 响应 { success, result, resultType } 或 { success: false, errors } */
const simulateResult = ref(null)
/** 模拟赋值行：[{ code, type, value, error }]，value 形态随 type（ARRAY 为 JSON 文本） */
const assignRows = ref([])

/** 清单加载态：loaded 置位后不重复拉取；三接口一次并行加载，成败分开记录 */
const loaded = ref(false)
const loadingData = ref(false)
const targets = ref([])
const functions = ref([])
const formulaOptions = ref([])
const targetsError = ref('')
const functionsError = ref('')
const optionsError = ref('')

let validateTimer = null
/** /validate 请求序号：仅应用最新一次编辑对应的响应，丢弃过期结果（防竞态错位） */
let validateSeq = 0

/* ==================== 变量声明与类型工具 ==================== */

/** 变量类型规整：大写 + 枚举白名单，非法或缺省回退 STRING */
function normalizeType(raw) {
  const type = typeof raw === 'string' ? raw.toUpperCase() : ''
  return VAR_TYPES.includes(type) ? type : 'STRING'
}

/** 系统注入变量判定：SYS_CURRENT_ 前缀为服务端注入语义，不生成模拟赋值行 */
function isSystemInjected(code) {
  return typeof code === 'string' && code.startsWith('SYS_CURRENT_')
}

/** props.availableVars 规整：元素 { code, type, label }，剔除无 code 项 */
const normalizedAvailableVars = computed(() =>
  (props.availableVars || [])
    .map((item) => {
      if (typeof item === 'string') {
        return { code: item, type: 'STRING', label: '' }
      }
      if (!item || !item.code) {
        return null
      }
      return { code: item.code, type: normalizeType(item.type), label: item.label || '' }
    })
    .filter(Boolean)
)

/**
 * 客户端粗提取表达式引用的根变量码（仅用于装配 /validate 的 vars 入参，
 * 不做任何合法性裁决）：与后端 ${} 解析同口径取根码——${code} 与
 * ${code.field}（路径投影）均取 code；字面量内 ${} 不甄别（粗提取可能
 * 多收，多收码不在已知声明内时只会落入宽松模式，无副作用）
 */
function extractReferenceCodes(expression) {
  const codes = []
  const pattern = /\$\{([a-zA-Z_][a-zA-Z0-9_]*)(?:\.[a-zA-Z_][a-zA-Z0-9_]*)?\}/g
  let match
  while ((match = pattern.exec(expression || ''))) {
    if (!codes.includes(match[1])) {
      codes.push(match[1])
    }
  }
  return codes
}

/**
 * 装配 /validate 的 vars 入参（严格 / 宽松模式裁决）。
 * 已知变量声明 = 系统变量 + props.availableVars（元素 { code, type }）。
 * 后端严格模式做「引用 ⇄ 声明」双向核对：若不加甄别地传入全部已知声明，
 * 表达式未引用到的声明（如 3 个系统变量未全部使用、availableVars 只用到
 * 其一）会被误报「声明的变量未被表达式引用」，编辑过程中错误面板将长期红显；
 * 故此处仅当表达式当前引用的根变量码全部落在已知声明内时，才按引用集合装配
 * vars（声明恰好被引用，双向核对天然通过，严格模式只承担类型声明与期望返回
 * 类型比对）；引用了未知变量（模型能力 / 命名公式码等运行期变量）或无任何
 * 引用时返回 null = 不传 vars 字段 = 宽松模式，对齐规格「无任何声明时不传
 * vars 字段 = 宽松模式」的兜底语义。
 */
function buildValidateVars(expression) {
  const known = new Map()
  SYSTEM_VARIABLES.forEach((item) => known.set(item.code, item.type))
  normalizedAvailableVars.value.forEach((item) => known.set(item.code, item.type))
  if (!known.size) {
    return null
  }
  const references = extractReferenceCodes(expression)
  if (!references.length) {
    return null
  }
  for (const code of references) {
    if (!known.has(code)) {
      return null
    }
  }
  return references.map((code) => ({ code, type: known.get(code) }))
}

/* ==================== 左树（四组节点，前端组装） ==================== */

/** 分组空态节点（加载失败 / 暂无数据 / 加载中提示，不可插入） */
function emptyNode(group, text) {
  return [{ key: 'hint:' + group + ':' + text, label: text, type: 'hint' }]
}

/** 变量组：固定系统变量 + availableVars 追加（label 优先取其 label，否则 code）；插入 ${code} */
const variableNodes = computed(() => {
  const nodes = SYSTEM_VARIABLES.map((item) => ({
    key: 'var:' + item.code,
    label: item.label,
    desc: item.code,
    type: 'variable',
    insert: '${' + item.code + '}'
  }))
  normalizedAvailableVars.value.forEach((item) => {
    // 与系统变量同码的声明不重复出节点（系统变量为准）
    if (SYSTEM_VARIABLES.some((system) => system.code === item.code)) {
      return
    }
    nodes.push({
      key: 'var:' + item.code,
      label: item.label || item.code,
      desc: item.code,
      type: 'variable',
      insert: '${' + item.code + '}'
    })
  })
  return nodes
})

/**
 * 业务模型组：数据源 targets 聚合的「能力 → 字段」两级节点（方案 A6，
 * 公式模块不另建字段台账）；能力节点插入 ${code}，字段节点插入
 * ${能力code.字段key}（路径投影形态，与 ${list.field} 同源）
 */
const modelNodes = computed(() => {
  if (targetsError.value) {
    return emptyNode('model', targetsError.value)
  }
  if (!targets.value.length) {
    return emptyNode('model', loaded.value ? '暂无业务模型' : '清单加载中…')
  }
  return targets.value.map((capability) => ({
    key: 'cap:' + capability.code,
    label: capability.name || capability.code,
    desc: capability.code,
    type: 'capability',
    insert: '${' + capability.code + '}',
    children: (capability.resultFields || []).map((field) => ({
      key: 'field:' + capability.code + '.' + field.key,
      label: field.label || field.key,
      desc: field.type ? field.key + '（' + field.type + '）' : field.key,
      type: 'field',
      insert: '${' + capability.code + '.' + field.key + '}'
    }))
  }))
})

/** 按注册元数据生成函数占位参数模板：无参为 name()，有参为 name(参数名1, 参数名2) */
function buildFunctionTemplate(fn) {
  const names = (fn.parameters || []).map((item) => item.name || 'arg')
  return fn.functionCode + '(' + names.join(', ') + ')'
}

/** props.functionCategories 过滤（空/缺省 = 全部），函数分类组与函数面板同源共用 */
const filteredFunctions = computed(() => {
  const list = functions.value
  if (!props.functionCategories || !props.functionCategories.length) {
    return list
  }
  return list.filter((fn) => props.functionCategories.includes(fn.category))
})

const functionFiltered = computed(() => !!(props.functionCategories && props.functionCategories.length))

/** 函数按分类分组（九类定序，未知分类兜底追加在后） */
const functionGroups = computed(() => {
  const groups = new Map()
  for (const fn of filteredFunctions.value) {
    const key = fn.category || 'OTHER'
    if (!groups.has(key)) {
      groups.set(key, [])
    }
    groups.get(key).push(fn)
  }
  const order = [...CATEGORY_ORDER, ...[...groups.keys()].filter((key) => !CATEGORY_ORDER.includes(key))]
  return order
    .filter((key) => groups.has(key))
    .map((key) => ({ key, label: CATEGORY_LABELS[key] || key, children: groups.get(key) }))
})

/** 函数分类组：分类层节点只承担分组不插入，子节点按元数据生成占位参数模板 */
const functionNodes = computed(() => {
  if (functionsError.value) {
    return emptyNode('func', functionsError.value)
  }
  if (!functionGroups.value.length) {
    return emptyNode('func', loaded.value ? '暂无函数' : '清单加载中…')
  }
  return functionGroups.value.map((group) => ({
    key: 'cate:' + group.key,
    label: group.label,
    desc: group.children.length + ' 个函数',
    type: 'category',
    children: group.children.map((fn) => ({
      key: 'func:' + fn.functionCode,
      label: fn.functionCode,
      desc: fn.functionName,
      type: 'function',
      insert: buildFunctionTemplate(fn)
    }))
  }))
})

/** 命名公式组：启用中条目（options，不暴露表达式原文）；插入 ${fieldCode}（执行期按编码加载） */
const formulaNodes = computed(() => {
  if (optionsError.value) {
    return emptyNode('opt', optionsError.value)
  }
  if (!formulaOptions.value.length) {
    return emptyNode('opt', loaded.value ? '暂无命名公式' : '清单加载中…')
  }
  return formulaOptions.value.map((item) => ({
    key: 'opt:' + item.fieldCode,
    label: item.fieldName || item.fieldCode,
    desc: item.fieldCode + (item.fieldReturnType ? '（' + item.fieldReturnType + '）' : ''),
    type: 'formula',
    insert: '${' + item.fieldCode + '}'
  }))
})

const treeData = computed(() => [
  { key: 'group-var', label: '变量', type: 'group', children: variableNodes.value },
  { key: 'group-model', label: '业务模型', type: 'group', children: modelNodes.value },
  { key: 'group-func', label: '函数分类', type: 'group', children: functionNodes.value },
  { key: 'group-formula', label: '命名公式', type: 'group', children: formulaNodes.value }
])

/* ==================== 编辑区与 /validate 联动 ==================== */

const hasExpression = computed(() => !!editorText.value.trim())

const validateErrors = computed(() => (validateResult.value && validateResult.value.errors) || [])

const dependencyList = computed(() => (validateResult.value && validateResult.value.dependencies) || [])

/** 编辑器补全变量名：系统变量 + availableVars + 业务模型能力码 + 命名公式码 */
const completionVariableNames = computed(() => {
  const names = [
    ...SYSTEM_VARIABLES.map((item) => item.code),
    ...normalizedAvailableVars.value.map((item) => item.code)
  ]
  targets.value.forEach((capability) => names.push(capability.code))
  formulaOptions.value.forEach((item) => names.push(item.fieldCode))
  return names
})

/** 编辑器补全函数名：带显示名与占位模板（FormulaCodeEditor 支持 { label, detail, apply }） */
const completionFunctionNames = computed(() =>
  filteredFunctions.value.map((fn) => ({
    label: fn.functionCode,
    detail: fn.functionName,
    apply: buildFunctionTemplate(fn)
  }))
)

/** 编辑防抖 500ms 后触发校验（同一计时器复用，连续输入只保留最后一次） */
function scheduleValidate() {
  if (validateTimer) {
    clearTimeout(validateTimer)
  }
  validateTimer = setTimeout(() => {
    validateTimer = null
    runValidate()
  }, VALIDATE_DEBOUNCE)
}

watch(editorText, () => scheduleValidate())

/**
 * 调 /validate 联动校验：表达式为空清空校验状态不发请求；失败只展示不阻断
 * （权威校验在后端），接口异常仅提示。仅应用最新序号的响应（防过期结果覆盖）。
 */
async function runValidate() {
  const expression = editorText.value
  if (!expression || !expression.trim()) {
    validateSeq += 1
    validateResult.value = null
    editorRef.value?.clearErrorLine()
    return
  }
  const seq = ++validateSeq
  const params = { expression }
  if (props.expectedReturnType) {
    params.expectedReturnType = props.expectedReturnType
  }
  const vars = buildValidateVars(expression)
  if (vars) {
    params.vars = vars
  }
  validating.value = true
  try {
    const res = await validateFormula(params)
    if (seq !== validateSeq) {
      return
    }
    validateResult.value = res.data || null
    applyErrorLine(res.data)
  } catch (error) {
    if (seq !== validateSeq) {
      return
    }
    // 校验服务不可用仅提示，不制造本地伪错误、不阻断编辑
    ElMessage.warning(error.message || '公式校验服务暂不可用')
  } finally {
    if (seq === validateSeq) {
      validating.value = false
    }
  }
}

/**
 * 错误行打标：从错误 message 中尽力解析行号（"line 3" / "lineNumber: 3" /
 * 「第 3 行」，不强制格式——Aviator 编译错误信息为 lineNumber: N 形态），
 * 解析得到则 setErrorLine 打标，否则清行标
 */
function applyErrorLine(result) {
  const line = parseErrorLine(result)
  if (line) {
    editorRef.value?.setErrorLine(line)
  } else {
    editorRef.value?.clearErrorLine()
  }
}

function parseErrorLine(result) {
  for (const err of (result && result.errors) || []) {
    const message = (err && err.message) || ''
    const match =
      message.match(/line\s+(\d+)/i) || message.match(/lineNumber:\s*(\d+)/i) || message.match(/第\s*(\d+)\s*行/)
    if (match) {
      return Number(match[1])
    }
  }
  return null
}

/* ==================== 函数面板 ==================== */

/** 参数签名展示（name:type 逗号连接）；无参函数为 () */
function paramSignature(fn) {
  const params = fn.parameters || []
  if (!params.length) {
    return '()'
  }
  return '(' + params.map((item) => (item.name || 'arg') + ':' + (item.type || '?')).join(', ') + ')'
}

/** 函数面板【插入】：与左树函数节点同语义，按注册元数据生成占位参数模板 */
function insertFunction(fn) {
  handleInsert({ insert: buildFunctionTemplate(fn) })
}

/* ==================== 节点插入 ==================== */

/**
 * 节点插入唯一通道：经 FormulaCodeEditor.insertText 在当前光标处写入并保持
 * 光标其后；分组 / 分类层 / 空态节点无 insert 字段静默忽略
 */
function handleInsert(node) {
  if (!node || !node.insert) {
    return
  }
  editorRef.value?.insertText(node.insert)
  editorRef.value?.focus()
}

/* ==================== 模拟区（/simulate 试算） ==================== */

/** 赋值行值缺省形态：NUMBER / BOOLEAN 无值（undefined 不视为已赋值），其余空串 */
function emptyValueFor(type) {
  return type === 'NUMBER' || type === 'BOOLEAN' ? undefined : ''
}

function createAssignRow(code, type) {
  return { code, type, value: emptyValueFor(type), error: '' }
}

/**
 * 赋值行同步：以「校验返回 dependencies ∪ availableVars 声明」为源（排除
 * SYS_CURRENT_ 前缀系统变量），已有行保留赋值不清空、新依赖码自动补行；
 * 类型默认优先取 availableVars 声明，dependencies 提取的默认 STRING
 */
function syncAssignRows() {
  const rows = [...assignRows.value]
  const seen = new Set(rows.map((row) => row.code))
  const declaredTypeMap = new Map(normalizedAvailableVars.value.map((item) => [item.code, item.type]))
  normalizedAvailableVars.value.forEach((item) => {
    if (seen.has(item.code) || isSystemInjected(item.code)) {
      return
    }
    seen.add(item.code)
    rows.push(createAssignRow(item.code, item.type))
  })
  for (const code of dependencyList.value) {
    if (seen.has(code) || isSystemInjected(code)) {
      continue
    }
    seen.add(code)
    rows.push(createAssignRow(code, declaredTypeMap.get(code) || 'STRING'))
  }
  assignRows.value = rows
}

watch(validateResult, () => syncAssignRows())
watch(normalizedAvailableVars, () => syncAssignRows())

/** 赋值行切换类型：清空原值与错误标记（类型语义不同，值不可复用） */
function handleRowTypeChange(row) {
  row.value = emptyValueFor(row.type)
  row.error = ''
}

/**
 * 装配模拟赋值对象：仅提交有值/已赋值的变量（undefined / 空串不提交；
 * NUMBER 的 0、BOOLEAN 的 false 均为有效赋值）；ARRAY 行以 JSON 文本解析，
 * 解析失败标红该行且不提交该变量
 */
function buildSimulateVars() {
  const vars = {}
  assignRows.value.forEach((row) => {
    row.error = ''
    const value = row.value
    if (row.type === 'ARRAY') {
      const raw = typeof value === 'string' ? value.trim() : ''
      if (!raw) {
        return
      }
      try {
        vars[row.code] = JSON.parse(raw)
      } catch (e) {
        row.error = 'JSON 解析失败，该变量未参与本次试算'
      }
      return
    }
    if (value === null || value === undefined || value === '') {
      return
    }
    vars[row.code] = value
  })
  return vars
}

/**
 * 【试算】调 /simulate：仅提交有值/已赋值的变量；空表达式禁用（按钮态兜底）；
 * 失败（success=false）与接口异常（网络等）仅展示/提示、不阻断弹窗操作，
 * 可继续编辑、校验、再试算
 */
async function handleSimulate() {
  const expression = editorText.value
  if (!expression || !expression.trim()) {
    return
  }
  const vars = buildSimulateVars()
  const params = { expression }
  if (Object.keys(vars).length) {
    params.vars = vars
  }
  if (props.expectedReturnType) {
    params.expectedReturnType = props.expectedReturnType
  }
  simulating.value = true
  try {
    const res = await simulateFormula(params)
    simulateResult.value = res.data || null
  } catch (error) {
    simulateResult.value = null
    ElMessage.warning(error.message || '试算服务暂不可用，请稍后重试')
  } finally {
    simulating.value = false
  }
}

/** 试算结果展示：对象/数组 JSON 序列化，其余 String 化（NUMBER 经 BigDecimal 规整为数值） */
function formatResultValue(value) {
  if (value === null || value === undefined) {
    return '-'
  }
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}

/* ==================== 数据加载 ==================== */

/**
 * 弹窗首次打开时并行加载 options / functions / targets 三接口（loaded 置位后
 * 不重复拉取）；单个接口失败仅 ElMessage 警告、对应分组展示空态，不阻断弹窗打开
 */
async function loadData() {
  if (loaded.value || loadingData.value) {
    return
  }
  loadingData.value = true
  const [optionRes, functionRes, targetRes] = await Promise.allSettled([
    getFormulaOptions(),
    getFormulaFunctions(),
    getDatasourceTargets()
  ])
  if (optionRes.status === 'fulfilled') {
    formulaOptions.value = optionRes.value.data || []
  } else {
    optionsError.value = '命名公式加载失败'
    ElMessage.warning('命名公式清单加载失败')
  }
  if (functionRes.status === 'fulfilled') {
    functions.value = functionRes.value.data || []
  } else {
    functionsError.value = '函数清单加载失败'
    ElMessage.warning('函数清单加载失败')
  }
  if (targetRes.status === 'fulfilled') {
    targets.value = targetRes.value.data || []
  } else {
    targetsError.value = '业务模型加载失败'
    ElMessage.warning('业务模型清单加载失败')
  }
  loadingData.value = false
  loaded.value = true
}

/* ==================== 弹窗开关与确认回写 ==================== */

/**
 * 弹窗打开：以 props.expression 重新同步编辑文本（外部可能已改值），校验 /
 * 试算态与赋值行重置（赋值行按当前 availableVars 重建，依赖行待校验返回后
 * 自动补齐）；首开时编辑器尚未挂载，行标清理经可选链兜底
 */
function handleOpen() {
  editorText.value = props.expression || ''
  validateResult.value = null
  simulateResult.value = null
  validating.value = false
  simulating.value = false
  assignRows.value = []
  editorRef.value?.clearErrorLine()
  loadData()
  if (hasExpression.value) {
    scheduleValidate()
  }
}

/** 遮罩 / 右上角 × / ESC 关闭：仅回传显隐，不回写表达式 */
function handleVisibleChange(value) {
  emit('update:visible', value)
}

function handleCancel() {
  emit('update:visible', false)
}

/**
 * 【确认回写】：回写语义与 update:expression 同步发生——先回写表达式文本，
 * 再 emit confirm（载荷 = 当前表达式文本），随后关闭弹窗；表达式为空也允许
 * 确认（由消费方 / 保存期校验兜底）
 */
function handleConfirm() {
  emit('update:expression', editorText.value)
  emit('confirm', editorText.value)
  emit('update:visible', false)
}

onBeforeUnmount(() => {
  if (validateTimer) {
    clearTimeout(validateTimer)
    validateTimer = null
  }
})
</script>

<style scoped>
.designer-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.designer-main {
  display: flex;
  gap: 12px;
}

/* ==================== 左树 ==================== */

.designer-left {
  width: 300px;
  flex-shrink: 0;
  height: 440px;
  overflow: auto;
  padding: 4px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  padding-right: 4px;
}

.tree-node-label {
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: default;
}

.tree-node-label.is-group,
.tree-node-label.is-category {
  font-weight: 600;
}

.tree-node-desc {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tree-node-insert {
  display: none;
  flex-shrink: 0;
}

.tree-node:hover .tree-node-insert {
  display: inline-flex;
}

/* ==================== 右栏：编辑区 + 函数面板 ==================== */

.designer-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.designer-cm {
  --fce-height: 170px;
}

.validate-status {
  margin-top: 8px;
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background-color: #fafbfc;
  font-size: 12px;
  line-height: 1.8;
}

.validate-head {
  display: flex;
  align-items: center;
}

.validate-ok {
  color: #67c23a;
}

.validate-bad {
  color: #f56c6c;
}

.validate-running,
.validate-tip {
  color: #909399;
}

.validate-return {
  margin-left: 12px;
  color: #606266;
}

.validate-errors {
  margin-top: 4px;
}

.validate-error {
  color: #f56c6c;
  word-break: break-all;
}

.validate-deps {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.validate-deps-label {
  color: #909399;
}

.validate-dep-tag {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}

.designer-functions {
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.panel-tip {
  font-size: 12px;
  color: #909399;
}

.panel-empty {
  padding: 8px 0;
  font-size: 12px;
  color: #909399;
}

.fn-scroll {
  max-height: 220px;
  overflow: auto;
  margin-top: 4px;
}

.fn-group-title {
  font-size: 13px;
  color: #303133;
}

.fn-group-count {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.fn-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
  font-size: 12px;
}

.fn-code {
  flex-shrink: 0;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  color: #9254de;
}

.fn-name {
  flex-shrink: 0;
  color: #303133;
}

.fn-sign {
  flex-shrink: 0;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  color: #606266;
}

.fn-desc {
  flex: 1;
  min-width: 0;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ==================== 模拟区 ==================== */

.designer-simulate {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.simulate-btn {
  margin-left: auto;
}

.assign-rows {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 150px;
  overflow: auto;
}

.assign-row {
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: 4px;
}

.assign-row.has-error {
  padding: 2px 4px;
  background-color: #fef0f0;
}

.assign-code {
  width: 200px;
  flex-shrink: 0;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  color: #409eff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.assign-type {
  width: 110px;
  flex-shrink: 0;
}

.assign-value {
  flex: 1;
  min-width: 0;
}

.assign-error {
  flex-shrink: 0;
  font-size: 12px;
  color: #f56c6c;
}

.simulate-result {
  font-size: 12px;
}

.simulate-ok {
  display: flex;
  align-items: center;
  gap: 8px;
}

.simulate-ok-label {
  color: #67c23a;
}

.simulate-value {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  color: #303133;
  word-break: break-all;
}

.simulate-error {
  color: #f56c6c;
  line-height: 1.8;
  word-break: break-all;
}

.simulate-tip {
  color: #909399;
}
</style>

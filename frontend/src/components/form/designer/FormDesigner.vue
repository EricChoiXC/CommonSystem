<template>
  <!--
    表单设计器（F15-FE-008，UI 分册 §4 表单设计器专章）。

    【三区域布局】顶部操作区（el-card #header .header-btns）+ 左物料栏（240px，可折叠）
    + 中设计画布（自适应，配置态渲染器挂载）+ 右属性/规则面板（el-tabs 双页签）。

    【画布 = 配置态渲染器】（§4.4 红线）：挂载 FormRenderer mode="design"——渲染物料
    占位态、数据绑定旁路；设计器只编辑 Schema、渲染器解释 Schema，输出统一 Schema、
    不生成 Vue 页面代码（要点第 2 步原则，§4.10 分离边界）。选中态经
    `.renderer-field-item[data-field]` 事件委托解析（FE-005 预留的结构钩子）。

    【拖拽实现裁决】原生 HTML5 DnD（dragstart/dragover/drop），不引入 vuedraggable/
    SortableJS 新依赖——画布 DOM 由 FormRenderer 内部递归渲染拥有，SortableJS 直接改
    DOM 会与 Vue 状态失同步、vuedraggable 无法接管渲染器内部列表；零依赖同时把 R07
    （新依赖体积/兼容风险）彻底消除。落点经 elementFromPoint/closest 解析插入位置
    （同层 before/after 排序、拖入容器成为其子字段）。

    【表单级动作】组件接收 formId prop 后自行调 loadForm 加载并自持状态；保存草稿调
    updateForm 只携 schema；发布/版本/回滚按 §4.9 全流程在组件内承接；【返回】以
    emit('back') 上抛页面层处理导航；expose isDirty()/getSchema() 供页面层路由守卫。
  -->
  <div class="form-designer">
    <el-card class="designer-card">
      <template #header>
        <div class="page-header">
          <span class="page-title">表单设计器</span>
          <div class="designer-meta">
            <span class="designer-meta-item">名称：{{ formName || '加载中…' }}</span>
            <span class="designer-meta-item">编码：{{ formCode || '-' }}</span>
            <span class="designer-meta-item">
              状态：
              <el-tag size="small" :type="formStatus === 'PUBLISHED' ? 'success' : 'info'">{{ statusLabel }}</el-tag>
            </span>
            <span class="designer-meta-item">版本：{{ formVersion === null || formVersion === undefined ? '-' : `V${formVersion}` }}</span>
            <span v-if="dirty" class="designer-dirty">已修改</span>
          </div>
          <div class="header-btns">
            <el-button size="small" @click="handleBack">返回</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="handleSaveDraft">保存草稿</el-button>
            <el-button size="small" @click="previewVisible = true">预览</el-button>
            <el-button size="small" @click="importVisible = true">导入</el-button>
            <el-button size="small" @click="handleExport">导出</el-button>
            <el-button size="small" @click="versionsVisible = true">版本</el-button>
            <el-tooltip :disabled="!publishDisabledReason" :content="publishDisabledReason" placement="top">
              <span>
                <el-button size="small" type="warning" :disabled="!!publishDisabledReason" :loading="publishing" @click="handlePublish">发布</el-button>
              </span>
            </el-tooltip>
          </div>
        </div>
      </template>

      <div class="designer-body">
        <!-- 左：物料栏（240px，可折叠） -->
        <div v-if="materialsVisible" class="designer-materials">
          <MaterialsPanel
            :max-reached="maxReached"
            @insert="handleMaterialInsert"
            @drag-start="handleMaterialDragStart"
            @drag-end="handleMaterialDragEnd"
          />
        </div>
        <div class="designer-materials-collapse">
          <el-button link size="small" @click="materialsVisible = !materialsVisible">{{ materialsVisible ? '〈' : '〉' }}</el-button>
        </div>

        <!-- 中：设计画布（工具条 + 配置态渲染器） -->
        <div class="designer-canvas-wrap">
          <div class="designer-toolbar">
            <span class="toolbar-label">画布工具</span>
            <el-button size="small" :disabled="!canUndo" @click="handleUndo">撤销</el-button>
            <el-button size="small" :disabled="!canRedo" @click="handleRedo">重做</el-button>
            <el-button size="small" :disabled="schemaFields.length === 0" @click="handleClear">清空</el-button>
            <span v-if="selectedEntry" class="toolbar-selected">已选中：{{ selectedEntry.label || selectedEntry.field }}（{{ selectedEntry.field }}）</span>
          </div>
          <div
            ref="canvasRef"
            class="designer-canvas"
            :class="{ 'is-drag-over': canvasDragOver }"
            @click="handleCanvasClick"
            @dragover="handleCanvasDragOver"
            @dragleave="handleCanvasDragLeave"
            @drop="handleCanvasDrop"
          >
            <!-- 配置态渲染器（数据绑定旁路）：schema 变更整体重建 -->
            <FormRenderer
              :key="rendererKey"
              :schema="schemaObject"
              mode="design"
              :field-permissions="null"
              @expression="handleCanvasExpression"
            />
            <div v-if="schemaFields.length === 0 && !canvasDragOver" class="canvas-empty">画布为空——从左侧物料栏点击或拖拽组件开始设计</div>
            <!-- 选中高亮描边（§4.4 选中态可视化） -->
            <div v-if="selectionBox.visible" class="selection-box" :style="selectionBox.style"></div>
            <!-- 静态初始隐藏 / 权限隐藏可视化（§4.4：半透明 + 角标，画布层叠加承载） -->
            <template v-for="(mark, markIndex) in hiddenMarks" :key="`mark-${markIndex}`">
              <div class="hidden-mark-shade" :style="mark.style"></div>
              <div class="hidden-mark-badge" :style="mark.badgeStyle">{{ mark.text }}</div>
            </template>
            <!-- 插入位置指示条（拖拽悬停时的落点可视化） -->
            <div v-if="dropIndicator.visible" class="drop-indicator" :style="dropIndicator.style"></div>
            <!-- 选中字段悬浮工具条：复制 / 删除 / 拖拽手柄（随选中元素定位） -->
            <div v-if="floatingToolbar.visible" class="floating-toolbar" :style="floatingToolbar.style" @click.stop>
              <span
                class="floating-handle"
                draggable="true"
                title="拖拽移动该字段"
                @dragstart="handleFieldDragStart($event)"
                @dragend="handleMaterialDragEnd"
              >⋮⋮</span>
              <button type="button" class="floating-btn" title="复制" @click.stop="handleCopy">复制</button>
              <button type="button" class="floating-btn floating-btn-danger" title="删除" @click.stop="handleDeleteClick">删除</button>
            </div>
          </div>
          <div class="designer-hints">
            点击物料卡片插入画布末尾（选中容器时插入其内部末尾），或拖拽至画布目标位置；点击字段选中并与右侧面板联动，
            拖拽手柄移动字段；分组/栅格/明细表容器支持嵌套放置（嵌套深度 ≤ {{ maxNestDepth }}，明细表行内仅数据类组件）；
            选中后按 Delete 键删除
          </div>
        </div>

        <!-- 右：属性 + 规则面板（el-tabs 双页签） -->
        <div class="designer-right">
          <el-tabs v-model="activeTab" class="designer-tabs">
            <el-tab-pane label="属性" name="property">
              <PropertyPanel
                :entry="selectedEntry"
                :sibling-codes="siblingCodes"
                :data-field-options="dataFieldOptions"
                :detail-numeric-options="detailNumericOptions"
                :available-vars="availableVars"
                :datasource-options="datasourceOptions"
                :canvas-summary="canvasSummary"
                @prop-change="handlePropChange"
                @field-change="handleFieldChange"
                @children-change="handleChildrenChange"
              />
            </el-tab-pane>
            <el-tab-pane label="规则" name="rule">
              <RulePanel
                :entry="selectedEntry"
                :rules="selectedRules"
                :action-target-options="actionTargetOptions"
                :field-label-map="fieldLabelMap"
                :total-rule-count="totalRuleCount"
                :existing-rule-ids="existingRuleIds"
                :trigger-data-type="selectedDataType"
                @rule-add="handleRuleAdd"
                @rule-update="handleRuleUpdate"
                @rule-delete="handleRuleDelete"
              />
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </el-card>

    <!-- 预览弹窗（编辑态渲染器 + 字段权限预演 + 本地校验） -->
    <PreviewDialog v-model:visible="previewVisible" :schema="schemaObject" />

    <!-- Schema 导入弹窗（轻量校验 + 二次确认 + 整表替换） -->
    <SchemaImportDialog v-model:visible="importVisible" :get-material="getComponentMaterial" @confirm="handleImportConfirm" />

    <!-- 版本列表与回滚弹窗（getFormVersions / rollbackForm） -->
    <VersionsDialog v-model:visible="versionsVisible" :form-id="formId" :current-version-id="currentVersionId" @rollback-success="handleRollbackSuccess" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import FormRenderer from '../FormRenderer.vue'
import '../materials/index.js'
import { getComponentMaterial } from '../registry.js'
import { deriveAvailableVars } from '../rendererSupport.js'
import MaterialsPanel from './MaterialsPanel.vue'
import PropertyPanel from './PropertyPanel.vue'
import RulePanel from './RulePanel.vue'
import PreviewDialog from './PreviewDialog.vue'
import SchemaImportDialog from './SchemaImportDialog.vue'
import VersionsDialog from './VersionsDialog.vue'
import { getDatasourceOptions } from '../../../api/datasource.js'
import { loadForm, updateForm, publishForm } from '../../../api/form.js'
import {
  MAX_FIELD_COUNT,
  MAX_NEST_DEPTH,
  buildFieldIndex,
  cloneFieldForCopy,
  countFields,
  countRules,
  countSubtree,
  createFieldNode,
  createSchemaHistory,
  deepClone,
  resolveEntryByCodeChain,
  resolvePathNode,
  validatePlacement,
} from './designerSupport.js'

/**
 * 表单设计器 props：
 * - formId：表单主档 ID（组件自行调 loadForm 加载并自持状态；画布初始化口径见 API
 *   分册 §3.4——draftSchema 为编辑对象，null 时以 publishedSchema 初始化，均 null 空白）。
 */
const props = defineProps({
  formId: { type: String, default: '' },
})

/**
 * emits：
 * - back：【返回】上抛页面层处理导航（关标签回列表页；页面层据 isDirty 决定是否拦截）；
 * - status-change：加载/保存/发布后表单元数据回显变化上抛（页面层可选消费）。
 */
const emit = defineEmits(['back', 'status-change'])

/* ==================== 表单元数据与 Schema 状态（组件自持） ==================== */

const formName = ref('')
const formCode = ref('')
const formStatus = ref('')
const formVersion = ref(null)
const currentVersionId = ref('')

/** 物料栏折叠开关（§4.2 左物料栏 240px 可折叠） */
const materialsVisible = ref(true)

/** 画布 Schema 字段树（编辑对象） */
const schemaFields = ref([])

/** 载入中的初始 Schema 引用（dirty 判定基线：结构未改动前不置脏） */
let baselineSchemaJson = '[]'

/** 结构改动标记（画布任何结构改动置 dirty，§4.2 脏数据保护） */
const dirty = ref(false)

const saving = ref(false)
const publishing = ref(false)
const previewVisible = ref(false)
const importVisible = ref(false)
const versionsVisible = ref(false)

const statusLabel = computed(() => {
  if (formStatus.value === 'PUBLISHED') return '已发布'
  if (formStatus.value === 'DRAFT') return '草稿'
  return formStatus.value || '-'
})

/** 渲染器消费的 Schema 对象（协议头带元数据回显，fields 为画布引用） */
const schemaObject = computed(() => ({
  formId: props.formId,
  formCode: formCode.value,
  formName: formName.value,
  version: formVersion.value === null || formVersion.value === undefined ? null : formVersion.value,
  fields: schemaFields.value,
}))

/** 渲染器重建键：结构快照回放 / 整表替换 / 清空时整体重建（Schema 内部就地变更走响应式） */
const rendererKey = ref(0)

/* ==================== 加载（§4.4 加载口径：draft → published → 空白） ==================== */

onMounted(async () => {
  if (!props.formId) {
    baselineSchemaJson = JSON.stringify([])
    resetHistory([])
    return
  }
  try {
    const response = await loadForm(props.formId)
    const data = response && response.data ? response.data : {}
    formName.value = data.fieldFormName || ''
    formCode.value = data.fieldFormCode || ''
    formStatus.value = data.fieldStatus || ''
    formVersion.value = data.fieldCurrentVersion === null || data.fieldCurrentVersion === undefined ? null : Number(data.fieldCurrentVersion)
    currentVersionId.value = data.fieldCurrentVersionId || ''
    const draftSchema = data.draftSchema && Array.isArray(data.draftSchema.fields) ? data.draftSchema : null
    const publishedSchema = data.publishedSchema && Array.isArray(data.publishedSchema.fields) ? data.publishedSchema : null
    // §3.4 加载口径：draftSchema 为编辑对象；null（已发布未再编辑）时以 publishedSchema 初始化；
    // 均 null（新建表单未编排）时画布为空白
    const initialFields = draftSchema ? deepClone(draftSchema.fields) : publishedSchema ? deepClone(publishedSchema.fields) : []
    schemaFields.value = initialFields
    hasDraftFromServer.value = !!draftSchema
    baselineSchemaJson = JSON.stringify(schemaFields.value)
    resetHistory(initialFields)
    emit('status-change', { formName: formName.value, formCode: formCode.value, fieldStatus: formStatus.value, fieldCurrentVersion: formVersion.value })
  } catch (error) {
    // 错误提示由 axios 实例统一拦截展示（CS_FORM_* message 直显）
  }
})

/* ==================== 快照栈（撤销重做，仅草稿会话，栈深 50） ==================== */

const history = createSchemaHistory([], { maxDepth: 50 })
const canUndo = ref(false)
const canRedo = ref(false)

function resetHistory(fields) {
  history.reset({ fields: deepClone(fields) })
  refreshHistoryFlags()
}

function refreshHistoryFlags() {
  canUndo.value = history.canUndo()
  canRedo.value = history.canRedo()
}

function handleUndo() {
  const state = history.undo()
  if (!state) return
  applyExternalFields(state.fields)
}

function handleRedo() {
  const state = history.redo()
  if (!state) return
  applyExternalFields(state.fields)
}

/** 外部来源（撤销/重做/导入）整表替换：重建响应式数组 + 渲染器重建 + 选中态清理 */
function applyExternalFields(fields) {
  schemaFields.value = deepClone(fields)
  clearSelection()
  rendererKey.value += 1
  refreshHistoryFlags()
  dirty.value = JSON.stringify(schemaFields.value) !== baselineSchemaJson
  schemaVersion.value++
}

/**
 * 结构变更统一入口：执行 mutator（在当前 schemaFields 上就地变更）→ 压快照 → 置脏。
 * mutator 接收 schemaFields.value（响应式数组）直接操作。
 */
function commitStructure(mutator) {
  mutator(schemaFields.value)
  history.push({ fields: schemaFields.value })
  refreshHistoryFlags()
  dirty.value = true
  schemaVersion.value++
}

/** 非结构属性变更（属性面板 / 规则面板回写）：置脏但不压快照（§4.4 快照栈仅结构变更压栈） */
function markDirty() {
  dirty.value = true
  schemaVersion.value++
}

/* ==================== 扁平索引（选中态 / 下拉选项 / 摘要共用） ==================== */

/**
 * Schema 变更版本号：结构/属性变更均为就地变更（schemaFields 数组引用不变），
 * 依赖 ref 身份的 computed 不会失效——统一在此版本号上登记依赖，强制下游索引
 * 与派生选项重建。
 */
const schemaVersion = ref(0)

const fieldIndex = computed(() => {
  schemaVersion.value
  const raw = buildFieldIndex(schemaFields.value)
  // 物料元数据挂载（属性面板 propSchema 动态渲染消费；条目展开仅拷贝索引字段，
  // node/parentList 引用保持指向 Schema 原树）
  const entries = raw.entries.map((entry) => ({ ...entry, material: getComponentMaterial(entry.type) }))
  return { entries, byPath: new Map(entries.map((entry) => [entry.path, entry])) }
})

/** 选中态（path = designerSupport 稳定路径键；undo/导入/删除后按 path 恢复或清理） */
const selectedPath = ref(null)
const selectedEntry = computed(() => (selectedPath.value ? fieldIndex.value.byPath.get(selectedPath.value) || null : null))

function clearSelection() {
  selectedPath.value = null
}

/** 选中条目变化时选中项丢失（结构变化）则清理（属性面板回落画布级摘要） */
watch(selectedEntry, (entry) => {
  if (!entry && selectedPath.value) selectedPath.value = null
})

/** 渲染器重建后 DOM 随新 key 重建，悬浮工具条按 path 缓存不可靠——结构变化时清理 */
watch(rendererKey, () => {
  floatingToolbar.visible = false
  dropIndicator.visible = false
})

/* ==================== 画布上下文选项派生（属性面板 / 规则面板） ==================== */

const maxReached = computed(() => {
  schemaVersion.value
  return countFields(schemaFields.value) >= MAX_FIELD_COUNT
})
const totalRuleCount = computed(() => {
  schemaVersion.value
  return countRules(schemaFields.value)
})
const existingRuleIds = computed(() => {
  const ids = []
  for (const entry of fieldIndex.value.entries) {
    for (const rule of entry.rules) {
      if (rule && typeof rule.ruleId === 'string') ids.push(rule.ruleId)
    }
  }
  return ids
})

const availableVars = computed(() => {
  schemaVersion.value
  return deriveAvailableVars(schemaFields.value, getComponentMaterial)
})

const fieldLabelMap = computed(() => {
  const map = {}
  for (const entry of fieldIndex.value.entries) map[entry.field] = entry.label || entry.field
  return map
})

const canvasSummary = computed(() => {
  schemaVersion.value
  return {
    formName: formName.value,
    formCode: formCode.value,
    fieldCount: countFields(schemaFields.value),
    ruleCount: countRules(schemaFields.value),
  }
})

/** 选中字段同层级既有字段码（排除自身，fieldCode 唯一性校验消费） */
const siblingCodes = computed(() => {
  const entry = selectedEntry.value
  if (!entry) return []
  return entry.parentList.filter((node) => node && node !== entry.node && typeof node.field === 'string').map((node) => node.field)
})

/** 主表层级数据类字段选项（级联 parentField / 数据源等 contextSelect 消费） */
const dataFieldOptions = computed(() => {
  const options = []
  for (const entry of fieldIndex.value.entries) {
    if (entry.containerType === 'detailTable') continue
    const material = getComponentMaterial(entry.type)
    if (!material || material.category !== 'DATA') continue
    options.push({ code: entry.field, label: entry.label || entry.field })
  }
  return options
})

/** 宿主明细表行内数值类字段选项（summary 统计字段；非明细行内选中态为空数组） */
const detailNumericOptions = computed(() => {
  const entry = selectedEntry.value
  if (!entry || entry.containerType !== 'detailTable') return []
  const options = []
  for (const candidate of fieldIndex.value.entries) {
    if (candidate.containerType !== 'detailTable' || candidate.parentField !== entry.parentField) continue
    const material = getComponentMaterial(candidate.type)
    if (!material || resolveMaterialDataType(material, candidate.props) !== 'NUMBER') continue
    options.push({ code: candidate.field, label: candidate.label || candidate.field })
  }
  return options
})

/** 规则动作目标字段选项（明细表触发源限行内字段码，其余主表数据类字段，§4.6） */
const actionTargetOptions = computed(() => {
  const entry = selectedEntry.value
  if (!entry) return []
  const inRow = entry.containerType === 'detailTable'
  const detailHost = entry.type === 'detailTable'
  const detailCode = inRow ? entry.parentField : detailHost ? entry.field : null
  if (detailCode) {
    const options = []
    for (const candidate of fieldIndex.value.entries) {
      if (candidate.containerType !== 'detailTable' || candidate.parentField !== detailCode) continue
      const material = getComponentMaterial(candidate.type)
      if (!material || material.category !== 'DATA') continue
      options.push({ code: candidate.field, label: candidate.label || candidate.field })
    }
    return options
  }
  return dataFieldOptions.value.filter((option) => option.code !== entry.field)
})

const selectedRules = computed(() => (selectedEntry.value ? selectedEntry.value.rules : []))

/** 选中触发字段解析 dataType（条件值控件选型） */
const selectedDataType = computed(() => {
  const entry = selectedEntry.value
  if (!entry) return ''
  const material = getComponentMaterial(entry.type)
  if (!material) return ''
  const dataType = resolveMaterialDataType(material, entry.props)
  return dataType || ''
})

/** 数据源启用清单（datasource 属性项下拉；onMounted 异步加载，失败静默留空） */
const datasourceOptions = ref([])
onMounted(async () => {
  try {
    const response = await getDatasourceOptions()
    const list = Array.isArray(response && response.data) ? response.data : []
    datasourceOptions.value = list.map((item) => ({ code: item.code, name: item.name }))
  } catch (error) {
    datasourceOptions.value = []
  }
})

/* ==================== 物料插入（点击插入 / 拖拽放置共用落点语义） ==================== */

/**
 * 计算插入目标（§4.4 点击插入）：选中容器时插入其内部末尾，否则画布末尾。
 * 返回 { list, containerKind, parentEntry } 或 { error }。
 */
function resolveInsertTarget(type) {
  const material = getComponentMaterial(type)
  if (!material) return { error: `组件「${type}」未注册，无法插入` }
  if (countFields(schemaFields.value) >= MAX_FIELD_COUNT) {
    return { error: `字段总数已达上限 ${MAX_FIELD_COUNT}（MAX_FIELD_COUNT），无法继续添加` }
  }
  const entry = selectedEntry.value
  // 选中容器（group/grid/detailTable）时插入其内部末尾
  if (entry && (entry.type === 'group' || entry.type === 'grid' || entry.type === 'detailTable')) {
    if (entry.type === 'grid') {
      // 栅格容器：插入选中列（点击选中栅格本体时落最后一列），列内放置约束同容器口径
      const gridTarget = resolveGridTarget(entry, NaN)
      const gridError = validatePlacement({ material, targetKind: 'grid', parentEntry: entry })
      if (gridError) return { error: gridError }
      return gridTarget
    }
    const kind = entry.type
    const placementError = validatePlacement({ material, targetKind: kind, parentEntry: entry })
    if (placementError) return { error: placementError }
    // children 缺失防御（脏 Schema 导入兜底）：就地规整为数组后承接插入
    if (!Array.isArray(entry.node.children)) entry.node.children = []
    return { list: entry.node.children, containerKind: kind, parentEntry: entry }
  }
  const placementError = validatePlacement({ material, targetKind: 'root', parentEntry: null })
  if (placementError) return { error: placementError }
  return { list: schemaFields.value, containerKind: 'root', parentEntry: null }
}

/** 栅格落点列解析（列下标非法回落最后一列；children 缺失就地规整，脏 Schema 导入兜底） */
function resolveGridTarget(gridEntry, columnIndex) {
  if (!Array.isArray(gridEntry.node.children)) gridEntry.node.children = []
  const columns = gridEntry.node.children
  let index = Number.isInteger(columnIndex) && columnIndex >= 0 && columnIndex < columns.length ? columnIndex : columns.length - 1
  if (index < 0) index = 0
  let column = columns[index]
  if (!column || !Array.isArray(column.fields)) {
    column = { span: 12, fields: [] }
    columns[index] = column
  }
  return { list: column.fields, containerKind: 'grid', parentEntry: gridEntry, columnIndex: index }
}

/** 点击物料卡片插入 */
function handleMaterialInsert(type) {
  const target = resolveInsertTarget(type)
  if (target.error) {
    ElMessage.warning(target.error)
    return
  }
  const created = createFieldNode(type, {
    getMaterial,
    siblingCodes: target.list.filter((node) => node && typeof node.field === 'string').map((node) => node.field),
    allFields: schemaFields.value,
  })
  if (created.error) {
    ElMessage.warning(created.error)
    return
  }
  commitStructure(() => {
    target.list.push(created.node)
  })
  // 插入后选中新字段并切到属性页签
  nextTickSelect(() => {
    const entry = fieldIndex.value.entries.find((candidate) => candidate.node === created.node)
    return entry ? entry.path : null
  })
}

/** 在索引重建后按 finder 定位并选中（切到属性页签） */
function nextTickSelect(finder) {
  Promise.resolve().then(() => {
    const path = finder()
    if (path) {
      selectedPath.value = path
      activeTab.value = 'property'
    }
  })
}

/* ==================== 拖拽（原生 HTML5 DnD） ==================== */

/**
 * 拖拽载荷（宿主持有）：物料卡片 dragstart 时记录 { kind: 'new', type }，选中字段
 * 工具条手柄 dragstart 时记录 { kind: 'move', path }。dragover 期 dataTransfer.getData
 * 不可读（浏览器安全约束），故以宿主状态桥接；dragend 清理。
 */
let dragPayload = null

/** 拖拽悬停落点指示（visible + 定位样式） */
const dropIndicator = reactive({ visible: false, style: {} })
const canvasDragOver = ref(false)

function handleMaterialDragStart(type) {
  dragPayload = { kind: 'new', type }
}

/** 选中字段工具条拖拽手柄 dragstart：记录移动载荷（drop 时按 path 重挂到落点） */
function handleFieldDragStart(event) {
  const entry = selectedEntry.value
  if (!entry) {
    event.preventDefault()
    return
  }
  dragPayload = { kind: 'move', path: entry.path }
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('application/json', JSON.stringify({ kind: 'move', path: entry.path }))
  }
}

function handleMaterialDragEnd() {
  dragPayload = null
  dropIndicator.visible = false
  canvasDragOver.value = false
}

/**
 * 画布 dragover：解析悬停落点并可视化指示条。落点解析（§4.4 容器嵌套放置）：
 * elementFromPoint → closest('.renderer-field-item') 取 data-field 属性链（由外到内
 * 容器链）→ resolveEntryByCodeChain 解析索引条目 → 鼠标相对目标矩形上半/下半判定
 * before/after；目标本身为容器且鼠标悬于其内部空白区（非贴边）则判「拖入容器末尾」。
 */
function handleCanvasDragOver(event) {
  if (!dragPayload) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = dragPayload.kind === 'move' ? 'move' : 'copy'
  canvasDragOver.value = true
  updateDropIndicator(event)
}

function handleCanvasDragLeave(event) {
  if (event.target === canvasRef.value) {
    dropIndicator.visible = false
    canvasDragOver.value = false
  }
}

function handleCanvasDrop(event) {
  event.preventDefault()
  const payload = dragPayload
  handleMaterialDragEnd()
  if (!payload) return
  if (payload.kind === 'new') {
    insertByDrop(payload.type, event)
  } else if (payload.kind === 'move') {
    moveByDrop(payload.path, event)
  }
}

/**
 * 解析拖拽落点（drop / dragover 共用）：
 * 返回 { mode: 'before'|'after'|'into', entry, list, containerKind, parentEntry, columnIndex }
 * 或 { mode: 'canvas-end', list, containerKind: 'root', parentEntry: null } 或 { error }。
 */
function resolveDropPosition(event) {
  const canvas = canvasRef.value
  if (!canvas) return { error: '画布未就绪' }
  const element = document.elementFromPoint(event.clientX, event.clientY)
  // FE-005 稳定钩子 .renderer-field-item[data-field]（明细表设计骨架行内单元格仅有 data-field，
  // 为二级回落选择器——选中/拖拽对行内字段的直接寻址消费）
  const fieldElement = element ? element.closest('.renderer-field-item[data-field]') || element.closest('[data-field]') : null
  if (!fieldElement || !canvas.contains(fieldElement)) {
    return { mode: 'canvas-end', list: schemaFields.value, containerKind: 'root', parentEntry: null }
  }
  // data-field 属性链（由外到内容器链）→ 索引条目
  const codes = []
  let cursor = fieldElement
  while (cursor && cursor !== canvas) {
    const code = cursor.getAttribute && cursor.getAttribute('data-field')
    if (code) codes.unshift(code)
    cursor = cursor.parentElement
  }
  const entry = resolveEntryByCodeChain(fieldIndex.value.entries, codes)
  if (!entry) return { mode: 'canvas-end', list: schemaFields.value, containerKind: 'root', parentEntry: null }
  const rect = fieldElement.getBoundingClientRect()
  const offsetY = event.clientY - rect.top
  const ratio = rect.height > 0 ? offsetY / rect.height : 1
  // 容器（group/grid/detailTable）悬于中部（15%~85%）判「拖入容器末尾」；贴边上下判同层 before/after
  const isContainer = entry.type === 'group' || entry.type === 'grid' || entry.type === 'detailTable'
  if (isContainer && ratio >= 0.15 && ratio <= 0.85) {
    if (entry.type === 'grid') {
      // 栅格容器：按鼠标横坐标定位目标列
      const columnElement = element.closest('[data-col-index]')
      const columnIndex = columnElement ? Number(columnElement.getAttribute('data-col-index')) : NaN
      return { mode: 'into', entry, ...resolveGridTarget(entry, Number.isFinite(columnIndex) ? columnIndex : NaN) }
    }
    // children 缺失防御（脏 Schema 导入兜底）：就地规整为数组后承接插入
    if (!Array.isArray(entry.node.children)) entry.node.children = []
    return { mode: 'into', entry, list: entry.node.children, containerKind: entry.type, parentEntry: entry }
  }
  const mode = ratio < 0.5 ? 'before' : 'after'
  return { mode, entry, list: entry.parentList, containerKind: entry.containerType, parentEntry: entry.parentEntry, columnIndex: entry.columnIndex }
}

/** 拖拽悬停指示条定位（before/after 横线，into 容器描边由 CSS class 承载——一期横线足够） */
function updateDropIndicator(event) {
  const canvas = canvasRef.value
  if (!canvas) return
  const position = resolveDropPosition(event)
  if (position.error) {
    dropIndicator.visible = false
    return
  }
  if (position.mode === 'canvas-end') {
    // 画布末尾：指示条贴画布内容底部（absolute 定位随滚动内容平移，scrollHeight 即内容高度）
    dropIndicator.style = { left: '8px', top: `${canvas.scrollHeight - 6}px`, width: `${canvas.clientWidth - 16}px` }
    dropIndicator.visible = true
    return
  }
  if (position.mode === 'into') {
    dropIndicator.visible = false
    return
  }
  const element = document.elementFromPoint(event.clientX, event.clientY)
  const fieldElement = element ? element.closest('.renderer-field-item[data-field]') || element.closest('[data-field]') : null
  if (!fieldElement) {
    dropIndicator.visible = false
    return
  }
  const rect = fieldElement.getBoundingClientRect()
  const canvasRect = canvas.getBoundingClientRect()
  dropIndicator.style = {
    left: `${rect.left - canvasRect.left}px`,
    top: `${(position.mode === 'before' ? rect.top : rect.bottom) - canvasRect.top - 2}px`,
    width: `${rect.width}px`,
  }
  dropIndicator.visible = true
}

/** 拖拽新建落点插入（含约束校验 + 上限拦截 + 嵌套深度拦截提示） */
function insertByDrop(type, event) {
  const material = getComponentMaterial(type)
  if (!material) {
    ElMessage.warning(`组件「${type}」未注册，无法放置`)
    return
  }
  if (countFields(schemaFields.value) >= MAX_FIELD_COUNT) {
    ElMessage.warning(`字段总数已达上限 ${MAX_FIELD_COUNT}（MAX_FIELD_COUNT），无法继续添加`)
    return
  }
  const position = resolveDropPosition(event)
  if (position.error) {
    ElMessage.warning(position.error)
    return
  }
  const targetList = position.list
  const parentEntry = position.parentEntry || null
  const containerKind = position.mode === 'canvas-end' ? 'root' : position.containerKind
  const placementError = validatePlacement({ material, targetKind: containerKind, parentEntry })
  if (placementError) {
    ElMessage.warning(placementError)
    return
  }
  const usedCodes = targetList.filter((node) => node && typeof node.field === 'string').map((node) => node.field)
  const created = createFieldNode(type, { getMaterial, siblingCodes: usedCodes, allFields: schemaFields.value })
  if (created.error) {
    ElMessage.warning(created.error)
    return
  }
  commitStructure(() => {
    if (position.mode === 'before') {
      targetList.splice(position.entry.parentIndex, 0, created.node)
    } else if (position.mode === 'after') {
      targetList.splice(position.entry.parentIndex + 1, 0, created.node)
    } else {
      targetList.push(created.node)
    }
  })
  nextTickSelect(() => {
    const found = fieldIndex.value.entries.find((candidate) => candidate.node === created.node)
    return found ? found.path : null
  })
}

/** 拖拽移动既有字段（选中工具条手柄发起）：同层排序 + 跨层移动（拖入容器成为其子字段） */
function moveByDrop(sourcePath, event) {
  if (!sourcePath) return
  const position = resolveDropPosition(event)
  if (position.error) {
    ElMessage.warning(position.error)
    return
  }
  const sourceNode = resolvePathNode({ fields: schemaFields.value }, sourcePath)
  if (!sourceNode) {
    ElMessage.warning('被拖拽字段已不存在（可能已被删除），请重新选择')
    return
  }
  // 容器不可拖入其自身子树（会成环）
  if (position.entry) {
    let cursor = position.entry
    while (cursor) {
      if (cursor.node === sourceNode) {
        ElMessage.warning('不能将容器拖入其自身内部')
        return
      }
      cursor = cursor.parentEntry
    }
  }
  const material = getComponentMaterial(sourceNode.type)
  const parentEntry = position.parentEntry || null
  const containerKind = position.mode === 'canvas-end' ? 'root' : position.containerKind
  const placementError = validatePlacement({ material, targetKind: containerKind, parentEntry })
  if (placementError) {
    ElMessage.warning(placementError)
    return
  }
  commitStructure(() => {
    // 先从原位置移除（按引用查找，因 splice 后下标会漂移）
    removeFromTree(schemaFields.value, sourceNode)
    // 再按落点插入（落点 entry 的 parentIndex 需在移除后重算——按目标节点引用查找）
    if (position.mode === 'before' || position.mode === 'after') {
      const targetIndex = position.list.indexOf(position.entry.node)
      if (targetIndex < 0) {
        position.list.push(sourceNode)
      } else {
        position.list.splice(position.mode === 'before' ? targetIndex : targetIndex + 1, 0, sourceNode)
      }
    } else {
      position.list.push(sourceNode)
    }
  })
}

/** 从字段树中按引用移除节点（grid 列 fields / group·detail children 递归） */
function removeFromTree(list, node) {
  if (!Array.isArray(list)) return false
  const index = list.indexOf(node)
  if (index >= 0) {
    list.splice(index, 1)
    return true
  }
  for (const item of list) {
    if (!item || typeof item !== 'object') continue
    if (item.type === 'group' && Array.isArray(item.children) && removeFromTree(item.children, node)) return true
    if (item.type === 'grid' && Array.isArray(item.children)) {
      for (const column of item.children) {
        if (column && Array.isArray(column.fields) && removeFromTree(column.fields, node)) return true
      }
    }
    if (item.type === 'detailTable' && Array.isArray(item.children) && removeFromTree(item.children, node)) return true
  }
  return false
}

/* ==================== 选中态与悬浮工具条 ==================== */

const canvasRef = ref(null)
const activeTab = ref('property')
const floatingToolbar = reactive({ visible: false, style: {} })

/** 选中高亮描边（画布层叠加，按选中元素矩形定位；随选中态/滚动/结构变化重算） */
const selectionBox = reactive({ visible: false, style: {} })

/** 静态初始隐藏（props.hidden）与权限隐藏（permission=hidden）字段可视化（§4.4） */
const hiddenMarks = ref([])

/** 重算画布层叠加物（选中描边 + 隐藏字段角标）：按 [data-field] 链定位元素矩形 */
function refreshOverlays() {
  const canvas = canvasRef.value
  if (!canvas) return
  const canvasRect = canvas.getBoundingClientRect()
  // 选中描边
  const entry = selectedEntry.value
  if (entry) {
    const element = locateElementByPath(entry.path)
    if (element) {
      const rect = element.getBoundingClientRect()
      selectionBox.style = {
        left: `${rect.left - canvasRect.left - 2}px`,
        top: `${rect.top - canvasRect.top - 2}px`,
        width: `${rect.width + 4}px`,
        height: `${rect.height + 4}px`,
      }
      selectionBox.visible = true
    } else {
      selectionBox.visible = false
    }
  } else {
    selectionBox.visible = false
  }
  // 隐藏字段半透明遮罩 + 角标（permission=hidden 优先文案，其余 props.hidden）
  const marks = []
  for (const candidate of fieldIndex.value.entries) {
    const hiddenPermission = candidate.props && candidate.props.permission === 'hidden'
    const staticHidden = candidate.props && candidate.props.hidden === true
    if (!hiddenPermission && !staticHidden) continue
    const element = locateElementByPath(candidate.path)
    if (!element) continue
    const rect = element.getBoundingClientRect()
    const top = rect.top - canvasRect.top
    marks.push({
      style: {
        left: `${rect.left - canvasRect.left}px`,
        top: `${top}px`,
        width: `${rect.width}px`,
        height: `${rect.height}px`,
      },
      badgeStyle: {
        left: `${rect.left - canvasRect.left}px`,
        top: `${Math.max(top - 16, 0)}px`,
      },
      text: hiddenPermission ? '〈权限隐藏〉' : '〈初始隐藏〉',
    })
  }
  hiddenMarks.value = marks
}

/**
 * 画布点击：事件委托解析选中态——closest('.renderer-field-item[data-field]') 取字段
 * 属性链 → resolveEntryByCodeChain 解析索引条目 → 选中并定位悬浮工具条；
 * 点击画布空白（未命中字段元素）取消选中（属性 Tab 回落画布级摘要，§4.4）。
 */
function handleCanvasClick(event) {
  const canvas = canvasRef.value
  if (!canvas) return
  const fieldElement = event.target.closest
    ? event.target.closest('.renderer-field-item[data-field]') || event.target.closest('[data-field]')
    : null
  if (!fieldElement || !canvas.contains(fieldElement)) {
    clearSelection()
    return
  }
  const codes = []
  let cursor = fieldElement
  while (cursor && cursor !== canvas) {
    const code = cursor.getAttribute && cursor.getAttribute('data-field')
    if (code) codes.unshift(code)
    cursor = cursor.parentElement
  }
  const entry = resolveEntryByCodeChain(fieldIndex.value.entries, codes)
  if (!entry) {
    clearSelection()
    return
  }
  selectedPath.value = entry.path
  positionToolbar(fieldElement)
}

/** 按 path 在画布 DOM 中定位字段元素（全量 [data-field] 元素链解析匹配，字段规模 ≤200 可接受） */
function locateElementByPath(path) {
  const canvas = canvasRef.value
  if (!canvas) return null
  for (const element of canvas.querySelectorAll('[data-field]')) {
    const codes = []
    let cursor = element
    while (cursor && cursor !== canvas) {
      const code = cursor.getAttribute && cursor.getAttribute('data-field')
      if (code) codes.unshift(code)
      cursor = cursor.parentElement
    }
    const entry = resolveEntryByCodeChain(fieldIndex.value.entries, codes)
    if (entry && entry.path === path) return element
  }
  return null
}

/** 悬浮工具条定位（选中元素右上角上方；出画布上边界时回落元素内顶部） */
function positionToolbar(fieldElement) {
  const canvas = canvasRef.value
  if (!canvas || !fieldElement) {
    floatingToolbar.visible = false
    return
  }
  const rect = fieldElement.getBoundingClientRect()
  const canvasRect = canvas.getBoundingClientRect()
  floatingToolbar.style = {
    left: `${Math.max(rect.right - canvasRect.left - 128, 4)}px`,
    top: `${Math.max(rect.top - canvasRect.top - 26, 2)}px`,
  }
  floatingToolbar.visible = true
}

/** 选中态 / 结构变化 / 画布滚动后重算画布层叠加物（描边 / 角标 / 工具条锚定元素） */
function refreshToolbar() {
  const entry = selectedEntry.value
  if (!entry) {
    floatingToolbar.visible = false
    refreshOverlays()
    return
  }
  positionToolbar(locateElementByPath(entry.path))
  refreshOverlays()
}

watch(selectedPath, () => Promise.resolve().then(refreshToolbar))
watch(schemaFields, () => Promise.resolve().then(refreshToolbar), { deep: true })

// 画布滚动：absolute 定位的工具条随内容平移天然同步，此处仅在滚动容器尺寸变化后校正
function handleCanvasScroll() {
  refreshToolbar()
}

watch(canvasRef, (element, previous) => {
  if (previous && typeof previous.removeEventListener === 'function') previous.removeEventListener('scroll', handleCanvasScroll)
  if (element && typeof element.addEventListener === 'function') element.addEventListener('scroll', handleCanvasScroll, { passive: true })
})

/** Delete 键删除选中字段（§4.4；输入控件聚焦时不触发——由 el-input 等内部按键吞掉，此处仅画布根监听） */
function handleCanvasKeydown(event) {
  if (event.key !== 'Delete') return
  if (!selectedEntry.value) return
  const activeElement = document.activeElement
  if (activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA')) return
  handleDeleteClick()
}

// Delete 键监听挂画布容器（捕获期，避免表单控件内 Delete 编辑字符误删字段）
watch(canvasRef, (element, previous) => {
  if (previous && typeof previous.removeEventListener === 'function') previous.removeEventListener('keydown', handleCanvasKeydown, true)
  if (element && typeof element.addEventListener === 'function') element.addEventListener('keydown', handleCanvasKeydown, true)
})

/** 删除选中字段：容器二次确认（提示级联删除 N 个字段 + 关联规则数），普通字段直接删 */
function handleDeleteClick() {
  const entry = selectedEntry.value
  if (!entry) return
  const subtree = countSubtree(entry.node)
  const cascade = entry.node.children && (entry.node.type === 'group' || entry.node.type === 'grid' || entry.node.type === 'detailTable')
  const confirmMessage = cascade
    ? `删除${containerLabel(entry.type)}「${entry.label || entry.field}」将同时删除其内部 ${subtree.fields - 1} 个字段与 ${subtree.rules} 条关联规则，确定删除？`
    : `删除字段「${entry.label || entry.field}」将同时删除其 ${subtree.rules} 条关联规则，确定删除？`
  const doRemove = () => {
    commitStructure(() => {
      removeFromTree(schemaFields.value, entry.node)
    })
    clearSelection()
    floatingToolbar.visible = false
  }
  if (cascade) {
    ElMessageBox.confirm(confirmMessage, '删除容器', { type: 'warning' })
      .then(doRemove)
      .catch(() => {})
  } else {
    doRemove()
  }
}

function containerLabel(type) {
  if (type === 'group') return '分组'
  if (type === 'grid') return '栅格'
  return '明细表'
}

/** 复制选中字段：深拷贝（含 props/rules/children）+ 重分配字段码，插入原字段之后 */
function handleCopy() {
  const entry = selectedEntry.value
  if (!entry) return
  if (countFields(schemaFields.value) + countSubtree(entry.node).fields - 1 >= MAX_FIELD_COUNT) {
    ElMessage.warning(`复制后字段总数将超过上限 ${MAX_FIELD_COUNT}（MAX_FIELD_COUNT），无法复制`)
    return
  }
  const targetList = entry.parentList
  const usedCodes = targetList.filter((node) => node && typeof node.field === 'string').map((node) => node.field)
  const clone = cloneFieldForCopy(entry.node, usedCodes)
  commitStructure(() => {
    targetList.splice(entry.parentIndex + 1, 0, clone)
  })
  nextTickSelect(() => {
    const found = fieldIndex.value.entries.find((candidate) => candidate.node === clone)
    return found ? found.path : null
  })
}

/* ==================== 属性面板回写（prop / field / children 三通道） ==================== */

function handlePropChange({ path, key, value }) {
  const node = resolvePathNode({ fields: schemaFields.value }, path)
  if (!node) return
  if (!node.props || typeof node.props !== 'object') node.props = {}
  node.props[key] = value
  markDirty()
}

function handleFieldChange({ path, key, value }) {
  const node = resolvePathNode({ fields: schemaFields.value }, path)
  if (!node) return
  node[key] = value
  markDirty()
}

function handleChildrenChange({ path, children }) {
  const node = resolvePathNode({ fields: schemaFields.value }, path)
  if (!node) return
  node.children = children
  markDirty()
}

/** FormulaWidget design 态 expression 回带（画布 FormulaWidget 弹窗 confirm → 渲染器
 *  emit expression → 设计器决定回写 field.props.expression，§4.3 第 7 条） */
function handleCanvasExpression(payload) {
  if (!payload || typeof payload.field !== 'string') return
  const entry = fieldIndex.value.entries.find((candidate) => candidate.field === payload.field)
  if (!entry) return
  if (!entry.node.props || typeof entry.node.props !== 'object') entry.node.props = {}
  entry.node.props.expression = payload.expression
  markDirty()
}

/* ==================== 规则面板回写 ==================== */

function handleRuleAdd({ path, rule }) {
  const node = resolvePathNode({ fields: schemaFields.value }, path)
  if (!node) return
  if (!Array.isArray(node.rules)) node.rules = []
  node.rules.push(rule)
  markDirty()
}

function handleRuleUpdate({ path, ruleId, rule }) {
  const node = resolvePathNode({ fields: schemaFields.value }, path)
  if (!node || !Array.isArray(node.rules)) return
  const targetIndex = node.rules.findIndex((item) => item && item.ruleId === ruleId)
  if (targetIndex >= 0) node.rules.splice(targetIndex, 1, rule)
  markDirty()
}

function handleRuleDelete({ path, ruleId }) {
  const node = resolvePathNode({ fields: schemaFields.value }, path)
  if (!node || !Array.isArray(node.rules)) return
  node.rules = node.rules.filter((item) => !(item && item.ruleId === ruleId))
  markDirty()
}

/* ==================== 清空 / 导入导出 / 保存 / 发布 ==================== */

/** 清空：二次确认后清空全部字段（等价一次可撤销的结构变更，走快照栈，§4.4） */
function handleClear() {
  ElMessageBox.confirm('确定清空画布全部字段？（可通过撤销恢复）', '清空画布', { type: 'warning' })
    .then(() => {
      commitStructure(() => {
        schemaFields.value.splice(0, schemaFields.value.length)
      })
      clearSelection()
      rendererKey.value += 1
    })
    .catch(() => {})
}

/** 导出：JSON 文件 {formCode}.json（草稿 version=null；体积超 512KB 提示，§4.8） */
function handleExport() {
  const schema = schemaObject.value
  if (!Array.isArray(schema.fields) || schema.fields.length === 0) {
    ElMessage.warning('画布为空，无法导出')
    return
  }
  const payload = { ...schema, version: null }
  const text = JSON.stringify(payload, null, 2)
  if (text.length > MAX_SCHEMA_JSON_LENGTH) {
    ElMessage.warning(`Schema 序列化体积超过上限（512KB，MAX_SCHEMA_JSON_LENGTH），请精简后再导出`)
    return
  }
  const blob = new Blob([text], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${formCode.value || 'form'}.json`
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

/** 导入确认（SchemaImportDialog 校验 + 二次确认后上抛）：整表替换画布（不直接落库） */
function handleImportConfirm(schema) {
  commitStructure(() => {
    schemaFields.value.splice(0, schemaFields.value.length, ...deepClone(schema.fields || []))
  })
  clearSelection()
  rendererKey.value += 1
  if (schema.formName && !formName.value) formName.value = schema.formName
  if (schema.formCode && !formCode.value) formCode.value = schema.formCode
}

/** 保存草稿：updateForm 只携 schema（fieldId 必填、schema 全量重写，API 分册 §3.5） */
function handleSaveDraft() {
  if (!props.formId) {
    ElMessage.warning('表单主档 ID 缺失，无法保存')
    return
  }
  saving.value = true
  updateForm({ fieldId: props.formId, schema: { ...schemaObject.value, version: null, fields: deepClone(schemaFields.value) } })
    .then((response) => {
      const data = response && response.data ? response.data : {}
      // 以服务端归一后的元数据刷新回显（分册 §3.5：data = SysFormVo，draftSchema 非空 = 草稿行已建）
      if (data.fieldStatus) formStatus.value = data.fieldStatus
      if (data.fieldCurrentVersion !== undefined && data.fieldCurrentVersion !== null) formVersion.value = Number(data.fieldCurrentVersion)
      if (data.fieldCurrentVersionId) currentVersionId.value = data.fieldCurrentVersionId
      hasDraftFromServer.value = !!(data.draftSchema && Array.isArray(data.draftSchema.fields))
      baselineSchemaJson = JSON.stringify(schemaFields.value)
      dirty.value = false
      resetHistory(schemaFields.value)
      ElMessage.success('草稿保存成功')
      emit('status-change', { formName: formName.value, formCode: formCode.value, fieldStatus: formStatus.value, fieldCurrentVersion: formVersion.value })
    })
    .catch(() => {
      // 错误提示由 axios 实例统一拦截展示（CS_FORM_* message 直显）
    })
    .finally(() => {
      saving.value = false
    })
}

/** 服务端侧是否存在草稿行（loadForm draftSchema 非空；发布预判兜底位） */
const hasDraftFromServer = ref(false)

/** 发布前置预判：无草稿行（已发布未再编辑）时置灰 + 提示（CS_FORM_NO_DRAFT 预判，§4.9） */
const publishDisabledReason = computed(() => {
  if (countFields(schemaFields.value) === 0) return '画布为空，请先设计表单结构'
  if (formStatus.value === 'PUBLISHED' && !dirty.value && !hasDraftFromServer.value) {
    return '无草稿可发布，请先修改表单'
  }
  return ''
})

/** 发布：确认弹窗（不可变版本语义）→ publishForm → 成功提示新版本号并刷新画布状态 */
function handlePublish() {
  if (dirty.value) {
    // 画布有未保存改动时发布将使用服务端存量草稿（与画布不一致）——先保存再发布
    ElMessage.warning('画布有未保存改动，请先【保存草稿】再发布')
    return
  }
  ElMessageBox.confirm(
    '发布将把当前草稿冻结为不可变新版本（版本号 Vn+1），已发布版本不可再修改；在途数据继续使用原版本，新填写使用新版本。确定发布？',
    '发布表单',
    { type: 'warning' },
  )
    .then(async () => {
      publishing.value = true
      try {
        const response = await publishForm(props.formId)
        const data = response && response.data ? response.data : {}
        formVersion.value = data.fieldVersion !== undefined && data.fieldVersion !== null ? Number(data.fieldVersion) : formVersion.value
        currentVersionId.value = data.fieldVersionId || currentVersionId.value
        formStatus.value = 'PUBLISHED'
        hasDraftFromServer.value = false
        baselineSchemaJson = JSON.stringify(schemaFields.value)
        dirty.value = false
        resetHistory(schemaFields.value)
        ElMessage.success(`发布成功，新版本 V${formVersion.value}`)
        emit('status-change', { formName: formName.value, formCode: formCode.value, fieldStatus: formStatus.value, fieldCurrentVersion: formVersion.value })
      } catch (error) {
        // 发布失败按校验链逐条展示（axios 实例统一拦截 message 直显）
      } finally {
        publishing.value = false
      }
    })
    .catch(() => {})
}

/** 回滚成功：刷新画布状态（回滚 = 重发布副本，不产生指针回拨；画布以服务端最新内容为准重载） */
async function handleRollbackSuccess() {
  if (!props.formId) return
  try {
    const response = await loadForm(props.formId)
    const data = response && response.data ? response.data : {}
    formStatus.value = data.fieldStatus || ''
    formVersion.value = data.fieldCurrentVersion === null || data.fieldCurrentVersion === undefined ? null : Number(data.fieldCurrentVersion)
    currentVersionId.value = data.fieldCurrentVersionId || ''
    const draftSchema = data.draftSchema && Array.isArray(data.draftSchema.fields) ? data.draftSchema : null
    const publishedSchema = data.publishedSchema && Array.isArray(data.publishedSchema.fields) ? data.publishedSchema : null
    const fields = draftSchema ? deepClone(draftSchema.fields) : publishedSchema ? deepClone(publishedSchema.fields) : []
    applyExternalFields(fields)
    baselineSchemaJson = JSON.stringify(schemaFields.value)
    dirty.value = false
  } catch (error) {
    // 错误提示由 axios 实例统一拦截展示
  }
}

/* ==================== 返回与脏数据保护（expose isDirty/getSchema） ==================== */

/** 【返回】上抛页面层处理导航；有未保存改动先二次确认（§4.2 脏数据保护） */
function handleBack() {
  if (!dirty.value) {
    emit('back')
    return
  }
  ElMessageBox.confirm('表单设计已修改，确认离开？', '离开确认', { type: 'warning' })
    .then(() => emit('back'))
    .catch(() => {})
}

/** 页面层路由守卫消费：是否存在未保存改动 */
function isDirty() {
  return dirty.value
}

/** 页面层消费：当前画布 Schema（深拷贝） */
function getSchema() {
  return deepClone(schemaObject.value)
}

defineExpose({ isDirty, getSchema })

/* ==================== 物料 dataType 解析（本组件局部等值实现，避免引渲染器内部模块） ==================== */

function resolveMaterialDataType(material, propsValue) {
  if (!material) return null
  const dataType = material.dataType
  if (typeof dataType === 'function') return dataType(propsValue || {})
  return dataType === undefined ? null : dataType
}

const maxNestDepth = MAX_NEST_DEPTH
</script>

<style scoped>
.form-designer {
  width: 100%;
}

.designer-card :deep(.el-card__body) {
  padding: 0;
}

/* 顶部元数据回显 */
.designer-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: 12px;
  flex: 1;
  flex-wrap: wrap;
}

.designer-meta-item {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.designer-dirty {
  font-size: 12px;
  color: var(--el-color-warning);
}

/* 三区域主体 */
.designer-body {
  display: flex;
  align-items: stretch;
  height: calc(100vh - 190px);
  min-height: 420px;
}

.designer-materials {
  flex: none;
  width: 240px;
  border-right: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}

.designer-materials-collapse {
  flex: none;
  width: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
}

/* 中画布 */
.designer-canvas-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--el-border-color-lighter);
}

.designer-toolbar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.toolbar-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.toolbar-selected {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-color-primary);
}

.designer-canvas {
  position: relative;
  flex: 1;
  overflow: auto;
  padding: 16px;
}

.designer-canvas.is-drag-over {
  outline: 1px dashed var(--el-color-primary-light-5);
  outline-offset: -4px;
}

.canvas-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  pointer-events: none;
}

/* 插入位置指示条 */
.drop-indicator {
  position: absolute;
  height: 4px;
  border-radius: 2px;
  background: var(--el-color-primary);
  pointer-events: none;
  z-index: 30;
}

/* 选中高亮描边（画布层叠加） */
.selection-box {
  position: absolute;
  border: 1px solid var(--el-color-primary);
  border-radius: 2px;
  background: var(--el-color-primary-light-9);
  pointer-events: none;
  z-index: 10;
}

/* 静态初始隐藏 / 权限隐藏可视化（§4.4：半透明遮罩 + 〈角标〉，画布层叠加承载） */
.hidden-mark-shade {
  position: absolute;
  background: rgba(255, 255, 255, 0.55);
  pointer-events: none;
  z-index: 20;
}

.hidden-mark-badge {
  position: absolute;
  transform: translate(0, -100%);
  padding: 0 4px;
  border-radius: 2px;
  background: var(--el-color-info-light-7);
  color: var(--el-text-color-secondary);
  font-size: 10px;
  line-height: 16px;
  white-space: nowrap;
  pointer-events: none;
  z-index: 21;
}

/* 选中悬浮工具条：拖拽手柄 + 复制 / 删除 */
.floating-toolbar {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 4px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-light);
  z-index: 40;
}

.floating-handle {
  cursor: grab;
  padding: 0 4px;
  font-size: 12px;
  line-height: 20px;
  letter-spacing: -2px;
  color: var(--el-text-color-secondary);
  user-select: none;
}

.floating-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 2px 8px;
  font-size: 12px;
  color: var(--el-color-primary);
}

.floating-btn:hover {
  background: var(--el-color-primary-light-9);
}

.floating-btn-danger {
  color: var(--el-color-danger);
}

.floating-btn-danger:hover {
  background: var(--el-color-danger-light-9);
}

.designer-hints {
  flex: none;
  padding: 6px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 11px;
  line-height: 1.6;
  color: var(--el-text-color-placeholder);
}

/* 右面板 */
.designer-right {
  flex: none;
  width: 340px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.designer-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.designer-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
}

.designer-tabs :deep(.el-tab-pane) {
  height: 100%;
}
</style>

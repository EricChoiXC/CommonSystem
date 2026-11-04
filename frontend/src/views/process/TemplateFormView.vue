<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">
          {{ pageTitle }}
          <el-tag v-if="form.fieldVersion" size="small" style="margin-left: 8px">v{{ form.fieldVersion }}</el-tag>
          <el-tag v-if="!isCreate" size="small" :type="form.fieldEnable ? 'success' : 'info'" style="margin-left: 6px">
            {{ form.fieldEnable ? '启用' : '停用' }}
          </el-tag>
        </span>
        <div class="header-btns">
          <el-button @click="goBack">关闭</el-button>
          <el-button v-if="isDetail && canEdit" type="primary" @click="switchToEdit">编辑</el-button>
          <el-button v-if="!isCreate && canEdit" :type="form.fieldEnable ? 'warning' : 'success'" @click="handleToggleEnable">
            {{ form.fieldEnable ? '停用' : '启用' }}
          </el-button>
          <el-button v-if="!isDetail && canEdit" type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </div>
    </template>

    <!-- ============ 上部：模板基本字段 ============ -->
    <el-form :model="form" label-width="40%" :disabled="isDetail || saving" class="page-form">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="模板名称" required>
            <el-input v-model="form.fieldName" placeholder="请输入模板名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模板编号">
            <el-input v-model="form.fieldCode" placeholder="请输入模板编号" />
          </el-form-item>
        </el-col>
        <el-col :span="24" v-if="form.fieldModelName">
          <el-form-item label="所属模块" label-width="20%">
            <el-input :model-value="modelLabel || form.fieldModelName" disabled />
          </el-form-item>
        </el-col>
        <!-- 备注为长内容字段：一行一字段，字段名:字段值 = 2:8（label 占 20%） -->
        <el-col :span="24">
          <el-form-item label="备注" label-width="20%">
            <el-input v-model="form.fieldRemark" type="textarea" :rows="2" placeholder="请输入备注" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- ============ 下部：节点配置列表 ============ -->
    <div class="node-section">
      <div class="node-section-header">
        <span class="node-section-title">节点配置</span>
        <el-button v-if="!isDetail && canEdit" type="primary" plain size="small" @click="addApproveNode">添加审批节点</el-button>
      </div>
      <el-table :data="form.nodes" border stripe row-key="fieldId" size="default">
        <el-table-column prop="fieldNo" label="节点编号" width="90" align="center" />
        <el-table-column label="节点名称" min-width="160">
          <template #default="{ row }">
            <el-input
              v-if="row.fieldNodeType === 'APPROVE' && !isDetail && canEdit"
              v-model="row.fieldNodeName"
              size="small"
              placeholder="请输入节点名称"
            />
            <span v-else>{{ row.fieldNodeName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="节点类型" min-width="110">
          <template #default="{ row }">
            <el-tag :type="nodeTypeTag(row.fieldNodeType)" size="small">{{ nodeTypeText(row.fieldNodeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="节点配置" min-width="140">
          <template #default="{ row }">
            <el-button
              v-if="row.fieldNodeType === 'APPROVE'"
              link
              type="primary"
              @click="openConfigDialog(row)"
            >配置</el-button>
            <span v-else class="node-config-empty">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isDetail && canEdit" label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.fieldNodeType === 'APPROVE'">
              <el-button
                link
                type="primary"
                :disabled="isFirstApprove(row)"
                @click="moveNode(row, -1)"
              >上移</el-button>
              <el-button
                link
                type="primary"
                :disabled="isLastApprove(row)"
                @click="moveNode(row, 1)"
              >下移</el-button>
              <el-button
                link
                type="danger"
                @click="removeApproveNode(row)"
              >删除</el-button>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!isDetail && canEdit" class="node-tip">
        固定节点（开始/草稿/结束）不可编辑；审批节点请点击「配置」设置审批来源、审批人、审批模式、节点触发等；结束节点编号随审批节点的创建自动顺延。
      </div>
    </div>

    <!-- ============ 节点配置弹框（多标签：基础 / 审批权限） ============ -->
    <el-dialog
      v-model="configDialogVisible"
      :title="`节点配置：${configNode.fieldNodeName || ''}`"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-tabs v-model="configActiveTab">
        <!-- 基础标签：审批来源、审批人、审批模式、节点触发、触发条件、允许自动跳过、允许连续跳过 -->
        <el-tab-pane label="基础" name="base">
          <el-form :model="configForm" label-width="110px" :disabled="isDetail">
            <el-form-item label="审批来源">
              <el-select v-model="configForm.handlerSource" style="width: 100%">
                <el-option v-for="opt in HANDLER_SOURCE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="configForm.handlerSource === 'org'" label="审批人" required>
              <AddressBook
                v-model:field-id="configForm.handlerIds"
                v-model:field-name="configForm.handlerNames"
                org-type="PERSONPOST"
                multi
                :disabled="isDetail"
              />
            </el-form-item>
            <el-form-item v-else label="审批人字段" required>
              <el-select v-model="configForm.handlerField" style="width: 100%" placeholder="请选择审批人字段" filterable>
                <el-option v-for="(label, key) in modelFieldsDict" :key="key" :label="label" :value="key" />
              </el-select>
              <div class="node-ops-tip">审批时读取该字段值解析出的组织架构（岗位/用户）作为审批人。</div>
            </el-form-item>
            <el-form-item label="审批模式">
              <el-select v-model="configForm.approveMode" style="width: 100%">
                <el-option v-for="opt in APPROVE_MODE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="节点触发">
              <el-select v-model="configForm.triggerType" style="width: 100%">
                <el-option v-for="opt in TRIGGER_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <!-- 触发条件：不可编辑输入框 + 「编辑」按钮，点击打开公式编辑弹窗，确认后回填 -->
            <el-form-item v-if="configForm.triggerType === 'condition'" label="触发条件" required>
              <el-input
                :model-value="configForm.condition"
                readonly
                placeholder="请点击「编辑」填写判断公式"
              >
                <template #append>
                  <el-button :disabled="isDetail" @click="openConditionDialog">编辑</el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="允许自动跳过">
              <el-select v-model="configForm.autoSkip" style="width: 100%">
                <el-option v-for="opt in AUTO_SKIP_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="允许连续跳过">
              <el-select v-model="configForm.autoContinueSkip" style="width: 100%">
                <el-option v-for="opt in AUTO_SKIP_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <!-- 审批权限标签：操作标识开关（按当前分类 process_template.${fieldModelName}.ops 字典渲染） -->
        <el-tab-pane label="审批权限" name="auth">
          <el-form :model="configForm" label-width="110px" :disabled="isDetail">
            <el-form-item label="操作标识">
              <div class="node-ops-list">
                <div v-for="(label, key) in modelOpsDict" :key="key" class="node-ops-item">
                  <el-switch v-model="configForm.ops[key]" :disabled="isDetail" />
                  <span class="node-ops-label">{{ label }}</span>
                </div>
                <div v-if="!Object.keys(modelOpsDict).length" class="node-ops-tip">当前分类未配置可用的操作标识。</div>
                <div v-else class="node-ops-tip">未开启的操作标识，该节点审批人不允许执行对应业务操作（默认不允许）。</div>
              </div>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="configDialogVisible = false">关闭</el-button>
        <el-button v-if="!isDetail" type="primary" @click="confirmConfig">确定</el-button>
      </template>
    </el-dialog>

    <!-- ============ 触发条件编辑弹窗 ============ -->
    <el-dialog
      v-model="conditionDialogVisible"
      title="触发条件编辑"
      width="760px"
      :close-on-click-modal="false"
      append-to-body
    >
      <div class="condition-editor">
        <!-- 左侧：fields 字段项 + 公式计算符，点击插入到右侧光标处 -->
        <div class="condition-source">
          <div class="condition-block-title">字段（fields）</div>
          <div class="condition-chip-list">
            <el-tag
              v-for="(label, key) in modelFieldsDict"
              :key="key"
              class="condition-chip"
              @click="insertCondition('${' + key + '}')"
            >{{ label }}</el-tag>
            <span v-if="!Object.keys(modelFieldsDict).length" class="condition-empty">当前分类未配置字段字典</span>
          </div>
          <div class="condition-block-title">公式计算符</div>
          <div class="condition-chip-list">
            <el-tag
              v-for="op in CONDITION_OPERATORS"
              :key="op.value"
              class="condition-chip"
              @click="insertCondition(op.value)"
            >{{ op.label }}</el-tag>
          </div>
        </div>
        <!-- 右侧：可编辑文本域，支持直接输入公式 -->
        <div class="condition-input">
          <el-input
            ref="conditionInputRef"
            v-model="conditionDraft"
            type="textarea"
            :rows="12"
            placeholder="直接输入公式，或点击左侧字段/计算符插入到光标处"
          />
          <div class="condition-tip">占位符字段必须为文档实体类存在的字段；如：${amount} &gt; 1000</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="conditionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCondition">确认</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { markTabToClose } from '@/utils/tabs'
import { ElMessage, ElMessageBox } from 'element-plus'
import AddressBook from '@/components/AddressBook.vue'
import { hasAuth } from '@/utils/auth'
import { getInitID } from '@/api/common'
import { getDictOptions, getDictTextMap } from '@/utils/dict'
import { watchRouteQuery } from '@/utils/routeQuery'
import {
  enableProcessTemplate,
  loadProcessTemplate,
  queryProcessTemplateModels,
  saveProcessTemplate,
  updateProcessTemplate
} from '@/api/process'

/**
 * 流程模板新建/编辑/详情页：/processTemplate/create 新建、
 * /processTemplate/edit/:id 编辑、/processTemplate/detail/:id 查看。
 * 顶部操作按钮：保存、编辑、启停用、关闭（新建页无编辑/启停用）；
 * 上部显示模板名称/编号/备注，下部为节点配置列表（固定节点不可编辑，
 * 审批节点点击「配置」弹出多标签弹框维护：「基础」标签含审批来源（组织架构/表单字段）、
 * 审批人（来源=组织架构为地址本多选限岗位+用户，来源=表单字段从分类 fields 字典选字段）、
 * 审批模式（单签/会审）、节点触发（固定/条件判断）、触发条件（不可编辑输入框 + 「编辑」
 * 打开公式编辑弹窗：左侧 fields 字段项与公式计算符，点击插入右侧文本域光标处，确认后回填）、
 * 是否允许自动跳过、是否允许连续跳过；「审批权限」标签含操作标识开关）。
 * 依据 artificial/docs/设计文档/sys/process/流程核心业务.md「模板详情/模板编辑 页面」「触发条件编辑弹窗」「审批人配置」。
 */

/** 节点类型文本映射（dict.process_node_type） */
const NODE_TYPE_TEXT = ref({})

const NODE_TYPE_TAG = {
  START: 'info',
  DRAFT: 'warning',
  END: 'danger',
  APPROVE: 'success',
  ROBORT: 'primary'
}

/** 节点配置下拉选项（dict.process_approve_mode / process_trigger_type / process_auto_skip / process_handler_source） */
const APPROVE_MODE_OPTIONS = ref([])
const TRIGGER_TYPE_OPTIONS = ref([])
const AUTO_SKIP_OPTIONS = ref([])
const HANDLER_SOURCE_OPTIONS = ref([])

/** 触发条件公式计算符（点击插入到公式编辑弹窗右侧文本域光标处） */
const CONDITION_OPERATORS = [
  { label: '(', value: '(' },
  { label: ')', value: ')' },
  { label: '+', value: '+' },
  { label: '-', value: '-' },
  { label: '*', value: '*' },
  { label: '/', value: '/' },
  { label: '>', value: '>' },
  { label: '>=', value: '>=' },
  { label: '<', value: '<' },
  { label: '<=', value: '<=' },
  { label: '==', value: '==' },
  { label: '!=', value: '!=' },
  { label: '&&', value: '&&' },
  { label: '||', value: '||' },
  { label: '!', value: '!' }
]

/** 编辑/启停用所需权限：与后端 module.update / module.enable / module.save 鉴权一致 */
const AUTH_TEMPLATE_ADMIN = ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_TEMPLATE_ADMIN']

const route = useRoute()
const router = useRouter()

const isCreate = computed(() => route.path.includes('/create'))
const isEdit = computed(() => route.path.includes('/edit/'))
const isDetail = computed(() => route.path.includes('/detail/'))

/** 新建/编辑/启停用按钮可见性（预判，最终权限由后端 ProcessResolver 兜底） */
const canEdit = computed(() => hasAuth(AUTH_TEMPLATE_ADMIN))

const saving = ref(false)
const modelLabel = ref('')

/** 当前分类可配置的操作标识字典（process_template.${fieldModelName}.ops，key=操作标识，value=中文描述） */
const modelOpsDict = ref({})

/** 当前分类文档实体字段字典（process_template.${fieldModelName}.fields，key=字段属性名，value=中文描述） */
const modelFieldsDict = ref({})

const form = reactive({
  fieldId: '',
  fieldName: '',
  fieldCode: '',
  fieldModelName: '',
  fieldRemark: '',
  fieldEnable: true,
  fieldVersion: null,
  nodes: []
})

const pageTitle = computed(() => {
  if (isCreate.value) return '新建流程模板'
  if (isEdit.value) return '编辑流程模板'
  return '流程模板详情'
})

function nodeTypeText(type) {
  return NODE_TYPE_TEXT.value[type] || type || '-'
}

function nodeTypeTag(type) {
  return NODE_TYPE_TAG[type] || 'info'
}

/* ============ 节点维护 ============ */

/** 固定节点构造：开始(1) / 草稿(2) / 结束(置末) */
function fixedNode(type, no, name) {
  return { fieldId: '', fieldNo: no, fieldNodeName: name, fieldNodeType: type }
}

/** 按固定节点规则重新编号：START=1、DRAFT=2、审批节点顺延、END 置末 */
function renumberNodes(nodes) {
  const start = nodes.find((n) => n.fieldNodeType === 'START')
  const draft = nodes.find((n) => n.fieldNodeType === 'DRAFT')
  const end = nodes.find((n) => n.fieldNodeType === 'END')
  const middles = nodes.filter((n) => !['START', 'DRAFT', 'END'].includes(n.fieldNodeType))
  const result = []
  if (start) result.push({ ...start, fieldNo: 1, fieldNodeName: '开始节点' })
  if (draft) result.push({ ...draft, fieldNo: 2, fieldNodeName: '草稿节点' })
  let no = 3
  middles.forEach((n) => {
    result.push({ ...n, fieldNo: no, fieldNodeName: n.fieldNodeName || `审批节点${no}` })
    no += 1
  })
  if (end) result.push({ ...end, fieldNo: no, fieldNodeName: '结束节点' })
  return result
}

/** 添加审批节点：插入到结束节点之前 */
function addApproveNode() {
  const nodes = form.nodes
  const endIndex = nodes.findIndex((n) => n.fieldNodeType === 'END')
  const node = {
    fieldId: '',
    fieldNodeName: '',
    fieldNodeType: 'APPROVE',
    handlerSource: 'org',
    handlerIds: '',
    handlerNames: '',
    handlerField: '',
    approveMode: 'single',
    triggerType: 'fixed',
    condition: '',
    autoSkip: 'system',
    autoContinueSkip: 'system'
  }
  if (endIndex >= 0) {
    nodes.splice(endIndex, 0, node)
  } else {
    nodes.push(node)
  }
  form.nodes = renumberNodes(nodes)
}

function removeApproveNode(row) {
  const index = form.nodes.findIndex((n) => n === row)
  if (index >= 0) {
    form.nodes.splice(index, 1)
  }
  form.nodes = renumberNodes(form.nodes)
}

/** 审批节点在数组中的下标列表（固定节点不参与移动） */
function approveIndexes() {
  const list = []
  form.nodes.forEach((n, i) => {
    if (n.fieldNodeType === 'APPROVE') list.push(i)
  })
  return list
}

/** 是否为第一个审批节点（首个不可上移） */
function isFirstApprove(row) {
  const idx = approveIndexes()
  return idx.length < 2 || idx[0] === form.nodes.indexOf(row)
}

/** 是否为最后一个审批节点（末个不可下移） */
function isLastApprove(row) {
  const idx = approveIndexes()
  return idx.length < 2 || idx[idx.length - 1] === form.nodes.indexOf(row)
}

/** 上移/下移审批节点：仅在审批节点之间交换相对顺序，随后按固定节点规则重新编号 */
function moveNode(row, offset) {
  const idx = approveIndexes()
  const from = form.nodes.indexOf(row)
  const pos = idx.indexOf(from)
  const to = idx[pos + offset]
  if (to == null) return
  const arr = [...form.nodes]
  const temp = arr[from]
  arr[from] = arr[to]
  arr[to] = temp
  form.nodes = renumberNodes(arr)
}

/* ============ 节点配置弹框 ============ */

const configDialogVisible = ref(false)
const configTarget = ref(null)
const configActiveTab = ref('base')
const configNode = reactive({ fieldNodeName: '' })
const configForm = reactive({
  handlerSource: 'org',
  handlerIds: '',
  handlerNames: '',
  handlerField: '',
  approveMode: 'single',
  triggerType: 'fixed',
  condition: '',
  autoSkip: 'system',
  autoContinueSkip: 'system',
  ops: {}
})

function openConfigDialog(row) {
  configTarget.value = row
  configActiveTab.value = 'base'
  Object.assign(configNode, { fieldNodeName: row.fieldNodeName })
  Object.assign(configForm, {
    handlerSource: row.handlerSource || 'org',
    handlerIds: row.handlerIds || '',
    handlerNames: row.handlerNames || '',
    handlerField: row.handlerField || '',
    approveMode: row.approveMode || 'single',
    triggerType: row.triggerType || 'fixed',
    condition: row.condition || '',
    autoSkip: row.autoSkip || 'system',
    autoContinueSkip: row.autoContinueSkip || 'system'
  })
  // 操作标识回填：仅开启项落库（后端仅保留 true 项），未配置视为全部关闭（默认不允许）
  const ops = {}
  for (const key of Object.keys(modelOpsDict.value)) {
    ops[key] = !!(row.ops && row.ops[key])
  }
  configForm.ops = ops
  configDialogVisible.value = true
}

function confirmConfig() {
  if (configForm.triggerType === 'condition' && !configForm.condition.trim()) {
    ElMessage.warning('节点触发为条件判断时，触发条件不能为空')
    return
  }
  if (configForm.handlerSource === 'field' && !configForm.handlerField) {
    ElMessage.warning('审批来源为表单字段时，请选择审批人字段')
    return
  }
  if (configForm.handlerSource === 'org' && !(configForm.handlerIds || '').trim()) {
    ElMessage.warning('审批来源为组织架构时，审批人不能为空')
    return
  }
  const row = configTarget.value
  if (row) {
    // 按审批来源保留对应明细：组织架构保留审批人，表单字段保留审批人字段
    const fromOrg = configForm.handlerSource === 'org'
    Object.assign(row, {
      handlerSource: configForm.handlerSource,
      handlerIds: fromOrg ? configForm.handlerIds : '',
      handlerNames: fromOrg ? configForm.handlerNames : '',
      handlerField: fromOrg ? '' : configForm.handlerField,
      approveMode: configForm.approveMode,
      triggerType: configForm.triggerType,
      condition: configForm.condition,
      autoSkip: configForm.autoSkip,
      autoContinueSkip: configForm.autoContinueSkip
    })
    // 操作标识：仅提交开启项（false/未开启等同不允许，由后端默认不允许语义兜底）
    const enabledOps = {}
    for (const [key, enabled] of Object.entries(configForm.ops || {})) {
      if (enabled) enabledOps[key] = true
    }
    row.ops = enabledOps
  }
  configDialogVisible.value = false
}

/* ============ 触发条件编辑弹窗 ============ */

const conditionDialogVisible = ref(false)
const conditionDraft = ref('')
const conditionInputRef = ref(null)

/** 打开公式编辑弹窗：以当前触发条件作为草稿 */
function openConditionDialog() {
  conditionDraft.value = configForm.condition || ''
  conditionDialogVisible.value = true
}

/** 获取公式文本域 DOM，用于读取/设置光标位置 */
function conditionTextarea() {
  return conditionInputRef.value?.$el?.querySelector('textarea') || null
}

/** 将左侧点选的字段占位符/计算符插入到右侧文本域当前光标处（有选中文本则替换） */
function insertCondition(text) {
  const el = conditionTextarea()
  const current = conditionDraft.value || ''
  const start = el && el.selectionStart != null ? el.selectionStart : current.length
  const end = el && el.selectionEnd != null ? el.selectionEnd : current.length
  conditionDraft.value = current.slice(0, start) + text + current.slice(end)
  const pos = start + text.length
  nextTick(() => {
    const target = conditionTextarea()
    if (target) {
      target.focus()
      target.setSelectionRange(pos, pos)
    }
  })
}

/** 确认：将右侧文本回填到节点配置「基础」标签的触发条件输入框 */
function confirmCondition() {
  configForm.condition = (conditionDraft.value || '').trim()
  conditionDialogVisible.value = false
}

/* ============ 初始化 ============ */

/** 新建初始化：预取对象 ID，所属模块来自列表页路由 query */
async function initForCreate() {
  try {
    const res = await getInitID()
    form.fieldId = (res && res.data) || ''
  } catch {
    // 预取失败不阻塞：后端 save 会自动生成 ID
  }
  form.fieldModelName = route.query.modelName || ''
  modelLabel.value = route.query.modelLabel || ''
  form.nodes = [
    fixedNode('START', 1, '开始节点'),
    fixedNode('DRAFT', 2, '草稿节点'),
    fixedNode('END', 3, '结束节点')
  ]
}

// 二次从流程模板列表页进入时实例复用、onMounted 不会重跑，
// 监听 query 按带入模块重建新建表单（重置节点默认结构并刷新模型字典）
watchRouteQuery(route, (q) => {
  if (!isCreate.value || !('modelName' in q)) return
  initForCreate()
  loadModelConfig()
})

/** 编辑/详情初始化：按路由 id 加载数据回填 */
async function initForLoad() {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少模板 ID')
    goBack()
    return
  }
  try {
    const res = await loadProcessTemplate(id)
    const row = res.data
    if (!row) {
      ElMessage.error('未找到该流程模板')
      goBack()
      return
    }
    form.fieldId = row.fieldId
    form.fieldName = row.fieldName
    form.fieldCode = row.fieldCode || ''
    form.fieldModelName = row.fieldModelName
    form.fieldRemark = row.fieldRemark || ''
    form.fieldEnable = !!row.fieldEnable
    form.fieldVersion = row.fieldVersion
    modelLabel.value = ''
    // 节点回填（编辑模式为可编辑副本）
    form.nodes = (row.nodes || []).map((n) => ({
      fieldId: n.fieldId || '',
      fieldNo: n.fieldNo,
      fieldNodeName: n.fieldNodeName,
      fieldNodeType: n.fieldNodeType,
      handlerSource: n.handlerSource || 'org',
      handlerIds: n.handlerIds || '',
      handlerNames: n.handlerNames || '',
      handlerField: n.handlerField || '',
      approveMode: n.approveMode || 'single',
      triggerType: n.triggerType || 'fixed',
      condition: n.condition || '',
      autoSkip: n.autoSkip || 'system',
      autoContinueSkip: n.autoContinueSkip || 'system',
      ops: n.ops || {}
    }))
  } catch (error) {
    ElMessage.error(error.message || '加载数据失败')
    goBack()
  }
}

onMounted(async () => {
  loadDictOptions()
  // 先完成初始化（确定 fieldModelName），再按分类加载操作标识/文档实体字段字典
  if (isCreate.value) {
    await initForCreate()
  } else {
    await initForLoad()
  }
  loadModelConfig()
})

/** 加载当前分类的操作标识字典与文档实体字段字典（queryProcessTemplateModels 返回分类列表，含 ops / fields）；失败时保持空字典不阻塞 */
async function loadModelConfig() {
  try {
    const res = await queryProcessTemplateModels()
    const list = (res && res.data) || []
    const found = list.find((m) => m.fieldModelName === form.fieldModelName)
    modelOpsDict.value = (found && found.ops) || {}
    modelFieldsDict.value = (found && found.fields) || {}
  } catch {
    modelOpsDict.value = {}
    modelFieldsDict.value = {}
  }
}

/** 加载节点类型文本与节点配置选项（dict.process_node_type / process_approve_mode / process_trigger_type / process_auto_skip / process_handler_source）；失败时保持空选项，不阻塞页面 */
async function loadDictOptions() {
  try {
    const [nodeTypeTextMap, approveModeOptions, triggerTypeOptions, autoSkipOptions, handlerSourceOptions] = await Promise.all([
      getDictTextMap('process_node_type'),
      getDictOptions('process_approve_mode'),
      getDictOptions('process_trigger_type'),
      getDictOptions('process_auto_skip'),
      getDictOptions('process_handler_source')
    ])
    NODE_TYPE_TEXT.value = nodeTypeTextMap
    APPROVE_MODE_OPTIONS.value = approveModeOptions
    TRIGGER_TYPE_OPTIONS.value = triggerTypeOptions
    AUTO_SKIP_OPTIONS.value = autoSkipOptions
    HANDLER_SOURCE_OPTIONS.value = handlerSourceOptions
  } catch {
    NODE_TYPE_TEXT.value = {}
    APPROVE_MODE_OPTIONS.value = []
    TRIGGER_TYPE_OPTIONS.value = []
    AUTO_SKIP_OPTIONS.value = []
    HANDLER_SOURCE_OPTIONS.value = []
  }
}

/* ============ 保存 / 启停用 / 关闭 ============ */

/** 详情 → 编辑：切换到编辑路由（同一卡片标签跟随） */
function switchToEdit() {
  router.push(`/processTemplate/edit/${form.fieldId}`)
}

async function handleSave() {
  if (!form.fieldName || !form.fieldName.trim()) {
    ElMessage.warning('模板名称不能为空')
    return
  }
  if (!form.fieldModelName) {
    ElMessage.warning('缺少所属模块，请返回列表页重新进入')
    return
  }
  // 节点校验：按审批来源校验审批人/审批人字段、条件判断时触发条件必填（最终以后端校验为准）
  for (const node of form.nodes) {
    if (node.fieldNodeType !== 'APPROVE') continue
    if (node.handlerSource === 'field') {
      if (!node.handlerField) {
        ElMessage.warning(`节点「${node.fieldNodeName || '审批节点'}」的审批人字段不能为空`)
        return
      }
    } else if (!node.handlerIds || !node.handlerIds.trim()) {
      ElMessage.warning(`节点「${node.fieldNodeName || '审批节点'}」的审批人不能为空`)
      return
    }
    if (node.triggerType === 'condition' && !node.condition.trim()) {
      ElMessage.warning(`节点「${node.fieldNodeName || '审批节点'}」触发条件不能为空`)
      return
    }
  }
  saving.value = true
  try {
    const body = {
      fieldId: form.fieldId,
      fieldName: form.fieldName.trim(),
      fieldCode: form.fieldCode.trim() || null,
      fieldModelName: form.fieldModelName,
      fieldRemark: form.fieldRemark.trim() || null,
      fieldEnable: form.fieldEnable,
      nodes: form.nodes
    }
    if (isEdit.value) {
      await updateProcessTemplate(body)
      ElMessage.success('保存成功')
    } else {
      await saveProcessTemplate(body)
      ElMessage.success('创建成功')
    }
    markTabToClose(route.path)
    router.push('/processTemplate')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleToggleEnable() {
  const enable = !form.fieldEnable
  try {
    await ElMessageBox.confirm(
      `确认将「${form.fieldName}」${enable ? '启用' : '停用'}？${enable ? '' : '停用后该模板将不可再发起新的流程。'}`,
      '操作确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await enableProcessTemplate({ fieldId: form.fieldId, fieldEnable: enable })
    form.fieldEnable = enable
    ElMessage.success(enable ? '已启用' : '已停用')
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

function goBack() {
  router.push('/processTemplate')
}
</script>

<script>
export default { name: 'TemplateFormView' }
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.header-btns {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-section {
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 12px;
}

.node-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.node-section-title {
  font-size: 14px;
  font-weight: 600;
}

.node-config-empty {
  color: var(--el-text-color-placeholder);
}

.node-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 节点配置弹框 - 操作标识开关区 */
.node-ops-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-ops-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-ops-label {
  font-size: 13px;
}

.node-ops-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

/* 触发条件编辑弹窗：左侧点选内容区 + 右侧可编辑文本域 */
.condition-editor {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.condition-source {
  width: 260px;
  flex-shrink: 0;
  max-height: 320px;
  overflow-y: auto;
}

.condition-block-title {
  font-size: 13px;
  font-weight: 600;
  margin: 4px 0 8px;
}

.condition-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.condition-chip {
  cursor: pointer;
}

.condition-chip:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.condition-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.condition-input {
  flex: 1;
  min-width: 0;
}

.condition-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>

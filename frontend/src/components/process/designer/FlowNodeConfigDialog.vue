<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="760px"
    :close-on-click-modal="false"
    @update:model-value="handleVisibleChange"
    @open="handleOpen"
  >
    <el-tabs v-if="supported" v-model="activeTab">
      <!-- ============ APPROVE · 基本 ============ -->
      <el-tab-pane v-if="isApprove" label="基本" name="base">
        <el-form label-width="120px" @submit.prevent>
          <el-form-item label="节点名称" required>
            <el-input v-model="form.nodeName" maxlength="100" placeholder="请输入节点名称" />
          </el-form-item>
          <el-form-item label="审批源">
            <el-select v-model="form.handlerSource" style="width: 100%" @change="handleHandlerSourceChange">
              <el-option v-for="opt in HANDLER_SOURCE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.handlerSource === 'org'" label="审批人" required>
            <!-- 六类全选：orgType="ALL" + multi + showRole（机构/部门/人员/岗位 + 群组/角色页签，分册 §5.2 冻结写法） -->
            <AddressBook
              v-model:field-id="form.handlerIds"
              v-model:field-name="form.handlerNames"
              org-type="ALL"
              multi
              show-role
              :callback="handleHandlersSelected"
            />
          </el-form-item>
          <el-form-item label="解析方式">
            <el-radio-group v-model="form.handlerResolveMode" :disabled="!resolveEditable">
              <el-radio v-for="opt in RESOLVE_MODE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
            </el-radio-group>
            <span class="field-tip" :class="{ 'is-warning': !resolveEditable }">{{ resolveModeTip }}</span>
          </el-form-item>
          <el-form-item v-if="form.handlerSource === 'field'" label="审批人字段" required>
            <el-select v-model="form.handlerField" filterable style="width: 100%" placeholder="请选择审批人字段">
              <el-option v-for="(label, key) in fieldOptions" :key="key" :label="label" :value="key" />
            </el-select>
            <div class="field-tip">审批时读取该字段值解析出的组织架构（岗位/用户）作为审批人。</div>
          </el-form-item>
          <el-form-item v-if="form.handlerSource === 'formula'" label="审批人公式" required>
            <FlowFormulaDialog
              v-model="form.handlerFormula"
              :formula-vars="formulaVars"
              placeholder="请点击「编辑」配置审批人公式"
              tip="公式结果按文本/Map/List 解析为组织对象；合法性权威校验在后端公式定义器"
            />
          </el-form-item>
          <el-form-item label="审批方式">
            <el-select v-model="form.approveMode" style="width: 100%">
              <el-option v-for="opt in APPROVE_MODE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="相同审批人跳过">
            <el-select v-model="form.skipMode" style="width: 100%" @change="handleSkipModeChange">
              <el-option v-for="opt in SKIP_MODE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <div v-if="skipModeLegacyTip" class="field-tip">{{ skipModeLegacyTip }}</div>
          </el-form-item>
          <el-form-item label="允许连续跳过">
            <el-select v-model="form.autoContinueSkip" style="width: 100%">
              <el-option v-for="opt in AUTO_CONTINUE_SKIP_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="无审批人时">
            <el-select v-model="form.emptyHandlerMode" style="width: 100%">
              <el-option v-for="opt in EMPTY_HANDLER_MODE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="驳回目标">
            <el-select v-model="form.rejectTargets" multiple filterable style="width: 100%" placeholder="可空，默认仅起草节点">
              <el-option v-for="opt in rejectTargetOptions" :key="opt.nodeKey" :label="candidateLabel(opt)" :value="opt.nodeKey" />
            </el-select>
            <div class="field-tip">候选=起草节点与图上游审批节点；留空时驳回默认仅回到起草节点。</div>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ============ APPROVE · 审批操作（三段明细，分册 §5.1/走查项 8） ============ -->
      <el-tab-pane v-if="isApprove" label="审批操作" name="operations">
        <div v-for="segment in OPERATION_SEGMENTS" :key="segment.key" class="ops-segment">
          <div class="ops-segment-header">
            <span class="ops-segment-title">{{ segment.label }}</span>
            <el-button size="small" type="primary" plain @click="addOperation(segment.key)">添加</el-button>
          </div>
          <el-table :data="form.operations[segment.key]" border size="small">
            <el-table-column type="index" label="#" width="44" align="center" />
            <el-table-column label="操作类型" min-width="180">
              <template #default="{ row }">
                <el-select v-model="row.operateType" size="small" placeholder="请选择操作类型" @change="handleOperateTypeChange(row)">
                  <el-option v-for="opt in segment.types" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="操作名称" min-width="150">
              <template #default="{ row }">
                <el-input
                  v-model="row.operateName"
                  size="small"
                  maxlength="100"
                  placeholder="留空默认取操作类型名"
                  @input="handleOperateNameInput(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="默认审批意见" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.defaultContent" size="small" maxlength="200" placeholder="面板选择该操作时回填意见框" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="64" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="removeOperation(segment.key, $index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="field-tip">面板按钮集=本配置 ∩ 用户身份；操作名称为审批按钮显示名，留空自动回填操作类型中文名。</div>
      </el-tab-pane>

      <!-- ============ APPROVE · 审批权限（ops 操作标识开关，现状语义迁移） ============ -->
      <el-tab-pane v-if="isApprove" label="审批权限" name="auth">
        <el-form label-width="120px" @submit.prevent>
          <el-form-item label="操作标识">
            <div class="ops-switch-list">
              <div v-for="(label, key) in opsOptions" :key="key" class="ops-switch-item">
                <el-switch v-model="form.ops[key]" />
                <span class="ops-switch-label">{{ label }}</span>
              </div>
              <div v-if="!Object.keys(opsOptions).length" class="field-tip">当前分类未配置可用的操作标识。</div>
              <div v-else class="field-tip">未开启的操作标识，该节点审批人不允许执行对应业务操作（默认不允许）。</div>
            </div>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ============ APPROVE / CC · 流程任务（分册 §5.5 通用明细表） ============ -->
      <el-tab-pane v-if="isApprove || isCc" label="流程任务" name="tasks">
        <FlowTaskDetailTable ref="taskTableRef" v-model:tasks="form.tasks" />
      </el-tab-pane>

      <!-- ============ CC · 基本 ============ -->
      <el-tab-pane v-if="isCc" label="基本" name="base">
        <el-form label-width="120px" @submit.prevent>
          <el-form-item label="节点名称" required>
            <el-input v-model="form.nodeName" maxlength="100" placeholder="请输入节点名称" />
          </el-form-item>
          <el-form-item label="抄送源">
            <el-select v-model="form.ccSource" style="width: 100%" @change="handleCcSourceChange">
              <el-option v-for="opt in CC_SOURCE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.ccSource === 'org'" label="抄送对象" required>
            <AddressBook
              v-model:field-id="form.ccIds"
              v-model:field-name="form.ccNames"
              org-type="ALL"
              multi
              show-role
            />
          </el-form-item>
          <el-form-item v-if="form.ccSource === 'formula'" label="抄送对象公式" required>
            <FlowFormulaDialog
              v-model="form.ccFormula"
              :formula-vars="formulaVars"
              placeholder="请点击「编辑」配置抄送对象公式"
              tip="公式结果按文本/Map/List 解析为组织对象；合法性权威校验在后端公式定义器"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ============ MANUAL · 基本（流向决策，分册 §5.3） ============ -->
      <el-tab-pane v-if="isManual" label="基本" name="base">
        <el-form label-width="120px" @submit.prevent>
          <el-form-item label="节点名称" required>
            <el-input v-model="form.nodeName" maxlength="100" placeholder="请输入节点名称" />
          </el-form-item>
          <el-form-item label="流向决策">
            <el-select v-model="form.flowDecisions" multiple filterable style="width: 100%" placeholder="可空">
              <el-option v-for="opt in flowDecisionOptions" :key="opt.nodeKey" :label="candidateLabel(opt)" :value="opt.nodeKey" />
            </el-select>
            <div class="field-tip">候选=起草节点与图上游审批节点；被选中节点的提交/通过操作可指定本节点流向（flowChoice）。</div>
          </el-form-item>
          <el-form-item label="默认流向" required>
            <el-select v-model="form.defaultFlowKey" filterable style="width: 100%" placeholder="请选择默认流向">
              <el-option v-for="opt in defaultFlowOptions" :key="opt.nodeKey" :label="candidateLabel(opt)" :value="opt.nodeKey" />
            </el-select>
            <div class="field-tip">选项=本节点实际出边目标；无人工指定流向时按默认流向流出（后提交覆盖先提交）。</div>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ============ ROBORT · 基本（执行任务，分册 §5.3） ============ -->
      <el-tab-pane v-if="isRobort" label="基本" name="base">
        <el-form label-width="120px" @submit.prevent>
          <el-form-item label="节点名称" required>
            <el-input v-model="form.nodeName" maxlength="100" placeholder="请输入节点名称" />
          </el-form-item>
        </el-form>
        <div class="robort-tasks-title">执行任务</div>
        <FlowTaskDetailTable ref="taskTableRef" v-model:tasks="form.tasks" />
        <div class="field-tip">节点进入时按行序依次执行，任一任务失败转流程异常。</div>
      </el-tab-pane>

      <!-- ============ FORK · 基本（汇聚配置，分册 §5.3；键名 T-09 定稿） ============ -->
      <el-tab-pane v-if="isFork" label="基本" name="base">
        <el-form label-width="120px" @submit.prevent>
          <el-form-item label="节点名称" required>
            <el-input v-model="form.nodeName" maxlength="100" placeholder="请输入节点名称" />
          </el-form-item>
          <el-form-item label="汇聚模式">
            <el-radio-group v-model="form.joinMode">
              <el-radio v-for="opt in JOIN_MODE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="无可流转分支时">
            <el-radio-group v-model="form.noBranchMode">
              <el-radio v-for="opt in NO_BRANCH_MODE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
            </el-radio-group>
            <div class="field-tip">并行开始节点的全部出边分支激活条件均为否时的处理行为。</div>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
    <el-empty v-else description="该节点类型不支持节点配置" :image-size="60" />

    <template #footer>
      <el-button @click="handleVisibleChange(false)">关闭</el-button>
      <el-button v-if="supported" type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 流程设计器 · 节点配置弹窗（v1_7 / P17-FE-003，分册 §5 冻结）
 *
 * 按节点类型动态渲染 el-tabs（分册 §5.1）：
 *   - APPROVE＝基本 / 审批操作 / 审批权限 / 流程任务 四标签；
 *   - CC＝基本 / 流程任务 两标签；
 *   - MANUAL＝基本（流向决策 flowDecisions 多选 + 默认流向 defaultFlowKey）；
 *   - ROBORT＝基本（执行任务 tasks 明细表内嵌）；
 *   - FORK＝基本（汇聚配置 joinMode all|any + 无可流转分支行为 noBranchMode error|continue，
 *     T-09 定稿键名）。
 * 弹窗宽度 760px（≥760）、:close-on-click-modal="false"；【确定】只做客户端校验并经
 * confirm 事件回抛配置字段（画布统一经 updateNodeProperties 局部合并回写、置脏），
 * 不调接口（随画布整体保存提交）；【关闭】/遮罩/ESC 放弃本次编辑。
 *
 * 数据流：弹窗编辑「工作副本」（打开时经 nodeConfig.echoFormState 快照回显，旧模板缺字段
 * 按默认值回显不报错），【确定】经 nodeConfig.buildConfigFields 组装仅弹窗渲染的字段——
 * 节点其余 properties（存量 triggerType/condition 等遗留字段）由画布局部合并原样保留。
 * 审批源/抄送源切换时清空并隐藏其他来源字段（现状 confirmConfig「按来源保留对应明细」写法）。
 *
 * 候选计算（graphTransform 纯函数，按打开时图上下文快照计算）：
 *   - rejectTargets 候选 = 起草节点 + 图上游可达审批节点（G9）；
 *   - flowDecisions 候选 = 起草节点 + 该 MANUAL 节点图上游审批节点（G10）；
 *   - defaultFlowKey 选项 = 本节点实际出边目标（G6，必填校验在【确定】执行）。
 *   回显值不在候选集（图已改动）时追加展示兜底，合法性由服务端校验兜底。
 *
 * 契约登记（与分册字面有出入或分册未冻结，详见任务清单 §4.32 交付记录）：
 *   - handlerResolveMode 仅当所选对象含部门(2)/岗位(4)时可编辑（经 AddressBook callback 的
 *     selectedList.fieldOrgType 判定）；回显场景无法判定所选对象类型 → 置灰 + 提示；
 *   - skipMode 旧值 yes/no 仅显示归一（yes→prev / no→none），未改动保存保留原值；
 *   - 「执行任务」(taskBean) 下拉 T-01 空态占位（接口未就绪），前端不拦截空 taskBean；
 *   - opsOptions（操作标识字典）为分册 §5.1 之外的必要 prop（审批权限标签键集与现状一致，
 *     字典来源 process_template.${fieldModelName}.ops，宿主 FE-004 传入）。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AddressBook from '@/components/AddressBook.vue'
import FlowFormulaDialog from './FlowFormulaDialog.vue'
import FlowTaskDetailTable from './FlowTaskDetailTable.vue'
import {
  APPROVE_MODE_OPTIONS,
  AUTO_CONTINUE_SKIP_OPTIONS,
  CC_SOURCE_OPTIONS,
  EMPTY_HANDLER_MODE_OPTIONS,
  HANDLER_SOURCE_OPTIONS,
  JOIN_MODE_OPTIONS,
  NO_BRANCH_MODE_OPTIONS,
  OPERATE_TYPE_LABELS,
  OPERATION_SEGMENTS,
  RESOLVE_MODE_OPTIONS,
  SKIP_MODE_OPTIONS,
  buildConfigFields,
  buildOpsSwitches,
  candidateLabel,
  echoFormState,
  fillOperationNames,
  validateOperations,
  withCurrentValue,
} from './nodeConfig'
import {
  flowDecisionCandidates,
  outEdgeTargets,
  rejectTargetCandidates,
} from './graphTransform'

const props = defineProps({
  /** 弹窗显隐（v-model:visible） */
  visible: { type: Boolean, default: false },
  /** 当前节点 { nodeKey, fieldNodeType, nodeName, properties }（properties 为配置平铺字段） */
  node: { type: Object, default: null },
  /** 图上下文快照 · 节点（画布打开弹窗时注入当前图；元素 { nodeKey, nodeType, nodeName }） */
  nodes: { type: Array, default: () => [] },
  /** 图上下文快照 · 连线（元素 { fromKey, toKey }） */
  lines: { type: Array, default: () => [] },
  /** 审批人字段字典（分类 process_template.${fieldModelName}.fields：key=字段属性名，value=中文） */
  fieldOptions: { type: Object, default: () => ({}) },
  /** 操作标识字典（分类 process_template.${fieldModelName}.ops：key=操作标识，value=中文；现状同源） */
  opsOptions: { type: Object, default: () => ({}) },
  /** 公式上下文变量声明（元素 { code, type?, label? }，宿主页传分类文档实体字段字典） */
  formulaVars: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:visible', 'confirm'])

/** 支持节点配置弹窗的节点类型（分册 §5.1；START/DRAFT/END/JOIN 由画布拦截不进本弹窗，
 *  CONDITION 由画布分流至条件分支节点配置弹窗 FlowConditionNodeConfigDialog，也不进本弹窗） */
const SUPPORTED_NODE_TYPES = ['APPROVE', 'CC', 'MANUAL', 'ROBORT', 'FORK']

const activeTab = ref('base')
const taskTableRef = ref(null)

/** 工作副本表单（打开时按节点快照重建） */
const form = reactive(echoFormState(null, {}))

const nodeType = computed(() => props.node?.fieldNodeType || '')
const supported = computed(() => SUPPORTED_NODE_TYPES.includes(nodeType.value))
const isApprove = computed(() => nodeType.value === 'APPROVE')
const isCc = computed(() => nodeType.value === 'CC')
const isManual = computed(() => nodeType.value === 'MANUAL')
const isRobort = computed(() => nodeType.value === 'ROBORT')
const isFork = computed(() => nodeType.value === 'FORK')
const nodeKey = computed(() => props.node?.nodeKey || '')

const dialogTitle = computed(() => `节点配置：${props.node?.nodeName || ''}（${nodeKey.value || '-'}）`)

/* ==================== 打开回显 ==================== */

watch(
  () => props.visible,
  (value) => {
    if (value) {
      handleOpen()
    }
  }
)

/** 打开：按节点快照重建工作副本（旧模板缺字段按默认值回显），审批权限开关按分类字典 ∩ 回显值构建 */
function handleOpen() {
  Object.assign(form, echoFormState(props.node || {}, props.node?.properties?.ops || {}))
  form.ops = buildOpsSwitches(props.opsOptions, form.opsEcho)
  activeTab.value = 'base'
}

/* ==================== 候选选项 ==================== */

/** 驳回目标候选（起草节点置首 + 上游审批节点；回显值不在候选集时追加兜底） */
const rejectTargetOptions = computed(() =>
  withCurrentValue(rejectTargetCandidates(props.nodes, props.lines, nodeKey.value), form.rejectTargets)
)

/** 流向决策候选（起草节点置首 + 该 MANUAL 节点图上游审批节点；回显值追加兜底） */
const flowDecisionOptions = computed(() =>
  withCurrentValue(flowDecisionCandidates(props.nodes, props.lines, nodeKey.value), form.flowDecisions)
)

/** 默认流向候选（本节点实际出边目标；回显值追加兜底展示，【确定】仍按实际出边校验） */
const defaultFlowOptions = computed(() =>
  withCurrentValue(outEdgeTargets(props.nodes, props.lines, nodeKey.value), form.defaultFlowKey)
)

/* ==================== 审批人解析方式显隐 ==================== */

/**
 * 解析方式可编辑性：本次会话经地址本确认过（resolveKnown）且所选对象含部门(2)/岗位(4)
 * （AddressBook callback 的 selectedList.fieldOrgType 判定，分册 §5.2「仅当所选对象含
 * 部门或岗位时显示」）。回显场景无法判定所选对象类型 → 置灰（登记口径见文件头）。
 */
const resolveEditable = computed(() => {
  if (!String(form.handlerIds || '').trim() || !form.resolveKnown) {
    return false
  }
  return form.selectedOrgTypes.some((type) => type === 2 || type === 4)
})

const resolveModeTip = computed(() => {
  if (resolveEditable.value) {
    return '仅对部门/岗位生效：负责人=部门/岗位/机构负责人，成员=部门/岗位/机构成员'
  }
  return '仅对部门/岗位生效' + (form.resolveKnown ? '' : '（回显数据无法判定所选对象类型，重新选择审批人后可编辑）')
})

/** 地址本确认回调：记录本次所选对象类型（fieldOrgType：2=部门 4=岗位），驱动解析方式显隐 */
function handleHandlersSelected(ids, names, selectedList) {
  form.handlerIds = ids || ''
  form.handlerNames = names || ''
  form.resolveKnown = true
  form.selectedOrgTypes = (selectedList || [])
    .map((item) => item?.fieldOrgType)
    .filter((type) => type !== undefined && type !== null)
}

/* ==================== 来源切换（按来源保留对应明细） ==================== */

/** 切换审批源：清空并隐藏其他来源字段（现状 confirmConfig 同口径，防残留值提交） */
function handleHandlerSourceChange(value) {
  if (value !== 'org') {
    form.handlerIds = ''
    form.handlerNames = ''
    form.resolveKnown = false
    form.selectedOrgTypes = []
  }
  if (value !== 'field') {
    form.handlerField = ''
  }
  if (value !== 'formula') {
    form.handlerFormula = ''
  }
}

/** 切换抄送源：同口径清空其他来源字段 */
function handleCcSourceChange(value) {
  if (value !== 'org') {
    form.ccIds = ''
    form.ccNames = ''
  }
  if (value !== 'formula') {
    form.ccFormula = ''
  }
}

/* ==================== skipMode 旧值兼容 ==================== */

/** 旧值兼容提示：存量 skipMode 为 yes/no（或承接旧 autoSkip）时说明显示归一与保留口径 */
const skipModeLegacyTip = computed(() => {
  const raw = String(form.skipModeRaw || '').trim()
  if (raw !== 'yes' && raw !== 'no') {
    return ''
  }
  const label = raw === 'yes' ? '上一审批节点审批人相同时跳过' : '不跳过'
  return `存量配置为旧值 ${raw}，显示为「${label}」；未改动时保存保留原值`
})

/** 用户改动跳过模式：置改动标记（【确定】写新枚举值；未改动则保留原值不改写存量 JSON） */
function handleSkipModeChange() {
  form.skipModeTouched = true
}

/* ==================== 审批操作三段明细 ==================== */

function addOperation(segmentKey) {
  form.operations[segmentKey].push({ operateType: '', operateName: '', defaultContent: '', _autoName: true })
}

function removeOperation(segmentKey, index) {
  form.operations[segmentKey].splice(index, 1)
}

/** 操作类型变化：名称为空或仍为自动回填值时，自动回填新类型中文名（分册 §5.1 默认口径） */
function handleOperateTypeChange(row) {
  if (row._autoName || !String(row.operateName || '').trim()) {
    row.operateName = OPERATE_TYPE_LABELS[row.operateType] || ''
    row._autoName = true
  }
}

/** 用户手输操作名称：退出自动回填跟踪 */
function handleOperateNameInput(row) {
  row._autoName = false
}

/* ==================== 确定（客户端校验 + 回写回抛，不调接口） ==================== */

function handleVisibleChange(value) {
  emit('update:visible', value)
}

function handleConfirm() {
  if (!supported.value) {
    return
  }
  if (!String(form.nodeName || '').trim()) {
    ElMessage.warning('节点名称不能为空')
    return
  }
  switch (nodeType.value) {
    case 'APPROVE': {
      if (form.handlerSource === 'org' && !String(form.handlerIds || '').trim()) {
        ElMessage.warning('审批来源为组织架构时，审批人不能为空')
        return
      }
      if (form.handlerSource === 'field' && !form.handlerField) {
        ElMessage.warning('审批来源为表单字段时，请选择审批人字段')
        return
      }
      if (form.handlerSource === 'formula' && !String(form.handlerFormula || '').trim()) {
        ElMessage.warning('审批来源为公式定义器时，请配置审批人公式')
        return
      }
      const operationsError = validateOperations(form.operations)
      if (operationsError) {
        ElMessage.error(operationsError)
        return
      }
      fillOperationNames(form.operations)
      break
    }
    case 'CC': {
      if (form.ccSource === 'org' && !String(form.ccIds || '').trim()) {
        ElMessage.warning('抄送来源为组织架构时，抄送对象不能为空')
        return
      }
      if (form.ccSource === 'formula' && !String(form.ccFormula || '').trim()) {
        ElMessage.warning('抄送来源为公式定义器时，请配置抄送对象公式')
        return
      }
      break
    }
    case 'MANUAL': {
      if (!form.defaultFlowKey) {
        ElMessage.warning('请选择默认流向')
        return
      }
      const targets = outEdgeTargets(props.nodes, props.lines, nodeKey.value)
      if (!targets.some((target) => target.nodeKey === form.defaultFlowKey)) {
        ElMessage.warning('默认流向必须是该节点实际出边目标之一')
        return
      }
      break
    }
    default:
      break
  }
  // 流程任务明细校验（任务名称/执行步骤必填、params JSON 预检；任务表仅 APPROVE/CC/ROBORT 渲染）
  const taskErrors = taskTableRef.value?.validate() || []
  if (taskErrors.length > 0) {
    ElMessage.error(taskErrors[0])
    return
  }
  emit('confirm', nodeKey.value, buildConfigFields(nodeType.value, form))
  emit('update:visible', false)
}
</script>

<style scoped>
.field-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.field-tip.is-warning {
  color: var(--el-color-warning);
}

.ops-segment {
  margin-bottom: 16px;
}

.ops-segment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.ops-segment-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ops-switch-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ops-switch-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ops-switch-label {
  font-size: 13px;
}

.robort-tasks-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>

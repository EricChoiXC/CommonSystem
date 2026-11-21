<template>
  <div v-loading="loading" class="process-approval-panel">
    <!-- ============ 审批卡片：仅当前用户拥有可执行审批操作时可见 ============ -->
    <el-card v-if="operations.length" shadow="never" class="panel-card">
      <template #header>
        <div class="panel-card-title">审批</div>
      </template>
      <!-- 操作按钮组（panel.operations 驱动，按钮集 = 节点配置 ∩ 用户身份，顺序与名称以返回为准） -->
      <div class="op-btns">
        <el-button
          v-for="op in visibleOperations"
          :key="op.operateType"
          v-bind="buttonAttrs(op)"
          class="op-btn"
          :class="{ 'op-btn-active': form.operateType === op.operateType }"
          @click="toggleOperation(op)"
        >
          {{ operateButtonText(op) }}
        </el-button>
      </div>
      <el-form :model="form" label-width="90px">
        <!-- 条件字段区：按选中操作动态渲染（分册 §7.4），未选中操作时不渲染任何条件字段 -->
        <el-form-item v-if="needWorkitem" :label="workitemLabel" required>
          <el-select v-model="form.workitemId" :placeholder="`请选择${workitemLabel}`" style="width: 100%">
            <el-option
              v-for="w in workitemOptions"
              :key="w.fieldId"
              :label="workitemOptionLabel(w)"
              :value="w.fieldId"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isReject" label="驳回目标" required>
          <el-select v-model="form.rejectTargetKey" placeholder="请选择驳回目标节点" style="width: 100%">
            <el-option
              v-for="t in rejectTargetOptions"
              :key="t.nodeKey"
              :label="t.nodeName"
              :value="t.nodeKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isReject" label="重新提交流转方式" required>
          <el-radio-group v-model="form.resumeMode">
            <el-radio v-for="opt in RESUME_MODE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="isJump" label="目标节点" required>
          <el-select v-model="form.targetNodeId" placeholder="请选择跳转节点（起草 / 审批 / 人工决策）" style="width: 100%">
            <el-option
              v-for="n in jumpableNodes"
              :key="n.fieldId"
              :label="`${n.fieldNodeNo} ${n.fieldNodeName}`"
              :value="n.fieldId"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="flowChoices" label="人工决策流向">
          <el-select
            :model-value="draftFlowRow ? form.draftFlowChoice : form.flowChoice"
            clearable
            placeholder="请选择流向（可空）"
            style="width: 100%"
            @change="onFlowChoiceChange"
          >
            <el-option
              v-for="choice in flowChoices"
              :key="choice.nodeKey"
              :label="choice.nodeName"
              :value="choice.nodeKey"
            />
          </el-select>
          <div class="field-tip">不选择时按该人工决策节点的默认流向流转</div>
        </el-form-item>
        <el-form-item v-if="isCc" label="抄送人" required>
          <AddressBook
            v-model:field-id="form.ccToIds"
            v-model:field-name="form.ccToNames"
            org-type="ALL"
            multi
            show-role
          />
        </el-form-item>
        <el-form-item v-if="isTransfer" label="被转办人" required>
          <AddressBook
            v-model:field-id="form.transferToIds"
            v-model:field-name="form.transferToNames"
            org-type="PERSON"
            multi
          />
        </el-form-item>
        <el-form-item v-if="isChangeHandler" label="目标用户" required>
          <AddressBook
            v-model:field-id="form.changeToId"
            v-model:field-name="form.changeToName"
            org-type="PERSON"
          />
        </el-form-item>
        <el-form-item label="审批意见" :required="contentRequired">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="3"
            :placeholder="contentRequired ? '请输入审批意见（必填）' : '请输入审批意见（可空）'"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
        <div class="panel-tip">
          面板仅收集审批信息并按所选操作组装负载，由所在业务文档页面提交到对应模块后端后调用流程模块完成操作。
        </div>
      </el-form>
    </el-card>

    <!-- ============ 审批记录卡片 ============ -->
    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-card-title">审批记录</div>
      </template>
      <el-table v-if="auditNotes.length" :data="auditNotes" border stripe size="small" row-key="fieldId">
        <el-table-column label="处理人" min-width="100">
          <template #default="{ row }">{{ row.fieldHandlerName || row.fieldHandlerId || '-' }}</template>
        </el-table-column>
        <el-table-column label="处理操作" width="120">
          <template #default="{ row }">{{ handleTypeText(row.fieldHandleType) }}</template>
        </el-table-column>
        <el-table-column prop="fieldContent" label="审批内容" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fieldContent || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="140">
          <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无审批记录" :image-size="60" />
    </el-card>

    <!-- ============ 流程卡片（v1_7：上半只读流程图 + 下半节点列表辅助） ============ -->
    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-card-title">流程</div>
      </template>
      <!-- 只读流程图复用 P17-FE-005 只读画布：nodes/lines 均来自 panel 响应（实例连线快照与 load 同源） -->
      <ProcessFlowViewer :nodes="nodes" :lines="panelData.lines || []" :height="260" />
      <el-table v-if="nodes.length" :data="nodes" border stripe size="small" row-key="fieldId" class="node-table">
        <el-table-column prop="fieldNodeNo" label="节点编号" width="80" align="center" />
        <el-table-column prop="fieldNodeName" label="节点名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="节点类型" width="90">
          <template #default="{ row }">{{ nodeTypeText(row.fieldNodeType) }}</template>
        </el-table-column>
        <el-table-column label="节点状态" width="90">
          <template #default="{ row }">{{ nodeStatusText(row.fieldStatus) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无节点信息" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { loadProcessExamplePanel } from '@/api/process'
import { getDictOptions, getDictTextMap } from '@/utils/dict'
import AddressBook from '@/components/AddressBook.vue'
import ProcessFlowViewer from './ProcessFlowViewer.vue'

/**
 * 流程审批面板（公共出口组件，components/index.js 注册）
 *
 * 依据 docs/设计文档/sys/process/流程v1_7前端UI设计分册.md §7（P17-FE-006 改造，替换
 * 现状「下拉选操作 + OPERATE_LABELS 硬编码旧标识」形态）：
 * 三块卡片保持（审批 / 审批记录 / 流程）。「审批」卡片按 panel.operations 渲染**操作按钮组**
 * （按钮集 = 节点 operations 配置 ∩ 用户身份，由后端计算返回，顺序与名称以返回为准；
 * 未携带 operateName 时回退 §7.2 默认名称表），选中操作后按 §7.4 动态渲染条件字段区
 * （待处理工作项 / 驳回目标 / 重新提交流转方式 / 人工决策流向 / 抄送人 / 被转办人 /
 * 目标用户 / 目标节点），意见默认值按节点配置回填，五类操作提交前 ElMessageBox 二次确认。
 *
 * 面板不直接调用操作类接口：内部仅读面板数据（loadProcessExamplePanel）并收集审批信息，
 * 通过 submit 事件将负载交给宿主业务文档页，由宿主转发各自模块后端 → 流程模块完成操作
 * （业务模块 audit 转发通道，接口清单 §6.5）。宿主转发完成后须调用 completeSubmit(success)
 * 反馈：成功 → 面板重拉 panel 并清空表单；失败 → 保留已填内容（失败提示由宿主负责）。
 * 宿主亦可在单据刷新后调用 refresh() 重拉面板。
 *
 * 大写特权标识口径（接口清单 §5.4，panel.operations 实际下发形态）：ABANDON / JUMP /
 * CHANGE_HANDLER 按 admin_abandon / admin_jump / admin_change_handler 语义渲染与提交
 * （payload.operateType 归一为小写，对齐统一操作通道 14 类取值）；FINISH / PAUSE / RESUME
 * 无统一通道对应项，按钮照常渲染并**原样透传** operateType，由宿主按 adminOperate 通道
 * 转发或忽略（分册 §7.2：终审/暂停/恢复保持特权操作入口语义）。
 *
 * 已知缺口（登记，P17-INT-003 处置，见任务清单 4.35 交付记录）：
 * handler_transfer_retract 不渲染工作项选择、不传 workitemId（转办发起人被弃置工作项的
 * 选取口径待定）；大写 CHANGE_HANDLER 的工作项选项为本人工作项，管理员更换「他人」工作项
 * 时无数据源。抄送与 processed_* 两类的工作项选择为对齐 §6.2 必填参数表的契约自洽补充。
 *
 * @param {String} exampleId 流程实例 id
 * @emits submit { exampleId, operateType, workitemId, content, resumeMode, rejectTargetKey,
 *                 flowChoice, transferToIds, changeToId, targetNodeId, ccToIds }
 *         其中 operateType=drafter_submit 的事件为**起草提交流向同步**（面板不提供起草提交
 *         按钮，宿主缓存 flowChoice 随自身提交动作转发，不作为执行指令），其余事件为执行指令。
 */

const props = defineProps({
  exampleId: { type: String, required: true }
})
const emit = defineEmits(['submit'])

/** 14 类操作标识默认名称兜底映射（分册 §7.2 默认名称表；仅当 operations 未携带 operateName 时使用） */
const DEFAULT_OPERATE_NAMES = {
  drafter_submit: '起草人提交',
  drafter_cancel: '起草人取消',
  drafter_retract: '起草人收回',
  drafter_expedite: '起草人催办',
  handler_pass: '通过',
  handler_reject: '拒绝',
  handler_cc: '抄送',
  handler_transfer: '转办',
  handler_transfer_retract: '转办收回',
  processed_retract: '已审批收回',
  processed_expedite: '已审批人催办',
  admin_abandon: '管理员废弃',
  admin_jump: '管理员跳转',
  admin_change_handler: '管理员更换审批人',
  // 6 类管理员特权操作大写标识（接口清单 §5.4，名称与后端 SysProcessPanelOperationVo 对齐）
  JUMP: '管理员跳转',
  FINISH: '管理员终审通过',
  ABANDON: '管理员作废',
  PAUSE: '管理员暂停',
  RESUME: '管理员恢复',
  CHANGE_HANDLER: '管理员更换审批人'
}

/** 大写特权标识 → 统一操作通道（14 类小写）别名：宿主 audit 转发按 14 类小写取用（接口清单 §6.1/§6.2） */
const ADMIN_TYPE_ALIAS = {
  ABANDON: 'admin_abandon',
  JUMP: 'admin_jump',
  CHANGE_HANDLER: 'admin_change_handler'
}

/** 需要选择待处理工作项（PROCESSING 选项）的操作（分册 §7.4 表；handler_cc 为契约自洽补充，见下） */
const PROCESSING_WORKITEM_TYPES = new Set(['handler_pass', 'handler_reject', 'handler_transfer', 'handler_cc', 'admin_change_handler'])
/** 已审批收回：§6.2 必填 workitemId=本人 FINISH 工作项且所属节点仍 PROCESSING（引擎 processedRetract 口径） */
const RETRACT_WORKITEM_TYPES = new Set(['processed_retract'])
/** 已审批人催办：§6.2 必填 workitemId=本人 FINISH / PROCESSING 工作项（引擎 processedExpedite 口径） */
const EXPEDITE_WORKITEM_TYPES = new Set(['processed_expedite'])

/** 审批意见可空的操作（接口清单 §6.2 必填参数表；其余类型必填，未登记类型按管理员口径必填兜底） */
const CONTENT_OPTIONAL_TYPES = new Set(['drafter_submit', 'drafter_retract', 'handler_pass'])

/** 提交前二次确认（分册 §7.3-5 五类，提示影响面文案按 §7.2 表要点） */
const CONFIRM_TEXTS = {
  drafter_cancel: '取消即废弃，流程状态将转为 ABANDON（作废），确认执行？',
  drafter_retract: '收回后流程将转回起草节点（DRAFT），确认执行？',
  handler_transfer_retract: '收回后被转办工作项作废，工作项回到您（原审批人）名下，确认执行？',
  processed_retract: '已审批收回仅用于正常流转，不能用于已流过的后续节点；收回后需本人重新审批，确认执行？',
  admin_abandon: '废弃后流程状态将转为 ABANDON（作废），不可恢复，确认执行？'
}

/** 码值文本映射与选项（dict.process_audit_type / process_node_type / process_node_status / process_resume_mode） */
const HANDLE_TYPE_LABELS = ref({})
const NODE_TYPE_LABELS = ref({})
const NODE_STATUS_LABELS = ref({})
const RESUME_MODE_OPTIONS = ref([])

const panelData = ref({})
const loading = ref(false)
const submitting = ref(false)
const form = reactive({
  operateType: '',
  workitemId: null,
  rejectTargetKey: null,
  resumeMode: 'restart',
  targetNodeId: null,
  flowChoice: null,
  draftFlowChoice: null,
  ccToIds: '',
  ccToNames: '',
  transferToIds: '',
  transferToNames: '',
  changeToId: '',
  changeToName: '',
  content: ''
})
/** 上一次自动回填的操作默认意见（§7.3-3：意见框等于该值时切换操作允许覆盖，手输内容保留） */
const lastDefaultContent = ref('')

const operations = computed(() => panelData.value.operations || [])
const nodes = computed(() => panelData.value.nodes || [])
const auditNotes = computed(() => panelData.value.auditNotes || [])
const myWorkitems = computed(() => panelData.value.myWorkitems || [])
const processingWorkitems = computed(() => myWorkitems.value.filter((w) => w.fieldStatus === 'PROCESSING'))
/** admin_jump / 大写 JUMP 目标候选：起草 / 审批 / 人工决策节点（接口清单 §5.5 目标范围扩展） */
const jumpableNodes = computed(() =>
  nodes.value.filter((n) => ['APPROVE', 'DRAFT', 'MANUAL'].includes(n.fieldNodeType))
)

/** 按钮渲染集：drafter_submit 不渲染按钮（提交动作由宿主文档页承载，面板仅在其带 flowChoices 时渲染流向控件） */
const visibleOperations = computed(() => operations.value.filter((op) => op.operateType !== 'drafter_submit'))
const selectedOperation = computed(() => operations.value.find((op) => op.operateType === form.operateType) || null)
/** 选中操作的语义类型（大写特权标识归一为 14 类小写） */
const activeType = computed(() => normalizeType(form.operateType))

const needWorkitem = computed(
  () =>
    PROCESSING_WORKITEM_TYPES.has(activeType.value) ||
    RETRACT_WORKITEM_TYPES.has(activeType.value) ||
    EXPEDITE_WORKITEM_TYPES.has(activeType.value)
)
const isReject = computed(() => activeType.value === 'handler_reject')
const isJump = computed(() => activeType.value === 'admin_jump')
const isCc = computed(() => activeType.value === 'handler_cc')
const isTransfer = computed(() => activeType.value === 'handler_transfer')
const isChangeHandler = computed(() => activeType.value === 'admin_change_handler')
const contentRequired = computed(() =>
  selectedOperation.value ? !CONTENT_OPTIONAL_TYPES.has(activeType.value) : false
)

/**
 * 工作项下拉选项（按操作语义分三档，均为 panel.myWorkitems 数据源内过滤）：
 * 审批人类操作（含抄送）取本人 PROCESSING 项；已审批收回取本人 FINISH 且所属节点仍 PROCESSING 项
 * （与引擎 processedRetract 前置一致，防提交后 STATE_CONFLICT）；已审批人催办取本人 FINISH/PROCESSING 项。
 * 注：分册 §7.4 控件清单未覆盖抄送与 processed_* 两类，此为对齐接口清单 §6.2 必填参数表的
 * 契约自洽补充（引擎三方法均 requireOperateParam(workitemId)），偏差已在交付记录登记。
 */
const workitemOptions = computed(() => {
  if (RETRACT_WORKITEM_TYPES.has(activeType.value)) {
    const processingNodeIds = new Set(
      nodes.value.filter((n) => n.fieldStatus === 'PROCESSING').map((n) => n.fieldId)
    )
    return myWorkitems.value.filter((w) => w.fieldStatus === 'FINISH' && processingNodeIds.has(w.fieldNodeId))
  }
  if (EXPEDITE_WORKITEM_TYPES.has(activeType.value)) {
    return myWorkitems.value.filter((w) => w.fieldStatus === 'FINISH' || w.fieldStatus === 'PROCESSING')
  }
  return processingWorkitems.value
})

/** 工作项控件标签与占位：审批人类操作为「待处理工作项」（§7.4 冻结）；processed_* 取本人已办结/持有项，标签「本人工作项」 */
const workitemLabel = computed(() =>
  PROCESSING_WORKITEM_TYPES.has(activeType.value) ? '待处理工作项' : '本人工作项'
)

/** 工作项选项 label：PROCESSING 档按 §7.4 冻结为处理人姓名；processed_* 档补充节点名消歧（同处理人多节点并行） */
function workitemOptionLabel(w) {
  const handler = w.fieldHandlerName || w.fieldHandlerId || '-'
  if (PROCESSING_WORKITEM_TYPES.has(activeType.value)) {
    return handler
  }
  const node = nodes.value.find((n) => n.fieldId === w.fieldNodeId)
  return `${node?.fieldNodeName || w.fieldNodeId} · ${handler}`
}

/** 驳回目标候选（operation.rejectTargets，值=nodeKey）；候选意外为空时防御兜底起草节点（后端已保证非空） */
const rejectTargetOptions = computed(() => {
  if (!isReject.value) {
    return []
  }
  const targets = selectedOperation.value?.rejectTargets || []
  if (targets.length) {
    return targets
  }
  const draft = nodes.value.find((n) => n.fieldNodeType === 'DRAFT')
  return draft ? [{ nodeKey: draft.fieldNodeKey || draft.fieldId, nodeName: draft.fieldNodeName }] : []
})

/**
 * 起草提交流向决策行（operations 含带 flowChoices 的 drafter_submit 行）：流向控件独立于
 * 选中操作常驻渲染，选择变更经 submit 事件（operateType=drafter_submit）同步宿主。
 */
const draftFlowRow = computed(
  () => operations.value.find((op) => op.operateType === 'drafter_submit' && (op.flowChoices || []).length) || null
)
/** 通过流向决策行（选中的 handler_pass 行且带 flowChoices）：流向值随该操作提交负载携带 */
const passFlowRow = computed(() => {
  const op = selectedOperation.value
  return op && activeType.value === 'handler_pass' && (op.flowChoices || []).length ? op : null
})
/** 当前渲染的流向候选（起草行优先——两者状态前置互斥，实际不会并存） */
const flowChoices = computed(() => {
  if (draftFlowRow.value) {
    return draftFlowRow.value.flowChoices
  }
  return passFlowRow.value ? passFlowRow.value.flowChoices : null
})

/** 大写特权标识归一为 14 类小写语义类型（其余原样返回） */
function normalizeType(operateType) {
  return ADMIN_TYPE_ALIAS[operateType] || operateType
}

/** 按钮显示名：panel.operations 携带的节点配置操作名优先，缺省回退默认名称表，再回退标识原文 */
function operateButtonText(op) {
  return op.operateName || DEFAULT_OPERATE_NAMES[op.operateType] || op.operateType
}

/** 按钮形态（分册 §7.3-1）：主操作 primary，危险操作 danger plain，其余 default */
function buttonAttrs(op) {
  const type = normalizeType(op.operateType)
  if (type === 'handler_pass') {
    return { type: 'primary' }
  }
  if (type === 'handler_reject' || type === 'admin_abandon') {
    return { type: 'danger', plain: true }
  }
  return { type: 'default' }
}

/** 清空条件字段（保留意见与起草流向选择——两者不随操作切换丢失） */
function clearConditional() {
  form.workitemId = null
  form.rejectTargetKey = null
  form.resumeMode = 'restart'
  form.targetNodeId = null
  form.flowChoice = null
  form.ccToIds = ''
  form.ccToNames = ''
  form.transferToIds = ''
  form.transferToNames = ''
  form.changeToId = ''
  form.changeToName = ''
}

/**
 * 选中 / 取消选中操作按钮：选中后按 §7.3-3 回填意见默认值
 * （仅在意见框为空或等于上一操作默认值时自动覆盖，用户手输内容保留）。
 */
function toggleOperation(op) {
  submitting.value = false
  if (form.operateType === op.operateType) {
    // 再次点击已选按钮 = 取消选择：条件字段区隐藏（意见保留）
    form.operateType = ''
    clearConditional()
    return
  }
  form.operateType = op.operateType
  clearConditional()
  const nextDefault = op.defaultContent || ''
  if (!form.content || form.content === lastDefaultContent.value) {
    form.content = nextDefault
  }
  lastDefaultContent.value = nextDefault
}

/**
 * 人工决策流向变更：起草决策行（drafter_submit）变更即 emit submit 同步宿主
 * （面板不提供起草提交按钮，宿主缓存 flowChoice 随自身提交动作转发，该事件不是执行指令）；
 * 通过决策行（handler_pass）仅记录值，随提交负载携带。
 */
function onFlowChoiceChange(value) {
  if (draftFlowRow.value) {
    form.draftFlowChoice = value || null
    emit('submit', buildPayload('drafter_submit', { content: '', flowChoice: form.draftFlowChoice }))
  } else {
    form.flowChoice = value || null
  }
}

/**
 * 组装 submit 事件负载（与统一操作接口字段对齐，接口清单 §6.1）：不适字段一律 null，
 * 由宿主按业务模块转发契约取用。
 */
function buildPayload(operateType, overrides = {}) {
  const type = normalizeType(operateType)
  return {
    exampleId: props.exampleId,
    // 大写特权别名（ABANDON/JUMP/CHANGE_HANDLER）归一为 14 类小写（对齐统一操作通道），
    // FINISH/PAUSE/RESUME 与其余类型原样透传
    operateType: type,
    workitemId: needWorkitem.value ? form.workitemId : null,
    content: (form.content || '').trim(),
    resumeMode: type === 'handler_reject' ? form.resumeMode : null,
    rejectTargetKey: type === 'handler_reject' ? form.rejectTargetKey : null,
    flowChoice: type === 'handler_pass' ? form.flowChoice || null : null,
    transferToIds: type === 'handler_transfer' ? form.transferToIds : null,
    changeToId: type === 'admin_change_handler' ? form.changeToId : null,
    targetNodeId: type === 'admin_jump' ? form.targetNodeId : null,
    ccToIds: type === 'handler_cc' ? form.ccToIds : null,
    ...overrides
  }
}

/** 提交：按所选操作校验必填项 → 五类操作二次确认 → emit submit（清空与刷新由宿主 completeSubmit 反馈驱动） */
async function handleSubmit() {
  const op = selectedOperation.value
  if (!op) {
    ElMessage.warning('请先选择审批操作')
    return
  }
  if (needWorkitem.value && !form.workitemId) {
    ElMessage.warning(`请选择${workitemLabel.value}`)
    return
  }
  if (isReject.value) {
    if (!form.rejectTargetKey) {
      ElMessage.warning('请选择驳回目标节点')
      return
    }
    if (!form.resumeMode) {
      ElMessage.warning('请选择重新提交流转方式')
      return
    }
  }
  if (isJump.value && !form.targetNodeId) {
    ElMessage.warning('请选择跳转节点')
    return
  }
  if (isCc.value && !form.ccToIds) {
    ElMessage.warning('请选择抄送人（至少 1 人）')
    return
  }
  if (isTransfer.value && !form.transferToIds) {
    ElMessage.warning('请选择被转办人')
    return
  }
  if (isChangeHandler.value && !form.changeToId) {
    ElMessage.warning('请选择目标用户')
    return
  }
  if (contentRequired.value && !form.content.trim()) {
    ElMessage.warning('审批意见必填')
    return
  }
  const confirmText = CONFIRM_TEXTS[activeType.value]
  if (confirmText) {
    try {
      await ElMessageBox.confirm(confirmText, '操作确认', { type: 'warning' })
    } catch {
      // 用户取消确认：不提交，保留已填内容
      return
    }
  }
  submitting.value = true
  emit('submit', buildPayload(op.operateType))
}

/**
 * 宿主转发完成回调（expose）：success=true 重拉 panel 并清空表单；false 保留已填内容
 * （失败提示由宿主按接口 message 展示）。宿主未回调时面板保持填写态，可重置后重试。
 */
function completeSubmit(success) {
  submitting.value = false
  if (success) {
    resetForm()
    loadPanel()
  }
}

/** 重拉 panel 数据（expose，供宿主在单据刷新后调用） */
function refresh() {
  return loadPanel()
}

defineExpose({ refresh, completeSubmit })

function resetForm() {
  form.operateType = ''
  clearConditional()
  form.draftFlowChoice = null
  form.content = ''
  lastDefaultContent.value = ''
  submitting.value = false
}

async function loadPanel() {
  loading.value = true
  try {
    const res = await loadProcessExamplePanel(props.exampleId)
    panelData.value = res.data || {}
  } catch (error) {
    ElMessage.error(error.message || '审批面板数据加载失败')
  } finally {
    loading.value = false
  }
}

function handleTypeText(type) {
  return HANDLE_TYPE_LABELS.value[type] || type || '-'
}

function nodeTypeText(type) {
  return NODE_TYPE_LABELS.value[type] || type || '-'
}

function nodeStatusText(status) {
  return NODE_STATUS_LABELS.value[status] || status || '-'
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

onMounted(() => {
  loadDictOptions()
  loadPanel()
})

/** 加载流程码值文本映射与选项（dict.process_*）；失败时文本回退原值、驳回重新提交流转保留空选项 */
async function loadDictOptions() {
  try {
    const [handleTypeMap, nodeTypeMap, nodeStatusMap, resumeModeOptions] = await Promise.all([
      getDictTextMap('process_audit_type'),
      getDictTextMap('process_node_type'),
      getDictTextMap('process_node_status'),
      getDictOptions('process_resume_mode')
    ])
    HANDLE_TYPE_LABELS.value = handleTypeMap
    NODE_TYPE_LABELS.value = nodeTypeMap
    NODE_STATUS_LABELS.value = nodeStatusMap
    RESUME_MODE_OPTIONS.value = resumeModeOptions
  } catch {
    HANDLE_TYPE_LABELS.value = {}
    NODE_TYPE_LABELS.value = {}
    NODE_STATUS_LABELS.value = {}
    RESUME_MODE_OPTIONS.value = []
  }
}
</script>

<style scoped>
.process-approval-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel-card-title {
  font-size: 14px;
  font-weight: 600;
}

/* 操作按钮组：数量多时 flex-wrap 自动换行（分册 §7.3-1） */
.op-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

/* 相邻按钮间距交给 gap，避免 Element Plus 兄弟按钮默认 margin 叠加 */
.op-btn {
  margin-left: 0;
}

/* 选中操作按钮：描边高亮 + 加粗（形态仍按分册 §7.3-1 冻结的 type/plain 规则） */
.op-btn-active {
  font-weight: 600;
  box-shadow: 0 0 0 1px var(--el-button-border-color, var(--el-border-color)) inset;
}

.field-tip {
  width: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.node-table {
  margin-top: 12px;
}

.panel-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
</style>

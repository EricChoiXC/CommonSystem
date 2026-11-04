<template>
  <div class="process-approval-panel">
    <!-- ============ 审批卡片：仅当前用户拥有可执行审批操作时可见 ============ -->
    <el-card v-if="operations.length" shadow="never" class="panel-card">
      <template #header>
        <div class="panel-card-title">审批</div>
      </template>
      <el-form :model="form" label-width="90px">
        <el-form-item label="审批操作" required>
          <el-select v-model="form.operateType" placeholder="请选择审批操作" style="width: 100%" @change="onOperateChange">
            <el-option v-for="o in operableOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="needWorkitem" label="工作项" required>
          <el-select v-model="form.workitemId" placeholder="请选择待处理工作项" style="width: 100%">
            <el-option
              v-for="w in processingWorkitems"
              :key="w.fieldId"
              :label="w.fieldHandlerName || w.fieldHandlerId"
              :value="w.fieldId"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.operateType === 'HANDLER_RETURN'" label="重新提交后流转方式" required>
          <el-radio-group v-model="form.resumeMode">
            <el-radio v-for="opt in RESUME_MODE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.operateType === 'ADMIN_JUMP'" label="跳转节点" required>
          <el-select v-model="form.targetNodeId" placeholder="请选择跳转节点（含起草节点和审批节点）" style="width: 100%">
            <el-option
              v-for="n in jumpableNodes"
              :key="n.fieldId"
              :label="`${n.fieldNodeNo} ${n.fieldNodeName}`"
              :value="n.fieldId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="审批意见" required>
          <el-input v-model="form.content" type="textarea" :rows="3" placeholder="请输入审批意见（必填）" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
        <div class="panel-tip">
          面板仅收集审批信息，由所在业务文档页面提交到对应模块后端后调用流程模块完成操作。
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

    <!-- ============ 流程卡片 ============ -->
    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-card-title">流程</div>
      </template>
      <el-table v-if="nodes.length" :data="nodes" border stripe size="small" row-key="fieldId">
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
import { ElMessage } from 'element-plus'
import { loadProcessExamplePanel } from '@/api/process'
import { getDictOptions, getDictTextMap } from '@/utils/dict'

/**
 * 流程审批面板（可嵌入组件）
 *
 * 依据 artificial/docs/设计文档/sys/process/流程核心业务.md「流程审批面板」：
 * 供插入业务文档页面中进行审批操作，提供除「起草保存」「起草提交」之外的审批处理。
 * 面板不提供接口调用：内部仅读取面板数据（panel 接口）并收集审批信息，
 * 通过 submit 事件将填写内容交给宿主页面，由宿主提交给各自模块后端，再调用流程模块服务完成操作。
 *
 * @param {String} exampleId 流程实例 id
 * @emits submit { exampleId, operateType, workitemId, content, resumeMode, targetNodeId }
 */

const props = defineProps({
  exampleId: { type: String, required: true }
})
const emit = defineEmits(['submit'])

const OPERATE_LABELS = {
  HANDLER_PASS: '通过',
  HANDLER_RETURN: '驳回',
  DRAFTER_BACK: '收回',
  DRAFTER_ABANDON: '废弃',
  ADMIN_JUMP: '管理员跳转',
  ADMIN_FINISH: '管理员办结',
  ADMIN_ABANDON: '管理员作废',
  ADMIN_PAUSE: '管理员暂停',
  ADMIN_RESUME: '管理员恢复'
}

/** 码值文本映射与选项（dict.process_audit_type / process_node_type / process_node_status / process_resume_mode） */
const HANDLE_TYPE_LABELS = ref({})
const NODE_TYPE_LABELS = ref({})
const NODE_STATUS_LABELS = ref({})
const RESUME_MODE_OPTIONS = ref([])

const panelData = ref({})
const loading = ref(false)
const submitting = ref(false)
const form = reactive({ operateType: '', workitemId: null, resumeMode: 'restart', targetNodeId: null, content: '' })

const operations = computed(() => panelData.value.operations || [])
const nodes = computed(() => panelData.value.nodes || [])
const auditNotes = computed(() => panelData.value.auditNotes || [])
const myWorkitems = computed(() => panelData.value.myWorkitems || [])
const processingWorkitems = computed(() => myWorkitems.value.filter((w) => w.fieldStatus === 'PROCESSING'))
const jumpableNodes = computed(() => nodes.value.filter((n) => n.fieldNodeType === 'APPROVE' || n.fieldNodeType === 'DRAFT'))

/** 可选择的审批操作：需要工作项的操作仅在有处理中工作项时提供 */
const operableOptions = computed(() => {
  const hasWorkitem = processingWorkitems.value.length > 0
  return operations.value
    .filter((op) => (op === 'HANDLER_PASS' || op === 'HANDLER_RETURN') ? hasWorkitem : true)
    .map((op) => ({ value: op, label: OPERATE_LABELS[op] || op }))
})

/** 是否需要选择工作项（通过/驳回针对具体工作项） */
const needWorkitem = computed(() => form.operateType === 'HANDLER_PASS' || form.operateType === 'HANDLER_RETURN')

function onOperateChange() {
  if (!needWorkitem.value) {
    form.workitemId = null
  }
  if (form.operateType !== 'HANDLER_RETURN') {
    form.resumeMode = 'restart'
  }
  if (form.operateType !== 'ADMIN_JUMP') {
    form.targetNodeId = null
  }
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

function handleSubmit() {
  if (!form.operateType) {
    ElMessage.warning('请选择审批操作')
    return
  }
  if (needWorkitem.value && !form.workitemId) {
    ElMessage.warning('请选择待处理工作项')
    return
  }
  if (form.operateType === 'HANDLER_RETURN' && !form.resumeMode) {
    ElMessage.warning('请选择重新提交后流转方式')
    return
  }
  if (form.operateType === 'ADMIN_JUMP' && !form.targetNodeId) {
    ElMessage.warning('请选择跳转节点')
    return
  }
  if (!form.content || !form.content.trim()) {
    ElMessage.warning('审批意见必填')
    return
  }
  submitting.value = true
  try {
    emit('submit', {
      exampleId: props.exampleId,
      operateType: form.operateType,
      workitemId: needWorkitem.value ? form.workitemId : null,
      content: form.content.trim(),
      resumeMode: form.operateType === 'HANDLER_RETURN' ? form.resumeMode : null,
      targetNodeId: form.operateType === 'ADMIN_JUMP' ? form.targetNodeId : null
    })
    resetForm()
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.operateType = ''
  form.workitemId = null
  form.resumeMode = 'restart'
  form.targetNodeId = null
  form.content = ''
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

.panel-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
</style>

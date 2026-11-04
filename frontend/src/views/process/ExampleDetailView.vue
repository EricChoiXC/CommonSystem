<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">
          流程实例详情
          <el-tag v-if="detail.example" size="small" :type="statusTag(detail.example.fieldStatus)" style="margin-left: 8px">
            {{ statusText(detail.example.fieldStatus) }}
          </el-tag>
        </span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
          <el-button v-if="canAdmin" type="danger" plain @click="openOperateDialog">特权操作</el-button>
        </div>
      </div>
    </template>

    <div v-loading="loading">
      <!-- ============ 流程实例基本信息 ============ -->
      <div class="section-block">
        <div class="section-title">流程实例</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="流程名称">{{ detail.example?.fieldName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="流程编号">{{ detail.example?.fieldCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所属模块">{{ modelLabel(detail.example?.fieldModelName) }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ detail.example?.fieldCreatorName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.example?.fieldCreateTime) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatTime(detail.example?.fieldFinishTime) }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ detail.example?.currentNodeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="流程状态">{{ statusText(detail.example?.fieldStatus) }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- ============ 流程实例节点 ============ -->
      <div class="section-block">
        <div class="section-title">流程实例节点</div>
        <el-table :data="detail.nodes || []" border stripe row-key="fieldId" size="small">
          <el-table-column prop="fieldNodeNo" label="节点编号" width="90" align="center" />
          <el-table-column prop="fieldNodeName" label="节点名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="节点类型" width="110">
            <template #default="{ row }">
              <el-tag :type="nodeTypeTag(row.fieldNodeType)" size="small">{{ nodeTypeText(row.fieldNodeType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="节点状态" width="110">
            <template #default="{ row }">
              <el-tag :type="nodeStatusTag(row.fieldStatus)" size="small">{{ nodeStatusText(row.fieldStatus) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- ============ 工作项 ============ -->
      <div class="section-block">
        <div class="section-title">工作项</div>
        <el-table :data="detail.workitems || []" border stripe row-key="fieldId" size="small">
          <el-table-column prop="fieldNodeName" label="所属节点" min-width="120" show-overflow-tooltip />
          <el-table-column label="处理人" min-width="110">
            <template #default="{ row }">{{ row.fieldHandlerName || row.fieldHandlerId || '-' }}</template>
          </el-table-column>
          <el-table-column label="期望处理人" min-width="110">
            <template #default="{ row }">{{ row.fieldExpectHandlerName || row.fieldExpectHandlerId || '-' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="150">
            <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
          </el-table-column>
          <el-table-column label="完成时间" min-width="150">
            <template #default="{ row }">{{ formatTime(row.fieldFinishTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="workitemStatusTag(row.fieldStatus)" size="small">{{ workitemStatusText(row.fieldStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="重新提交流转方式" width="140">
            <template #default="{ row }">
              {{ resumeModeText(row.resumeMode) }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- ============ 审批记录 ============ -->
      <div class="section-block">
        <div class="section-title">审批记录</div>
        <el-table :data="detail.auditNotes || []" border stripe row-key="fieldId" size="small">
          <el-table-column label="处理人" min-width="110">
            <template #default="{ row }">{{ row.fieldHandlerName || row.fieldHandlerId || '-' }}</template>
          </el-table-column>
          <el-table-column label="处理操作" width="130">
            <template #default="{ row }">
              <el-tag size="small">{{ handleTypeText(row.fieldHandleType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="fieldContent" label="审批内容" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.fieldContent || '-' }}</template>
          </el-table-column>
          <el-table-column label="时间" min-width="150">
            <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- ============ 特权操作弹窗 ============ -->
    <el-dialog v-model="operateDialogVisible" title="特权操作" width="480px" :close-on-click-modal="false">
      <el-form :model="operateForm" label-width="90px">
        <el-form-item label="操作类型" required>
          <el-select v-model="operateForm.operateType" placeholder="请选择操作类型" style="width: 100%">
            <el-option v-for="o in OPERATE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="operateForm.operateType === 'JUMP'" label="跳转节点" required>
          <el-select v-model="operateForm.targetNodeId" placeholder="请选择跳转节点（含起草节点和审批节点）" style="width: 100%">
            <el-option
              v-for="n in jumpableNodes"
              :key="n.fieldId"
              :label="`${n.fieldNodeNo} ${n.fieldNodeName}（${nodeTypeText(n.fieldNodeType)}）`"
              :value="n.fieldId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="意见" required>
          <el-input v-model="operateForm.content" type="textarea" :rows="3" placeholder="请输入意见（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="operateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="operating" @click="submitOperate">提交</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { hasAuth } from '@/utils/auth'
import { adminOperateProcessExample, loadProcessExample, queryProcessExampleModels } from '@/api/process'
import { getDictOptions, getDictTextMap } from '@/utils/dict'

/**
 * 流程实例详情页：显示流程实例、流程实例节点、工作项、审批记录信息；
 * 仅 SYS_PROCESS_EXAMPLE_ADMIN 级权限用户显示「特权操作」按钮（跳转/终审通过/作废/暂停/恢复，
 * 跳转节点包含起草节点和审批节点，意见必填），提交后生成审批记录并刷新。
 * 依据 artificial/docs/设计文档/sys/process/流程核心业务.md「流程实例详情」。
 * 状态/节点/审批记录等文本（dict.process_*）由 dict 模块提供。
 */

/** 特权操作选项（dict.process_admin_operate） */
const OPERATE_OPTIONS = ref([])
/** 码值文本映射（dict.process_example_status / process_node_type / process_node_status / process_workitem_status / process_audit_type / process_resume_mode） */
const STATUS_MAP = ref({})
const NODE_TYPE_MAP = ref({})
const NODE_STATUS_MAP = ref({})
const WORKITEM_STATUS_MAP = ref({})
const HANDLE_TYPE_MAP = ref({})
const RESUME_MODE_TEXT = ref({})

/** 特权操作所需权限：与后端 example.adminOperate 鉴权一致 */
const AUTH_EXAMPLE_ADMIN = ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_EXAMPLE_ADMIN']

const route = useRoute()
const router = useRouter()

/** 特权操作按钮可见性（预判，最终权限由后端 ProcessResolver 兜底） */
const canAdmin = computed(() => hasAuth(AUTH_EXAMPLE_ADMIN))

const loading = ref(false)
const detail = ref({})
const models = ref([])

async function loadDetail() {
  const fieldId = route.params.id
  if (!fieldId) {
    ElMessage.error('缺少流程实例 id')
    goBack()
    return
  }
  loading.value = true
  try {
    const res = await loadProcessExample(fieldId)
    detail.value = res.data || {}
  } catch (error) {
    ElMessage.error(error.message || '流程实例加载失败')
    goBack()
  } finally {
    loading.value = false
  }
}

async function loadModels() {
  try {
    const res = await queryProcessExampleModels()
    models.value = res.data || []
  } catch (error) {
    // 所属模块翻译失败不阻断详情展示
  }
}

function modelLabel(fieldModelName) {
  if (!fieldModelName) return '-'
  const found = models.value.find((m) => m.fieldModelName === fieldModelName)
  return found ? found.name : fieldModelName
}

/* ============ 特权操作 ============ */

const operateDialogVisible = ref(false)
const operating = ref(false)
const operateForm = reactive({ operateType: '', targetNodeId: null, content: '' })

/** 跳转节点：包含起草节点和审批节点 */
const jumpableNodes = computed(() =>
  (detail.value.nodes || []).filter((n) => n.fieldNodeType === 'APPROVE' || n.fieldNodeType === 'DRAFT')
)

function openOperateDialog() {
  operateForm.operateType = ''
  operateForm.targetNodeId = null
  operateForm.content = ''
  operateDialogVisible.value = true
}

async function submitOperate() {
  if (!operateForm.operateType) {
    ElMessage.warning('请选择操作类型')
    return
  }
  if (operateForm.operateType === 'JUMP' && !operateForm.targetNodeId) {
    ElMessage.warning('请选择跳转节点')
    return
  }
  if (!operateForm.content || !operateForm.content.trim()) {
    ElMessage.warning('意见必填')
    return
  }
  operating.value = true
  try {
    await adminOperateProcessExample({
      fieldId: route.params.id,
      operateType: operateForm.operateType,
      targetNodeId: operateForm.operateType === 'JUMP' ? operateForm.targetNodeId : null,
      content: operateForm.content.trim()
    })
    ElMessage.success('操作成功')
    operateDialogVisible.value = false
    loadDetail()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    operating.value = false
  }
}

/* ============ 展示工具 ============ */

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function statusText(status) {
  return STATUS_MAP.value[status] || status || '-'
}

function statusTag(status) {
  switch (status) {
    case 'PROCESSING':
      return 'warning'
    case 'FINISH':
      return 'success'
    case 'DRAFT':
      return 'info'
    default:
      return 'info'
  }
}

function nodeTypeText(type) {
  return NODE_TYPE_MAP.value[type] || type || '-'
}

function nodeTypeTag(type) {
  switch (type) {
    case 'START':
      return 'success'
    case 'END':
      return 'danger'
    case 'DRAFT':
      return 'info'
    case 'APPROVE':
      return 'warning'
    default:
      return 'info'
  }
}

function nodeStatusText(status) {
  return NODE_STATUS_MAP.value[status] || status || '-'
}

function nodeStatusTag(status) {
  switch (status) {
    case 'PROCESSING':
      return 'warning'
    case 'FINISH':
      return 'success'
    case 'PASS':
      return 'info'
    case 'REJECT':
      return 'danger'
    default:
      return 'info'
  }
}

function workitemStatusText(status) {
  return WORKITEM_STATUS_MAP.value[status] || status || '-'
}

function workitemStatusTag(status) {
  switch (status) {
    case 'PROCESSING':
      return 'warning'
    case 'FINISH':
      return 'success'
    case 'ABANDON':
      return 'info'
    case 'PAUSE':
      return 'danger'
    default:
      return 'info'
  }
}

function handleTypeText(type) {
  return HANDLE_TYPE_MAP.value[type] || type || '-'
}

function resumeModeText(mode) {
  if (!mode) return '-'
  return RESUME_MODE_TEXT.value[mode] || mode
}

function goBack() {
  router.push('/processExample')
}

onMounted(() => {
  loadDictOptions()
  loadModels()
  loadDetail()
})

/** 加载流程码值选项与文本映射（dict.process_*）；失败时文本回退原值、特权操作无选项 */
async function loadDictOptions() {
  try {
    const [operateOptions, statusMap, nodeTypeMap, nodeStatusMap, workitemStatusMap, handleTypeMap, resumeModeMap] =
      await Promise.all([
        getDictOptions('process_admin_operate'),
        getDictTextMap('process_example_status'),
        getDictTextMap('process_node_type'),
        getDictTextMap('process_node_status'),
        getDictTextMap('process_workitem_status'),
        getDictTextMap('process_audit_type'),
        getDictTextMap('process_resume_mode')
      ])
    OPERATE_OPTIONS.value = operateOptions
    STATUS_MAP.value = statusMap
    NODE_TYPE_MAP.value = nodeTypeMap
    NODE_STATUS_MAP.value = nodeStatusMap
    WORKITEM_STATUS_MAP.value = workitemStatusMap
    HANDLE_TYPE_MAP.value = handleTypeMap
    RESUME_MODE_TEXT.value = resumeModeMap
  } catch {
    OPERATE_OPTIONS.value = []
    STATUS_MAP.value = {}
    NODE_TYPE_MAP.value = {}
    NODE_STATUS_MAP.value = {}
    WORKITEM_STATUS_MAP.value = {}
    HANDLE_TYPE_MAP.value = {}
    RESUME_MODE_TEXT.value = {}
  }
}
</script>

<script>
export default { name: 'ExampleDetailView' }
</script>

<style scoped>
.section-block {
  margin-bottom: 20px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  padding: 6px 0 10px;
}
</style>

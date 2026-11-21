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
      <!-- ============ 流程实例基本信息（form-page.css 详情网格，D-05 归一） ============ -->
      <div class="section-block">
        <div class="section-title">流程实例</div>
        <div class="detail-grid">
          <div class="detail-item">
            <div class="detail-label">流程名称</div>
            <div class="detail-value">{{ detail.example?.fieldName || '-' }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">流程编号</div>
            <div class="detail-value">{{ detail.example?.fieldCode || '-' }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">所属模块</div>
            <div class="detail-value">{{ modelLabel(detail.example?.fieldModelName) }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">发起人</div>
            <div class="detail-value">{{ detail.example?.fieldCreatorName || '-' }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">创建时间</div>
            <div class="detail-value">{{ formatTime(detail.example?.fieldCreateTime) }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">完成时间</div>
            <div class="detail-value">{{ formatTime(detail.example?.fieldFinishTime) }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">当前节点</div>
            <div class="detail-value">{{ detail.example?.currentNodeName || '-' }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">流程状态</div>
            <div class="detail-value">
              <el-tag size="small" :type="statusTag(detail.example?.fieldStatus)">
                {{ statusText(detail.example?.fieldStatus) }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- ============ 流程图（只读渲染，节点按状态着色，分册 §6.1/§6.2） ============ -->
      <div class="section-block">
        <div class="section-title">流程图</div>
        <ProcessFlowViewer
          v-if="loaded"
          :nodes="detail.nodes || []"
          :lines="detail.lines || []"
          :workitems="detail.workitems || []"
          :height="360"
        />
      </div>

      <!-- ============ 流程实例节点 ============ -->
      <div class="section-block">
        <div class="section-title">流程实例节点</div>
        <el-table :data="detail.nodes || []" border stripe row-key="fieldId" size="small">
          <el-table-column prop="fieldNodeNo" label="节点编号" width="90" align="center" />
          <el-table-column label="节点键" width="90" align="center">
            <template #default="{ row }">{{ row.fieldNodeKey || '-' }}</template>
          </el-table-column>
          <el-table-column prop="fieldNodeName" label="节点名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="节点类型" width="110">
            <template #default="{ row }">
              <el-tag v-bind="nodeTypeTag(row.fieldNodeType)" size="small">{{ nodeTypeText(row.fieldNodeType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="节点状态" width="110">
            <template #default="{ row }">
              <el-tag v-bind="nodeStatusTag(row.fieldStatus)" size="small">{{ nodeStatusText(row.fieldStatus) }}</el-tag>
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
          <el-table-column label="来源工作项" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ sourceWorkitemText(row) }}</template>
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

    <!-- ============ 特权操作弹窗（复用 ProcessAdminOperateDialog，v1_7 P17-FE-007 抽取为流程模块公共组件） ============ -->
    <ProcessAdminOperateDialog
      v-model="operateDialogVisible"
      :field-id="route.params.id"
      :nodes="detail.nodes || []"
      :workitems="detail.workitems || []"
      @success="loadDetail"
    />
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { hasAuth } from '@/utils/auth'
import { loadProcessExample, queryProcessExampleModels } from '@/api/process'
import { getDictTextMap } from '@/utils/dict'
import ProcessFlowViewer from '@/components/process/ProcessFlowViewer.vue'
import ProcessAdminOperateDialog from '@/components/process/ProcessAdminOperateDialog.vue'
import { NODE_TYPE_LABELS } from '@/components/process/designer/flowNodes'

/**
 * 流程实例详情页：流程实例信息（form-page.css 详情网格）、只读流程图（ProcessFlowViewer，
 * 节点按状态着色 WAITING/PROCESSING/FINISH/PASS/REJECT，图数据来自 load 响应 nodes + lines，
 * 无连线数据降级按节点序号排布）、流程实例节点/工作项/审批记录三张明细表；
 * 仅 SYS_PROCESS_EXAMPLE_ADMIN 级权限用户显示「特权操作」按钮（跳转/终审通过/作废/暂停/
 * 恢复/更换审批人，弹窗复用 ProcessAdminOperateDialog——跳转节点为起草/审批/人工决策节点，
 * 更换审批人选当前节点 PROCESSING 工作项 + 地址本单选人员，意见必填），提交后刷新详情。
 * 依据 docs/设计文档/sys/process/流程核心业务.md「流程实例详情」、
 * docs/设计文档/sys/process/流程v1_7前端UI设计分册.md §6。
 * 状态/节点/审批记录等文本（dict.process_*）由 dict 模块提供，缺失时回退原值。
 */

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
/** 详情是否加载成功（流程图区首次渲染守卫，避免加载期闪空态） */
const loaded = ref(false)
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
    loaded.value = true
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

/* ============ 特权操作（弹窗复用 ProcessAdminOperateDialog，提交成功后刷新详情） ============ */

const operateDialogVisible = ref(false)

function openOperateDialog() {
  operateDialogVisible.value = true
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
  return NODE_TYPE_MAP.value[type] || NODE_TYPE_LABELS[type] || type || '-'
}

/** 节点类型 tag（10 类全集；plain 浅色区分次要类型，文本列兜底歧义） */
function nodeTypeTag(type) {
  switch (type) {
    case 'START':
      return { type: 'success' }
    case 'DRAFT':
      return { type: 'primary' }
    case 'END':
      return { type: 'danger' }
    case 'APPROVE':
      return { type: 'warning' }
    case 'CC':
      return { type: 'success', effect: 'plain' }
    case 'CONDITION':
      return { type: 'warning', effect: 'plain' }
    case 'FORK':
      return { type: 'primary', effect: 'plain' }
    case 'JOIN':
      return { type: 'info', effect: 'plain' }
    case 'ROBORT':
      return { type: 'info', effect: 'plain' }
    case 'MANUAL':
      return { type: 'danger', effect: 'plain' }
    default:
      return { type: 'info' }
  }
}

function nodeStatusText(status) {
  return NODE_STATUS_MAP.value[status] || status || '-'
}

/** 节点状态 tag（分册 §6.2 着色表：WAITING 灰/PROCESSING 蓝/FINISH 绿/PASS 青浅/REJECT 红） */
function nodeStatusTag(status) {
  switch (status) {
    case 'PROCESSING':
      return { type: 'primary' }
    case 'FINISH':
      return { type: 'success' }
    case 'PASS':
      return { type: 'success', effect: 'plain' }
    case 'REJECT':
      return { type: 'danger' }
    default:
      return { type: 'info' }
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

/** 来源工作项（转办/收回链路 A→B 可追溯）：展示「来源节点 · 来源处理人」可读文本，不跳转 */
function sourceWorkitemText(row) {
  const fromId = row.fieldFromWorkitemId || row.fromWorkitemId
  if (!fromId) return '-'
  const source = (detail.value.workitems || []).find((w) => w && w.fieldId === fromId)
  if (!source) return fromId
  const node = source.fieldNodeName || '-'
  const handler = source.fieldHandlerName || source.fieldHandlerId || '-'
  return `${node} · ${handler}`
}

function goBack() {
  router.push('/processExample')
}

onMounted(() => {
  loadDictOptions()
  loadModels()
  loadDetail()
})

/** 加载流程码值文本映射（dict.process_*）；失败时文本回退原值，不阻塞详情展示
 *  （特权操作弹窗的选项与兜底由 ProcessAdminOperateDialog 组件内部自行加载） */
async function loadDictOptions() {
  try {
    const [statusMap, nodeTypeMap, nodeStatusMap, workitemStatusMap, handleTypeMap, resumeModeMap] =
      await Promise.all([
        getDictTextMap('process_example_status'),
        getDictTextMap('process_node_type'),
        getDictTextMap('process_node_status'),
        getDictTextMap('process_workitem_status'),
        getDictTextMap('process_audit_type'),
        getDictTextMap('process_resume_mode')
      ])
    STATUS_MAP.value = statusMap
    NODE_TYPE_MAP.value = nodeTypeMap
    NODE_STATUS_MAP.value = nodeStatusMap
    WORKITEM_STATUS_MAP.value = workitemStatusMap
    HANDLE_TYPE_MAP.value = handleTypeMap
    RESUME_MODE_TEXT.value = resumeModeMap
  } catch {
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

/* 页面容器、详情网格（detail-grid / detail-item / is-full）、header 按钮区
   等公共样式见 styles/form-page.css（全局引入），不在此重复定义 */
</style>

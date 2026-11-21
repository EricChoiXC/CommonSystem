<template>
  <el-dialog v-model="visible" title="特权操作" width="520px" :close-on-click-modal="false">
    <el-form :model="form" label-width="100px">
      <el-form-item label="操作类型" required>
        <el-select v-model="form.operateType" placeholder="请选择操作类型" style="width: 100%">
          <el-option v-for="o in OPERATE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.operateType === 'JUMP'" label="跳转节点" required>
        <el-select v-model="form.targetNodeId" placeholder="请选择跳转节点（起草/审批/人工决策节点）" style="width: 100%">
          <el-option
            v-for="n in jumpableNodes"
            :key="n.fieldId"
            :label="`${n.fieldNodeNo} ${n.fieldNodeName}（${nodeTypeText(n.fieldNodeType)}）`"
            :value="n.fieldId"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.operateType === 'CHANGE_HANDLER'" label="待处理工作项" required>
        <el-select
          v-model="form.workitemId"
          :placeholder="pendingWorkitems.length ? '请选择待处理工作项' : '当前节点暂无处理中工作项'"
          style="width: 100%"
        >
          <el-option
            v-for="w in pendingWorkitems"
            :key="w.fieldId"
            :label="w.fieldHandlerName || w.fieldHandlerId"
            :value="w.fieldId"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.operateType === 'CHANGE_HANDLER'" label="更换为" required>
        <AddressBook
          v-model:field-id="form.changeToId"
          v-model:field-name="form.changeToName"
          org-type="PERSON"
        />
      </el-form-item>
      <el-form-item label="意见" required>
        <el-input v-model="form.content" type="textarea" :rows="3" placeholder="请输入意见（必填）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="operating" @click="submitOperate">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { adminOperateProcessExample } from '@/api/process'
import { getDictOptions, getDictTextMap } from '@/utils/dict'
import AddressBook from '@/components/AddressBook.vue'
import { NODE_TYPE_LABELS } from '@/components/process/designer/flowNodes'

/**
 * 流程实例特权操作弹窗（v1_7 P17-FE-007 自 ExampleDetailView 抽取为流程模块公共组件，
 * 实例详情页与流程监控页共用同一弹窗形态，仅流程模块内部消费、不进 components/index.js 出口）。
 * 操作类型：跳转/终审通过/作废/暂停/恢复/更换审批人（dict.process_admin_operate，
 * 缺项时按 v1_7 六类兜底补全）；跳转节点限起草/审批/人工决策节点；更换审批人 =
 * PROCESSING 工作项下拉（label 处理人姓名）+ 目标用户地址本单选（orgType="PERSON"）；
 * 意见必填。提交走 adminOperateProcessExample 通道，成功后 emit success 由宿主自行刷新。
 * 依据 docs/设计文档/sys/process/流程v1_7前端UI设计分册.md §6.4/§8.3、
 * docs/设计文档/sys/process/流程v1_7接口清单.md §5.5。
 */

const props = defineProps({
  /** 弹窗显示（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 被操作的流程实例 id（adminOperate 提交与最终鉴权 documentId） */
  fieldId: { type: String, default: '' },
  /** 实例节点集合（load 响应 nodes；JUMP 候选节点与换人候选工作项的所属节点过滤） */
  nodes: { type: Array, default: () => [] },
  /** 实例工作项集合（load 响应 workitems；CHANGE_HANDLER 候选下拉） */
  workitems: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'success'])

/** 特权操作选项（dict.process_admin_operate；缺项时经 mergeAdminOperateOptions 兜底补全） */
const OPERATE_OPTIONS = ref([])
/** 节点类型文本映射（dict.process_node_type；JUMP 候选节点标签展示用） */
const NODE_TYPE_MAP = ref({})

/** 管理员特权操作兜底选项（v1_7 六类；dict 缺项/加载失败时补全，保证弹窗可用） */
const ADMIN_OPERATE_FALLBACKS = [
  { value: 'JUMP', label: '跳转' },
  { value: 'FINISH', label: '终审通过' },
  { value: 'ABANDON', label: '作废' },
  { value: 'PAUSE', label: '暂停' },
  { value: 'RESUME', label: '恢复' },
  { value: 'CHANGE_HANDLER', label: '更换审批人' },
]

const operating = ref(false)
const form = reactive({
  operateType: '',
  targetNodeId: null,
  workitemId: '',
  changeToId: '',
  changeToName: '',
  content: ''
})

/** 弹窗显示双向绑定：宿主以 v-model 控制开闭 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 弹窗每次打开重置表单（与抽取前宿主「先重置再显示」的 openOperateDialog 行为一致）
watch(
  () => props.modelValue,
  (val) => {
    if (val) resetForm()
  }
)

/** 跳转节点：v1_7 扩展为起草节点、审批节点、人工决策节点（接口清单 §5.5 目标范围） */
const jumpableNodes = computed(() =>
  (props.nodes || []).filter((n) => ['DRAFT', 'APPROVE', 'MANUAL'].includes(n.fieldNodeType))
)

/** 更换审批人的待处理工作项：当前 PROCESSING 节点的 PROCESSING 工作项（label 展示处理人姓名） */
const pendingWorkitems = computed(() => {
  const processingNodeIds = new Set(
    (props.nodes || []).filter((n) => n && n.fieldStatus === 'PROCESSING').map((n) => n.fieldId)
  )
  return (props.workitems || []).filter(
    (w) => w && w.fieldStatus === 'PROCESSING' && processingNodeIds.has(w.fieldNodeId)
  )
})

function resetForm() {
  form.operateType = ''
  form.targetNodeId = null
  form.workitemId = ''
  form.changeToId = ''
  form.changeToName = ''
  form.content = ''
}

async function submitOperate() {
  if (!props.fieldId) {
    ElMessage.error('缺少流程实例 id')
    return
  }
  if (!form.operateType) {
    ElMessage.warning('请选择操作类型')
    return
  }
  if (form.operateType === 'JUMP' && !form.targetNodeId) {
    ElMessage.warning('请选择跳转节点')
    return
  }
  if (form.operateType === 'CHANGE_HANDLER' && !form.workitemId) {
    ElMessage.warning('请选择待处理工作项')
    return
  }
  if (form.operateType === 'CHANGE_HANDLER' && !form.changeToId) {
    ElMessage.warning('请选择更换后的审批人')
    return
  }
  if (!form.content || !form.content.trim()) {
    ElMessage.warning('意见必填')
    return
  }
  operating.value = true
  try {
    const payload = {
      fieldId: props.fieldId,
      operateType: form.operateType,
      content: form.content.trim()
    }
    if (form.operateType === 'JUMP') {
      payload.targetNodeId = form.targetNodeId
    }
    if (form.operateType === 'CHANGE_HANDLER') {
      payload.workitemId = form.workitemId
      payload.changeToId = form.changeToId
    }
    await adminOperateProcessExample(payload)
    ElMessage.success('操作成功')
    visible.value = false
    emit('success')
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    operating.value = false
  }
}

function nodeTypeText(type) {
  return NODE_TYPE_MAP.value[type] || NODE_TYPE_LABELS[type] || type || '-'
}

onMounted(() => {
  loadDictOptions()
})

/** 加载特权操作选项（dict.process_admin_operate，六类兜底补全）与节点类型文本映射（dict.process_node_type）；
 *  失败时选项走兜底、文本回退原值，不阻塞弹窗打开 */
async function loadDictOptions() {
  try {
    const [operateOptions, nodeTypeMap] = await Promise.all([
      getDictOptions('process_admin_operate'),
      getDictTextMap('process_node_type')
    ])
    OPERATE_OPTIONS.value = mergeAdminOperateOptions(operateOptions)
    NODE_TYPE_MAP.value = nodeTypeMap
  } catch {
    OPERATE_OPTIONS.value = mergeAdminOperateOptions([])
    NODE_TYPE_MAP.value = {}
  }
}

/** 管理员特权操作选项兜底：dict 加载结果在前，缺登记的 v1_7 六类按默认中文名补后 */
function mergeAdminOperateOptions(options) {
  const known = new Set((options || []).map((o) => o.value))
  return [...(options || []), ...ADMIN_OPERATE_FALLBACKS.filter((o) => !known.has(o.value))]
}
</script>

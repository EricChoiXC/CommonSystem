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

    <!-- ============ 下部：画布设计器三段（物料面板 / LogicFlow 画布 / 属性区） ============
         节点配置（双击节点或【配置节点】）与分支配置（点击分支出边连线或【配置分支】）
         由画布内置 FlowNodeConfigDialog / FlowBranchConfigDialog 承载，页面不再自建弹窗；
         详情模式 readonly：不挂物料面板与编辑交互，仅渲染只读画布。 -->
    <div class="designer-section">
      <div class="designer-section-header">
        <span class="designer-section-title">流程设计</span>
      </div>
      <FlowDesignerCanvas
        ref="designerRef"
        :nodes="graphNodes"
        :lines="graphLines"
        :readonly="isDetail"
        :field-model-name="form.fieldModelName"
        :field-options="modelFieldsDict"
        :ops-options="modelOpsDict"
        :formula-vars="formulaVars"
      />
    </div>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { markTabToClose } from '@/utils/tabs'
import { ElMessage, ElMessageBox } from 'element-plus'
import FlowDesignerCanvas from '@/components/process/designer/FlowDesignerCanvas.vue'
import { hasAuth } from '@/utils/auth'
import { getInitID } from '@/api/common'
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
 * 顶部操作按钮沿用现状口径：关闭、编辑（仅详情）、启停用（非新建）、保存（新建/编辑）；
 * 上部显示模板名称/编号/所属模块/备注（校验与提交语义不变），
 * 下部为画布设计器三段（左侧节点物料面板 + 中部 LogicFlow 画布 + 右侧属性区），
 * 节点配置与分支配置经画布内置弹窗（FlowNodeConfigDialog / FlowBranchConfigDialog）维护。
 * 保存链路：画布客户端图校验（G1~G7 可前端判定子集，错误逐条展示）→
 * 画布组装 nodes + lines 全量提交（新建 save / 编辑 update PATCH 含 fieldId；
 * 节点键归一 START=N1/DRAFT=N2/END=N3/中间 N4+ 顺延、field_no 开始1、起草2、中间3…n、
 * 结束n+1，坐标不落库不提交，服务端 G1~G10 兜底）→ 成功后关标签回列表。
 * 画布图改动置脏：【关闭】与路由离开时二次确认「流程图已修改，确认离开？」。
 * 依据 docs/设计文档/sys/process/流程核心业务.md「模板详情/模板编辑 页面」、
 * docs/设计文档/sys/process/流程v1_7前端UI设计分册.md §4（画布交互）、§4.6（graph 转换与保存数据组装）。
 */

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

/**
 * 公式上下文变量声明（FormulaDesigner availableVars 契约：元素 { code, type?, label? }）。
 * 由分类 fields 字典换算：code=字段属性名（公式占位符 ${code}）、label=中文描述；
 * fields 字典未携带类型声明，type 缺省由 FormulaDesigner 按宽松模式处理（权威校验在后端）。
 */
const formulaVars = computed(() =>
  Object.entries(modelFieldsDict.value).map(([code, label]) => ({ code, label }))
)

const form = reactive({
  fieldId: '',
  fieldName: '',
  fieldCode: '',
  fieldModelName: '',
  fieldRemark: '',
  fieldEnable: true,
  fieldVersion: null
})

/** 画布图数据：load 响应 nodes/lines 平铺 VO 直传（无坐标，画布按拓扑自动分层布局）；
 * 新建传空数组，由画布空图播种固定三节点链（开始 → 起草 → 结束） */
const graphNodes = ref([])
const graphLines = ref([])

/** 画布组件实例（expose：validateGraph / getSavePayload / isDirty / resetDirty / fit） */
const designerRef = ref(null)

const pageTitle = computed(() => {
  if (isCreate.value) return '新建流程模板'
  if (isEdit.value) return '编辑流程模板'
  return '流程模板详情'
})

/* ============ 初始化 ============ */

/** 新建初始化：预取对象 ID，所属模块来自列表页路由 query；节点链由画布空图播种 */
async function initForCreate() {
  try {
    const res = await getInitID()
    form.fieldId = (res && res.data) || ''
  } catch {
    // 预取失败不阻塞：后端 save 会自动生成 ID
  }
  form.fieldModelName = route.query.modelName || ''
  modelLabel.value = route.query.modelLabel || ''
}

/** 清空画布图数据（新建模式重建时使用；空数组触发画布重新播种固定三节点链） */
function resetGraph() {
  graphNodes.value = []
  graphLines.value = []
}

// 二次从流程模板列表页进入时实例复用、onMounted 不会重跑，
// 监听 query 按带入模块重建新建表单（重置所属模块并清空画布重新播种）
watchRouteQuery(route, (q) => {
  if (!isCreate.value || !('modelName' in q)) return
  initForCreate()
  loadModelConfig()
  resetGraph()
})

/** 编辑/详情初始化：按路由 id 加载数据回填（nodes + lines 直传画布渲染） */
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
    graphNodes.value = row.nodes || []
    graphLines.value = row.lines || []
  } catch (error) {
    ElMessage.error(error.message || '加载数据失败')
    goBack()
  }
}

onMounted(async () => {
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

/* ============ 脏数据保护 ============ */

/** 画布脏数据离开确认（流程图已修改时二次确认，分册 §4.4） */
async function confirmDirtyLeave() {
  if (!designerRef.value?.isDirty()) {
    return true
  }
  try {
    await ElMessageBox.confirm('流程图已修改，确认离开？', '离开确认', { type: 'warning' })
    return true
  } catch {
    return false
  }
}

/** 路由离开保护：画布有未保存改动时二次确认（确认离开即放弃改动，复位脏标记放行） */
onBeforeRouteLeave(async () => {
  const confirmed = await confirmDirtyLeave()
  if (confirmed) {
    designerRef.value?.resetDirty()
  }
  return confirmed
})

/* ============ 保存 / 启停用 / 关闭 ============ */

/** 详情 → 编辑：切换到编辑路由（同一卡片标签跟随；详情画布只读恒不置脏，无需离开确认） */
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
  // 客户端图校验先行（N1→N3 连通、无孤立节点、出边约束、条件分支默认边唯一、
  // FORK/JOIN 成对、人工决策默认流向、审批操作必含一个 handler_pass），逐条展示；
  // 其余（G8 任务注册、G9/G10 候选合法性等）交服务端统一校验兜底，按 message 展示
  const graphErrors = designerRef.value ? designerRef.value.validateGraph() : []
  if (graphErrors.length) {
    graphErrors.forEach((message) => ElMessage.error(message))
    return
  }
  saving.value = true
  try {
    // nodes + lines 全量组装（节点键/展示序号由画布按保存口径归一；坐标不提交）
    const { nodes, lines } = designerRef.value.getSavePayload()
    const body = {
      fieldId: form.fieldId,
      fieldName: form.fieldName.trim(),
      fieldCode: form.fieldCode.trim() || null,
      fieldModelName: form.fieldModelName,
      fieldRemark: form.fieldRemark.trim() || null,
      fieldEnable: form.fieldEnable,
      nodes,
      lines
    }
    if (isEdit.value) {
      await updateProcessTemplate(body)
      ElMessage.success('保存成功')
    } else {
      await saveProcessTemplate(body)
      ElMessage.success('创建成功')
    }
    // 复位脏标记后再关标签回列表，避免路由离开守卫二次确认
    designerRef.value?.resetDirty()
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

/** 关闭：画布有未保存改动时二次确认，确认后关标签回列表 */
async function goBack() {
  if (!(await confirmDirtyLeave())) {
    return
  }
  designerRef.value?.resetDirty()
  router.push('/processTemplate')
}
</script>

<script>
export default { name: 'TemplateFormView' }
</script>

<style scoped>
/* 下部画布设计器区（头部按钮区/表单布局样式复用全局 form-page.css，不在此重复定义） */
.designer-section {
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 12px;
}

.designer-section-header {
  margin-bottom: 10px;
}

.designer-section-title {
  font-size: 14px;
  font-weight: 600;
}
</style>

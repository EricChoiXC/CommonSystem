<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="760px"
    :close-on-click-modal="false"
    @update:model-value="handleVisibleChange"
    @open="handleOpen"
  >
    <el-form class="fork-form" label-width="110px" @submit.prevent>
      <el-form-item label="节点名称" required>
        <el-input v-model="form.nodeName" maxlength="100" placeholder="请输入并行开始节点名称" />
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

    <div class="branch-detail">
      <div class="branch-detail-title">并行分支明细表</div>
      <el-table
        :data="workingBranches"
        border
        size="small"
        empty-text="暂无并行分支：请先在画布上从本节点锚点拉出出边连线"
      >
        <el-table-column label="序号" width="96" align="center">
          <template #default="{ $index }">
            <span class="branch-no">并行分支{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分支名称" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.branchName" size="small" maxlength="200" placeholder="可空，最长 200 字" />
          </template>
        </el-table-column>
        <el-table-column label="流向节点" min-width="110">
          <template #default="{ row }">
            <span class="target-name" :title="row.targetNodeName || row.targetNodeKey">
              {{ row.targetNodeName || row.targetNodeKey || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="分支启动条件" min-width="230">
          <template #default="{ row }">
            <FlowFormulaDialog
              v-model="row.condition"
              expected-return-type="BOOLEAN"
              :formula-vars="formulaVars"
              placeholder="未配置（空 = 无条件激活）"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ $index }">
            <el-button link type="primary" size="small" :disabled="$index === 0" @click="moveBranch($index, -1)">
              上移
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="$index === workingBranches.length - 1"
              @click="moveBranch($index, 1)"
            >
              下移
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="branch-detail-tip">
        行序即分支展示顺序（序号 1..n 由「上移 / 下移」决定）；分支与出边一一对应，删除分支请在画布上删除对应出边连线后重新打开本弹窗。
      </div>
    </div>

    <template #footer>
      <el-button @click="handleVisibleChange(false)">关闭</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 流程设计器 · 并行分支节点配置弹窗（V1.12 改版，分册 §4.5-2）
 *
 * 并行分支（FORK）的分支配置入口由「出边连线」改为「并行开始节点本身」（与条件分支
 * V1.11 节点级化同口径）：双击节点 / 右侧【配置节点】/ 选中并行出边【配置并行分支】
 * 打开本弹窗，弹窗内聚合该节点全部出边统一配置。分支数据仍落**连线表**
 * （field_branch_name / field_branch_order / field_condition），不在节点配置中冗余——
 * 接口与表结构不变。原 FlowBranchConfigDialog（连线弹窗）随本改版移除。
 *
 * 面板结构（分册 §4.5-2 冻结）：
 *   - 节点名称：可编辑，必填；【确定】经 confirm 回抛，画布统一回写节点文本；
 *   - 汇聚模式 joinMode（all/any）与无可流转分支时行为 noBranchMode（error/continue，
 *     T-09 定稿键名）：原 FlowNodeConfigDialog「基本（汇聚配置）」标签字段并入本弹窗；
 *   - 并行分支明细表：行序 1..n 由「上移 / 下移」决定，每行含分支名称（行内输入）、
 *     流向节点（只读显示出边目标）、分支启动条件（FlowFormulaDialog：只读输入框 +
 *     【编辑】打开公共 FormulaDesigner，BOOLEAN；**可空**，空 = 无条件激活）。
 *
 * 交互契约：
 *   - 弹窗编辑「工作副本」，【确定】校验后经 confirm 事件整体回抛
 *     { nodeKey, nodeName, joinMode, noBranchMode, branches }，画布侧统一回写
 *     节点名称/汇聚配置与出边 properties；【关闭】/ 遮罩 / ESC 放弃本次编辑；
 *   - 校验：节点名称必填；启动条件可空（无默认分支、无条件必填语义）；
 *   - 弹窗不提供删除分支：分支与出边一一对应，删除分支=在画布上删除对应出边连线
 *     （分支源节点最后一条出边由保存期 G3 校验拦截）。
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import FlowFormulaDialog from './FlowFormulaDialog.vue'
import { JOIN_MODE_OPTIONS, NO_BRANCH_MODE_OPTIONS } from './nodeConfig'

const props = defineProps({
  /** 弹窗显隐（v-model:visible） */
  visible: { type: Boolean, default: false },
  /** 并行开始节点键（N4…），用于标题与 confirm 回执 */
  nodeKey: { type: String, default: '' },
  /** 并行开始节点名称（打开时快照，弹窗内可编辑） */
  nodeName: { type: String, default: '' },
  /** 汇聚模式回显（joinMode：all | any，缺省 all） */
  joinMode: { type: String, default: 'all' },
  /** 无可流转分支时行为回显（noBranchMode：error | continue，缺省 error） */
  noBranchMode: { type: String, default: 'error' },
  /**
   * 该节点的全部出边分支数据（画布侧收集，按 branchOrder 升序由弹窗自排，元素：
   * { edgeId, branchName, branchOrder, condition, targetNodeKey, targetNodeName }）
   */
  branches: { type: Array, default: () => [] },
  /** 公式上下文变量声明（元素 { code, type?, label? }，宿主页传分类文档实体字段字典） */
  formulaVars: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:visible', 'confirm'])

/** 工作副本（【确定】前不触碰画布） */
const workingBranches = ref([])
/** 弹窗表单：节点名称 + 汇聚配置 */
const form = reactive({ nodeName: '', joinMode: 'all', noBranchMode: 'error' })

const dialogTitle = computed(() => `并行分支配置 - ${props.nodeName || '并行开始'}（${props.nodeKey || '-'}）`)

/** 打开时快照：节点名称/汇聚配置回显 + 分支数据按 branchOrder 升序（空值排后）、序号归一 1..n */
function handleOpen() {
  const orderOf = (value) =>
    value !== null && value !== undefined && Number.isFinite(Number(value)) ? Number(value) : Number.MAX_SAFE_INTEGER
  workingBranches.value = (props.branches || [])
    .map((branch) => ({
      edgeId: branch.edgeId || '',
      branchName: branch.branchName || '',
      branchOrder: branch.branchOrder,
      condition: branch.condition || '',
      targetNodeKey: branch.targetNodeKey || '',
      targetNodeName: branch.targetNodeName || '',
    }))
    .sort((a, b) => orderOf(a.branchOrder) - orderOf(b.branchOrder))
    .map((branch, index) => ({ ...branch, branchOrder: index + 1 }))
  form.nodeName = props.nodeName || ''
  form.joinMode = props.joinMode === 'any' ? 'any' : 'all'
  form.noBranchMode = props.noBranchMode === 'continue' ? 'continue' : 'error'
}

/** 上移/下移：出边集合内交换相邻顺序并即时归一序号 */
function moveBranch(index, direction) {
  const target = index + direction
  if (target < 0 || target >= workingBranches.value.length) {
    return
  }
  const list = workingBranches.value
  const temp = list[target]
  list[target] = list[index]
  list[index] = temp
  list.forEach((branch, position) => {
    branch.branchOrder = position + 1
  })
}

function handleVisibleChange(value) {
  emit('update:visible', value)
}

/** 确定：客户端校验 → confirm 回抛（画布侧统一回写节点配置与全部出边分支属性） */
function handleConfirm() {
  const nodeName = String(form.nodeName || '').trim()
  if (!nodeName) {
    ElMessage.warning('节点名称不能为空')
    return
  }
  emit('update:visible', false)
  emit('confirm', {
    nodeKey: props.nodeKey,
    nodeName,
    joinMode: form.joinMode === 'any' ? 'any' : 'all',
    noBranchMode: form.noBranchMode === 'continue' ? 'continue' : 'error',
    branches: workingBranches.value.map((branch) => ({
      edgeId: branch.edgeId,
      branchOrder: branch.branchOrder,
      branchName: branch.branchName,
      condition: branch.condition,
    })),
  })
}
</script>

<style scoped>
.fork-form {
  margin-bottom: 4px;
}

.field-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.branch-detail-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.branch-detail-tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.branch-no {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.target-name {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  color: var(--el-text-color-regular);
}
</style>

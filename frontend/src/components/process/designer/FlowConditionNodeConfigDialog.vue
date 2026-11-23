<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="760px"
    :close-on-click-modal="false"
    @update:model-value="handleVisibleChange"
    @open="handleOpen"
  >
    <el-form class="condition-form" label-width="96px" @submit.prevent>
      <el-form-item label="节点名称" required>
        <el-input v-model="form.nodeName" maxlength="100" placeholder="请输入条件分支节点名称" />
      </el-form-item>
      <el-form-item label="默认条件分支" required>
        <el-select
          v-model="form.defaultEdgeId"
          style="width: 100%"
          placeholder="请选择默认条件分支（必选）"
          @change="handleDefaultChange"
        >
          <el-option
            v-for="(row, index) in workingBranches"
            :key="row.edgeId"
            :label="defaultOptionLabel(row, index)"
            :value="row.edgeId"
          />
        </el-select>
        <div class="field-tip">必选：所有条件分支判断公式均为否时按默认分支流出；切换时自动清除旧默认标记。</div>
      </el-form-item>
    </el-form>

    <div class="branch-detail">
      <div class="branch-detail-title">条件分支明细表</div>
      <el-table
        :data="workingBranches"
        border
        size="small"
        empty-text="暂无条件分支：请先在画布上从本节点锚点拉出出边连线"
      >
        <el-table-column label="序号" width="96" align="center">
          <template #default="{ $index }">
            <span class="branch-no">条件分支{{ $index + 1 }}</span>
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
        <el-table-column label="条件分支判断公式" min-width="230">
          <template #default="{ row }">
            <FlowFormulaDialog
              v-model="row.condition"
              expected-return-type="BOOLEAN"
              :formula-vars="formulaVars"
              placeholder="未配置条件公式"
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
        行序即引擎求值顺序（序号 1..n 由「上移 / 下移」决定）；分支与出边一一对应，删除分支请在画布上删除对应出边连线后重新打开本弹窗。
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
 * 流程设计器 · 条件分支节点配置弹窗（V1.11 改版，分册 §4.5-1）
 *
 * 条件分支（CONDITION）的分支配置入口由「出边连线」改为「条件分支节点本身」：
 * 双击节点 / 右侧【配置节点】/ 选中条件出边【配置条件分支】打开本弹窗，弹窗内聚合
 * 该节点全部出边统一配置。分支数据仍落**连线表**（field_branch_name / field_branch_order /
 * field_condition / field_is_default），不在节点配置中冗余——接口与表结构不变。
 *
 * 面板结构（分册 §4.5-1 冻结）：
 *   - 节点名称：可编辑，必填；【确定】经 confirm 回抛，画布统一回写节点文本；
 *   - 默认条件分支：必选下拉（选项=该节点全部出边），同一节点出边中有且仅有一条默认，
 *     切换自动清除旧值（对齐原分支弹窗互斥口径）；
 *   - 条件分支明细表：行序即引擎求值顺序（1..n 由「上移 / 下移」决定），每行含
 *     分支名称（行内输入）、流向节点（只读显示出边目标）、条件分支判断公式
 *     （FlowFormulaDialog：只读输入框 +【编辑】打开公共 FormulaDesigner，BOOLEAN）。
 *
 * 交互契约：
 *   - 弹窗编辑「工作副本」，【确定】校验后经 confirm 事件整体回抛
 *     { nodeKey, nodeName, branches }，画布侧统一回写节点名称与出边 properties；
 *     【关闭】/ 遮罩 / ESC 放弃本次编辑；
 *   - 校验：节点名称必填；默认分支有且仅有一条（「条件分支节点必须指定且仅指定一条默认分支」）；
 *     非默认出边必填条件公式（默认兜底出边可空，分册 §4.5-1 口径）；
 *   - 弹窗不提供删除分支：分支与出边一一对应，删除分支=在画布上删除对应出边连线
 *     （分支源节点最后一条出边由保存期 G3 校验拦截）。
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import FlowFormulaDialog from './FlowFormulaDialog.vue'

const props = defineProps({
  /** 弹窗显隐（v-model:visible） */
  visible: { type: Boolean, default: false },
  /** 条件分支节点键（N4…），用于标题与 confirm 回执 */
  nodeKey: { type: String, default: '' },
  /** 条件分支节点名称（打开时快照，弹窗内可编辑） */
  nodeName: { type: String, default: '' },
  /**
   * 该节点的全部出边分支数据（画布侧收集，按 branchOrder 升序由弹窗自排，元素：
   * { edgeId, branchName, branchOrder, condition, isDefault, targetNodeKey, targetNodeName }）
   */
  branches: { type: Array, default: () => [] },
  /** 公式上下文变量声明（元素 { code, type?, label? }，宿主页传分类文档实体字段字典） */
  formulaVars: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:visible', 'confirm'])

/** 工作副本（【确定】前不触碰画布） */
const workingBranches = ref([])
/** 弹窗表单：节点名称 + 默认分支（值=出边 edgeId） */
const form = reactive({ nodeName: '', defaultEdgeId: '' })

const dialogTitle = computed(() => `条件分支配置 - ${props.nodeName || '条件分支'}（${props.nodeKey || '-'}）`)

/** 打开时快照分支数据：按 branchOrder 升序（空值排后），序号归一为 1..n 展示；
 *  默认分支归一为至多一条（首个默认保留，余者清标记，兜底畸形数据），下拉回显 */
function handleOpen() {
  const orderOf = (value) =>
    value !== null && value !== undefined && Number.isFinite(Number(value)) ? Number(value) : Number.MAX_SAFE_INTEGER
  const source = (props.branches || [])
    .map((branch) => ({
      edgeId: branch.edgeId || '',
      branchName: branch.branchName || '',
      branchOrder: branch.branchOrder,
      condition: branch.condition || '',
      isDefault: branch.isDefault === true,
      targetNodeKey: branch.targetNodeKey || '',
      targetNodeName: branch.targetNodeName || '',
    }))
    .sort((a, b) => orderOf(a.branchOrder) - orderOf(b.branchOrder))
    .map((branch, index) => ({ ...branch, branchOrder: index + 1 }))
  const defaultBranch = source.find((branch) => branch.isDefault)
  source.forEach((branch) => {
    branch.isDefault = branch === defaultBranch
  })
  workingBranches.value = source
  form.nodeName = props.nodeName || ''
  form.defaultEdgeId = defaultBranch ? defaultBranch.edgeId : ''
}

/** 默认条件分支下拉选项文案：分支名称（空时取「条件分支N」）+ 流向节点 */
function defaultOptionLabel(row, index) {
  const name = String(row.branchName || '').trim() || `条件分支${index + 1}`
  const target = String(row.targetNodeName || row.targetNodeKey || '').trim()
  return target ? `${name}（流向：${target}）` : name
}

/** 默认分支互斥单选：切「是」自动清同节点其余出边默认标记 */
function handleDefaultChange(edgeId) {
  workingBranches.value.forEach((branch) => {
    branch.isDefault = branch.edgeId === edgeId
  })
}

/** 上移/下移：出边集合内交换相邻顺序并即时归一序号（引擎求值顺序） */
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

/** 确定：客户端校验 → confirm 回抛（画布侧统一回写节点名称与全部出边分支属性） */
function handleConfirm() {
  if (workingBranches.value.length === 0) {
    ElMessage.warning('该节点暂无出边分支，请先在画布上从本节点锚点拉出连线')
    return
  }
  const nodeName = String(form.nodeName || '').trim()
  if (!nodeName) {
    ElMessage.warning('节点名称不能为空')
    return
  }
  const defaultCount = workingBranches.value.filter((branch) => branch.isDefault).length
  if (defaultCount !== 1) {
    ElMessage.error('条件分支节点必须指定且仅指定一条默认分支')
    return
  }
  // 非默认出边必须配置条件公式（默认兜底出边可空）
  const missing = workingBranches.value.filter((branch) => !branch.isDefault && !String(branch.condition || '').trim())
  if (missing.length > 0) {
    ElMessage.error('条件分支的非默认出边必须配置条件公式（默认分支可空）')
    return
  }
  emit('update:visible', false)
  emit('confirm', {
    nodeKey: props.nodeKey,
    nodeName,
    branches: workingBranches.value.map((branch) => ({
      edgeId: branch.edgeId,
      branchOrder: branch.branchOrder,
      branchName: branch.branchName,
      condition: branch.condition,
      isDefault: branch.isDefault === true,
    })),
  })
}
</script>

<style scoped>
.condition-form {
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

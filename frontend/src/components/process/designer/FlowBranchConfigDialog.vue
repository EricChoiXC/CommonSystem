<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="680px"
    :close-on-click-modal="false"
    @update:model-value="handleVisibleChange"
    @open="handleOpen"
  >
    <el-form class="branch-form" label-width="84px">
      <el-form-item label="分支名称">
        <el-input
          v-model="activeBranch.branchName"
          maxlength="200"
          placeholder="可空，最长 200 字"
          clearable
        />
      </el-form-item>
      <el-form-item label="分支序号">
        <span class="branch-order-text">{{ activeBranch.branchOrder }}</span>
        <span class="branch-order-tip">（只读，由「上移 / 下移」决定，引擎按序号升序求值）</span>
      </el-form-item>
      <el-form-item label="条件公式">
        <div class="condition-row">
          <el-input :model-value="activeBranch.condition" readonly placeholder="未配置条件公式" />
          <el-button type="primary" plain @click="formulaVisible = true">编辑</el-button>
        </div>
        <div class="condition-tip">
          {{
            nodeType === 'CONDITION'
              ? '条件分支出边必填（默认分支可空，作为兜底）；公式须为判断公式，最终权威校验在后端'
              : '并行分支的启动条件，可空（空 = 无条件激活）'
          }}
        </div>
      </el-form-item>
      <el-form-item v-if="nodeType === 'CONDITION'" label="默认分支">
        <el-radio-group :model-value="activeBranch.isDefault ? 'yes' : 'no'" @update:model-value="handleDefaultChange">
          <el-radio value="yes">是</el-radio>
          <el-radio value="no">否</el-radio>
        </el-radio-group>
        <span class="condition-tip">同一条件分支节点的出边中有且仅有一条默认分支（切换时自动清除旧值）</span>
      </el-form-item>
    </el-form>

    <div class="branch-detail">
      <div class="branch-detail-title">分支明细（同一节点的全部出边，按分支序号升序）</div>
      <el-table :data="workingBranches" border stripe size="small" highlight-current-row @row-click="handleRowClick">
        <el-table-column label="序号" prop="branchOrder" width="60" align="center" />
        <el-table-column label="分支名称" prop="branchName" min-width="120">
          <template #default="{ row }">
            <span :title="row.branchName">{{ row.branchName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="条件公式（摘要）" min-width="180">
          <template #default="{ row }">
            <span class="condition-summary" :title="row.condition">{{ row.condition || '（空）' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" size="small" type="warning">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center">
          <template #default="{ row, $index }">
            <el-button
              link
              type="primary"
              size="small"
              :disabled="$index === 0"
              @click.stop="moveBranch($index, -1)"
            >
              上移
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="$index === workingBranches.length - 1"
              @click.stop="moveBranch($index, 1)"
            >
              下移
            </el-button>
            <el-button link type="primary" size="small" @click.stop="selectBranch(row)">配置</el-button>
            <el-button link type="danger" size="small" @click.stop="removeBranch($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="handleVisibleChange(false)">关闭</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </template>

    <!-- 条件公式编辑：复用公共公式设计器（分册 §5.4 路径 A；条件分支/并行启动条件均传 BOOLEAN） -->
    <FormulaDesigner
      v-model:visible="formulaVisible"
      :expression="activeBranch.condition || ''"
      expected-return-type="BOOLEAN"
      :available-vars="formulaVars"
      @confirm="handleFormulaConfirm"
    />
  </el-dialog>
</template>

<script setup>
/**
 * 流程设计器 · 分支配置弹窗（v1_7 / P17-FE-002，分册 §4.5 冻结）
 *
 * 适用：CONDITION（条件分支）/ FORK（并行开始）节点的出边连线，数据落连线表
 * （field_branch_name / field_branch_order / field_condition / field_is_default），
 * 不在节点配置中冗余（分册 §4.5 开篇口径）。
 *
 * 交互契约（分册 §4.5）：
 *   - 弹窗编辑「工作副本」，【确定】校验后经 confirm 事件整体回抛，画布侧统一回写
 *     画布 edges properties（对齐分册 §5.x「确定执行客户端校验并回写、不立即调接口」）；
 *     【关闭】/ 遮罩 / ESC 放弃本次编辑；
 *   - 分支序号只读，由「上移 / 下移」在出边集合内交换相邻两条的顺序决定（引擎求值顺序）；
 *   - 条件公式经只读输入框 +【编辑】打开公共公式设计器（expectedReturnType=BOOLEAN），
 *     回填后权威校验在后端；
 *   - 默认分支仅条件分支显示，同一节点出边中有且仅有一条（互斥，切换自动清旧值）；
 *     【确定】校验不满足时提示「条件分支节点必须指定且仅指定一条默认分支」。
 *
 * 自定项登记（分册未冻结细节，P17-DOC-010 走查时可调）：
 *   - 「条件分支出边必填」按分册 §4.5 示例解释为：非默认出边必填、默认（兜底）出边可空
 *     （示例中默认分支条件公式为空）；
 *   - 删除分支后剩余 0 条时拦截（分支源节点至少保留一条流出连线，G3 出边约束）；
 *   - 弹窗内删除连线在【确定】时经 removedEdgeIds 回抛画布执行，取消即放弃。
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import FormulaDesigner from '@/components/formula/FormulaDesigner.vue'

const props = defineProps({
  /** 弹窗显隐（v-model:visible） */
  visible: { type: Boolean, default: false },
  /** 分支源节点键（N4…），仅用于标题与 confirm 回执 */
  nodeKey: { type: String, default: '' },
  /** 分支源节点名称 */
  nodeName: { type: String, default: '' },
  /** 分支源节点类型：CONDITION | FORK */
  nodeType: { type: String, default: 'CONDITION' },
  /**
   * 该节点的全部出边分支数据（画布侧收集，元素：
   * { edgeId, branchName, branchOrder, condition, isDefault }）
   */
  branches: { type: Array, default: () => [] },
  /** 公式上下文变量声明（元素 { code, type?, label? }，宿主页传分类文档实体字段字典） */
  formulaVars: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:visible', 'confirm'])

/** 工作副本（【确定】前不触碰画布） */
const workingBranches = ref([])
/** 弹窗内删除的连线（confirm 时回抛画布执行删除） */
const removedEdgeIds = ref([])
/** 当前编辑分支（顶部表单绑定项，指向工作副本元素） */
const activeBranch = reactive({ edgeId: '', branchName: '', branchOrder: 1, condition: '', isDefault: false })
/** 公式设计器显隐 */
const formulaVisible = ref(false)

const dialogTitle = computed(() => {
  const typeLabel = props.nodeType === 'FORK' ? '并行分支' : '条件分支'
  return `分支配置 - ${props.nodeName || typeLabel}（${props.nodeKey || '-'}）`
})

/** 打开时快照分支数据：按 branchOrder 升序（空值排后，Number(null)=0 需显式排除），序号归一为 1..n 展示 */
function handleOpen() {
  const orderOf = (value) => (value !== null && value !== undefined && Number.isFinite(Number(value)) ? Number(value) : Number.MAX_SAFE_INTEGER)
  const source = (props.branches || [])
    .map((branch) => ({ ...branch, branchName: branch.branchName || '', condition: branch.condition || '', isDefault: branch.isDefault === true }))
    .sort((a, b) => orderOf(a.branchOrder) - orderOf(b.branchOrder) || 0)
    .map((branch, index) => ({ ...branch, branchOrder: index + 1 }))
  workingBranches.value = source
  removedEdgeIds.value = []
  syncActive(source[0] || { edgeId: '', branchName: '', branchOrder: 1, condition: '', isDefault: false })
}

function syncActive(branch) {
  activeBranch.edgeId = branch.edgeId || ''
  activeBranch.branchName = branch.branchName || ''
  activeBranch.branchOrder = branch.branchOrder || 1
  activeBranch.condition = branch.condition || ''
  activeBranch.isDefault = branch.isDefault === true
}

/** 顶部表单与工作副本双向同步（编辑即写入副本，取消由关闭丢弃） */
function syncWorkingActive() {
  const branch = workingBranches.value.find((item) => item.edgeId === activeBranch.edgeId)
  if (!branch) {
    return
  }
  branch.branchName = activeBranch.branchName
  branch.condition = activeBranch.condition
  branch.isDefault = activeBranch.isDefault
}

/** 默认分支互斥单选：切「是」自动清同节点其余出边默认标记（分册 §4.5 字段规格） */
function handleDefaultChange(value) {
  const isDefault = value === 'yes'
  activeBranch.isDefault = isDefault
  workingBranches.value.forEach((branch) => {
    branch.isDefault = branch.edgeId === activeBranch.edgeId ? isDefault : false
  })
}

/** 上移/下移：同节点出边集合内交换相邻顺序并即时归一序号（引擎求值顺序） */
function moveBranch(index, direction) {
  const target = index + direction
  if (target < 0 || target >= workingBranches.value.length) {
    return
  }
  const list = workingBranches.value
  const current = list[index]
  if (current.edgeId === activeBranch.edgeId) {
    syncWorkingActive()
  }
  const temp = list[target]
  list[target] = current
  list[index] = temp
  list.forEach((branch, position) => {
    branch.branchOrder = position + 1
  })
  if (current.edgeId === activeBranch.edgeId) {
    syncActive(current)
  }
}

function handleRowClick(row) {
  selectBranch(row)
}

/** 切换当前编辑分支（顶部表单同步，编辑中内容先写入原分支） */
function selectBranch(row) {
  syncWorkingActive()
  syncActive(row)
}

/** 弹窗内删除分支：至少保留一条（分支源节点 G3 出边约束，自定项见文件头登记） */
function removeBranch(index) {
  if (workingBranches.value.length <= 1) {
    ElMessage.warning('该节点至少保留一条流出连线，不能删除最后一个分支')
    return
  }
  const removed = workingBranches.value[index]
  if (removed.edgeId) {
    removedEdgeIds.value.push(removed.edgeId)
  }
  workingBranches.value.splice(index, 1)
  workingBranches.value.forEach((branch, position) => {
    branch.branchOrder = position + 1
  })
  if (activeBranch.edgeId === removed.edgeId) {
    syncActive(workingBranches.value[0])
  }
}

/** 公式设计器确认回填当前分支条件公式 */
function handleFormulaConfirm(expression) {
  activeBranch.condition = expression || ''
  syncWorkingActive()
}

function handleVisibleChange(value) {
  emit('update:visible', value)
}

/** 确定：编辑内容写入副本 → 客户端校验 → confirm 回抛（画布侧统一回写） */
function handleConfirm() {
  syncWorkingActive()
  if (props.nodeType === 'CONDITION') {
    const defaultCount = workingBranches.value.filter((branch) => branch.isDefault).length
    if (defaultCount !== 1) {
      ElMessage.error('条件分支节点必须指定且仅指定一条默认分支')
      return
    }
    // 非默认出边必须配置条件公式（默认兜底出边可空，自定项见文件头登记）
    const missing = workingBranches.value.filter((branch) => !branch.isDefault && !String(branch.condition || '').trim())
    if (missing.length > 0) {
      ElMessage.error('条件分支的非默认出边必须配置条件公式（默认分支可空）')
      return
    }
  }
  emit('update:visible', false)
  emit('confirm', {
    nodeKey: props.nodeKey,
    branches: workingBranches.value.map((branch) => ({
      edgeId: branch.edgeId,
      branchOrder: branch.branchOrder,
      branchName: branch.branchName,
      condition: branch.condition,
      isDefault: branch.isDefault,
    })),
    removedEdgeIds: removedEdgeIds.value.slice(),
  })
}
</script>

<style scoped>
.branch-form {
  margin-bottom: 4px;
}

.branch-order-text {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.branch-order-tip,
.condition-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.condition-tip {
  display: block;
  margin-left: 0;
  line-height: 1.6;
}

.condition-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.branch-detail-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.condition-summary {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 12px;
}
</style>

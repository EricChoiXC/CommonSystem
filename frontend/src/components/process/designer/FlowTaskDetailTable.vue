<template>
  <div class="flow-task-table">
    <el-table :data="rows" border stripe size="small">
      <el-table-column type="index" label="#" width="44" align="center" />
      <el-table-column label="任务名称" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.taskName" size="small" maxlength="200" placeholder="必填，仅作展示名" :disabled="disabled" />
        </template>
      </el-table-column>
      <el-table-column label="执行任务" min-width="150">
        <template #default="{ row }">
          <!-- T-01 定稿：@ProcessTask 注册清单接口后端未提供，「执行任务」下拉以「接口未就绪」空态占位
               （禁用 + 空态文案），禁止硬编码任务清单；清单接口（P17-BE-005）落地后由宿主传入 options 启用 -->
          <el-select
            v-model="row.taskBean"
            size="small"
            :disabled="disabled"
            :placeholder="taskBeanPlaceholder"
            style="width: 100%"
          />
        </template>
      </el-table-column>
      <el-table-column label="执行步骤" min-width="150">
        <template #default="{ row }">
          <el-select v-model="row.taskType" size="small" :disabled="disabled" placeholder="必选" style="width: 100%">
            <el-option v-for="opt in PROCESS_TASK_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="任务参数（JSON）" min-width="210">
        <template #default="{ row, $index }">
          <el-input
            v-model="row.paramsText"
            type="textarea"
            :rows="2"
            size="small"
            placeholder='可空，JSON 对象，如 {"keep":true}'
            :disabled="disabled"
          />
          <div v-if="rowParamsErrors[$index]" class="task-params-error">{{ rowParamsErrors[$index] }}</div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="64" align="center">
        <template #default="{ $index }">
          <el-button link type="danger" size="small" :disabled="disabled" @click="removeRow($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="task-table-footer">
      <el-button size="small" type="primary" plain :disabled="disabled" @click="addRow">添加任务</el-button>
      <span class="task-table-tip">行序即执行顺序（新增行追加尾部，不提供排序控件）；「执行任务」下拉依赖 @ProcessTask 注册清单接口，接口未就绪前以空态占位。</span>
    </div>
  </div>
</template>

<script setup>
/**
 * 流程设计器 · 流程任务明细表（v1_7 / P17-FE-003，分册 §5.5）
 *
 * 审批 / 抄送节点「流程任务」标签与机器人节点「基本」标签通用的同一张明细表。
 * 列：任务名称（必填）/ 执行任务（@ProcessTask name，T-01 空态占位）/ 执行步骤
 * （ProcessTaskType 八枚举，必填）/ 任务参数（JSON 文本，可空，前端合法性预检）/ 删除。
 * 行序即执行顺序（引擎按 tasks 顺序执行），不提供排序控件，新增行追加尾部。
 *
 * T-01 定稿（2026-11-21）：@ProcessTask 注册清单接口后端未提供，「执行任务」(taskBean)
 * 下拉以「接口未就绪」空态占位（禁用 + 空态文案），禁止硬编码任务清单；已存值（回显）
 * 以原文展示在禁用下拉中。清单接口落地后由宿主经 taskBeanOptions prop 注入并启用
 * （本任务先落 prop 契约）。
 *
 * 数据流：props.tasks（VO 形态 { taskName, taskBean, taskType, params 对象 }）进出经
 * nodeConfig.js 纯函数换算为编辑行（params ⇄ JSON 文本）；用户编辑即 emit update:tasks
 * （v-model:tasks），关闭弹窗不落任何数据（弹窗工作副本语义与分支配置弹窗一致）。
 *
 * 自定项登记（分册未冻结细节，P17-DOC-010 走查时可调）：
 *   - 「执行任务必填」校验随 T-01 延后：清单接口未就绪期间前端不拦截空 taskBean
 *     （拦截将导致任何任务行都无法通过校验、流程任务标签不可用），交后端 G8 兜底拒绝；
 *   - params 预检按 Do v2 的 Map<String,Object> 口径要求为 JSON 对象（数组/标量视为非法）；
 *   - 行内 params 非法即时红字提示，【确定】时汇总「第 N 行」文案阻断回写。
 */
import { computed, nextTick, ref, watch } from 'vue'
import {
  PARAMS_ERROR_TEXT,
  PROCESS_TASK_TYPE_OPTIONS,
  taskRowsFromVo,
  taskRowsToVo,
  validateTaskRows,
} from './nodeConfig'

const props = defineProps({
  /** 任务明细 VO 数组（v-model:tasks，元素 { taskName, taskBean, taskType, params }） */
  tasks: { type: Array, default: () => [] },
  /** 执行任务下拉选项（T-01：清单接口未就绪前为空 → 下拉禁用空态占位；元素 { value, label }） */
  taskBeanOptions: { type: Array, default: () => [] },
  /** 是否禁用（只读） */
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:tasks'])

/** 编辑行工作副本（params 以 JSON 文本编辑） */
const rows = ref([])

/**
 * props ↔ 工作副本同步守卫：
 *   - lastEmitted 记录最近一次 emit 的数组引用——父级 v-model 回灌同一引用时跳过重克隆
 *     （避免逐键回灌导致行对象整体重建、输入焦点/IME 组合被打断）；
 *   - syncing 兜底克隆期间的行变更不回抛，防止 v-model 循环。
 */
let syncing = false
let lastEmitted = null

watch(
  () => props.tasks,
  (value) => {
    if (value && value === lastEmitted) {
      return
    }
    syncing = true
    lastEmitted = null
    rows.value = taskRowsFromVo(value)
    nextTick(() => {
      syncing = false
    })
  },
  { immediate: true }
)

watch(
  rows,
  () => {
    if (syncing) {
      return
    }
    lastEmitted = taskRowsToVo(rows.value)
    emit('update:tasks', lastEmitted)
  },
  { deep: true }
)

/** 「执行任务」下拉占位文案：无选项（接口未就绪）时以空态文案占位 */
const taskBeanPlaceholder = computed(() => (props.taskBeanOptions.length ? '请选择执行任务' : '接口未就绪'))

/** 行内 params JSON 预检结果（即时红字提示，冻结文案「任务参数必须是合法 JSON」） */
const rowParamsErrors = computed(() =>
  rows.value.map((row) => {
    const text = String(row.paramsText || '').trim()
    if (!text) {
      return ''
    }
    let ok = false
    try {
      const parsed = JSON.parse(text)
      ok = parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)
    } catch {
      ok = false
    }
    return ok ? '' : PARAMS_ERROR_TEXT
  })
)

/** 新增行追加尾部（行序即执行顺序，不提供排序控件） */
function addRow() {
  rows.value.push({ taskName: '', taskBean: '', taskType: '', paramsText: '' })
}

function removeRow(index) {
  rows.value.splice(index, 1)
}

/** 客户端校验（弹窗【确定】调用）：返回中文错误消息数组（空数组 = 通过） */
function validate() {
  return validateTaskRows(rows.value)
}

defineExpose({ validate })
</script>

<style scoped>
.flow-task-table {
  width: 100%;
}

.task-params-error {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-color-danger);
}

.task-table-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.task-table-tip {
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>

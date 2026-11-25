<template>
  <!--
    表单设计器 · 版本列表与回滚弹窗（F15-FE-008，UI 分册 §4.9）。
    与管理页行【版本】共用同一弹窗形态（F15-FE-009 页面复用本组件）：getFormVersions
    按 fieldVersion 降序；列 = 版本号 / 状态 tag / 发布时间（草稿行显示「-」）/ 创建时间 /
    操作（回滚——仅 PUBLISHED / DEPRECATED 行可回滚，DRAFT 行禁用）；
    **版本行不携带 Schema 内容，不做历史版本内容预览 / 对比**（API 分册 §3.9）；
    回滚确认文案与结果提示按 §4.9 定稿（回滚 = 复制历史版本内容发布为新版本，
    不产生指针回拨，功能详细设计 §5.1 原则 5）。
    设计器内部组件：仅 FormDesigner / 表单模块页面消费，不单独对外导出。
  -->
  <el-dialog :model-value="visible" title="版本列表" width="680px" @update:model-value="handleVisibleChange">
    <el-table v-loading="loading" :data="versions" size="small" border>
      <el-table-column label="版本号" width="90">
        <template #default="{ row }">
          V{{ row.fieldVersion }}
          <el-tag v-if="isCurrentPublished(row)" size="small" type="success" class="version-current-tag">当前</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.fieldStatus)">{{ statusLabel(row.fieldStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="170">
        <template #default="{ row }">{{ row.fieldPublishedAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ row.fieldCreateTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="90">
        <template #default="{ row }">
          <el-tooltip :disabled="rollbackDisabledReason(row) === ''" :content="rollbackDisabledReason(row)" placement="top">
            <span>
              <el-button
                link
                type="primary"
                size="small"
                :disabled="row.fieldStatus === 'DRAFT' || rollbackLoading"
                @click="handleRollback(row)"
              >
                回滚
              </el-button>
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <div class="version-tip">版本行不携带 Schema 内容，不做历史版本内容预览 / 对比；回滚 = 复制该版本内容发布为新版本（Vn+1）</div>
    <template #footer>
      <el-button @click="handleVisibleChange(false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFormVersions, rollbackForm } from '../../../api/form.js'

/**
 * 版本弹窗 props：
 * - visible：弹窗显隐（v-model:visible 双向）；
 * - formId：表单主档 ID（版本列表与回滚目标）；
 * - currentVersionId：当前发布版本行 ID（「当前」标记消费，可空）。
 */
const props = defineProps({
  visible: { type: Boolean, default: false },
  formId: { type: String, default: '' },
  currentVersionId: { type: String, default: '' },
})

const emit = defineEmits(['update:visible', 'rollback-success'])

const loading = ref(false)
const rollbackLoading = ref(false)
const versions = ref([])

// 打开时拉取版本列表（按 fieldVersion 降序，后端排序口径）
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      loadVersions()
    } else {
      versions.value = []
    }
  },
)

async function loadVersions() {
  if (!props.formId) {
    versions.value = []
    return
  }
  loading.value = true
  try {
    const response = await getFormVersions(props.formId)
    versions.value = Array.isArray(response && response.data) ? response.data : []
  } catch (error) {
    versions.value = []
    // 错误提示由 axios 实例统一拦截展示（CS_FORM_* message 直显）
  } finally {
    loading.value = false
  }
}

function handleVisibleChange(value) {
  emit('update:visible', value)
}

function isCurrentPublished(row) {
  return row.fieldStatus === 'PUBLISHED' && (!props.currentVersionId || row.fieldId === props.currentVersionId)
}

/** 状态 tag（§4.9：DRAFT 草稿 info / PUBLISHED 当前发布 success / DEPRECATED 已替代 info） */
function statusTagType(status) {
  if (status === 'PUBLISHED') return 'success'
  return 'info'
}

function statusLabel(status) {
  if (status === 'DRAFT') return '草稿'
  if (status === 'PUBLISHED') return '当前发布'
  if (status === 'DEPRECATED') return '已替代'
  return status || '-'
}

/** 回滚禁用原因（DRAFT 行禁用——回滚目标须为 PUBLISHED / DEPRECATED 行，§4.9） */
function rollbackDisabledReason(row) {
  if (row.fieldStatus === 'DRAFT') return '草稿行不可回滚（回滚目标须为已发布 / 已替代版本）'
  return ''
}

/** 回滚：确认文案按 §4.9 定稿 → rollbackForm → 成功提示新版本号并刷新列表、上抛宿主刷新画布 */
function handleRollback(row) {
  ElMessageBox.confirm(
    `回滚将复制该历史版本内容发布为新版本（Vn+1），不影响既有草稿与在途数据；确定回滚到 V${row.fieldVersion}？`,
    '回滚版本',
    { type: 'warning' },
  )
    .then(async () => {
      rollbackLoading.value = true
      try {
        const response = await rollbackForm(props.formId, row.fieldId)
        const data = response && response.data ? response.data : {}
        ElMessage.success(`回滚成功，新版本 V${data.fieldVersion}`)
        emit('rollback-success', data)
        await loadVersions()
      } catch (error) {
        // 错误提示由 axios 实例统一拦截展示（CS_FORM_* message 直显）
      } finally {
        rollbackLoading.value = false
      }
    })
    .catch(() => {})
}
</script>

<style scoped>
.version-current-tag {
  margin-left: 4px;
}

.version-tip {
  margin-top: 10px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--el-text-color-placeholder);
}
</style>

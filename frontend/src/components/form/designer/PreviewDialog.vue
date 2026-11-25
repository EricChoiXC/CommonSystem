<template>
  <!--
    表单设计器 · 预览弹窗（F15-FE-008，UI 分册 §4.7）。
    大尺寸弹窗（width 960px）挂载 FormRenderer mode="edit"（编辑态渲染器：完整数据
    绑定 + 默认值初始化 + 规则引擎挂载 + 校验，schema = 当前画布 Schema——未落库、
    不进快照栈、关闭弹窗即丢弃）；
    「预演字段权限」开关：演示 fieldPermissions 外部覆盖（readonly 置灰 / hidden 不渲染，
    为 v1.8 BPM 外部权限覆盖预留的预演位）；
    【校验】按钮仅触发渲染器本地 expose.validate() 展示结果（本地预演），**不发数据面
    请求**（服务端权威校验在保存 / 发布 / 提交链路）。
    设计器内部组件：仅 FormDesigner 消费，不单独对外导出。
  -->
  <el-dialog :model-value="visible" title="表单预览" width="960px" destroy-on-close @update:model-value="handleVisibleChange">
    <div class="preview-toolbar">
      <div class="preview-permission">
        <span class="preview-permission-label">预演字段权限</span>
        <el-switch v-model="permissionDemoEnabled" />
        <el-select
          v-if="permissionDemoEnabled"
          v-model="permissionDemoValue"
          class="preview-permission-select"
          size="small"
        >
          <el-option label="全部可编辑（editable）" value="editable" />
          <el-option label="全部只读（readonly）" value="readonly" />
          <el-option label="全部隐藏（hidden）" value="hidden" />
        </el-select>
        <span class="preview-permission-tip">演示字段级权限外部覆盖效果（readonly 置灰 / hidden 不渲染），不落库</span>
      </div>
      <el-button size="small" type="primary" plain @click="handleValidate">校验</el-button>
    </div>
    <el-alert
      v-if="validateResult"
      class="preview-alert"
      :type="validateResult.pass ? 'success' : 'warning'"
      :closable="false"
      show-icon
      :title="validateResult.pass ? '校验通过' : `共 ${validateResult.errors.length} 处校验未通过`"
    />
    <div v-if="validateResult && !validateResult.pass" class="preview-errors">
      <div v-for="(error, index) in validateResult.errors" :key="index" class="preview-error-item">
        {{ error.message }}
      </div>
    </div>
    <div class="preview-body">
      <!-- 预览为一次性编辑态渲染：schema 取打开时刻画布快照（弹窗内不再跟随画布编辑变化） -->
      <FormRenderer
        v-if="visible && previewSchema"
        ref="rendererRef"
        :schema="previewSchema"
        mode="edit"
        :field-permissions="previewPermissions"
        v-model="previewData"
      />
      <el-empty v-else description="画布暂无字段，无法预览" />
    </div>
    <template #footer>
      <el-button @click="handleVisibleChange(false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import FormRenderer from '../FormRenderer.vue'
import '../materials/index.js'

/**
 * 预览弹窗 props：
 * - visible：弹窗显隐（v-model:visible 双向）；
 * - schema：当前画布 Schema（打开时刻快照语义——宿主传入后本组件持独立引用）。
 */
const props = defineProps({
  visible: { type: Boolean, default: false },
  schema: { type: Object, default: null },
})

const emit = defineEmits(['update:visible'])

const rendererRef = ref(null)
const previewData = ref({})

/** 打开时刻的 Schema 快照（弹窗内不再跟随画布编辑变化，§4.7「未落库、不进快照栈」） */
const previewSchema = ref(null)

/** 预演字段权限开关与取值（演示用外部覆盖映射；关闭 = null 不覆盖） */
const permissionDemoEnabled = ref(false)
const permissionDemoValue = ref('editable')

const previewPermissions = computed(() => {
  if (!permissionDemoEnabled.value || !previewSchema.value || !Array.isArray(previewSchema.value.fields)) return null
  const map = {}
  const collect = (list) => {
    for (const node of list) {
      if (!node || typeof node !== 'object' || !node.field) continue
      map[node.field] = permissionDemoValue.value
      if (node.type === 'group' && Array.isArray(node.children)) collect(node.children)
      else if (node.type === 'grid' && Array.isArray(node.children)) {
        for (const column of node.children) {
          if (column && Array.isArray(column.fields)) collect(column.fields)
        }
      } else if (node.type === 'detailTable' && Array.isArray(node.children)) collect(node.children)
    }
  }
  collect(previewSchema.value.fields)
  return map
})

/** 校验结果（本地预演展示） */
const validateResult = ref(null)

// 打开时重置预演状态并快照 Schema；关闭时清理
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      previewSchema.value = props.schema ? JSON.parse(JSON.stringify(props.schema)) : null
      previewData.value = {}
      validateResult.value = null
      permissionDemoEnabled.value = false
      permissionDemoValue.value = 'editable'
    } else {
      previewSchema.value = null
      previewData.value = {}
      validateResult.value = null
    }
  },
)

function handleVisibleChange(value) {
  emit('update:visible', value)
}

/**
 * 本地校验（§4.7）：仅触发渲染器 expose.validate()（静态基线 + 规则动态校验合并，
 * 本地预演），展示结果——不发数据面请求（服务端权威校验在保存 / 发布 / 提交链路）。
 */
function handleValidate() {
  const renderer = rendererRef.value
  if (!renderer || typeof renderer.validate !== 'function') return
  validateResult.value = renderer.validate()
}
</script>

<style scoped>
.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.preview-permission {
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-permission-label {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.preview-permission-select {
  width: 180px;
}

.preview-permission-tip {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.preview-alert {
  margin-bottom: 8px;
}

.preview-errors {
  max-height: 160px;
  overflow-y: auto;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 4px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: var(--el-color-warning-light-9);
}

.preview-error-item {
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-color-warning);
}

.preview-body {
  max-height: 60vh;
  overflow-y: auto;
  padding: 4px 2px;
}
</style>

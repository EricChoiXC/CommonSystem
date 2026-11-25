<template>
  <!--
    表单设计器 · Schema 导入弹窗（F15-FE-008，UI 分册 §4.8）。
    el-input textarea 粘贴 JSON 文本 → 前端轻量结构校验（designerSupport.
    validateSchemaImport：JSON.parse + 协议头核对 + fields 递归 + type 白名单 + 资源
    上限，自研轻量校验器不引入 Ajv，§4.1）→ 通过则提示「导入将覆盖当前画布内容，
    未保存的改动将丢失」二次确认后上抛宿主整表替换画布；失败时逐条展示校验原因
    （对齐 CS_FORM_SCHEMA_INVALID 逐条中文原因形态）。
    落库边界：导入不直接落库——仅替换画布内容，须【保存草稿】/【发布】后才经
    updateForm / publishForm 进入服务端。
    设计器内部组件：仅 FormDesigner 消费，不单独对外导出。
  -->
  <el-dialog :model-value="visible" title="导入 Schema" width="640px" @update:model-value="handleVisibleChange">
    <el-alert
      class="import-alert"
      type="info"
      :closable="false"
      show-icon
      title="粘贴表单 Schema JSON 文本；导入仅替换画布内容、不直接落库，需【保存草稿】/【发布】后才写入服务端"
    />
    <el-input
      v-model="jsonText"
      type="textarea"
      :rows="12"
      class="import-textarea"
      placeholder='粘贴 Schema JSON：{ "formId": "...", "formCode": "...", "formName": "...", "version": null, "fields": [ ... ] }'
    />
    <template v-if="validateErrors.length > 0">
      <el-alert class="import-alert" type="error" :closable="false" show-icon title="结构校验未通过，请修正后重试" />
      <div class="import-errors">
        <div v-for="(error, index) in validateErrors" :key="index" class="import-error-item">{{ error }}</div>
      </div>
    </template>
    <el-alert
      v-else-if="validatePassed"
      class="import-alert"
      type="success"
      :closable="false"
      show-icon
      title="结构校验通过，点击「校验并导入」完成二次确认后替换画布"
    />
    <template #footer>
      <el-button @click="handleVisibleChange(false)">取消</el-button>
      <el-button type="primary" :loading="confirming" @click="handleImport">校验并导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { validateSchemaImport } from './designerSupport.js'

/**
 * 导入弹窗 props：
 * - visible：弹窗显隐（v-model:visible 双向）；
 * - getMaterial：物料访问器注入（registry.getComponentMaterial，type 白名单判定消费）。
 */
const props = defineProps({
  visible: { type: Boolean, default: false },
  getMaterial: { type: Function, required: true },
})

const emit = defineEmits(['update:visible', 'confirm'])

const jsonText = ref('')
const validateErrors = ref([])
const validatePassed = ref(false)
const confirming = ref(false)

// 打开时重置粘贴内容与校验态
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      jsonText.value = ''
      validateErrors.value = []
      validatePassed.value = false
    }
  },
)

function handleVisibleChange(value) {
  emit('update:visible', value)
}

/** 校验并导入：轻量结构校验 → 二次确认「导入将覆盖当前画布内容」→ 上抛已解析 Schema */
async function handleImport() {
  const result = validateSchemaImport(jsonText.value, { getMaterial: props.getMaterial })
  validateErrors.value = result.errors
  validatePassed.value = result.ok
  if (!result.ok) return
  confirming.value = true
  try {
    await ElMessageBox.confirm('导入将覆盖当前画布内容，未保存的改动将丢失。确定导入？', '导入确认', { type: 'warning' })
  } catch (error) {
    confirming.value = false
    return
  }
  confirming.value = false
  emit('confirm', result.schema)
  ElMessage.success('导入成功，画布内容已替换（未落库，请保存草稿或发布）')
  handleVisibleChange(false)
}
</script>

<style scoped>
.import-alert {
  margin-bottom: 10px;
}

.import-textarea {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
}

.import-errors {
  max-height: 180px;
  overflow-y: auto;
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 4px;
  padding: 8px 12px;
  background: var(--el-color-danger-light-9);
}

.import-error-item {
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-color-danger);
}
</style>

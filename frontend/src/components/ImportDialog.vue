<template>
  <el-dialog
    :model-value="modelValue"
    :title="title || $t('frontend.common.importExport.title')"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="handleUpdateVisible"
    @open="handleOpen"
    @closed="handleClosed"
  >
    <div class="import-dialog">
      <!-- 1. 模板下载（templatePath 有值才渲染） -->
      <div v-if="templatePath" class="import-template">
        <el-button size="small" :loading="templateLoading" @click="handleDownloadTemplate">
          {{ $t('frontend.common.importExport.template.download') }}
        </el-button>
      </div>

      <!-- 2. 文件选择（drag、手动上传、单文件、仅 .xlsx） -->
      <el-upload
        ref="uploadRef"
        drag
        action="#"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        :on-exceed="handleExceed"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">{{ $t('frontend.common.importExport.file.placeholder') }}</div>
      </el-upload>

      <!-- 3. 导入配置区：公共开关内置渲染，业务键由 configFields 追加在后 -->
      <div class="import-config">
        <el-form label-width="120px" size="default">
          <el-form-item :label="$t('frontend.common.importExport.config.ignoreError')">
            <el-switch v-model="params.ignoreError" />
          </el-form-item>
          <el-form-item :label="$t('frontend.common.importExport.config.fullUpdate')">
            <el-switch v-model="params.fullUpdate" />
          </el-form-item>
          <template v-for="field in configFields" :key="field.key">
            <el-form-item :label="field.label || field.key">
              <el-switch v-if="field.type === 'switch'" v-model="params[field.key]" />
              <el-select
                v-else-if="field.type === 'select'"
                v-model="params[field.key]"
                clearable
                :placeholder="$t('frontend.common.importExport.config.select.placeholder')"
              >
                <el-option
                  v-for="opt in field.options || []"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </template>
        </el-form>
      </div>

      <!-- 4. 导入结果（props.result 非空时渲染） -->
      <div v-if="hasResult" class="import-result">
        <el-alert
          v-if="result.success > 0 && !(result.errors && result.errors.length)"
          type="success"
          :closable="false"
          show-icon
          :title="$t('frontend.common.importExport.result.all.success')"
        />
        <div class="import-result-count">
          <span>{{ $t('frontend.common.importExport.result.total', { total: result.total }) }}</span>
          <span class="import-result-success">
            {{ $t('frontend.common.importExport.result.success.count', { count: result.success }) }}
          </span>
          <span class="import-result-failed">
            {{ $t('frontend.common.importExport.result.failed.count', { count: result.failed }) }}
          </span>
        </div>
        <div v-if="result.errors && result.errors.length" class="import-result-errors">
          <div class="import-result-errors-title">
            {{ $t('frontend.common.importExport.result.errors.title') }}
          </div>
          <el-table :data="result.errors" size="small" max-height="200">
            <el-table-column prop="row" :label="$t('frontend.common.importExport.result.errors.column.row')" width="90" />
            <el-table-column prop="field" :label="$t('frontend.common.importExport.result.errors.column.field')" min-width="120" show-overflow-tooltip />
            <el-table-column prop="message" :label="$t('frontend.common.importExport.result.errors.column.message')" min-width="180" show-overflow-tooltip />
          </el-table>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleUpdateVisible(false)">{{ $t('frontend.common.importExport.cancel') }}</el-button>
      <el-button type="primary" @click="handleConfirm">
        {{ $t('frontend.common.importExport.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { importTemplate } from '@/api/common'
import { downloadBlob } from '@/utils/download'

/**
 * 公共导入弹窗（docs/v1_1/04-导入导出/导入导出公共方案设计.md 第 8.4 节）
 *
 * 四要素契约：
 * 1. 模板下载按钮：props.templatePath 有值才渲染；点击调 api/common.js#importTemplate
 *    (templatePath, templateBody) 后经 utils/download.js#downloadBlob 触发保存，失败 ElMessage.error；
 * 2. 文件选择：el-upload（drag、auto-upload=false、limit=1、accept=".xlsx"），
 *    选中即校验扩展名（.xlsx，不区分大小写）与大小（props.maxSize，默认 50MB，对齐
 *    CS_ATT_MAX_FILE_SIZE，前端先拦），不合规清空选择并 ElMessage.warning；
 * 3. 导入配置区：props.configFields 动态渲染（{ key, label, type: 'switch'|'select', options,
 *    defaultValue }），弹窗打开时按 defaultValue 初始化 params；公共开关 ignoreError / fullUpdate
 *    内置渲染（默认均 false），业务键追加在后；
 * 4. 确认 / 取消：未选文件点确认给警告不 emit；确认 emit('confirm', { file, params })，
 *    由页面调 api/common.js#importExcel 并将响应（ImportResultVo）传回 props.result；
 *    组件内置公共结果渲染：成功 / 失败计数 + errors 明细表（row=文件行号、field=字段、
 *    message=原因），errors 为空且 success > 0 显示全部成功提示；关闭时重置选择与结果区。
 * 组件内文案一律走 vue-i18n（key 前缀 frontend.common.importExport.*）。
 */
const props = defineProps({
  /** 显隐控制（v-model），父组件绑定控制弹窗打开 / 关闭 */
  modelValue: { type: Boolean, default: false },
  /** 弹窗标题（缺省用 i18n 默认标题） */
  title: { type: String, default: '' },
  /** 导入模板下载接口完整路径（如 /backend/sys/dict/export）；有值才渲染模板下载按钮 */
  templatePath: { type: String, default: '' },
  /** 模板下载请求体 { body, params }（与 /export 同构，可选） */
  templateBody: { type: Object, default: null },
  /** 业务导入配置字段数组：{ key, label, type: 'switch'|'select', options, defaultValue }，追加在公共开关之后 */
  configFields: { type: Array, default: () => [] },
  /** 导入文件大小上限（字节），默认 50 * 1024 * 1024，对齐后端 CS_ATT_MAX_FILE_SIZE */
  maxSize: { type: Number, default: 50 * 1024 * 1024 },
  /** 导入结果 ImportResultVo { total, success, failed, errors }，由页面导入成功后传回；非空时渲染结果区 */
  result: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const { t } = useI18n()

const uploadRef = ref(null)
const templateLoading = ref(false)
const file = ref(null)
const params = reactive({ ignoreError: false, fullUpdate: false })

/** props.result 是否有有效内容（null / 空对象视为无结果） */
const hasResult = computed(() => {
  if (!props.result) {
    return false
  }
  return Object.keys(props.result).length > 0
})

/** 弹窗打开：按 configFields 的 defaultValue 初始化导入配置（公共开关固定默认 false） */
function handleOpen() {
  params.ignoreError = false
  params.fullUpdate = false
  ;(props.configFields || []).forEach((field) => {
    params[field.key] = field.defaultValue !== undefined ? field.defaultValue : null
  })
}

/** 弹窗关闭动画结束：重置文件选择与结果区（结果区内容由页面置回，组件只清自身选择态） */
function handleClosed() {
  clearFile()
}

function handleUpdateVisible(visible) {
  emit('update:modelValue', visible)
}

/** 清空文件选择（el-upload 已有文件时需先清其内部列表再重选） */
function clearFile() {
  file.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

/** 模板下载：importTemplate 文件流响应 + downloadBlob 触发保存 */
async function handleDownloadTemplate() {
  templateLoading.value = true
  try {
    const response = await importTemplate(props.templatePath, props.templateBody || {})
    downloadBlob(response, 'template.xlsx')
    ElMessage.success(t('frontend.common.importExport.template.download.success'))
  } catch (error) {
    ElMessage.error(error.message || t('frontend.common.importExport.template.download.failed'))
  } finally {
    templateLoading.value = false
  }
}

/** 选中即校验扩展名（.xlsx，不区分大小写）与大小，不合规清空选择 */
function handleFileChange(uploadFile) {
  const raw = uploadFile.raw
  if (!raw) {
    return
  }
  if (!/\.xlsx$/i.test(raw.name || '')) {
    ElMessage.warning(t('frontend.common.importExport.file.type.invalid'))
    clearFile()
    return
  }
  if (raw.size > props.maxSize) {
    ElMessage.warning(t('frontend.common.importExport.file.size.exceed', { size: maxSizeText() }))
    clearFile()
    return
  }
  file.value = raw
}

function handleFileRemove() {
  file.value = null
}

/** limit=1 下重复选择：清掉旧文件后将新文件放入列表（handleStart 为 el-upload 公开方法）再走校验 */
function handleExceed(files) {
  if (!files || !files.length) {
    return
  }
  uploadRef.value && uploadRef.value.clearFiles()
  if (uploadRef.value && uploadRef.value.handleStart) {
    uploadRef.value.handleStart(files[0])
  }
  handleFileChange({ raw: files[0] })
}

/** 大小上限展示文本（字节约为 MB，如 50MB） */
function maxSizeText() {
  const mb = props.maxSize / (1024 * 1024)
  return Number.isInteger(mb) ? `${mb}MB` : `${mb.toFixed(1)}MB`
}

/** 确认导入：未选文件给警告不 emit；确认回调 { file, params } 由页面执行导入 */
function handleConfirm() {
  if (!file.value) {
    ElMessage.warning(t('frontend.common.importExport.file.required'))
    return
  }
  emit('confirm', { file: file.value, params: { ...params } })
}
</script>

<style scoped>
.import-template {
  margin-bottom: 12px;
}

.import-config {
  margin-top: 12px;
}

.import-result {
  margin-top: 12px;
}

.import-result-count {
  display: flex;
  gap: 16px;
  margin: 8px 0;
  font-size: 13px;
}

.import-result-success {
  color: var(--el-color-success);
}

.import-result-failed {
  color: var(--el-color-danger);
}

.import-result-errors-title {
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: bold;
}
</style>

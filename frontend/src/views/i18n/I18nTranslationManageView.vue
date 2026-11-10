<template>
  <div class="i18n-translation-manage">
    <!-- 筛选条件区：模块单选下拉（model/list，显示名回退 field_model） -->
    <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item :label="t('frontend.i18n.translation.filter.model')">
            <el-select
              v-model="filters.fieldModel"
              :placeholder="t('frontend.i18n.translation.filter.model.placeholder')"
              style="width: 100%"
              @change="handleModelChange"
            >
              <el-option
                v-for="model in modelOptions"
                :key="model.fieldModel"
                :label="model.fieldModelName"
                :value="model.fieldModel"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col v-if="filters.fieldModel" :span="6">
          <el-form-item :label="t('frontend.i18n.translation.matrix.key')">
            <el-input
              v-model="filters.fieldKey"
              :placeholder="t('frontend.i18n.translation.matrix.key.placeholder')"
              clearable
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 工具区：左侧说明 + 右侧导出/导入/提交 -->
    <div class="list-toolbar">
      <div class="sort-area">
        <span v-if="changedCount > 0" class="changed-hint">
          {{ t('frontend.i18n.translation.changedHint', { count: changedCount }) }}
        </span>
      </div>
      <div class="btn-area">
        <el-button :disabled="!filters.fieldModel" @click="handleExport">
          {{ t('frontend.i18n.translation.export') }}
        </el-button>
        <el-button v-if="isI18nAdmin" :disabled="!filters.fieldModel" @click="importDialogVisible = true">
          {{ t('frontend.i18n.translation.import') }}
        </el-button>
        <el-button
          v-if="isI18nAdmin"
          type="primary"
          :disabled="changedCount === 0"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ t('frontend.common.submit') }}
        </el-button>
      </div>
    </div>

    <!-- 列表区：矩阵表格。资源列 fixed=left；语言列按 field_order 升序、超宽横向滚动（el-table 原生） -->
    <el-table
      :data="matrixRows"
      v-loading="loading"
      border
      row-key="fieldKey"
      :max-height="tableMaxHeight"
    >
      <template #empty>
        <el-empty :description="t('frontend.common.noData')" :image-size="80" />
      </template>
      <el-table-column
        prop="fieldKey"
        :label="t('frontend.i18n.translation.matrix.key')"
        min-width="220"
        fixed="left"
        show-overflow-tooltip
      />
      <el-table-column
        v-for="column in matrixLanguages"
        :key="column.fieldCode"
        :label="column.fieldName"
        min-width="240"
      >
        <template #header>
          <span>{{ column.fieldName }}</span>
          <span class="lang-code">（{{ column.fieldCode }}）</span>
        </template>
        <template #default="{ row }">
          <el-input
            v-model="row.messages[column.fieldCode]"
            type="textarea"
            :rows="1"
            :autosize="{ minRows: 1, maxRows: 4 }"
            @input="markChanged(row, column.fieldCode)"
          />
        </template>
      </el-table-column>
      <el-table-column
        v-if="isI18nAdmin"
        :label="t('frontend.i18n.translation.operation')"
        width="90"
        align="center"
        fixed="right"
      >
        <template #default="{ row }">
          <el-tooltip :content="t('frontend.i18n.translation.resetTip')" placement="top">
            <span>
              <el-button
                link
                type="warning"
                :disabled="submitting"
                @click="handleResetRow(row)"
              >
                {{ t('frontend.i18n.translation.reset') }}
              </el-button>
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <!-- 合计行（资源行数，metadata.total 口径） -->
    <div class="list-total-bar">
      {{ t('frontend.common.total', { total: matrixRows.length }) }}
    </div>

    <!-- 导入弹窗（公共组件）：i18n 无模板下载接口与导入配置（方案假设 A3），
         不传 templatePath / configFields 传空，公共开关由组件内置展示；
         导入结果公共字段由组件内置渲染，扩展字段（跳过项）由页面补充提示 -->
    <ImportDialog
      v-model="importDialogVisible"
      :title="t('frontend.i18n.translation.import')"
      :config-fields="[]"
      :result="importResult"
      @confirm="handleConfirmImport"
      @update:model-value="handleImportDialogClose"
    />

    <!-- 提交确认弹窗：仅列出本次改动项 -->
    <el-dialog
      v-model="diffDialogVisible"
      :title="t('frontend.i18n.translation.confirm.submitTitle')"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-table :data="changedDetails" max-height="360" size="small" border>
        <el-table-column prop="fieldKey" :label="t('frontend.i18n.translation.matrix.key')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fieldLanguage" label="语言" width="110" />
        <el-table-column :label="t('frontend.i18n.translation.diff.before')" min-width="160">
          <template #default="{ row }">
            <span class="diff-before">{{ row.before || '∅' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('frontend.i18n.translation.diff.after')" min-width="160">
          <template #default="{ row }">
            <span class="diff-after">{{ row.fieldMessage || '∅' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="diffDialogVisible = false">{{ t('frontend.common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmSubmit">
          {{ t('frontend.common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 重置确认弹窗 -->
    <el-dialog
      v-model="resetDialogVisible"
      :title="t('frontend.i18n.translation.confirm.resetRowTitle')"
      width="480px"
    >
      <el-form label-width="80px">
        <el-form-item :label="t('frontend.i18n.translation.matrix.key')">
          <span>{{ resetTarget.fieldKey }}</span>
        </el-form-item>
        <el-form-item :label="t('frontend.i18n.translation.matrix.language')">
          <el-select v-model="resetTarget.fieldLanguage" style="width: 100%">
            <el-option
              v-for="column in matrixLanguages"
              :key="column.fieldCode"
              :label="`${column.fieldName}（${column.fieldCode}）`"
              :value="column.fieldCode"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="reset-warning">{{ t('frontend.i18n.translation.confirm.resetRowText') }}</p>
      <template #footer>
        <el-button @click="resetDialogVisible = false">{{ t('frontend.common.cancel') }}</el-button>
        <el-button type="warning" :loading="submitting" @click="confirmReset">
          {{ t('frontend.common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { hasAuth } from '@/utils/auth'
import { ImportDialog } from '@/components'
import { downloadBlob } from '@/utils/download'
import {
  listModels,
  listTranslationMatrix,
  updateTranslations,
  resetTranslation,
  exportTranslations,
  importTranslations
} from '@/api/i18n'

/**
 * 翻译维护页：i18n 翻译矩阵编辑（任务清单 I18N4-FE-001）。
 * 行 = 资源 key（field_key 升序），列 = 启用语言（field_order 升序）；
 * 单元格直接编辑，提交时 diff「加载快照 vs 当前值」→ 确认弹窗列出改动项 → 批量提交；
 * 操作列「重置」按资源行 × 语言（选定语言后二次确认，调 reset 接口）；
 * 工具区导出（全量 xlsx 文件流，downloadBlob 公共下载）与导入（公共 ImportDialog 弹窗，
 * 固定覆盖导入，方案设计 9.2 节前端对齐）。
 */
const { t } = useI18n()

const isI18nAdmin = computed(() => hasAuth('SYS_I18N_ADMIN'))

/* ========== 模块下拉与矩阵加载 ========== */

const loading = ref(false)
const submitting = ref(false)
const filters = reactive({
  fieldModel: '',
  fieldKey: ''
})
const modelOptions = ref([])
/** 语言列（启用语言，field_order 升序） */
const matrixLanguages = ref([])
/** 矩阵行：[{ fieldKey, snapshot, messages: {语言码: 文本} }] */
const matrixRows = ref([])

const tableMaxHeight = ref(560)

async function loadModels() {
  try {
    const res = await listModels()
    modelOptions.value = res.data || []
    if (!filters.fieldModel && modelOptions.value.length > 0) {
      filters.fieldModel = modelOptions.value[0].fieldModel
      loadMatrix()
    }
  } catch (error) {
    ElMessage.error(error.message || t('frontend.common.loadFailed'))
  }
}

async function loadMatrix() {
  if (!filters.fieldModel) return
  loading.value = true
  try {
    const res = await listTranslationMatrix(filters.fieldModel)
    const vo = res.data || {}
    matrixLanguages.value = vo.languages || []
    matrixRows.value = (vo.keys || []).map((keyRow) => {
      const rowMessages = {}
      for (const column of matrixLanguages.value) {
        const value = vo.matrix && vo.matrix[keyRow.fieldKey]
        rowMessages[column.fieldCode] = (value && value[column.fieldCode]) || ''
      }
      // matrix 响应未携带翻译记录 fieldId，提交统一按 (key, language) 定位，
      // 由后端 updateBatch 的 (key, language) 分支定位/补建（任务清单 I18N4-FE-001 契约）
      return {
        fieldKey: keyRow.fieldKey,
        snapshot: { ...rowMessages },
        messages: rowMessages
      }
    })
    filters.fieldKey = ''
  } catch (error) {
    ElMessage.error(error.message || t('frontend.common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function handleModelChange() {
  loadMatrix()
}

/* ========== 编辑 diff 与提交 ========== */

/** 改动计数：与快照不同的 (行, 语言) 格子数 */
const changedCount = computed(() => changedDetails.value.length)

/** 本次改动明细：[{ fieldKey, fieldLanguage, before, fieldMessage }] */
const changedDetails = computed(() => {
  const details = []
  for (const row of matrixRows.value) {
    if (filters.fieldKey && !row.fieldKey.includes(filters.fieldKey)) continue
    for (const column of matrixLanguages.value) {
      const code = column.fieldCode
      const before = row.snapshot[code] || ''
      const after = row.messages[code] || ''
      if (before !== after) {
        details.push({
          fieldKey: row.fieldKey,
          fieldLanguage: code,
          before,
          fieldMessage: after
        })
      }
    }
  }
  return details
})

function markChanged() {
  // 变更计数为 computed 实时算出，此函数仅为输入事件的语义锚点
}

const diffDialogVisible = ref(false)

function handleSubmit() {
  if (changedDetails.value.length === 0) return
  diffDialogVisible.value = true
}

async function confirmSubmit() {
  submitting.value = true
  try {
    // 统一走 (key, language) 定位：后端 updateBatch 二选一契约（已有记录更新、空格补建）
    await updateTranslations(
      changedDetails.value.map((item) => ({
        fieldKey: item.fieldKey,
        fieldLanguage: item.fieldLanguage,
        fieldMessage: item.fieldMessage
      }))
    )
    ElMessage.success(t('frontend.common.success'))
    diffDialogVisible.value = false
    loadMatrix()
  } catch (error) {
    ElMessage.error(error.message || t('frontend.common.loadFailed'))
  } finally {
    submitting.value = false
  }
}

/* ========== 重置（资源行 × 语言） ========== */

const resetDialogVisible = ref(false)
const resetTarget = reactive({
  fieldKey: '',
  fieldLanguage: ''
})

function handleResetRow(row) {
  resetTarget.fieldKey = row.fieldKey
  // 多语言列时先选语言：此处用带输入的确认弹窗（简化为取第一启用语言列的场景由用户在
  // 弹窗中改选）；为保持交互简单，语言选择放弹窗内下拉
  resetTarget.fieldLanguage = matrixLanguages.value[0] ? matrixLanguages.value[0].fieldCode : ''
  resetDialogVisible.value = true
}

async function confirmReset() {
  submitting.value = true
  try {
    await resetTranslation(resetTarget.fieldKey, resetTarget.fieldLanguage)
    ElMessage.success(t('frontend.common.success'))
    resetDialogVisible.value = false
    loadMatrix()
  } catch (error) {
    ElMessage.error(error.message || t('frontend.common.loadFailed'))
  } finally {
    submitting.value = false
  }
}

/* ========== 导出 / 导入 ========== */

async function handleExport() {
  try {
    const response = await exportTranslations(filters.fieldModel)
    // 文件流下载走公共工具：解析 Content-Disposition 的 filename*（后端带时间戳命名），
    // fallbackName 仅在响应头解析不到时兜底（方案设计 9.2 节导出下载对齐）
    downloadBlob(response, `translations_${filters.fieldModel}.xlsx`)
  } catch (error) {
    ElMessage.error(error.message || t('frontend.common.loadFailed'))
  }
}

/* ========== 导入（公共 ImportDialog） ========== */

const importDialogVisible = ref(false)
/** 导入结果（SysI18nTranslationImportResultVo，继承公共 ImportResultVo）：
 * 公共字段 total / success / failed / errors 由 ImportDialog 内置渲染；
 * 扩展字段 skippedKeys / skippedLanguages 由页面在确认回调中补充提示 */
const importResult = ref(null)

/**
 * 弹窗确认导入：调 i18n 既有 importTranslations（接口不收 params，不走 common#importExcel）。
 * 成功：结果存入 importResult 供弹窗结果区渲染，跳过项（扩展字段）追加提示，刷新矩阵；
 * 失败：统一 ElMessage.error，弹窗保持打开便于重选文件。
 * @param {{ file: File, params: Object }} payload ImportDialog 确认回调（params 后端不消费）
 */
async function handleConfirmImport({ file }) {
  try {
    const res = await importTranslations(file)
    importResult.value = res.data || null
    const result = importResult.value || {}
    const skippedKeys = result.skippedKeys || []
    const skippedLanguages = result.skippedLanguages || []
    if (skippedKeys.length > 0 || skippedLanguages.length > 0) {
      let message = t('frontend.i18n.translation.importResult', { count: result.success || 0 })
      if (skippedKeys.length > 0) {
        message += t('frontend.i18n.translation.importSkippedKeys', { keys: skippedKeys.join(', ') })
      }
      if (skippedLanguages.length > 0) {
        message += t('frontend.i18n.translation.importSkippedLanguages', { languages: skippedLanguages.join(', ') })
      }
      ElMessage({ type: 'success', message, duration: 6000, showClose: true })
    }
    loadMatrix()
  } catch (error) {
    ElMessage.error(error.message || t('frontend.common.loadFailed'))
  }
}

/** 弹窗关闭：清理结果区，下次打开不残留上次结果 */
function handleImportDialogClose() {
  importResult.value = null
}

onMounted(() => {
  loadModels()
})
</script>

<style scoped>
.lang-code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: normal;
}
.changed-hint {
  color: var(--el-color-warning);
  font-size: 13px;
}
.diff-before {
  color: var(--el-color-danger);
  text-decoration: line-through;
}
.diff-after {
  color: var(--el-color-success);
}
.reset-warning {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.list-total-bar {
  margin-top: 10px;
  text-align: right;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>

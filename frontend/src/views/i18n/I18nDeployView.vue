<template>
  <div class="i18n-deploy">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <span>{{ t('frontend.i18n.deploy.page') }}</span>
          <div class="header-btns">
            <el-button
              v-if="isI18nAdmin"
              type="primary"
              :loading="deploying"
              @click="handleDeploy"
            >
              {{ t('frontend.i18n.deploy.run') }}
            </el-button>
          </div>
        </div>
      </template>

      <p class="deploy-desc">{{ t('frontend.i18n.deploy.desc') }}</p>

      <el-empty v-if="!result" :description="t('frontend.i18n.deploy.empty')" :image-size="80" />

      <el-descriptions v-else :column="2" border>
        <el-descriptions-item :label="t('frontend.i18n.deploy.result.modelCount')">
          {{ result.syncedModelCount }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('frontend.i18n.deploy.result.keyCount')">
          {{ result.syncedKeyCount }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 各语言翻译条数：语言码 → 条数 -->
      <el-table
        v-if="result && languageCountRows.length"
        :data="languageCountRows"
        border
        size="small"
        class="deploy-table"
      >
        <el-table-column prop="fieldCode" :label="t('frontend.i18n.language.column.code')" min-width="160" />
        <el-table-column prop="count" :label="t('frontend.i18n.deploy.result.languageCounts')" width="160" align="center" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { hasAuth } from '@/utils/auth'
import { runDeploy } from '@/api/i18n'

/**
 * 部署页：手动触发「资源 → DB 同步」+ 刷新后端缓存（任务清单 I18N5-FE-001）。
 * 页面仅一个「部署」按钮；点击后展示结果（同步资源数、各语言翻译条数）；
 * 执行中按钮禁用防重复提交；二次确认后执行。
 * D2：前端语言包为动态接口，部署刷新缓存即生效，前端下次拉取即新值。
 */
const { t } = useI18n()

const isI18nAdmin = computed(() => hasAuth('SYS_I18N_ADMIN'))
const deploying = ref(false)
const result = ref(null)

/** 各语言翻译条数展开为行（languageCounts: 语言码 → 条数） */
const languageCountRows = computed(() => {
  const counts = (result.value && result.value.languageCounts) || {}
  return Object.keys(counts).map((code) => ({ fieldCode: code, count: counts[code] }))
})

function handleDeploy() {
  ElMessageBox.confirm(
    t('frontend.i18n.deploy.confirmText'),
    t('frontend.i18n.deploy.confirmTitle'),
    { type: 'warning' }
  )
    .then(async () => {
      deploying.value = true
      try {
        const res = await runDeploy()
        result.value = res.data || null
        ElMessage.success(t('frontend.i18n.deploy.success'))
      } catch (error) {
        ElMessage.error(error.message || t('frontend.common.loadFailed'))
      } finally {
        deploying.value = false
      }
    })
    .catch(() => {})
}
</script>

<style scoped>
.i18n-deploy {
  padding: 16px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-btns {
  display: flex;
  gap: 8px;
}
.deploy-desc {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin: 0 0 16px;
}
.deploy-table {
  margin-top: 16px;
}
</style>

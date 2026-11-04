<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">执行记录详情</span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
        </div>
      </div>
    </template>

    <div v-loading="loading" class="detail-body">
      <template v-if="detail">
        <!-- 基本字段：自定义网格，一行两字段（label 40% / 内容 60%） -->
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">任务名</span>
            <span class="detail-value">{{ detail.fieldTaskName || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">结果</span>
            <span class="detail-value">
              <el-tag :type="detail.fieldResult === '1' ? 'success' : 'danger'" size="small">
                {{ RESULT_TEXT[detail.fieldResult] || '-' }}
              </el-tag>
            </span>
          </div>
          <div class="detail-item">
            <span class="detail-label">开始时间</span>
            <span class="detail-value">{{ formatTime(detail.fieldStartTime) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">结束时间</span>
            <span class="detail-value">{{ formatTime(detail.fieldEndTime) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">执行组</span>
            <span class="detail-value">{{ detail.fieldGroup || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">执行参数</span>
            <span class="detail-value">
              <template v-if="paramsRows.length">
                <div v-for="(item, index) in paramsRows" :key="index" class="params-line">
                  {{ item.key }} = {{ item.value }}
                </div>
              </template>
              <span v-else>-</span>
            </span>
          </div>
        </div>

        <!-- 日志：独占一行 2:8 比例（label 20% / 内容 80%） -->
        <div class="detail-log">
          <span class="detail-label">日志</span>
          <pre class="log-box">{{ detail.fieldLog || '（无日志）' }}</pre>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="未找到该执行记录" :image-size="80" />
    </div>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loadTaskHistoryDetail } from '@/api/task'
import { getDictTextMap } from '@/utils/dict'

/**
 * 执行记录详情页：/taskHistory/detail/:id。
 * 按路由 id 经 load 接口展示；字段：任务名、结果、开始时间、结束时间、执行组、
 * 执行参数（JSON 解析为 key=value 行）与日志（只读文本）。
 * 结果文本（dict.task_result）由 dict 模块提供。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detail = ref(null)

/** 结果文本映射（dict.task_result） */
const RESULT_TEXT = ref({})

const paramsRows = computed(() => {
  const json = detail.value && detail.value.fieldParams
  if (!json) return []
  try {
    const map = typeof json === 'string' ? JSON.parse(json) : json
    if (!map || typeof map !== 'object') return []
    return Object.entries(map).map(([key, value]) => ({ key, value: String(value) }))
  } catch {
    return []
  }
})

async function loadDetail() {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少执行记录 ID')
    goBack()
    return
  }
  loading.value = true
  try {
    const res = await loadTaskHistoryDetail(id)
    detail.value = res.data
    if (!detail.value) {
      ElMessage.error('未找到该执行记录')
    }
  } catch (error) {
    ElMessage.error(error.message || '加载数据失败')
    goBack()
  } finally {
    loading.value = false
  }
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function goBack() {
  router.push('/taskHistory')
}

onMounted(() => {
  loadDictOptions()
  loadDetail()
})

/** 加载执行结果文本映射（dict.task_result）；失败时结果回退原值 */
async function loadDictOptions() {
  try {
    RESULT_TEXT.value = await getDictTextMap('task_result')
  } catch {
    RESULT_TEXT.value = {}
  }
}
</script>

<script>
export default { name: 'TaskHistoryDetailView' }
</script>

<style scoped>
.detail-body {
  min-height: 120px;
}

/* —— 基本字段网格：一行两字段，字段名:字段值 = 2:3（label 40% / 内容 60%） —— */
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  row-gap: 14px;
  column-gap: 24px;
  margin-bottom: 8px;
}

.detail-item {
  display: flex;
  align-items: flex-start;
}

.detail-label {
  flex-shrink: 0;
  width: 40%;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.detail-value {
  width: 60%;
  color: var(--el-text-color-primary);
  font-size: 13px;
  word-break: break-all;
}

.params-line {
  line-height: 1.8;
}

/* —— 日志：独占一行 2:8 比例（label 20% / 内容 80%） —— */
.detail-log {
  display: flex;
  align-items: flex-start;
  margin-top: 8px;
}

.detail-log .detail-label {
  width: 20%;
}

.log-box {
  width: 80%;
  margin: 0;
  padding: 10px 12px;
  min-height: 120px;
  max-height: 360px;
  overflow: auto;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-primary);
  background: #f7f8fa;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>

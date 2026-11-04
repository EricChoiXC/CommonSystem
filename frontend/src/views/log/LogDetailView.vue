<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">
          日志详情
          <el-tag :type="row.fieldLogType === '1' ? 'primary' : 'warning'" size="small" style="margin-left: 8px">
            {{ LOG_TYPE_TEXT[row.fieldLogType] || row.fieldLogType || '-' }}
          </el-tag>
        </span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
        </div>
      </div>
    </template>

    <!-- 详情网格：默认一行两字段（字段名:内容 = 2:3）；长内容字段（is-full）一行一字段（2:8） -->
    <div v-if="loaded" class="detail-grid">
      <div
        v-for="item in detailFields"
        :key="item.label"
        class="detail-item"
        :class="{ 'is-full': item.full }"
      >
        <span class="detail-label">{{ item.label }}</span>
        <span class="detail-value">
          <el-tag v-if="item.tag" :type="item.resultCode === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ item.value }}
          </el-tag>
          <template v-else>{{ item.value }}</template>
        </span>
      </div>
    </div>
    <el-empty v-else description="加载中或数据不存在" />
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLogDetail } from '@/api/log'
import { getDictTextMap } from '@/utils/dict'

/**
 * 日志详情页：/log/detail/:id，按路由 id 经 load 接口加载数据只读展示。
 * 详情布局采用自定义网格（见 styles/form-page.css）：
 * 默认一行两字段 2:3，长内容字段（变更前/变更后 JSON）一行一字段 2:8。
 * 日志只读，底部仅提供「返回」。
 * 动作文本（dict.log_action）由 dict 模块提供。
 */
const route = useRoute()
const router = useRouter()

const row = ref({})
const loaded = ref(false)

/** 动作文本映射（dict.log_action） */
const ACTION_TEXT = ref({})
/** 结果文本映射（dict.log_result） */
const RESULT_TEXT = ref({})
/** 日志类型文本映射（dict.log_type） */
const LOG_TYPE_TEXT = ref({})

/** 详情字段：按日志类型动态组装（操作日志带模块/动作/业务文档ID/变更内容，登录日志带登录名） */
const detailFields = computed(() => {
  const r = row.value
  if (!r.fieldId) return []
  const fields = [
    { label: '操作时间', value: formatTime(r.fieldOperateTime) },
    { label: '操作人', value: r.fieldUserName || '-' }
  ]
  if (r.fieldLogType === '1') {
    fields.push(
      { label: '模块', value: r.fieldModuleName || r.fieldModule || '-' },
      { label: '动作', value: ACTION_TEXT.value[r.fieldAction] || r.fieldAction || '-' },
      { label: '业务文档ID', value: r.fieldBusinessId || '-' }
    )
  } else {
    fields.push({ label: '登录名', value: r.fieldLoginName || '-' })
  }
  fields.push(
    { label: '结果', value: resultText(r.fieldResult), tag: true, resultCode: r.fieldResult },
    { label: 'IP', value: r.fieldIp || '-' },
    { label: '备注', value: r.fieldRemark || '-' },
    { label: '失败原因', value: r.fieldFailReason || '-' },
    { label: '客户端UA', value: r.fieldUserAgent || '-' }
  )
  if (r.fieldLogType === '1') {
    fields.push({ label: '变更前JSON', value: r.fieldBefore || '-', full: true })
    fields.push({ label: '变更后JSON', value: r.fieldAfter || '-', full: true })
  }
  return fields
})

onMounted(async () => {
  const id = route.params.id
  if (!id) {
    ElMessage.warning('缺少日志标识，无法查看详情')
    router.replace('/log')
    return
  }
  loadDictOptions()
  try {
    const res = await getLogDetail(id)
    if (!res || !res.data) {
      ElMessage.warning('未找到该日志')
      router.replace('/log')
      return
    }
    row.value = res.data
    loaded.value = true
  } catch (error) {
    ElMessage.error(error.message || '加载失败')
    router.replace('/log')
  }
})

/** 加载动作/结果/日志类型文本映射（dict.log_action / log_result / log_type）；失败时文本回退原值 */
async function loadDictOptions() {
  try {
    const [actionTextMap, resultTextMap, logTypeTextMap] = await Promise.all([
      getDictTextMap('log_action'),
      getDictTextMap('log_result'),
      getDictTextMap('log_type')
    ])
    ACTION_TEXT.value = actionTextMap
    RESULT_TEXT.value = resultTextMap
    LOG_TYPE_TEXT.value = logTypeTextMap
  } catch {
    ACTION_TEXT.value = {}
    RESULT_TEXT.value = {}
    LOG_TYPE_TEXT.value = {}
  }
}

function resultText(value) {
  return RESULT_TEXT.value[value] || value || '-'
}

function goBack() {
  router.push('/log')
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
</script>

<script>
export default { name: 'LogDetailView' }
</script>

<style scoped>
/* 页面容器、详情网格、底部操作区等公共样式见 styles/form-page.css */
</style>

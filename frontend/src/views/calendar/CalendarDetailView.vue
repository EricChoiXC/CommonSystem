<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">日程详情</span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
          <el-button v-if="canEdit" type="primary" @click="goEdit">编辑</el-button>
        </div>
      </div>
    </template>

    <div v-if="detail.row" class="detail-grid">
      <template v-for="(item, index) in detailFields" :key="item.placeholder ? `__placeholder__${index}` : item.label">
        <div v-if="item.placeholder" class="detail-item detail-item-placeholder">
          <span class="detail-label"></span>
          <span class="detail-value"></span>
        </div>
        <div v-else class="detail-item" :class="{ 'is-full': item.full }">
          <span class="detail-label">{{ item.label }}</span>
          <span class="detail-value">
            <el-tag v-if="item.tag" :type="item.tagType" size="small">{{ item.value }}</el-tag>
            <template v-else>{{ item.value }}</template>
          </span>
        </div>
      </template>
    </div>
    <el-empty v-else description="加载中或数据不存在" />

    <!-- 接收情况（团队日程详情页显示） -->
    <div v-if="detail.row && detail.row.fieldType === 'TEAM' && detail.row.persons" class="accept-section">
      <h3 class="section-title">接收情况</h3>
      <el-table :data="sortedPersons" border stripe>
        <el-table-column prop="fieldPersonName" label="接收人" min-width="120" />
        <el-table-column prop="fieldAccept" label="接收状态" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.fieldAccept === true" type="success" size="small">已接收</el-tag>
            <el-tag v-else-if="row.fieldAccept === false" type="danger" size="small">已拒绝</el-tag>
            <el-tag v-else type="info" size="small">未确认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" v-if="showAcceptButtons">
          <template #default="{ row }">
            <template v-if="row.fieldPersonId === currentUserId">
              <el-button v-if="row.fieldAccept !== true" type="success" size="small" @click="handleAccept(true)">接收</el-button>
              <el-button v-if="row.fieldAccept !== false" type="danger" size="small" @click="handleAccept(false)">拒绝</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loadCalendar, acceptCalendar } from '@/api/calendar'
import { useUserStore } from '@/stores/user'
import { hasAuth } from '@/utils/auth'
import { buildDetailGridFields } from '@/utils/detail'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const currentUserId = computed(() => userStore.userInfo?.fieldId || '')

const detail = reactive({ row: null })

const TYPE_TEXT = { PERSONAL: '个人日程', TEAM: '团队日程' }

const canEdit = computed(() => {
  const r = detail.row
  return r && currentUserId.value === r.fieldCreatorId
})

/** 当前用户在团队日程中是否能看到接收/拒绝按钮 */
const showAcceptButtons = computed(() => {
  return detail.row && detail.row.fieldType === 'TEAM'
})

/** 接收人排序：当前接收人排第一，其他按id排序 */
const sortedPersons = computed(() => {
  if (!detail.row || !detail.row.persons) return []
  const persons = [...detail.row.persons]
  persons.sort((a, b) => {
    if (a.fieldPersonId === currentUserId.value) return -1
    if (b.fieldPersonId === currentUserId.value) return 1
    return (a.fieldPersonId || '').localeCompare(b.fieldPersonId || '')
  })
  return persons
})

const detailFields = computed(() => {
  const r = detail.row
  if (!r) return []
  const fields = [
    { label: '标题', value: r.fieldName || '' },
    { label: '类型', value: TYPE_TEXT[r.fieldType] || r.fieldType, tag: true, tagType: r.fieldType === 'TEAM' ? 'warning' : 'info' },
    { label: '日程开始时间', value: formatDateTime(r.fieldStartTime) },
    { label: '日程结束时间', value: formatDateTime(r.fieldEndTime) }
  ]
  if (r.fieldContent) {
    fields.push({ label: '内容', value: r.fieldContent, full: true })
  }
  return buildDetailGridFields(fields)
})

function formatDateTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${h}:${min}`
}

onMounted(async () => {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少查看对象的 ID')
    goBack()
    return
  }
  try {
    const res = await loadCalendar(id)
    detail.row = res.data || null
    if (!detail.row) {
      ElMessage.warning('未找到该日程')
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
    goBack()
  }
})

async function handleAccept(accept) {
  try {
    await acceptCalendar(detail.row.fieldId, accept)
    ElMessage.success(accept ? '已接收' : '已拒绝')
    // 刷新详情
    const res = await loadCalendar(detail.row.fieldId)
    detail.row = res.data || null
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

function goBack() {
  router.push('/calendar')
}

function goEdit() {
  router.push('/calendar/edit/' + detail.row.fieldId)
}
</script>

<script>
export default { name: 'CalendarDetailView' }
</script>

<style scoped>
.accept-section {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}
</style>
<template>
  <div class="att-operation">
    <!-- 附件信息条：展示当前附件，来自附件列表跳转携带的 query -->
    <div class="att-info">
      <el-tag type="primary" size="small">附件</el-tag>
      <span class="att-name">{{ attName || '未知附件' }}</span>
      <span class="att-id">{{ fieldId }}</span>
    </div>

    <!-- 筛选条件区：操作类型 -->
    <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="操作类型">
            <el-select v-model="filters.fieldOperateType" style="width: 100%">
              <el-option v-for="opt in OPERATE_TYPE_OPTIONS" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 工具区：左侧排序（仅操作时间）+ 右侧按钮 -->
    <div class="list-toolbar">
      <div class="sort-area">
        <span
          v-for="opt in SORT_OPTIONS"
          :key="opt.value"
          class="sort-item"
          :class="{ active: sortField === opt.value }"
          @click="toggleSort(opt.value)"
        >
          {{ opt.label }}
          <i v-if="sortField === opt.value" class="sort-arrow">{{ sortOrder === 'desc' ? '↓' : '↑' }}</i>
        </span>
      </div>
      <div class="btn-area">
        <el-button type="primary" @click="handleQuery">查询</el-button>
        <el-button @click="goBack">返回附件列表</el-button>
      </div>
    </div>

    <!-- 列表区：操作日志只读展示，无操作列 -->
    <el-table :data="list" v-loading="loading" border stripe row-key="fieldId">
      <el-table-column prop="fieldOperatorName" label="操作人" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fieldOperatorName || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作类型" width="110">
        <template #default="{ row }">
          <el-tag :type="operateTypeTag(row.fieldOperateType)" size="small">
            {{ operateTypeText(row.fieldOperateType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作时间" width="170">
        <template #default="{ row }">{{ formatTime(row.fieldOperateTime) }}</template>
      </el-table-column>
      <el-table-column prop="fieldRemark" label="操作备注" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fieldRemark || '-' }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="queryPage"
      v-model:page-size="queryPageSize"
      class="list-pagination"
      :total="total"
      layout="total, prev, pager, next, sizes"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="loadList"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { queryAttOperationList } from '@/api/attachment'
import { getDictOptions, getDictTextMap } from '@/utils/dict'

/**
 * 附件操作日志页（后台管理，SYS_ATT_MAIN）：从附件管理列表「日志」按钮跳转进入，
 * 按 field_att_id 查询该附件的操作日志记录（sys_att_operation），只读展示。
 * 筛选项为操作类型（默认「不限」）；显示项为操作人、操作类型、操作时间、操作备注；
 * 排序仅「操作时间」维度（默认降序）；无行操作。
 */

/** 操作类型筛选（dict.att_operate_type，字符串值；null=不限） */
const OPERATE_TYPE_OPTIONS = ref([{ label: '不限', value: null }])
/** 操作类型文本映射（dict.att_operate_type） */
const OPERATE_TYPE_TEXT = ref({})
const SORT_OPTIONS = [{ value: 'operateTime', label: '操作时间' }]

const route = useRoute()
const router = useRouter()

const fieldId = route.params.id || ''
const attName = route.query.name || ''

const filters = reactive({
  fieldOperateType: null // 默认「不限」
})

const sortField = ref('operateTime') // 仅操作时间
const sortOrder = ref('desc') // 默认降序

const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

function operateTypeText(value) {
  return OPERATE_TYPE_TEXT.value[value] || '-'
}

function operateTypeTag(value) {
  return {
    1: 'success',
    2: 'primary',
    3: 'danger',
    4: 'danger',
    5: 'info',
    6: 'info'
  }[value] || 'info'
}

/** 仅操作时间一个排序维度：点击切换升降序 */
function toggleSort() {
  sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  queryPage.value = 1
  loadList()
}

function handleQuery() {
  queryPage.value = 1
  loadList()
}

function handleSizeChange() {
  queryPage.value = 1
  loadList()
}

async function loadList() {
  loading.value = true
  try {
    const res = await queryAttOperationList({
      body: {
        fieldAttId: fieldId,
        fieldOperateType: filters.fieldOperateType
      },
      pages: { pageNo: queryPage.value, pageSize: queryPageSize.value, needPage: true },
      sortOrder: sortOrder.value
    })
    list.value = res.data || []
    total.value = (res.metadata && res.metadata.total) || list.value.length
  } catch (error) {
    ElMessage.error(error.message || '查询失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/att')
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

onMounted(() => {
  // 缺附件 id 时提示并返回附件列表页（同查看页规范）
  if (!fieldId) {
    ElMessage.warning('缺少附件标识，无法查看操作日志')
    router.replace('/att')
    return
  }
  loadDictOptions()
  loadList()
})

/** 加载操作类型选项与文本映射（dict.att_operate_type）；失败时保留「不限」兜底 */
async function loadDictOptions() {
  try {
    const [options, textMap] = await Promise.all([
      getDictOptions('att_operate_type'),
      getDictTextMap('att_operate_type')
    ])
    OPERATE_TYPE_OPTIONS.value = [{ label: '不限', value: null }, ...options]
    OPERATE_TYPE_TEXT.value = textMap
  } catch {
    OPERATE_TYPE_OPTIONS.value = [{ label: '不限', value: null }]
    OPERATE_TYPE_TEXT.value = {}
  }
}
</script>

<script>
export default { name: 'AttOperationView' }
</script>

<style scoped>
.att-operation {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */

.att-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0 12px;
}
.att-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.att-id {
  font-size: 12px;
  color: #909399;
}
</style>

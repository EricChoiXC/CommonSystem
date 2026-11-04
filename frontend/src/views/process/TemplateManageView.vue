<template>
  <div class="template-manage">
    <!-- ============ 左侧：模板分类（process_template 配置） ============ -->
    <div class="template-left">
      <div class="template-left-title">模板分类</div>
      <div v-if="models.length" class="model-list">
        <div
          v-for="m in models"
          :key="m.fieldModelName"
          class="model-item"
          :class="{ active: currentModel === m.fieldModelName }"
          @click="selectModel(m)"
        >
          <span class="model-item-name">{{ m.name }}</span>
          <span class="model-item-key">{{ m.fieldModelName }}</span>
        </div>
      </div>
      <el-empty v-else description="暂无模板分类（process_template 未配置）" :image-size="60" />
    </div>

    <!-- ============ 右侧：筛选 + 排序/按钮 + 列表 ============ -->
    <div class="template-right">
      <!-- 筛选条件区：四分栅栏排列 -->
      <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="模板名称">
              <el-input v-model="filters.fieldName" placeholder="请输入模板名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="模板编号">
              <el-input v-model="filters.fieldCode" placeholder="请输入模板编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="使用启用">
              <el-select v-model="filters.enable" style="width: 100%">
                <el-option label="不限" :value="null" />
                <el-option label="启用" :value="true" />
                <el-option label="停用" :value="false" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 工具区：左侧排序 + 右侧按钮 -->
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
          <el-button v-if="canEdit" type="success" @click="goCreate">新建</el-button>
        </div>
      </div>

      <!-- 列表区：末列操作按钮 -->
      <el-table :data="list" v-loading="loading" border stripe row-key="fieldId">
        <el-table-column prop="fieldName" label="模板名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="fieldCode" label="模板编号" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fieldCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="使用启用" width="90">
          <template #default="{ row }">
            <el-tag :type="row.fieldEnable ? 'success' : 'info'" size="small">{{ row.fieldEnable ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.fieldDefault" type="warning" size="small">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.fieldCreateTime) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.fieldUpdateTime) }}</template>
        </el-table-column>
        <el-table-column label="版本号" width="80" align="center">
          <template #default="{ row }">v{{ row.fieldVersion }}</template>
        </el-table-column>
        <el-table-column label="操作" :width="canEdit ? 240 : 90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">详情</el-button>
            <el-button v-if="canEdit" link type="primary" @click="goEdit(row)">编辑</el-button>
            <el-button v-if="canEdit" link type="warning" :disabled="!row.fieldEnable || row.fieldDefault" @click="handleSetDefault(row)">
              {{ row.fieldDefault ? '默认' : '置为默认' }}
            </el-button>
            <el-button v-if="canEdit" link :type="row.fieldEnable ? 'danger' : 'success'" @click="handleToggleEnable(row)">
              {{ row.fieldEnable ? '停用' : '启用' }}
            </el-button>
          </template>
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
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasAuth } from '@/utils/auth'
import {
  enableProcessTemplate,
  queryProcessTemplateList,
  queryProcessTemplateModels,
  setProcessTemplateDefault
} from '@/api/process'

/**
 * 流程模板管理页：左侧模板分类（来自各模块 application.yml 的 process_template 配置，
 * 选中项作为 field_model_name 固定放入查询）+ 右侧列表查询。
 * 右侧支持模板名称/编号模糊、使用启用筛选，单条件排序（创建时间/更新时间）与分页；
 * 操作列提供详情/编辑/启停用；新建/编辑/查看跳转独立路由页面。
 * 依据 artificial/docs/设计文档/sys/process/流程核心业务.md「流程模板」。
 */

const SORT_OPTIONS = [
  { value: 'createTime', label: '创建时间' },
  { value: 'updateTime', label: '更新时间' }
]

/** 新建/编辑/启停用所需权限：与后端 module.save / module.update / module.enable 鉴权一致 */
const AUTH_TEMPLATE_ADMIN = ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_TEMPLATE_ADMIN']

const router = useRouter()

/** 新建/编辑/启停用按钮可见性（预判，最终权限由后端 ProcessResolver 兜底） */
const canEdit = computed(() => hasAuth(AUTH_TEMPLATE_ADMIN))

/* ============ 左侧分类 ============ */

const models = ref([])
const currentModel = ref('')
const currentModelName = ref('')

async function loadModels() {
  try {
    const res = await queryProcessTemplateModels()
    models.value = res.data || []
    if (models.value.length) {
      // 默认选中第一个分类
      selectModel(models.value[0])
    } else {
      currentModel.value = ''
      currentModelName.value = ''
      list.value = []
      total.value = 0
    }
  } catch (error) {
    ElMessage.error(error.message || '模板分类加载失败')
  }
}

function selectModel(model) {
  currentModel.value = model.fieldModelName
  currentModelName.value = model.name
  queryPage.value = 1
  loadList()
}

/* ============ 筛选 / 排序 / 列表 ============ */

const filters = reactive({
  fieldName: '',
  fieldCode: '',
  enable: true // 默认「启用」
})

const sortField = ref('createTime') // 默认创建时间
const sortOrder = ref('desc') // 默认降序

const queryPage = ref(1)
const queryPageSize = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

/** 单选条件排序：点击已选中项切换升降序，点击未选中项选中并默认降序 */
function toggleSort(value) {
  if (sortField.value === value) {
    sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  } else {
    sortField.value = value
    sortOrder.value = 'desc'
  }
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
  if (!currentModel.value) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const res = await queryProcessTemplateList({
      body: {
        fieldModelName: currentModel.value,
        fieldName: filters.fieldName || null,
        fieldCode: filters.fieldCode || null,
        enable: filters.enable
      },
      pages: { pageNo: queryPage.value, pageSize: queryPageSize.value, needPage: true },
      sortField: sortField.value,
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

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/* ============ 路由跳转（新建/编辑/查看） ============ */

/** 新建：跳转独立路由，携带当前分类作为默认所属模块 */
function goCreate() {
  router.push({
    path: '/processTemplate/create',
    query: { modelName: currentModel.value, modelLabel: currentModelName.value }
  })
}

function goDetail(row) {
  router.push(`/processTemplate/detail/${row.fieldId}`)
}

function goEdit(row) {
  router.push(`/processTemplate/edit/${row.fieldId}`)
}

/* ============ 置为默认 / 停用 / 启用 ============ */

/** 置为默认模板：替换同分类现有默认模板（每分类最多一个默认模板） */
async function handleSetDefault(row) {
  try {
    await ElMessageBox.confirm(
      `确认将「${row.fieldName}」置为默认模板？该分类现有默认模板将被替换，未指定流程模板的单据将按此模板启动流程。`,
      '操作确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await setProcessTemplateDefault({ fieldId: row.fieldId })
    ElMessage.success('已置为默认模板')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

async function handleToggleEnable(row) {
  const enable = !row.fieldEnable
  try {
    await ElMessageBox.confirm(
      `确认将「${row.fieldName}」${enable ? '启用' : '停用'}？${enable ? '' : '停用后该模板将不可再发起新的流程。'}`,
      '操作确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await enableProcessTemplate({ fieldId: row.fieldId, fieldEnable: enable })
    ElMessage.success(enable ? '已启用' : '已停用')
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

/* ============ 初始化 ============ */

onMounted(() => {
  loadModels()
})
// KeepAlive 页签切换返回时刷新列表数据（保留筛选与分页状态；首次进入由 onMounted 加载）
let firstActivation = true
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  loadList()
})
</script>

<script>
export default { name: 'TemplateManageView' }
</script>

<style scoped>
.template-manage {
  display: flex;
  height: 100%;
  min-height: 480px;
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}

/* —— 左侧分类 —— */
.template-left {
  flex-shrink: 0;
  width: 220px;
  border-right: 1px solid var(--el-border-color-lighter);
  padding: 8px;
  overflow: auto;
}

.template-left-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  padding: 6px 8px 10px;
}

.model-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  margin-bottom: 4px;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.model-item:hover {
  color: #409eff;
  background: var(--el-color-primary-light-9);
}

.model-item.active {
  color: #409eff;
  border-color: #409eff;
  background: var(--el-color-primary-light-9);
}

.model-item-name {
  font-size: 13px;
}

.model-item-key {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* —— 右侧区域 —— */
.template-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 12px;
}
/* 筛选条件区、工具区（排序/按钮）、分页等公共样式见 styles/list-page.css */
</style>

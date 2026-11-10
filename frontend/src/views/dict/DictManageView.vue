<template>
  <div class="dict-manage">
    <!-- 左右分栏：左侧码值类型列表，右侧码值项列表 -->
    <div class="dict-layout">
      <!-- 左栏：类型过滤 + 类型列表（按 field_code 排序，不可维护） -->
      <aside class="dict-types">
        <div class="dict-types-title">码值类型</div>
        <el-input
          v-model="keyword"
          placeholder="筛选名称或编号"
          clearable
          class="dict-types-filter"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div class="dict-types-list">
          <div
            v-for="type in filteredTypes"
            :key="type.fieldId"
            class="dict-type-item"
            :class="{ active: currentType && currentType.fieldId === type.fieldId }"
            @click="handleSelectType(type)"
          >
            <div class="dict-type-code">{{ type.fieldCode }}</div>
            <div class="dict-type-name">{{ type.fieldName || '-' }}</div>
          </div>
          <el-empty v-if="!filteredTypes.length" description="无码值类型" :image-size="60" />
        </div>
      </aside>

      <!-- 右栏：码值项列表（无筛选区、无排序区，固定按 field_value 排序） -->
      <!-- 导出过程 v-loading 遮罩（本期不做进度条） -->
      <section class="dict-items" v-loading="exporting">
        <div class="dict-items-header">
          <div>
            <span class="dict-items-title">
              {{ currentType ? currentType.fieldName : '码值项' }}
            </span>
            <span v-if="currentType" class="dict-items-code">{{ currentType.fieldCode }}</span>
            <span v-if="currentType && currentType.fieldDesc" class="dict-items-desc">
              {{ currentType.fieldDesc }}
            </span>
          </div>
          <!-- 工具区右侧按钮（仅 SYS_DICT_ADMIN 级用户可见，hasAuth 角色码预判，后端最终鉴权兜底）：
               导入/导出走公共导入导出通道，导出恒全量（与当前选中类型无关）；
               模板下载置于导入弹窗内，工具区只保留导入 + 导出（假设 A5） -->
          <div v-if="isDictAdmin" class="dict-items-actions">
            <el-button :loading="exporting" @click="handleExport">导出</el-button>
            <el-button @click="importVisible = true">导入</el-button>
            <el-button type="primary" :disabled="!currentType" @click="openCreate">新增</el-button>
          </div>
        </div>

        <el-table :data="itemList" v-loading="loading" border stripe row-key="fieldId">
          <template #empty>
            <el-empty description="当前类型下暂无码值项" :image-size="80" />
          </template>
          <el-table-column prop="fieldValue" label="码值" width="160" show-overflow-tooltip />
          <el-table-column prop="fieldName" label="显示值" min-width="200" show-overflow-tooltip />
          <el-table-column label="启用" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.fieldEnable ? 'success' : 'danger'" size="small">
                {{ row.fieldEnable ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.fieldSystem" type="warning" size="small">系统</el-tag>
              <el-tag v-else type="primary" size="small" effect="plain">人工</el-tag>
            </template>
          </el-table-column>
          <el-table-column
            v-if="isDictAdmin"
            label="操作"
            width="90"
            align="center"
            fixed="right"
          >
            <template #default="{ row }">
              <el-tooltip
                :content="row.fieldSystem ? '系统码值不可维护' : '编辑'"
                placement="top"
              >
                <span>
                  <el-button
                    link
                    type="primary"
                    :disabled="!!row.fieldSystem"
                    @click="openEdit(row)"
                  >
                    编辑
                  </el-button>
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <!-- 新增/编辑码值项弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增码值' : '编辑码值'"
      width="460px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="码值" prop="fieldValue">
          <el-input v-model="form.fieldValue" placeholder="请输入码值，如 1 / active" maxlength="50" />
        </el-form-item>
        <el-form-item label="显示值" prop="fieldName">
          <el-input v-model="form.fieldName" placeholder="请输入显示值，如 是 / 启用" maxlength="200" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.fieldEnable" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入码值项弹窗（公共 ImportDialog，经 components/index.js 具名导出引入）：
         templatePath 指向后端模板接口；dict 导入本期只有公共开关 ignoreError（组件内置渲染），
         无业务配置项故 configFields 传空数组；templateBody 走组件默认（后端模板接口不消费请求体）；
         导入结果 ImportResultVo 传回 props.result 由组件内置渲染（errors[].row 为文件行号） -->
    <ImportDialog
      v-model="importVisible"
      :template-path="DICT_TEMPLATE_PATH"
      :config-fields="[]"
      :result="importResult"
      @confirm="handleImportConfirm"
    />
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { hasAuth } from '@/utils/auth'
import { ImportDialog } from '@/components'
import { exportExcel, importExcel } from '@/api/common'
import { downloadBlob } from '@/utils/download'
import {
  queryDictTypeList,
  queryDictItemList,
  saveDictItem,
  updateDictItem
} from '@/api/dict'

/**
 * 码值管理页（后台管理，全员可见）：左侧码值类型列表（按 field_code 排序，
 * 顶部过滤文本框可快速筛选名称或编号，类型不可维护），右侧为选中类型下的
 * 码值项列表（无筛选区、无排序区，固定按 field_value 排序）。
 * 维护操作（新增/编辑、导入/导出/模板下载）仅 SYS_DICT_ADMIN 级用户可见；field_system = true 的
 * 系统码值不可维护（编辑按钮禁用，后端最终鉴权与拦截）。导入/导出走公共导入导出通道。
 */
const DICT_ADMIN_AUTH = 'SYS_DICT_ADMIN'

const isDictAdmin = computed(() => hasAuth(DICT_ADMIN_AUTH))

const keyword = ref('')
const typeList = ref([])
const currentType = ref(null)
const itemList = ref([])
const loading = ref(false)

/** 前端快速筛选：匹配码值类型的名称或编号 */
const filteredTypes = computed(() => {
  const kw = (keyword.value || '').trim().toLowerCase()
  if (!kw) return typeList.value
  return typeList.value.filter(
    (type) =>
      (type.fieldName || '').toLowerCase().includes(kw) ||
      (type.fieldCode || '').toLowerCase().includes(kw)
  )
})

async function loadTypes() {
  try {
    const res = await queryDictTypeList()
    typeList.value = res.data || []
    // 默认选中第一个类型；当前选中类型不在结果中时回退到第一个
    const currentId = currentType.value && currentType.value.fieldId
    const next =
      typeList.value.find((type) => type.fieldId === currentId) || typeList.value[0] || null
    currentType.value = next
    if (next) {
      await loadItems(next.fieldId)
    } else {
      itemList.value = []
    }
  } catch (error) {
    ElMessage.error(error.message || '码值类型加载失败')
    typeList.value = []
    currentType.value = null
    itemList.value = []
  }
}

function handleSelectType(type) {
  currentType.value = type
  loadItems(type.fieldId)
}

async function loadItems(fieldTypeId) {
  loading.value = true
  try {
    const res = await queryDictItemList(fieldTypeId)
    itemList.value = res.data || []
  } catch (error) {
    ElMessage.error(error.message || '码值项加载失败')
    itemList.value = []
  } finally {
    loading.value = false
  }
}

/* ========== 新增 / 编辑弹窗 ========== */

const dialogVisible = ref(false)
const dialogMode = ref('create')
const saving = ref(false)
const formRef = ref(null)
const form = reactive({
  fieldId: '',
  fieldTypeId: '',
  fieldValue: '',
  fieldName: '',
  fieldEnable: true
})

const formRules = {
  fieldValue: [{ required: true, message: '请输入码值', trigger: 'blur' }],
  fieldName: [{ required: true, message: '请输入显示值', trigger: 'blur' }]
}

function openCreate() {
  dialogMode.value = 'create'
  form.fieldId = ''
  form.fieldTypeId = currentType.value ? currentType.value.fieldId : ''
  form.fieldValue = ''
  form.fieldName = ''
  form.fieldEnable = true
  dialogVisible.value = true
}

function openEdit(row) {
  dialogMode.value = 'edit'
  form.fieldId = row.fieldId
  form.fieldTypeId = row.fieldTypeId
  form.fieldValue = row.fieldValue
  form.fieldName = row.fieldName
  form.fieldEnable = !!row.fieldEnable
  dialogVisible.value = true
}

function resetForm() {
  formRef.value && formRef.value.clearValidate()
}

async function handleSave() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await saveDictItem({
        fieldTypeId: form.fieldTypeId,
        fieldValue: form.fieldValue,
        fieldName: form.fieldName,
        fieldEnable: form.fieldEnable
      })
      ElMessage.success('新增成功')
    } else {
      await updateDictItem({
        fieldId: form.fieldId,
        fieldTypeId: form.fieldTypeId,
        fieldValue: form.fieldValue,
        fieldName: form.fieldName,
        fieldEnable: form.fieldEnable
      })
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await loadItems(form.fieldTypeId)
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/* ========== 导出 / 导入（公共导入导出通道，IE4-FE-001） ========== */

/** 导入弹窗模板下载路径（POST {path}/template；后端 @RequestBody(required=false)，不消费请求体） */
const DICT_TEMPLATE_PATH = '/backend/sys/dict/template'

/** 导出过程 v-loading 遮罩 */
const exporting = ref(false)
/** 导入弹窗显隐（v-model） */
const importVisible = ref(false)
/** 导入结果 ImportResultVo（传回 ImportDialog 内置渲染：成功/失败计数 + errors 明细表） */
const importResult = ref(null)

/**
 * 组装导出筛选条件：body 与 /list 同构（字段名与 SysDictItem Model 一致），经 BaseRequest 包装提交；
 * 码值项列表无筛选区、无排序区，当前条件恒为空对象 → 导出恒全量（field_value 升序）；
 * 后续若增加筛选条件，在此回填即可。
 */
function buildDictItemCriteria() {
  return {}
}

/**
 * 导出码值项：exportExcel 文件流响应 → downloadBlob 触发保存
 * （文件名解析 Content-Disposition，解析不到回退 dict_items.xlsx）。
 */
async function handleExport() {
  exporting.value = true
  try {
    const response = await exportExcel('/backend/sys/dict/export', {
      body: buildDictItemCriteria()
    })
    downloadBlob(response, 'dict_items.xlsx')
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error(error.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

/**
 * 导入确认（ImportDialog @confirm 回调 { file, params }）：
 * importExcel multipart 提交（params 含公共开关 ignoreError，后端默认 false 任一行失败整体回滚）；
 * 成功结果传回 ImportDialog 内置渲染（errors[].row 为文件行号），并用 ElMessage 反馈成功/失败计数。
 */
async function handleImportConfirm({ file, params }) {
  try {
    const res = await importExcel('/backend/sys/dict/import', file, params)
    const result = res.data || {}
    importResult.value = result
    const success = result.success || 0
    const failed = result.failed || 0
    if (failed === 0) {
      ElMessage.success(`导入成功，共 ${success} 条`)
    } else {
      ElMessage.warning(`导入完成：成功 ${success} 条，失败 ${failed} 条，详见失败明细`)
    }
  } catch (error) {
    ElMessage.error(error.message || '导入失败')
  }
}

// 弹窗关闭后清空导入结果，避免下次打开残留上次结果（组件关闭时只重置自身文件选择态）
watch(importVisible, (visible) => {
  if (!visible) {
    importResult.value = null
  }
})

onMounted(() => {
  loadTypes()
})
// KeepAlive 页签切换返回时刷新列表数据（保留筛选与分页状态；首次进入由 onMounted 加载）
let firstActivation = true
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  loadTypes()
})
</script>

<script>
export default { name: 'DictManageView' }
</script>

<style scoped>
.dict-manage {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
  height: 100%;
  box-sizing: border-box;
}

/* —— 左右分栏 —— */
.dict-layout {
  display: flex;
  gap: 12px;
  height: 100%;
}

/* —— 左栏：码值类型 —— */
.dict-types {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}

.dict-types-title {
  padding: 10px 12px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  background-color: #f5f7fa;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.dict-types-filter {
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.dict-types-list {
  flex: 1;
  overflow-y: auto;
}

.dict-type-item {
  padding: 10px 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--el-border-color-lighter);
  transition: background-color 0.2s;
}

.dict-type-item:hover {
  background-color: #f5f7fa;
}

.dict-type-item.active {
  background-color: var(--el-color-primary-light-9);
  border-left: 3px solid #409eff;
}

.dict-type-code {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.dict-type-name {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

/* —— 右栏：码值项 —— */
.dict-items {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.dict-items-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0 12px;
}

.dict-items-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.dict-items-code {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.dict-items-desc {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

/* —— 工具区右侧按钮容器：导入 / 导出 / 新增（间距沿用 el-button 相邻默认 margin） —— */
.dict-items-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
</style>

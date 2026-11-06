<template>
  <div class="i18n-language-manage">
    <!-- 筛选条件区：四分栅栏（名称 | 语言码 | 启用状态） -->
    <el-form class="filter-form" :model="filters" label-width="80px" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item :label="t('frontend.i18n.language.filter.name')">
            <el-input
              v-model="filters.fieldName"
              :placeholder="t('frontend.i18n.language.filter.name.placeholder')"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item :label="t('frontend.i18n.language.filter.code')">
            <el-input
              v-model="filters.fieldCode"
              :placeholder="t('frontend.i18n.language.filter.code.placeholder')"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item :label="t('frontend.i18n.language.filter.enable')">
            <el-select v-model="filters.fieldEnable" style="width: 100%" clearable>
              <el-option :label="t('frontend.common.enabled')" value="true" />
              <el-option :label="t('frontend.common.disabled')" value="false" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 工具区：左侧排序（单条件）+ 右侧查询/创建 -->
    <div class="list-toolbar">
      <div class="sort-area">
        <span class="sort-item" :class="{ active: true }" @click="toggleSort">
          {{ t('frontend.i18n.language.column.order') }}
          <i class="sort-arrow">{{ orderSort === 'asc' ? '↑' : '↓' }}</i>
        </span>
      </div>
      <div class="btn-area">
        <el-button type="primary" @click="handleQuery">{{ t('frontend.common.query') }}</el-button>
        <el-button v-if="isI18nAdmin" type="success" @click="openCreate">
          {{ t('frontend.common.create') }}
        </el-button>
      </div>
    </div>

    <!-- 列表区：无复选框列（无批量操作），末列操作按钮（默认语言行操作禁用，A2） -->
    <el-table :data="sortedList" v-loading="loading" border stripe row-key="fieldId">
      <template #empty>
        <el-empty :description="t('frontend.common.noData')" :image-size="80" />
      </template>
      <el-table-column prop="fieldCode" :label="t('frontend.i18n.language.column.code')" min-width="140" show-overflow-tooltip />
      <el-table-column prop="fieldName" :label="t('frontend.i18n.language.column.name')" min-width="160" show-overflow-tooltip />
      <el-table-column :label="t('frontend.i18n.language.column.enable')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.fieldEnable ? 'success' : 'danger'" size="small">
            {{ row.fieldEnable ? t('frontend.common.enabled') : t('frontend.common.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('frontend.i18n.language.column.default')" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.fieldDefault" type="warning" size="small">
            {{ t('frontend.i18n.language.defaultTag') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="fieldOrder" :label="t('frontend.i18n.language.column.order')" width="90" align="center" />
      <el-table-column
        v-if="isI18nAdmin"
        :label="t('frontend.i18n.language.column.operation')"
        width="230"
        align="center"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="row.fieldDefault"
            @click="handleSetEnable(row)"
          >
            {{ row.fieldEnable ? t('frontend.common.disable') : t('frontend.common.enable') }}
          </el-button>
          <el-button link type="primary" @click="openEdit(row)">
            {{ t('frontend.common.edit') }}
          </el-button>
          <el-button
            link
            type="warning"
            :disabled="row.fieldDefault"
            @click="handleSetDefault(row)"
          >
            {{ t('frontend.common.setDefault') }}
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="row.fieldDefault"
            @click="handleDelete(row)"
          >
            {{ t('frontend.common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 语言行数少，后端全量返回不分页（AGENTS：切页大小回第 1 页等规则不适用） -->
    <div class="list-total-bar">
      {{ t('frontend.common.total', { total: languageList.length }) }}
    </div>

    <!-- 新增/编辑弹窗（A5：仅 5 个字段，免增子路由） -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('frontend.i18n.language.create') : t('frontend.i18n.language.edit')"
      width="460px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item :label="t('frontend.i18n.language.field.code')" prop="fieldCode">
          <el-input
            v-model="form.fieldCode"
            :placeholder="t('frontend.i18n.language.field.code.placeholder')"
            :disabled="dialogMode === 'edit'"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item :label="t('frontend.i18n.language.field.name')" prop="fieldName">
          <el-input
            v-model="form.fieldName"
            :placeholder="t('frontend.i18n.language.field.name.placeholder')"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item :label="t('frontend.i18n.language.field.enable')">
          <el-switch v-model="form.fieldEnable" />
        </el-form-item>
        <el-form-item :label="t('frontend.i18n.language.field.order')">
          <el-input-number v-model="form.fieldOrder" :min="1" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('frontend.common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ t('frontend.common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { hasAuth } from '@/utils/auth'
import { getInitID } from '@/api/common'
import {
  listLanguages,
  saveLanguage,
  updateLanguage,
  deleteLanguage,
  setDefaultLanguage
} from '@/api/i18n'

/**
 * 语言管理页：sys_i18n_language 列表维护（任务清单 I18N3-FE-001）。
 * 标准三段式列表页 + 新增/编辑弹窗（A5，免增子路由）；
 * 默认语言行禁用 停用/删除/设默认（A2，后端同口径校验）；
 * 删除级联（D4）由后端完成：删该语言全部翻译、用户语言登记改绑默认语言。
 */
const { t } = useI18n()

const isI18nAdmin = computed(() => hasAuth('SYS_I18N_ADMIN'))

/* ========== 筛选与列表 ========== */

const loading = ref(false)
const filters = reactive({
  fieldName: '',
  fieldCode: '',
  fieldEnable: null
})
const languageList = ref([])
/** 排序区单条件（排序号）本地升降切换；后端列表固定 field_order 升序、field_code 升序返回 */
const orderSort = ref('asc')

const sortedList = computed(() => {
  const list = [...languageList.value]
  list.sort((a, b) => {
    const diff = (a.fieldOrder || 0) - (b.fieldOrder || 0)
    return orderSort.value === 'asc' ? diff : -diff
  })
  return list
})

async function loadList() {
  loading.value = true
  try {
    const params = {}
    if (filters.fieldName) params.fieldName = filters.fieldName
    if (filters.fieldCode) params.fieldCode = filters.fieldCode
    if (filters.fieldEnable !== null && filters.fieldEnable !== '') {
      params.fieldEnable = filters.fieldEnable
    }
    const res = await listLanguages(params)
    languageList.value = res.data || []
  } catch (error) {
    ElMessage.error(error.message || t('frontend.common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  loadList()
}

function toggleSort() {
  orderSort.value = orderSort.value === 'asc' ? 'desc' : 'asc'
}

function confirmThen(message, action) {
  ElMessageBox.confirm(message, t('frontend.common.confirm'), { type: 'warning' })
    .then(action)
    .catch(() => {})
}

function handleSetEnable(row) {
  const nextEnable = !row.fieldEnable
  const tip = nextEnable
    ? t('frontend.i18n.language.confirm.enable')
    : t('frontend.i18n.language.confirm.disable')
  confirmThen(tip, async () => {
    // 后端 update 接口：fieldCode 与 field_default 不可修改，仅提交 fieldId + fieldEnable
    await updateLanguage({ fieldId: row.fieldId, fieldEnable: nextEnable })
    ElMessage.success(t('frontend.common.success'))
    loadList()
  })
}

function handleSetDefault(row) {
  confirmThen(t('frontend.i18n.language.confirm.setDefault'), async () => {
    await setDefaultLanguage(row.fieldId)
    ElMessage.success(t('frontend.common.success'))
    loadList()
  })
}

function handleDelete(row) {
  confirmThen(t('frontend.i18n.language.confirm.delete'), async () => {
    await deleteLanguage(row.fieldId)
    ElMessage.success(t('frontend.common.success'))
    loadList()
  })
}

/* ========== 新增 / 编辑弹窗 ========== */

const dialogVisible = ref(false)
const dialogMode = ref('create')
const saving = ref(false)
const formRef = ref(null)
const form = reactive({
  fieldId: '',
  fieldCode: '',
  fieldName: '',
  fieldEnable: true,
  fieldOrder: 1
})

const formRules = computed(() => ({
  fieldCode: [
    {
      required: true,
      message: t('frontend.i18n.language.validate.code.required'),
      trigger: 'blur'
    },
    {
      pattern: /^[A-Za-z0-9_]+$/,
      message: t('frontend.i18n.language.validate.code.pattern'),
      trigger: 'blur'
    }
  ],
  fieldName: [
    {
      required: true,
      message: t('frontend.i18n.language.validate.name.required'),
      trigger: 'blur'
    }
  ]
}))

function openCreate() {
  dialogMode.value = 'create'
  form.fieldId = ''
  form.fieldCode = ''
  form.fieldName = ''
  form.fieldEnable = true
  form.fieldOrder = languageList.value.length + 1
  dialogVisible.value = true
}

function openEdit(row) {
  dialogMode.value = 'edit'
  form.fieldId = row.fieldId
  form.fieldCode = row.fieldCode
  form.fieldName = row.fieldName
  form.fieldEnable = !!row.fieldEnable
  form.fieldOrder = row.fieldOrder || 1
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.clearValidate()
}

async function handleSave() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const body = {
      fieldId: form.fieldId,
      fieldCode: form.fieldCode,
      fieldName: form.fieldName,
      fieldEnable: form.fieldEnable,
      fieldOrder: form.fieldOrder
    }
    if (dialogMode.value === 'create') {
      // 新建必须先取 32 位主键 ID（工程规范：新建先调 getInitID）
      const res = await getInitID()
      body.fieldId = res.data
      await saveLanguage(body)
    } else {
      await updateLanguage(body)
    }
    ElMessage.success(t('frontend.common.success'))
    dialogVisible.value = false
    loadList()
  } catch (error) {
    ElMessage.error(error.message || t('frontend.common.loadFailed'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
/* 语言行数少，后端全量返回不分页：合计行右对齐（替代 el-pagination） */
.list-total-bar {
  margin-top: 10px;
  text-align: right;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>

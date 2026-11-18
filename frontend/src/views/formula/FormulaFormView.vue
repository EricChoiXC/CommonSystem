<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">{{ isEdit ? '编辑公式' : '新建公式' }}</span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" plain @click="openDesigner">设计公式</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </div>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="40%" :disabled="saving" class="page-form">
      <el-row :gutter="12">
        <!-- 公式编码：全局唯一（保存期后端查重，重复报 CS_FORMULA_CODE_DUPLICATED） -->
        <el-col :span="12">
          <el-form-item label="公式编码" prop="fieldCode">
            <el-input v-model="form.fieldCode" maxlength="100" placeholder="请输入公式编码（全局唯一）" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="公式名称" prop="fieldName">
            <el-input v-model="form.fieldName" maxlength="200" placeholder="请输入公式名称" />
          </el-form-item>
        </el-col>
        <!-- 公式类型：仅作用途分类标记，不改变执行语义 -->
        <el-col :span="12">
          <el-form-item label="公式类型" prop="fieldType">
            <el-select v-model="form.fieldType" style="width: 100%" placeholder="请选择公式类型">
              <el-option v-for="opt in FORMULA_TYPE_OPTIONS" :key="opt" :label="opt" :value="opt" />
            </el-select>
          </el-form-item>
        </el-col>
        <!-- 返回类型：作为 expectedReturnType 参与 validate / simulate -->
        <el-col :span="12">
          <el-form-item label="返回类型" prop="fieldReturnType">
            <el-select v-model="form.fieldReturnType" style="width: 100%" placeholder="请选择返回类型">
              <el-option v-for="opt in RETURN_TYPE_OPTIONS" :key="opt" :label="opt" :value="opt" />
            </el-select>
          </el-form-item>
        </el-col>

        <!-- 表达式：monospace 只读回显，编排入口唯一——【设计公式】打开 FormulaDesigner 弹窗回写 -->
        <el-col :span="24">
          <el-form-item label="表达式" prop="fieldExpression" label-width="20%">
            <div class="expression-view" :class="{ 'is-empty': !form.fieldExpression }">
              {{ form.fieldExpression || '尚未编排表达式，请点击【设计公式】' }}
            </div>
            <div class="field-tip">表达式经【设计公式】弹窗编排后回写；保存时由后端强制校验（语法 / 函数注册 / 变量码）</div>
          </el-form-item>
        </el-col>

        <!-- 备注：长内容字段一行一字段 2:8（label 20% / 内容 80%） -->
        <el-col :span="24">
          <el-form-item label="备注" label-width="20%">
            <el-input v-model="form.fieldRemark" type="textarea" :rows="3" maxlength="500" placeholder="请输入备注（可空）" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 公式设计弹窗：availableVars 不传（管理页无外部变量），expectedReturnType 联动表单返回类型，
         @confirm 回写 fieldExpression（组件契约见 FormulaDesigner.vue 头注释） -->
    <FormulaDesigner
      v-model:visible="designerVisible"
      :expression="form.fieldExpression"
      :expected-return-type="form.fieldReturnType"
      @confirm="handleDesignerConfirm"
    />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { markTabToClose } from '@/utils/tabs'
import { ElMessage } from 'element-plus'
import FormulaDesigner from '@/components/formula/FormulaDesigner.vue'
import { getInitID } from '@/api/common'
import { loadFormula, saveFormula, updateFormula } from '@/api/formula'

/**
 * 公式定义器新建/编辑页：/formula/create 新建、/formula/edit/:id 编辑，两路由共用本组件，
 * 按 route.path 是否含 /edit/ 区分模式（对齐 DatasourceFormView 先例）。
 * 契约要点（docs/系统模块/系统模块UI设计文档.md 4.11.5）：
 * - 元数据字段一行两字段（label-width 40% + :span 12）；表达式与备注为长内容字段
 *   一行一字段（2:8，label-width 20%）；
 * - 表达式 monospace 只读回显，编排入口唯一——【设计公式】打开 FormulaDesigner 弹窗，
 *   确认后回写 fieldExpression（expectedReturnType 联动表单返回类型，availableVars 不传）；
 * - 新建 onMounted 调 getInitID() 预取 32 位 ID（失败不阻塞，后端 save 兜底生成）；
 *   编辑按路由 id 调 loadFormula 回填（含 fieldExpression 表达式原文）；
 * - 保存载荷 { fieldId, fieldCode, fieldName, fieldType, fieldExpression, fieldReturnType,
 *   fieldRemark }（fieldDependencies 由后端保存期解析落库，前端不传）；前端仅做必填性校验，
 *   语法校验由后端保存期强制 validate（编码重复报 CS_FORMULA_CODE_DUPLICATED）。
 * 任务编号：F14-FE-005。
 */
const route = useRoute()
const router = useRouter()

const isEdit = computed(() => route.path.includes('/edit/'))

const formRef = ref(null)
const saving = ref(false)
/** 公式设计弹窗显隐 */
const designerVisible = ref(false)

/** 公式类型六类枚举（仅作用途分类标记，不改变执行语义，需求文档 12.1） */
const FORMULA_TYPE_OPTIONS = ['FIELD', 'CONDITION', 'VALIDATION', 'VISIBILITY', 'WORKFLOW', 'CUSTOM']

/** 返回类型五类枚举（作为 expectedReturnType 参与 validate / simulate） */
const RETURN_TYPE_OPTIONS = ['NUMBER', 'STRING', 'BOOLEAN', 'DATE', 'ARRAY']

const form = reactive({
  fieldId: '',
  fieldCode: '',
  fieldName: '',
  fieldType: '',
  fieldExpression: '',
  fieldReturnType: '',
  fieldRemark: ''
})

/** 前端仅做必填性校验；表达式经弹窗回写（change 触发清错），语法校验由后端保存期强制 validate */
const rules = {
  fieldCode: [{ required: true, message: '请输入公式编码', trigger: 'blur' }],
  fieldName: [{ required: true, message: '请输入公式名称', trigger: 'blur' }],
  fieldType: [{ required: true, message: '请选择公式类型', trigger: 'change' }],
  fieldReturnType: [{ required: true, message: '请选择返回类型', trigger: 'change' }],
  fieldExpression: [{ required: true, message: '请通过【设计公式】编排表达式', trigger: 'change' }]
}

/** 新建初始化：预取 32 位 ID（预取失败不阻塞，后端 save 会自动生成 ID） */
async function initForCreate() {
  try {
    const res = await getInitID()
    form.fieldId = (res && res.data) || ''
  } catch {
    // 预取失败不阻塞
  }
}

/** 编辑初始化：按路由 id 加载公式回填（fieldDependencies 后端解析，本页不消费） */
async function initForEdit() {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少编辑对象的 ID')
    goBack()
    return
  }
  try {
    const res = await loadFormula(id)
    const row = res.data
    if (!row) {
      ElMessage.error('未找到该公式')
      goBack()
      return
    }
    form.fieldId = row.fieldId
    form.fieldCode = row.fieldCode || ''
    form.fieldName = row.fieldName || ''
    form.fieldType = row.fieldType || ''
    form.fieldExpression = row.fieldExpression || ''
    form.fieldReturnType = row.fieldReturnType || ''
    form.fieldRemark = row.fieldRemark || ''
  } catch (error) {
    ElMessage.error(error.message || '加载数据失败')
    goBack()
  }
}

onMounted(() => {
  if (isEdit.value) {
    initForEdit()
  } else {
    initForCreate()
  }
})

function openDesigner() {
  designerVisible.value = true
}

/** 设计弹窗【确认回写】：回写表达式并清除该字段校验态 */
function handleDesignerConfirm(expression) {
  form.fieldExpression = expression
  formRef.value?.clearValidate('fieldExpression')
}

async function handleSave() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    // fieldDependencies 由后端保存期解析落库，前端不传；表达式原文原样提交
    const payload = {
      fieldId: form.fieldId,
      fieldCode: form.fieldCode.trim(),
      fieldName: form.fieldName.trim(),
      fieldType: form.fieldType,
      fieldExpression: form.fieldExpression,
      fieldReturnType: form.fieldReturnType,
      fieldRemark: form.fieldRemark ? form.fieldRemark.trim() : ''
    }
    if (isEdit.value) {
      await updateFormula(payload)
    } else {
      await saveFormula(payload)
    }
    ElMessage.success('保存成功')
    markTabToClose(route.path)
    router.push('/formula')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  markTabToClose(route.path)
  router.push('/formula')
}
</script>

<script>
export default { name: 'FormulaFormView' }
</script>

<style scoped>
/* 表达式 monospace 只读回显块：与 form-page.css 详情网格协调的浅底描边样式 */
.expression-view {
  width: 100%;
  min-height: 64px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background-color: var(--el-fill-color-light);
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  word-break: break-all;
  box-sizing: border-box;
}

.expression-view.is-empty {
  color: var(--el-text-color-secondary);
  font-family: inherit;
}

/* 辅助说明文字（el-form-item__content 为 flex，占满整行换行展示） */
.field-tip {
  width: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">{{ isEdit ? '编辑数据源' : '新建数据源' }}</span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </div>
    </template>

    <el-form :model="form" label-width="40%" :disabled="saving" class="page-form">
      <el-row :gutter="12">
        <!-- D3：目标能力保存后锁定，编辑态只读展示且保存载荷不携带 fieldTarget（后端以库值为准） -->
        <el-col :span="12">
          <el-form-item label="目标能力" required>
            <el-select
              v-if="!isEdit"
              v-model="form.fieldTarget"
              style="width: 100%"
              filterable
              placeholder="请选择目标能力"
              @change="handleTargetChange"
            >
              <el-option v-for="item in targets" :key="item.code" :label="targetOptionLabel(item)" :value="item.code" />
            </el-select>
            <div v-else class="target-locked">
              <span class="readonly-text">{{ form.fieldTargetName ? `${form.fieldTarget}（${form.fieldTargetName}）` : form.fieldTarget || '-' }}</span>
              <span class="field-tip">保存后锁定不可改</span>
            </div>
          </el-form-item>
        </el-col>
        <!-- 数据源编码保存后仍可改，服务端查重（重复报 CS_DATASOURCE_CODE_DUPLICATED） -->
        <el-col :span="12">
          <el-form-item label="数据源编码" required>
            <el-input v-model="form.fieldCode" maxlength="100" placeholder="请输入数据源编码" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数据源名称" required>
            <el-input v-model="form.fieldName" maxlength="200" placeholder="请输入数据源名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="备注">
            <el-input v-model="form.fieldRemark" type="textarea" :rows="2" maxlength="500" placeholder="请输入备注（可空）" />
          </el-form-item>
        </el-col>

        <!-- 固定条件：按所选能力 conditionFields 动态渲染行，长内容字段独占一行 2:8（label 20% / 内容 80%） -->
        <el-col :span="24">
          <el-form-item label="固定条件" label-width="20%">
            <div class="condition-editor">
              <div v-for="(row, index) in conditions" :key="index" class="condition-row">
                <el-select v-model="row.key" class="condition-field" filterable placeholder="条件字段" @change="handleConditionFieldChange(row)">
                  <el-option v-for="field in conditionOptions" :key="field.key" :label="field.label" :value="field.key" />
                </el-select>
                <el-select v-model="row.op" class="condition-op" filterable placeholder="操作符">
                  <el-option v-for="op in conditionFieldOps(row.key)" :key="op" :label="opLabel(op)" :value="op" />
                </el-select>
                <!-- literalAllowed=false 字段：值固定 ${CURRENT_USER_ID}，只读 tag 展示，不给输入框 -->
                <el-tag v-if="isLiteralLocked(row.key)" type="info" size="small" class="condition-tag">当前登录人</el-tag>
                <el-date-picker
                  v-else-if="conditionValueType(row.key) === 'DATE'"
                  v-model="row.value"
                  type="date"
                  class="condition-value"
                  value-format="YYYY-MM-DD"
                  placeholder="请选择日期"
                />
                <el-date-picker
                  v-else-if="conditionValueType(row.key) === 'DATETIME'"
                  v-model="row.value"
                  type="datetime"
                  class="condition-value"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  placeholder="请选择时间"
                />
                <el-input v-else v-model="row.value" class="condition-value" placeholder="请输入条件值" />
                <el-button link type="danger" @click="removeCondition(index)">删除</el-button>
              </div>
              <div v-if="form.fieldTarget && !conditionOptions.length" class="field-tip">该目标能力无条件字段，无法编排固定条件</div>
              <el-button plain :disabled="!conditionOptions.length" @click="addCondition">添加条件</el-button>
            </div>
          </el-form-item>
        </el-col>

        <!-- 返回字段：空选 = 默认返回目标能力全字段 -->
        <el-col :span="24">
          <el-form-item label="返回字段" label-width="20%">
            <el-select
              v-model="form.fieldFields"
              multiple
              filterable
              style="width: 100%"
              placeholder="不选择时默认返回目标能力全字段"
            >
              <el-option v-for="field in resultOptions" :key="field.key" :label="field.label" :value="field.key" />
            </el-select>
            <div class="field-tip">不选择时默认返回目标能力全字段</div>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { markTabToClose } from '@/utils/tabs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getInitID } from '@/api/common'
import { getDatasourceTargets, loadDatasource, saveDatasource, updateDatasource } from '@/api/datasource'

/**
 * 数据源新建/编辑页：/datasource/create 新建、/datasource/edit/:id 编辑，两路由共用本组件，
 * 按 route.path 是否含 /edit/ 区分模式（对齐 NumberFormView 先例）。
 * 契约要点（docs/系统模块/系统模块UI设计文档.md 4.10.5）：
 * - D3 目标能力保存后锁定：新建经下拉选定并按能力 conditionFields / resultFields 渲染
 *   条件编辑器与字段多选，新建态切换能力时二次确认并清空已编排条件与字段选择（防跨能力
 *   残留非法 key）；编辑态只读展示，保存载荷不携带 fieldTarget（后端以库值为准）；
 * - 固定条件行 = 字段下拉 + 操作符下拉（选项为所选字段 ops）+ 值输入（按字段 type 选型：
 *   STRING/NUMBER 文本、DATE/DATETIME 日期选择）；literalAllowed=false 字段值固定
 *   ${CURRENT_USER_ID}，展示为只读 tag「当前登录人」，不给输入框；可空 = 无固定条件；
 * - 返回字段空选 = 默认全字段（表单内提示），fieldFields 为字段名字符串数组；
 * - 保存载荷分流：新建 { fieldId, fieldCode, fieldName, fieldTarget, fieldConditions,
 *   fieldFields, fieldRemark }；编辑仅无 fieldTarget；空数组 / 无固定条件提交空数组。
 * 任务编号：F18-FE-003。
 */
const route = useRoute()
const router = useRouter()

const isEdit = computed(() => route.path.includes('/edit/'))
const saving = ref(false)

/** 运行时变量占位符（literalAllowed=false 字段固定值，对齐 SysDatasourceVar.CURRENT_USER_ID） */
const CURRENT_USER_VAR = '${CURRENT_USER_ID}'

/** 操作符中文展示（对齐 SysDatasourceOp 枚举语义；未收录时回退原文） */
const OP_LABELS = {
  EQ: '等于',
  NE: '不等于',
  LIKE: '包含',
  GT: '大于',
  GE: '大于等于',
  LT: '小于',
  LE: '小于等于',
  IN: '属于'
}

/** 目标能力清单（getDatasourceTargets，含各能力 conditionFields / resultFields 定义） */
const targets = ref([])

/** 编辑态锁定展示用目标能力名称（loadDatasource 带出，目标失效时为空） */
const fieldTargetName = ref('')

const form = reactive({
  fieldId: '',
  fieldCode: '',
  fieldName: '',
  fieldTarget: '',
  fieldFields: [],
  fieldRemark: ''
})

/** 固定条件行编辑态：[{ key, op, value }]，保存时映射为同构数组提交 */
const conditions = ref([])

/** 新建态最近一次生效的目标能力（切换取消时回退用） */
const lastAppliedTarget = ref('')

/** 当前选中的目标能力定义 */
const currentTarget = computed(() => targets.value.find((item) => item.code === form.fieldTarget))

/** 条件字段定义选项（conditionFields）；未选能力或能力失效时为空 */
const conditionOptions = computed(() => (currentTarget.value && currentTarget.value.conditionFields) || [])

/** 返回字段定义选项（resultFields） */
const resultOptions = computed(() => (currentTarget.value && currentTarget.value.resultFields) || [])

/** 目标能力下拉 label：code（name），名称缺失时仅编码 */
function targetOptionLabel(item) {
  return item.name ? `${item.code}（${item.name}）` : item.code
}

/** 条件字段定义：按 key 在当前能力 conditionFields 内查找 */
function conditionFieldDef(key) {
  return conditionOptions.value.find((field) => field.key === key)
}

/** 条件字段可选操作符（该字段 ops）；字段未命中定义时为空 */
function conditionFieldOps(key) {
  const def = conditionFieldDef(key)
  return (def && def.ops) || []
}

/** 条件值输入选型：DATE / DATETIME 用日期选择，其余（STRING/NUMBER）文本输入 */
function conditionValueType(key) {
  const def = conditionFieldDef(key)
  const type = (def && def.type ? String(def.type) : 'STRING').toUpperCase()
  return type === 'DATE' || type === 'DATETIME' ? type : 'TEXT'
}

/** 字面量锁定字段判定：literalAllowed=false 时值固定 ${CURRENT_USER_ID} */
function isLiteralLocked(key) {
  const def = conditionFieldDef(key)
  return !!def && def.literalAllowed === false
}

function opLabel(op) {
  return OP_LABELS[op] || op
}

/** 拉取目标能力清单：新建选能力、编辑渲染锁定能力的条件/字段定义均依赖；失败保持空选项 */
async function initTargets() {
  try {
    const res = await getDatasourceTargets()
    targets.value = (res && res.data) || []
  } catch {
    targets.value = []
  }
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

/** 编辑初始化：按路由 id 加载数据源回填；fieldConditions / fieldFields 为后端解析后对象直接消费 */
async function initForEdit() {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少编辑对象的 ID')
    goBack()
    return
  }
  try {
    const res = await loadDatasource(id)
    const row = res.data
    if (!row) {
      ElMessage.error('未找到该数据源')
      goBack()
      return
    }
    form.fieldId = row.fieldId
    form.fieldCode = row.fieldCode || ''
    form.fieldName = row.fieldName || ''
    form.fieldTarget = row.fieldTarget || ''
    form.fieldRemark = row.fieldRemark || ''
    fieldTargetName.value = row.fieldTargetName || ''
    conditions.value = (Array.isArray(row.fieldConditions) ? row.fieldConditions : []).map((item) => ({
      key: item.key,
      op: item.op,
      value: item.value == null ? '' : String(item.value)
    }))
    // fieldFields NULL = 默认全字段 → 表单内多选置空
    form.fieldFields = Array.isArray(row.fieldFields) ? [...row.fieldFields] : []
  } catch (error) {
    ElMessage.error(error.message || '加载数据失败')
    goBack()
  }
}

onMounted(() => {
  initTargets()
  if (isEdit.value) {
    initForEdit()
  } else {
    initForCreate()
  }
})

/** 新建态切换目标能力：已有编排内容时二次确认，确认后清空已编排条件与字段选择（防跨能力残留非法 key） */
async function handleTargetChange(code) {
  if (!code) return
  const hasContent = conditions.value.length > 0 || form.fieldFields.length > 0
  if (hasContent) {
    try {
      await ElMessageBox.confirm(
        '切换目标能力将清空已编排的固定条件与返回字段选择，确定切换？',
        '切换确认',
        { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
      )
    } catch {
      form.fieldTarget = lastAppliedTarget.value
      return
    }
  }
  lastAppliedTarget.value = code
  conditions.value = []
  form.fieldFields = []
}

/** 添加条件行：默认选第一个字段与其第一个 op；literalAllowed=false 字段值固定 ${CURRENT_USER_ID} */
function addCondition() {
  const first = conditionOptions.value[0]
  if (!first) return
  conditions.value.push({
    key: first.key,
    op: (first.ops && first.ops[0]) || '',
    value: first.literalAllowed === false ? CURRENT_USER_VAR : ''
  })
}

/** 行内切换条件字段：操作符重置为该字段第一个 op，值按字面量限制重置 */
function handleConditionFieldChange(row) {
  const def = conditionFieldDef(row.key)
  row.op = (def && def.ops && def.ops[0]) || ''
  row.value = def && def.literalAllowed === false ? CURRENT_USER_VAR : ''
}

function removeCondition(index) {
  conditions.value.splice(index, 1)
}

async function handleSave() {
  if (!isEdit.value && !form.fieldTarget) {
    ElMessage.warning('请选择目标能力')
    return
  }
  if (!form.fieldCode || !form.fieldCode.trim()) {
    ElMessage.warning('数据源编码不能为空')
    return
  }
  if (!form.fieldName || !form.fieldName.trim()) {
    ElMessage.warning('数据源名称不能为空')
    return
  }
  saving.value = true
  try {
    // fieldConditions / fieldFields 空数组 / 无固定条件提交空数组；literalAllowed=false 字段值强制 ${CURRENT_USER_ID}
    const payload = {
      fieldId: form.fieldId,
      fieldCode: form.fieldCode.trim(),
      fieldName: form.fieldName.trim(),
      fieldConditions: conditions.value.map((row) => ({
        key: row.key,
        op: row.op,
        value: isLiteralLocked(row.key) ? CURRENT_USER_VAR : row.value == null ? '' : String(row.value)
      })),
      fieldFields: [...form.fieldFields],
      fieldRemark: form.fieldRemark ? form.fieldRemark.trim() : ''
    }
    if (isEdit.value) {
      // 编辑：不传 fieldTarget（D3，后端以库值为准）
      await updateDatasource(payload)
      ElMessage.success('保存成功')
    } else {
      await saveDatasource({ ...payload, fieldTarget: form.fieldTarget })
      ElMessage.success('保存成功')
    }
    markTabToClose(route.path)
    router.push('/datasource')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  markTabToClose(route.path)
  router.push('/datasource')
}
</script>

<script>
export default { name: 'DatasourceFormView' }
</script>

<style scoped>
.readonly-text {
  color: var(--el-text-color-regular);
  word-break: break-all;
}

/* 编辑态目标能力锁定展示：只读文本 + 锁定提示（对齐 NumberFormView .model-locked） */
.target-locked {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.field-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 固定条件动态行编辑器 */
.condition-editor {
  width: 100%;
}

.condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.condition-field {
  width: 220px;
  flex-shrink: 0;
}

.condition-op {
  width: 130px;
  flex-shrink: 0;
}

.condition-value {
  flex: 1;
}

.condition-tag {
  flex-shrink: 0;
}
</style>

<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">{{ isEdit ? '编辑编号规则' : '新建编号规则' }}</span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </div>
    </template>

    <el-form :model="form" label-width="40%" :disabled="saving" class="page-form">
      <el-row :gutter="12">
        <el-col :span="12">
          <!-- D3：所属接入项保存后锁定，编辑态只读展示且不再提交模型三字段 -->
          <el-form-item label="所属接入项" required>
            <el-select
              v-if="!isEdit"
              v-model="form.fieldModelId"
              style="width: 100%"
              placeholder="请选择所属接入项"
              @change="handleModelChange"
            >
              <el-option v-for="opt in MODEL_OPTIONS" :key="opt.key" :label="modelOptionLabel(opt)" :value="opt.key" />
            </el-select>
            <div v-else class="model-locked">
              <span class="readonly-text">{{ form.fieldModelName || form.fieldModelId || '-' }}</span>
              <span class="field-tip">保存后锁定不可改</span>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <!-- 规则编码保存后仍可改，服务端查重 -->
          <el-form-item label="规则编码" required>
            <el-input v-model="form.fieldCode" placeholder="请输入规则编码" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="规则名称" required>
            <el-input v-model="form.fieldName" placeholder="请输入规则名称" />
          </el-form-item>
        </el-col>
        <!-- 「是否启用」不在表单内编辑（启停走列表页 enable 接口），编辑态仅只读展示状态 -->
        <el-col v-if="isEdit" :span="12">
          <el-form-item label="启用状态">
            <el-tag :type="form.fieldEnable ? 'success' : 'info'" size="small" disable-transitions>
              {{ form.fieldEnable ? '启用' : '停用' }}
            </el-tag>
          </el-form-item>
        </el-col>

        <!-- 规则配置：长内容字段独占一行 2:8（label 20% / 内容 80%） -->
        <el-col :span="24">
          <el-form-item label="编号规则" label-width="20%">
            <NumberRuleComposer :value="form.fieldRule" :on-change="handleRuleChange" field-name="fieldRule" :disabled="saving" />
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
import { ElMessage } from 'element-plus'
import { NumberRuleComposer } from '@/components'
import { getInitID } from '@/api/common'
import { loadNumberRule, saveNumberRule, updateNumberRule, queryNumberModelOptions } from '@/api/number'

/**
 * 编号规则新建/编辑页：/number/create 新建、/number/edit/:id 编辑，两路由共用本组件，
 * 按 route.path 是否含 /edit/ 区分模式（对齐 OrgFormView 先例）。
 * 契约要点（docs/v1_5-编号功能/编号机制实现方案.md）：
 * - D3 接入项保存后锁定：新建经下拉选定并自动带出 fieldModelName / fieldModelField，
 *   编辑态只读展示，保存载荷不携带模型三字段与启用态（后端以库值为准）；
 * - D5 seq 项 id 锁定：规则项 item.id 由 NumberRuleComposer 生成并原样透传，本页不做改写；
 * - 「是否启用」不出现在表单内：新建默认启用（save 同事务自动停用同接入项原启用规则），
 *   启停一律走列表页 enable 接口，编辑态仅只读展示状态 tag；
 * - fieldRule 为规则配置 JSON 对象（方案 §2.1），编辑回填时后端已解析为对象，直接赋值消费。
 * 任务编号：NB4-FE-004。
 */
const route = useRoute()
const router = useRouter()

const isEdit = computed(() => route.path.includes('/edit/'))
const saving = ref(false)

/** 接入项下拉选项（queryNumberModelOptions，[{ key, name, modelName, modelField }]），仅新建模式使用 */
const MODEL_OPTIONS = ref([])

const form = reactive({
  fieldId: '',
  fieldCode: '',
  fieldName: '',
  fieldModelId: '',
  fieldModelName: '',
  fieldModelField: '',
  fieldEnable: true, // 仅编辑态只读展示，不随表单提交
  fieldRule: { items: [] }
})

/** 接入项选项展示文案：name 为主，附 key 便于辨识 */
function modelOptionLabel(opt) {
  return opt.key ? `${opt.name}（${opt.key}）` : opt.name
}

/** 新建初始化：预取 32 位 ID，加载接入项下拉选项 */
async function initForCreate() {
  try {
    const res = await getInitID()
    form.fieldId = (res && res.data) || ''
  } catch {
    // 预取失败不阻塞：后端 save 会自动生成 ID
  }
  try {
    const res = await queryNumberModelOptions()
    MODEL_OPTIONS.value = (res && res.data) || []
  } catch {
    MODEL_OPTIONS.value = []
  }
}

/** 下拉选定接入项后自动带出模型名 / 模型字段（D3） */
function handleModelChange(key) {
  const opt = MODEL_OPTIONS.value.find((item) => item.key === key)
  if (opt) {
    form.fieldModelName = opt.modelName || opt.name || ''
    form.fieldModelField = opt.modelField || ''
  }
}

/** 编辑初始化：按路由 id 加载规则回填；fieldRule 后端已解析为对象，直接消费 */
async function initForEdit() {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少编辑对象的 ID')
    goBack()
    return
  }
  try {
    const res = await loadNumberRule(id)
    const row = res.data
    if (!row) {
      ElMessage.error('未找到该编号规则')
      goBack()
      return
    }
    Object.assign(form, {
      fieldId: row.fieldId,
      fieldCode: row.fieldCode || '',
      fieldName: row.fieldName || '',
      fieldModelId: row.fieldModelId || '',
      fieldModelName: row.fieldModelName || '',
      fieldModelField: row.fieldModelField || '',
      fieldEnable: !!row.fieldEnable,
      fieldRule: row.fieldRule && typeof row.fieldRule === 'object' ? row.fieldRule : { items: [] }
    })
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

/** 编号规则组件受控回传：value 深拷贝生成的新对象直接替换表单值（seq 项 id 原样透传，D5） */
function handleRuleChange(newValue) {
  form.fieldRule = newValue
}

async function handleSave() {
  if (!form.fieldModelId) {
    ElMessage.warning('请选择所属接入项')
    return
  }
  if (!form.fieldCode || !form.fieldCode.trim()) {
    ElMessage.warning('规则编码不能为空')
    return
  }
  if (!form.fieldName || !form.fieldName.trim()) {
    ElMessage.warning('规则名称不能为空')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      // 编辑：不传 fieldModelId / fieldModelName / fieldModelField / fieldEnable（D3，后端以库值为准）
      await updateNumberRule({
        fieldId: form.fieldId,
        fieldCode: form.fieldCode.trim(),
        fieldName: form.fieldName.trim(),
        fieldRule: form.fieldRule
      })
      ElMessage.success('保存成功')
    } else {
      await saveNumberRule({
        fieldId: form.fieldId,
        fieldCode: form.fieldCode.trim(),
        fieldName: form.fieldName.trim(),
        fieldModelId: form.fieldModelId,
        fieldModelName: form.fieldModelName,
        fieldModelField: form.fieldModelField,
        fieldRule: form.fieldRule
      })
      ElMessage.success('保存成功，已自动启用并停用同接入项下其他已启用规则')
    }
    markTabToClose(route.path)
    router.push('/number')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/number')
}
</script>

<script>
export default { name: 'NumberFormView' }
</script>

<style scoped>
.readonly-text {
  color: var(--el-text-color-regular);
  word-break: break-all;
}

/* 编辑态接入项锁定展示：只读文本 + 锁定提示 */
.model-locked {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.field-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

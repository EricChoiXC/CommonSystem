<template>
  <div class="page-card">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <span class="page-title">发送待办</span>
        <div class="header-btns">
            <el-button @click="handleBack">返回</el-button>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">发送</el-button>
        </div>
        </div>
      </template>

      <el-form class="page-form" ref="formRef" :model="form" :rules="rules" label-width="40%" @submit.prevent>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="标题" prop="fieldName">
              <el-input v-model="form.fieldName" placeholder="请输入标题" clearable maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="待办类型" prop="fieldNotifyType">
              <el-select v-model="form.fieldNotifyType" style="width: 100%">
                <el-option v-for="opt in NOTIFY_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源模块" prop="fieldModelName">
              <el-select v-model="form.fieldModelName" placeholder="请选择来源模块" style="width: 100%">
                <el-option v-for="opt in modelOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接收人" prop="receiverIds">
              <AddressBook v-model:field-id="form.receiverIds" v-model:field-name="form.receiverNames" org-type="PERSON" multi />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="内容" label-width="20%">
              <el-input v-model="form.fieldContent" type="textarea" :rows="4" placeholder="请输入内容" maxlength="5000" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { markTabToClose } from '@/utils/tabs'
import { ElMessage } from 'element-plus'
import AddressBook from '@/components/AddressBook.vue'
import { getInitID } from '@/api/common'
import { queryModelOptions, sendTodo } from '@/api/notify'
import { getDictOptions } from '@/utils/dict'

/**
 * 发送待办页（后台管理，SYS_NOTIFY_ADMIN）：
 * 填写标题、待办类型（默认待办）、来源模块、接收人（多选）、内容（非必填）后提交；
 * 发送成功后返回「待办管理」页面。
 * 新建页打开时经 getInitID() 预取 32 位对象 ID 赋给 fieldId（docs/开发规范.md 第 9 节）。
 * 待办类型选项（dict.notify_type）由 dict 模块提供。
 */
const router = useRouter()
const route = useRoute()

const formRef = ref(null)
const submitting = ref(false)

/** 待办类型下拉选项（dict.notify_type，不含"全部"） */
const NOTIFY_TYPE_OPTIONS = ref([])

/** 来源模块选项（读取 model:name:map 缓存，value=MODEL_KEY，label=MODEL_NAME） */
const modelOptions = ref([])

const form = reactive({
  fieldId: '',
  fieldName: '',
  fieldNotifyType: '1', // 默认待办
  fieldModelName: '',
  fieldContent: '',
  // 接收人多选：AddressBook 以 ';' 分隔符拼接 id / 名称，须挂载在 form 上供 el-form 校验
  receiverIds: '',
  receiverNames: ''
})

const rules = {
  fieldName: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  fieldNotifyType: [{ required: true, message: '请选择待办类型', trigger: 'change' }],
  fieldModelName: [{ required: true, message: '请选择来源模块', trigger: 'change' }],
  // 接收人：trigger 用非交互值 'submit'，仅保留提交校验。
  // AddressBook 内部控件（el-table 勾选 checkbox、readonly 输入框聚焦）会经 provide/inject 触发
  // el-form 字段校验（validate('change'/'blur')），而 form.receiverIds 在弹窗「确认」时才回写，
  // 若 trigger 为 'change' 会在勾选/聚焦过程产生「请选择接收人」错误且确认后不自动清除。
  receiverIds: [{ required: true, message: '请选择接收人', trigger: 'submit' }]
}

onMounted(async () => {
  try {
    const res = await getInitID()
    form.fieldId = res && res.data ? res.data : ''
  } catch {
    // 预取失败不阻塞页面，后端发送时兜底生成
  }
  loadDictOptions()
  loadModelOptions()
})

/** 加载待办类型选项（dict.notify_type）；失败时保持空选项，不阻塞页面 */
async function loadDictOptions() {
  try {
    NOTIFY_TYPE_OPTIONS.value = await getDictOptions('notify_type')
  } catch {
    NOTIFY_TYPE_OPTIONS.value = []
  }
}

/** 加载来源模块下拉选项（MODEL_KEY → MODEL_NAME） */
async function loadModelOptions() {
  try {
    const res = await queryModelOptions()
    const map = (res && res.data) || {}
    modelOptions.value = Object.entries(map).map(([value, label]) => ({ value, label }))
  } catch {
    // 选项加载失败不阻塞页面，提交时后端会兜底校验来源模块合法性
  }
}

function handleBack() {
  router.push('/notify')
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const userIds = String(form.receiverIds || '')
    .split(';')
    .map((id) => id.trim())
    .filter(Boolean)
  if (!userIds.length) {
    ElMessage.warning('请选择接收人')
    return
  }
  submitting.value = true
  try {
    await sendTodo(
      {
        fieldId: form.fieldId,
        fieldName: form.fieldName,
        fieldNotifyType: form.fieldNotifyType,
        fieldModelName: form.fieldModelName,
        fieldContent: form.fieldContent || null
      },
      userIds
    )
    ElMessage.success('发送成功')
    markTabToClose(route.path)
    router.push('/notify')
  } catch (error) {
    ElMessage.error(error.message || '发送失败')
  } finally {
    submitting.value = false
  }
}
</script>

<script>
export default { name: 'TodoSendView' }
</script>

<style scoped>
/* 页面容器、标题、表单、底部操作区等公共样式见 styles/form-page.css */
</style>

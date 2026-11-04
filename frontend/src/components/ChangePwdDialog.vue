<template>
  <el-dialog
    :model-value="modelValue"
    title="修改密码"
    width="420px"
    :close-on-click-modal="false"
    @update:model-value="handleUpdateVisible"
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="新密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          placeholder="请输入新密码"
          autocomplete="new-password"
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirm">
        <el-input
          v-model="form.confirm"
          type="password"
          show-password
          placeholder="请再次输入新密码"
          autocomplete="new-password"
          @keyup.enter="handleSubmit"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleUpdateVisible(false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { changePwd } from '@/api/org'

/**
 * 修改密码弹窗：用户编辑/查看页按钮区共用。
 * 输入新密码与二次确认密码后调用 PATCH /backend/sys/org/user/changePwd，
 * 仅更新密码与最后修改密码时间；鉴权与用户编辑一致（SYS_ORG_ADMIN / SYS_ORG_USER_MANAGER / 本人）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  fieldId: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref(null)
const submitting = ref(false)
const form = reactive({ password: '', confirm: '' })

const rules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function handleUpdateVisible(visible) {
  emit('update:modelValue', visible)
}

function handleClosed() {
  form.password = ''
  form.confirm = ''
  formRef.value && formRef.value.clearValidate()
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await changePwd(props.fieldId, form.password)
    ElMessage.success('密码修改成功')
    emit('success')
    handleUpdateVisible(false)
  } catch (error) {
    ElMessage.error(error.message || '密码修改失败')
  } finally {
    submitting.value = false
  }
}
</script>

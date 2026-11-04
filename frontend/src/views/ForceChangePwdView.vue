<template>
  <div class="force-pwd-page">
    <el-card class="force-pwd-card">
      <h2 class="force-pwd-title">修改密码</h2>
      <p class="force-pwd-desc">系统要求您定期更换密码，请设置一个新密码。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" size="large" @keyup.enter="handleSubmit">
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入新密码（6-32位）"
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
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="submit-btn" :loading="submitting" @click="handleSubmit">
            确认修改
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { changePwd } from '@/api/org'

/**
 * 强制修改密码页面：/changePwd
 * 当用户登录时服务器返回 forceChangePwd=true，或用户在首页检测到需要更换密码时，
 * 跳转到本页面。修改成功后跳转首页。
 */
const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  password: '',
  confirm: ''
})

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

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const userId = userStore.userInfo && userStore.userInfo.fieldId
  if (!userId) {
    ElMessage.error('未获取到用户信息')
    return
  }
  submitting.value = true
  try {
    await changePwd(userId, form.password)
    ElMessage.success('密码修改成功')
    router.push('/')
  } catch (error) {
    ElMessage.error(error.message || '密码修改失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.force-pwd-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f0f2f5;
}

.force-pwd-card {
  width: 420px;
}

.force-pwd-title {
  margin: 0 0 8px;
  text-align: center;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.force-pwd-desc {
  text-align: center;
  font-size: 13px;
  color: #909399;
  margin: 0 0 24px;
}

.submit-btn {
  width: 100%;
}
</style>
<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="login-title">公共系统登录</h2>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="loginName">
          <el-input
            v-model="form.loginName"
            :placeholder="loginPlaceholder"
            autocomplete="username"
            clearable
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/sys'
import { useUserStore } from '@/stores/user'
import { loadSetting } from '@/api/common-setting'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  loginName: '',
  password: ''
})

const rules = {
  loginName: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** 登录配置 */
const loginEmailEnabled = ref(false)
const loginPhoneEnabled = ref(false)

/** 登录名输入框提示文本 */
const loginPlaceholder = computed(() => {
  if (loginEmailEnabled.value && loginPhoneEnabled.value) {
    return '请输入账号/邮箱/手机号'
  }
  if (loginEmailEnabled.value) {
    return '请输入账号/邮箱'
  }
  if (loginPhoneEnabled.value) {
    return '请输入账号/手机号'
  }
  return '请输入账号'
})

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const res = await login({
      body: {
        fieldLoginName: form.loginName,
        fieldPassword: form.password
      }
    })
    const token = res.metadata && res.metadata['X-Login-Token']
    userStore.setLogin(res.data, token)

    // 检查是否需要强制修改密码
    const forceChangePwd = res.metadata && res.metadata.forceChangePwd
    if (forceChangePwd) {
      ElMessage.success('登录成功，请修改密码')
      router.push('/changePwd')
    } else {
      ElMessage.success('登录成功')
      router.push('/')
    }
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

// 加载系统配置以获取登录选项
onMounted(async () => {
  try {
    const res = await loadSetting()
    const data = res.data || {}
    loginEmailEnabled.value = data.login_email_enable === 'true'
    loginPhoneEnabled.value = data.login_phone_enable === 'true'
  } catch {
    // 配置加载失败不影响登录，使用默认提示
  }
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f0f2f5;
}

.login-card {
  width: 380px;
}

.login-title {
  margin: 0 0 24px;
  text-align: center;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.login-btn {
  width: 100%;
}
</style>
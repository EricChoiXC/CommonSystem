<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="login-title">{{ t('frontend.login.title') }}</h2>
      <!-- 语言下拉：免鉴权 language/list，默认语言置顶且默认选中；所选语言随登录 body fieldLanguage 提交 -->
      <div class="login-language">
        <span class="login-language-label">{{ t('frontend.login.language') }}</span>
        <el-select
          v-model="selectedLanguage"
          :disabled="languageLoading"
          size="default"
          class="login-language-select"
          @change="handleLanguageChange"
        >
          <el-option
            v-for="lang in languageOptions"
            :key="lang.fieldCode"
            :label="lang.fieldName"
            :value="lang.fieldCode"
          />
        </el-select>
      </div>
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
            :placeholder="t('frontend.login.password.placeholder')"
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
            {{ t('frontend.login.submit') }}
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
import { useI18n } from 'vue-i18n'
import { login } from '@/api/sys'
import { useUserStore } from '@/stores/user'
import { useLanguageStore } from '@/stores/language'
import { listLanguages } from '@/api/i18n'
import { loadSetting } from '@/api/common-setting'

const router = useRouter()
const userStore = useUserStore()
const languageStore = useLanguageStore()
const { t } = useI18n()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  loginName: '',
  password: ''
})

const rules = computed(() => ({
  loginName: [{ required: true, message: t('frontend.login.validate.account'), trigger: 'blur' }],
  password: [{ required: true, message: t('frontend.login.validate.password'), trigger: 'blur' }]
}))

/** 登录配置 */
const loginEmailEnabled = ref(false)
const loginPhoneEnabled = ref(false)

/** 登录名输入框提示文本 */
const loginPlaceholder = computed(() => {
  if (loginEmailEnabled.value && loginPhoneEnabled.value) {
    return t('frontend.login.account.placeholder.all')
  }
  if (loginEmailEnabled.value) {
    return t('frontend.login.account.placeholder.email')
  }
  if (loginPhoneEnabled.value) {
    return t('frontend.login.account.placeholder.phone')
  }
  return t('frontend.login.account.placeholder')
})

/* ========== 语言下拉（登录页语言选择，随登录 body fieldLanguage 落库） ========== */

/** 启用语言选项（免鉴权 language/list，fieldEnable=true） */
const languageOptions = ref([])
const languageLoading = ref(false)
/** 所选语言码：默认取当前语言（localStorage 记忆），无匹配选项时取列表默认语言 */
const selectedLanguage = ref('')

async function loadLanguageOptions() {
  languageLoading.value = true
  try {
    const res = await listLanguages({ fieldEnable: 'true' })
    const languages = res.data || []
    // 默认语言置顶，其余按后端返回顺序（field_order 升序）
    languageOptions.value = [
      ...languages.filter((lang) => lang.fieldDefault),
      ...languages.filter((lang) => !lang.fieldDefault)
    ]
    // 默认选中：优先当前 locale（记忆值），不在列表中时取默认语言，再退列表首项
    const codes = languageOptions.value.map((lang) => lang.fieldCode)
    if (codes.includes(languageStore.current)) {
      selectedLanguage.value = languageStore.current
    } else {
      const defaultLang = languageOptions.value.find((lang) => lang.fieldDefault)
      selectedLanguage.value =
        (defaultLang && defaultLang.fieldCode) || codes[0] || languageStore.current
    }
  } catch {
    // 语言列表加载失败不阻塞登录：下拉为空、fieldLanguage 不随登录提交（后端回退默认语言）
    languageOptions.value = []
  } finally {
    languageLoading.value = false
  }
}

/** 登录前把所选语言同步到前端 locale（登录页文案即时切换） */
function handleLanguageChange(code) {
  languageStore.setLanguage(code)
}

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const body = {
      fieldLoginName: form.loginName,
      fieldPassword: form.password
    }
    // D3 登录语言登记契约：所选语言码随登录 body 提交，后端登录成功后 upsert 用户语言表；
    // 未选择（列表加载失败）时不携带，行为与既有登录完全一致（零回归）
    if (selectedLanguage.value) {
      body.fieldLanguage = selectedLanguage.value
    }
    const res = await login({ body })
    const token = res.metadata && res.metadata['X-Login-Token']
    userStore.setLogin(res.data, token)

    // 检查是否需要强制修改密码
    const forceChangePwd = res.metadata && res.metadata.forceChangePwd
    if (forceChangePwd) {
      ElMessage.success(t('frontend.login.success.changePwd'))
      router.push('/changePwd')
    } else {
      ElMessage.success(t('frontend.login.success'))
      router.push('/')
    }
  } catch (error) {
    ElMessage.error(error.message || t('frontend.login.failed'))
  } finally {
    loading.value = false
  }
}

// 加载系统配置以获取登录选项
onMounted(async () => {
  loadLanguageOptions()
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

.login-language {
  display: flex;
  align-items: center;
  margin-bottom: 18px;
}

.login-language-label {
  width: 52px;
  color: #606266;
  font-size: 14px;
}

.login-language-select {
  flex: 1;
}

.login-btn {
  width: 100%;
}
</style>

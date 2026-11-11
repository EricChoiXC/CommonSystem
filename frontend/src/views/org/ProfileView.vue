<template>
  <div class="profile-page">
    <!-- ==================== 卡片一:头像 ==================== -->
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-header">
          <span class="page-title">头像</span>
        </div>
      </template>
      <div class="avatar-section">
        <!-- 64px 圆形预览:无头像时回退账号首字符;与顶栏 HomeView 消费同一 profileStore,上传/移除后即时刷新 -->
        <img v-if="profileStore.avatarUrl" class="avatar-preview" :src="profileStore.avatarUrl" alt="头像" />
        <el-avatar v-else :size="64">{{ avatarText }}</el-avatar>
        <div class="header-btns">
          <!-- el-upload 不配 action、不自动上传:选中文件后按 Attachment.vue 先例手动组装 FormData 上传 -->
          <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="handleAvatarChange">
            <el-button type="primary" :loading="uploading">上传头像</el-button>
          </el-upload>
          <!-- 当前无头像时禁用移除 -->
          <el-button :disabled="!profileStore.avatarAttId" :loading="removing" @click="handleRemoveAvatar">
            移除头像
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- ==================== 卡片二:基本资料 ==================== -->
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-header">
          <span class="page-title">基本资料</span>
          <div class="header-btns">
            <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          </div>
        </div>
      </template>
      <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="40%" class="page-form">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="用户名" prop="fieldName">
              <el-input v-model="form.fieldName" placeholder="请输入用户名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="登录名">
              <!-- 登录名只读,不参与提交(后端亦不更新) -->
              <el-input v-model="form.fieldLoginName" disabled placeholder="登录名不可修改" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="fieldPhone">
              <el-input v-model="form.fieldPhone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="fieldEmail">
              <el-input v-model="form.fieldEmail" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别">
              <el-select v-model="form.fieldGender" style="width: 100%">
                <el-option v-for="opt in GENDER_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生日" prop="fieldBirthday">
              <el-date-picker
                v-model="form.fieldBirthday"
                type="date"
                format="YYYY-MM-DD"
                placeholder="请选择生日"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <!-- 长内容一行一字段 -->
          <el-col :span="24">
            <el-form-item label="联系地址" prop="fieldAddress">
              <el-input
                v-model="form.fieldAddress"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="请输入联系地址"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- ==================== 卡片三:关联账号 ==================== -->
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-header">
          <span class="page-title">关联账号</span>
        </div>
      </template>
      <!-- 总开关关闭:仅提示,不渲染渠道行 -->
      <el-alert
        v-if="loaded && !profile.openidBindEnabled"
        type="info"
        :closable="false"
        title="第三方账号绑定未开启(管理员可在系统设置中开启)"
      />
      <div v-else-if="loaded" class="openid-list">
        <el-row v-for="ch in channelRows" :key="ch.key" :gutter="12" align="middle" class="openid-row">
          <el-col :span="4" class="openid-channel">{{ ch.label }}</el-col>
          <el-col :span="13">
            <!-- 已绑定:昵称 + 绑定时间(bindTime 为后端返回的字符串,原样展示) -->
            <template v-if="ch.info">已绑定:{{ ch.info.nickname || '-' }}(绑定时间:{{ ch.info.bindTime || '-' }})</template>
            <span v-else class="openid-unbound">未绑定</span>
            <!-- 渠道凭证未配置:绑定按钮禁用,行尾灰字提示 -->
            <span v-if="!ch.enabled" class="openid-unconfigured">未配置</span>
          </el-col>
          <el-col :span="7" class="openid-actions">
            <el-button
              v-if="!ch.info"
              type="primary"
              size="small"
              :disabled="!ch.enabled"
              :loading="binding === ch.key"
              @click="handleBind(ch.key)"
            >
              绑定
            </el-button>
            <el-button v-else size="small" :loading="unbinding === ch.key" @click="handleUnbind(ch.key)">解绑</el-button>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOpenidBindUrl, loadProfile, saveProfile, unbindOpenid } from '@/api/org'
import { deleteAttachment, uploadAttachment } from '@/api/attachment'
import { useProfileStore } from '@/stores/profile'
import { useUserStore } from '@/stores/user'

/**
 * 个人资料页(/profile,本人自助,PF4-FE-002):
 * 三块独立卡片——头像(上传/移除,经 profileStore 统一刷新,顶栏 HomeView 消费同一 store 即时同步)、
 * 基本资料(loadProfile 回填与 saveProfile 保存,登录名只读不参与提交)、
 * 关联账号(微信/QQ 绑定状态展示、整页跳转绑定、解绑)。
 * 第三方 callback 以 /profile?bind={channel}:{reason} 回跳,onMounted 先提示再清理 query,避免刷新重复提示。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const profileStore = useProfileStore()

/** 性别下拉选项(码值与后端 dict gender 一致:0=其他 1=男 2=女,文案同 OrgFormView 先例) */
const GENDER_OPTIONS = [
  { value: 0, label: '其他' },
  { value: 1, label: '男' },
  { value: 2, label: '女' }
]

/** 支持绑定的第三方渠道(顺序即展示顺序) */
const CHANNELS = [
  { key: 'wechat', label: '微信' },
  { key: 'qq', label: 'QQ' }
]

/** 头像回退首字符(取登录态用户名,与 HomeView 顶栏口径一致) */
const avatarText = computed(() => {
  const name = userStore.userInfo && userStore.userInfo.fieldName
  return name ? name.charAt(0) : ''
})

const loading = ref(false) // 资料加载中
const loaded = ref(false) // 首次资料加载完成(关联账号区渲染开关)
const saving = ref(false)
const uploading = ref(false)
const removing = ref(false)
const binding = ref('') // 正在获取绑定跳转地址的渠道 key(防重复提交)
const unbinding = ref('') // 正在解绑的渠道 key(防重复提交)

/** 本人个人资料原始数据(loadProfile 的 res.data,含 openid 绑定信息与渠道开关) */
const profile = ref({})

const formRef = ref(null)

const form = reactive({
  fieldLoginName: '',
  fieldName: '',
  fieldPhone: '',
  fieldEmail: '',
  fieldGender: 1,
  fieldBirthday: null, // Date|null(el-date-picker 不设 value-format,保持 Date 对象)
  fieldAddress: ''
})

/** 生日校验:可空,不可晚于当天 */
function validateBirthday(rule, value, callback) {
  if (!value) {
    callback()
    return
  }
  const day = new Date(value)
  if (Number.isNaN(day.getTime())) {
    callback(new Error('生日格式不正确'))
    return
  }
  const now = new Date()
  const endOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999)
  if (day.getTime() > endOfToday.getTime()) {
    callback(new Error('生日不能晚于当天'))
    return
  }
  callback()
}

/** 校验规则与后端保存校验一致 */
const rules = {
  fieldName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  fieldPhone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  fieldEmail: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  fieldAddress: [{ max: 500, message: '联系地址不能超过 500 个字符', trigger: 'blur' }],
  fieldBirthday: [{ validator: validateBirthday, trigger: 'change' }]
}

/** 渠道行渲染数据:绑定信息取 profile.openid.{wechat|qq},渠道开关取 wechatEnabled/qqEnabled */
const channelRows = computed(() =>
  CHANNELS.map((ch) => {
    const openid = profile.value.openid || {}
    return {
      key: ch.key,
      label: ch.label,
      // 开关字段缺省时视为已开启,避免后端未返回该字段时误禁绑定
      enabled: profile.value[`${ch.key}Enabled`] !== false,
      info: openid[ch.key] || null
    }
  })
)

/** 加载本人个人资料并回填表单(fieldBirthday 为后端 Date JSON 串,new Date() 转换后赋值,空值保持 null) */
async function loadProfileData() {
  loading.value = true
  try {
    const res = await loadProfile()
    const data = res.data || {}
    profile.value = data
    Object.assign(form, {
      fieldLoginName: data.fieldLoginName || '',
      fieldName: data.fieldName || '',
      fieldPhone: data.fieldPhone || '',
      fieldEmail: data.fieldEmail || '',
      fieldGender: data.fieldGender == null ? 1 : data.fieldGender,
      fieldBirthday: data.fieldBirthday ? new Date(data.fieldBirthday) : null,
      fieldAddress: data.fieldAddress || ''
    })
  } catch (error) {
    ElMessage.error(error.message || '加载个人资料失败')
  } finally {
    loading.value = false
    loaded.value = true
  }
}

/** 保存基本资料:validate → saveProfile(回传登录态 fieldId 供后端一致性断言) → 成功后重载回显最新 */
async function handleSave() {
  if (!userStore.userInfo || !userStore.userInfo.fieldId) {
    ElMessage.warning('登录态缺失,请重新登录后再保存')
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    // 校验不通过:字段错误已就地提示
    return
  }
  saving.value = true
  try {
    await saveProfile({
      fieldId: userStore.userInfo.fieldId,
      fieldName: (form.fieldName || '').trim(),
      fieldPhone: form.fieldPhone || '',
      fieldEmail: form.fieldEmail || '',
      fieldAddress: form.fieldAddress || '',
      fieldGender: form.fieldGender,
      fieldBirthday: form.fieldBirthday || null
    })
    ElMessage.success('保存成功')
    await loadProfileData() // 重载回显后端落库后的最新数据
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 上传头像:FormData{file,modelName,modelId,key} → 取新附件 fieldId → 删旧(失败不阻断) → 刷新 store */
async function handleAvatarChange(uploadFile) {
  const file = uploadFile && uploadFile.raw
  if (!file) return
  const modelId = userStore.userInfo && userStore.userInfo.fieldId
  if (!modelId) {
    ElMessage.warning('登录态缺失,请重新登录后再上传头像')
    return
  }
  uploading.value = true
  try {
    // FormData 组装口径同 Attachment.vue 上传先例(file/modelName/modelId/key)
    const formData = new FormData()
    formData.append('file', file)
    formData.append('modelName', 'sys_org_user')
    formData.append('modelId', modelId)
    formData.append('key', 'profile')
    const res = await uploadAttachment(formData)
    // 后端 upload 返回 SysAttMainResponse(非 BaseResponse 包装),新附件 id 取 res.data.fieldId
    const newAttId = res.data && res.data.fieldId
    if (!newAttId) {
      throw new Error('上传返回数据缺少附件 ID')
    }
    // D3 口径:删除旧头像附件失败仅提示,不回滚本次上传、不阻断新头像生效
    const oldAttId = profileStore.avatarAttId
    if (oldAttId) {
      try {
        await deleteAttachment(oldAttId)
      } catch {
        ElMessage.warning('旧头像清理失败,不影响新头像使用')
      }
    }
    // 统一经 profileStore 刷新,顶栏(HomeView 消费同一 store)即时同步
    profileStore.applyAvatarAttId(newAttId)
    ElMessage.success('头像已更新')
  } catch (error) {
    ElMessage.error(error.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

/** 移除头像:二次确认 → 删除附件 → 清空 store(顶栏即时回退首字符头像) */
async function handleRemoveAvatar() {
  const attId = profileStore.avatarAttId
  if (!attId) return
  try {
    await ElMessageBox.confirm('确定移除头像?', '移除确认', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消'
    })
  } catch {
    return // 用户取消
  }
  removing.value = true
  try {
    await deleteAttachment(attId)
    profileStore.applyAvatarAttId(null)
    ElMessage.success('头像已移除')
  } catch (error) {
    ElMessage.error(error.message || '移除失败')
  } finally {
    removing.value = false
  }
}

/** 绑定:取第三方授权页地址后整页跳转(跳转发起后本页即卸载,失败时恢复按钮态) */
async function handleBind(channel) {
  binding.value = channel
  try {
    const res = await getOpenidBindUrl({ channel })
    const bindUrl = res.data && res.data.bindUrl
    if (!bindUrl) {
      throw new Error('未获取到绑定跳转地址')
    }
    window.location.href = bindUrl
  } catch (error) {
    ElMessage.error(error.message || '获取绑定地址失败')
    binding.value = ''
  }
}

/** 解绑:二次确认 → unbindOpenid → 重载刷新绑定状态 */
async function handleUnbind(channel) {
  const ch = CHANNELS.find((item) => item.key === channel)
  const label = ch ? ch.label : channel
  try {
    await ElMessageBox.confirm(`确定解除绑定的${label}账号吗?`, '解绑确认', {
      type: 'warning',
      confirmButtonText: '解绑',
      cancelButtonText: '取消'
    })
  } catch {
    return // 用户取消
  }
  unbinding.value = channel
  try {
    await unbindOpenid({ channel })
    ElMessage.success('解绑成功')
    await loadProfileData()
  } catch (error) {
    ElMessage.error(error.message || '解绑失败')
  } finally {
    unbinding.value = ''
  }
}

/** 第三方 callback 回跳提示:/profile?bind={channel}:{reason},提示后 router.replace 清理 query 防刷新重复提示 */
function handleBindCallback() {
  const bind = route.query.bind
  if (!bind) return
  const raw = String(bind)
  const sepIndex = raw.indexOf(':')
  const channelKey = sepIndex > -1 ? raw.slice(0, sepIndex) : ''
  const reason = sepIndex > -1 ? raw.slice(sepIndex + 1) : raw
  const channelName = channelKey === 'wechat' ? '微信' : channelKey === 'qq' ? 'QQ' : channelKey
  // reason → 提示文案与类型(success 之外按配置类 warning / 失败类 error 区分)
  const REASON_MESSAGES = {
    success: { type: 'success', text: `${channelName}账号绑定成功` },
    expired: { type: 'error', text: '绑定会话已过期,请重新发起绑定' },
    disabled: { type: 'warning', text: '第三方账号绑定未开启' },
    unconfigured: { type: 'warning', text: '第三方绑定未配置,请联系管理员' },
    oauth_error: { type: 'error', text: '第三方授权失败,请重试' },
    dup: { type: 'error', text: `该${channelName}账号已被其他账号绑定` }
  }
  const hit = REASON_MESSAGES[reason] || { type: 'error', text: '绑定失败' }
  ElMessage({ type: hit.type, message: hit.text })
  router.replace({ path: '/profile' })
}

onMounted(() => {
  handleBindCallback() // 先提示第三方回跳结果并清理 query
  loadProfileData()
})
</script>

<script>
export default { name: 'ProfileView' }
</script>

<style scoped>
/* ===== 本页专属样式(公共类复用 styles/form-page.css,不重复定义) ===== */

/* 三块卡片纵向堆叠间距 */
.profile-page .page-card + .page-card {
  margin-top: 16px;
}

/* 头像区:64px 圆形预览与操作按钮水平排列 */
.avatar-section {
  display: flex;
  align-items: center;
  gap: 24px;
}

.avatar-preview {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}

/* 关联账号渠道行 */
.openid-list {
  display: flex;
  flex-direction: column;
}

.openid-row {
  padding: 6px 0;
}

.openid-channel {
  font-weight: 600;
}

.openid-unbound {
  color: var(--el-text-color-secondary);
}

/* 渠道未配置:行尾灰字 */
.openid-unconfigured {
  color: var(--el-text-color-placeholder);
  margin-left: 8px;
}

.openid-actions {
  text-align: right;
}
</style>

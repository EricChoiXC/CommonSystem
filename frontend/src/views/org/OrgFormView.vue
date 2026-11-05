<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">{{ isEdit ? '编辑组织架构' : '新建组织架构' }}</span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
          <el-button v-if="canChangePwd" @click="pwdDialogVisible = true">修改密码</el-button>
          <el-button v-if="canSave" type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </div>
    </template>

    <el-form :model="form" label-width="40%" :disabled="saving" class="page-form">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="名称" required>
            <el-input v-model="form.fieldName" placeholder="请输入名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="编码" required>
            <el-input v-model="form.fieldCode" placeholder="请输入编码" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" required>
            <el-select v-model="form.fieldOrgType" style="width: 100%" :disabled="isEdit">
              <el-option v-for="opt in ORG_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="上级">
            <AddressBook v-model:field-id="form.fieldParentId" v-model:field-name="form.fieldParentName" org-type="ORGDEPT" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="是否虚拟">
            <el-switch v-model="form.fieldIsVirtual" :active-value="true" :inactive-value="false" active-text="是" inactive-text="否" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="启用状态">
            <el-switch v-model="form.fieldEnable" :active-value="true" :inactive-value="false" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </el-col>

        <!-- 机构专有字段 -->
        <template v-if="form.fieldOrgType === 1">
          <el-col :span="12">
            <el-form-item label="负责人">
              <AddressBook v-model:field-id="form.fieldLeaderId" v-model:field-name="form.fieldLeaderName" org-type="PERSON" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.fieldPhone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系邮箱">
              <el-input v-model="form.fieldEmail" placeholder="请输入联系邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系地址">
              <el-input v-model="form.fieldAddress" placeholder="请输入联系地址" />
            </el-form-item>
          </el-col>
        </template>

        <!-- 部门专有字段 -->
        <template v-else-if="form.fieldOrgType === 2">
          <el-col :span="12">
            <el-form-item label="负责人">
              <AddressBook v-model:field-id="form.fieldLeaderId" v-model:field-name="form.fieldLeaderName" org-type="PERSON" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.fieldPhone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系地址">
              <el-input v-model="form.fieldAddress" placeholder="请输入联系地址" />
            </el-form-item>
          </el-col>
        </template>

        <!-- 人员专有字段 -->
        <template v-else-if="form.fieldOrgType === 3">
          <el-col :span="12">
            <el-form-item label="登录名" required>
              <el-input v-model="form.fieldLoginName" placeholder="请输入登录名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码" required v-if="!isEdit">
              <el-input v-model="form.fieldPassword" type="password" show-password placeholder="请输入初始密码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别" required>
              <el-select v-model="form.fieldGender" style="width: 100%">
                <el-option v-for="opt in GENDER_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生日">
              <DateTimePicker v-model="form.fieldBirthday" calendar-type="date" date-format="YYYY-MM-DD" style="width: 100%" placeholder="请选择生日" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="在岗状态" required>
              <el-select v-model="form.fieldJobStatus" style="width: 100%">
                <el-option v-for="opt in JOB_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.fieldPhone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系邮箱">
              <el-input v-model="form.fieldEmail" placeholder="请输入联系邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系地址">
              <el-input v-model="form.fieldAddress" placeholder="请输入联系地址" />
            </el-form-item>
          </el-col>
        </template>

        <!-- 岗位专有字段 -->
        <template v-else-if="form.fieldOrgType === 4">
          <el-col :span="12">
            <el-form-item label="负责人">
              <AddressBook v-model:field-id="form.fieldLeaderId" v-model:field-name="form.fieldLeaderName" org-type="PERSON" />
            </el-form-item>
          </el-col>
        </template>
      </el-row>
    </el-form>

    <!-- 修改密码弹窗：仅人员编辑模式可用，密码不随编辑表单提交 -->
    <ChangePwdDialog v-model="pwdDialogVisible" :field-id="form.fieldId" />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { markTabToClose } from '@/utils/tabs'
import { ElMessage } from 'element-plus'
import AddressBook from '@/components/AddressBook.vue'
import ChangePwdDialog from '@/components/ChangePwdDialog.vue'
import DateTimePicker from '@/components/DateTimePicker.vue'
import { useUserStore } from '@/stores/user'
import { hasAuth } from '@/utils/auth'
import { getInitID } from '@/api/common'
import { loadElement, saveElement, updateElement } from '@/api/org'
import { getDictOptions } from '@/utils/dict'
import { watchRouteQuery } from '@/utils/routeQuery'

/**
 * 组织架构新建/编辑页：/org/create 新建、/org/edit/:id 编辑。
 * 新建时按 docs/开发规范.md 第 9 节先经 getInitID() 预取对象 ID，
 * 默认上级/类型取自列表页路由 query；编辑时按路由 id 经 load 接口回填表单。
 * 类型/在岗状态选项（dict.org_element_type / org_job_status）由 dict 模块提供。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isEdit = computed(() => route.path.includes('/edit/'))
const saving = ref(false)
const pwdDialogVisible = ref(false)

/** 组织架构类型下拉选项（dict.org_element_type，number 值，不含"全部"） */
const ORG_TYPE_OPTIONS = ref([])
/** 人员在岗状态下拉选项（dict.org_job_status，number 值，不含"全部"） */
const JOB_STATUS_OPTIONS = ref([])
/** 性别下拉选项（dict.gender，number 值，不含"全部"） */
const GENDER_OPTIONS = ref([])

const form = reactive({
  fieldId: '',
  fieldName: '',
  fieldCode: '',
  fieldParentId: '',
  fieldParentName: '',
  fieldOrgType: 1,
  fieldIsVirtual: false,
  fieldEnable: true,
  fieldLeaderId: '',
  fieldLeaderName: '',
  fieldPhone: '',
  fieldEmail: '',
  fieldAddress: '',
  fieldGender: 1,
  fieldBirthday: '',
  fieldLoginName: '',
  fieldPassword: '',
  fieldJobStatus: 1
})

/**
 * 修改密码按钮可见性（仅人员编辑模式，预判与 user/changePwd 后端鉴权一致）：
 * SYS_ORG_ADMIN / SYS_ORG_USER_MANAGER，或 用户本人（编辑对象为当前登录用户）。
 * 最终权限由后端 UserResolver 兜底。
 */
const canChangePwd = computed(() => {
  if (!isEdit.value || form.fieldOrgType !== 3 || !form.fieldId) return false
  if (hasAuth(['SYS_ORG_ADMIN', 'SYS_ORG_USER_MANAGER'])) return true
  return !!userStore.userInfo && userStore.userInfo.fieldId === form.fieldId
})

/**
 * 保存按钮可见性：新建需 SYS_ORG_ELEMENT_ADD 组、编辑需 SYS_ORG_ELEMENT_EDIT 组
 * （均含 SYS_ORG_ADMIN / SYS_ORG_ELEMENT_ADMIN，系统管理员 SYS_ADMIN 由 hasAuth 自动放行），
 * 最终权限由后端 ElementResolver 兜底。
 */
const canSave = computed(() =>
  isEdit.value
    ? hasAuth(['SYS_ORG_ADMIN', 'SYS_ORG_ELEMENT_ADMIN', 'SYS_ORG_ELEMENT_EDIT'])
    : hasAuth(['SYS_ORG_ADMIN', 'SYS_ORG_ELEMENT_ADMIN', 'SYS_ORG_ELEMENT_ADD'])
)

/** 新建初始化：预取对象 ID，默认上级/类型来自列表页路由 query */
async function initForCreate() {
  try {
    const res = await getInitID()
    form.fieldId = (res && res.data) || ''
  } catch {
    // 预取失败不阻塞：后端 save 会自动生成 ID
  }
  form.fieldParentId = route.query.parentId || ''
  form.fieldParentName = route.query.parentName || ''
  const queryType = Number(route.query.orgType)
  form.fieldOrgType = ORG_TYPE_OPTIONS.value.some((opt) => opt.value === queryType) ? queryType : 1
}

// 二次从组织架构列表页进入时实例复用、onMounted 不会重跑，监听 query 同步默认上级与类型
watchRouteQuery(route, (q) => {
  if (isEdit.value || !('parentId' in q)) return
  form.fieldParentId = q.parentId || ''
  form.fieldParentName = q.parentName || ''
  const queryType = Number(q.orgType)
  form.fieldOrgType = ORG_TYPE_OPTIONS.value.some((opt) => opt.value === queryType) ? queryType : 1
})

/** 编辑初始化：按路由 id 加载数据回填 */
async function initForEdit() {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少编辑对象的 ID')
    goBack()
    return
  }
  try {
    const res = await loadElement(id)
    const row = res.data
    if (!row) {
      ElMessage.error('未找到该组织架构元素')
      goBack()
      return
    }
    Object.assign(form, {
      fieldId: row.fieldId,
      fieldName: row.fieldName,
      fieldCode: row.fieldCode,
      fieldParentId: row.fieldParentId || '',
      fieldParentName: row.fieldParentName || '',
      fieldOrgType: row.fieldOrgType,
      fieldIsVirtual: !!row.fieldIsVirtual,
      fieldEnable: !!row.fieldEnable,
      fieldLeaderId: row.fieldLeaderId || '',
      fieldLeaderName: row.fieldLeaderName || '',
      fieldPhone: row.fieldPhone || '',
      fieldEmail: row.fieldEmail || '',
      fieldAddress: row.fieldAddress || '',
      fieldGender: row.fieldGender == null ? 1 : row.fieldGender,
      fieldBirthday: row.fieldBirthday || '',
      fieldLoginName: row.fieldLoginName || '',
      fieldPassword: '',
      fieldJobStatus: row.fieldJobStatus == null ? 1 : row.fieldJobStatus
    })
  } catch (error) {
    ElMessage.error(error.message || '加载数据失败')
    goBack()
  }
}

onMounted(async () => {
  // 先加载码值选项（新建页默认类型判断依赖 org_element_type），再初始化表单
  await loadDictOptions()
  if (isEdit.value) {
    initForEdit()
  } else {
    initForCreate()
  }
})

/** 加载类型/在岗状态/性别选项（dict.org_element_type / org_job_status / gender）；失败时保持空选项，不阻塞页面 */
async function loadDictOptions() {
  try {
    const [orgTypeOptions, jobStatusOptions, genderOptions] = await Promise.all([
      getDictOptions('org_element_type', { number: true }),
      getDictOptions('org_job_status', { number: true }),
      getDictOptions('gender', { number: true })
    ])
    // org_element_type 新增 5=角色、6=群组（仅后端使用，不可经本页创建），下拉仅保留 1~4
    ORG_TYPE_OPTIONS.value = orgTypeOptions.filter((opt) => [1, 2, 3, 4].includes(Number(opt.value)))
    JOB_STATUS_OPTIONS.value = jobStatusOptions
    GENDER_OPTIONS.value = genderOptions
  } catch {
    ORG_TYPE_OPTIONS.value = []
    JOB_STATUS_OPTIONS.value = []
    GENDER_OPTIONS.value = []
  }
}

async function handleSave() {
  if (!form.fieldName || !form.fieldName.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  if (!form.fieldCode || !form.fieldCode.trim()) {
    ElMessage.warning('编码不能为空')
    return
  }
  if (form.fieldOrgType === 3 && !isEdit.value && !form.fieldPassword) {
    ElMessage.warning('新建人员时密码不能为空')
    return
  }
  // 登录名校验：不可为邮箱格式或11位数字
  if (form.fieldOrgType === 3 && form.fieldLoginName) {
    const emailRegex = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
    if (emailRegex.test(form.fieldLoginName)) {
      ElMessage.warning('登录名不能为邮箱格式')
      return
    }
    const phone11Regex = /^\d{11}$/
    if (phone11Regex.test(form.fieldLoginName)) {
      ElMessage.warning('登录名不能为纯手机号（11位数字）')
      return
    }
  }
  saving.value = true
  try {
    const body = { ...form }
    body.fieldName = body.fieldName.trim()
    body.fieldCode = body.fieldCode.trim()
    if (!body.fieldPassword) {
      delete body.fieldPassword // 密码不随编辑接口更新（修改密码走「修改密码」按钮弹窗）
    }
    if (isEdit.value) {
      await updateElement(body)
      ElMessage.success('保存成功')
    } else {
      await saveElement(body)
      ElMessage.success('创建成功')
    }
    markTabToClose(route.path)
    router.push('/org')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/org')
}
</script>

<script>
export default { name: 'OrgFormView' }
</script>

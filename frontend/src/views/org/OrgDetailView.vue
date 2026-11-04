<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">组织架构详情</span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
          <el-button v-if="canChangePwd" @click="pwdDialogVisible = true">修改密码</el-button>
          <el-button v-if="canEdit" type="primary" @click="goEdit">编辑</el-button>
        </div>
      </div>
    </template>

    <!-- 详情网格：默认一行两字段（字段名:内容 = 2:3）；长内容字段（is-full）一行一字段（2:8） -->
    <div v-if="detail.row" class="detail-grid">
      <template v-for="(item, index) in detailFields" :key="item.placeholder ? `__placeholder__${index}` : item.label">
        <div v-if="item.placeholder" class="detail-item detail-item-placeholder">
          <span class="detail-label"></span>
          <span class="detail-value"></span>
        </div>
        <div v-else class="detail-item" :class="{ 'is-full': item.full }">
          <span class="detail-label">{{ item.label }}</span>
          <span class="detail-value">
            <el-tag v-if="item.tag" :type="item.tagType" size="small">{{ item.value }}</el-tag>
            <template v-else>{{ item.value }}</template>
          </span>
        </div>
      </template>
    </div>
    <el-empty v-else description="加载中或数据不存在" />

    <!-- 修改密码弹窗：仅人员详情可用，密码不随编辑表单提交 -->
    <ChangePwdDialog v-model="pwdDialogVisible" :field-id="detail.row && detail.row.fieldId" />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ChangePwdDialog from '@/components/ChangePwdDialog.vue'
import { useUserStore } from '@/stores/user'
import { hasAuth } from '@/utils/auth'
import { loadElement } from '@/api/org'
import { getDictTextMap } from '@/utils/dict'
import { buildDetailGridFields } from '@/utils/detail'

/**
 * 组织架构查看页：/org/detail/:id，按路由 id 经 load 接口加载数据只读展示。
 * 排版与新建/编辑页保持一致：默认一行两字段，字段名:字段值 = 2:3；
 * 长内容字段（联系地址）一行一字段，字段名:字段值 = 2:8。
 * 类型/性别/在岗状态文本（dict.org_element_type / gender / org_job_status）由 dict 模块提供。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const detail = reactive({ row: null })
const pwdDialogVisible = ref(false)

/** 码值文本映射（dict：org_element_type / gender / org_job_status / yes_no，number/boolean 值） */
const ORG_TYPE_TEXT = ref({})
const GENDER_TEXT = ref({})
const JOB_STATUS_TEXT = ref({})
const YES_NO_TEXT = ref({})

/**
 * 编辑按钮可见性：与组织架构管理页编辑/停用启用一致
 * （SYS_ORG_ADMIN / SYS_ORG_ELEMENT_ADMIN / SYS_ORG_ELEMENT_EDIT），
 * 最终权限由后端 ElementResolver 兜底。
 */
const canEdit = computed(() => hasAuth(['SYS_ORG_ADMIN', 'SYS_ORG_ELEMENT_ADMIN', 'SYS_ORG_ELEMENT_EDIT']))

/**
 * 修改密码按钮可见性（仅人员详情，预判与 user/changePwd 后端鉴权一致）：
 * SYS_ORG_ADMIN / SYS_ORG_USER_MANAGER，或 用户本人（查看对象为当前登录用户）。
 * 最终权限由后端 UserResolver 兜底。
 */
const canChangePwd = computed(() => {
  const r = detail.row
  if (!r || r.fieldOrgType !== 3 || !r.fieldId) return false
  if (hasAuth(['SYS_ORG_ADMIN', 'SYS_ORG_USER_MANAGER'])) return true
  return !!userStore.userInfo && userStore.userInfo.fieldId === r.fieldId
})

onMounted(async () => {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少查看对象的 ID')
    goBack()
    return
  }
  loadDictOptions()
  try {
    const res = await loadElement(id)
    detail.row = res.data || null
    if (!detail.row) {
      ElMessage.warning('未找到该组织架构元素')
    }
  } catch (error) {
    ElMessage.error(error.message || '加载详情失败')
    goBack()
  }
})

/** 加载码值文本映射（org_element_type / gender / org_job_status / yes_no）；失败时文本回退原值 */
async function loadDictOptions() {
  try {
    const [orgTypeMap, genderMap, jobStatusMap, yesNoMap] = await Promise.all([
      getDictTextMap('org_element_type', { number: true }),
      getDictTextMap('gender', { number: true }),
      getDictTextMap('org_job_status', { number: true }),
      getDictTextMap('yes_no', { boolean: true })
    ])
    ORG_TYPE_TEXT.value = orgTypeMap
    GENDER_TEXT.value = genderMap
    JOB_STATUS_TEXT.value = jobStatusMap
    YES_NO_TEXT.value = yesNoMap
  } catch {
    // 码值加载失败不阻塞详情展示
  }
}

/** 详情字段数组：按节点类型动态组装，长内容字段标记 full（一行一字段，2:8） */
const detailFields = computed(() => {
  const r = detail.row
  if (!r) return []
  const orgType = r.fieldOrgType
  const fields = [
    { label: '名称', value: r.fieldName },
    { label: '编码', value: r.fieldCode },
    { label: '类型', value: orgTypeText(orgType) },
    { label: '上级', value: r.fieldParentName || '根节点' },
    { label: '是否虚拟', value: yesNoText(r.fieldIsVirtual) },
    { label: '启用状态', value: r.fieldEnable ? '启用' : '停用', tag: true, tagType: r.fieldEnable ? 'success' : 'danger' }
  ]
  // 机构/部门/岗位：负责人
  if (orgType === 1 || orgType === 2 || orgType === 4) {
    fields.push({ label: '负责人', value: r.fieldLeaderName || '-' })
  }
  // 机构/部门/人员：联系电话
  if (orgType === 1 || orgType === 2 || orgType === 3) {
    fields.push({ label: '联系电话', value: r.fieldPhone || '-' })
  }
  // 机构/人员：联系邮箱
  if (orgType === 1 || orgType === 3) {
    fields.push({ label: '联系邮箱', value: r.fieldEmail || '-' })
  }
  // 机构/部门/人员：联系地址（长内容，一行一字段）
  if (orgType === 1 || orgType === 2 || orgType === 3) {
    fields.push({ label: '联系地址', value: r.fieldAddress || '-', full: true })
  }
  // 人员专有字段
  if (orgType === 3) {
    fields.push(
      { label: '登录名', value: r.fieldLoginName || '-' },
      { label: '性别', value: genderText(r.fieldGender) },
      { label: '生日', value: formatTime(r.fieldBirthday, true) },
      { label: '在岗状态', value: jobStatusText(r.fieldJobStatus) }
    )
  }
  fields.push(
    { label: '创建时间', value: formatTime(r.fieldCreateTime) },
    { label: '更新时间', value: formatTime(r.fieldUpdateTime) }
  )
  return buildDetailGridFields(fields)
})

function orgTypeText(type) {
  return ORG_TYPE_TEXT.value[type] || '-'
}

function genderText(value) {
  return GENDER_TEXT.value[value] ?? '-'
}

function jobStatusText(value) {
  return JOB_STATUS_TEXT.value[value] ?? '-'
}

function yesNoText(value) {
  return YES_NO_TEXT.value[value] ?? '-'
}

function formatTime(value, dateOnly = false) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  const text = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  return dateOnly ? text : `${text} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function goBack() {
  router.push('/org')
}

function goEdit() {
  if (detail.row && detail.row.fieldId) {
    router.push(`/org/edit/${detail.row.fieldId}`)
  }
}
</script>

<script>
export default { name: 'OrgDetailView' }
</script>

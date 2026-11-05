<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">{{ isEdit ? '编辑群组' : '新建群组' }}</span>
        <div class="header-btns">
          <el-button plain @click="goBack">返回</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </div>
    </template>

    <el-form :model="form" label-width="40%" :disabled="saving" class="page-form">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="名称" required>
            <el-input v-model="form.fieldName" maxlength="200" show-word-limit placeholder="请输入群组名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" required>
            <!-- 普通用户默认且锁定「个人」（后端强制 PERSONAL 双层保证）；管理员默认「公共」可切换 -->
            <el-select v-model="form.fieldType" style="width: 100%" :disabled="!isAdmin" @change="handleTypeChange">
              <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>

        <!-- 群组成员：独占一行 2:8 比例（label 20% / 内容 80%），地址本复选（仅人员），写 sys_org_group_user -->
        <el-col :span="24">
          <el-form-item label="群组成员" label-width="20%">
            <AddressBook
              v-model:field-id="form.userIdsText"
              v-model:field-name="form.userNamesText"
              org-type="PERSON"
              multi
            />
          </el-form-item>
        </el-col>

        <!-- 可见用户：仅公共群组显示，独占一行；机构/部门/岗位/人员均可选，写 sys_org_group_viewer -->
        <el-col v-if="form.fieldType === 'COMMON'" :span="24">
          <el-form-item label="可见用户" label-width="20%">
            <AddressBook
              v-model:field-id="form.viewerIdsText"
              v-model:field-name="form.viewerNamesText"
              org-type="ORGDEPTPOSTPERSON"
              multi
            />
            <div class="viewer-tip">不选择时默认全员可见</div>
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="是否启用" label-width="20%">
            <el-switch
              v-model="form.fieldEnable"
              :active-value="true"
              :inactive-value="false"
              active-text="启用"
              inactive-text="停用"
            />
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
import AddressBook from '@/components/AddressBook.vue'
import { useUserStore } from '@/stores/user'
import { hasAuth } from '@/utils/auth'
import { getInitID } from '@/api/common'
import { loadGroup, saveGroup, updateGroup } from '@/api/org'
import { getDictOptions } from '@/utils/dict'

/**
 * 群组新建/编辑页：/org/group/create 新建、/org/group/edit/:id 编辑（02-群组功能分析.md 3.3）。
 * 新建时按 docs/开发规范.md 第 9 节先经 getInitID() 预取对象 ID；
 * 编辑时按路由 id 经 loadGroup 回填（含成员 userIds/userNames 与可见范围 viewerIds/viewerNames），
 * 回填后按维护权限口径（管理员 || 自己的个人群组）前置拦截，最终权限由后端兜底。
 * 类型（dict.org_group_type：COMMON=公共 / PERSONAL=个人）普通用户默认且锁定「个人」；
 * 切到「个人」隐藏并清空可见用户（个人群组恒全员可见，后端派生 fieldIsAllViewer）。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isEdit = computed(() => route.path.includes('/edit/'))
const saving = ref(false)

/** 管理员（与后端 group 鉴权一致）：群组管理员 / 模块管理员，SYS_ADMIN 由 hasAuth 放行 */
const AUTH_GROUP_ADMIN = ['SYS_ORG_GROUP_ADMIN', 'SYS_ORG_ADMIN']
const isAdmin = computed(() => hasAuth(AUTH_GROUP_ADMIN))

/** 群组类型下拉选项（dict.org_group_type，字符串码值 COMMON/PERSONAL） */
const typeOptions = ref([])

const form = reactive({
  fieldId: '',
  fieldName: '',
  fieldType: '', // 新建在 initForCreate 按角色赋默认值；编辑按 loadGroup 回填
  fieldEnable: true,
  userIdsText: '', // AddressBook 回显：分号分隔的成员人员 ID
  userNamesText: '', // AddressBook 回显：分号分隔的成员名称
  viewerIdsText: '', // AddressBook 回显：分号分隔的可见范围节点 ID
  viewerNamesText: '' // AddressBook 回显：分号分隔的可见范围节点名称
})

/** 新建初始化：普通用户默认且锁定「个人」，管理员默认「公共」；预取对象 ID */
async function initForCreate() {
  form.fieldType = isAdmin.value ? 'COMMON' : 'PERSONAL'
  try {
    const res = await getInitID()
    form.fieldId = (res && res.data) || ''
  } catch {
    // 预取失败不阻塞：后端 save 会自动生成 ID
  }
}

/** 编辑对象的维护权限判定（02 文档 2.3，与 GroupManageView.canManage 同口径）：
 * 管理员可维护全部；普通用户仅可维护自己的个人群组 */
function canMaintain(row) {
  if (isAdmin.value) {
    return true
  }
  const currentId = userStore.userInfo && userStore.userInfo.fieldId
  return row.fieldType === 'PERSONAL' && !!currentId && row.fieldOwnerId === currentId
}

/** 编辑初始化：按路由 id 加载数据回填，无维护权限时提示并返回列表 */
async function initForEdit() {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少编辑对象的 ID')
    goBack()
    return
  }
  try {
    const res = await loadGroup(id)
    const row = res.data
    if (!row) {
      ElMessage.error('未找到该群组')
      goBack()
      return
    }
    if (!canMaintain(row)) {
      ElMessage.error('无权编辑该群组')
      goBack()
      return
    }
    form.fieldId = row.fieldId
    form.fieldName = row.fieldName || ''
    form.fieldType = row.fieldType || 'PERSONAL'
    form.fieldEnable = row.fieldEnable !== false
    form.userIdsText = (row.userIds || []).join(';')
    form.userNamesText = (row.userNames || []).join(';')
    form.viewerIdsText = (row.viewerIds || []).join(';')
    form.viewerNamesText = (row.viewerNames || []).join(';')
  } catch (error) {
    ElMessage.error(error.message || '加载数据失败')
    goBack()
  }
}

onMounted(() => {
  // 类型选项（dict.org_group_type）；接口失败保持空选项，不阻塞页面（默认类型不依赖字典）
  getDictOptions('org_group_type')
    .then((opts) => {
      typeOptions.value = opts
    })
    .catch(() => {
      typeOptions.value = []
    })
  if (isEdit.value) {
    initForEdit()
  } else {
    initForCreate()
  }
})

/** 类型切换：切到「个人」隐藏并清空可见用户（切回「公共」不恢复已清空值） */
function handleTypeChange(value) {
  if (value === 'PERSONAL') {
    form.viewerIdsText = ''
    form.viewerNamesText = ''
  }
}

async function handleSave() {
  if (!form.fieldName || !form.fieldName.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  if (!form.fieldType) {
    ElMessage.warning('类型不能为空')
    return
  }
  saving.value = true
  try {
    const body = {
      fieldId: form.fieldId,
      fieldName: form.fieldName.trim(),
      fieldType: form.fieldType,
      userIds: splitSelected(form.userIdsText),
      // 仅公共群组维护可见范围；个人群组恒全员可见（后端派生 fieldIsAllViewer，前端不传）
      viewerIds: form.fieldType === 'COMMON' ? splitSelected(form.viewerIdsText) : [],
      fieldEnable: form.fieldEnable
    }
    if (isEdit.value) {
      await updateGroup(body)
      ElMessage.success('保存成功')
    } else {
      await saveGroup(body)
      ElMessage.success('创建成功')
    }
    markTabToClose(route.path)
    router.push('/org/group')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 按 AddressBook 分隔符拆分 ID 字符串为数组 */
function splitSelected(text) {
  if (!text) return []
  return String(text)
    .split(';')
    .map((s) => s.trim())
    .filter(Boolean)
}

function goBack() {
  router.push('/org/group')
}
</script>

<script>
export default { name: 'GroupFormView' }
</script>

<style scoped>
/* 可见用户提示：地址本下方整行灰字（el-form-item 内容区 flex-wrap 换行） */
.viewer-tip {
  width: 100%;
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>

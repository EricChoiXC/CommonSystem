<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">{{ isEdit ? '编辑角色' : '新建角色' }}</span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </div>
    </template>

    <el-form :model="form" label-width="40%" :disabled="saving" class="page-form">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="角色名" required>
            <el-input v-model="form.fieldName" placeholder="请输入角色名" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="编码">
            <el-input v-model="form.fieldCode" placeholder="请输入编码" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="是否启用" required label-width="20%">
            <el-switch
              v-model="form.fieldEnable"
              :active-value="true"
              :inactive-value="false"
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
        </el-col>

        <!-- 组织架构：独占一行 2:8 比例（label 20% / 内容 80%），地址本复选，关联 sys_org_role_element -->
        <el-col :span="24">
          <el-form-item label="组织架构" label-width="20%">
            <AddressBook
              v-model:field-id="form.elementIdsText"
              v-model:field-name="form.elementNamesText"
              org-type="ALL"
              multi
            />
          </el-form-item>
        </el-col>

        <!-- 权限：独占一行 2:8 比例（label 20% / 内容 80%），按 sys_org_auth.field_model 分组、Card 展示，已选来源 sys_org_role_auth -->
        <el-col :span="24">
          <el-form-item label="权限" label-width="20%">
            <div class="auth-groups" v-loading="authLoading">
              <template v-if="authGroups.length">
                <el-card
                  v-for="group in authGroups"
                  :key="group.model"
                  shadow="never"
                  class="auth-group-card"
                >
                  <template #header>
                    <span class="auth-group-title">{{ group.modelName || group.model }}</span>
                  </template>
                  <el-checkbox-group v-model="form.authIds" class="auth-checkbox-group">
                    <el-checkbox v-for="auth in group.auths" :key="auth.fieldId" :value="auth.fieldId">
                      {{ auth.fieldName || auth.fieldCode }}
                      <span v-if="auth.fieldName" class="auth-code">{{ auth.fieldCode }}</span>
                    </el-checkbox>
                  </el-checkbox-group>
                </el-card>
              </template>
              <el-empty v-else description="暂无权限数据" :image-size="60" />
            </div>
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
import { getInitID } from '@/api/common'
import { loadRole, queryAuthOptions, saveRole, updateRole } from '@/api/org'

/**
 * 角色新建/编辑页：/role/create 新建、/role/edit/:id 编辑。
 * 新建时按 docs/开发规范.md 第 9 节先经 getInitID() 预取对象 ID；
 * 编辑时按路由 id 经 load 接口回填（含关联组织架构与已选权限）。
 * 基本字段外额外维护「组织架构」（地址本复选，sys_org_role_element）
 * 与「权限」（可选项按 sys_org_auth.field_model 分组 Card 展示，已选 sys_org_role_auth）。
 */
const route = useRoute()
const router = useRouter()

const isEdit = computed(() => route.path.includes('/edit/'))
const saving = ref(false)
const authLoading = ref(false)
const authGroups = ref([])

const form = reactive({
  fieldId: '',
  fieldName: '',
  fieldCode: '',
  fieldEnable: true,
  elementIdsText: '', // AddressBook 回显：分号分隔的组织架构 ID
  elementNamesText: '', // AddressBook 回显：分号分隔的组织架构名称
  authIds: [] // 已选权限 ID（sys_org_role_auth）
})

/** 加载权限可选项：按 field_model 分组，分组标题取 application.yml 的 MODEL_NAME */
async function loadAuthOptions() {
  authLoading.value = true
  try {
    const res = await queryAuthOptions()
    authGroups.value = res.data || []
  } catch (error) {
    ElMessage.error(error.message || '权限数据加载失败')
    authGroups.value = []
  } finally {
    authLoading.value = false
  }
}

/** 新建初始化：预取对象 ID */
async function initForCreate() {
  try {
    const res = await getInitID()
    form.fieldId = (res && res.data) || ''
  } catch {
    // 预取失败不阻塞：后端 save 会自动生成 ID
  }
  loadAuthOptions()
}

/** 编辑初始化：按路由 id 加载数据回填 */
async function initForEdit() {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少编辑对象的 ID')
    goBack()
    return
  }
  try {
    const res = await loadRole(id)
    const row = res.data
    if (!row) {
      ElMessage.error('未找到该角色')
      goBack()
      return
    }
    form.fieldId = row.fieldId
    form.fieldName = row.fieldName || ''
    form.fieldCode = row.fieldCode || ''
    form.fieldEnable = row.fieldEnable !== false
    form.elementIdsText = (row.elementIds || []).join(';')
    form.elementNamesText = (row.elementNames || []).join(';')
    form.authIds = row.authIds || []
    loadAuthOptions()
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

async function handleSave() {
  if (!form.fieldName || !form.fieldName.trim()) {
    ElMessage.warning('角色名不能为空')
    return
  }
  saving.value = true
  try {
    const body = {
      fieldId: form.fieldId,
      fieldName: form.fieldName.trim(),
      fieldCode: form.fieldCode ? form.fieldCode.trim() : null,
      fieldEnable: form.fieldEnable,
      elementIds: splitSelected(form.elementIdsText),
      authIds: form.authIds
    }
    if (isEdit.value) {
      await updateRole(body)
      ElMessage.success('保存成功')
    } else {
      await saveRole(body)
      ElMessage.success('创建成功')
    }
    markTabToClose(route.path)
    router.push('/role')
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
  router.push('/role')
}
</script>

<script>
export default { name: 'RoleFormView' }
</script>

<style scoped>
/* —— 权限分组 Card 区 —— */
.auth-groups {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}

.auth-group-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.auth-group-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.auth-checkbox-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
}

.auth-code {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

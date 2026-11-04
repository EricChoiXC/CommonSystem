<template>
  <div class="page-card">
    <el-card>
      <template #header>
        <div class="page-header">
          <span class="page-title">流程公共配置</span>
          <div class="setting-actions">
            <el-button :loading="loading" @click="load">刷新</el-button>
            <el-button type="primary" :loading="saving" :disabled="dirtyCount === 0" @click="save">提交</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="fieldDesc" label="中文描述" min-width="240" />
        <el-table-column label="值" min-width="320">
          <template #default="{ row }">
            <el-switch
              v-if="row.fieldType === 'process'"
              v-model="row.fieldValue"
              active-value="true"
              inactive-value="false"
              inline-prompt
              active-text="是"
              inactive-text="否"
              @change="markDirty(row)"
            />
            <el-input v-else v-model="row.fieldValue" :placeholder="row.fieldDefaultValue" @input="markDirty(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.fieldValue === row.fieldDefaultValue" @click="reset(row)">
              重置
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="dirtyCount > 0" class="setting-tip">
        有 {{ dirtyCount }} 项配置未提交，点击「提交」保存
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { queryProcessSettingList, resetProcessSetting, saveProcessSettings } from '@/api/process'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
// 本地变更集：fieldId -> 修改后的值（仅含与加载值不一致的项）
const dirtyMap = ref({})

const dirtyCount = computed(() => Object.keys(dirtyMap.value).length)

/** 布尔配置展示归一化：统一为小写 true/false 字符串（与后端 yml 默认值一致，Boolean.valueOf 大小写不敏感） */
function normalizeBool(value) {
  return String(value == null ? '' : value).toLowerCase() === 'true' ? 'true' : 'false'
}

/** 加载配置列表：重新读取当前配置项值 */
async function load() {
  loading.value = true
  try {
    const res = await queryProcessSettingList()
    // 保存服务端加载原值（_original）用于变更判定，不参与提交；
    // process 类型布尔配置归一化为小写 true/false，保证开关状态与 dirty 比较一致
    rows.value = (res.data || []).map((item) => {
      const isProcess = item.fieldType === 'process'
      const value = isProcess ? normalizeBool(item.fieldValue) : item.fieldValue
      const defaultValue = isProcess ? normalizeBool(item.fieldDefaultValue) : item.fieldDefaultValue
      return { ...item, fieldValue: value, fieldDefaultValue: defaultValue, _original: value }
    })
    dirtyMap.value = {}
  } finally {
    loading.value = false
  }
}

/** 输入变化时登记变更项：与加载原值不一致才记录，改回原值则移出变更集 */
function markDirty(row) {
  const next = { ...dirtyMap.value }
  if (row.fieldValue !== row._original) {
    next[row.fieldId] = row.fieldValue
  } else {
    delete next[row.fieldId]
  }
  dirtyMap.value = next
}

/** 提交：仅将有更新（或重置）的配置项提交保存 */
async function save() {
  const items = Object.entries(dirtyMap.value).map(([fieldId, fieldValue]) => ({ fieldId, fieldValue }))
  if (items.length === 0) {
    return
  }
  saving.value = true
  try {
    await saveProcessSettings(items)
    ElMessage.success('配置已提交')
    await load()
  } finally {
    saving.value = false
  }
}

/** 重置：将单个配置项还原为默认值 */
async function reset(row) {
  try {
    await resetProcessSetting(row.fieldId)
    row.fieldValue = row.fieldDefaultValue
    row._original = row.fieldDefaultValue
    const next = { ...dirtyMap.value }
    delete next[row.fieldId]
    dirtyMap.value = next
    ElMessage.success('已还原为默认值')
  } catch (e) {
    // 错误提示由 request 拦截统一处理
  }
}

onMounted(load)
</script>

<script>
export default { name: 'ProcessSettingView' }
</script>

<style scoped>
.setting-actions {
  display: flex;
  gap: 8px;
}

.setting-tip {
  margin-top: 10px;
  font-size: 13px;
  color: var(--el-color-warning);
}
</style>

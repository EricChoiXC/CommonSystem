<template>
  <div class="page-card">
    <el-card>
      <template #header>
        <div class="page-header">
          <span class="page-title">公共配置</span>
          <div class="setting-actions">
            <el-button :loading="loading" @click="loadData">刷新</el-button>
            <el-button type="primary" :loading="saving" :disabled="dirtyCount === 0" @click="handleSave">提交</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="fieldDesc" label="中文描述" min-width="240" />
        <el-table-column label="值" min-width="320">
          <template #default="{ row }">
            <el-switch
              v-if="row.fieldType === 'bool'"
              v-model="row.fieldValue"
              active-value="true"
              inactive-value="false"
              inline-prompt
              active-text="是"
              inactive-text="否"
              @change="markDirty(row)"
            />
            <el-input-number
              v-else-if="row.fieldType === 'int'"
              v-model="row.fieldValue"
              :min="0"
              :step="1"
              :placeholder="row.fieldDefaultValue"
              style="width: 100%"
              @change="markDirty(row)"
            />
            <el-input v-else v-model="row.fieldValue" :placeholder="row.fieldDefaultValue" @input="markDirty(row)" />
          </template>
        </el-table-column>
        <el-table-column label="默认值" min-width="120">
          <template #default="{ row }">
            <span class="default-value">{{ row.fieldDefaultValue }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.fieldValue === row.fieldDefaultValue" @click="resetRow(row)">
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
import { loadSetting, updateSetting } from '@/api/common-setting'

/**
 * 公共配置管理页：/setting
 * 仅 SYS_ADMIN 权限可见，维护系统级公共配置项。
 * 参照 ProcessSettingView 的表格 + 脏标记方案。
 */

/** 配置项元数据：key、中文描述、类型、默认值 */
const CONFIG_META = [
  { fieldKey: 'login_email_enable', fieldDesc: '允许邮箱登录', fieldType: 'bool', fieldDefaultValue: 'false' },
  { fieldKey: 'login_phone_enable', fieldDesc: '允许电话登录', fieldType: 'bool', fieldDefaultValue: 'false' },
  { fieldKey: 'pwd_change_cycle', fieldDesc: '定期更换密码周期（天）', fieldType: 'int', fieldDefaultValue: '0' },
  { fieldKey: 'pwd_repeat_limit', fieldDesc: '不可重复设定密码次数', fieldType: 'int', fieldDefaultValue: '3' },
  { fieldKey: 'openid_bind_enable', fieldDesc: '开启第三方账号绑定（微信/QQ）', fieldType: 'bool', fieldDefaultValue: 'false' }
]

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
/** 本地变更集：fieldKey -> 修改后的值（仅含与加载值不一致的项） */
const dirtyMap = ref({})

const dirtyCount = computed(() => Object.keys(dirtyMap.value).length)

/** 布尔配置展示归一化：统一为小写 true/false 字符串 */
function normalizeBool(value) {
  return String(value == null ? '' : value).toLowerCase() === 'true' ? 'true' : 'false'
}

/** 加载配置列表：从后端读取当前值，与元数据合并构建表格行 */
async function loadData() {
  loading.value = true
  try {
    const res = await loadSetting()
    const data = res.data || {}
    // 用元数据构建行，后端返回的值覆盖 fieldValue
    rows.value = CONFIG_META.map((meta) => {
      const raw = data[meta.fieldKey]
      let value
      if (meta.fieldType === 'bool') {
        value = raw != null ? normalizeBool(raw) : meta.fieldDefaultValue
      } else if (meta.fieldType === 'int') {
        value = raw != null ? String(Number(raw)) : meta.fieldDefaultValue
      } else {
        value = raw != null ? raw : meta.fieldDefaultValue
      }
      // 用 fieldKey 作为行标识（fieldId 复用 fieldKey 字段做 dirty 追踪）
      return {
        fieldId: meta.fieldKey,
        fieldKey: meta.fieldKey,
        fieldDesc: meta.fieldDesc,
        fieldType: meta.fieldType,
        fieldValue: value,
        fieldDefaultValue: meta.fieldDefaultValue,
        _original: value
      }
    })
    dirtyMap.value = {}
  } finally {
    loading.value = false
  }
}

/** 输入变化时登记变更项：与加载原值不一致才记录，改回原值则移出变更集 */
function markDirty(row) {
  // int 类型 el-input-number 的 v-model 绑定的是 Number，转字符串统一比较
  const current = row.fieldType === 'int' ? String(row.fieldValue) : row.fieldValue
  const original = row._original
  const next = { ...dirtyMap.value }
  if (current !== original) {
    next[row.fieldKey] = current
  } else {
    delete next[row.fieldKey]
  }
  dirtyMap.value = next
}

/** 提交：仅将有更新的配置项提交保存 */
async function handleSave() {
  const items = Object.entries(dirtyMap.value).map(([fieldKey, fieldValue]) => ({ fieldKey, fieldValue }))
  if (items.length === 0) return
  saving.value = true
  try {
    for (const item of items) {
      await updateSetting(item.fieldKey, item.fieldValue)
    }
    ElMessage.success('配置已提交')
    await loadData()
  } finally {
    saving.value = false
  }
}

/** 重置：将单个配置项还原为默认值 */
function resetRow(row) {
  row.fieldValue = row.fieldDefaultValue
  row._original = row.fieldDefaultValue
  const next = { ...dirtyMap.value }
  delete next[row.fieldKey]
  dirtyMap.value = next
  ElMessage.success('已还原为默认值')
}

onMounted(loadData)
</script>

<script>
export default { name: 'CommonSettingView' }
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

.default-value {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
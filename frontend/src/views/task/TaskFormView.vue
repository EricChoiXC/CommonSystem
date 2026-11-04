<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">编辑定时任务</span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
          <!-- 状态为运行中时额外显示「中止当前任务」按钮 -->
          <el-button v-if="form.fieldStatus === 'RUNNING'" type="danger" plain :loading="stopping" @click="handleStop">
            中止当前任务
          </el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </div>
    </template>

    <el-form :model="form" label-width="40%" :disabled="saving" class="page-form">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="任务名" required>
            <el-input v-model="form.fieldName" placeholder="请输入任务名" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="cron表达式" required>
            <el-input v-model="form.fieldCron" placeholder="如 0 0 2 * * ?（秒 分 时 日 月 周）" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-tag :type="statusType(form.fieldStatus)" size="small">
              {{ statusText(form.fieldStatus) }}
            </el-tag>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="任务类名">
            <span class="readonly-text">{{ form.fieldClass || '-' }}</span>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="登记时间" label-width="20%">
            <span class="readonly-text">{{ formatTime(form.fieldCreateTime) }}</span>
          </el-form-item>
        </el-col>

        <!-- 参数列表值：独占一行 2:8 比例（label 20% / 内容 80%），JSON 保存（值均为字符串） -->
        <el-col :span="24">
          <el-form-item label="参数列表值" label-width="20%">
            <div class="params-editor">
              <div v-for="(item, index) in form.params" :key="index" class="params-row">
                <el-input v-model="item.key" placeholder="参数名" class="params-key" />
                <span class="params-eq">=</span>
                <el-input v-model="item.value" placeholder="参数值" class="params-value" />
                <el-button link type="danger" :disabled="saving" @click="removeParam(index)">删除</el-button>
              </div>
              <el-button size="small" :disabled="saving" @click="addParam">+ 添加参数</el-button>
              <div class="params-tip">参数以 JSON 对象保存，所有值按字符串类型存储；任务执行时以 key=value 传入 execute 方法。</div>
            </div>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { markTabToClose } from '@/utils/tabs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { loadTask, stopTask, updateTask } from '@/api/task'
import { getDictTextMap } from '@/utils/dict'

/**
 * 定时任务编辑页：/task/edit/:id。
 * 按路由 id 经 load 接口回填；可编辑维护 名称、cron表达式、参数列表值 三个字段；
 * 提交时后端校验 cron 合法性；参数列表值在数据库中保存为 json 格式，所有值按字符串类型保存。
 * 根据「状态」字段的值，状态为运行中时额外显示「中止当前任务」按钮，
 * 执行后停止当前任务实例，当前任务按「失败」结果记录。
 */
const route = useRoute()
const router = useRouter()

const saving = ref(false)
const stopping = ref(false)

const form = reactive({
  fieldId: '',
  fieldName: '',
  fieldCron: '',
  fieldClass: '',
  fieldStatus: '',
  fieldCreateTime: '',
  params: [] // [{ key, value }]
})

/** 状态文案（dict.task_status，仅文本映射；标签类型映射 statusType 为本地展示样式，非码值） */
const STATUS_TEXT = ref({})

function statusText(value) {
  return STATUS_TEXT.value[value] || value || '-'
}

function statusType(value) {
  return (
    {
      WAITING: 'info',
      RUNNING: 'warning',
      FINISH: 'success',
      BLOCK: 'danger',
      FAIL: 'danger'
    }[value] || 'info'
  )
}

/** 参数 JSON 对象字符串 → [{ key, value }] 编辑行 */
function parseParams(json) {
  if (!json) return []
  try {
    const map = typeof json === 'string' ? JSON.parse(json) : json
    if (!map || typeof map !== 'object') return []
    return Object.entries(map).map(([key, value]) => ({ key, value: String(value) }))
  } catch {
    return []
  }
}

/** 编辑行 → 参数 JSON 对象字符串（跳过空参数名） */
function buildParamsJson(rows) {
  const map = {}
  for (const row of rows || []) {
    const key = (row.key || '').trim()
    if (!key) continue
    map[key] = row.value == null ? '' : String(row.value)
  }
  return JSON.stringify(map)
}

function addParam() {
  form.params.push({ key: '', value: '' })
}

function removeParam(index) {
  form.params.splice(index, 1)
}

/** 编辑初始化：按路由 id 加载数据回填，缺 id 或加载失败时提示并返回列表页 */
async function initForEdit() {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少编辑对象的 ID')
    goBack()
    return
  }
  try {
    const res = await loadTask(id)
    const row = res.data
    if (!row) {
      ElMessage.error('未找到该任务')
      goBack()
      return
    }
    form.fieldId = row.fieldId
    form.fieldName = row.fieldName || ''
    form.fieldCron = row.fieldCron || ''
    form.fieldClass = row.fieldClass || ''
    form.fieldStatus = row.fieldStatus || ''
    form.fieldCreateTime = row.fieldCreateTime || ''
    form.params = parseParams(row.fieldParams)
  } catch (error) {
    ElMessage.error(error.message || '加载数据失败')
    goBack()
  }
}

onMounted(() => {
  loadDictOptions()
  initForEdit()
})

/** 加载任务状态文本映射（dict.task_status）；失败时状态回退原值 */
async function loadDictOptions() {
  try {
    STATUS_TEXT.value = await getDictTextMap('task_status')
  } catch {
    STATUS_TEXT.value = {}
  }
}

async function handleSave() {
  if (!form.fieldName || !form.fieldName.trim()) {
    ElMessage.warning('任务名不能为空')
    return
  }
  if (!form.fieldCron || !form.fieldCron.trim()) {
    ElMessage.warning('cron表达式不能为空')
    return
  }
  // 前端基础校验：cron 应为 6 段（秒 分 时 日 月 周）；最终以后端校验为准
  const parts = form.fieldCron.trim().split(/\s+/)
  if (parts.length !== 6) {
    ElMessage.warning('cron表达式格式不正确，应为 6 段（秒 分 时 日 月 周）')
    return
  }
  saving.value = true
  try {
    await updateTask({
      fieldId: form.fieldId,
      fieldName: form.fieldName.trim(),
      fieldCron: form.fieldCron.trim(),
      fieldParams: buildParamsJson(form.params)
    })
    ElMessage.success('保存成功')
    markTabToClose(route.path)
    router.push('/task')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 中止当前任务：停止当前任务实例，任务按「失败」结果记录 */
async function handleStop() {
  try {
    await ElMessageBox.confirm(
      '确定中止当前任务实例？执行中的任务将被中断，并按「失败」结果记录到执行记录。',
      '中止确认',
      { type: 'warning', confirmButtonText: '中止', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  stopping.value = true
  try {
    await stopTask(form.fieldId)
    ElMessage.success('已发送中止指令')
  } catch (error) {
    ElMessage.error(error.message || '中止失败')
  } finally {
    stopping.value = false
  }
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function goBack() {
  router.push('/task')
}
</script>

<script>
export default { name: 'TaskFormView' }
</script>

<style scoped>
.readonly-text {
  color: var(--el-text-color-regular);
  word-break: break-all;
}

/* —— 参数列表值编辑区 —— */
.params-editor {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.params-row {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 720px;
}

.params-key {
  width: 240px;
}

.params-eq {
  color: var(--el-text-color-secondary);
}

.params-value {
  flex: 1;
}

.params-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">数据源详情</span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" @click="goEdit">编辑</el-button>
        </div>
      </div>
    </template>

    <!-- 详情网格：默认一行两字段（字段名:字段值 = 2:3）；长内容字段（is-full）一行一字段（2:8） -->
    <div v-if="detail.row" class="detail-grid">
      <template v-for="(item, index) in detailFields" :key="item.placeholder ? `__placeholder__${index}` : item.label">
        <div v-if="item.placeholder" class="detail-item detail-item-placeholder">
          <span class="detail-label"></span>
          <span class="detail-value"></span>
        </div>
        <div v-else-if="item.target" class="detail-item" :class="{ 'is-full': item.full }">
          <span class="detail-label">{{ item.label }}</span>
          <span class="detail-value">
            <span>{{ item.value }}</span>
            <!-- 目标失效标记（R05）：fieldTarget 不在 targets 编码全集，与列表页同口径 -->
            <el-tag v-if="targetInvalid" type="danger" size="small" class="target-invalid-tag">目标失效</el-tag>
            <div v-if="item.path" class="field-tip">目标接口路径：{{ item.path }}</div>
          </span>
        </div>
        <div v-else class="detail-item" :class="{ 'is-full': item.full }">
          <span class="detail-label">{{ item.label }}</span>
          <span class="detail-value" :class="{ 'detail-tags': !!item.fields }">
            <el-tag v-if="item.tag" :type="item.tagType" size="small">{{ item.value }}</el-tag>
            <!-- 固定条件明细：逐行只读「字段显示名 + 操作符中文 + 值」，运行时变量值为 tag -->
            <div v-else-if="item.conditions" class="condition-list">
              <template v-if="item.conditions.length">
                <div v-for="(cond, condIndex) in item.conditions" :key="condIndex" class="condition-line">
                  <span class="condition-text">{{ conditionFieldLabel(cond.key) }} {{ opLabel(cond.op) }}</span>
                  <el-tag v-if="isRuntimeVar(cond.value)" type="info" size="small">{{ runtimeVarLabel(cond.value) }}</el-tag>
                  <span v-else class="condition-value-text">{{ cond.value }}</span>
                </div>
              </template>
              <span v-else>无固定条件</span>
            </div>
            <!-- 返回字段白名单：字段显示名 tag 列表，空 = 目标能力默认全字段 -->
            <template v-else-if="item.fields && item.fields.length">
              <el-tag v-for="(name, fieldIndex) in item.fields" :key="fieldIndex" size="small">{{ name }}</el-tag>
            </template>
            <template v-else-if="item.fields">目标能力默认全字段</template>
            <template v-else>{{ item.value }}</template>
          </span>
        </div>
      </template>
    </div>
    <el-empty v-else description="加载中或数据不存在" />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { markTabToClose } from '@/utils/tabs'
import { getDatasourceTargets, loadDatasource } from '@/api/datasource'
import { buildDetailGridFields } from '@/utils/detail'

/**
 * 数据源详情页：/datasource/detail/:id（UI 文档 4.10.6），只读查看，
 * 按路由 id 经 loadDatasource 回填（fieldConditions / fieldFields 为后端解析后对象）。
 * 排版对齐 GroupDetailView 基准：detail-grid 默认一行两字段（2:3），
 * 固定条件明细 / 返回字段白名单 / 备注长内容字段独占一行（is-full，2:8），
 * 由 buildDetailGridFields 自动补位防边框断裂，不使用 el-descriptions。
 * 目标失效判定（R05）与列表页同口径：加载时调一次 getDatasourceTargets() 取
 * 编码全集，fieldTarget 不在全集即失效（danger tag「目标失效」）；targets 同时
 * 供固定条件字段显示名与返回字段显示名回查（查不到回退字段名）。
 * 页面只读，不提供启停用/删除入口（均在列表页操作列承载）。
 * 任务编号：F18-FE-004。
 */
const route = useRoute()
const router = useRouter()

/** 运行时变量占位符（literalAllowed=false 字段固定值，对齐 SysDatasourceVar.CURRENT_USER_ID） */
const CURRENT_USER_VAR = '${CURRENT_USER_ID}'

/** 操作符中文展示（对齐 SysDatasourceOp 枚举语义；未收录时回退原文） */
const OP_LABELS = {
  EQ: '等于',
  NE: '不等于',
  LIKE: '包含',
  GT: '大于',
  GE: '大于等于',
  LT: '小于',
  LE: '小于等于',
  IN: '属于'
}

const detail = reactive({ row: null })

/** 目标能力清单（getDatasourceTargets，含各能力 conditionFields / resultFields 定义） */
const targets = ref([])
/** 目标能力编码全集；null = targets 加载失败，不做失效标记（避免误标，对齐列表页 R05 口径） */
const targetCodes = ref(null)

/** 当前数据源命中的目标能力定义（目标失效时为 undefined） */
const currentTarget = computed(() => targets.value.find((item) => item.code === (detail.row && detail.row.fieldTarget)))

/** 失效判定（R05）：fieldTarget 不在目标能力编码全集 */
const targetInvalid = computed(() => {
  const r = detail.row
  return !!r && Array.isArray(targetCodes.value) && !targetCodes.value.includes(r.fieldTarget)
})

/** 目标能力展示：code（name），能力失效（名称缺失）时仅展示编码 */
const targetText = computed(() => {
  const r = detail.row
  if (!r) return '-'
  return r.fieldTargetName ? `${r.fieldTarget}（${r.fieldTargetName}）` : r.fieldTarget || '-'
})

/** 详情字段数组：固定条件明细 / 返回字段白名单 / 备注长内容字段标记 full（一行一字段，2:8） */
const detailFields = computed(() => {
  const r = detail.row
  if (!r) return []
  const fields = [
    { label: '数据源编码', value: r.fieldCode || '-' },
    { label: '数据源名称', value: r.fieldName || '-' },
    // 目标能力：code（name）+ 接口路径辅助说明 + 失效标记（专用分支渲染）
    { label: '目标能力', target: true, value: targetText.value, path: r.fieldTargetPath || '' },
    { label: '启用状态', value: r.fieldEnable ? '启用' : '停用', tag: true, tagType: r.fieldEnable ? 'success' : 'info' },
    { label: '固定条件数', value: conditionCount(r) },
    { label: '返回字段数', value: fieldCountText(r) },
    // 固定条件明细：逐行「字段显示名 + 操作符中文 + 值」，运行时变量值为 tag（独占一行）
    { label: '固定条件明细', conditions: parseConfigArray(r.fieldConditions) || [], full: true },
    // 返回字段白名单：字段显示名列表，空 = 目标能力默认全字段（独占一行）
    { label: '返回字段白名单', fields: resultFieldNames(r), full: true },
    { label: '备注', value: r.fieldRemark || '-', full: true },
    // 底部公共字段：load 仅返回创建/更新人 ID，名称字段（后端未带出时）回退 ID 展示
    { label: '创建人', value: r.fieldCreatorName || r.fieldCreatorId || '-' },
    { label: '创建时间', value: formatTime(r.fieldCreateTime) },
    { label: '更新人', value: r.fieldUpdaterName || r.fieldUpdaterId || '-' },
    { label: '更新时间', value: formatTime(r.fieldUpdateTime) }
  ]
  return buildDetailGridFields(fields)
})

/** 拉取目标能力清单：失效判定与条件/字段显示名回查共用；失败保持空选项（不误标失效） */
async function loadTargets() {
  try {
    const res = await getDatasourceTargets()
    targets.value = (res && res.data) || []
    targetCodes.value = targets.value.map((item) => item.code)
  } catch {
    targets.value = []
    targetCodes.value = null
  }
}

/**
 * 解析配置 JSON 为数组：空配置返回 null；损坏 JSON / 非数组返回 undefined；
 * 正常返回数组。load 响应为解析后对象，此处兜底兼容原文 JSON 字符串形态
 * （计数口径与列表页 conditionCount / fieldCount 一致）。
 */
function parseConfigArray(value) {
  if (value == null || value === '') return null
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return Array.isArray(parsed) ? parsed : undefined
  } catch {
    return undefined
  }
}

/** 固定条件数：空 = 0（无固定条件），解析失败 =「-」 */
function conditionCount(row) {
  const arr = parseConfigArray(row.fieldConditions)
  if (arr == null) return 0
  return arr === undefined ? '-' : arr.length
}

/** 返回字段数：空/NULL/空数组 =「全字段」（目标能力默认全字段），解析失败 =「-」 */
function fieldCountText(row) {
  const arr = parseConfigArray(row.fieldFields)
  if (arr == null) return '全字段'
  if (arr === undefined) return '-'
  return arr.length ? arr.length : '全字段'
}

/** 返回字段白名单展示名：按 key 在目标能力 resultFields 查显示名，查不到回退字段名 */
function resultFieldNames(row) {
  const arr = parseConfigArray(row.fieldFields)
  if (!arr || !arr.length) return []
  return arr.map((key) => {
    const def = currentTarget.value && currentTarget.value.resultFields
    const hit = def && def.find((field) => field.key === key)
    return (hit && hit.label) || key
  })
}

/** 固定条件字段显示名：按 key 在目标能力 conditionFields 查 label，查不到回退字段名 */
function conditionFieldLabel(key) {
  const def = currentTarget.value && currentTarget.value.conditionFields
  const hit = def && def.find((field) => field.key === key)
  return (hit && hit.label) || key
}

function opLabel(op) {
  return OP_LABELS[op] || op
}

/** 运行时变量判定：${...} 占位符形态（如 ${CURRENT_USER_ID}） */
function isRuntimeVar(value) {
  return typeof value === 'string' && value.startsWith('${') && value.endsWith('}')
}

/** 运行时变量展示：当前登录人占位符展示中文语义，其余变量回退原文 */
function runtimeVarLabel(value) {
  return value === CURRENT_USER_VAR ? '当前登录人' : value
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function goBack() {
  markTabToClose(route.path)
  router.push('/datasource')
}

function goEdit() {
  if (detail.row && detail.row.fieldId) {
    router.push(`/datasource/edit/${detail.row.fieldId}`)
  }
}

onMounted(async () => {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少查看对象的 ID')
    goBack()
    return
  }
  loadTargets()
  try {
    const res = await loadDatasource(id)
    const row = res && res.data
    if (!row) {
      ElMessage.warning('未找到该数据源')
      goBack()
      return
    }
    detail.row = row
  } catch (error) {
    ElMessage.error(error.message || '加载详情失败')
    goBack()
  }
})
</script>

<script>
export default { name: 'DatasourceDetailView' }
</script>

<style scoped>
/* 页面容器、详情网格（detail-grid / detail-item / is-full）等公共样式见 styles/form-page.css */

/* 目标能力失效标记与能力文本的间距 */
.target-invalid-tag {
  margin-left: 6px;
}

/* 目标接口路径辅助说明 */
.field-tip {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 固定条件明细逐行展示容器 */
.condition-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 单条固定条件：字段显示名 + 操作符中文 + 值（运行时变量为 tag） */
.condition-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.condition-value-text {
  word-break: break-all;
}

/* 返回字段白名单多值标签容器：flex 换行 + 间距（对齐 GroupDetailView .detail-tags） */
.detail-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
}
</style>

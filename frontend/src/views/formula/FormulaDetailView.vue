<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">公式详情</span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" @click="goEdit">编辑</el-button>
        </div>
      </div>
    </template>

    <!-- 详情网格：默认一行两字段（2:3）；表达式 / 变量依赖明细 / 备注 is-full 一行一字段（2:8），
         六个半格 + 三个整行 + 四个半格均成对，无孤立半格，不使用 el-descriptions -->
    <div v-if="detail.row" class="detail-grid">
      <div class="detail-item">
        <span class="detail-label">公式编码</span>
        <span class="detail-value">{{ detail.row.fieldCode || '-' }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">公式名称</span>
        <span class="detail-value">{{ detail.row.fieldName || '-' }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">公式类型</span>
        <span class="detail-value">{{ detail.row.fieldType || '-' }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">返回类型</span>
        <span class="detail-value">{{ detail.row.fieldReturnType || '-' }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">变量依赖数</span>
        <span class="detail-value">{{ dependencyList.length }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">启用状态</span>
        <span class="detail-value">
          <el-tag :type="detail.row.fieldEnable ? 'success' : 'info'" size="small">
            {{ detail.row.fieldEnable ? '启用' : '停用' }}
          </el-tag>
        </span>
      </div>
      <!-- 表达式：monospace 只读代码块，${} 形态原样展示（存储态即 ${} 形态，方案 A5） -->
      <div class="detail-item is-full">
        <span class="detail-label">表达式</span>
        <span class="detail-value">
          <code class="expression-code">{{ detail.row.fieldExpression || '-' }}</code>
        </span>
      </div>
      <!-- 变量依赖明细：变量码逐项只读 tag；空显示「无变量依赖」 -->
      <div class="detail-item is-full">
        <span class="detail-label">变量依赖明细</span>
        <span class="detail-value" :class="{ 'detail-tags': dependencyList.length }">
          <template v-if="dependencyList.length">
            <el-tag v-for="dep in dependencyList" :key="dep" size="small" class="dep-tag">{{ dep }}</el-tag>
          </template>
          <template v-else>无变量依赖</template>
        </span>
      </div>
      <div class="detail-item is-full">
        <span class="detail-label">备注</span>
        <span class="detail-value">{{ detail.row.fieldRemark || '-' }}</span>
      </div>
      <!-- 底部公共字段：load 仅返回创建/更新人 ID，名称字段（后端未带出时）回退 ID 展示 -->
      <div class="detail-item">
        <span class="detail-label">创建人</span>
        <span class="detail-value">{{ detail.row.fieldCreatorName || detail.row.fieldCreatorId || '-' }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">创建时间</span>
        <span class="detail-value">{{ formatTime(detail.row.fieldCreateTime) }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">更新人</span>
        <span class="detail-value">{{ detail.row.fieldUpdaterName || detail.row.fieldUpdaterId || '-' }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">更新时间</span>
        <span class="detail-value">{{ formatTime(detail.row.fieldUpdateTime) }}</span>
      </div>
    </div>
    <el-empty v-else description="加载中或数据不存在" />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { markTabToClose } from '@/utils/tabs'
import { loadFormula } from '@/api/formula'

/**
 * 公式定义器详情页：/formula/detail/:id（UI 文档 4.11.6），只读查看，
 * 按路由 id 经 loadFormula 回填（fieldDependencies 为后端解析后的变量码数组）。
 * 排版对齐 DatasourceDetailView 基准：detail-grid 默认一行两字段（2:3），
 * 表达式 / 变量依赖明细 / 备注长内容字段独占一行（is-full，2:8），
 * 不使用 el-descriptions。表达式以 monospace 只读代码块原样展示 ${} 形态文本。
 * 页面只读，不提供启停用/删除入口（均在列表页操作列承载）；
 * 【编辑】跳 /formula/edit/:id，【返回】关标签回列表。
 * 任务编号：F14-FE-005。
 */
const route = useRoute()
const router = useRouter()

const detail = reactive({ row: null })

/** 变量依赖明细：load 响应为解析后数组；兜底兼容原文 JSON 字符串形态，空 = [] */
const dependencyList = computed(() => parseDependencies(detail.row && detail.row.fieldDependencies))

function parseDependencies(value) {
  if (value == null || value === '') return []
  if (Array.isArray(value)) return value.map(String)
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed.map(String) : []
    } catch {
      return []
    }
  }
  return []
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
  router.push('/formula')
}

function goEdit() {
  if (detail.row && detail.row.fieldId) {
    router.push(`/formula/edit/${detail.row.fieldId}`)
  }
}

onMounted(async () => {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少查看对象的 ID')
    goBack()
    return
  }
  try {
    const res = await loadFormula(id)
    const row = res && res.data
    if (!row) {
      ElMessage.warning('未找到该公式')
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
export default { name: 'FormulaDetailView' }
</script>

<style scoped>
/* 页面容器、详情网格（detail-grid / detail-item / is-full）等公共样式见 styles/form-page.css */

/* 表达式 monospace 只读代码块：浅底描边，${} 形态原文换行展示 */
.expression-code {
  display: block;
  padding: 6px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background-color: var(--el-fill-color-light);
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  word-break: break-all;
}

/* 变量依赖多值标签容器：flex 换行 + 间距（对齐 DatasourceDetailView .detail-tags） */
.detail-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.dep-tag {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}
</style>

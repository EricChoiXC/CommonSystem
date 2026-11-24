<template>
  <!--
    内容型只读表格（F15-FE-007，§1.3 table 行：columns[{label, prop, width}] 列定义 +
    rows 静态数据数组；不进数据载荷）。el-table 只读渲染（静态 rows，非分页）；
    design 形态同展示，未配置列时（经 disabled 控制位识别）展示占位便于画布识别。
  -->
  <div v-if="hasColumns" class="table-widget">
    <el-table :data="tableRows" border size="default">
      <el-table-column
        v-for="column in tableColumns"
        :key="columnKey(column)"
        :prop="column.prop"
        :label="column.label"
        :width="columnWidth(column)"
      />
    </el-table>
  </div>
  <div v-else class="table-widget-placeholder">表格（未配置列定义）</div>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 表格 widget（type=table，功能详细设计 §1.3 内容类 table 行）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明——内容类不承载数据、无数据绑定语义，保留声明
 *   对齐统一契约、不触发回写）+ 扩展属性项 columns/rows + 统一控制位 disabled/readonly
 *   （内容型只读表格，无交互时机，控制位仅契约保留；design 形态经 disabled 识别展示
 *   未配置占位）；
 * - emits：update:modelValue / change / focus / blur（无交互时机，保留声明、不触发）；
 * - 值形态：不进数据载荷（物料 dataType=null，§1.3）；rows 为静态展示数据，编辑过程
 *   中不产生任何值回写；
 * - rows 脏形态容错：属性面板 JSON 文本域配置（UI 分册 §4.5.2），容忍字符串形态
 *   （JSON 合法性预检前的中间态），不可解析按空数组展示；
 * - 固定属性项由渲染器层承载，本组件只渲染表格本体，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值（内容类不承载数据，保留声明对齐统一契约） */
  modelValue: { type: null, default: null },
  /** 列定义 [{ label, prop, width }] */
  columns: { type: Array, default: () => [] },
  /** 静态数据数组（只读展示，非分页） */
  rows: { type: Array, default: () => [] },
  /** 统一控制位：禁用（design 形态经此识别，未配置列时展示占位） */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（内容型只读表格，契约保留） */
  readonly: { type: Boolean, default: false },
})

// 统一契约四事件保留声明；内容型只读表格无交互时机，不触发
const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])
void emit

const tableColumns = computed(() => (Array.isArray(props.columns) ? props.columns.filter((column) => column && typeof column === 'object') : []))

const hasColumns = computed(() => tableColumns.value.length > 0)

/** 静态数据：数组直用；字符串形态（JSON 预检中间态）容错解析；其余按空数组 */
const tableRows = computed(() => {
  if (Array.isArray(props.rows)) return props.rows
  if (typeof props.rows === 'string' && props.rows.trim() !== '') {
    try {
      const parsed = JSON.parse(props.rows)
      return Array.isArray(parsed) ? parsed : []
    } catch (error) {
      return []
    }
  }
  return []
})

function columnKey(column) {
  return `${column.prop || ''}#${column.label || ''}`
}

/** 列宽：width 配置为权威像素值；未配置自适应（el-table 均分） */
function columnWidth(column) {
  const width = column.width
  if (width === null || width === undefined || width === '') return undefined
  const num = Number(width)
  return Number.isFinite(num) && num > 0 ? num : undefined
}
</script>

<style scoped>
.table-widget {
  width: 100%;
}

.table-widget-placeholder {
  padding: 12px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
</style>

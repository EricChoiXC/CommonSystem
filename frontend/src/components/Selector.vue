<template>
  <div class="selector">
    <!-- 触发入口：只读展示当前选中值，点击打开弹窗 -->
    <el-input
      :model-value="fieldName"
      placeholder="请选择"
      readonly
      clearable
      @clear="handleClear"
      @click="openDialog"
    >
      <template #append>
        <el-button @click.stop="openDialog">选择</el-button>
      </template>
    </el-input>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="720px"
      append-to-body
      destroy-on-close
    >
      <!-- 筛选区：按 filter 配置生成筛选项 -->
      <el-form inline class="sel-filter">
        <el-form-item v-for="item in filter" :key="item.name" :label="item.text">
          <el-input
            v-model="queryForm[item.name]"
            :placeholder="'请输入' + item.text"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表区：按 show 配置渲染列 -->
      <el-table
        :data="list"
        v-loading="loading"
        height="360"
        :row-class-name="rowClassName"
        @row-click="handleRowClick"
        @selection-change="handleSelectionChange"
      >
        <el-table-column v-if="multi" type="selection" width="45" />
        <el-table-column
          v-for="col in show"
          :key="col.name"
          :prop="col.name"
          :label="col.text"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="loadList"
        @size-change="loadList"
      />

      <!-- 已选项展示 -->
      <div class="sel-selected">
        <span class="sel-selected-label">已选：</span>
        <el-tag
          v-for="item in selected"
          :key="item.fieldId"
          closable
          @close="removeSelected(item)"
        >
          {{ item.fieldName }}
        </el-tag>
        <span v-if="!selected.length" class="sel-selected-empty">未选择</span>
      </div>

      <template #footer>
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/index'

/**
 * 选择器组件：提供对数据库来源数据的选择
 * 通过 url 调用列表查询接口（POST，请求体遵循 BaseRequest：{ body, pages }），
 * filter 定义可筛选项，show 定义列表显示项，确认后回调。
 */
const props = defineProps({
  /** 关联的 id 值字段，必填 */
  fieldId: { type: String, default: '' },
  /** 关联的名称值字段，必填 */
  fieldName: { type: String, default: '' },
  /** 请求的列表查询接口 url，必填 */
  url: { type: String, required: true },
  /** 可筛选项，对象数组 { text: 查询的中文名, name: 查询的字段名称 } */
  filter: {
    type: Array,
    default: () => [{ text: '名称', name: 'fieldName' }]
  },
  /** 列表显示项，对象数组 { text: 显示的中文名, name: 显示的字段名称 } */
  show: {
    type: Array,
    default: () => [{ text: '名称', name: 'fieldName' }]
  },
  /** 是否多选，默认 false */
  multi: { type: Boolean, default: false },
  /** 多选的分隔符，默认 ';'，要求仅限符号 */
  septarator: { type: String, default: ';' },
  /** 确认选择后的回调函数 callback(fieldId, fieldName, selectedList) */
  callback: { type: Function, default: null }
})
const emit = defineEmits(['update:fieldId', 'update:fieldName'])

const dialogVisible = ref(false)
const dialogTitle = computed(() => (props.multi ? '选择（可多选）' : '选择'))

const loading = ref(false)
const list = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const queryForm = reactive({})
props.filter.forEach((item) => {
  queryForm[item.name] = ''
})

const selected = ref([])
const selectedBackup = ref([])

/** 按分隔符将 fieldId / fieldName 拆分为已选项数组，用于弹窗打开时回显 */
function splitSelected(fieldId, fieldName) {
  if (!fieldId) {
    return []
  }
  const ids = String(fieldId).split(props.septarator)
  const names = String(fieldName || '').split(props.septarator)
  return ids.map((id, index) => ({
    fieldId: id,
    fieldName: names[index] || id
  }))
}

function openDialog() {
  selected.value = splitSelected(props.fieldId, props.fieldName)
  selectedBackup.value = selected.value.map((item) => ({ ...item }))
  dialogVisible.value = true
  page.value = 1
  loadList()
}

function resetQueryForm() {
  props.filter.forEach((item) => {
    queryForm[item.name] = ''
  })
}

async function loadList() {
  loading.value = true
  try {
    const res = await request.post(props.url, {
      body: { ...queryForm },
      pages: { pageNo: page.value, pageSize: pageSize.value, needPage: true }
    })
    list.value = res.data || []
    // 后端未返回 total 时以当前页条数兜底
    total.value = (res.metadata && res.metadata.total) || list.value.length
  } catch (error) {
    ElMessage.error(error.message || '查询失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadList()
}

function handleReset() {
  resetQueryForm()
  page.value = 1
  loadList()
}

/** 单选：点击行选中；多选：走 checkbox 勾选 */
function handleRowClick(row) {
  if (props.multi) {
    return
  }
  selected.value = [row]
}

function handleSelectionChange(rows) {
  if (!props.multi) {
    return
  }
  selected.value = rows
}

/** 单选时高亮已选行 */
function rowClassName({ row }) {
  if (!props.multi && selected.value.length && selected.value[0].fieldId === row.fieldId) {
    return 'sel-current-row'
  }
  return ''
}

function removeSelected(item) {
  selected.value = selected.value.filter((s) => s.fieldId !== item.fieldId)
}

function handleConfirm() {
  const ids = selected.value.map((s) => s.fieldId).join(props.septarator)
  const names = selected.value.map((s) => s.fieldName).join(props.septarator)
  emit('update:fieldId', ids)
  emit('update:fieldName', names)
  if (typeof props.callback === 'function') {
    props.callback(ids, names, selected.value.map((s) => ({ ...s })))
  }
  dialogVisible.value = false
}

function handleCancel() {
  selected.value = selectedBackup.value.map((item) => ({ ...item }))
  dialogVisible.value = false
}

function handleClear() {
  emit('update:fieldId', '')
  emit('update:fieldName', '')
}
</script>

<style scoped>
.selector {
  width: 100%;
}

.sel-filter {
  margin-bottom: 4px;
}

.sel-selected {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 32px;
  margin-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 8px;
}

.sel-selected-label {
  color: var(--el-text-color-secondary);
}

.sel-selected-empty {
  color: var(--el-text-color-placeholder);
}

:deep(.sel-current-row) {
  --el-table-tr-bg-color: var(--el-color-primary-light-9);
}
</style>

<template>
  <div class="nrc-composer" :class="{ 'is-disabled': disabled }">
    <!-- 上区：插入项 -->
    <div class="nrc-zone nrc-zone-top">
      <span class="nrc-zone-label">插入项</span>
      <div class="nrc-insert-btns">
        <el-button
          v-for="t in INSERT_TYPES"
          :key="t.type"
          size="small"
          :disabled="disabled"
          @click="insertItem(t.type)"
        >
          {{ t.label }}
        </el-button>
      </div>
    </div>

    <!-- 中区：已插入项列表 -->
    <div class="nrc-zone nrc-zone-middle">
      <span class="nrc-zone-label">已插入项</span>
      <div v-if="!items.length" class="nrc-empty">暂无规则项，请先在上方插入</div>
      <div v-else class="nrc-item-list">
        <div
          v-for="(item, index) in items"
          :key="item.id"
          class="nrc-item"
          :class="{ 'is-active': item.id === selectedId }"
          @click="selectItem(item.id)"
        >
          <span class="nrc-item-index">{{ index + 1 }}</span>
          <el-tag :type="typeMeta(item.type).tag" size="small" disable-transitions>
            {{ typeMeta(item.type).label }}
          </el-tag>
          <span class="nrc-item-name">{{ item.name || typeMeta(item.type).label }}</span>
          <span class="nrc-item-summary">{{ itemSummary(item) }}</span>
          <span class="nrc-item-btns" @click.stop>
            <el-button
              link
              size="small"
              :disabled="disabled || index === 0"
              :aria-label="'左移第' + (index + 1) + '项'"
              @click="moveItem(index, -1)"
            >←</el-button>
            <el-button
              link
              size="small"
              :disabled="disabled || index === items.length - 1"
              :aria-label="'右移第' + (index + 1) + '项'"
              @click="moveItem(index, 1)"
            >→</el-button>
            <el-button link size="small" type="danger" :disabled="disabled" @click="removeItem(index)">删除</el-button>
          </span>
        </div>
      </div>
    </div>

    <!-- 下区：选中项属性面板（id 为隐藏属性，不渲染任何编辑入口） -->
    <div class="nrc-zone nrc-zone-bottom">
      <span class="nrc-zone-label">属性设置</span>
      <div v-if="!selectedItem" class="nrc-empty">请先插入规则项</div>
      <el-form v-else class="nrc-props" label-width="100px" :disabled="disabled" @submit.prevent>
        <el-form-item label="名称">
          <el-input
            :model-value="selectedItem.name"
            :name="inputName('name')"
            :aria-label="inputName('name')"
            maxlength="32"
            placeholder="请输入展示名称"
            @update:model-value="updateSelected('name', $event)"
          />
        </el-form-item>

        <!-- text：常量内容 -->
        <el-form-item v-if="selectedItem.type === 'text'" label="常量内容">
          <el-input
            :model-value="selectedItem.text"
            :name="inputName('text')"
            :aria-label="inputName('text')"
            maxlength="32"
            show-word-limit
            placeholder="请输入常量内容"
            @update:model-value="updateSelected('text', $event)"
          />
        </el-form-item>

        <!-- datetime：格式（白名单 + 受限自定义，服务端最终校验） -->
        <el-form-item v-else-if="selectedItem.type === 'datetime'" label="格式">
          <div class="nrc-format">
            <el-select
              :model-value="selectedItem.format"
              filterable
              allow-create
              default-first-option
              :aria-label="inputName('format')"
              placeholder="选择或输入日期格式"
              @update:model-value="updateSelected('format', $event)"
            >
              <el-option v-for="f in DATETIME_FORMAT_OPTIONS" :key="f" :label="f" :value="f" />
            </el-select>
            <p class="nrc-hint">自定义仅允许 y M d H m s 与 - / : 空格 组合，服务端最终校验</p>
          </div>
        </el-form-item>

        <!-- seq：起始值 / 长度 / 补零 / 重置周期 -->
        <template v-else-if="selectedItem.type === 'seq'">
          <el-form-item label="起始值">
            <el-input-number
              :model-value="toNum(selectedItem.start, 0)"
              :min="0"
              :step="1"
              step-strictly
              :aria-label="inputName('start')"
              @update:model-value="updateSelectedNumber('start', $event)"
            />
          </el-form-item>
          <el-form-item label="长度">
            <el-input-number
              :model-value="toNum(selectedItem.length, 3)"
              :min="1"
              :max="9"
              :step="1"
              step-strictly
              :aria-label="inputName('length')"
              @update:model-value="updateSelectedNumber('length', $event)"
            />
          </el-form-item>
          <el-form-item label="补零">
            <el-switch
              :model-value="selectedItem.padZero"
              :aria-label="inputName('padZero')"
              @update:model-value="updateSelected('padZero', $event)"
            />
          </el-form-item>
          <el-form-item label="重置周期">
            <el-select
              :model-value="selectedItem.resetPeriod || 'NONE'"
              :aria-label="inputName('resetPeriod')"
              @update:model-value="updateSelected('resetPeriod', $event)"
            >
              <el-option
                v-for="opt in RESET_PERIOD_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
        </template>

        <!-- random：仅数字；numericOnly=false 时 min/max 置灰（仅 length 生效） -->
        <template v-else-if="selectedItem.type === 'random'">
          <el-form-item label="仅数字">
            <el-switch
              :model-value="selectedItem.numericOnly"
              :aria-label="inputName('numericOnly')"
              @update:model-value="updateSelected('numericOnly', $event)"
            />
          </el-form-item>
          <el-form-item label="最小值">
            <el-input-number
              :model-value="toNum(selectedItem.min, 0)"
              :min="0"
              :max="maxBound(selectedItem)"
              :disabled="!selectedItem.numericOnly"
              :step="1"
              step-strictly
              :aria-label="inputName('min')"
              @update:model-value="updateSelectedNumber('min', $event)"
            />
          </el-form-item>
          <el-form-item label="最大值">
            <el-input-number
              :model-value="toNum(selectedItem.max, 9999)"
              :min="minBound(selectedItem)"
              :disabled="!selectedItem.numericOnly"
              :step="1"
              step-strictly
              :aria-label="inputName('max')"
              @update:model-value="updateSelectedNumber('max', $event)"
            />
          </el-form-item>
          <el-form-item label="长度">
            <el-input-number
              :model-value="toNum(selectedItem.length, 4)"
              :min="1"
              :max="9"
              :step="1"
              step-strictly
              :aria-label="inputName('length')"
              @update:model-value="updateSelectedNumber('length', $event)"
            />
          </el-form-item>
          <el-form-item label="补零">
            <el-switch
              :model-value="selectedItem.padZero"
              :aria-label="inputName('padZero')"
              @update:model-value="updateSelected('padZero', $event)"
            />
          </el-form-item>
        </template>
      </el-form>
    </div>

    <!-- 实时预览：预览引擎为后端 preview/render，前端不自算 -->
    <div class="nrc-zone nrc-zone-preview">
      <span class="nrc-zone-label">实时预览</span>
      <template v-if="items.length">
        <div v-if="previewState === 'loading'" class="nrc-preview-tip">正在获取预览样例…</div>
        <div v-else-if="previewState === 'failed'" class="nrc-preview-tip">预览获取失败，请稍后重试（不影响编辑）</div>
        <template v-else>
          <ul v-if="previewSamples.length" class="nrc-samples">
            <li v-for="(sample, i) in previewSamples" :key="i" class="nrc-sample">{{ sample }}</li>
          </ul>
          <div v-else-if="!previewErrors.length" class="nrc-preview-tip">暂无预览样例</div>
          <ul v-if="previewErrors.length" class="nrc-errors">
            <li v-for="(err, i) in previewErrors" :key="i" class="nrc-error">{{ errorText(err) }}</li>
          </ul>
        </template>
      </template>
      <div v-else class="nrc-preview-tip">插入规则项后自动生成 3 个样例号</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { renderNumberPreview } from '@/api/number'

/**
 * 编号规则编排组件（NumberRuleComposer）
 *
 * 受控复用组件：不内部持有 value 作为数据源，所有修改基于 props.value 深拷贝生成
 * 新对象后经 onChange 回传宿主；value 协议为冻结契约（方案 §2.1 的 JSON 对象
 * { items: [...] }），value 为 null / undefined 时按 { items: [] } 处理。
 *
 * seq 项 id 锁定（D5）：已保存规则编辑态由宿主保证不重生成 seq id；本组件不提供
 * id 编辑入口——任何逻辑都不会修改已有 item.id，仅新插入项生成新 id
 * （ci- 前缀 + 12 位十六进制小写）。
 *
 * 实时预览：深度 watch value，防抖 500ms 调 preview/render 展示 3 个样例号；
 * 校验失败（C1~C5）就地红字提示；预览引擎为后端，前端不自算。
 */
const props = defineProps({
  /** 规则配置对象（§2.1 value 协议 { items: [...] }），传 null / undefined 视为空规则 */
  value: { type: Object, default: null },
  /** 值变更回调：组件基于当前 value 深拷贝生成新对象后回传 onChange(newValue) */
  onChange: { type: Function, default: null },
  /** 宿主表单字段名（编号编辑页传 "fieldRule"），仅用于组件内部 aria / 表单语义提示 */
  fieldName: { type: String, default: '' },
  /** 是否禁用（置灰插入、排序、删除与属性编辑） */
  disabled: { type: Boolean, default: false }
})

/** 可插入项类型（上区按钮顺序） */
const INSERT_TYPES = [
  { type: 'text', label: '文本' },
  { type: 'seq', label: '流水号' },
  { type: 'datetime', label: '日期时间' },
  { type: 'random', label: '随机数' }
]

/** 类型展示元信息：中区 el-tag 颜色与名称 */
const TYPE_META = {
  text: { label: '文本', tag: 'info' },
  seq: { label: '流水号', tag: 'success' },
  datetime: { label: '日期时间', tag: 'warning' },
  random: { label: '随机数', tag: 'danger' }
}

/** 各类型新项默认值（每次插入生成全新对象） */
const DEFAULT_FACTORIES = {
  text: () => ({ type: 'text', name: '文本', text: '' }),
  seq: () => ({ type: 'seq', name: '流水号', start: 1, length: 3, padZero: true, resetPeriod: 'NONE' }),
  datetime: () => ({ type: 'datetime', name: '日期时间', format: 'yyyyMMdd' }),
  random: () => ({ type: 'random', name: '随机码', min: 0, max: 9999, length: 4, padZero: true, numericOnly: true })
}

/** 日期格式白名单（§2.2，自定义经 allow-create 输入，服务端最终校验） */
const DATETIME_FORMAT_OPTIONS = ['yyyy-MM-dd', 'yyyyMMdd', 'MMdd', 'HHmmss', 'yyyyMMddHHmmss', 'yyyy-MM-dd HH:mm:ss']

/** seq 重置周期选项 */
const RESET_PERIOD_OPTIONS = [
  { value: 'NONE', label: '不重置' },
  { value: 'DAY', label: '按天' },
  { value: 'WEEK', label: '按周' },
  { value: 'MONTH', label: '按月' },
  { value: 'YEAR', label: '按年' }
]

const PREVIEW_DEBOUNCE_MS = 500

/** 当前规则项列表（只读渲染视图，修改一律经 emitChange 深拷贝回传） */
const items = computed(() => {
  const value = props.value
  return value && typeof value === 'object' && Array.isArray(value.items) ? value.items : []
})

/** 选中项 id（组件内部交互态，不属于 value 协议） */
const selectedId = ref('')

const selectedItem = computed(() => items.value.find((item) => item && item.id === selectedId.value) || null)

/* ==================== 值修改（受控模式） ==================== */

/** 深拷贝当前 value 为纯 JSON 新对象，保证 items 为数组（value 空时按 { items: [] } 处理） */
function deepCopyValue(value) {
  const copy = value && typeof value === 'object' ? JSON.parse(JSON.stringify(value)) : {}
  if (!Array.isArray(copy.items)) {
    copy.items = []
  }
  return copy
}

/** 受控变更统一入口：深拷贝 -> 变更 -> 回传宿主；disabled 或未传回调时不动作 */
function emitChange(mutate) {
  if (props.disabled || typeof props.onChange !== 'function') {
    return
  }
  const next = deepCopyValue(props.value)
  mutate(next)
  props.onChange(next)
}

/** 生成新项 id：ci- 前缀 + 12 位十六进制小写（新插入项专用，避免与既有 id 重复） */
function genItemId(existingItems) {
  const used = new Set((existingItems || []).map((item) => item && item.id))
  let id = ''
  do {
    id = 'ci-' + randomHex12()
  } while (used.has(id))
  return id
}

function randomHex12() {
  const bytes = new Uint8Array(6)
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(bytes)
  } else {
    for (let i = 0; i < bytes.length; i++) {
      bytes[i] = Math.floor(Math.random() * 256)
    }
  }
  let hex = ''
  for (let i = 0; i < bytes.length; i++) {
    hex += bytes[i].toString(16).padStart(2, '0')
  }
  return hex
}

/** 上区：在 items 末尾追加新项（仅新插入项生成 id）并自动选中 */
function insertItem(type) {
  if (!DEFAULT_FACTORIES[type]) {
    return
  }
  emitChange((next) => {
    const item = Object.assign({ id: genItemId(next.items) }, DEFAULT_FACTORIES[type]())
    next.items.push(item)
    selectedId.value = item.id
  })
}

/** 中区：选中规则项（仅交互态，不改 value） */
function selectItem(id) {
  if (props.disabled) {
    return
  }
  selectedId.value = id
}

/** 中区：左移 / 右移（按 index 交换位置，item.id 不变） */
function moveItem(index, delta) {
  emitChange((next) => {
    const target = index + delta
    if (target < 0 || target >= next.items.length) {
      return
    }
    const moved = next.items.splice(index, 1)[0]
    next.items.splice(target, 0, moved)
  })
}

/** 中区：删除规则项；删除选中项时清空选中态 */
function removeItem(index) {
  const removed = items.value[index]
  emitChange((next) => {
    next.items.splice(index, 1)
  })
  if (removed && removed.id === selectedId.value) {
    selectedId.value = ''
  }
}

/** 下区：修改选中项属性（按 id 定位，回传新 value） */
function updateSelected(prop, val) {
  const id = selectedId.value
  if (!id) {
    return
  }
  emitChange((next) => {
    const target = next.items.find((item) => item && item.id === id)
    if (target) {
      target[prop] = val
    }
  })
}

/** 数值属性修改：忽略 el-input-number 清空产生的 null（失焦后由 min/max 钳制回显） */
function updateSelectedNumber(prop, val) {
  if (val === null || val === undefined || val === '') {
    return
  }
  updateSelected(prop, val)
}

/* ==================== 展示辅助 ==================== */

function typeMeta(type) {
  return TYPE_META[type] || { label: type || '未知', tag: 'info' }
}

function toNum(val, fallback) {
  const n = Number(val)
  return Number.isFinite(n) ? n : fallback
}

/** random 最小值上界联动：≤ 最大值（服务端最终校验） */
function maxBound(item) {
  return toNum(item.max, 999999999)
}

/** random 最大值下界联动：≥ 最小值（服务端最终校验） */
function minBound(item) {
  return toNum(item.min, 0)
}

/** seq 容量：10^length - 1（length 越界按 1..9 钳制） */
function seqCapacity(length) {
  const len = Math.min(Math.max(Math.round(toNum(length, 1)), 1), 9)
  return Math.pow(10, len) - 1
}

/** 中区关键属性摘要 */
function itemSummary(item) {
  if (!item) {
    return ''
  }
  if (item.type === 'text') {
    const text = item.text === undefined || item.text === null || item.text === '' ? '（空）' : item.text
    return '内容：' + text
  }
  if (item.type === 'datetime') {
    return '格式：' + (item.format || '（未设置）')
  }
  if (item.type === 'seq') {
    return toNum(item.start, 0) + '~' + seqCapacity(item.length)
  }
  if (item.type === 'random') {
    if (item.numericOnly) {
      return toNum(item.min, 0) + '-' + toNum(item.max, 9999)
    }
    return '长度 ' + toNum(item.length, 0) + '（字符 A-Za-z0-9）'
  }
  return ''
}

/** 表单语义提示名：宿主字段名.属性名（如 fieldRule.start） */
function inputName(prop) {
  return props.fieldName ? props.fieldName + '.' + prop : prop
}

/** 预览错误文案：规则号 + 中文信息 + itemId 定位到第几项 */
function errorText(err) {
  let text = (err && err.code ? '[' + err.code + '] ' : '') + (err && err.message ? err.message : '')
  if (err && err.itemId) {
    const index = items.value.findIndex((item) => item && item.id === err.itemId)
    if (index >= 0) {
      const name = items.value[index].name
      text += '（第 ' + (index + 1) + ' 项' + (name ? ' ' + name : '') + '）'
    }
  }
  return text
}

/* ==================== 实时预览（后端 preview/render） ==================== */

const previewSamples = ref([])
const previewErrors = ref([])
const previewState = ref('idle')

/** 防抖定时器与请求序号：仅采纳最后一次请求结果，卸载时清理 */
let debounceTimer = null
let requestSeq = 0

watch(
  () => props.value,
  () => {
    reconcileSelection()
    schedulePreview()
  },
  { deep: true, immediate: true }
)

/** 选中项被外部变更（如宿主重新加载）后不存在时清空选中态 */
function reconcileSelection() {
  if (selectedId.value && !items.value.some((item) => item && item.id === selectedId.value)) {
    selectedId.value = ''
  }
}

/** items 为空时清空预览区且不调接口；否则防抖触发预览 */
function schedulePreview() {
  if (!items.value.length) {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
    requestSeq++
    previewSamples.value = []
    previewErrors.value = []
    previewState.value = 'idle'
    return
  }
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  debounceTimer = setTimeout(runPreview, PREVIEW_DEBOUNCE_MS)
}

async function runPreview() {
  debounceTimer = null
  const seq = ++requestSeq
  previewState.value = 'loading'
  try {
    const res = await renderNumberPreview({ rule: deepCopyValue(props.value), count: 3 })
    if (seq !== requestSeq) {
      return
    }
    const data = res && res.data ? res.data : {}
    previewSamples.value = Array.isArray(data.samples) ? data.samples : []
    previewErrors.value = Array.isArray(data.errors) ? data.errors : []
    previewState.value = 'done'
  } catch (e) {
    if (seq !== requestSeq) {
      return
    }
    // 预览失败（网络 / 登录）静默降级为提示文案，不弹消息、不阻塞编辑
    previewSamples.value = []
    previewErrors.value = []
    previewState.value = 'failed'
  }
}

onBeforeUnmount(() => {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
  requestSeq++
})
</script>

<script>
export default { name: 'NumberRuleComposer' }
</script>

<style scoped>
.nrc-composer {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
  overflow: hidden;
}

.nrc-composer.is-disabled {
  opacity: 0.7;
}

.nrc-zone {
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.nrc-zone:last-child {
  border-bottom: none;
}

.nrc-zone-label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

/* 上区：插入项按钮行 */
.nrc-zone-top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nrc-zone-top .nrc-zone-label {
  margin-bottom: 0;
  flex-shrink: 0;
}

.nrc-insert-btns {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nrc-insert-btns .el-button + .el-button {
  margin-left: 0;
}

/* 中区：已插入项列表 */
.nrc-item-list {
  max-height: 220px;
  overflow-y: auto;
}

.nrc-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  margin-bottom: 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  cursor: pointer;
}

.nrc-item:last-child {
  margin-bottom: 0;
}

.nrc-item:hover {
  border-color: var(--el-color-primary-light-5);
}

.nrc-item.is-active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.nrc-item-index {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  border-radius: 50%;
  background: var(--el-fill-color);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.nrc-item-name {
  flex-shrink: 0;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.nrc-item-summary {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.nrc-item-btns {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.nrc-item-btns .el-button + .el-button {
  margin-left: 8px;
}

.nrc-empty {
  padding: 6px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* 下区：属性面板 */
.nrc-props :deep(.el-select) {
  width: 240px;
}

.nrc-props :deep(.el-input) {
  max-width: 320px;
}

.nrc-props :deep(.el-input-number) {
  width: 160px;
}

.nrc-format {
  width: 100%;
}

.nrc-hint {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

/* 预览区：样例号等宽字体，校验错误就地红字 */
.nrc-samples {
  list-style: none;
  margin: 0;
  padding: 0;
}

.nrc-sample {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.9;
  color: var(--el-text-color-primary);
}

.nrc-errors {
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
}

.nrc-error {
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-color-danger);
}

.nrc-preview-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

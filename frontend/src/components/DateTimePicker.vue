<template>
  <!-- 年份 -->
  <el-date-picker
    v-if="calendarType === 'year'"
    v-model="model"
    type="year"
    :format="dateFormat || 'YYYY'"
    :value-format="dateFormat || 'YYYY'"
    :name="fieldName"
    :placeholder="singlePlaceholder('年份')"
    :disabled-date="disabledDate"
    @change="handleChange"
  />
  <!-- 年月 -->
  <el-date-picker
    v-else-if="calendarType === 'month'"
    v-model="model"
    type="month"
    :format="dateFormat || 'YYYY-MM'"
    :value-format="dateFormat || 'YYYY-MM'"
    :name="fieldName"
    :placeholder="singlePlaceholder('月份')"
    :disabled-date="disabledDate"
    @change="handleChange"
  />
  <!-- 周 -->
  <el-date-picker
    v-else-if="calendarType === 'week'"
    v-model="model"
    type="week"
    :format="weekFormat"
    :value-format="dateFormat || 'YYYY-MM-DD'"
    :name="fieldName"
    :placeholder="singlePlaceholder('周')"
    :disabled-date="disabledDate"
    @change="handleChange"
  />
  <!-- 日期 -->
  <el-date-picker
    v-else-if="calendarType === 'date'"
    v-model="model"
    type="date"
    :format="dateFormat"
    :value-format="dateFormat"
    :name="fieldName"
    :placeholder="singlePlaceholder('日期')"
    :disabled-date="disabledDate"
    @change="handleChange"
  />
  <!-- 日期范围 -->
  <el-date-picker
    v-else-if="calendarType === 'daterange'"
    v-model="model"
    type="daterange"
    :format="dateFormat || 'YYYY-MM-DD'"
    :value-format="dateFormat || 'YYYY-MM-DD'"
    :start-placeholder="startPlaceholder"
    :end-placeholder="endPlaceholder"
    :range-separator="rangeSeparator"
    :disabled-date="disabledDate"
    @change="handleChange"
  />
  <!-- 日期时间范围 -->
  <el-date-picker
    v-else-if="calendarType === 'datetimerange'"
    v-model="model"
    type="datetimerange"
    :format="datetimeFormat || 'YYYY-MM-DD HH:mm:ss'"
    :value-format="datetimeFormat || 'YYYY-MM-DD HH:mm:ss'"
    :start-placeholder="startPlaceholder"
    :end-placeholder="endPlaceholder"
    :range-separator="rangeSeparator"
    :disabled-date="disabledDate"
    @change="handleChange"
  />
  <!-- 时间 -->
  <el-time-picker
    v-else-if="calendarType === 'time'"
    v-model="model"
    :format="timeFormat"
    :value-format="timeFormat"
    :name="fieldName"
    :placeholder="singlePlaceholder('时间')"
    :disabled-hours="disabledHours"
    :disabled-minutes="disabledMinutes"
    :disabled-seconds="disabledSeconds"
    @change="handleChange"
  />
  <!-- 日期时间（默认） -->
  <el-date-picker
    v-else
    v-model="model"
    type="datetime"
    :format="datetimeFormat"
    :value-format="datetimeFormat"
    :name="fieldName"
    :placeholder="singlePlaceholder('日期时间')"
    :disabled-date="disabledDate"
    @change="handleChange"
  />
</template>

<script setup>
import { computed } from 'vue'

/**
 * 日期时间选择器：提供日期、时间、日期时间等类型的选择
 * 通过 calendarType 切换选择器类型，支持年份/月份/小时/分钟/星期范围限制
 * （范围参数支持 'a-b' 区间或 [a,b,c] 列表两种写法，星期约定 1 为星期一、7 为星期日）。
 */
const props = defineProps({
  /** 选择器关联的字段，用于 name 属性与占位提示（单值类型） */
  fieldName: { type: String, default: '' },
  /** 单值类型占位提示覆盖，不传时按 fieldName 生成「请选择 + fieldName」 */
  placeholder: { type: String, default: '' },
  /** 范围类型起始占位提示，默认「开始时间」 */
  startPlaceholder: { type: String, default: '开始时间' },
  /** 范围类型结束占位提示，默认「结束时间」 */
  endPlaceholder: { type: String, default: '结束时间' },
  /** 范围类型分隔符，默认「至」 */
  rangeSeparator: { type: String, default: '至' },
  /** 选择器类型，默认 datetime：year / month / week / date / daterange / time / datetime / datetimerange */
  calendarType: {
    type: String,
    default: 'datetime',
    validator: (value) =>
      ['year', 'month', 'week', 'date', 'daterange', 'time', 'datetime', 'datetimerange'].includes(value)
  },
  /** 日期格式，默认 YYYY-MM-DD（dayjs token，年/月/周/日期/日期范围使用） */
  dateFormat: { type: String, default: 'YYYY-MM-DD' },
  /** 时间格式，默认 HH:mm:ss */
  timeFormat: { type: String, default: 'HH:mm:ss' },
  /** 日期时间格式，默认 YYYY-MM-DD HH:mm:ss */
  datetimeFormat: { type: String, default: 'YYYY-MM-DD HH:mm:ss' },
  /** 可选年份范围或年份列表，如 '2024-2030'、[2024, 2025, 2026] */
  yearRange: { type: [String, Array], default: '' },
  /** 可选月份范围或月份列表，如 '1-12'、[1, 3, 5, 7, 9, 11] */
  monthRange: { type: [String, Array], default: '' },
  /** 可选小时范围或小时列表，如 '6-18'、[8, 9, 10, 14, 15, 16] */
  hourRange: { type: [String, Array], default: '' },
  /** 可选分钟范围或分钟列表，如 '0-59'、[0, 15, 30, 45] */
  minuteRange: { type: [String, Array], default: '' },
  /** 可选星期范围或星期列表，如 [1]、[1, 2, 3, 4, 5]，约定 1 为星期一，7 为星期日 */
  weekDays: { type: [String, Array], default: '' }
})

const model = defineModel()

const emit = defineEmits(['change'])

/** 单值类型占位提示：优先取 placeholder，其次按 fieldName 生成「请选择 + fieldName」 */
function singlePlaceholder(label) {
  return props.placeholder || '请选择' + (props.fieldName || label)
}

/** 转发内部选择器的 change 事件（范围类型回传数组，单值类型回传字符串/日期对象） */
function handleChange(value) {
  emit('change', value)
}

const weekFormat = 'YYYY 第 ww 周'

const yearList = computed(() => parseRange(props.yearRange))
const monthList = computed(() => parseRange(props.monthRange))
const hourList = computed(() => parseRange(props.hourRange))
const minuteList = computed(() => parseRange(props.minuteRange))
const weekList = computed(() => parseRange(props.weekDays))

/** 解析范围参数：'a-b' 展开为区间数组，[a,b,c] 原样转为数字数组，空返回 [] */
function parseRange(range) {
  if (range === null || range === undefined || range === '') {
    return []
  }
  if (Array.isArray(range)) {
    return range.map(Number).filter((n) => !Number.isNaN(n))
  }
  const str = String(range)
  if (str.includes('-')) {
    const [start, end] = str.split('-').map(Number)
    if (Number.isNaN(start) || Number.isNaN(end)) {
      return []
    }
    const result = []
    for (let i = start; i <= end; i++) {
      result.push(i)
    }
    return result
  }
  return str
    .split(',')
    .map(Number)
    .filter((n) => !Number.isNaN(n))
}

/** 禁用日期：按年份范围 / 月份范围 / 星期范围过滤（js getDay：0=周日，转 7） */
function disabledDate(date) {
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  const week = date.getDay() === 0 ? 7 : date.getDay()
  if (yearList.value.length && !yearList.value.includes(year)) {
    return true
  }
  if (monthList.value.length && !monthList.value.includes(month)) {
    return true
  }
  if (weekList.value.length && !weekList.value.includes(week)) {
    return true
  }
  return false
}

/** 禁用小时：hourRange 限定可选小时 */
function disabledHours(hour) {
  return hourList.value.length > 0 && !hourList.value.includes(hour)
}

/** 禁用分钟：minuteRange 限定可选分钟 */
function disabledMinutes(minute) {
  return minuteList.value.length > 0 && !minuteList.value.includes(minute)
}

function disabledSeconds() {
  return false
}
</script>

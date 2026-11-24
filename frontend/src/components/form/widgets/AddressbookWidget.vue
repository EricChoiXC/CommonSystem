<template>
  <!--
    复用公共组件 AddressBook（components/AddressBook.vue，只消费不修改既有契约）：
    v-model:fieldId + v-model:fieldName 双绑定（多选分号拼接）、orgType 可选类型、
    multi 单多选、showRole 角色页签、disabled 只读展示不可打开弹窗；septarator
    用 materials/common.js 的 ADDRESSBOOK_SEPARATOR 常量（对齐其契约默认 ';'）。
  -->
  <AddressBook
    :field-id="innerValue.fieldId"
    :field-name="innerValue.fieldName"
    :org-type="orgType"
    :multi="multi"
    :septarator="ADDRESSBOOK_SEPARATOR"
    :show-role="showRole"
    :disabled="disabled || readonly"
    @update:field-id="handleFieldId"
    @update:field-name="handleFieldName"
  />
</template>

<script setup>
import { ref, watch } from 'vue'
import AddressBook from '../../../components/AddressBook.vue'
import { ADDRESSBOOK_SEPARATOR } from '../materials/common.js'

/**
 * 地址本 widget（type=addressbook，功能详细设计 §1.3 addressbook 行；§6.5 复用口径）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明）+ 扩展属性项 multi/orgType/showRole + 统一
 *   控制位 disabled/readonly（readonly 与 disabled 同为置灰禁开弹窗——AddressBook 契约
 *   自带只读展示，经 disabled 透传承载）；
 * - emits：update:modelValue / change / focus / blur（地址本为弹窗选择型控件、无聚焦
 *   时机，focus/blur 无对应触发时机，按契约保留声明）；
 * - 值契约（组件值空间，与物料 deserializer/serializer 对齐，materials/common.js）：
 *   modelValue = { fieldId: '', fieldName: '' }（AddressBook 双绑定形态，多选经
 *   ADDRESSBOOK_SEPARATOR 分号拼接，空串表示未选）；组件值 ↔ 载荷 { ids: [], names: [] }
 *   的互转由 serializeAddressBook/deserializeAddressBook 承载、在渲染器层做转换，
 *   widget 内部只处理组件值形态；
 * - AddressBook 确认/清空按序 emit update:fieldId 与 update:fieldName 两个事件，
 *   本组件合并同拍双事件（microtask 合帧）归一为一次 { fieldId, fieldName } 组件值
 *   上报，避免中间态（新 id + 旧 name）外溢与 change 重复触发；
 * - 固定属性项由渲染器层承载，本组件只渲染控件本体，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值（AddressBook 双绑定形态 { fieldId, fieldName }，空串表示未选） */
  modelValue: { type: Object, default: () => ({ fieldId: '', fieldName: '' }) },
  /** 单多选（透传 AddressBook.multi，多选 fieldId/fieldName 分号拼接） */
  multi: { type: Boolean, default: false },
  /** 可选类型：ALL/ORG/DEPT/ORGDEPT/PERSON/POST/PERSONPOST/ORGDEPTPOSTPERSON（透传） */
  orgType: { type: String, default: 'ALL' },
  /** 是否显示「角色」页签（透传 AddressBook.showRole） */
  showRole: { type: Boolean, default: false },
  /** 统一控制位：禁用（透传 AddressBook.disabled——只读展示不可打开选择弹窗） */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（与 disabled 同为置灰禁开弹窗，经 disabled 透传承载） */
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

/** 内部双绑定值（AddressBook 双绑定需可写中间态，props 只读不可直接改） */
const innerValue = ref(normalize(props.modelValue))

/** 组件值归一：缺字段补空串（空串表示未选，AddressBook 契约为 String 双绑定） */
function normalize(value) {
  const source = value && typeof value === 'object' ? value : {}
  return {
    fieldId: source.fieldId === null || source.fieldId === undefined ? '' : String(source.fieldId),
    fieldName: source.fieldName === null || source.fieldName === undefined ? '' : String(source.fieldName),
  }
}

/** 回显同步：外部 modelValue 替换（反序列化回填 / 服务端重算回写）时同步内部双绑定值 */
watch(
  () => props.modelValue,
  (value) => {
    innerValue.value = normalize(value)
  },
  { deep: true },
)

/** 同拍双事件合帧标记（见头注：update:fieldId/update:fieldName 按序到达归并为一次上报） */
let flushScheduled = false

function scheduleEmit() {
  if (flushScheduled) return
  flushScheduled = true
  Promise.resolve().then(() => {
    flushScheduled = false
    const value = { ...innerValue.value }
    emit('update:modelValue', value)
    emit('change', value)
  })
}

function handleFieldId(fieldId) {
  innerValue.value = { ...innerValue.value, fieldId: fieldId === null || fieldId === undefined ? '' : String(fieldId) }
  scheduleEmit()
}

function handleFieldName(fieldName) {
  innerValue.value = { ...innerValue.value, fieldName: fieldName === null || fieldName === undefined ? '' : String(fieldName) }
  scheduleEmit()
}
</script>

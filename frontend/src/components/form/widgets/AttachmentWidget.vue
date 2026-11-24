<template>
  <!--
    复用公共组件 Attachment（components/Attachment.vue，只消费不修改既有契约）：
    fieldModelId/fieldModelName（必填）/fieldKey/fileType 透传、自带按 model 维度查询
    附件清单与上传/预览/下载/删除；multi 恒 true（值本身就是引用数组，条数上限由
    maxCount 收口）；canUpload/canDelete 由本组件按控制位与 maxCount 计算。
  -->
  <Attachment
    :field-model-name="modelName"
    :field-model-id="modelId"
    :field-key="fieldKey"
    :multi="true"
    :file-type="fileType"
    :can-upload="canUpload"
    :can-delete="canDelete"
    :upload-callback="handleUpload"
    :delete-callback="handleDelete"
  />
</template>

<script setup>
import { computed } from 'vue'
import Attachment from '../../../components/Attachment.vue'

/**
 * 附件 widget（type=attachment，功能详细设计 §1.3 attachment 行；§6.5 复用口径）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明）+ 扩展属性项 maxCount/fileType + 注入口径
 *   modelName/modelId/fieldKey + 统一控制位 disabled/readonly；
 * - emits：update:modelValue / change / focus / blur（附件为上传/清单型控件、无聚焦
 *   时机，focus/blur 无对应触发时机，按契约保留声明）；
 * - 值契约：modelValue = [{ attId, fileName }] 引用数组（附件实体落 sys/att，表单数据
 *   仅存引用，§6.5；不建 sys_form_attachment）；元素经 serializeAttachment 同款归一
 *   （去重、按 attId，剥离多余字段）；
 * - 注入口径（FE-005 对接预留）：modelName 缺省 'SYS-FORM'（表单模块 MODEL_KEY，
 *   properties/sys/form/application.yml，Attachment.fieldModelName 必填契约）；
 *   modelId = 数据行 ID（渲染器以数据行维度注入，填写页经 getInitID 预生成数据行 ID
 *   后方可上传，Attachment.triggerUpload 对空 modelId 自带警告兜底）；fieldKey = 字段码
 *   （渲染器按 field.field 注入）；
 * - canUpload = 可编辑 &&（无 maxCount 或当前引用数 < maxCount）；canDelete = 可编辑；
 *   disabled/readonly → canUpload=false、canDelete=false（只读回显附件清单，预览/下载
 *   仍可用——Attachment 契约对所有人显示）；
 * - 上传/删除经 Attachment 的 uploadCallback/deleteCallback 回调同步维护 modelValue
 *   引用数组（按 attId 去重；达上限后按钮已隐藏，回调侧防御性不再追加）；
 * - 固定属性项由渲染器层承载，本组件只渲染控件本体，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值（附件引用数组 [{ attId, fileName }]，空值 []） */
  modelValue: { type: Array, default: () => [] },
  /** 数量上限（null/非正数 = 无上限；默认 5 由物料 defaultProps 承载） */
  maxCount: { type: Number, default: null },
  /** 允许上传文件格式（逗号分隔后缀，空 = 不限制；透传 Attachment.fileType） */
  fileType: { type: String, default: '' },
  /** 附件归属模块名（缺省表单模块 MODEL_KEY 'SYS-FORM'，透传 Attachment.fieldModelName 必填契约） */
  modelName: { type: String, default: 'SYS-FORM' },
  /** 附件归属数据行 ID（渲染器以数据行维度注入，空 = 未生成，上传前置要求已存在） */
  modelId: { type: String, default: '' },
  /** 附件归属字段码（渲染器按 field.field 注入，透传 Attachment.fieldKey） */
  fieldKey: { type: String, default: '' },
  /** 统一控制位：禁用（canUpload/canDelete 置 false，只读回显附件清单） */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（同上，只读回显附件清单） */
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

/** 是否可编辑（控制位统一收敛：disabled / readonly 均视为不可编辑） */
const editable = computed(() => !props.disabled && !props.readonly)

/** 上传入口显隐：可编辑 &&（无 maxCount 或当前引用数 < maxCount） */
const canUpload = computed(() => {
  if (!editable.value) return false
  const maxCount = normalizeMaxCount()
  return maxCount === null || normalizeRefs(props.modelValue).length < maxCount
})

/** 删除按钮显隐：可编辑（Attachment 契约预览/下载对所有人显示） */
const canDelete = computed(() => editable.value)

/** maxCount 归一：null/undefined/非正数 → null（无上限） */
function normalizeMaxCount() {
  if (props.maxCount === null || props.maxCount === undefined || props.maxCount === '') return null
  const count = Number(props.maxCount)
  return Number.isFinite(count) && count > 0 ? count : null
}

/** 引用数组归一：过滤非对象与缺 attId 项，元素收敛为 { attId, fileName }（对齐 serializeAttachment） */
function normalizeRefs(value) {
  if (!Array.isArray(value)) return []
  return value
    .filter((item) => item !== null && typeof item === 'object' && item.attId !== null && item.attId !== undefined && item.attId !== '')
    .map((item) => ({ attId: item.attId, fileName: item.fileName }))
}

/** 上报归一后的引用数组（update:modelValue + change，契约统一双事件） */
function emitRefs(refs) {
  emit('update:modelValue', refs)
  emit('change', refs)
}

/**
 * 上传回调（Attachment 上传成功后调用，载荷为附件行 { fieldId, fieldName, ... }）：
 * 按 attId 去重后追加引用；达 maxCount 后不再追加（按钮已隐藏，防御性兜底）。
 */
function handleUpload(attachment) {
  const attId = attachment && attachment.fieldId
  if (attId === null || attId === undefined || attId === '') return
  const refs = normalizeRefs(props.modelValue).filter((item) => String(item.attId) !== String(attId))
  const maxCount = normalizeMaxCount()
  if (maxCount !== null && refs.length >= maxCount) return
  refs.push({ attId, fileName: attachment.fileName || '' })
  emitRefs(refs)
}

/** 删除回调（Attachment 删除成功后调用）：按 attId 移除引用 */
function handleDelete(attachment) {
  const attId = attachment && attachment.fieldId
  if (attId === null || attId === undefined || attId === '') return
  emitRefs(normalizeRefs(props.modelValue).filter((item) => String(item.attId) !== String(attId)))
}
</script>

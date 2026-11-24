/**
 * 自定义表单数据类物料（category=DATA，12 个，F15-FE-002；F15-FE-003 接入基础七件运行时、
 * F15-FE-004 接入业务数据四件运行时）
 *
 * 口径依据：功能详细设计 §1.3 数据类清单（R03）+ UI 设计分册 §4.5.2 数据类扩展属性项
 * 登记表（三方一致口径：组件文档表格 / UI 分册 / 功能设计 §1.3）。
 * - 固定属性项（名称/ID/是否必填/说明文本/默认值）由 dataFixedPropSchema 按类别裁剪生成；
 * - component：基础七件已由 F15-FE-003 接入（input/textarea/number/choice/select/
 *   date/switch → widgets/ 下同名 *Widget.vue，统一 props/v-model/事件分发契约见
 *   各 widget 头注与功能设计 §4.3）；业务数据四件已由 F15-FE-004 接入（formula/
 *   datasource/addressbook/attachment → widgets/ 下同名 *Widget.vue，复用契约与值
 *   形态见各 widget 头注与功能设计 §6.3/§6.5）；cascade 待 F15-FE-010 注册，渲染器
 *   对 null 按未知 type 兜底占位；
 * - dataType：单列取值为字符串；choice/select/cascade/datasource 随 props.multi 多态、
 *   formula 随 props.expectedReturnType 多态（以 (props)=>dataType 函数承载）；
 * - validator 返回中文错误消息数组（约定见 common.js 头注）。
 */

import InputWidget from '../widgets/InputWidget.vue'
import TextareaWidget from '../widgets/TextareaWidget.vue'
import NumberWidget from '../widgets/NumberWidget.vue'
import ChoiceWidget from '../widgets/ChoiceWidget.vue'
import SelectWidget from '../widgets/SelectWidget.vue'
import DateWidget from '../widgets/DateWidget.vue'
import SwitchWidget from '../widgets/SwitchWidget.vue'
import FormulaWidget from '../widgets/FormulaWidget.vue'
import DatasourceWidget from '../widgets/DatasourceWidget.vue'
import AddressbookWidget from '../widgets/AddressbookWidget.vue'
import AttachmentWidget from '../widgets/AttachmentWidget.vue'
import {
  MAX_TEXT_LENGTH,
  ADDRESSBOOK_ORG_TYPES,
} from './common.js'
import {
  dataFixedPropSchema,
  dataDefaultProps,
  checkRequired,
  checkStringType,
  checkArrayType,
  checkMaxLength,
  checkNumber,
  checkEnumOptions,
  isEmptyValue,
  labelOf,
  serializeIdentity,
  deserializeIdentity,
  serializeAddressBook,
  deserializeAddressBook,
  serializeAttachment,
  deserializeAttachment,
} from './common.js'

/* 选项编辑器列描述（显示值 / 实际值，一期静态配置；optionsSource=dict 协议位二期开放） */
const OPTION_COLUMNS = [
  { key: 'label', label: '显示值', editor: 'input' },
  { key: 'value', label: '实际值', editor: 'input' },
]

const inputMaterial = {
  type: 'input',
  name: 'input',
  label: '单行文本',
  category: 'DATA',
  icon: 'Edit',
  defaultProps: dataDefaultProps({ maxlength: 200, password: false }),
  propSchema: [
    ...dataFixedPropSchema('input'),
    { key: 'maxlength', label: '最大长度', editor: 'number', min: 1, max: MAX_TEXT_LENGTH, tip: `应用层上限 ${MAX_TEXT_LENGTH}（MAX_TEXT_LENGTH）` },
    { key: 'password', label: '是否密码', editor: 'switch', tip: '掩码输入（R03：密码并入单行文本属性）' },
  ],
  component: InputWidget, // 运行时组件（F15-FE-003 接入，契约见 widgets/InputWidget.vue 头注）
  defaultValueGenerator: () => null,
  dataType: 'STRING',
  validator(value, props, context) {
    return [...checkRequired(value, props, context), ...checkStringType(value, context), ...checkMaxLength(value, props, context)]
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

const textareaMaterial = {
  type: 'textarea',
  name: 'textarea',
  label: '多行文本',
  category: 'DATA',
  icon: 'Document',
  defaultProps: dataDefaultProps({ maxlength: 200 }),
  propSchema: [
    ...dataFixedPropSchema('input'),
    { key: 'maxlength', label: '最大长度', editor: 'number', min: 1, max: MAX_TEXT_LENGTH, tip: `应用层上限 ${MAX_TEXT_LENGTH}（MAX_TEXT_LENGTH）` },
  ],
  component: TextareaWidget, // 运行时组件（F15-FE-003 接入，契约见 widgets/TextareaWidget.vue 头注）
  defaultValueGenerator: () => null,
  dataType: 'STRING',
  validator(value, props, context) {
    return [...checkRequired(value, props, context), ...checkStringType(value, context), ...checkMaxLength(value, props, context)]
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

const numberMaterial = {
  type: 'number',
  name: 'number',
  label: '数字',
  category: 'DATA',
  icon: 'Histogram',
  defaultProps: dataDefaultProps({ numberType: 'DECIMAL', precision: 2, max: null, min: null, allowNegative: false, separator: 'NONE' }),
  propSchema: [
    ...dataFixedPropSchema('number'),
    {
      key: 'numberType',
      label: '数字类型',
      editor: 'select',
      options: [
        { label: '整数', value: 'INTEGER' },
        { label: '小数', value: 'DECIMAL' },
        { label: '百分比', value: 'PERCENT' },
        { label: '金额', value: 'AMOUNT' },
      ],
    },
    { key: 'precision', label: '精度', editor: 'number', min: 0, max: 6 },
    { key: 'max', label: '最大值', editor: 'number' },
    { key: 'min', label: '最小值', editor: 'number' },
    { key: 'allowNegative', label: '允许负数', editor: 'switch' },
    {
      key: 'separator',
      label: '分隔符',
      editor: 'select',
      options: [
        { label: '无', value: 'NONE' },
        { label: '千分位', value: 'THOUSAND' },
        { label: '万分位', value: 'TEN_THOUSAND' },
        { label: '亿分位', value: 'HUNDRED_MILLION' },
      ],
    },
  ],
  component: NumberWidget, // 运行时组件（F15-FE-003 接入，格式化/解析/钳制收口 widgets/numberFormat.js）
  defaultValueGenerator: () => null,
  dataType: 'NUMBER',
  validator(value, props, context) {
    return [...checkRequired(value, props, context), ...checkNumber(value, props, context)]
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

const choiceMaterial = {
  type: 'choice',
  name: 'choice',
  label: '选择框',
  category: 'DATA',
  icon: 'Finished',
  defaultProps: dataDefaultProps({ multi: false, layout: 'horizontal', options: [] }),
  propSchema: [
    ...dataFixedPropSchema('json'),
    { key: 'multi', label: '单多选', editor: 'switch', tip: '开启后值为数组' },
    {
      key: 'layout',
      label: '排列方式',
      editor: 'radio',
      options: [
        { label: '横排', value: 'horizontal' },
        { label: '竖排', value: 'vertical' },
      ],
    },
    { key: 'options', label: '选择项', editor: 'table', columns: OPTION_COLUMNS, tip: '显示值/实际值；一期静态配置（optionsSource=dict 协议位二期开放）' },
  ],
  component: ChoiceWidget, // 运行时组件（F15-FE-003 接入，契约见 widgets/ChoiceWidget.vue 头注）
  defaultValueGenerator: (props) => (props && props.multi ? [] : null),
  dataType: (props) => (props && props.multi ? 'ARRAY' : 'STRING'),
  validator(value, props, context) {
    return [...checkRequired(value, props, context), ...checkEnumOptions(value, props, context)]
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

const selectMaterial = {
  type: 'select',
  name: 'select',
  label: '下拉选择器',
  category: 'DATA',
  icon: 'ArrowDown',
  defaultProps: dataDefaultProps({ multi: false, options: [] }),
  propSchema: [
    ...dataFixedPropSchema('json'),
    { key: 'multi', label: '单多选', editor: 'switch', tip: '开启后值为数组' },
    { key: 'options', label: '选择项', editor: 'table', columns: OPTION_COLUMNS, tip: '显示值/实际值；一期静态配置（optionsSource=dict 协议位二期开放）' },
  ],
  component: SelectWidget, // 运行时组件（F15-FE-003 接入，契约见 widgets/SelectWidget.vue 头注）
  defaultValueGenerator: (props) => (props && props.multi ? [] : null),
  dataType: (props) => (props && props.multi ? 'ARRAY' : 'STRING'),
  validator(value, props, context) {
    return [...checkRequired(value, props, context), ...checkEnumOptions(value, props, context)]
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

const dateMaterial = {
  type: 'date',
  name: 'date',
  label: '日期',
  category: 'DATA',
  icon: 'Calendar',
  defaultProps: dataDefaultProps({ dateType: 'DATE', format: 'YYYY-MM-DD' }),
  propSchema: [
    ...dataFixedPropSchema('date'),
    {
      key: 'dateType',
      label: '类型',
      editor: 'select',
      options: [
        { label: '日期', value: 'DATE' },
        { label: '时间', value: 'TIME' },
        { label: '日期时间', value: 'DATETIME' },
      ],
    },
    {
      key: 'format',
      label: '显示格式',
      editor: 'select',
      options: [
        { label: 'YYYY-MM-DD', value: 'YYYY-MM-DD' },
        { label: 'HH:mm:ss', value: 'HH:mm:ss' },
        { label: 'YYYY-MM-DD HH:mm:ss', value: 'YYYY-MM-DD HH:mm:ss' },
      ],
      tip: 'dayjs token，按类型 dateType 联动可选集（对齐公共日期选择器口径）；值以格式化字符串落库，避免时区歧义',
    },
  ],
  component: DateWidget, // 运行时组件（F15-FE-003 接入，契约见 widgets/DateWidget.vue 头注）
  defaultValueGenerator: () => null,
  dataType: 'STRING',
  // 一期不做格式正则校验：合法格式由 dateType/format 约束的控件产出（对齐后端 BE-003「date 文本」类型口径）
  validator(value, props, context) {
    return [...checkRequired(value, props, context), ...checkStringType(value, context)]
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

const switchMaterial = {
  type: 'switch',
  name: 'switch',
  label: '开关',
  category: 'DATA',
  icon: 'Switch',
  defaultProps: dataDefaultProps({ onText: '开', offText: '关' }),
  propSchema: [
    ...dataFixedPropSchema('switch'),
    { key: 'onText', label: '【开】显示文本', editor: 'input' },
    { key: 'offText', label: '【关】显示文本', editor: 'input' },
  ],
  component: SwitchWidget, // 运行时组件（F15-FE-003 接入，契约见 widgets/SwitchWidget.vue 头注）
  defaultValueGenerator: () => false, // 开关默认关（false 为有义值，后端 BE-003 同口径）
  dataType: 'BOOLEAN',
  validator(value, props, context) {
    if (!isEmptyValue(value) && typeof value !== 'boolean') return [`${labelOf(context)}必须为布尔值`]
    return checkRequired(value, props, context)
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

const cascadeMaterial = {
  type: 'cascade',
  name: 'cascade',
  label: '级联选择器',
  category: 'DATA',
  icon: 'Connection',
  defaultProps: dataDefaultProps({ parentField: '', options: [], multi: false }),
  propSchema: [
    ...dataFixedPropSchema('json'),
    {
      key: 'parentField',
      label: '父数据项',
      editor: 'contextSelect',
      context: 'dataFields',
      tip: '选项 = 表单内已配置的数据类字段码（排除自身，R08 第 4 条）；父项值清空时清空本字段值（组件内置行为，不经规则 DSL 配置）',
    },
    {
      key: 'options',
      label: '选择项',
      editor: 'table',
      columns: [
        ...OPTION_COLUMNS,
        { key: 'showCondition', label: '显隐条件', editor: 'condition', tip: '可空；条件 DSL：operator 十项 + value（R08 一期形态，不走逐项 simulate）' },
      ],
      tip: '显隐条件按 R08 条件 DSL 前端本地过滤 + 保存时服务端复核',
    },
    { key: 'multi', label: '单多选', editor: 'switch', tip: '开启后值为数组' },
  ],
  component: null, // 待 F15-FE-010 注册
  defaultValueGenerator: (props) => (props && props.multi ? [] : null),
  dataType: (props) => (props && props.multi ? 'ARRAY' : 'STRING'),
  // 静态选项集比对；「父项条件过滤后的动态可选集」由渲染器本地过滤 + 服务端复核（R08），不在静态校验内
  validator(value, props, context) {
    return [...checkRequired(value, props, context), ...checkEnumOptions(value, props, context)]
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

const formulaMaterial = {
  type: 'formula',
  name: 'formula',
  label: '公式计算',
  category: 'DATA',
  icon: 'MagicStick',
  defaultProps: dataDefaultProps({ expression: '', expectedReturnType: 'NUMBER' }),
  propSchema: [
    ...dataFixedPropSchema('json'),
    {
      key: 'expression',
      label: '公式',
      editor: 'formula',
      tip: '只读输入框 +【设计公式】按钮打开 FormulaDesigner 弹窗（F14 契约：availableVars 传表单字段码声明，含明细数组变量 ${list.field} 投影）；不允许直接手改',
    },
    {
      key: 'expectedReturnType',
      label: '期望返回类型',
      editor: 'select',
      options: [
        { label: '数字', value: 'NUMBER' },
        { label: '文本', value: 'STRING' },
        { label: '布尔', value: 'BOOLEAN' },
        { label: '日期', value: 'DATE' },
      ],
    },
  ],
  component: FormulaWidget, // 运行时组件（F15-FE-004 接入：配置态内嵌 FormulaDesigner 编辑公式、编辑/详情态只读展示计算结果，ADR-09 前端不本地计算）
  defaultValueGenerator: () => null, // 值以服务端重算为准（ADR-09），前端不生成
  dataType: (props) => (props && props.expectedReturnType ? props.expectedReturnType : 'NUMBER'),
  // 公式字段不做前端值校验（ADR-09：前端值仅预览性质、以服务端重算结果为权威落库，差额不报错）
  validator() {
    return []
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

const datasourceMaterial = {
  type: 'datasource',
  name: 'datasource',
  label: '数据源',
  category: 'DATA',
  icon: 'Collection',
  defaultProps: dataDefaultProps({ datasourceCode: '', multi: false }),
  propSchema: [
    ...dataFixedPropSchema('json'),
    {
      key: 'datasourceCode',
      label: '数据源编码',
      editor: 'datasource',
      tip: '选项来自 api/datasource.js#getDatasourceOptions() 启用数据源清单；组件存编码不存快照',
    },
    { key: 'multi', label: '单多选', editor: 'switch', tip: '开启后值为数组' },
  ],
  component: DatasourceWidget, // 运行时组件（F15-FE-004 接入：经 api/datasource.js queryDatasource 运行时取数的选择器，存编码不存快照）
  defaultValueGenerator: (props) => (props && props.multi ? [] : null),
  dataType: (props) => (props && props.multi ? 'ARRAY' : 'STRING'),
  // 选项运行时经数据源取数、无静态 options，枚举校验不适用（取数合法性由组件取数行为与服务端把关）
  validator(value, props, context) {
    return checkRequired(value, props, context)
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

const addressbookMaterial = {
  type: 'addressbook',
  name: 'addressbook',
  label: '地址本',
  category: 'DATA',
  icon: 'Avatar',
  defaultProps: dataDefaultProps({ multi: false, orgType: 'ALL', showRole: false }),
  propSchema: [
    ...dataFixedPropSchema('none'),
    { key: 'multi', label: '单多选', editor: 'switch' },
    { key: 'orgType', label: '可选类型', editor: 'select', options: ADDRESSBOOK_ORG_TYPES },
    { key: 'showRole', label: '是否显示角色页签', editor: 'switch' },
  ],
  component: AddressbookWidget, // 运行时组件（F15-FE-004 接入：复用 components/AddressBook.vue 双绑定契约，组件值空间 { fieldId, fieldName }，载荷转换在渲染器层经 serializer）
  // 组件双绑定空态（fieldId/fieldName 空串）；载荷空值由 serializer 归一
  defaultValueGenerator: () => ({ fieldId: '', fieldName: '' }),
  dataType: 'OBJECT', // 载荷形态 { ids: [], names: [] }
  validator(value, props, context) {
    if (!isEmptyValue(value) && (typeof value !== 'object' || Array.isArray(value))) {
      return [`${labelOf(context)}值必须为对象`]
    }
    return checkRequired(value, props, context)
  },
  serializer: serializeAddressBook,
  deserializer: deserializeAddressBook,
}

const attachmentMaterial = {
  type: 'attachment',
  name: 'attachment',
  label: '附件',
  category: 'DATA',
  icon: 'Paperclip',
  // maxCount 默认 5 为实现设定值（文档未定稿默认数量）
  defaultProps: dataDefaultProps({ maxCount: 5, fileType: '' }),
  propSchema: [
    ...dataFixedPropSchema('none'),
    { key: 'maxCount', label: '数量上限', editor: 'number', min: 1 },
    { key: 'fileType', label: '允许上传文件格式', editor: 'input', tip: '逗号分隔后缀，空 = 不限制（Attachment 契约 fileType）' },
  ],
  component: AttachmentWidget, // 运行时组件（F15-FE-004 接入：复用 components/Attachment.vue，附件实体落 sys/att、表单数据存引用 [{ attId, fileName }]）
  defaultValueGenerator: () => [],
  dataType: 'ARRAY', // [{ attId, fileName }]
  validator(value, props, context) {
    const errors = [...checkRequired(value, props, context), ...checkArrayType(value, context)]
    if (errors.length > 0 || !Array.isArray(value)) return errors
    const label = labelOf(context)
    const maxCount = props && props.maxCount !== null && props.maxCount !== undefined ? Number(props.maxCount) : null
    if (maxCount !== null && value.length > maxCount) {
      errors.push(`${label}最多上传${maxCount}个附件`)
    }
    value.forEach((item, index) => {
      if (!item || typeof item !== 'object' || item.attId === null || item.attId === undefined || item.attId === '') {
        errors.push(`${label}第${index + 1}项缺少附件引用`)
      }
    })
    return errors
  },
  serializer: serializeAttachment,
  deserializer: deserializeAttachment,
}

/** 数据类 12 物料（§1.3 顺序：input/textarea/number/choice/select/date/switch/cascade/formula/datasource/addressbook/attachment） */
const DATA_MATERIALS = [
  inputMaterial,
  textareaMaterial,
  numberMaterial,
  choiceMaterial,
  selectMaterial,
  dateMaterial,
  switchMaterial,
  cascadeMaterial,
  formulaMaterial,
  datasourceMaterial,
  addressbookMaterial,
  attachmentMaterial,
]

export default DATA_MATERIALS

/**
 * 自定义表单内容类物料（category=CONTENT，4 个，F15-FE-002）
 *
 * 口径依据：功能详细设计 §1.3 内容类清单 + §2.1 明细表行结构（R04 一层）+ UI 设计分册
 * §4.5.2 内容类扩展属性项登记表。
 * - detailTable 为数据容器特例：dataType=ARRAY（扁平行对象数组，§2.1），固定项含 ID/必填
 *   （语义=至少一行按 rowMin）、无 defaultValue；行内字段 children[] 由画布嵌套放置直接
 *   编辑（UI 分册 §4.5.2），不经属性面板配置，propSchema 不登记 children；
 * - table/text/image 不承载数据（dataType=null，值不进数据载荷），固定项仅名称/说明文本；
 * - component 一律 null：运行时组件待 F15-FE-007（内容与明细表）注册，渲染器对 null 按
 *   未知 type 兜底占位。
 */

import {
  MAX_DETAIL_ROWS,
  detailTableFixedPropSchema,
  detailTableDefaultProps,
  contentFixedPropSchema,
  plainDefaultProps,
  checkArrayType,
  labelOf,
  serializeIdentity,
  deserializeIdentity,
} from './common.js'

const detailTableMaterial = {
  type: 'detailTable',
  name: 'detailTable',
  label: '明细表',
  category: 'CONTENT',
  icon: 'List',
  defaultProps: detailTableDefaultProps({ rowMin: 0, rowMax: MAX_DETAIL_ROWS, summary: [] }),
  propSchema: [
    ...detailTableFixedPropSchema(),
    { key: 'rowMin', label: '行数下限', editor: 'number', min: 0, tip: '是否必填语义 = 至少一行（按本下限）' },
    { key: 'rowMax', label: '行数上限', editor: 'number', min: 1, max: MAX_DETAIL_ROWS, tip: `不得超过 ${MAX_DETAIL_ROWS}（MAX_DETAIL_ROWS，对齐后端明细行上限，超限面板提示）` },
    {
      key: 'summary',
      label: '统计行',
      editor: 'table',
      columns: [
        { key: 'field', label: '统计字段', editor: 'contextSelect', context: 'detailNumericFields', tip: '行内数值类字段码' },
        {
          key: 'fn',
          label: '统计方式',
          editor: 'select',
          options: [
            { label: '合计', value: 'sum' },
            { label: '平均', value: 'avg' },
            { label: '计数', value: 'count' },
          ],
        },
      ],
    },
    // children[]（行内字段）不由属性面板配置——行内字段经画布嵌套放置直接编辑（UI 分册 §4.5.2）
  ],
  component: null, // 待 F15-FE-007 注册（增删行/行内规则/自动计算/合计）
  defaultValueGenerator: () => [], // 空行数组；补足 rowMin 起始行由渲染器职责（F15-FE-007）
  dataType: 'ARRAY',
  /**
   * 明细表宿本校验器只管行数组类型与行数语义：required 的语义 = 至少一行（按 rowMin，
   * §1.1），不套用通用必填文案；rowMax 缺省用 MAX_DETAIL_ROWS。行内字段校验由渲染器对
   * children 逐行执行（行上下文 rowIndex 0 起始，F15-FE-007），行内校验口径与后端
   * BE-003 一致。
   */
  validator(value, props, context) {
    const errors = checkArrayType(value, context)
    if (errors.length > 0) return errors
    const label = labelOf(context)
    const rows = Array.isArray(value) ? value : []
    const rowMin = props && props.rowMin !== null && props.rowMin !== undefined ? Number(props.rowMin) : 0
    const minRows = Math.max(rowMin, props && props.required ? 1 : 0)
    if (minRows > 0 && rows.length < minRows) {
      errors.push(`${label}至少需要${minRows}行`)
    }
    const rowMax = props && props.rowMax !== null && props.rowMax !== undefined ? Number(props.rowMax) : MAX_DETAIL_ROWS
    if (rowMax > 0 && rows.length > rowMax) {
      errors.push(`${label}最多允许${rowMax}行`)
    }
    return errors
  },
  serializer: serializeIdentity, // 载荷即扁平行对象数组（§2.1 明细行数据结构）
  deserializer: deserializeIdentity,
}

const tableMaterial = {
  type: 'table',
  name: 'table',
  label: '表格',
  category: 'CONTENT',
  icon: 'Menu',
  defaultProps: plainDefaultProps({ columns: [], rows: [] }),
  propSchema: [
    ...contentFixedPropSchema(),
    {
      key: 'columns',
      label: '列定义',
      editor: 'table',
      columns: [
        { key: 'label', label: '列标题', editor: 'input' },
        { key: 'prop', label: '字段名', editor: 'input' },
        { key: 'width', label: '列宽', editor: 'number' },
      ],
    },
    { key: 'rows', label: '静态数据', editor: 'json', tip: 'JSON 文本域（前端做 JSON 合法性预检）；内容型只读表格，不进数据载荷' },
  ],
  component: null, // 待 F15-FE-007 注册
  defaultValueGenerator: () => null,
  dataType: null, // 不承载数据，不进数据载荷
  validator() {
    return []
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

const textMaterial = {
  type: 'text',
  name: 'text',
  label: '文本',
  category: 'CONTENT',
  icon: 'Tickets',
  defaultProps: plainDefaultProps({ content: '' }),
  propSchema: [...contentFixedPropSchema(), { key: 'content', label: '文本内容', editor: 'textarea', tip: '静态说明文字' }],
  component: null, // 待 F15-FE-007 注册
  defaultValueGenerator: () => null,
  dataType: null,
  validator() {
    return []
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

const imageMaterial = {
  type: 'image',
  name: 'image',
  label: '图片',
  category: 'CONTENT',
  icon: 'Picture',
  defaultProps: plainDefaultProps({ src: '', width: null }),
  propSchema: [
    ...contentFixedPropSchema(),
    { key: 'src', label: '图片地址', editor: 'input' },
    { key: 'width', label: '展示宽度', editor: 'number', min: 0, tip: '像素（px），留空自适应' },
  ],
  component: null, // 待 F15-FE-007 注册
  defaultValueGenerator: () => null,
  dataType: null,
  validator() {
    return []
  },
  serializer: serializeIdentity,
  deserializer: deserializeIdentity,
}

/** 内容类 4 物料（§1.3 顺序：detailTable/table/text/image） */
const CONTENT_MATERIALS = [detailTableMaterial, tableMaterial, textMaterial, imageMaterial]

export default CONTENT_MATERIALS

/**
 * 自定义表单布局类物料（category=LAYOUT，2 个，F15-FE-002）
 *
 * 口径依据：功能详细设计 §1.3 布局类清单（R03 一期新增）+ §2.1（容器 children 结构）+
 * UI 设计分册 §4.5.2 布局类扩展属性项登记表。
 * - 固定项仅名称（分组即区块标题；无 ID/必填/说明/默认值）；
 * - group.children / grid.children（列对象 { span, fields[] }）均为 Schema 字段对象
 *   children（§2.1），由画布嵌套放置承载，不经 props 存储；grid 属性面板维护列数与
 *   各列 span（24 栅格制），落在 children 列对象上（target='children'）；
 * - 嵌套深度受 §2.2 MAX_NEST_DEPTH=3 与 R04「明细表内禁容器」约束——由后端结构校验器
 *   固化拒绝（保存期权威），设计器画布嵌套校验随 F15-FE-008；
 * - dataType=null（容器不承载数据，值不进数据载荷）；component 一律 null：运行时组件
 *   待 F15-FE-007 注册，渲染器对 null 按未知 type 兜底占位。
 */

import {
  layoutFixedPropSchema,
  plainDefaultProps,
  serializeIdentity,
  deserializeIdentity,
} from './common.js'

const groupMaterial = {
  type: 'group',
  name: 'group',
  label: '分组',
  category: 'LAYOUT',
  icon: 'Folder',
  defaultProps: plainDefaultProps(),
  propSchema: [
    ...layoutFixedPropSchema(),
    // children[] 由画布嵌套承载，不经属性面板配置（UI 分册 §4.5.2：分组无扩展属性项）
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

const gridMaterial = {
  type: 'grid',
  name: 'grid',
  label: '栅格',
  category: 'LAYOUT',
  icon: 'Grid',
  defaultProps: plainDefaultProps(),
  propSchema: [
    ...layoutFixedPropSchema(),
    {
      // 列配置存字段对象 children（列对象 { span, fields[] }，§2.1），属性面板维护列数与各列 span
      key: 'columns',
      label: '列配置',
      editor: 'gridColumns',
      target: 'children',
      tip: '24 栅格制，各列 span 合计超出 24 时提示；列内字段经画布嵌套放置承载',
    },
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

/** 布局类 2 物料（§1.3 顺序：group/grid） */
const LAYOUT_MATERIALS = [groupMaterial, gridMaterial]

export default LAYOUT_MATERIALS

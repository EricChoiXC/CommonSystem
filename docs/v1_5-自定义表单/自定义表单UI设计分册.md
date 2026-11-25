# 自定义表单 UI 设计分册（v1.5）

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 文档定位 | 自定义表单 v1_5 的**前端 UI 设计冻结稿**（Wave 0 任务 F15-DOC-004 产出）：页面与路由总表、菜单装配与 `activeMenu` 归约、表单设计器三区域交互（物料/画布/属性与规则面板）、渲染器三形态消费方式、管理列表页、表单元数据表单页、填写页与数据详情页、`api/form.js` 具名函数清单与组件目录规划、页面走查清单 |
| 目标版本 | v1.5（自定义表单：组件协议 + 表单设计器 + 动态渲染器 + 规则引擎 + 数据引擎） |
| 模块编号 | F15（模块 key `form`，前端 `views/form/` / `components/form/` / `api/form.js`） |
| 文档版本 | V1.0 |
| 创建日期 | 2026-11-23 |
| 文档状态 | 评审稿（接口未实现、页面未编码；随 F15-FE-001/002/008/009/010 落地并同批回写） |
| 输入依据 | [自定义表单功能详细设计.md](自定义表单功能详细设计.md)（F15-DOC-001 产出：组件协议 §1 / Schema 协议 §2 / 规则协议 §3 / 渲染器契约 §4 / 版本机制 §5 / 权限与配置定稿 §12）、[自定义表单API接口设计分册.md](自定义表单API接口设计分册.md)（F15-DOC-002 产出：15 接口契约与 `CS_FORM_*` 错误码）、[自定义表单组件.md](自定义表单组件.md)（组件全集表格）、[自定义表单开发任务清单.md](自定义表单开发任务清单.md)（§4.4 任务卡片、§6 R06/R07 风险行）、[AGENTS.md](../../AGENTS.md) §6 前端约束、[前端公共组件.md](../系统模块/前端公共组件.md)（AddressBook / Attachment / FormulaDesigner / DatasourceTable 复用契约）、[流程v1_7前端UI设计分册.md](../设计文档/sys/process/流程v1_7前端UI设计分册.md)（文档结构与设计器专章先例） |
| 承接关系 | 本文档供 F15-FE-001（api 封装）、F15-FE-002/008（物料库与设计器）、F15-FE-009（页面与路由菜单）、F15-FE-010（级联配置形态）实现与 F15-INT-002 页面走查使用；系统模块层页面承接见 [系统模块UI设计文档](../系统模块/系统模块UI设计文档.md) §4.12（占位摘要，权威口径为本文） |

### 1.1 范围与边界

- **In Scope**：路由与 `meta` 冻结口径、菜单装配与 `activeMenu` 归约、设计器三区域布局与画布交互、属性面板固定属性项与逐组件扩展属性项登记、规则配置面板四类规则的可视化配置形态、预览 / Schema 导入导出 / 发布与版本入口、渲染器三形态消费方式与组件契约、管理列表页三段式、表单元数据表单页、填写页与数据详情页（R06 路由形态定稿）、`api/form.js` 函数清单、组件目录规划、页面走查清单。
- **Out of Scope**：后端接口契约本体（见 [自定义表单API接口设计分册.md](自定义表单API接口设计分册.md)）、表结构与字段（见自定义表单数据库设计分册）、组件协议 / Schema 协议 / 规则协议 / 渲染器契约的语义定义（见功能详细设计 §1~§4，本文只做 UI 形态承接）、权限码与数据归属口径定稿（见功能详细设计 §12，本文只做菜单与入口承接）。
- **不写代码**：本任务只产出设计文档，不改动任何 Vue / JS / CSS 代码，不执行构建与提交。
- 本文档中标注「**v1_5 待实现（任务号）**」的内容为设计冻结项、尚未有代码；引用的现状代码事实（路由组织、`HomeView` 菜单结构、`activeMenu` 实现、公共样式类名、`api/index.js` 实例）基线核对日为 2026-11-23。

### 1.2 事实源优先级与冲突裁决

| 冲突项 | 裁决 |
| --- | --- |
| 组件协议 / Schema 协议 / 规则协议 / 渲染器契约（§1~§4）、版本机制、权限与数据归属口径（§12） | 以[自定义表单功能详细设计.md](自定义表单功能详细设计.md)为准；本文与其冲突时以功能详细设计为准并登记回写点（§11） |
| 接口路径、HTTP 方法、请求/响应字段、`ACTION_*`、错误码 `CS_FORM_*` | 以[自定义表单API接口设计分册.md](自定义表单API接口设计分册.md)为准；本文页面动作与接口的承接关系逐页对齐该分册 |
| 组件全集与属性项明细 | 功能详细设计 §1.3 定稿清单 > 本文属性面板登记表（UI 形态细化）> [自定义表单组件.md](自定义表单组件.md) 表格（输入基线）；三方应逐字一致，偏差随 F15-DOC-006 收口回查 |
| 列表页 / 表单页 / 路由 / 菜单通用形态 | [AGENTS.md](../../AGENTS.md) §6 前端约束为硬规范；与既有页面形态冲突时以 §6 为准 |
| 表名、字段类型、索引 | 以自定义表单数据库设计分册（F15-DOC-003 产出）为准，本文不涉及 |

### 1.3 版本变更记录

| 版本 | 日期 | 变更说明 |
| --- | --- | --- |
| V1.0 | 2026-11-23 | 初版：6 条路由与菜单冻结（R06 填写页路由形态定稿）、设计器三区域与画布交互冻结（R07 依赖倾向登记）、属性面板固定属性项 + 18 组件扩展属性项登记、规则面板四类规则配置形态、渲染器三形态消费方式、管理列表页 / 元数据表单页 / 填写页 / 数据详情页契约、`api/form.js` 15 函数清单、组件目录规划、走查清单与待确认事项 |
| V1.1 | 2026-11-25 | F15-FE-008 实现期回写：§4.1 拖拽依赖实现期裁决为原生 HTML5 DnD（零新依赖，R07 收口 CLOSED，§4.4 两处联动修正）；§9.2 设计器子组件实现命名落位（MaterialsPanel / PropertyPanel / RulePanel / PreviewDialog / SchemaImportDialog / VersionsDialog / designerSupport.js，designerSupport 承载快照栈与导入校验器）且 FormDesigner 经 components/index.js 统一出口导出（编译覆盖，区别于「不进统一出口」初版规划）；§4.4 补选中/落点解析实现口径（`.renderer-field-item[data-field]` 委托 + 码链消歧）、§4.9 补发布前置「画布有未保存改动先保存」预判；§11 T-01/T-05 收口 |

---

## 2. 页面与路由总表

### 2.1 路由约定（沿用仓库既有风格）

- 路由 path **不带前导 `/`**（`HomeView` 布局壳 `path: '/'` 的 `children` 相对写法，现状先例 `calendar/create`、`process/monitor`、`datasource/edit/:id`），顶层 `children` 不嵌套，靠 `meta.parent` / `meta.group` 渲染面包屑。
- 动态路由（`edit` / `design` / `fill` / `data`）设 `meta.tab: 'name'` 复用单标签（标签去重键取路由 name，先例 `org/edit/:id`、`processExample/detail/:id`）；静态叶子路由（`form/manage`、`form/create`）不设 `meta.tab`。
- `meta.auth` 为前端路由守卫与按钮预判，**不替代后端鉴权**（`ACTION_*` + resolver + `authorize` 为最终裁决，口径见功能详细设计 §12.3）；`SYS_ADMIN` 经 `hasAuth` 超管短路天然可见全部页面。
- 全部 6 条路由声明 `meta.moduleGroup: 'form'`（模块组 key 定稿见功能详细设计 §12.4）：所属模块组已关闭时布局层渲染「模块未开启」空态、菜单项隐藏（对齐 `calendar` / `datasource` / `formula` 先例）。

### 2.2 页面与路由总表

| 页面名称 | 路由 path | 路由 name | `meta`（title / parent / group / auth / tab） | 菜单归属 | 页面组件 | 主要角色 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 表单管理 | `form/manage` | `FormManage` | 表单管理 / 系统管理 / 自定义表单 / `SYS_FORM_ADMIN` / — | 系统管理 → 自定义表单 → 表单管理 | `views/form/FormManageView.vue` | 自定义表单管理员 | v1_5 待实现（F15-FE-009） |
| 表单新建 | `form/create` | `FormCreate` | 新建表单 / 系统管理 / 自定义表单 / `SYS_FORM_ADMIN` / — | 管理页【新建】进入，无独立菜单 | `views/form/FormFormView.vue` | 自定义表单管理员 | v1_5 待实现（F15-FE-009） |
| 表单编辑 | `form/edit/:id` | `FormEdit` | 编辑表单 / 系统管理 / 自定义表单 / `SYS_FORM_ADMIN` / **`tab: 'name'`** | 管理页行【编辑】进入 | `views/form/FormFormView.vue` | 自定义表单管理员 | v1_5 待实现（F15-FE-009） |
| 表单设计器 | `form/design/:id` | `FormDesign` | 表单设计器 / 系统管理 / 自定义表单 / `SYS_FORM_ADMIN` / **`tab: 'name'`** | 管理页行【设计】或元数据表单页【设计表单】进入 | `views/form/FormDesignView.vue` | 自定义表单管理员 | v1_5 待实现（F15-FE-008/009） |
| 表单填写 | `form/fill/:formId` | `FormFill` | 填写表单 / —（不设 parent/group，对齐 `profile` 先例）/ **不设 auth（登录即可）** / **`tab: 'name'`** | **不设独立菜单**（R06 定稿，见 §3.4） | `views/form/FormFillView.vue` | 登录用户（数据面） | v1_5 待实现（F15-FE-009） |
| 表单数据详情 | `form/data/:id` | `FormDataDetail` | 表单数据详情 / —（不设 parent/group）/ **不设 auth（登录即可 + 数据归属）** / **`tab: 'name'`** | **不设独立菜单**（经填写页【查看数据】、业务系统 businessId 链接进入） | `views/form/FormDataDetailView.vue` | 数据创建人或 `SYS_FORM_ADMIN` | v1_5 待实现（F15-FE-009） |

> 说明 1：路由参数语义——`form/edit/:id`、`form/design/:id` 的 `:id` 为**表单主档 `fieldId`**；`form/fill/:formId` 的 `:formId` 为**表单主档 `fieldId`**（Schema 协议头 `formId` 与主档 `field_id` 同值，API 分册 §3.9 参数名定稿说明）；`form/data/:id` 的 `:id` 为**数据行 `fieldId`**。
>
> 说明 2：`form/fill/:formId` 支持可选 query 参数 `businessId`（外部业务对象弱关联 ID，提交时随 `saveFormData` 请求体 `businessId` 携带；不传为空）——业务系统经链接集成时的最小承接位（§8.3）。
>
> 说明 3：填写页与数据详情页不设 `meta.auth`：数据面口径为「登录即可 + 数据归属」（R06 已定稿，功能详细设计 §12.2）——普通登录用户必须能访问填写页与本人数据详情页；越权访问（非本人且非管理员）由后端接口以 `CS_FORM_DATA_FORBIDDEN` 拒绝、页面以错误空态呈现，路由守卫不做权限拦截。
>
> 说明 4：一期**不设数据列表页**（`listFormData` 为业务集成消费通道，见 §11 T-02）；管理页操作列无【数据】入口。

### 2.3 路由片段（冻结写法）

以下为冻结的路由声明口径（实现时按 `frontend/src/router/index.js` 现有 `routes` 数组风格追加，**不改变既有路由**）：

```text
{
  path: 'form/manage',
  name: 'FormManage',
  component: () => import('@/views/form/FormManageView.vue'),
  meta: { title: '表单管理', parent: '系统管理', group: '自定义表单',
          auth: 'SYS_FORM_ADMIN', moduleGroup: 'form' }
}
{
  path: 'form/create',
  name: 'FormCreate',
  component: () => import('@/views/form/FormFormView.vue'),
  meta: { title: '新建表单', parent: '系统管理', group: '自定义表单',
          auth: 'SYS_FORM_ADMIN', moduleGroup: 'form' }
}
{
  path: 'form/edit/:id',
  name: 'FormEdit',
  component: () => import('@/views/form/FormFormView.vue'),
  meta: { title: '编辑表单', parent: '系统管理', group: '自定义表单',
          auth: 'SYS_FORM_ADMIN', tab: 'name', moduleGroup: 'form' }
}
{
  path: 'form/design/:id',
  name: 'FormDesign',
  component: () => import('@/views/form/FormDesignView.vue'),
  meta: { title: '表单设计器', parent: '系统管理', group: '自定义表单',
          auth: 'SYS_FORM_ADMIN', tab: 'name', moduleGroup: 'form' }
}
{
  // 填写页（R06 定稿：独立路由）：登录即可（不设 meta.auth），归属越权由后端拒绝兜底；
  // 面包屑不设 parent/group（对齐 profile 先例）；query 参数 businessId 为业务集成承接位
  path: 'form/fill/:formId',
  name: 'FormFill',
  component: () => import('@/views/form/FormFillView.vue'),
  meta: { title: '填写表单', tab: 'name', moduleGroup: 'form' }
}
{
  // 数据详情页：登录即可 + 数据归属（本人或 SYS_FORM_ADMIN，后端 Service 层判定兜底）
  path: 'form/data/:id',
  name: 'FormDataDetail',
  component: () => import('@/views/form/FormDataDetailView.vue'),
  meta: { title: '表单数据详情', tab: 'name', moduleGroup: 'form' }
}
```

> 6 条路由均为 `HomeView` 布局壳的**扁平叶子路由**（无嵌套 children）；`form/edit/:id`、`form/design/:id`、`form/fill/:formId`、`form/data/:id` 四条动态路由设 `meta.tab: 'name'` 复用单标签，`form/manage`、`form/create` 两条静态路由不设。

---

## 3. 菜单装配说明（`HomeView.vue`）

> v1_5 待实现（F15-FE-009）：本节为菜单配置与 `activeMenu` 归约的冻结口径，实现改动集中在 `frontend/src/views/HomeView.vue` 的 `menus` 常量（现状约 263~386 行）与 `activeMenu` 计算属性（现状约 418~460 行）。

### 3.1 菜单结构

「系统管理」下新增**【自定义表单】二级分组**（与【流程管理】【编号机制】等分组并列，建议位置置于「流程管理」分组之后；实现时按现有 `menus` 数组顺序追加、**不重排既有菜单项**）：

```text
系统管理（一级菜单，auth: ''）
├─ 流程管理 / 通知待办 / 附件管理 / 权限管理 / 码值管理 / 定时任务 / 日志管理 / 后台管理 / 多语言管理（现状不变）
└─ 自定义表单（二级分组，auth: ''）                        ← v1_5 新增
   └─ 表单管理（叶子，path: /form/manage，auth: 'SYS_FORM_ADMIN'，moduleGroup: 'form'）
```

菜单片段（冻结写法，对齐「编号机制」单叶子分组先例——组 `auth` 下放由叶子各自声明）：

```text
{
  title: '自定义表单',
  auth: '',
  children: [
    { title: '表单管理', path: '/form/manage', auth: 'SYS_FORM_ADMIN', moduleGroup: 'form' }
  ]
}
```

- 分组下唯一叶子即**管理入口**；设计器页（`/form/design/:id`）与元数据表单页（`/form/create`、`/form/edit/:id`）为管理页子路由，**不单设菜单**，经 `activeMenu` 归约保持「表单管理」高亮。
- 叶子菜单声明 `moduleGroup: 'form'`（模块组关闭时菜单隐藏，对齐数据源管理 / 公式定义器管理先例）。
- **填写页（`/form/fill/:formId`）与数据详情页（`/form/data/:id`）不设独立菜单**：数据面为登录即可页面，消费对象不限于持 `SYS_FORM_ADMIN` 的管理员；进入方式见 §3.4。

### 3.2 `activeMenu` 前缀归约（含写法约束）

现状 `activeMenu` 计算属性用于把不在菜单中的子路由归约到对应列表页菜单项以保持高亮。v1_5 需**补一条归约**：

| 归约分支 | 归约结果 | 覆盖子路由 |
| --- | --- | --- |
| `path.startsWith('/form/')` | `/form/manage` | `form/create`、`form/edit/:id`、`form/design/:id`、`form/fill/:formId`、`form/data/:id` |

冻结写法（片段，实现时落到 `activeMenu` 内）：

```text
// 自定义表单子路由（/form/create、/form/edit/:id、/form/design/:id、/form/fill/:formId、
// /form/data/:id）归约到 /form/manage 保持菜单高亮；/form/manage 自身命中本分支返回自身、无副作用
// ⚠ 写法约束：必须带尾部斜杠写作 '/form/'——'/formula'.startsWith('/form') 为 true，
// 若简写为 '/form' 会把公式定义器管理页误归约到 /form/manage、误高亮（同类教训先例：
// '/process/ 分支必须写在 /processTemplate/、/processExample/ 之后'，见 HomeView 现状注释）
if (path.startsWith('/form/')) return '/form/manage'
```

- **与 `/formula` 的关系**：本分支带尾部斜杠后，`'/formula'.startsWith('/form/')` 为 `false`，与既有 `/formula` 归约分支（`startsWith('/formula')`）**天然互斥、无顺序约束**——但带斜杠写法为冻结口径，不得简写；放置位置建议紧随既有 `/formula` 归约分支之后（可读性）。
- `/form/fill/:formId`、`/form/data/:id` 归约到 `/form/manage` 后：持 `SYS_FORM_ADMIN` 的用户命中菜单项正常高亮；普通登录用户（经业务链接进入填写页）菜单无对应项、不高亮——**已知可接受行为**（该两页本就不属于管理菜单体系）。

### 3.3 权限显隐与 `SYS_ADMIN` 超管短路

- 菜单过滤走既有 `filterMenuNode`：`node.auth && !hasAuth(node.auth)` 返回 `null`；模块组关闭（`moduleGroup` 命中）同样过滤；分组下无可见子项时整组隐藏——无 `SYS_FORM_ADMIN` 且非 `SYS_ADMIN` 的账号不可见【自定义表单】分组，直访管理路由被既有路由守卫按 `meta.auth` 重定向首页。
- `hasAuth` 对 `SYS_ADMIN` **超管短路**：持有 `SYS_ADMIN` 的用户天然可见全部管理页面与菜单（对齐 `BaseResolver` 中 `SYS_ADMIN` 自动放行的后端语义）。
- 页面内按钮显隐另用 `hasAuth([...])` 预判（如管理页【新建】、详情页【删除】的身份预判），**均不替代后端鉴权**。

### 3.4 填写页与数据详情页的进入方式（R06 定稿）与内嵌复用

**R06 路由形态裁决（本分册正式定稿，2026-11-23）**：表单填写页挂载形态 = **独立路由 `/form/fill/:formId`** + **渲染器组件可供业务系统内嵌复用**。两种消费方式并存：

1. **独立路由（本工程内）**：管理页行【填写】进入（管理员）；填写页保存成功后【查看数据】跳数据详情页；业务系统可拼接 `/form/fill/{formId}?businessId={业务对象ID}` 链接由登录用户直接进入（链接进入的提交归属 = 会话用户，与页面进入方式无关）。
2. **组件内嵌（业务系统页面）**：业务页直接引入 `FormRenderer` 组件（经 `components/index.js` 统一出口），以 `props.schema`（经 `getFillSchema(formId)` 取当前发布版本 Schema）+ `props.mode="'edit'"` + `v-model` 挂载，提交由宿主页调 `saveFormData`（`businessId` 传业务对象 ID）——渲染器只解释 Schema、不发请求（功能详细设计 §4.2），数据面鉴权（登录即可 + 归属）由后端接口兜底。

- 填写页与数据详情页为**数据面页面**，不挂管理菜单、不设 `meta.auth`（§2.2 说明 3）；数据归属与三种拒绝语义的权威口径见功能详细设计 §12.2（本分册为 UI 承接，结论与该口径兼容——功能详细设计 §12.7 第 1 条「R06 的 UI 半边」随本节收口）。

---

## 4. 表单设计器专章

> v1_5 待实现（F15-FE-008 设计器组件、F15-FE-009 页面接入；画布分层形态参照 v1.7 流程设计器先例 `frontend/src/components/process/designer/`——物料面板 / 画布容器 / 配置弹窗分层）。
> 设计器页路由 `form/design/:id`（独立整页路由，区别于 v1.7 流程模板「表单页内嵌画布」形态——表单元数据与 Schema 编排分离为两个页面，元数据在 §7 表单页维护、设计器页专注 Schema 编排）。

### 4.1 依赖与加载策略（R07 已收口：零新依赖）

| 项 | 约定 |
| --- | --- |
| 拖拽依赖 | **一期不引入任何拖拽依赖（实现期裁决，R07 收口 CLOSED）**：拖拽放置与排序基于原生 HTML5 DnD（dragstart/dragover/drop + `elementFromPoint`/`closest` 落点解析）。裁决理由——画布 DOM 由配置态渲染器（`FormRenderer` 内部递归渲染）拥有，SortableJS 直接改 DOM 会与 Vue 状态失同步、vuedraggable 无法接管渲染器内部列表，原生 DnD 零依赖同时彻底消除新依赖体积与 Vite 5 兼容风险（R07 验收口径）；本行替代 V1.0 的「一期必需引入 vuedraggable / SortableJS」倾向登记 |
| Monaco Editor | **一期不引入**（R02 已裁决一期无代码编辑器；设计器无代码块编辑面板，§4.6 响应函数占位不提供配置入口） |
| Ajv | **一期不引入**（R01 倾向自研轻量结构校验器：校验规则 = 功能详细设计 §2.2 资源限制常量 + §3 DSL 合法性，不需要完整 JSON Schema Draft 校验器；随 F15-BE-001 / F15-FE-002 编码复核） |
| 引入方式 | 设计器组件仅在 `form/design/:id` 路由被访问时随路由分块懒加载（`component: () => import(...)` 既有写法天然分块），不进主包；`npm run build` 验证 chunk 体积（R07 验收口径，F15-FE-008 已验证：设计器独立 chunk 约 54KB / gzip 约 17KB，零新依赖、无新增 chunk 体积异常，主包 chunk 告警为 element-plus 既有口径不变） |

### 4.2 三区域布局

设计器页整页为「顶部操作区 + 三区域主体」布局：

```text
| ← 返回 | 表单名称（只读）· 编码（只读）· 状态[草稿|已发布] · 版本 Vn    保存草稿  预览  导入  导出  版本  发布 （el-card #header .header-btns）|
|--------------------------------------------------------------------------------------------------------------------------------------|
| 物料栏（240px，可折叠）    |  设计画布（自适应宽度，内部滚动）                    |  右侧面板（320px，el-tabs）         |
| ┌────────────────────┐   |  ┌────────────────────────────────────────────┐  |  ┌───────────────────────────┐  |
| │ ▼ 数据（12）        │   |  │ 申请人*        [________________]           │  |  │ [ 属性 ] [ 规则 ]          │  |
| │  [单行文本][多行文本]│   |  │ 申请日期*      [____2026-11-23____]         │  |  │───────────────────────────│  |
| │  [数字][选择框]     │   |  │ 采购明细（明细表）                          │  |  │ 属性面板（选中字段）       │  |
| │  [下拉选择器][日期] │   |  │  ┌──────────────────────────────────────┐  │  |  │  名称     [申请人      ]  │  |
| │  [开关][级联选择器] │   |  │  │ 商品名称｜数量｜单价｜金额｜ 操作     │  │  |  │  字段码   [applicant   ]  │  |
| │  [公式计算][数据源] │   |  │  │ [＋ 添加一行]                        │  │  |  │  必填     [✓]             │  |
| │  [地址本][附件]     │   |  │  └──────────────────────────────────────┘  │  |  │  说明文本 [____________]  │  |
| │ ▼ 内容（4）         │   |  │ 合计金额       [自动计算]                   │  |  │  默认值   [____________]  │  |
| │  [明细表][表格]     │   |  │ 付款方式*      ( ) 现金  (•) 转账           │  |  │  ── 扩展属性 ──           │  |
| │  [文本][图片]       │   |  │ 银行账号       [____________]〈初始隐藏〉   │  |  │  （按物料 propSchema      │  |
| │ ▼ 布局（2）         │   |  └────────────────────────────────────────────┘  |  │    动态渲染，见 §4.5）    │  |
| │  [分组][栅格]       │   |  画布工具条：撤销  重做  清空                     |  │ 规则面板（选中字段触发源） │  |
| │ （控制类不展示）     │   |  选中组件悬浮工具条：复制  删除                   |  │  规则列表 + [＋ 添加规则]  │  |
| └────────────────────┘   |                                                  |  └───────────────────────────┘  |
|--------------------------------------------------------------------------------------------------------------------------------------|
| 提示条：物料卡片点击插入画布末尾或拖拽至目标位置；同层拖拽排序；点击选中高亮并与右侧面板联动；容器组件（分组/栅格/明细表）支持嵌套放置        |
```

- **顶部操作区**（`el-card` `#header` 内 `.page-header > .header-btns`，不用底部操作区）：【返回】（关标签回列表页）、【保存草稿】【预览】【导入】【导出】【版本】【发布】；表单名称 / 编码 / 状态 / 当前版本号为只读回显（元数据在 §7 表单页维护）。
- **三区域**：左物料栏（固定宽约 240px，可折叠）、中设计画布（自适应，配置态渲染器挂载）、右属性 + 规则面板（`el-tabs` 两个页签）。
- **脏数据保护**：画布任何结构改动置 `dirty` 标记；【返回】与路由离开时二次确认「表单设计已修改，确认离开？」（对齐 v1_7 设计器离开保护口径）。

### 4.3 物料栏与物料卡片

物料栏按 `category` 分组展示物料卡片（卡片 = 图标 + 中文名称），**点击插入画布末尾或拖拽至画布目标位置**两种放置方式并存。物料全集 = 功能详细设计 §1.3 R03 定稿清单（12 数据 + 4 内容 + 2 布局）；**控制类权限组件（type=permission）仅协议位——不注册物料、物料栏不展示**（功能详细设计 §1.3）。

| 分组 | 物料（type） | 卡片图标建议 | 画布放置约束 |
| --- | --- | --- | --- |
| 数据（DATA，12） | 单行文本 `input`、多行文本 `textarea`、数字 `number`、选择框 `choice`、下拉选择器 `select`、日期 `date`、开关 `switch`、级联选择器 `cascade`、公式计算 `formula`、数据源 `datasource`、地址本 `addressbook`、附件 `attachment` | 通用编辑/选择/日历类图标 | 可放置于画布顶层、group/grid 内、**明细表行内**（行内仅数据类组件，R04） |
| 内容（CONTENT，4） | 明细表 `detailTable`、表格 `table`、文本 `text`、图片 `image` | 表格/段落/图片类图标 | 顶层或 group/grid 内；**明细表不可放入明细表或任何容器行内**（R04） |
| 布局（LAYOUT，2） | 分组 `group`、栅格 `grid` | 区块/栅格类图标 | 顶层或相互嵌套一层（容器链深度受 `MAX_NEST_DEPTH=3` 约束，超限拖拽拦截并提示） |
| 控制（CONTROL） | 权限组件 `permission` | — | **物料栏不展示**（协议位，运行时随 v1.8 BPM 消费） |

- 物料卡片入画布时自动生成默认名称（组件中文名 + 序号，如「单行文本1」）与默认字段码（`{type}_{序号}`，如 `input_1`），两者均可在属性面板修改。
- 字段总数达 `MAX_FIELD_COUNT=200` 时物料卡片置灰 + 提示「字段总数已达上限」（保存期服务端权威校验，画布侧为交互预判）。

### 4.4 画布交互

| 交互 | 触发方式 | 行为约定 |
| --- | --- | --- |
| 拖拽放置 | 物料卡片拖入画布（**原生 HTML5 DnD**，实现期裁决见 §4.1——R07 收口零新依赖） | 落点生成新字段对象（默认名称 + 默认字段码），推入目标层级的 `fields[]` / `children[]` / 列 `fields[]`；落点解析：`elementFromPoint` → `closest('.renderer-field-item[data-field]')`（明细表设计骨架行内单元格回落 `[data-field]`）→ 由外到内字段码链反查扁平索引条目（行内字段码可与主表同码，码链消歧），目标矩形上半/下半判定同层 before/after、悬于容器中部（15%~85%）判定拖入容器成为其子字段；悬停以插入位置指示条可视化 |
| 点击插入 | 物料卡片单击 | 插入画布末尾（或当前选中容器的 `children` 末尾——选中容器时插入其内部） |
| 排序 | 同层级拖拽 | 仅调整同层级字段顺序（含容器内与明细行内），跨层拖拽由拖拽目标位置语义决定（拖入容器即成为其子字段） |
| 选中态 | 点击画布组件 | 选中高亮（描边）+ 右侧属性面板联动（属性 Tab 加载该字段属性、规则 Tab 加载该字段触发源规则）；点击画布空白处取消选中（属性 Tab 回落画布级摘要） |
| 复制 | 选中组件悬浮工具条【复制】 | 深拷贝字段对象（含 `props` / `rules`；容器含 `children` 递归拷贝）并**重新分配字段码**（避免同层唯一冲突），插入原字段之后 |
| 删除 | 选中组件悬浮工具条【删除】或 `Delete` 键 | 容器组件删除时二次确认「删除分组将同时删除其内部 N 个字段」（级联删除 `children`）；明细表删除同口径 |
| 撤销 / 重做 | 画布工具条【撤销】【重做】 | **Schema 快照栈**（内存栈，每次结构变更压栈）：仅作用于**本次设计会话的草稿 Schema**——发布产生的不可变版本不进快照栈、离开页面快照丢弃、不做服务端历史（边界：撤销重做永不跨发布边界，已发布版本不可变原则不受影响） |
| 清空 | 画布工具条【清空】 | 二次确认后清空全部字段（等价一次可撤销的结构变更，走快照栈） |
| 容器嵌套放置 | 拖拽组件至分组 / 栅格 / 明细表内部 | 分组 / 栅格：可嵌套数据 / 内容组件与相互嵌套一层（容器链深度 ≤ `MAX_NEST_DEPTH=3`，超限拦截提示）；**明细表行内仅允许数据类组件**（含行内 formula），拖入内容 / 布局 / 明细表组件时拦截并提示「明细表行内仅支持数据类组件」（R04：一期一层明细表） |
| 静态初始隐藏可视化 | 画布渲染 | `props.hidden: true` 的字段在画布中以半透明 + 〈初始隐藏〉角标展示（配置态可视化，运行态语义见 §5.3） |
| 必填可视化 | 画布渲染 | `props.required: true` 的字段 label 前渲染红色星号（与运行态一致） |

- **画布 = 配置态渲染器**（`FormRenderer` `mode="design"`）：渲染物料占位态、数据绑定旁路（无真实数据读写，功能详细设计 §4.1）；设计器只编辑 Schema、渲染器解释 Schema，设计器**输出统一 Schema、不生成 Vue 页面代码**（要点第 2 步原则，功能详细设计 §4.10 分离边界）。
- 设计器加载口径（对齐 API 分册 §3.4 消费规则）：`loadForm(fieldId)` 后画布以 `draftSchema` 为编辑对象；`draftSchema` 为 null（已发布未再编辑）时以 `publishedSchema` 初始化画布，首次【保存草稿】（`updateForm` 携带 `schema`）由服务端自动创建草稿行；两者均 null（新建表单未编排）时画布为空白。

### 4.5 属性面板字段规格

属性面板（右侧 `el-tabs`【属性】页签）按**选中物料的 `propSchema`**（物料七件套之一，功能详细设计 §1.1）动态渲染属性表单；未选中字段时显示画布级摘要（表单名称 / 编码 / 状态只读 + 引导文案「请从左侧物料栏添加组件」）。

#### 4.5.1 固定属性项（按组件类别裁剪，功能详细设计 §1.1 定稿）

| 属性项 | 配置键 | 控件 | 适用类别 |
| --- | --- | --- | --- |
| 名称 | `label` | `el-input` | 全部（DATA / CONTENT / LAYOUT / detailTable；分组组件名称即区块标题） |
| ID（字段码） | `field` | `el-input` + 格式与唯一性校验：`[a-zA-Z_][a-zA-Z0-9_]*`、同层级唯一（明细行内字段码可与主表同码但行内唯一，功能详细设计 §2.1） | DATA 全部 12 组件 + `detailTable` |
| 是否必填 | `required` | `el-switch` | DATA 全部 12 组件；`detailTable`（语义 = 至少一行，按 `rowMin`） |
| 说明文本 | `tooltip` | `el-input`（运行态渲染为字段旁问号提示） | DATA 全部 12 组件、CONTENT 全部 4 组件、`detailTable` |
| 默认值 | `defaultValue` | 控件随物料 `dataType` 选型（STRING 文本 / NUMBER 数字 / BOOLEAN 开关 / DATE 日期选择 / ARRAY·OBJECT 一期以 JSON 文本承载）；`addressbook` / `attachment` 等复杂组件一期留空（由物料默认值生成器兜底） | DATA 全部 12 组件 |

> CONTENT（除明细表）无 ID / 必填 / 默认值（不承载数据）；LAYOUT 仅名称（无 ID / 必填 / 说明 / 默认值）——与功能详细设计 §1.1 固定属性项裁剪表逐字一致。

#### 4.5.2 逐组件扩展属性项登记表（与《自定义表单组件.md》表格、功能详细设计 §1.3 三方一致）

**数据类（12，category=DATA）**

| type | 名称 | 扩展属性项 | 控件与取值域 |
| --- | --- | --- | --- |
| `input` | 单行文本 | 最大长度 `maxlength`、是否密码 `password` | `maxlength`：`el-input-number`（默认 200，应用层上限 `MAX_TEXT_LENGTH=2000`）；`password`：`el-switch`（掩码输入，R03 密码并入本属性） |
| `textarea` | 多行文本 | 最大长度 `maxlength` | `el-input-number`（默认 200，上限同上） |
| `number` | 数字 | 数字类型 `numberType`、精度 `precision`、最大值 `max`、最小值 `min`、允许负数 `allowNegative`、分隔符 `separator` | `numberType`：`el-select`（INTEGER 整数 / DECIMAL 小数 / PERCENT 百分比 / AMOUNT 金额）；`precision`：`el-input-number`（0~6）；`max` / `min`：`el-input-number`；`allowNegative`：`el-switch`；`separator`：`el-select`（NONE 无 / THOUSAND 千分位 / TEN_THOUSAND 万分位 / HUNDRED_MILLION 亿分位） |
| `choice` | 选择框 | 单多选 `multi`、排列方式 `layout`、选择项 `options[{label, value}]` | `multi`：`el-switch`；`layout`：`el-radio-group`（横排 / 竖排）；`options`：明细表（显示值 label / 实际值 value，增删行；一期选项静态配置，`optionsSource=dict` 协议位一期不开放） |
| `select` | 下拉选择器 | 单多选 `multi`、选择项 `options[{label, value}]` | `multi`：`el-switch`；`options`：明细表（显示值 / 实际值，同上） |
| `date` | 日期 | 类型 `dateType`、显示格式 `format` | `dateType`：`el-select`（DATE 日期 / TIME 时间 / DATETIME 日期时间）；`format`：`el-select` 常用格式（dayjs token，按 `dateType` 联动可选集：`YYYY-MM-DD` / `HH:mm:ss` / `YYYY-MM-DD HH:mm:ss`，对齐公共日期选择器口径；值以格式化字符串落库） |
| `switch` | 开关 | 【开】显示文本 `onText`、【关】显示文本 `offText` | 均 `el-input`（默认「开」/「关」） |
| `cascade` | 级联选择器 | 父数据项 `parentField`、选择项 `options[{label, value, showCondition:{operator, value}}]`、单多选 `multi` | `parentField`：`el-select`（选项 = 表单内**已配置的数据类字段码**，排除自身——结构校验器同口径）；`options`：明细表（显示值 / 实际值 / **显隐条件**——`showCondition` 可空，配置形态 = 条件 DSL：operator 下拉（§3.2 十项全集）+ value 输入，R08 一期 DSL 条件形态，不走逐项 simulate）；`multi`：`el-switch`；组件内置行为：父项值清空时清空本字段值（不经规则 DSL 配置） |
| `formula` | 公式计算 | 公式 `expression`、期望返回类型 `expectedReturnType` | `expression`：**只读输入框 + 【设计公式】按钮**，打开 `FormulaDesigner` 公共弹窗（复用 F14 契约：`v-model:expression`、`:available-vars` 传**表单字段码声明**——全部已配置数据类字段 + 明细表数组变量支持 `${list.field}` 投影、`:expected-return-type` 随 `expectedReturnType`；不允许在输入框直接手改公式，对齐 v1_7 公式编辑交互先例）；`expectedReturnType`：`el-select`（NUMBER / STRING / BOOLEAN / DATE）；值以服务端重算为准（ADR-09） |
| `datasource` | 数据源 | 数据源编码 `datasourceCode`、单多选 `multi` | `datasourceCode`：`el-select`（选项来自 `api/datasource.js#getDatasourceOptions()` 启用数据源清单，存编码不存快照）；`multi`：`el-switch` |
| `addressbook` | 地址本 | 单多选 `multi`、可选类型 `orgType`、是否显示角色页签 `showRole` | `multi`：`el-switch`；`orgType`：`el-select`（对齐 AddressBook 契约全集：ALL / ORG / DEPT / ORGDEPT / PERSON / POST / PERSONPOST / ORGDEPTPOSTPERSON）；`showRole`：`el-switch`（复用 `AddressBook.vue` 不改既有契约） |
| `attachment` | 附件 | 数量上限 `maxCount`、允许上传文件格式 `fileType` | `maxCount`：`el-input-number`；`fileType`：`el-input`（逗号分隔后缀，空 = 不限制；复用 `Attachment.vue` 契约 `multi` / `fileType`，附件实体落 `sys/att`、表单数据存引用） |

**内容类（4，category=CONTENT）**

| type | 名称 | 扩展属性项 | 控件与取值域 |
| --- | --- | --- | --- |
| `detailTable` | 明细表 | 行内字段 `children[]`、行数下限 `rowMin`、行数上限 `rowMax`、统计行 `summary[{field, fn}]` | `children`：**不由属性面板配置**——行内字段经画布嵌套放置直接编辑（§4.4 容器嵌套）；`rowMin` / `rowMax`：`el-input-number`（`rowMax ≤ MAX_DETAIL_ROWS=200`，超限提示）；`summary`：明细表（统计字段 `el-select`——行内数值类字段码 / 统计方式 `el-select`：sum / avg / count） |
| `table` | 表格 | 列定义 `columns[{label, prop, width}]`、静态数据 `rows` | `columns`：明细表（列标题 / 字段名 / 列宽）；`rows`：JSON 文本域（前端做 JSON 合法性预检；内容型只读表格，不进数据载荷） |
| `text` | 文本 | 文本内容 `content` | 多行文本域（静态说明文字） |
| `image` | 图片 | 图片地址 `src`、展示宽度 `width` | `src`：`el-input`；`width`：`el-input-number`（px） |

**布局类（2，category=LAYOUT）**

| type | 名称 | 扩展属性项 | 控件与取值域 |
| --- | --- | --- | --- |
| `group` | 分组 | 无（`children[]` 由画布嵌套承载） | 区块标题 = 名称（固定属性项） |
| `grid` | 栅格 | 列配置 `columns[{span, fields[]}]` | 属性面板维护**列数与各列 span**（24 栅格制，各列 span 合计超出 24 时提示）；列内字段经画布嵌套放置承载 |

**控制类（1，category=CONTROL，仅协议位）**：`permission` 权限组件不注册物料、物料栏不展示、不进 Schema `fields`，属性面板无配置项（消费形态随 v1.8 BPM 由流程侧下发字段权限覆盖，功能详细设计 §1.3 / §6.6）。

### 4.6 规则配置面板

右侧 `el-tabs`【规则】页签 = **以当前选中字段为触发源（`triggerField`）的规则列表**；规则宿主存储于字段对象 `rules[]`（随版本内嵌，ADR-06）。四类规则与一期配置口径：

| 规则类别 | 可视化配置形态 | 一期口径 |
| --- | --- | --- |
| 显隐规则 | 动作类型 `show`（显示）/ `hide`（隐藏） | 对称显隐建议成对配置（先例：采购示例 rule_001/002、rule_003/004）；`hide` 隐藏不删值（§5.3） |
| 联动规则 | 动作类型 `setValue`（赋值）/ `clearValue`（清空值） | `setValue` 的 value 为字面量或 `${字段码}` 引用（联动赋值）；`onSubmit` / `onValidate` 事件下禁用（§3.4 载荷约束） |
| 动态校验规则 | 动作类型 `setRequired`（动态必填）/ `setValidate`（条件范围 / 条件正则覆盖） | 与字段静态校验器合并裁决（服务端权威） |
| 响应函数（占位） | **不提供配置入口** | R02 已裁决一期不落地 JS 执行引擎，代码块组件协议位二期；面板不渲染任何代码编辑区（Monaco 不引入，§4.1） |

**规则编辑表单**（【添加规则】/ 行内【编辑】打开，el-dialog 弹窗）：

| 配置项 | 配置键 | 控件 | 约束 |
| --- | --- | --- | --- |
| 触发事件 | `event` | `el-select` | 可配置六类：`onChange`（主力）/ `onLoad` / `onAdd` / `onDelete` / `onSubmit` / `onValidate`；**`onFocus` / `onBlur` 一期不开放配置**（仅事件透传，防规则风暴，功能详细设计 §3.4） |
| 触发字段 | `triggerField` | 只读回显 | = 规则面板当前选中字段（规则宿主即触发源字段对象） |
| 条件（可开关「无条件」） | `condition{operator, value}` | 「无条件」开关 + `operator` 下拉 + `value` 控件 | `operator` 十项全集（中文标签）：等于 `equal` / 不等于 `notEqual` / 大于 `gt` / 大于等于 `gte` / 小于 `lt` / 小于等于 `lte` / 属于 `in` / 不属于 `notIn` / 为空 `empty` / 不为空 `notEmpty`；`value` 控件随 operator：标量输入（equal/notEqual/gt/gte/lt/lte，选型随触发字段数据类型——数字 / 日期字符串 / 文本）、多值编辑（in/notIn，标签式多值输入）、无值控件（empty/notEmpty 隐藏 value） |
| 动作明细表 | `actions[]` | 明细表（动作类型 / 目标字段 / 值） | 动作类型 `el-select` 六项：显示 `show` / 隐藏 `hide` / 赋值 `setValue` / 清空值 `clearValue` / 动态必填 `setRequired` / 动态校验覆盖 `setValidate`；目标字段 `el-select`（选项 = 表单内全部已配置字段；**明细表触发源（onAdd/onDelete/明细行规则）的动作目标限行内字段码**）；值列随动作类型：`show`/`hide`/`clearValue` 无值、`setRequired` 开关（boolean）、`setValue` 文本输入 + 字段码引用带入、`setValidate` 子表单（min / max / pattern / message） |

**规则列表管理与循环防护提示**：

- 规则列表行内展示「事件 + 条件摘要 + 动作摘要」，行内【编辑】【删除】；规则宿主字段被删除时其 `rules[]` 随字段级联删除（删除确认文案提示关联规则数）。
- **上限提示文案**（面板底部固定说明，常量为功能详细设计 §2.2 草案值、编码期 F15-BE-001 可复核，提示文案同步）：「单表单规则总数上限 100（`MAX_RULE_COUNT`）；规则链深度上限 5 层（`MAX_RULE_CHAIN_DEPTH`）；单次交互动作执行上限 50 次（`MAX_RULE_EXECUTIONS_PER_CYCLE`），超限熔断并在控制台告警」。添加规则达 `MAX_RULE_COUNT` 时【添加规则】按钮禁用 + 提示。
- **边界**：级联选择器的选项显隐条件**不在规则面板配置**——在级联物料属性面板 `options` 明细表内逐项配置 `showCondition`（R08 条件 DSL，§4.5.2）；规则面板与属性面板职责以此分界。

### 4.7 预览

- 【预览】按钮打开大尺寸弹窗（`el-dialog`，建议全屏或 `width ≥ 900px`），挂载 `FormRenderer` `props.mode="'edit'"`、`props.schema` = 当前画布 Schema（未落库、不进快照栈、关闭弹窗即丢弃）。
- 弹窗内提供「预演字段权限」开关（传 `props.fieldPermissions` 演示 readonly 置灰 / hidden 不渲染效果——为 v1.8 BPM 外部权限覆盖预留的预演位）。
- 弹窗内【校验】按钮仅触发渲染器本地 `expose.validate()` 展示校验结果（本地预演），**不发数据面请求**（服务端权威校验在保存 / 发布 / 提交链路）。

### 4.8 Schema 导入导出

| 入口 | 行为约定 |
| --- | --- |
| 导出 | 【导出】按钮将当前画布 Schema 序列化为 JSON 文件下载（文件名 `{formCode}.json`）；草稿导出时协议头 `version` 为 null（版本号仅发布动作分配，功能详细设计 §2.1）；Schema 体积超 `MAX_SCHEMA_JSON_LENGTH=512KB` 时提示（应用层口径，与保存期校验同常量） |
| 导入 | 【导入】按钮打开弹窗：`el-input type="textarea"` 粘贴 JSON 文本 → **前端轻量结构校验**（JSON.parse + 协议头结构核对 + `fields[]` 递归结构 / type 白名单核对——自研轻量校验器，不引入 Ajv，§4.1）→ 通过则提示「导入将覆盖当前画布内容，未保存的改动将丢失」二次确认后**整表替换画布**；失败时逐条展示校验原因（对齐 `CS_FORM_SCHEMA_INVALID` 逐条中文原因形态） |
| 落库边界 | 导入**不直接落库**——仅替换画布内容，须【保存草稿】/【发布】后才经 `updateForm` / `publishForm` 进入服务端；服务端保存期仍有权威结构校验（`CS_FORM_SCHEMA_INVALID` / `CS_FORM_SCHEMA_LIMIT_EXCEEDED`，API 分册 §3.5）；协议头 `formId` 缺省允许（导入即新表单场景） |

### 4.9 发布入口与版本列表 / 回滚

| 入口 | 行为约定 |
| --- | --- |
| 发布（设计器页【发布】） | 前端预判：无草稿行（已发布未再编辑，`draftSchema` 为 null 且画布无未保存改动）时按钮置灰 + 提示「无草稿可发布，请先修改表单」（对应 `CS_FORM_NO_DRAFT` 非法流转预判）；**画布有未保存改动（dirty）时发布入口提示「请先【保存草稿】再发布」**（避免发布与画布不一致的服务端存量草稿，实现期预判细化）；可发布时弹**发布确认弹窗**：「发布将把当前草稿冻结为不可变新版本（版本号 Vn+1），已发布版本不可再修改；在途数据继续使用原版本，新填写使用新版本。确定发布？」→ 确认调 `publishForm({ fieldId })` → 成功提示新版本号（响应 `{ fieldVersionId, fieldVersion }`）并刷新画布状态（草稿行已冻结、后续编辑产生新草稿）；发布失败按校验链逐条展示（P1 `CS_FORM_NO_DRAFT` / P2 `CS_FORM_SCHEMA_INVALID` 中文原因列表 / P3 `CS_FORM_SCHEMA_LIMIT_EXCEEDED` / P4 `CS_FORMULA_*` 透传，API 分册 §3.8） |
| 版本列表（设计器页【版本】+ 管理页行【版本】共用弹窗） | 打开版本列表弹窗（`getFormVersions(fieldId)`，响应按 `fieldVersion` 降序）：列 = 版本号 / 状态（`DRAFT` 草稿 tag info（至多一行）/ `PUBLISHED` 当前发布 tag success / `DEPRECATED` 已替代 tag info）/ 发布时间（草稿行显示「-」）/ 创建时间 / 操作（回滚——仅 `PUBLISHED` / `DEPRECATED` 行可回滚，`DRAFT` 行禁用）；**版本行不携带 Schema 内容，弹窗不做历史版本内容预览 / 对比**（API 分册 §3.9 / §10-6 登记） |
| 回滚（版本弹窗行【回滚】） | 确认文案：「回滚将复制该历史版本内容发布为新版本（Vn+1），不影响既有草稿与在途数据；确定回滚？」→ 确认调 `rollbackForm({ fieldId, fieldVersionId })` → 成功提示新版本号并刷新版本列表与画布状态（回滚 = 重发布副本，不产生指针回拨，功能详细设计 §5.1 原则 5） |

### 4.10 设计器与渲染器分离边界（验收红线）

- 设计器只编辑 Schema（物料 / 属性面板 / 规则面板 / 快照栈全部是 Schema 编辑器），渲染器解释 Schema——设计器不写任何渲染逻辑、渲染器不含任何物料知识，二者只经 `componentRegistry` 交互（功能详细设计 §1.2「渲染器与设计器只经 registry 取物料」）。
- 画布配置态 = `FormRenderer` `mode="design"` 内部复用（物料占位态、数据绑定旁路）；设计器**输出统一 Schema、不生成 Vue 页面代码**（要点第 2 步原则）。
- 撤销重做仅作用于草稿（§4.4）；发布后版本不可变（发布 = 冻结，修正走「再编辑 → 新版本」）。

---

## 5. 渲染器三形态消费方式

> 渲染器契约语义权威 = 功能详细设计 §4（`FormRenderer.vue`，F15-FE-005 落地）；本节为 UI 消费承接。

### 5.1 三形态与消费位置

| mode | 形态 | 消费位置 | UI 表现要点 |
| --- | --- | --- | --- |
| `design` | 配置（设计器画布） | 设计器画布（§4.4） | 物料占位态渲染、选中 / 拖拽插入与右侧面板联动、数据绑定旁路 |
| `edit` | 编辑（填写） | 填写页（§8.1）、设计器预览弹窗（§4.7）、业务系统内嵌填写 | 完整数据绑定 + 默认值初始化 + 规则引擎挂载 + 校验 |
| `detail` | 详情（只读） | 数据详情页（§8.2）、业务系统只读内嵌 | 全部字段只读回显；显隐规则照常求值（与提交时刻形态一致）；动态校验不执行 |

### 5.2 组件契约（props / v-model / 事件）

| 项 | 契约 | 宿主用法 |
| --- | --- | --- |
| `props.schema` | 版本 Schema 对象（功能详细设计 §2.1）；渲染器只解释 Schema、不感知表单元数据接口 | 填写页经 `getFillSchema(formId)` 获取；数据详情页取 `loadFormData` 响应 `schema`；设计器画布传画布 Schema |
| `props.mode` | `design` / `edit` / `detail`（默认 `edit`） | 三形态消费位置见 §5.1 |
| `props.modelValue` | 表单数据对象（v-model）：渲染器为受控组件，字段值变更经 `update:modelValue` 回传全量数据对象 | 填写页 `v-model="formData"` 宿主持值提交 |
| `props.fieldPermissions` | 字段级权限外部覆盖映射 `{ 字段码: editable/readonly/hidden }`，默认 null；与 Schema 内 `props.permission` 合并时外部覆盖优先 | 一期设计器预览预演位；v1.8 BPM 节点权限注入位 |
| `emits.update:modelValue` | 任意字段值变化回传全量数据对象 | — |
| `emits.change` | 单字段明细载荷 `{ field, value, oldValue }` | 宿主按需监听 |
| `expose.validate()` | 全量校验（基础校验 + 规则动态校验合并，本地预演），返回 `{ pass, errors: [{ field, ruleId, message }] }` | 填写页提交前预演（服务端权威校验经 `validateFormData`） |
| `expose.reset()` | 重置：清空后按物料默认值生成器重新生成默认值 | 填写页【重置】按钮 |
| `expose.getData()` | 取当前数据对象 | 宿主提交取值 |

- **事件分发**：字段组件只 emit 原始事件（onLoad / onChange / onFocus / onBlur / onAdd / onDelete / onSubmit / onValidate），统一进入渲染器内置事件总线，规则引擎（`components/form/rules/`，F15-FE-006）订阅同一事件流完成规则匹配与动作执行——**规则不写死在任何字段组件内**（架构原则 2）。
- **渲染器零业务规则**：渲染器不写任何具体业务规则，显隐 / 联动 / 校验全部交规则引擎；未知 type 渲染「组件未注册」占位块（不抛异常、不阻断整表，ADR-10 库内 Schema 最后防线）。

### 5.3 字段级权限渲染与「隐藏不删值」UI 语义

| 场景 | 渲染表现 | 值语义 |
| --- | --- | --- |
| `permission = readonly`（或外部覆盖 readonly） | 组件置灰只读（disabled + 视觉置灰），值照常回显 | 值仍随载荷提交（一期 save 为新增落库、无基线比对；实例更新的值变更拒绝语义挂二期，API 分册 §4.3 S7） |
| `permission = hidden` | **不渲染**（不占布局位） | 值不进入提交载荷（静态可知、前端一致裁剪）；提交与读取均被服务端裁剪（后端不信任前端兜底） |
| 规则动态隐藏（show/hide 动作） | 不渲染、不占布局位 | **隐藏不删值**（ADR-08 渲染层）：编辑过程中被隐藏字段的值保留于数据对象，条件恢复显示后**原值回显**；需要清值的业务场景必须显式配置 `clearValue` 动作（语义严格分离） |
| 规则动态隐藏 · 持久层 | — | 提交时服务端复算显隐规则，提交时刻判定隐藏的字段值**裁剪不落库**（主表顶层字段；明细行内显隐一期为渲染层语义、行数据按行原样落库，功能详细设计 §3.6 一期边界） |
| 规则动态隐藏 · 读取层 | — | 回显 / 详情响应不下发提交时刻被裁剪的字段——编辑回显与详情回显自然一致 |

- **详情态（`mode="detail"`）**：显隐规则照常求值（与提交时刻形态一致）、动态校验不执行、全部组件只读回显——数据详情页直接复用，无需另写只读页面。

---

## 6. 管理列表页（`/form/manage`）

### 6.1 页面元信息

| 项 | 值 |
| --- | --- |
| 路由 | `form/manage`（`meta.title: '表单管理'`） |
| 页面组件 | `views/form/FormManageView.vue` |
| 页面类型 | 列表页（三段式：筛选区 → 工具区 → 列表区） |
| 菜单入口 | 【系统管理】→【自定义表单】→【表单管理】 |
| 显示权限 | 叶子菜单与路由 `meta.auth` 绑定 `SYS_FORM_ADMIN`；列表查询经 `POST /define/list` 由后端鉴权兜底 |
| 页面样式 | 复用 `list-page.css`（`.filter-form` / `.list-toolbar` / `.sort-area` / `.sort-item` / `.sort-arrow` / `.list-pagination`），不在页面内重复定义 |

### 6.2 页面布局 ASCII 示意图

```text
| 表单编码 | 表单名称 | 状态(全部/草稿/已发布) | 启用(全部/启用/停用) |
| sort-area(创建时间↑↓)                          | 查询  新建 |
|--------------------------------------------------------------------------|
| 编码 | 名称 | 当前版本 | 状态 | 启用 | 创建时间 | 操作(设计/填写/版本/启停用/删除) |
|--------------------------------------------------------------------------|
| total, prev, pager, next, sizes                                          |
```

### 6.3 三段式规格

- **筛选区**：`el-form.filter-form` 一行四列（`el-col :span="6"`）——表单编码（模糊）、表单名称（模糊）、状态（静态选项「全部 / 草稿 DRAFT / 已发布 PUBLISHED」，默认「全部」）、启用状态（静态选项「全部 / 启用 / 停用」，默认「全部」——管理视角需同时看到停用表单以便排查填写入口关闭原因）；输入框回车触发查询，空值提交 `null`。
- **工具区**：左侧 `sort-area` 仅创建时间单条件排序（选中项 `.active` + `.sort-arrow` 显示 `↑`/`↓`，默认创建时间降序，`sortField=fieldCreateTime` 口径对齐 API 分册 §2.2）；右侧【查询】【新建】（新建 `hasAuth(['SYS_FORM_ADMIN'])` 预判显隐，跳 `form/create`）。**本页无批量操作**——分册未定义批量启停用 / 删除接口，不设复选框列（对齐 AGENTS §6「列表首列复选框（有批量操作时）」与公式 / 数据源管理页先例）。
- **列表区**：不展示主键 `fieldId`；末列操作列 `fixed="right"`；分页 `layout="total, prev, pager, next, sizes"`、`page-sizes [10,20,50,100]`，切页大小回第 1 页；数据经 `listForms`（`POST /define/list`，请求体 `{ body, pages, sortField, sortOrder }`，总数取 `metadata.total`）；列表行不携带 Schema 内容（API 分册 §3.2）。

### 6.4 列与操作列规格

| 列 | 字段 | 组件与规格 |
| --- | --- | --- |
| 编码 | `fieldFormCode` | 文本，溢出省略 |
| 名称 | `fieldFormName` | 文本，溢出省略 |
| 当前版本 | `fieldCurrentVersion` | 文本（整数版本号）；从未发布显示 `-` |
| 状态 | `fieldStatus` | `el-tag`：`DRAFT` 草稿 info / `PUBLISHED` 已发布 success |
| 启用 | `fieldEnable` | `el-tag`：启用 success / 停用 info |
| 创建时间 | `fieldCreateTime` | 格式化时间 |
| 操作 | — | 设计、填写、版本、启停用、删除（link 按钮，`fixed="right"`） |

操作列行为约定（页面动作 → 接口函数 → HTTP 方法承接见 §9.1）：

| 操作 | 行为与约束 |
| --- | --- |
| 设计 | 跳 `form/design/:id`（设计器页，复用单标签）——任意状态可用（新建草稿或继续编辑草稿） |
| 填写 | 跳 `form/fill/:formId`；**行内预判**：`fieldEnable === true` 且 `fieldCurrentVersion` 非空时可用，否则置灰 + `title` 提示「表单未发布或已停用，填写入口关闭」（对应 `CS_FORM_NO_PUBLISHED_VERSION` / `CS_FORM_DISABLED` 拒绝语义的前端预演，后端仍权威兜底） |
| 版本 | 打开版本列表弹窗（与设计器页共用，§4.9；含回滚入口） |
| 启停用 | 二次确认（停用提示「停用后填写入口与数据提交关闭，定义面与数据查询保留；确定停用？」）→ `enableForm({ fieldId, fieldEnable })`（PATCH）→ 刷新列表 |
| 删除 | 二次确认（提示「删除将同时删除该表单全部版本行，不可恢复；确定删除？」）→ `deleteForm(fieldId)`（DELETE）；存在实例数据时后端拒绝（`CS_FORM_HAS_DATA`，不提供级联删除）→ 错误提示「该表单存在实例数据，禁止删除；如需停用请使用启停用」（管理引导对齐 API 分册 §3.7） |

---

## 7. 表单元数据表单页（`form/create`、`form/edit/:id`）

### 7.1 页面元信息

| 项 | 值 |
| --- | --- |
| 路由 | `form/create`（新建）、`form/edit/:id`（编辑）——**共用 `FormFormView.vue`，按路由 path 区分模式**（先例 `number/create` 与 `number/edit/:id` 共用 `NumberFormView`） |
| 页面组件 | `views/form/FormFormView.vue` |
| 页面类型 | 表单页（`form-page.css` 规范） |
| 显示权限 | 路由 `meta.auth` 绑定 `SYS_FORM_ADMIN`（定义面动作） |
| 页面样式 | 复用 `form-page.css`（`.page-card` / `.page-header` / `.page-title` / `.header-btns` / `.page-form`） |

### 7.2 页面布局 ASCII 示意图

```text
| 关闭  保存  设计表单                              （el-card #header .header-btns）|
|------------------------------------------------------------------------------|
| 表单编码 [____________________]     | 表单名称 [____________________]         |
|------------------------------------------------------------------------------|
| 提示条：新建保存成功后可点击【设计表单】进入设计器编排表单结构与规则              |
```

### 7.3 字段与操作规格

| 项 | 规格 |
| --- | --- |
| 表单编码 | `fieldFormCode`，`el-input`，必填；**编辑态只读**（编码为保存后稳定标识、不可变更——`update` 接口不含 `fieldFormCode`，API 分册 §3.5；调整走删除重建）；保存期服务端全局查重（`CS_FORM_CODE_DUPLICATED`） |
| 表单名称 | `fieldFormName`，`el-input`，必填 |
| 布局 | 一行两字段（`label-width="40%"` + `el-col :span="12"`，对齐 AGENTS §6 表单页规范）；无长内容字段 |
| 新建模式 | `onMounted` 调 `api/common.js` 的 `getInitID()`（`GET /backend/common/initID`）取 32 位 ID 赋 `fieldId`；【保存】调 `saveForm({ fieldId, fieldFormCode, fieldFormName })`（schema 缺省，服务端以空白草稿初始化）；保存成功提示后跳转管理列表页（或停留并解锁【设计表单】） |
| 编辑模式 | 按路由 id 调 `loadForm(fieldId)` 回填元数据（只消费行字段，不消费 Schema 载荷）；【保存】调 `updateForm({ fieldId, fieldFormName })`（仅名称更新时不含 schema） |
| 按钮 | `el-card` `#header` 内 `.header-btns`：【关闭】（关标签回列表）、【保存】、**【设计表单】**（内嵌设计器入口：新建态先执行保存取得 `fieldId` 再跳 `form/design/:id`；编辑态未保存改动先保存再跳） |
| 错误提示 | `CS_FORM_*` 错误码以 message 前缀承载、逐条中文原因，经 `ElMessage` 直接展示 message（不做错误码翻译，§8.1 同口径） |

---

## 8. 填写页与数据详情页

### 8.1 填写页（`form/fill/:formId`）

| 项 | 规格 |
| --- | --- |
| 页面组件 | `views/form/FormFillView.vue` |
| 页面类型 | 表单页（编辑态渲染器承载，非表单元数据表单） |
| 访问口径 | 登录即可（R06 定稿；无 `meta.auth`，不设菜单，进入方式见 §3.4）；支持可选 query 参数 `businessId` |
| 初始化 | `onMounted`：调 `getInitID()` 预生成**数据行 `fieldId`**（填写页属新建资源，对齐 AGENTS §6 与 API 分册 §4.3）+ 调 `getFillSchema(formId)` 取当前发布版本 Schema（`{ fieldId, formId, businessId, formData, schema }` 就绪后挂载渲染器） |
| 前置拒绝空态 | `CS_FORM_NOT_FOUND`（表单不存在）/ `CS_FORM_DISABLED`（表单已停用——填写入口与数据提交关闭）/ `CS_FORM_NO_PUBLISHED_VERSION`（无已发布版本）→ 渲染器不挂载，页面渲染错误空态（`el-empty` + message 可读文案），「表单不存在或未发布，请联系表单管理员」 |
| 渲染 | `FormRenderer props.mode="'edit'"`、`:schema="schema"`、`v-model="formData"`；默认值由渲染器挂载时按物料默认值生成器初始化 |
| 按钮 | `el-card` `#header` 内 `.header-btns`：【重置】（渲染器 `expose.reset()`，二次确认「将清空已填写内容并恢复默认值」）、【提交】 |
| 提交链路 | ① 渲染器本地 `expose.validate()` 预演（快速反馈，pass=false 时定位展示不提交）；② 调 `validateFormData({ formId, businessId, formData })` **服务端预检**（与 save 共用校验引擎、不落库）；③ 预检 `pass=true` 后调 `saveFormData({ fieldId, formId, businessId, formData })`（POST）落库 |
| 校验错误展示 | `validate` 响应 `data.errors[]`（`{ fieldCode, rowIndex, ruleId, message }`）**字段级定位展示**：主表字段错误滚动定位至对应控件 + 控件下方红字；明细行内错误在明细表对应行内标红并提示行号（`rowIndex` 0 起始）；页面顶部 `el-alert` 汇总「共 N 处校验未通过」；错误文案直接展示 message（含规则 ID 摘要） |
| `CS_FORM_*` 可读提示 | 错误码以 message 前缀承载（`CS_FORM_UNKNOWN_FIELD` / `CS_FORM_CASCADE_OPTION_INVALID` / `CS_FORM_DATA_INVALID` 首条摘要 / `CS_FORMULA_*` 透传等，API 分册 §7），经 `ElMessage` / `el-alert` 直接展示 message 逐条中文原因，**前端不做错误码翻译** |
| 保存成功 | 响应 `data = { fieldId, formData }`——`formData` 为服务端最终落库形态（公式重算值回填、隐藏字段裁剪后），**以响应数据刷新展示**（公式字段展示值与服务端收敛，ADR-09）；提示「保存成功」，提供【再填一条】（`reset` 后继续）与【查看数据】（跳 `form/data/:id`，`id` = 响应 `fieldId`） |

### 8.2 数据详情页（`form/data/:id`）

| 项 | 规格 |
| --- | --- |
| 页面组件 | `views/form/FormDataDetailView.vue` |
| 页面类型 | 详情页（详情态渲染器 + `.detail-grid` 元信息区） |
| 访问口径 | 登录即可 + 数据归属：**本人（数据创建人）或 `SYS_FORM_ADMIN` / `SYS_ADMIN`**（R06 定稿，功能详细设计 §12.2）；无 `meta.auth`；越权访问后端拒绝 `CS_FORM_DATA_FORBIDDEN`（`code = FORBIDDEN` + message 前缀）→ 页面渲染错误空态 |
| 初始化 | 按路由 id 调 `loadFormData(fieldId)`（GET `/data/load?fieldId=`）→ 响应 `SysFormDataDetailVo`（`fieldId` / `fieldFormId` / `fieldFormVersionId` / `fieldBusinessId` / `fieldCreatorId` / `fieldCreateTime` / `formData`（读取层裁剪后形态）/ `schema`（实例锁定版本 Schema，原则 2 在途实例用原版本）） |
| 元信息区 | `.detail-grid` / `.detail-item`（不用 `el-descriptions` 跨行，AGENTS §6 硬规范）：提交人 `fieldCreatorId`、提交时间 `fieldCreateTime`、业务关联 ID `fieldBusinessId`（空显示 `-`）、表单名称与版本（取自 `schema` 协议头 `formName` / `version`） |
| 渲染 | `FormRenderer props.mode="'detail'"`、`:schema="schema"`、`v-model="formData"`——详情态只读回显，显隐规则照常求值、动态校验不执行（§5.1） |
| 按钮 | `el-card` `#header` 内 `.header-btns`：【返回】（关标签）；【删除】——**身份预判**：`userStore` 当前用户 ID === `fieldCreatorId`，或 `hasAuth(['SYS_FORM_ADMIN'])` 时显示（前端预判不替代后端归属判定）；二次确认「删除后数据不可恢复，确定删除该条数据？」→ `deleteFormData(fieldId)`（DELETE）→ 成功关标签返回进入源页面 |

### 8.3 业务系统内嵌复用（R06 定稿的组件复用半边）

- 业务页引入 `FormRenderer`（经 `components/index.js` 统一出口）即可在业务系统任意页面挂载表单：
  - **填写（内嵌编辑态）**：宿主页调 `getFillSchema(formId)` 取当前发布版本 Schema → `<FormRenderer mode="edit" :schema="schema" v-model="formData" />` → 提交由宿主页调 `saveFormData`（`businessId` 传业务对象 ID，归属仍由服务端绑定会话用户）；
  - **只读回显（内嵌详情态）**：宿主页调 `loadFormData(fieldId)` → `<FormRenderer mode="detail" :schema="resp.schema" v-model="resp.formData" />`——v1.8 BPM 审批页的数据回显即此形态的前置能力；
  - 业务页可传 `props.fieldPermissions` 做字段级权限外部覆盖（v1.8 BPM 节点字段权限注入位，一期设计器预览预演）。
- 渲染器**不发任何请求**（Schema 与数据由宿主供给、提交由宿主编排），鉴权与归属由数据面接口兜底（登录即可 + 创建人归属 + 管理员全量，功能详细设计 §12.2）——内嵌消费方无需重复实现规则引擎与权限裁剪。

---

## 9. 前端契约

### 9.1 `src/api/form.js` 具名函数清单（与 API 分册 15 接口一一对应）

全部请求经 `src/api/index.js` 的 axios 实例以 `/backend/sys/form/**` 相对路径发出（F15-FE-001 落地）；页面只 import 具名函数，禁止直接写 `axios` 或裸 URL；列表类请求统一 `{ body, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }`、总数取 `metadata.total`。

| 具名函数 | 对应后端接口 | HTTP 方法 | 消费页面 |
| --- | --- | --- | --- |
| `listForms(data)` | `/backend/sys/form/define/list` | POST | 管理列表页 |
| `saveForm(body)` | `/backend/sys/form/define/save` | POST | 表单元数据表单页（新建） |
| `loadForm(fieldId)` | `/backend/sys/form/define/load?fieldId=` | GET | 表单元数据表单页（编辑）、设计器页（画布初始化） |
| `updateForm(body)` | `/backend/sys/form/define/update` | PATCH | 表单元数据表单页（编辑）、设计器页（保存草稿：携带 `schema` 全量重写） |
| `enableForm(body)` | `/backend/sys/form/define/enable` | PATCH | 管理列表页（启停用 `{ fieldId, fieldEnable }`） |
| `deleteForm(fieldId)` | `/backend/sys/form/define/delete?fieldId=` | DELETE | 管理列表页（删除） |
| `publishForm(body)` | `/backend/sys/form/define/publish` | POST | 设计器页（发布 `{ fieldId }`） |
| `getFormVersions(fieldId)` | `/backend/sys/form/define/versions?fieldId=` | GET | 版本列表弹窗（管理页行【版本】与设计器页【版本】共用） |
| `rollbackForm(body)` | `/backend/sys/form/define/rollback` | POST | 版本列表弹窗（回滚 `{ fieldId, fieldVersionId }`） |
| `getFillSchema(formId)` | `/backend/sys/form/data/schema?formId=` | GET | 填写页、业务系统内嵌（取当前发布版本 Schema，API 分册 §4.1 登记函数名） |
| `saveFormData(body)` | `/backend/sys/form/data/save` | POST | 填写页（提交）、业务系统内嵌 |
| `validateFormData(body)` | `/backend/sys/form/data/validate` | POST | 填写页（提交前服务端预检）、业务集成预检 |
| `loadFormData(fieldId)` | `/backend/sys/form/data/load?fieldId=` | GET | 数据详情页（回显 + 实例锁定版本 Schema）、业务系统内嵌只读 |
| `listFormData(data)` | `/backend/sys/form/data/list` | POST | 一期无独立数据列表页——业务集成消费通道（`fieldFormId` 必填、归属强制过滤，API 分册 §4.6；页面承接见 §11 T-02） |
| `deleteFormData(fieldId)` | `/backend/sys/form/data/delete?fieldId=` | DELETE | 数据详情页（删除，本人或管理员） |

> `getInitID()` 为公共通道（`api/common.js`，`GET /backend/common/initID`），不在本模块函数清单内；表单元数据表单页（新建）与填写页均经其预生成 `fieldId`。

### 9.2 组件目录规划

```text
frontend/src/components/form/
├─ registry.js                  # 组件注册协议（componentRegistry + registerComponent，功能详细设计 §1.2；
│                               #   重复 type 注册抛错、物料七件套完整性断言 fail-fast）
├─ FormRenderer.vue             # 动态渲染器（三形态 design/edit/detail + 字段级权限渲染，功能详细设计 §4；
│                               #   进 components/index.js 统一出口——业务系统内嵌复用入口，§8.3）
├─ materials/                   # 物料库（每组件物料 = 元数据 + 七件套：propSchema/component/默认值生成器/
│  ├─ dataMaterials.js          #   dataType/校验器/序列化/反序列化；一期倾向按分类聚合文件，随 F15-FE-002 定）
│  ├─ contentMaterials.js       #   数据类 12 物料 / 内容类 4 物料 / 布局类 2 物料
│  └─ layoutMaterials.js        #   （控制类 permission 仅协议位，不注册物料）
├─ widgets/                     # 运行时组件（按 F15-FE-003 基础七件 → F15-FE-004 业务四件 → F15-FE-007
│                               #   内容与明细 → F15-FE-010 级联 分批注册；统一 props/v-model/事件分发）
├─ rules/                       # 前端规则引擎（F15-FE-006：事件总线 + 四类规则求值器 + 循环防护熔断）
│  ├─ eventBus.js               #   渲染器事件总线订阅入口
│  ├─ ruleEngine.js             #   register/execute/validate 统一接口
│  └─ evaluators.js             #   条件求值（operator 十项）与动作执行器（六类动作）
└─ designer/                    # 设计器子组件（F15-FE-008 落地命名；FormDesigner 经
   ├─ FormDesigner.vue          #   components/index.js 统一出口导出——编译覆盖 + 页面层
   │                            #   `<FormDesigner :form-id @back>` 整体内嵌消费；
   │                            #   三区域主组件：顶部工具区与表单级动作（保存草稿/预览/
   │                            #   导入/导出/版本/发布自持承接）、画布挂载配置态渲染器、
   │                            #   选中态/拖拽落点解析/悬浮工具条/快照栈接线/脏数据保护
   │                            #   （expose isDirty()/getSchema() 供页面层路由守卫）
   ├─ MaterialsPanel.vue        # 左物料栏（分类分组 + 点击插入 / 原生拖拽）
   ├─ PropertyPanel.vue         # 右属性面板（固定属性项 + 按物料 propSchema 动态渲染
   │                            #   扩展属性，EDITOR_TYPES 全类型支持；公式项复用
   │                            #   FormulaDesigner 弹窗）
   ├─ RulePanel.vue             # 规则配置面板（四类规则可视化 + 上限提示 + 循环防护文案）
   ├─ PreviewDialog.vue         # 预览弹窗（编辑态渲染器挂载 + 字段权限预演 + 本地校验）
   ├─ SchemaImportDialog.vue    # Schema 导入弹窗（粘贴 + 轻量结构校验 + 二次确认）
   ├─ VersionsDialog.vue        # 版本列表与回滚弹窗（管理页与设计器页共用）
   └─ designerSupport.js        # 设计器纯逻辑模块（零 import、node 可直测）：资源限制
                                #   等值常量 / Schema 快照栈（undo/redo，栈深 50）/
                                #   扁平索引（码链消歧）/ 字段码与默认名称生成 /
                                #   放置约束 / 导入轻量结构校验器 / 规则与条件摘要文案
```

- 目录骨架对齐功能详细设计 §0.2 定稿（`registry.js` + `materials/` + `widgets/` + `rules/` + `designer/` + `FormRenderer.vue`）与任务卡 F15-FE-002/005/006/008 影响路径；designer/ 子文件名已按实现落地命名回写（规划名微调，目录分层不变——§11 T-08），与 V1.0 规划的差异：画布逻辑内聚于 FormDesigner 主组件（不拆 DesignCanvas）、快照栈与导入校验器合并入 designerSupport.js 纯逻辑模块、**FormDesigner 经 components/index.js 统一出口导出**（与「不进统一出口」初版规划不同——页面层以 `<FormDesigner :form-id @back>` 整体内嵌消费，统一出口同时保证 `npm run build` 编译覆盖设计器代码）。
- 渲染器与设计器均只经 `registry` 取物料，任何组件不直接 import 其他组件的运行时实现（开闭原则：新增组件只需注册物料、不改渲染器与规则引擎）。
- 命名遵循 AGENTS §7：组件 `PascalCase.vue`、api / rules / utils 用小写；页面组件 `views/form/` 下 `PascalCase` + `View.vue` 结尾且显式声明组件名（供 `HomeView` 的 KeepAlive `include` 匹配）。

### 9.3 `views/ui.vue` 样例登记（收口归 F15-DOC-006）

- 按公共组件样例页先例（`AddressBook` / `Attachment` / `DatasourceTable` / `FormulaDesigner` 各有演示块 + 公共组件清单条目），`FormRenderer` 收口时在 `views/ui.vue`（路由 `/ui`）登记：公共组件清单补 `FormRenderer` 条目（name / code / summary）+ 演示块（内置静态样例 Schema + `mode` 切换演示 edit / detail 两形态，`design` 形态依赖设计器上下文不演示）。
- 同批在 [前端公共组件.md](../系统模块/前端公共组件.md) 增补 `FormRenderer` 契约条目（props / emits / expose，§5.2 表为底稿）——**登记动作归 F15-DOC-006 文档收尾回查**，本分册不改动该文档。

---

## 10. 页面走查清单（供 F15-INT-002 使用）

| # | 走查项 | 判定标准 | 关联任务 |
| --- | --- | --- | --- |
| 1 | 路由与 `meta` | 6 条路由 path / name / `meta`（title / parent / group / auth / tab / moduleGroup）与 §2.3 冻结片段逐字一致；4 条动态路由 `meta.tab: 'name'` 复用单标签 | F15-FE-009 |
| 2 | 菜单装配 | 【系统管理】→【自定义表单】→【表单管理】齐全；无 `SYS_FORM_ADMIN` 账号不可见分组且直访管理路由被守卫重定向首页；`SYS_ADMIN` 可见全部 | F15-FE-009 |
| 3 | `activeMenu` 归约 | `/form/create|edit/:id|design/:id|fill/:formId|data/:id` 归约到 `/form/manage` 高亮正确（管理员视角）；**回归验证 `/formula` 页面高亮不受影响**（带斜杠写法约束）；普通用户经链接进填写页不高亮为已知可接受行为 | F15-FE-009 |
| 4 | 模块组开关 | `form` 模块组关闭时：管理菜单隐藏、6 条路由渲染「模块未开启」空态 | F15-FE-009 |
| 5 | 管理列表页三段式 | 筛选四项一行 `span=6`、回车查询、枚举默认「全部」；`sort-area` 默认创建时间降序；无批量操作、无复选框列、不展示主键、操作列 `fixed="right"`；分页 `layout` 与 `page-sizes` 合规、切页大小回第 1 页；样式复用 `list-page.css` 无页面内重复定义 | F15-FE-009 |
| 6 | 启停用与删除 | 启停用 / 删除均二次确认；停用后该行【填写】置灰；有数据删除返回 `CS_FORM_HAS_DATA` 且提示文案含「如需停用请使用启停用」引导 | F15-FE-009 |
| 7 | 元数据表单页 | 新建 `onMounted` 调 `getInitID()`；编辑按路由 id `loadForm` 回填；编码编辑态只读；`label-width="40%"` 一行两字段；按钮在 `#header .header-btns`；【设计表单】保存后跳设计器 | F15-FE-009 |
| 8 | 设计器三区域 | 左物料分组 12/4/2 齐全、权限组件不展示；点击与拖拽两种入画布方式均可用；右面板【属性】【规则】双页签与选中联动 | F15-FE-008 |
| 9 | 画布交互 | 拖拽放置 / 同层排序 / 选中高亮联动 / 复制（自动重配字段码）/ 删除（容器级联删除确认）/ 撤销重做（草稿边界）/ 清空 / 脏数据离开二次确认 | F15-FE-008 |
| 10 | 容器嵌套约束 | 分组 / 栅格相互嵌套一层、超 `MAX_NEST_DEPTH` 拦截提示；明细表行内拖入内容 / 布局 / 明细表组件被拦截并提示「明细表行内仅支持数据类组件」 | F15-FE-008 |
| 11 | 属性面板 | 固定属性项按类别裁剪（内容类无 ID/必填/默认值、布局类仅名称）；18 组件扩展属性项与 §4.5.2 登记表逐项一致；字段码格式 `[a-zA-Z_][a-zA-Z0-9_]*` 与同层唯一校验生效 | F15-FE-002/008 |
| 12 | 公式属性 | 【设计公式】打开 `FormulaDesigner`（`availableVars` 传表单字段码、明细数组变量投影可用）；只读输入框不可手改；`expectedReturnType` 传入弹窗 | F15-FE-004/008 |
| 13 | 规则面板 | 四类规则可配置（响应函数无配置入口）；operator 十项与 value 控件联动（empty/notEmpty 无值控件）；`onSubmit`/`onValidate` 下 `setValue`/`clearValue` 禁用；上限提示文案与【添加规则】达上限禁用 | F15-FE-008 |
| 14 | 预览 | 预览弹窗挂载编辑态渲染器、字段权限预演开关生效；本地校验展示、不发数据面请求 | F15-FE-008 |
| 15 | 导入导出 | 导出文件名 `{formCode}.json`、草稿 `version` 为 null；粘贴导入轻量校验失败逐条提示、成功覆盖画布需二次确认；导入不直接落库 | F15-FE-008 |
| 16 | 发布与版本 | 无草稿行发布置灰预判；发布确认文案含不可变版本语义、成功回显新版本号；版本列表三态 tag 与回滚按钮行级控制（DRAFT 禁用）；回滚确认文案与结果（新版本号）正确 | F15-FE-008/009 |
| 17 | 填写页 | 前置拒绝三态（不存在 / 停用 / 未发布）空态可读；渲染器 edit 渲染 + 默认值初始化；本地预演 + `validateFormData` 预检 errors 字段级定位（明细行标行号）；`CS_FORM_*` message 直接展示；保存成功以响应 `formData` 刷新、【查看数据】跳转正确；query 参数 `businessId` 随 save 提交 | F15-FE-009 |
| 18 | 数据详情页 | `detail` 态渲染 + 显隐规则照常求值；元信息 `.detail-grid`（无 `el-descriptions`）；本人可见、他人非管理员拒绝（`CS_FORM_DATA_FORBIDDEN` 错误空态）；【删除】身份预判 + 二次确认 | F15-FE-009 |
| 19 | 内嵌复用 | `FormRenderer` 经 `components/index.js` 可引入；props / v-model / expose 契约与 §5.2 一致；渲染器不发请求 | F15-FE-005 |
| 20 | 硬规范复核 | 列表页 / 表单页 / 详情页样式复用 `list-page.css` / `form-page.css`；接口全部经 `api/form.js` 具名函数（无裸 axios / 裸 URL）；无 ERP 工程标识残留 | 全部 FE |
| 21 | 构建 | `cd frontend && npm run build` 通过；设计器懒加载分块、新增依赖 chunk 体积正常（R07 验证口径） | F15-QA-001 |
| 22 | 文档一致性 | 本文档与 `docs/系统模块/系统模块UI设计文档.md` §4.12 占位、功能详细设计 §1.3 / §4、API 分册接口清单四处描述一致 | F15-DOC-006 |

---

## 11. 待确认事项（移交登记）

| 编号 | 待确认项 | 影响范围 | 本分册已给口径 / 建议处置 |
| --- | --- | --- | --- |
| T-01 | ~~R07 拖拽依赖最终选型~~ **已收口（2026-11-25，F15-FE-008）**：实现期裁决为原生 HTML5 DnD、零新依赖（裁决理由见 §4.1），R07 CLOSED；chunk 体积已实测（设计器独立分块，主包口径不变） | 物料库 / 设计器（F15-FE-002/008） | ~~本分册只登记依赖倾向~~ 已回写 §4.1 |
| T-02 | `listFormData` 的页面承接（数据列表页） | 管理视角数据查看（F15-INT-002 评估） | 一期不设数据列表页（§2.2 说明 4）——数据查看经填写页【查看数据】、业务链接或业务内嵌消费；联调期出现管理侧数据列表诉求时补页面与路由并同批回写本分册与 API 分册 |
| T-03 | 历史版本 Schema 内容预览 / 版本对比 | 版本列表弹窗（§4.9） | 一期不设版本行详情接口（API 分册 §10-6 登记）；版本弹窗仅元信息；出现诉求须先补 `GET /define/version` 接口并回写 API 分册，再补 UI |
| T-04 | 数据实例再编辑页（`PATCH /data/update`） | 填写页 / 数据详情页 | 一期填写页仅新建、详情页只读（API 分册 §4.3 一期范围说明）；出现「数据再编辑」诉求须补接口与页面并回写两册 |
| T-05 | 设计器快照栈容量上限与跨页签行为（撤销重做内存策略） | 设计器画布（F15-FE-008） | **已实现定值（2026-11-25）**：栈深上限 50 步（`SNAPSHOT_STACK_LIMIT`，超出丢弃最旧快照；仅结构变更压栈），仅作用于草稿会话、离开页面快照丢弃——不改本分册交互语义 |
| T-06 | 功能详细设计 §6.1 第②步「未知字段裁剪 + 告警」表述与 API 分册 §7.4① 定稿「拒绝（`CS_FORM_UNKNOWN_FIELD`）」的统一 | ~~文档一致性（F15-DOC-006）~~ **已闭环（2026-11-23 核实）**：功能设计保存校验链第②步在 F15-DOC-002 提交时已同步为「拒绝」口径（并登记 API 分册 §7.4 指向），本项无需 DOC-006 再处理；本分册填写页错误提示按「拒绝」口径书写（§8.1） | 以 API 分册定稿为准 |
| T-07 | 业务系统链接拼接规范细化（`/form/fill/{formId}?businessId=` 的编码与防篡改约定） | 填写页（F15-FE-009） | 本分册仅登记参数位与语义（§2.2 说明 2、§3.4）；`businessId` 为弱关联、归属以会话用户为准，防篡改无附加要求；细化随业务接入批次 |
| T-08 | §9.2 组件目录子文件名（规划名）与 `materials/` 聚合粒度 | 物料库 / 设计器（F15-FE-002/008） | 实现期可微调，目录分层（`registry.js` + `materials/` + `widgets/` + `rules/` + `designer/` + `FormRenderer.vue`）不变 |
| T-09 | 明细行内显隐的服务端裁剪（一期为渲染层语义、行数据按行原样落库） | 数据详情页回显一致性（二期评估） | 功能详细设计 §3.6 一期边界已登记；详情页对行内显隐照常求值展示，跨会话行内「提交时隐藏」字段可能回显旧值属接受行为，二期行级裁剪落地后自然收敛 |

---

## 12. 引用与关联文档

| 文档 | 关系 |
| --- | --- |
| [自定义表单功能详细设计.md](自定义表单功能详细设计.md) | 上游权威设计：组件协议 §1（§1.3 组件全集与属性项）、Schema 协议 §2（§2.2 资源限制常量、§2.3 示例）、规则协议 §3、渲染器契约 §4、版本机制 §5、权限与配置定稿 §12（R06 数据归属）——本文 UI 形态的契约基线 |
| [自定义表单API接口设计分册.md](自定义表单API接口设计分册.md) | 接口契约权威：15 接口路径与方法、`ACTION_*`、错误码 `CS_FORM_*` 全集——本文页面动作与接口承接的唯一依据 |
| [自定义表单组件.md](自定义表单组件.md) | 组件表格输入基线（属性面板登记的三方对齐源之一） |
| [自定义表单开发任务清单.md](自定义表单开发任务清单.md) | §4.4 本任务卡片、§6 R06/R07 风险行、F15-FE-001/002/005/006/008/009/010 与 F15-INT-002 任务承接 |
| [AGENTS.md](../../AGENTS.md) | §6 前端约束（列表页三段式 / 表单页 / 路由 / 菜单 / 权限硬规范）、§7 命名 |
| [前端公共组件.md](../系统模块/前端公共组件.md) | 复用组件契约：地址本 `AddressBook`（第一节）、附件 `Attachment`（第二节）、数据源表格 `DatasourceTable`（第九节）、公式设计器 `FormulaDesigner`（第十节） |
| [系统模块UI设计文档.md](../系统模块/系统模块UI设计文档.md) | 系统模块层页面承接（§4.12 自定义表单占位摘要，权威口径为本文） |
| [流程v1_7前端UI设计分册.md](../设计文档/sys/process/流程v1_7前端UI设计分册.md) | 文档结构先例与设计器画布分层形态参照（`components/process/designer/` 物料 / 画布 / 配置弹窗分层） |
| `frontend/src/router/index.js`、`views/HomeView.vue`、`styles/list-page.css`、`styles/form-page.css`、`api/index.js`、`views/ui.vue` | 现状代码基线（路由组织与 meta 写法、菜单结构与 `activeMenu` 归约、公共样式类名、axios 实例、样例页登记方式；基线核对日 2026-11-23） |

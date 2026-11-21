# 流程 v1_7 前端 UI 设计分册

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 文档定位 | v1_7 流程管理改造的**前端 UI 设计冻结稿**：LogicFlow 设计器画布交互、节点配置弹窗、实例流程图渲染、审批面板操作交互、流程监控页、审批一级菜单、统计页布局，以及路由与菜单 / `meta.tab` 约定 |
| 输入依据 | 《功能实现方案-改造分析与方案设计》（§3.3 节点类型与图形、§4 审批操作体系、§5 节点配置、§9 前端设计，D1~D8 已评审）；《流程核心业务.md》（P17-DOC-001 改造稿）；《流程管理v1_7研发任务清单.md》（P17-DOC-004 卡片）；`AGENTS.md` 第 6 节前端约束；现状前端代码基线 |
| 适用版本 | v1_7 流程管理（连线 DAG 引擎 + 10 类节点 + 14 类审批操作 + @ProcessTask + 监控统计） |
| 创建日期 | 2026-11-19 |
| 文档版本 | V1.7 |
| 文档状态 | 已确认（Wave 0 / P17-DOC-004 产出；V1.2 为 P17-FE-003 实施回写、V1.3 为 P17-FE-004 实施回写、V1.4 为 P17-FE-005 实施回写、V1.5 为 P17-FE-006 实施回写、V1.6 为 P17-FE-007 实施回写、V1.7 为 P17-FE-008 实施回写） |
| 承接关系 | 本文档供 P17-FE-001~009 实现、P17-QA-003 页面走查使用；系统模块层页面承接见 `docs/系统模块/系统模块UI设计文档.md` §3.3、§4.3.4（已引用本文档） |

### 1.1 范围与边界

- **In Scope**：路由与菜单装配、`meta` 约定、`activeMenu` 前缀归约、设计器画布交互与节点物料、节点配置弹窗多标签结构、分支配置弹窗、实例详情流程图渲染、审批面板改造、流程监控页、我发起的/我参与的/我的流程统计页、流程统计页、`src/api/process.js` 具名函数清单、组件目录规划、页面走查清单。
- **Out of Scope**：后端接口契约本体（见 P17-DOC-002 流程 v1_7 API 接口设计分册）、表结构与迁移（见 P17-DOC-003）、业务规则真相（见《流程核心业务.md》）、权限码与配置项落库（见 P17-DOC-005）。
- **不写代码**：本任务只产出设计文档，不改动任何 Vue / JS / CSS 代码，不执行构建与提交。
- 本文档中标注「**v1_7 待实现（任务号）**」的内容为设计冻结项、尚未有代码；未标注者为现状代码已存在的事实（基线核对日 2026-11-19）。

### 1.2 版本变更记录

| 版本 | 日期 | 变更说明 |
| --- | --- | --- |
| V1.0 | 2026-11-19 | 初版：v1_7 前端 UI 设计冻结（5 个新页面路由与菜单、设计器画布与 10 类节点物料、节点配置弹窗、实例流程图、审批面板 14 类操作渲染、监控与统计布局、API 具名函数清单、走查清单） |
| V1.1 | 2026-11-21 | P17-FE-002 定稿回写：T-08 坐标不落库结论落 §4.4「节点移动」与 §4.6-4（load 无坐标、画布拓扑自动分层、保存不提交坐标）；§4.5 条件公式必填口径细化为「非默认出边必填、默认分支可空（兜底）」（对齐本节弹窗示意）；设计器画布已实现交互与本节逐条核对一致 |
| V1.2 | 2026-11-21 | P17-FE-003 定稿回写：T-09 并行开始「无可流转分支时行为」配置键名定稿为 `noBranchMode`（error/continue，后端 SysProcessTemplateNodeConfigDo 已实现），落 §5.3 与 §12.3；节点配置弹窗已实现结构与本节逐条核对一致 |
| V1.3 | 2026-11-21 | P17-FE-004 实施回写：§4.2 顶部按钮集按现状页面实际口径修正（新建【关闭】【保存】/ 编辑【关闭】【启停用】【保存】/ 详情【关闭】【编辑】【启停用】，无独立【返回】按钮）；TemplateFormView 已重写为画布设计器三段形态（顶部表单 + FlowDesignerCanvas，详情 readonly），节点/分支配置走画布内置弹窗，保存走 validateGraph → getSavePayload → save/update（nodes+lines 全量），画布改动置脏、【关闭】与路由离开二次确认 |
| V1.4 | 2026-11-21 | P17-FE-005 实施回写：§6.2 连线「已流经/未流经」口径定稿（源节点 FINISH/PASS 且目标节点非 WAITING，REJECT 源出边按未流经展示，仅视觉）、只读实现方案核实结论（isSilentMode 下 node:dbclick 仍触发，沿用静默方案）、双击节点提示以 el-dialog 承载、实例节点字段口径（fieldNodeNo/fieldNodeKey）；§12.3 T-04 定稿（已随本任务归一 .detail-grid）、T-05 登记本期不加（保持待定） |
| V1.5 | 2026-11-21 | P17-FE-006 实施回写：§7.3-6 抄送字段名定稿回写 `ccIds`→`ccToIds`（对齐接口清单 §6.1 Q-01/P17-BE-008）；§7.3 补第 8 条实施形态（按钮组 + 条件字段区内联承载、宿主 completeSubmit/refresh 反馈契约、大写特权标识归一/透传口径、抄送与 processed_* 工作项选择的契约自洽补充） |
| V1.6 | 2026-11-21 | P17-FE-007 实施回写：§8 监控页偏差回写（「当前节点」筛选不落——后端 SysProcessExampleQuery 无对应字段，筛选位由「仅看在途」`inFlightOnly` 开关补位；异常筛选落两态 全部/仅异常——后端 `exceptionOnly` boolean 承载，「排除异常」不落；T-02 定稿异常标志由行内 `exceptionFlag` 承载、响应无错误摘要字段故异常标记列 title 提示不落；排序仅创建时间——后端排序白名单仅 `createTime`）；§8.3 异常标记字段名修正 `errorFlag/errorMsg`→`exceptionFlag`；§11.2 补 `ProcessAdminOperateDialog.vue`（自 ExampleDetailView 抽取，实例详情页/监控页共用） |
| V1.7 | 2026-11-21 | P17-FE-008 实施回写：§9.1/§9.2 偏差注记（「当前节点」筛选不落——与监控页同源，后端 SysProcessExampleQuery 无对应字段；T-03 定稿——我参与的「参与方式」筛选与「我的处理状态」列均不落，后端 Query 无参与方式区分字段、list 响应无本人处理状态字段）；§12.3 T-03 状态更新为已定稿；§9 标注两页已交付、我的流程统计页仍待 P17-FE-009 |

---

## 2. 页面与路由总表

### 2.1 路由约定（沿用仓库既有风格）

- 路由 path **不带前导 `/`**（子路由相对写法），与现有 `processSetting` / `processTemplate` / `processExample` 扁平命名风格一致。
- 统一挂在 `HomeView` 布局壳（`path: '/'`）的 `children` 下；顶层 `children` 不嵌套，靠 `meta.parent` / `meta.group` 渲染面包屑。
- 动态路由（`edit` / `detail`）设 `meta.tab: 'name'` 复用单标签（先例：`processTemplate/edit/:id`、`processTemplate/detail/:id`、`processExample/detail/:id`）。
- `meta.auth` 为前端按钮/路由预判，**不替代后端鉴权**；`SYS_ADMIN` 经 `hasAuth` 超管短路天然可见全部页面（`BaseResolver` 自动放行）。

### 2.2 页面与路由总表

| 页面名称 | 路由 path | 路由 name | `meta`（title / parent / group / auth / tab） | 菜单归属 | 页面类型 | 主要角色 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 流程公共配置 | `processSetting` | `ProcessSetting` | 公共配置 / 系统管理 / 流程管理 / `['SYS_PROCESS_ADMIN','SYS_PROCESS_SETTING_ADMIN']` / — | 后台管理 → 流程管理 → 公共配置 | 列表页 | 流程模块公共配置维护员 | 现状保留 |
| 流程模板管理 | `processTemplate` | `ProcessTemplate` | 模板管理 / 系统管理 / 流程管理 / `['SYS_PROCESS_ADMIN','SYS_PROCESS_TEMPLATE_ADMIN','SYS_PROCESS_TEMPLATE_VIEWER']` / — | 后台管理 → 流程管理 → 模板管理 | 列表页（左分类 + 右三段式列表） | 流程模板管理员/查看者 | 现状保留 |
| 流程模板新增 | `processTemplate/create` | `ProcessTemplateCreate` | 新建流程模板 / 系统管理 / 流程管理 / `['SYS_PROCESS_ADMIN','SYS_PROCESS_TEMPLATE_ADMIN']` / — | 列表页【新建】进入，无独立菜单 | 表单页（画布式设计器） | 流程模板管理员 | v1_7 改造（P17-FE-004） |
| 流程模板编辑 | `processTemplate/edit/:id` | `ProcessTemplateEdit` | 编辑流程模板 / 系统管理 / 流程管理 / `['SYS_PROCESS_ADMIN','SYS_PROCESS_TEMPLATE_ADMIN']` / **`tab: 'name'`** | 列表页行【编辑】进入 | 表单页（画布式设计器） | 流程模板管理员 | v1_7 改造（P17-FE-004） |
| 流程模板详情 | `processTemplate/detail/:id` | `ProcessTemplateDetail` | 流程模板详情 / 系统管理 / 流程管理 / `['SYS_PROCESS_ADMIN','SYS_PROCESS_TEMPLATE_ADMIN','SYS_PROCESS_TEMPLATE_VIEWER']` / **`tab: 'name'`** | 列表页行【详情】进入 | 详情页（只读画布） | 流程模板查看者 | v1_7 改造（P17-FE-004） |
| 流程实例 | `processExample` | `ProcessExample` | 流程实例 / 系统管理 / 流程管理 / `['SYS_PROCESS_ADMIN','SYS_PROCESS_EXAMPLE_ADMIN','SYS_PROCESS_EXAMPLE_VIEWER']` / — | 后台管理 → 流程管理 → 流程实例 | 列表页 | 流程实例管理员/查看者 | 现状保留 |
| 流程实例详情 | `processExample/detail/:id` | `ProcessExampleDetail` | 流程实例详情 / 系统管理 / 流程管理 / `['SYS_PROCESS_ADMIN','SYS_PROCESS_EXAMPLE_ADMIN','SYS_PROCESS_EXAMPLE_VIEWER']` / **`tab: 'name'`** | 列表页行【详情】进入 | 详情页（只读流程图 + 三张明细表） | 流程实例管理员/查看者 | v1_7 改造（P17-FE-005） |
| **流程监控** | `process/monitor` | `ProcessMonitor` | **流程监控** / 系统管理 / 流程管理 / **`['SYS_PROCESS_ADMIN','SYS_PROCESS_EXAMPLE_ADMIN']`** / — | 后台管理 → 流程管理 → **流程监控** | 列表页（三段式） | 流程实例管理员 | **v1_7 新增（P17-FE-007）** |
| **流程统计** | `process/statistic` | `ProcessStatistic` | **流程统计** / 系统管理 / 流程管理 / **`['SYS_PROCESS_ADMIN','SYS_PROCESS_EXAMPLE_ADMIN']`** / — | 后台管理 → 流程管理 → **流程统计** | 统计页（指标卡 + 聚合表） | 流程实例管理员 | **v1_7 新增（P17-FE-009）** |
| **我发起的** | `approval/created` | `ApprovalCreated` | **我发起的** / **审批** / — / **`['SYS_PROCESS_ADMIN','SYS_PROCESS_MY_VIEWER']`** / — | **审批（新一级菜单）→ 我发起的** | 列表页（三段式） | 登录用户（全员） | **v1_7 新增（P17-FE-008）** |
| **我参与的** | `approval/joined` | `ApprovalJoined` | **我参与的** / **审批** / — / **`['SYS_PROCESS_ADMIN','SYS_PROCESS_MY_VIEWER']`** / — | **审批 → 我参与的** | 列表页（三段式） | 登录用户（全员） | **v1_7 新增（P17-FE-008）** |
| **我的流程统计** | `approval/statistic` | `ApprovalStatistic` | **我的流程统计** / **审批** / — / **`['SYS_PROCESS_ADMIN','SYS_PROCESS_MY_VIEWER']`** / — | **审批 → 我的流程统计** | 统计页（指标卡 + 聚合表） | 登录用户（全员） | **v1_7 新增（P17-FE-009）** |

> 说明 1：`meta.parent`/`meta.group` 的取值规则——「后台管理」下的流程页面沿用既有 `parent: '系统管理'` + `group: '流程管理'`（面包屑「系统管理 / 流程管理 / 流程监控」）；新增【审批】一级菜单页面取 `parent: '审批'`、**不设 `group`**（面包屑「审批 / 我发起的」，与【工作台】下 `待办`/`日程` 的既有形态一致）。
>
> 说明 2：`meta.auth` 数组中的 `SYS_PROCESS_ADMIN` 沿用现状写法（流程模块总权限，`hasAuth` 命中即显示）；页面级准入的新权限码 `SYS_PROCESS_MY_VIEWER`（`roles.yml` 默认全员授予）以 P17-DOC-005 权限码设计确认结果为准。
>
> 说明 3：`process/monitor` 与 `process/statistic` 为**同名一级前缀下新增的两条静态路由**，与既有 `processSetting` / `processTemplate` / `processExample` **不存在路径冲突**（后三者无 `/process/` 形态）。

### 2.3 路由片段（冻结写法）

以下为冻结的路由 `meta` 声明口径（实现时按仓库现有 `routes` 数组风格追加，**不改变既有路由**）：

```text
{
  path: 'process/monitor',
  name: 'ProcessMonitor',
  component: () => import('@/views/process/ProcessMonitorView.vue'),
  meta: { title: '流程监控', parent: '系统管理', group: '流程管理',
          auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_EXAMPLE_ADMIN'] }
}
{
  path: 'process/statistic',
  name: 'ProcessStatistic',
  component: () => import('@/views/process/ProcessStatisticView.vue'),
  meta: { title: '流程统计', parent: '系统管理', group: '流程管理',
          auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_EXAMPLE_ADMIN'] }
}
{
  path: 'approval/created',
  name: 'ApprovalCreated',
  component: () => import('@/views/process/MyCreatedView.vue'),
  meta: { title: '我发起的', parent: '审批',
          auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_MY_VIEWER'] }
}
{
  path: 'approval/joined',
  name: 'ApprovalJoined',
  component: () => import('@/views/process/MyJoinedView.vue'),
  meta: { title: '我参与的', parent: '审批',
          auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_MY_VIEWER'] }
}
{
  path: 'approval/statistic',
  name: 'ApprovalStatistic',
  component: () => import('@/views/process/MyStatisticView.vue'),
  meta: { title: '我的流程统计', parent: '审批',
          auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_MY_VIEWER'] }
}
```

> 5 个新页面均为**叶子路由**（无 create/edit/detail 子路由），故**均不设 `meta.tab`**；`meta.tab: 'name'` 仅用于 `processTemplate` 与 `processExample` 的动态子路由（见 2.1）。

---

## 3. 菜单装配说明（`HomeView.vue`）

> v1_7 待实现（P17-FE-007 / P17-FE-008）：本节为菜单配置与 `activeMenu` 归约的冻结口径，实现改动集中在 `frontend/src/views/HomeView.vue` 的 `menus` 常量（现状约 263~368 行）与 `activeMenu` 计算属性（现状约 400~431 行）。

### 3.1 菜单结构变化

「系统管理 → 流程管理」由 **3 页扩为 5 页**（新增流程监控、流程统计），并在「系统管理」同级新增**【审批】一级菜单**（3 页）：

```text
系统管理（一级菜单，auth: ''）
├─ 流程管理（二级分组，auth: ''）
│  ├─ 公共配置    /processSetting                     （现状）
│  ├─ 模板管理    /processTemplate                    （现状）
│  ├─ 流程实例    /processExample                     （现状）
│  ├─ 流程监控    /process/monitor                    （v1_7 新增，管理员级）
│  └─ 流程统计    /process/statistic                  （v1_7 新增，管理员级）
├─ 通知待办 / 附件管理 / 权限管理 / 码值管理 / 定时任务 / 日志管理 / 后台管理 / 多语言管理（现状不变）
└─ ...

审批（一级菜单，auth: ''）
├─ 我发起的       /approval/created                   （v1_7 新增，全员）
├─ 我参与的       /approval/joined                    （v1_7 新增，全员）
└─ 我的流程统计   /approval/statistic                 （v1_7 新增，全员）
```

菜单项 `auth` 声明（叶子项各自声明，沿用「后台管理」子组 auth 下放的既有惯例）：

| 菜单项 | path | `auth` |
| --- | --- | --- |
| 流程管理 → 流程监控 | `/process/monitor` | `['SYS_PROCESS_ADMIN','SYS_PROCESS_EXAMPLE_ADMIN']` |
| 流程管理 → 流程统计 | `/process/statistic` | `['SYS_PROCESS_ADMIN','SYS_PROCESS_EXAMPLE_ADMIN']` |
| 审批 → 我发起的 | `/approval/created` | `['SYS_PROCESS_ADMIN','SYS_PROCESS_MY_VIEWER']` |
| 审批 → 我参与的 | `/approval/joined` | `['SYS_PROCESS_ADMIN','SYS_PROCESS_MY_VIEWER']` |
| 审批 → 我的流程统计 | `/approval/statistic` | `['SYS_PROCESS_ADMIN','SYS_PROCESS_MY_VIEWER']` |

- 【审批】一级菜单 `auth: ''`（所有登录用户可见），**页签与权限下放到 3 个叶子项各自声明**；分组下无可见子项时，既有 `filterMenuNode` 递归过滤会自动隐藏整个分组（无需额外逻辑，`SYS_ADMIN` 经 `hasAuth` 超管短路天然可见全部）。
- 【审批】一级菜单与【工作台】【组织架构】同为「一级菜单直接挂叶子」（无二级分组），走既有 `group.leafChildren` 渲染分支，**不新增渲染层级**（现有菜单渲染最多支持三级：一级 → 二级分组 → 三级叶子/分组）。
- 通知待办页**不加新页签**（决策 D4）：抄送与催办的待阅统一在通知中心既有「待阅」列表呈现，`MyTodoView.vue` 的筛选区「通知类型」选项不新增取值。

### 3.2 `activeMenu` 前缀归约规则（含顺序约束）

现状 `activeMenu` 计算属性（`HomeView.vue` 约 400~431 行）用于把不在菜单中的子路由归约到对应列表页菜单项以保持高亮。v1_7 需**补两条归约**并遵守下述顺序：

| 归约分支 | 归约结果 | 说明 |
| --- | --- | --- |
| `path.startsWith('/processTemplate/')` | `/processTemplate` | 现状保留（模板新建/编辑/详情） |
| `path.startsWith('/processExample/')` | `/processExample` | **v1_7 补**（现状仅有 `/processTemplate/` 分支；`processExample/detail/:id` 目前会落到 `return path`） |
| `path.startsWith('/process/')` | `/process` | **v1_7 新增**（流程监控/流程统计） |
| `path.startsWith('/approval/')` | `/approval` | **v1_7 新增**（我发起的/我参与的/我的流程统计） |

> ⚠ **顺序约束（必须显式落实）**：`/process/` 归约**必须放在 `/processTemplate/`、`/processExample/` 判断之后**。
>
> 原因：`'/processTemplate/edit/1'.startsWith('/process/')` 为 `true`——`/process` 是 `/processTemplate` 与 `/processExample` 的**字符串前缀**。若把 `/process/` 写在前面，模板编辑页会被误归约为 `/process`，菜单高亮落到不存在的菜单项、失去高亮。
>
> 该约束与既有先例同构：`/org/group` 归约必须写在 `/org/` 之前（见 `HomeView.vue` 注释「必须先于下方 /org/ 归约判断，否则群组页会被误归约、误高亮当前组织架构」）。**本处恰好相反：短前缀 `/process/` 必须写在长前缀之后。**
>
> 冻结写法（片段，实现时按此顺序落到 `activeMenu`）：

```text
if (path.startsWith('/processTemplate/')) return '/processTemplate'
if (path.startsWith('/processExample/')) return '/processExample'
if (path.startsWith('/process/')) return '/process'        // 必须在这两条之后
if (path.startsWith('/approval/')) return '/approval'
```

- `/process`、`/approval` 本身无菜单项（`/process` 下只有 `monitor`/`statistic` 两个页面，`/approval` 下只有 3 个页面），因此 `/approval/created` 归约到 `/approval` 后**仍不会命中任何菜单项**——这是**已知的可接受行为**（与现状 `return path` 的视觉效果一致：该页自身即菜单叶子，无需归约高亮）；保留归约分支用于统一维护口径与后续扩展。
- **`/approval/*` 归约的实际收益在 v1_7 之后**（若新增审批子路由如 `approval/detail/:id`，归约即生效）；本期 0 风险、0 副作用。

### 3.3 权限显隐与 `SYS_ADMIN` 超管短路

- 菜单过滤走既有 `filterMenuNode`：`node.auth && !hasAuth(node.auth)` 返回 `null`；分组下无可见子项时整组隐藏。
- `hasAuth` 对 `SYS_ADMIN` **超管短路**：持有 `SYS_ADMIN` 的用户天然可见全部页面与菜单，**不依赖** `SYS_PROCESS_MY_VIEWER` 是否授予（对齐 `BaseResolver` 中 `SYS_ADMIN` 自动放行的后端语义）。
- 新增权限码 `SYS_PROCESS_MY_VIEWER` 由 `roles.yml` **默认全员授予**，「我发起的/我参与的/我的流程统计」对普通登录用户可见；该权限码的最终命名、默认授予方式与库/yml 两处同批落地方案以 P17-DOC-005 为准（见第 12.3 待确认事项）。
- 页面内按钮显隐另用 `hasAuth([...])` 预判（如流程模板列表的「新建/编辑/启停用」、实例详情的「特权操作」），**均不替代后端鉴权**。

---

## 4. 流程设计器（LogicFlow 画布）设计

> v1_7 待实现（P17-FE-001 依赖与物料、P17-FE-002 画布组件、P17-FE-004 页面接入）。
> 现状基线：`TemplateFormView.vue` 为「节点列表 + 配置弹窗」形态（无画布）；`frontend/package.json` **无** `@logicflow/*` 依赖。

### 4.1 依赖与加载策略

| 项 | 约定 |
| --- | --- |
| 依赖 | `@logicflow/core`、`@logicflow/extension`（具体版本随实施时最新稳定版，落 `frontend/package.json`） |
| 引入方式 | **路由懒加载**：设计器组件仅在 `processTemplate/create|edit|detail` 路由被访问时随路由分块加载（风险 R03 对策：设计器不进主包） |
| 按需引入 | 仅注册实际使用的能力（自定义节点 + 必要扩展），不整包注册 `extension` 全部插件；包体积在 P17-FE-001 实施时实测 |
| 只读模式 | 同一画布组件通过 `readonly` prop 复用：详情页与实例详情流程图**只挂载画布、不注册编辑交互与物料面板** |

### 4.2 页面三段式布局（画布版）

设计器页面在既有表单页规范上做**三段横向布局**（顶部表单照旧，下部为画布三段）：

```text
| 关闭  编辑  启停用  保存                         （el-card #header .header-btns）|
|-------------------------------------------------------------------------------|
| 模板名称                        | 模板编号                                     |
| 所属模块（只读，来自列表页分类） | 备注（textarea，整行 2:8）                    |
|-------------------------------------------------------------------------------|
| 左侧物料面板 |  中部画布（LogicFlow 容器，占满剩余）        | 右侧属性区     |
| 节点物料     |  ┌──────────────────────────────────────┐  | 选中节点摘要   |
|  · 开始       |  │ [开始 N1] → [起草 N2] → ... → [结束 N3]│  | 节点名称       |
|  · 起草       |  │        ↘ [审批 N4] ↘ [条件 N5]        │  | 节点类型       |
|  · 结束       |  └──────────────────────────────────────┘  | [配置节点]按钮 |
|  · 审批       |  工具条：缩放 / 适应画布 / 撤销 / 重做        | 连线摘要       |
|  · 抄送       |                                            | [配置分支]按钮 |
|  · 条件分支   |                                            |                |
|  · 并行开始   |                                            |                |
|  · 并行结束   |                                            |                |
|  · 机器人     |                                            |                |
|  · 人工决策   |                                            |                |
|-------------------------------------------------------------------------------|
| 提示条：固定节点 N1/N2/N3 不可删除；双击节点打开配置弹窗；条件/并行分支连线点击后配置分支 |
```

- 顶部表单字段沿用现状（模板名称/模板编号/所属模块/备注），**校验与提交语义不变**；下部由「节点列表 + 添加审批节点」替换为画布三段。
- 详情模式（`processTemplate/detail/:id`）：隐藏左侧物料面板与右侧「配置」按钮，画布为只读，顶部保留【关闭】【编辑】【启停用】。
- 顶部操作按钮位置**不变**：仍置于 `el-card` `#header` 内 `.page-header > .header-btns`，不使用底部操作区（`form-page.css` 约定）。按钮集沿用现状页面实际口径（**P17-FE-004 实施回写**）：新建【关闭】【保存】、编辑【关闭】【启停用】【保存】、详情【关闭】【编辑】【启停用】——【关闭】即关标签返回列表页，无独立【返回】按钮（本节示意图已按此修正）。

### 4.3 节点物料面板与 10 类节点图形

左侧物料面板按《流程核心业务》「节点类型全集」渲染 **10 类节点**，图形样式冻结如下（`ROBORT` 为**存量历史拼写，不得写为 ROBOT**，代码常量/文档/页面一致）：

| # | 节点物料 | `node_type` | 画布图形 | 建议形状参数 | 出边数 | 是否可删除 | 是否可复制/多实例 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 开始节点 | `START` | **圆角矩形** | 宽 120 × 高 48，圆角 12 | 1（固定 → N2） | ❌ 固定 N1 | 否 |
| 2 | 起草节点 | `DRAFT` | **直角矩形** | 宽 120 × 高 48，圆角 0 | 1（默认 → N3） | ❌ 固定 N2 | 否 |
| 3 | 结束节点 | `END` | **圆角矩形** | 宽 120 × 高 48，圆角 12 | 0 | ❌ 固定 N3 | 否 |
| 4 | 审批节点 | `APPROVE` | **直角矩形** | 宽 140 × 高 52，圆角 0 | 1 | ✅ | 是 |
| 5 | 抄送节点 | `CC` | **直角矩形** | 宽 140 × 高 52，圆角 0 | 1 | ✅ | 是 |
| 6 | 条件分支 | `CONDITION` | **斜正方形** | 菱形（正方形旋转 45°），对角线 96 | N | ✅ | 是 |
| 7 | 并行开始 | `FORK` | **上半圆** | 宽 120 × 高 60，顶部半圆（下半为直边） | N | ✅ | 是 |
| 8 | 并行结束 | `JOIN` | **下半圆** | 宽 120 × 高 60，底部半圆（上半为直边） | 1 | ✅ | 是 |
| 9 | 机器人节点 | `ROBORT` | **直角矩形** | 宽 140 × 高 52，圆角 0 | 1 | ✅ | 是 |
| 10 | 人工决策 | `MANUAL` | **斜正方形** | 菱形（正方形旋转 45°），对角线 96 | N | ✅ | 是 |

物料面板交互与图形实现要点：

1. **物料面板分组**：分「固定节点」（START/DRAFT/END，展示但**不可拖拽新增**，画布上必有且唯一）与「可添加节点」（其余 7 类，可拖拽）两组。
2. **图形识别辅助**：直角矩形按节点类型叠加类型角标或底色区分（审批/抄送/机器人同为直角矩形但语义不同）——建议保留矩形主体形状，物料面板与画布节点内显示「节点名称 + 类型中文名」两行文本，避免仅靠形状产生歧义。
3. **文字溢出**：节点名称超宽时节点内省略号截断，`title` 悬浮展示全名。
4. **默认节点命名**：拖入时可添加节点默认命名「审批节点」「抄送节点」等；批准节点建议自动补序号（如「审批节点1」），**序号仅作展示，不参与流转**（流转按 `node_key`）。
5. **自定义节点注册**：10 类节点以 LogicFlow 自定义节点注册（继承矩形/菱形/半圆三种 base），`node_type` 写入节点 `properties`，序列化时直接取用。

### 4.4 画布交互设计

| 交互 | 触发方式 | 行为约定 |
| --- | --- | --- |
| 拖拽入画布 | 从左侧物料面板拖拽 | 落点生成新节点，自动分配 `node_key`（`N4`、`N5`… 顺延，取当前画布未占用的最小可用序号） |
| 节点移动 | 拖拽节点本体 | 仅改变坐标（`x`/`y`），不改变图结构；**坐标不落库、不随保存提交**（T-08 定稿，2026-11-21 回写，见 4.6-4）：load 后画布按拓扑自动分层布局，节点移动仅影响本次会话内的画布视觉排布与「自上而下、自左而右」的出现顺序口径 |
| 拉出连线 | 从节点锚点（左/右/上/下四锚点）按住拉出到目标节点 | 生成连线；连线方向即流转方向（`from_key` → `to_key`） |
| 删除节点 | 选中 + `Delete` 键 / 右键菜单「删除」 | **固定节点 `N1`/`N2`/`N3` 不可删**：命中时拦截并提示「开始/起草/结束节点为固定节点，不可删除」 |
| 删除连线 | 选中连线 + `Delete` 键 / 右键菜单「删除」 | 可删；删除后若图不连通，交由保存期校验统一提示（见 4.6） |
| 出边数约束提示 | 拉线时 | 出边数达上限的类型（START/DRAFT/APPROVE/CC/ROBORT/JOIN 为 1）禁止再拉出第二条，提示「该节点类型只允许一条流出连线」 |
| 分支连线配置 | **点击条件分支/并行分支节点的出边连线** | 打开「分支配置」弹窗（见 4.5） |
| 节点配置 | **双击节点**（或选中后点右侧【配置节点】） | 打开节点配置弹窗（见第 5 章）；START/DRAFT/END/CONDITION/JOIN **无节点配置弹窗**（固定节点与分支节点的分支信息在连线上） |
| 画布工具条 | 右上角按钮组 | 放大 / 缩小 / 适应画布 / 撤销 / 重做（撤销重做仅限本次编辑会话，不做服务端历史） |
| 复制粘贴 | 选中节点 + Ctrl+C/V | 可选增强；若实现，复制出的节点 `node_key` **必须重新分配**，节点配置一并复制但 `field_id` 置空 |
| 缩放与平移 | 滚轮 / 拖拽空白区 | 平移与缩放在提交时**不落库**（仅坐标变更落 `nodes[]`） |
| 脏数据提示 | 任何图结构改动 | 置 `dirty` 标记；【关闭】/路由离开时二次确认「流程图已修改，确认离开？」（对齐现有表单页离开保护口径） |

### 4.5 分支配置弹窗（条件分支 / 并行分支连线）

对 `CONDITION` 与 `FORK` 节点的**出边**逐条配置分支信息（数据落**连线表**，不在节点配置中冗余）：

```text
| 分支配置 - {节点名称}                                          （×）|
|---------------------------------------------------------------------|
| 分支名称   [_________________________]                              |
| 分支序号   [ 2 ]  （只读，由列表顺序决定）                            |
| 条件公式   [ 只读回显 ]                              [ 编辑 ]        |
| 默认分支   (●) 是   ( ) 否      仅「条件分支」节点显示                |
|---------------------------------------------------------------------|
| 分支明细（同一节点的全部出边，按分支序号升序）                        |
| 序号 | 分支名称 | 条件公式（摘要） | 默认 | 操作（上移/下移/配置/删除）|
|  1   | 金额较大 | ${amount} > 1000  |      | ↓ 配置 删除              |
|  2   | 其他     | （空）            | 默认 | ↑ 配置 删除              |
|---------------------------------------------------------------------|
|                                        [关闭]  [确定]               |
```

字段与控件规格：

| 字段 | 配置键（连线表列） | 控件 | 约束 |
| --- | --- | --- | --- |
| 分支名称 | `field_branch_name` | `el-input` | 可空，最长 200 |
| 分支序号 | `field_branch_order` | 只读文本 | 由「上移/下移」决定的列表顺序，**上移下移即改此值**；引擎按序号升序求值 |
| 条件公式 | `field_condition` | 只读 `el-input` + 【编辑】 | 条件分支出边必填（**默认分支可空**，作为所有条件为 `false` 时的兜底出边——与下方弹窗示意中默认分支条件为空一致，P17-FE-002 实施口径回写）；并行分支出边为「分支启动条件」，可空（空 = 无条件激活） |
| 默认分支 | `field_is_default` | `el-radio-group`（是/否） | **仅条件分支节点显示**；同一 `CONDITION` 节点的出边中**有且仅有一条**默认为「是」（互斥单选取代，切换时自动清旧值） |

交互约定：

1. **分支上移/下移**：仅在同一节点的出边集合内交换相邻两条的 `field_branch_order`，界面即时刷新「分支序号」列；上移/下移反映的是**引擎求值顺序**（条件分支取第一个 `true` 的出边）。
2. **条件公式编辑复用公式弹窗**：点【编辑】打开统一公式编辑弹窗（`FormulaDesigner`，见 5.4），回填后仍须满足「必须是判断公式 + 占位符仅限当前分类文档实体字段」。
3. **默认分支兜底**：条件分支的所有条件均为 `false` 时走默认分支出边；客户端在【确定】时校验「默认分支有且仅有一条」，否则提示「条件分支节点必须指定且仅指定一条默认分支」。
4. **并行分支的启动条件**：可空；`FORK` 节点另在**节点配置弹窗「基本（汇聚配置）」**中配置汇聚模式与无可流转分支行为（见 5.3）。
5. **连线类型**：`field_line_type` 由**源节点类型**决定（`NORMAL` / `CONDITION` / `PARALLEL` / `MANUAL`），前端只读推导、不手工选择；保存时随连线一并提交。

### 4.6 graph ⇄ `nodes[] + lines[]` 双向转换与保存数据组装

**加载（load → graph）**：`loadProcessTemplate` 响应提供 `nodes`（含 `nodeKey`、`nodeType`、`nodeName`、坐标与 `nodeConfig`）与 `lines`（含 `fromKey`、`toKey`、`lineType`、`branchOrder`、`branchName`、`condition`、`isDefault`）。

```text
nodes[] → LogicFlow nodes：{ id: nodeKey, type: 节点类型 → 自定义节点名,
                             x, y, properties: { nodeKey, nodeType, nodeName, nodeConfig } }
lines[] → LogicFlow edges：{ id: 连线 id 或 fromKey_toKey, sourceNodeId: fromKey, targetNodeId: toKey,
                             properties: { lineType, branchOrder, branchName, condition, isDefault } }
```

**保存（graph → 请求体）**：

```text
提交体 = {
  fieldId, fieldName, fieldCode, fieldModelName, fieldRemark, fieldEnable,   // 现状字段，语义不变
  nodes: [ { fieldId, nodeKey, nodeType, nodeName, fieldNo, x, y, nodeConfig } ],
  lines: [ { fromKey, toKey, lineType, branchOrder, branchName, condition, isDefault } ]
}
```

组装规则：

1. **`field_no` 保留为展示序号**：由前端按画布「自上而下、自左而右」的节点顺序重新编号（仅展示，不参与流转），固定节点 START=1、DRAFT=2、END 置末，保持与现状列表语义连续。
2. **连线全量替换**：`lines` 为当前画布的**全量集合**（不等量增量），后端按「全量删除重建，同节点模式」处理（对齐 P17-BE-006 契约）。
3. **节点 `node_config`**：审批/抄送/机器人/人工决策/并行开始的配置对象按第 5 章字段序列化；固定节点与条件分支/并行结束**不提交 node_config**。
4. **坐标不落库（T-08 定稿）**：load 响应 `nodes` 不含 `x`/`y`，画布每次加载按拓扑自动分层布局（自上而下、自左而右）；保存**不提交坐标**，节点移动仅影响本次会话画布排布与出现顺序（节点键/`field_no` 归一口径），**前端不得依赖坐标参与流转语义**。
5. **保存校验（客户端先行，后端兜底）**：至少提示「N1 起连通可达 N3、无孤立节点、出边数符合类型约束、条件分支默认边唯一、FORK/JOIN 成对、人工决策默认流向合法、审批节点 operations 必含且仅含一个 `handler_pass`」中的**可前端判定项**；其余交后端统一返回中文错误提示（前端按 message 展示，不做错误码翻译）。
6. **保存成功**：`markTabToClose(route.path)` 后 `router.push('/processTemplate')`（沿用现状写法）。

---

## 5. 节点配置弹窗设计

> v1_7 待实现（P17-FE-003）。现状 `TemplateFormView.vue` 的配置弹窗仅有「基础 / 审批权限」两标签且审批人地址本限 `orgType="PERSONPOST"`，v1_7 需按节点类型扩展为多标签并按 5.1 扩展字段。

### 5.1 标签结构（按节点类型渲染）

**固定节点（START/DRAFT/END）、条件分支（CONDITION）、并行结束（JOIN）无节点配置弹窗**（双击不弹窗；固定节点名称为固定中文名，分支信息在连线上配置）。

| 节点类型 | 标签结构 | 标签内容概要 |
| --- | --- | --- |
| 审批节点 `APPROVE` | **基本 / 审批操作 / 审批权限 / 流程任务** | 基本=审批源与审批人、审批方式、跳过与驳回目标；审批操作=三行明细表；审批权限=`ops` 操作标识开关（现状不变）；流程任务=任务明细表 |
| 抄送节点 `CC` | **基本 / 流程任务** | 基本=节点名称、抄送源、抄送对象；流程任务=任务明细表 |
| 人工决策 `MANUAL` | **基本** | 节点名称、流向决策、默认流向 |
| 机器人节点 `ROBORT` | **基本（执行任务）** | 节点名称、执行任务明细表 |
| 并行开始 `FORK` | **基本（汇聚配置）** | 节点名称、汇聚模式、无可流转分支时行为 |

弹窗骨架：

```text
| 节点配置：{节点名称}                                       （×）|
|----------------------------------------------------------------|
| [基本] [审批操作] [审批权限] [流程任务]      ← el-tabs，按节点类型渲染 |
|----------------------------------------------------------------|
|  标签内容区（el-form label-width 统一，一行一字段为主）           |
|----------------------------------------------------------------|
|                                    [关闭]  [确定]               |
```

- 弹窗 `width` 建议 ≥ 760px（审批操作明细表与任务明细表需要横向空间）；`:close-on-click-modal="false"`（沿用现状）。
- 【确定】执行客户端校验并回写节点 `node_config`，**不立即调接口**（随画布整体保存提交）；【关闭】放弃本次编辑。
- 标签名称随节点类型动态生成；旧模板（无新字段）打开弹窗时**按默认值回显**，不因缺字段报错。

### 5.2 审批节点 · 基本标签

| 字段 | 配置键 | 控件 | 约束与说明 |
| --- | --- | --- | --- |
| 节点名称 | `field_node_name` | `el-input` | 必填 |
| 审批源 | `handlerSource` | `el-select` | `org`（地址本，默认）/ `field`（表单字段，沿用）/ `formula`（公式定义器，v1_7 新增） |
| 审批人 | `handlerIds` / `handlerNames` | **地址本多选**（`AddressBook`） | 审批源=`org` 时必填；`orgType="ALL"` + `multi` + **`showRole`**（覆盖机构/部门/人员/岗位/群组/角色**六类**） |
| 审批人解析方式 | `handlerResolveMode` | `el-radio-group` | `leader`（负责人，默认）/ `member`（成员）；**仅当所选对象含部门或岗位时显示**，无部门/岗位时置灰或隐藏并提示「仅对部门/岗位生效」 |
| 审批人字段 | `handlerField` | `el-select`（可筛选） | 审批源=`field` 时必填；选项来自该分类 `process_template.${fieldModelName}.fields` 字典 |
| 审批人公式 | `handlerFormula` | 只读 `el-input` + 【编辑】 | 审批源=`formula` 时必填；经**统一公式编辑弹窗**编辑（见 5.4），结果按「文本/Map/List」解析为组织对象 |
| 审批方式 | `approveMode` | `el-select` | `single` 单签（默认）/ `serial` 串行（v1_7 新增）/ `countersign` 会签 |
| 相同审批人是否跳过 | `skipMode` | `el-select` | `system` 同系统配置 / `none` 不跳过 / `prev` 上一审批节点审批人相同时跳过 / `any` 已审批人相同时跳过；**旧值兼容**：读到的 `yes`/`no` 在前端映射为 `prev`/`none` 显示，**不改写存量 JSON** |
| 是否允许连续跳过 | `autoContinueSkip` | `el-select` | `system`（默认）/ `yes` / `no`（沿用现状） |
| 无审批人时是否跳过 | `emptyHandlerMode` | `el-select` | `system`（同系统，默认跳过）/ `skip` 跳过该节点 / `error` 不跳过（转流程异常） |
| 驳回目标 | `rejectTargets` | `el-select multiple` / 复选列表 | 候选 = **起草节点 N2 + 图上游审批节点**（前端按当前图计算可达上游）；空 = 默认仅起草节点；候选展示为「节点名称（`node_key`）」 |

字段联动：切换「审批源」时清空并隐藏其他来源的字段（沿用现状 `confirmConfig` 的「按来源保留对应明细」写法），避免残留值被一起提交。

### 5.3 其余节点类型的标签与字段

**抄送节点 CC**

| 字段 | 配置键 | 控件 | 约束 |
| --- | --- | --- | --- |
| 节点名称 | `field_node_name` | `el-input` | 必填 |
| 抄送源 | `ccSource` | `el-select` | `org`（地址本）/ `formula`（公式定义器） |
| 抄送对象 | `ccIds` / `ccNames` | 地址本多选（`orgType="ALL"` + `multi` + `showRole`） | 抄送源=`org` 时必填；六类全类型 |
| 抄送对象公式 | `ccFormula` | 只读 `el-input` + 【编辑】 | 抄送源=`formula` 时必填；解析规则同审批人公式 |

**人工决策 MANUAL**

| 字段 | 配置键 | 控件 | 约束 |
| --- | --- | --- | --- |
| 节点名称 | `field_node_name` | `el-input` | 必填 |
| 流向决策 | `flowDecisions` | `el-select multiple` / 复选列表 | 候选 = **起草节点 N2 + 该节点图上游（可达上游）的审批节点**；多选 |
| 默认流向 | `defaultFlowKey` | `el-select` | 选项 = **本节点的实际出边目标节点**（`node_key`）；必填且必须是出边目标之一 |

**机器人节点 ROBORT（基本 · 执行任务）**

| 字段 | 配置键 | 控件 | 约束 |
| --- | --- | --- | --- |
| 节点名称 | `field_node_name` | `el-input` | 必填 |
| 执行任务 | `tasks` | 明细表（见 5.5） | 节点进入时按序执行，任一失败转流程异常 |

**并行开始 FORK（基本 · 汇聚配置）**

| 字段 | 配置键 | 控件 | 约束 |
| --- | --- | --- | --- |
| 节点名称 | `field_node_name` | `el-input` | 必填 |
| 汇聚模式 | `joinMode` | `el-radio-group` | `all`（全部激活分支到达后流出）/ `any`（任一到达即流出） |
| 无可流转分支时行为 | `noBranchMode` | `el-radio-group` | 报错（转流程异常）/ 继续（全部出边分支均激活）；**T-09 已定稿（2026-11-21）**：键名 `noBranchMode`（后端 `SysProcessTemplateNodeConfigDo` 已实现，error 缺省） |

### 5.4 公式编辑弹窗复用（`FormulaDesigner`）

分支条件、审批人公式、抄送对象公式**共用同一公式编辑能力**，冻结两条实现路径：

| 路径 | 适用 | 说明 |
| --- | --- | --- |
| **A（推荐，v1_7 目标）** | 统一公式编辑 | 复用公共组件 `components/formula/FormulaDesigner.vue`（v1.4 已落地，契约见 `docs/系统模块/前端公共组件.md` 第十节、UI 文档 §4.11.7）；`expectedReturnType` 条件分支/并行启动条件传 `BOOLEAN`，审批人/抄送对象公式按解析语义传 `STRING` 或留空（宽松模式） |
| **B（回退兼容）** | 现状形态保留 | `TemplateFormView.vue` 现内置的「左侧 fields 芯片 + 公式计算符芯片 / 右侧文本域，点击插入光标处」弹窗形态（约 202~249 行模板 + `insertCondition` 方法）；仅在 A 因环境不具备（如公式服务不可用）时保留 |

无论走 A 还是 B，交互契约一致（方案文档 §5「分支条件与公式编辑弹窗」冻结）：

1. 入口统一为**不可编辑 `el-input` + 【编辑】按钮**，点击打开弹窗，**不允许在表单里直接手改公式**；
2. 弹窗左右分栏：**左侧**可点选内容区两块——`fields`（当前分类文档实体字段字典，点击插入 `${字段属性名}` 占位符）与**公式计算符**（`+ - * / ( ) > >= < <= == != && || !` 等）；**右侧**为可编辑文本域（支持直接键盘输入）；
3. 左侧点选某项 → 文本插入右侧文本域**当前光标处**（有选中文本则替换选中内容），插入后**光标移动到插入内容之后**；
4. 【确认】把右侧文本回填到原只读输入框并关闭弹窗；【取消】放弃本次编辑；
5. 回填后仍须满足「条件公式必须是判断公式 + 占位符仅限文档实体字段」；**最终权威校验在后端**（公式定义器 `validate`），前端不做语法裁决。

### 5.5 流程任务明细表（审批 / 抄送 / 机器人节点通用）

同一张明细表组件在三类节点的「流程任务」标签（机器人节点为「基本」标签内）复用：

```text
| 任务名称 | 执行任务（下拉） | 执行步骤（下拉） | 任务参数（JSON 文本） | 操作 |
| 归档     | documentArchive  | EndNode          | {"keep":true}         | 删除 |
| [+ 添加任务]                                                          |
```

| 列 | 字段 | 控件 | 约束 |
| --- | --- | --- | --- |
| 任务名称 | 明细行 `taskName` | `el-input` | 必填（仅作展示名） |
| 执行任务 | 明细行 `taskBean`（@ProcessTask `name`） | `el-select` | 必填；选项来自**已注册任务清单接口**（实现时以 P17-BE-005 落地的清单接口为准；当前后端尚无该接口，属待实现项） |
| 执行步骤 | 明细行 `taskType` | `el-select` | 必填；选项 = `ProcessTaskType` 八枚举：`CreateProcess`（创建实例）/ `StartProcess`（流程发起）/ `StartNode`（节点进入）/ `StartWorkitem`（工作项开始）/ `SendTodo`（发送待办）/ `EndWorkitem`（工作项结束）/ `EndNode`（节点结束）/ `EndProcess`（流程结束） |
| 任务参数 | 明细行 `params` | `el-input type="textarea"`（JSON） | 可空；填入时做 JSON 合法性前端预检，非法提示「任务参数必须是合法 JSON」 |
| 操作 | — | 删除按钮 | 删行后同节点任务顺序即为执行顺序 |

- 明细表**行序即执行顺序**（引擎按 `tasks` 顺序执行），不提供上移/下移以外的手工排序字段。
- 保存校验（客户端预检、后端兜底）：任务必须在注册清单内、`processTemplate` 匹配、`allowTaskType` 包含所选步骤（后端 fail 提示以接口 message 展示）。

---

## 6. 流程实例详情页改造

> v1_7 待实现（P17-FE-005）。改造对象：`frontend/src/views/process/ExampleDetailView.vue`（现状约 407 行）；图数据来自 `load` 响应（`nodes` + `lines`）。
> **注意（现状做法差异）**：现状「流程实例 / 流程实例节点 / 工作项 / 审批记录」四段为 `el-card` + `el-descriptions` + 明细表；v1_7 新增流程图区时，**新增的只读信息区按 `form-page.css` 的 `.detail-grid` / `.detail-item` / `.is-full` 规范落地**，不再使用 `el-descriptions` 跨行（AGENTS.md 第 6 节硬规范）；存量四段随本任务同批归一（见待确认事项 T-04）。

### 6.1 页面结构（流程图优先）

```text
| 返回  特权操作                                （el-card #header .header-btns）|
|----------------------------------------------------------------------------|
| 流程实例（状态 tag）                                                        |
| 流程名称 | 流程编号 | 所属模块 | 发起人 | 创建时间 | 完成时间 | 当前节点      |
|----------------------------------------------------------------------------|
| 流程图（只读渲染，节点按状态着色）                                            |
|  ┌──────────────────────────────────────────────────────────────────────┐  |
|  │ [开始] → [起草] → [审批：部门负责人] → [条件分支] ─┬→ [审批] → [结束]   │  |
|  │                                        └→ [抄送] → [结束]            │  |
|  └──────────────────────────────────────────────────────────────────────┘  |
|  图例：待流转WAITING 灰 | 处理中PROCESSING 蓝 | 完成FINISH 绿 |          |
|        通过PASS 青 | 驳回REJECT 红                                          |
|----------------------------------------------------------------------------|
| 流程实例节点（辅助列表）｜ 工作项（辅助列表）｜ 审批记录（辅助列表）           |
```

### 6.2 只读流程图渲染

| 项 | 约定 |
| --- | --- |
| 组件 | 只读画布（`components/process/ProcessFlowViewer.vue`，v1_7 已实现 P17-FE-005，与设计器共用 LogicFlow 节点注册；实例详情 + 面板「流程」卡片复用） |
| 数据源 | `loadProcessExample(fieldId)` 响应的 `nodes` + `lines`（实例快照，含 `nodeKey`/`fromKey`/`toKey`）；**实例侧字段名以实现为准**：节点序号 `fieldNodeNo`、节点键 `fieldNodeKey`（viewer 内适配为 nodeKey/fieldNo，不改 graphTransform 签名） |
| 布局 | 按 `lines` 做**自动分层布局**（无坐标数据时按拓扑层级排布）；有实例快照坐标时优先用坐标 |
| 状态着色 | 按实例节点 `fieldStatus` 着色（见下表）；**着色规则为前端展示语义，不改变后端状态值** |
| 连线态 | 已流经连线可用实线/主色，未流经连线用浅色虚线；不改变流转语义（仅视觉）。**口径定稿（P17-FE-005 实施回写，自定项）**：已流经 = 源节点状态 ∈ {FINISH, PASS}（已随出边继续流转）且目标节点已被到达（状态非空且非 WAITING）；REJECT 源节点的出边按未流经展示（驳回未沿出边继续流转）——已流经实线主色 #409EFF、未流经浅灰虚线 #C0C4CC(6,4)，经自定义 flow-line 连线 model 的 properties.style 覆盖 |
| 交互 | 只读：禁止拖拽/连线/删除；双击节点可弹出节点信息提示（节点名称、类型、状态、处理人摘要），不弹配置弹窗。**实现方案（P17-FE-005 实施回写）**：沿用「isSilentMode 静默 + node:dbclick 监听」——LogicFlow v2 安装包源码核实 NODE_DBCLICK 为无条件 emit（静默仅关闭文本编辑/拖拽/锚点/平移缩放），未采用「非静默 + editConfig 全禁 + guards」备选方案；提示形态以 **el-dialog**（append-to-body）承载（备选 el-popover 需节点屏幕坐标，静默模式无公开锚点定位 API） |
| 兼容 | **旧实例（线性迁移后）渲染正常**：迁移已为存量模板/在途实例生成线性连线快照（`DM-xxx-002`），无连线数据的异常实例降级为「按 `field_no` 顺序横向排布 + 提示图数据缺失」。**实现口径（P17-FE-005）**：降级判定 = 连线缺省或连线键无法对齐节点；降级按 `fieldNodeNo` 升序横向排布 + el-alert「图数据缺失，按节点序号排布」；后端 load 暂未返回 lines 前统一走降级（`SysProcessExampleDetailVo` 契约待后端补齐，组件契约就绪） |
| 空态 | `nodes` 为空时显示 `el-empty`「暂无流程节点信息」 |

节点状态着色表：

| 节点状态 | 展示名 | 色板建议 | 说明 |
| --- | --- | --- | --- |
| `WAITING` | 待流转 | 灰（info） | 未到达 |
| `PROCESSING` | 处理中 | 蓝（primary） | 当前停留节点 |
| `FINISH` | 完成 | 绿（success） | 正常结束 |
| `PASS` | 通过 | 青（success 浅色/自定） | 审批通过 |
| `REJECT` | 驳回 | 红（danger） | 被驳回 |

### 6.3 明细列表（保留为辅助）

保留现状三张表并同步 v1_7 字段：

1. **流程实例节点表**：节点编号（`fieldNo`，展示序号）、节点键（`nodeKey`，v1_7 新增展示，便于与后端排查对齐）、节点名称、节点类型（10 类 tag）、节点状态（着色 tag）；节点类型 tag 需补 `CC`/`CONDITION`/`FORK`/`JOIN`/`MANUAL` 五类。
2. **工作项表**：所属节点、处理人、期望处理人、创建时间、完成时间、状态、重新提交流转方式；v1_7 建议补展示 `field_from_workitem_id`（转办/收回来源工作项）以便链路追溯（展示为「来源工作项」可读文本，不做跳转）。
3. **审批记录表**：处理人、处理操作（`process_audit_type` 码值，需补 v1_7 新类型：`HANDLER_REJECT`、`CC_AUTO`、`DRAFTER_EXPEDITE`、`HANDLER_CC`、`HANDLER_TRANSFER`、`HANDLER_TRANSFER_RETRACT`、`PROCESSED_RETRACT`、`PROCESSED_EXPEDITE`、`ADMIN_CHANGE_HANDLER`、`SYSTEM_ERROR`）、审批内容、时间。
4. **流程任务执行日志（v1_7 新增建议展示）**：`sys_process_task_log` 明细（任务名称、执行步骤、是否成功、耗时、失败信息）——是否在本页展示由 P17-FE-005 与 P17-BE-005 联调后定稿（见待确认事项 T-05）。

### 6.4 特权操作入口

- 按钮位置与显隐不变：`#header` 右侧【特权操作】，`hasAuth(['SYS_PROCESS_ADMIN','SYS_PROCESS_EXAMPLE_ADMIN'])` 预判，后端兜底。
- 弹窗操作类型按 `dict.process_admin_operate` 选项渲染，v1_7 **新增「更换审批人」**：

| 操作类型 | 条件字段 | 控件与约束 |
| --- | --- | --- |
| 跳转 `JUMP` | 跳转节点 | `el-select`；候选限**起草节点、审批节点、人工决策节点**（v1_7 扩展，现状仅 APP） |
| 终审通过 `FINISH` / 作废 `ABANDON` / 暂停 `PAUSE` / 恢复 `RESUME` | — | 沿用现状 |
| **更换审批人 `CHANGE_HANDLER`** | 待处理工作项 | `el-select`，选项 = 当前节点 `PROCESSING` 工作项（label 展示处理人姓名） |
| | 更换为 | **地址本单选**（`orgType="PERSON"`，结果须为人员）；仅本实例有效 |
| 意见 | 全部操作 | `el-input type="textarea"`，必填 |

- 提交后刷新详情（`loadDetail()`）；操作留痕由后端写入，前端无需处理。

---

## 7. 审批面板改造（`ProcessApprovalPanel.vue`）

> v1_7 待实现（P17-FE-006）。改造对象：`frontend/src/components/process/ProcessApprovalPanel.vue`（现状约 293 行，已注册在 `components/index.js` 公共组件出口）。
> 现状为「下拉选操作类型 + 工作项 + 流转方式 + 审批意见」的**单下拉形态**，按钮名称硬编码在组件内 `OPERATE_LABELS`；v1_7 改为**按 `panel.operations` 渲染操作按钮**，操作名称取自节点配置。

### 7.1 面板定位与三块卡片（不变）

- 面板**不直接调用接口**：内部仅读 `panel` 数据（`loadProcessExamplePanel`），通过 `submit` 事件把填写内容交给宿主业务文档页 → 由业务模块后端转发流程模块完成操作（契约扩展见第 11 章）。
- 三块卡片保持：**审批**（仅当前用户有可执行操作时可见）/ **审批记录** / **流程**。
- 「流程」卡片 v1_7 起**渲染只读流程图**（逻辑与展示复用第 6 章只读画布组件），节点列表保留为辅助。
- `panel.nodeOps` 业务标识逻辑**维持现有设计不变**（供宿主页控制表单字段/附件可编辑性；本面板不改其计算与消费口径）。

### 7.2 操作按钮来源与 14 类操作标识

**按钮集 = 节点 `operations` 配置 ∩ 用户身份**，由后端 `panel.operations` 计算并返回；前端**按返回结果渲染按钮，顺序与名称以返回为准**。前端保留一份**默认名称兜底映射**（仅当 `panel.operations` 给出裸标识、未带操作名称时使用）：

| # | 操作标识（`operateType`） | 默认名称 | 执行身份 | 面板呈现要点 |
| --- | --- | --- | --- | --- |
| 1 | `drafter_submit` | 起草人提交 | 起草人 | 由宿主文档页承载（面板不提供起草保存/提交，见面板定位）；若该节点被列为流向决策来源，提交时提供**人工决策流向**控件（`flowChoice`） |
| 2 | `drafter_cancel` | 起草人取消 | 起草人 | 按 D2 并入废弃语义；二次确认文案说明「取消即废弃，流程状态转 ABANDON」 |
| 3 | `drafter_retract` | 起草人收回 | 起草人 | 二次确认；成功后流程回起草节点 |
| 4 | `drafter_expedite` | 起草人催办 | 起草人 | 二次确认后向当前节点全部 `PROCESSING` 工作项处理人发催办待阅 |
| 5 | `handler_pass` | 通过 | 当前处理人 | 主按钮（`type="primary"`）；可选**待处理工作项**、**审批意见**、**人工决策流向**（`flowChoice`） |
| 6 | `handler_reject` | 拒绝 | 当前处理人 | 打开**驳回弹窗**：驳回目标（`rejectTargetKey`）+ 重新提交后流转方式（`resumeMode`）+ 意见 |
| 7 | `handler_cc` | 抄送 | 当前处理人 | 打开**抄送弹窗**：地址本多选（`orgType="ALL"` + `multi` + `showRole`，六类全类型）+ 意见 |
| 8 | `handler_transfer` | 转办 | 当前处理人 | 打开**转办弹窗**：待处理工作项 + 被转办人地址本多选（`transferToIds`）+ 意见 |
| 9 | `handler_transfer_retract` | 转办收回 | 原审批人（转办发起人） | 二次确认后原审批人重新持工作项（`A→B→A` 链路） |
| 10 | `processed_retract` | 已审批收回 | 已处理人 | 二次确认；仅节点仍 `PROCESSING` 时可用，文案提示「仅用于正常流转，不能用于已流过的后续节点」 |
| 11 | `processed_expedite` | 已审批人催办 | 已处理人 | 同起草人催办 |
| 12 | `admin_abandon` | 管理员废弃 | 流程管理员 | 二次确认；仅在实例详情页/面板管理员入口出现 |
| 13 | `admin_jump` | 管理员跳转 | 流程管理员 | 打开弹窗：目标节点（起草/审批/人工决策）+ 意见 |
| 14 | `admin_change_handler` | 管理员更换审批人 | 流程管理员 | 打开弹窗：待处理工作项 + 目标用户（人员）+ 意见 |

> 说明：`ADMIN_FINISH`（终审通过）、`ADMIN_PAUSE`（暂停）、`ADMIN_RESUME`（恢复）**不属于上述 14 类操作矩阵**，保持现有特权操作入口与语义不变（见 6.4）；抄送节点自动留痕 `CC_AUTO`、自动跳过 `AUTO_PASS`、流程异常 `SYSTEM_ERROR` 为系统自动生成，不在面板操作内。

### 7.3 按钮渲染与交互规则

1. **按钮形态**：由现有「下拉选操作」改为**按钮组**（主操作 `handler_pass` 用 `type="primary"`，危险操作 `handler_reject`/`admin_abandon` 用 `type="danger"` plain，其余 `default`）；按钮数量多时自动换行（`flex-wrap`）。
2. **按钮名称**：取 `panel.operations` 元素携带的**节点配置操作名称**（审批节点「审批操作」三行明细中的「操作名称」，可编辑、默认操作类型名）；未携带时回退 7.2 默认名称表。
3. **审批意见默认值回填**：选择某操作后，意见框**自动回填该操作的「默认审批意见」**（来自节点配置），用户可改；不同操作切换时按新操作默认值重置（若用户已手输则二次确认是否覆盖——实现口径：仅在意见框为空或等于上一操作默认值时自动覆盖）。
4. **条件字段**：按所选操作动态渲染（工作项、驳回目标、流转方式、抄送人、转办人、目标节点、流向选择），未选中操作时不渲染任何条件字段。
5. **二次确认**：`drafter_cancel`、`drafter_retract`、`handler_transfer_retract`、`processed_retract`、`admin_abandon` 五类操作在提交前 `ElMessageBox.confirm` 提示影响面。
6. **提交事件**：`submit` 事件负载扩展为（与统一操作接口字段对齐，宿主按业务模块转发契约取用）：

```text
emit('submit', {
  exampleId, operateType, workitemId, content,
  resumeMode,        // handler_reject 专用
  rejectTargetKey,   // handler_reject 专用
  flowChoice,        // handler_pass / drafter_submit 且本节点为流向决策来源时携带
  transferToIds,     // handler_transfer 专用（多选人员 id）
  changeToId,        // admin_change_handler 专用
  targetNodeId,      // admin_jump 专用
  ccToIds            // handler_cc 专用（多选 id，';' 连接；字段名定稿 ccToIds，Q-01/P17-BE-008，对齐接口清单 §6.1/§6.4）
})
```

7. **提交后**：面板重新拉取 `panel` 数据（刷新按钮集与审批记录卡片），宿主页面按需刷新单据状态；失败按接口 message 提示，保留用户已填内容（不清空表单）。

8. **实施形态（P17-FE-006 实施回写，2026-11-21）**：

   - 条件字段不按 §7.2「弹窗」措辞单独开弹窗，按任务卡片 P17-FE-006 口径以「**条件字段区**」内联承载：按钮组点击选中操作（再点取消），选中后按 §7.4 表动态渲染对应控件，字段集合与弹窗形态一致；
   - 面板**不提供起草提交按钮**（提交动作由宿主文档页承载）；`operations` 含带 `flowChoices` 的 `drafter_submit` 行时渲染人工决策流向控件（可空、独立于选中操作常驻），**选择变更即 emit `submit`**（`operateType=drafter_submit`）把 `flowChoice` 同步给宿主缓存——该事件是流向同步、不是执行指令，宿主随自身提交动作转发；
   - 面板 emit `submit` 后不自行清空表单：宿主转发完成后调 `completeSubmit(success)`（true → 面板重拉 panel 并清空表单；false → 保留已填内容由宿主提示），另暴露 `refresh()` 供宿主在单据刷新后重拉面板；
   - 大写特权标识（接口清单 §5.4 实际下发形态，后端 `computeOperations` 源码口径）：`ABANDON` / `JUMP` / `CHANGE_HANDLER` 按 `admin_abandon` / `admin_jump` / `admin_change_handler` 语义渲染，`payload.operateType` 归一为小写（对齐统一操作通道 14 类取值）；`FINISH` / `PAUSE` / `RESUME` 无统一通道对应项，按钮照常渲染并**原样透传**，由宿主按 adminOperate 通道转发或忽略；
   - 工作项选择控件按接口清单 §6.2 必填参数表扩展为三档（契约自洽补充，§7.4 表未覆盖处）：`handler_cc` 取本人 PROCESSING 项（引擎 `handlerCc` 必填 workitemId）；`processed_retract` 取本人 FINISH 且所属节点仍 PROCESSING 项（引擎 `processedRetract` 前置一致）；`processed_expedite` 取本人 FINISH/PROCESSING 项（引擎 `processedExpedite` 口径）；`handler_transfer_retract` 按任务卡片缺口登记不渲染工作项选择、不传 `workitemId`（P17-INT-003 处置）。

### 7.4 审批意见与流向选择控件

| 控件 | 触发条件 | 规格 |
| --- | --- | --- |
| 审批意见 | 任何操作 | `el-input type="textarea"`（3 行）；默认值按 7.3-3 回填；`handler_*`/`admin_*` 类操作必填 |
| 待处理工作项 | 通过/拒绝/转办/转办收回/更换审批人 | `el-select`，选项 = `panel.myWorkitems` 中 `fieldStatus === 'PROCESSING'` 的项（label 为处理人姓名） |
| 驳回目标 | `handler_reject` | `el-select`，选项 = `panel.rejectTargets`（后端按节点 `rejectTargets` ∩ 已流经节点计算）；**为空时默认仅起草节点** |
| 重新提交后流转方式 | `handler_reject` | `el-radio-group`，选项来自 `dict.process_resume_mode`（`restart` 重新流转 / `backToNode` 转回驳回节点），**必选** |
| 人工决策流向（`flowChoice`） | 当前操作对应节点在 `panel.flowDecisions` 中（即本节点被某人工决策节点列为决策来源） | `el-select`，**可空**；选项 = 该人工决策节点的**出边目标节点**（`node_key` + 名称）；提示「不选择时按该人工决策节点的默认流向流转」；**后提交覆盖先提交**为后端语义，前端不做特殊处理 |
| 抄送人 | `handler_cc` | 地址本多选（`orgType="ALL"` + `multi` + `showRole`）；必填至少 1 人 |
| 被转办人 | `handler_transfer` | 地址本多选（`orgType="PERSON"` 或 `ALL`，结果须为人员）；必填 |
| 目标用户 | `admin_change_handler` | 地址本单选（人员） |
| 目标节点 | `admin_jump` | `el-select`，选项 = `panel.nodes` 中类型为 `APPROVE` / `DRAFT` / `MANUAL` 的节点 |

### 7.5 审批记录与流程卡片

- **审批记录卡片**：表格列保持（处理人 / 处理操作 / 审批内容 / 时间）；`process_audit_type` 码值需补 v1_7 新增 10 个类型（见 6.3-3），未维护码值时回退显示原始英文标识（现状兜底逻辑不变）。
- **流程卡片**：v1_7 起上半部为**只读流程图**（复用第 6 章只读画布组件，节点状态着色），下半部保留节点列表（节点编号 / 名称 / 类型 / 状态）。面板内画布高度受限，建议不显示工具条、按容器高度自适应缩放。

---

## 8. 流程监控页（`/process/monitor`）

> v1_7 待实现（P17-FE-007）。新增 `frontend/src/views/process/ProcessMonitorView.vue`。
> 页面定位：`SYS_PROCESS_EXAMPLE_ADMIN`（管理员级）视图，覆盖**全部在途/全部实例**，支持状态与异常筛选、详情跳转复用、特批操作入口。

### 8.1 三段式布局（严格按仓库列表页规范）

```text
| 流程名称 | 流程编号 | 所属模块 | 发起人                                  |
| 创建时间(range) | 流程状态 | 异常状态 | 当前节点                          |
|----------------------------------------------------------------------------|
| sort-area(创建时间↑↓/更新时间↑↓)            | 查询                          |
|----------------------------------------------------------------------------|
| 流程名称 | 流程编号 | 所属模块 | 发起人 | 创建时间 | 当前节点 | 流程状态 | 异常标记 | 操作 |
|----------------------------------------------------------------------------|
| total, prev, pager, next, sizes                                            |
```

- **筛选区**：`el-form.filter-form` + `el-row` + `el-col :span="6"`（一行四列）；输入框（流程名称、流程编号、发起人、当前节点）回车触发查询，空值提交 `null`；日期区间用 `DateTimePicker`（`calendar-type="datetimerange"`）；下拉（所属模块、流程状态、异常状态）默认「不限」。
- **工具区**：左侧 `sort-area` 单条件排序（创建时间 / 更新时间，选中项 `.active` + `.sort-arrow` 显示 ↑/↓，默认创建时间降序）；右侧【查询】（本页**无新建按钮**，实例由业务模块创建）。
- **列表区**：本页**无批量操作**（不设复选框列）、**不展示主键 `fieldId`**；末列操作列 `fixed="right"`；分页 `layout="total, prev, pager, next, sizes"`、`page-sizes [10,20,50,100]`，切页大小回第 1 页。
- 样式复用 `list-page.css`，**不在页面内重复定义** `.filter-form` / `.list-toolbar` / `.sort-area` / `.list-pagination`。

> **实施回写（P17-FE-007，2026-11-21）**：后端 `SysProcessExampleQuery`（monitorList 契约）无「当前节点」筛选字段，本节示意第 2 行第 4 格的「当前节点」筛选**本期不落**，该筛选位由**「仅看在途」开关**（`inFlightOnly` boolean，后端已备、本节示意未列）补位；排序区仅落**创建时间**单条件（后端 `normalizeSortField` 白名单仅 `createTime`，示意的更新时间排序后端未承载，`sortField`/`sortOrder` 仍按 `{ body, pages, sortField, sortOrder }` 形态透传）。

### 8.2 状态与异常筛选

| 筛选项 | 控件 | 说明 |
| --- | --- | --- |
| 流程状态 | `el-select`（选项来自 `dict.process_example_status`） | `DRAFT` / `PROCESSING` / `RETURN` / `PAUSE` / `FINISH` / `ABANDON`；空 = 全部（**管理视角默认不限**，与实例页默认不同） |
| 异常状态 | `el-select` | 选项：**全部 / 仅异常 / 排除异常**；「异常」语义 = 流程处于 `PAUSE` 且最近一条审批记录类型为 `SYSTEM_ERROR`（**待确认事项 T-02**：若后端 `monitorList` 提供专门异常标志字段，则前端优先消费该字段，不自行推导） |

- 列表行**异常标记列**：异常实例显示 `el-tag type="danger"`「流程异常」（`title` 提示错误摘要，摘要文本由后端在 `monitorList` 响应中提供）。

> **实施回写（P17-BE-015 / P17-FE-007，2026-11-21）**：T-02 已定稿——异常标志由 `monitorList` 行内 **`exceptionFlag`** 承载（存在 `SYSTEM_ERROR` 留痕或状态 `PAUSE`，二者取或），前端直接消费、不自行推导；异常筛选落**两态**（全部 / 仅异常，后端 `exceptionOnly` boolean，缺省 `false`），「排除异常」后端未承载、本期不落（不做前端当前页过滤）；`monitorList` 响应**无错误摘要字段**，异常标记列 `title` 提示不落，异常实例仅显示「流程异常」tag、无异常显示 `-`。

### 8.3 列表列与操作列

| 列 | 字段 | 组件与规格 |
| --- | --- | --- |
| 流程名称 | `fieldName` | 文本，溢出省略 |
| 流程编号 | `fieldCode` | 文本，空显示 `-` |
| 所属模块 | `fieldModelName` | 经 `queryProcessExampleModels()` 分类映射显示中文名 |
| 发起人 | `fieldCreatorName` | 文本 |
| 创建时间 | `fieldCreateTime` | 格式化时间 |
| 当前节点 | `currentNodeName` | 文本 |
| 流程状态 | `fieldStatus` | `el-tag`（`PROCESSING` warning / `FINISH` success / `DRAFT` info / `PAUSE` danger / `RETURN` danger / `ABANDON` info，沿用实例页 `statusTag` 口径） |
| 异常标记 | `exceptionFlag` | `el-tag danger`「流程异常」；无异常显示 `-`（响应无错误摘要字段，`title` 提示不落，见 8.2 实施回写） |
| 操作 | — | 详情、特批操作（link 按钮，`fixed="right"`） |

操作列约定：

1. **详情**：跳 `/processExample/detail/:id`，**复用现有实例详情页**（`meta.tab: 'name'` 单标签复用），不在监控页另建详情页。
2. **特批操作**：仅 `hasAuth(['SYS_PROCESS_ADMIN','SYS_PROCESS_EXAMPLE_ADMIN'])` 显示；点击打开**特权操作弹窗**——与实例详情页 6.4 同一弹窗形态（操作类型：跳转/终审通过/作废/暂停/恢复/**更换审批人**；跳转节点限起草/审批/人工决策节点；更换审批人需选待处理工作项 + 目标用户；意见必填），**复用现有 `adminOperate` 通道 + v1_7 新增更换审批人**；弹窗抽为公共组件 `ProcessAdminOperateDialog`（见 11.2，详情页/监控页共用），打开前按行 `loadProcessExample` 取跳转候选节点与换人候选工作项。
3. 弹窗提交成功后刷新当前页列表（不跳转）。

---

## 9. 我发起的 / 我参与的 / 我的流程统计

> v1_7 待实现（P17-FE-008）。新增 `MyCreatedView.vue`、`MyJoinedView.vue`、`MyStatisticView.vue`。
> 三页均在【审批】一级菜单下，**全员可见**（登录用户即可访问，`SYS_PROCESS_MY_VIEWER` 默认全员授予）。
> **实施状态（P17-FE-008，2026-11-21）**：`MyCreatedView.vue` / `MyJoinedView.vue` 已交付（审批一级菜单仅挂我发起的/我参与的 2 个叶子项，我的流程统计待 P17-FE-009 落地后再补菜单项）。

### 9.1 我发起的（`/approval/created`，`scope = myCreated`）

```text
| 流程名称 | 流程编号 | 所属模块 | 创建时间(range)                          |
| 流程状态 | 当前节点 |                                          |
|----------------------------------------------------------------------------|
| sort-area(创建时间↑↓/更新时间↑↓)            | 查询                          |
|----------------------------------------------------------------------------|
| 流程名称 | 流程编号 | 所属模块 | 创建时间 | 当前节点 | 流程状态 | 操作(详情) |
|----------------------------------------------------------------------------|
| total, prev, pager, next, sizes                                            |
```

- **数据条件**：`queryProcessExampleList({ body: { ...筛选, scope: 'myCreated' }, pages, sortField, sortOrder })`——`scope=myCreated` 由后端强制限本人数据（**前端不得自行拼装发起人条件替代 `scope`**）。
- 三段式与实例列表页同构：筛选区一行四列（流程名称、流程编号、所属模块、创建时间区间、流程状态、当前节点），工具区左侧单条件排序 + 右侧【查询】（**无新建**），列表无复选框、不展示主键、末列 `fixed="right"`。
- **实施注记（P17-FE-008，2026-11-21）**：筛选示意中的「当前节点」筛选**不落**——后端 `SysProcessExampleQuery` 无对应字段（与 §8 监控页偏差同源）；筛选区实落五项：流程名称/流程编号/所属模块/创建时间区间/流程状态。排序实落仅创建时间（默认降序）。
- 操作列仅【详情】→ `/processExample/detail/:id`（复用单标签）。已按冻结口径实施；注：该详情路由 `meta.auth` 为实例查看权限集，仅持 `SYS_PROCESS_MY_VIEWER` 的普通用户点击详情会被路由守卫重定向首页，本期不做路由放权改动（登记观察项，供 P17-QA-003 走查关注）。
- 越权语义：即使前端传入他人 `fieldCreatorId` 也无法越权（后端 scope 强制过滤，P17-BE-015 验收「my* 维度越权数据零泄露」）。

### 9.2 我参与的（`/approval/joined`，`scope = myJoined`）

- 与 9.1 同构，**唯一差异**：`scope: 'myJoined'`（作为审批人/已审批人参与）；筛选区补「我的参与方式」为可选扩展（**待确认事项 T-03**，若后端 `myJoined` 支持区分「待我审批/我已审批」则增加单选框，否则不加）。
- 列表列建议在 9.1 基础上补「我的处理状态」（待我处理 / 已处理 —— 由 `panel.myWorkitems` 语义推导或后端列表字段提供，取后端字段优先）。
- 操作列仅【详情】。
- **实施注记（P17-FE-008，2026-11-21，T-03 已定稿）**：两处扩展均**不落**——后端 `SysProcessExampleQuery` 无参与方式区分字段、list 响应无本人处理状态字段，「参与方式」筛选与「我的处理状态」列均不加（与 9.1 同列集）；两页独立成文件同构实现、不强抽公共组件（登记取舍）。

### 9.3 我的流程统计（`/approval/statistic`）

- 全员可见的统计页，布局与管理员统计页不同（指标口径不同），指标项：**今日发起 / 我的未办结 / 待审 / 今日已审 / 平均耗时**；详见第 10 章。

---

## 10. 流程统计页（`/process/statistic`）与我的流程统计页

> v1_7 待实现（P17-FE-009）。新增 `ProcessStatisticView.vue`（管理员）与 `MyStatisticView.vue`（我的）。
> 两页均**不是**列表页三段式，而是「筛选区 → 指标卡 → 聚合表」的统计布局；两页共用同一个统计组件（`mode` 区分，见第 11 章），避免重复实现。

### 10.1 管理员统计页（`/process/statistic`）布局

```text
| 所属模块 | 模板 | 流程状态 | 时间区间            （筛选区，一行四列）|
|----------------------------------------------------------------------------|
| [指标卡] 流程总数      [指标卡] 在途流程    [指标卡] 已办结    [指标卡] 平均耗时 |
|  1,286                 37                  1,249               3.4 天       |
|----------------------------------------------------------------------------|
| 聚合表切换：[按模板] [按状态] [按人员工作量 TopN]   [查询]                   |
|----------------------------------------------------------------------------|
| 模板名称 | 模板编号 | 流程总数 | 在途 | 已办结 | 平均耗时 | 最短耗时 | 最长耗时 |
|----------------------------------------------------------------------------|
| total, prev, pager, next, sizes（聚合表分页，TopN 表不分页）                  |
```

- **筛选区**：`el-form.filter-form` 一行四列——所属模块（分类下拉）、模板（可空，按模块联动）、流程状态（`dict.process_example_status`）、时间区间（`DateTimePicker` datetimerange）；切换筛选后需重新查询（回车或【查询】）。
- **指标卡区**：四张指标卡（`el-card` + 大字号数值 + 中文标签 + 口径提示 `title`）；数值口径见 10.3。
- **聚合表区**：`el-tabs`（或 `el-radio-group`）切换三个视图：
  - **按模板**：模板名称 / 模板编号 / 流程总数 / 在途 / 已办结 / 平均耗时 / 最短耗时 / 最长耗时；
  - **按状态**：流程状态 / 数量 / 占比；
  - **按人员工作量 TopN**：处理人 / 待处理数 / 已处理数 / 平均处理耗时（TopN 默认 10，不分页）。
- 聚合表**无行级操作**（只读），不展示主键；若后端分页则分页参数与列表页规范一致（`total, prev, pager, next, sizes`、`[10,20,50,100]`）。
- 统计为只读查询，**不侵入流转 SQL**（后端约束，前端只需按接口返回渲染，不假设前端计算聚合）。

### 10.2 我的流程统计页（`/approval/statistic`）布局

```text
| 时间区间（默认今日） | 所属模块          （筛选区，一行四列）|
|----------------------------------------------------------------------------|
| [指标卡] 今日发起  [指标卡] 我的未办结  [指标卡] 待审  [指标卡] 今日已审  [指标卡] 平均耗时 |
|   3                  12                 5              8              1.8 天 |
|----------------------------------------------------------------------------|
| 我的流程明细（按状态/模板聚合的小表，随接口返回渲染）    |
```

- 五项指标为**冻结口径**（方案 §8.3、核心业务「流程监控与统计」）：今日发起 / 我的未办结 / 待审 / 今日已审 / 平均耗时。
- 指标卡为**只读展示**，点击不跳转（如需下钻，本期不做，见待确认事项 T-06）。
- 明细聚合表的列以接口返回为准渲染（**不假设固定列**，对齐仓库数据源消费惯例）；无数据时显示空态。

### 10.3 指标口径（前端展示文案，权威口径以 API 分册为准）

| 指标 | 管理员页口径 | 我的页口径 |
| --- | --- | --- |
| 流程总数 | 筛选区间内创建的实例数（全量） | —（不展示） |
| 在途流程 / 我的未办结 | 状态 ∈ {`DRAFT`,`PROCESSING`,`RETURN`,`PAUSE`} 的实例数 | 本人相关的未办结实例数 |
| 已办结 / 今日已审 | 状态为 `FINISH` 的实例数 | 本人今日完成审批的工作项数 |
| 平均耗时 | 已办结实例「创建 → 完成」的平均时长 | 本人已办结实例的平均时长 |
| 今日发起 | —（不展示，管理员按时间区间筛） | 本人今日创建（提交）的实例数 |
| 待审 | —（不展示） | 当前待本人处理的工作项数 |

> 统计页指标卡需在卡片 `title` 或下方小字注明口径（如「按创建时间统计」），避免与明细列表抽样结果产生歧义（P17-INT-004 口径核对）。

---

## 11. 前端契约

### 11.1 `src/api/process.js` 具名函数清单

> 现状 9 个函数（`queryProcessSettingList` / `saveProcessSettings` / `resetProcessSetting` / `queryProcessTemplateModels` / `queryProcessTemplateList` / `loadProcessTemplate` / `saveProcessTemplate` / `updateProcessTemplate` / `enableProcessTemplate` / `setProcessTemplateDefault` / `queryProcessExampleModels` / `queryProcessExampleList` / `loadProcessExample` / `loadProcessExamplePanel` / `adminOperateProcessExample`）。
> 下表「改造/新增」为 v1_7 待实现（P17-FE-004/005/006/007/008/009 同批落地）；接口路径与请求体以 P17-DOC-002 API 分册为准，本表只冻结**前端函数名与调用形态**。

| 具名函数 | 对应后端接口 | HTTP 方法 | 变更 |
| --- | --- | --- | --- |
| `queryProcessSettingList()` | `/backend/sys/process/setting/list` | GET | 现状不变 |
| `saveProcessSettings(items)` | `/backend/sys/process/setting/save` | POST | 现状不变 |
| `resetProcessSetting(fieldId)` | `/backend/sys/process/setting/reset` | POST | 现状不变 |
| `queryProcessTemplateModels()` | `/backend/sys/process/module/models` | GET | 现状不变（响应分类含 `ops` / `fields` 字典，现状已消费） |
| `queryProcessTemplateList(data)` | `/backend/sys/process/module/list` | POST | 现状不变 |
| `loadProcessTemplate(fieldId)` | `/backend/sys/process/module/load` | GET | **响应扩展**：`nodes`（含 `nodeKey`、坐标、`nodeConfig`）+ `lines`（含 `branchOrder`/`branchName`/`condition`/`isDefault`/`lineType`） |
| `saveProcessTemplate(body)` | `/backend/sys/process/module/save` | POST | **请求体扩展**：`nodes` + `lines` 全量提交 |
| `updateProcessTemplate(body)` | `/backend/sys/process/module/update` | PATCH | 同上（携带 `fieldId`） |
| `enableProcessTemplate(body)` | `/backend/sys/process/module/enable` | PATCH | 现状不变 |
| `setProcessTemplateDefault(body)` | `/backend/sys/process/module/setDefault` | PATCH | 现状不变 |
| `queryProcessExampleModels()` | `/backend/sys/process/example/models` | GET | 现状不变 |
| `queryProcessExampleList(data)` | `/backend/sys/process/example/list` | POST | **请求体扩展** `scope`：`admin`（默认）/ `myCreated` / `myJoined`；`my*` 由后端强制限本人 |
| `loadProcessExample(fieldId)` | `/backend/sys/process/example/load` | GET | **响应扩展**：新增 `lines`（流程图渲染）；实例节点补 `nodeKey` / `fieldRuntime` |
| `loadProcessExamplePanel(fieldId)` | `/backend/sys/process/example/panel` | GET | **响应扩展**：`operations` 改为「按节点 `operations` 配置 ∩ 用户身份」计算并携带操作名称/默认意见；新增 `lines`、`rejectTargets`、`flowDecisions`、可选工作项与抄送所需数据 |
| `adminOperateProcessExample(body)` | `/backend/sys/process/example/adminOperate` | POST | **沿用**（跳转/终审/作废/暂停/恢复）；**目标节点范围扩展** + 更换审批人参数（`changeToId`） |
| **`operateProcessExample(body)`** | `/backend/sys/process/operation/operate` | POST | **新增**（统一操作入口）：`{ exampleId, operateType, workitemId, content, targetNodeId, rejectTargetKey, flowChoice, transferToIds, changeToId, resumeMode }`；**审批面板自身不调用**，供宿主业务页面/流程页面提交使用 |
| **`queryProcessMonitorList(data)`** | `/backend/sys/process/example/monitorList` | POST | **新增**：监控列表（管理员，状态/异常筛选），分页与 `metadata.total` 口径同列表页 |
| **`queryProcessStatisticAdmin(data)`** | `/backend/sys/process/statistic/admin` | POST | **新增**：管理员统计（按模块/模板/状态/时间区间数量、平均耗时、人员工作量 TopN） |
| **`queryProcessStatisticMy(data)`** | `/backend/sys/process/statistic/my` | POST | **新增**：我的统计（今日发起/未办结/待审/今日已审/平均耗时） |
| **`queryProcessTaskOptions()`** | 待定（@ProcessTask 注册清单接口，P17-BE-005 定稿） | GET | **新增（待定稿）**：节点配置「流程任务」明细表「执行任务」下拉选项数据源；接口未落地前不允许前端硬编码任务清单 |

约定：

1. 全部请求经 `src/api/index.js` 的 axios 实例以 `/backend/...` 相对路径发出；**页面只 import 具名函数，禁止直接写 `axios` 或裸 URL**。
2. 列表类请求统一 `{ body, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }`；总数取 `metadata.total`。
3. 单 `id` 查询用 GET + `params`（沿用现状 `load` / `loadPanel` 写法）；含非 id 条件用 POST；更新用 PATCH；删除用 DELETE。

### 11.2 组件目录规划

```text
frontend/src/
├─ api/
│  └─ process.js                                  # 11.1 具名函数（改造 + 新增）
├─ components/process/
│  ├─ ProcessApprovalPanel.vue                    # 现状：审批面板（v1_7 改造，仍从 components/index.js 出口）
│  ├─ ProcessAdminOperateDialog.vue               # v1_7 已实现（P17-FE-007）：特权操作弹窗（自 ExampleDetailView 抽取，实例详情页/监控页共用；不进出口，流程模块内相对导入）
│  ├─ ProcessFlowViewer.vue                       # v1_7 已实现（P17-FE-005）：只读流程图（实例详情 + 面板「流程」卡片复用；不进出口，宿主相对导入）
│  └─ designer/                                   # v1_7 新增目录（P17-FE-001/002/003 + FE-005 exampleGraph）
│     ├─ FlowDesignerCanvas.vue                   # 画布容器（LogicFlow 实例、拖拽/连线/删除、readonly 模式）
│     ├─ FlowNodeMaterials.vue                    # 左侧物料面板（10 类节点，固定节点不可拖拽）
│     ├─ FlowNodeConfigDialog.vue                 # 节点配置弹窗（多标签，按 node_type 渲染）
│     ├─ FlowBranchConfigDialog.vue               # 分支配置弹窗（条件/并行分支连线）
│     ├─ FlowTaskDetailTable.vue                  # 流程任务明细表（审批/抄送/机器人节点复用）
│     ├─ FlowFormulaDialog.vue                     # 公式编辑弹窗（复用 FormulaDesigner 或现状形态，见 5.4）
│     ├─ flowNodes.js                             # 10 类节点注册与形状定义（核心/扩展按需引入）
│     └─ exampleGraph.js                          # v1_7 已实现（P17-FE-005）：实例 VO → 只读图数据适配（状态着色/连线态/降级布局，纯 JS）
└─ views/process/
   ├─ ProcessSettingView.vue                      # 现状不变
   ├─ TemplateManageView.vue                      # 现状不变
   ├─ TemplateFormView.vue                        # v1_7 已改造（P17-FE-004）：接入画布设计器
   ├─ ExampleManageView.vue                       # 现状不变
   ├─ ExampleDetailView.vue                       # v1_7 已改造（P17-FE-005）：只读流程图 + detail-grid 归一 + 特权操作扩展
   ├─ ProcessMonitorView.vue                      # v1_7 新增（P17-FE-007）
   ├─ ProcessStatisticView.vue                    # v1_7 新增（P17-FE-009）
   ├─ MyCreatedView.vue                           # v1_7 新增（P17-FE-008）
   ├─ MyJoinedView.vue                            # v1_7 新增（P17-FE-008）
   └─ MyStatisticView.vue                         # v1_7 新增（P17-FE-009）
```

命名与导出约定：

- 页面组件 `PascalCase` 且以 `View.vue` 结尾；组件 `PascalCase.vue`（`AGENTS.md` 第 7 节）。
- 设计器子组件置于 `components/process/designer/` 下，**默认不进 `components/index.js` 统一出口**（仅在流程模块内部消费）；`ProcessApprovalPanel` 已是公共出口组件，其 props/emits 变更需同步 `docs/系统模块/前端公共组件.md`。
- `ProcessStatisticView.vue` 与 `MyStatisticView.vue`：若共用统计组件，建议抽 `components/process/ProcessStatisticPanel.vue`（`mode: 'admin' | 'my'`）；两者页面仅负责筛选区与权限/菜单语义，聚合渲染由组件承担，避免重复实现（实现取舍记入 T-06）。
- `views/process/` 下所有页面 `export default { name: 'XxxView' }` 显式声明组件名（现状写法，供 `HomeView` 的 KeepAlive `include` 匹配）。

---

## 12. 页面走查清单与待确认事项

### 12.1 页面走查清单（供 P17-QA-003 使用）

| # | 走查项 | 判定标准 | 关联任务 |
| --- | --- | --- | --- |
| 1 | 路由与 `meta` | 5 个新页面路由 path/name/`meta`（title/parent/group/auth）与第 2.2 表逐字一致；`processTemplate/*`、`processExample/detail/*` 动态路由 `meta.tab: 'name'` 保留 | P17-FE-004/005/007/008/009 |
| 2 | 菜单装配 | 【后台管理】→【流程管理】下 5 页齐全；【审批】一级菜单 3 页齐全；无权限账号不可见对应菜单且直访被守卫拦截；`SYS_ADMIN` 可见全部 | P17-FE-007/008 |
| 3 | `activeMenu` 归约 | `/processTemplate/edit|detail/*` → `/processTemplate`；`/processExample/detail/*` → `/processExample`；`/process/monitor`、`/process/statistic` 高亮正确（**验证 `/process/` 分支写在 `/processTemplate/`、`/processExample/` 之后**）；`/approval/*` 分支已存在且不影响其它菜单高亮 | P17-FE-007/008 |
| 4 | 设计器画布 | 10 类节点物料可拖入、图形与 4.3 表一致；`ROBORT` 拼写为 **ROBORT**；锚点拉线、删节点/连线正常；`N1`/`N2`/`N3` 删除被拦截并提示 | P17-FE-001/002 |
| 5 | 分支配置 | 条件分支/并行分支连线点击弹出配置；分支名称/序号/条件公式/默认标记齐全；上移下移即时改变分支序号；条件分支默认分支唯一性校验生效 | P17-FE-002 |
| 6 | graph 转换与保存 | 打开旧模板（含线性迁移数据）画布正确回显；保存提交 `nodes` + `lines` 全量；保存成功后关标签回列表 | P17-FE-002/004 |
| 7 | 节点配置弹窗 | 标签结构按节点类型渲染（APPROVE 四标签 / CC 两标签 / MANUAL 一标签 / ROBORT 一标签 / FORK 一标签）；固定节点、CONDITION、JOIN 双击不弹窗 | P17-FE-003 |
| 8 | 操作明细表 | 审批操作三行（起草人/审批人/已处理人）× {操作类型/操作名称/默认审批意见}；`handler_pass` 必含且仅含一个的校验生效 | P17-FE-003 |
| 9 | 地址本 | 审批人/抄送对象地址本可选六类（机构/部门/人员/岗位/群组/角色，`showRole` 已开）；解析方式控件仅对部门/岗位显示 | P17-FE-003 |
| 10 | 公式弹窗 | 分支条件/审批人公式/抄送对象公式三处入口一致：只读输入框 + 【编辑】；左侧 fields + 运算符插入右侧光标处；确认回填、取消放弃 | P17-FE-003 |
| 11 | 流程任务明细 | 任务名称/执行任务/执行步骤/任务参数四列；步骤下拉为 `ProcessTaskType` 八枚举；行序即执行顺序 | P17-FE-003 |
| 12 | 实例详情流程图 | 只读渲染、五状态着色（WAITING/PROCESSING/FINISH/PASS/REJECT）；旧实例（线性）渲染正常；节点/工作项/审批记录三表保留 | P17-FE-005 |
| 13 | 特权操作 | 【特权操作】按钮仅管理员级可见；跳转节点含起草/审批/人工决策节点；新增「更换审批人」（选工作项 + 目标人员 + 意见必填） | P17-FE-005/007 |
| 14 | 审批面板按钮集 | 按钮来自 `panel.operations`；名称取节点配置操作名称；14 类操作标识全部可按配置出现，未配置的不出现 | P17-FE-006 |
| 15 | 审批面板交互 | 驳回弹窗含驳回目标 + 重新提交流转方式；抄送为地址本多选；流向选择控件仅在节点被列为决策来源时出现且可空；意见默认值按配置回填；`submit` 负载字段齐全 | P17-FE-006 |
| 16 | 面板兼容 | `nodeOps` 业务标识逻辑不变（宿主可编辑性行为零回归）；三块卡片齐全；面板不直接调接口 | P17-FE-006 |
| 17 | 监控页 | 三段式（筛选/排序/列表）；状态 + 异常筛选可用；无批量操作、不展示主键、操作列 `fixed="right"`；分页 layout 与 page-sizes 合规；详情跳转复用实例详情页 | P17-FE-007 |
| 18 | 我发起的/我参与的 | 三段式合规；`scope=myCreated`/`myJoined` 正确消费；全员可见；他人数据不可见（抽样验证） | P17-FE-008 |
| 19 | 统计页 | 指标卡 + 聚合表渲染正确；筛选联动查询；管理员统计（模板/状态/人员工作量）与我的统计（五项指标）口径与第 10.3 表一致 | P17-FE-009 |
| 20 | 硬规范复核 | 列表页三段式与 `list-page.css` 复用（无页面内重复定义）；表单/详情页 `form-page.css`（`.detail-grid`/`.detail-item`/`.is-full`、`#header` 按钮区、不用 `el-descriptions` 跨行）；接口全部经 `api/process.js` 具名函数 | 全部 FE |
| 21 | 构建 | `cd frontend && npm run build` 通过 | P17-QA-003 |
| 22 | 文档一致性 | 本文档与 `docs/系统模块/系统模块UI设计文档.md` §3.3/§4.3.4、`流程核心业务.md` 三处页面/菜单/操作标识描述一致 | P17-DOC-010 |

### 12.2 与现状代码核对的差异清单

| # | 发现 | 影响 | 处置 |
| --- | --- | --- | --- |
| D-01 | 现状 `activeMenu` **无 `/processExample/` 归约分支**（`processExample/detail/:id` 会落到 `return path`）；`/processTemplate/` 分支在 `/formula` 分支之后、顺序上位于 `return path` 之前 | 现状实例详情页菜单高亮依赖「该路径本身就是菜单项」的巧合；新增 `/process/` 后若不按 3.2 顺序，模板/实例页高亮会被误吞 | 本分册冻结 `/processExample/` 补归约 + `/process/` 顺序约束；P17-FE-007/008 落实并在走查项 3 验证 |
| D-02 | 现状 `ProcessApprovalPanel.vue` 使用**下拉选操作类型**且操作名称硬编码（`OPERATE_LABELS`，仅 9 项，含 3 项非 14 类矩阵的管理员操作）；请求语义为 `HANDLER_RETURN`/`DRAFT_BACK` 等**旧标识** | 与 v1_7 `operations` 配置渲染 + `handler_reject`/`handler_transfer` 等新标识不一致 | P17-FE-006 改为按钮组 + `panel.operations` 驱动；旧标识映射仅在过渡期兜底，联调后移除 |
| D-03 | 现状 `TemplateFormView.vue` 内注释引用的文档路径为**历史目录写法**（非本仓库现行 `docs/设计文档/...`） | 违反仓库「无历史标识残留」自检项 | P17-FE-004 重写该文件时同步修正为 `docs/设计文档/sys/process/流程核心业务.md`（不属本文档改动范围，登记为实施注意事项） |
| D-04 | 现状 `ProcessApprovalPanel.vue` / `TemplateFormView.vue` / `ExampleDetailView.vue` / `TemplateManageView.vue` / `ExampleManageView.vue` 均存在同类历史路径注释 | 同上 | 由对应 FE 任务在重写/改动时顺手修正 |
| D-05 | 现状实例详情页使用 `el-descriptions`（非垂直模式无法跨行）承载流程实例信息 | 与 AGENTS.md 第 6 节「详情不用 `el-descriptions` 跨行，用 `form-page.css` 的 `.detail-grid`」不一致 | 随 P17-FE-005 改造同批归一（见 T-04） |
| D-06 | `frontend/package.json` **当前无 `@logicflow/*` 依赖** | 设计器不可直接开工 | P17-FE-001 首步接入依赖并按路由懒加载验证 `npm run build`（风险 R03） |
| D-07 | 地址本现状 `orgType` 允许值里**没有单独表示「含群组/角色」的枚举**（群组页签由「含人员」自动渲染，角色页签由 `showRole` 开关控制） | 「六类全选」需 `orgType="ALL"` + `showRole` 组合，容易漏配 | 本分册在 5.2/5.3/7.2 明确冻结 `orgType="ALL"` + `multi` + `showRole` 写法 |
| D-08 | 现状无「@ProcessTask 注册清单」接口 | 节点配置「执行任务」下拉无数据源 | P17-FE-003 依赖 P17-BE-005 提供清单接口；未落地前**不得前端硬编码任务清单**（登记 T-01） |
| D-09 | 现状流程实例列表/详情/监控均无 `scope` 概念 | 我发起的/我参与的无法复用 | P17-BE-015 先落地 `scope`，P17-FE-008 再消费（任务依赖已在清单中声明） |

### 12.3 待确认事项

| 编号 | 待确认项 | 影响范围 | 建议处置 |
| --- | --- | --- | --- |
| T-01 | @ProcessTask 注册清单接口的路径、响应结构（`name` / `processTemplate[]` / `allowTaskType[]` / 中文描述） | 节点配置「流程任务」标签「执行任务」下拉（P17-FE-003） | 由 P17-BE-005 与 P17-DOC-002 定稿后回填本文档 11.1；定稿前该下拉以「接口未就绪」空态占位，禁止硬编码 |
| T-02 | 流程监控「异常状态」的数据来源：由 `monitorList` 提供专门异常标志字段，还是前端按「状态 = PAUSE 且最近审批记录 = SYSTEM_ERROR」推导 | 监控页筛选与异常标记列（P17-FE-007） | **已定稿（2026-11-21）**：由后端字段承载——`monitorList` 行内透出 `exceptionFlag`（存在 `SYSTEM_ERROR` 留痕或状态 `PAUSE`，二者取或），前端直接消费不自行推导；异常筛选落两态（后端 `exceptionOnly` boolean）；响应无错误摘要字段，异常标记列 `title` 提示不落（§8.2 已回写） |
| T-03 | 「我参与的」是否区分「待我审批 / 我已审批」参与方式筛选 | 我参与的页筛选区（P17-FE-008） | **已定稿（2026-11-21）：本期不加**——后端 `SysProcessExampleQuery` 无参与方式区分字段、list 响应无本人处理状态字段，「参与方式」筛选与「我的处理状态」列均不落（§9.2 已回写）；待后端契约扩展后再评估 |
| T-04 | 实例详情页「流程实例信息区」是否随 P17-FE-005 一并从 `el-descriptions` 迁移到 `form-page.css` 的 `.detail-grid` | 实例详情页布局一致性（D-05） | **已定稿（2026-11-21）：一并迁移**——P17-FE-005 已实施，流程实例信息区为 `.detail-grid` 八字段两列（字段与状态 tag 全保留），新增流程图区同规范 |
| T-05 | `sys_process_task_log` 任务执行日志是否在实例详情页/面板展示 | 实例详情页、审批面板（P17-FE-005/006） | 建议 P17-BE-005 落地后可读后在实例详情页增加「任务日志」辅助表；本期不阻塞主流程。**P17-FE-005 登记（2026-11-21）：本期不加**——task_log 不在 load 响应契约内（`SysProcessExampleDetailVo` 无该字段），详情页未渲染任务日志表；待后端契约扩展后再评估 |
| T-06 | 统计页是否需要指标卡下钻（点击指标卡跳转带条件列表） | 统计两页（P17-FE-009） | 本期不做下钻（保持只读展示）；如需下钻由后续版本单独立项 |
| T-07 | `SYS_PROCESS_MY_VIEWER` 的最终权限码命名与默认全员授予方式（roles.yml + 库两处） | 审批一级菜单 3 页的路由 `meta.auth` 与菜单 `auth` | 以 P17-DOC-005 权限码设计确认结果为准；若命名变化，本文档 2.2/3.1 同步替换 |
| T-08 | 设计器画布坐标（`x`/`y`）是否落库 | 模板保存/回显布局（P17-FE-002/004） | **已定稿（2026-11-21）：不落库**——load 响应 nodes 无 x/y、保存不提交坐标，画布按拓扑自动分层布局（4.4「节点移动」与 4.6-4 已同步回写）；P17-FE-002 已按此实现 |
| T-09 | 并行开始节点「无可流转分支时行为」的配置键名 | 节点配置弹窗 FORK 标签（P17-FE-003） | **已定稿（2026-11-21）：`noBranchMode`**（`error` 转流程异常（缺省）/ `continue` 全部出边激活；后端 `SysProcessTemplateNodeConfigDo` 已实现），§5.3 已回写；前端按定稿键名绑定（P17-FE-003 已实现并同批补 graphTransform `NODE_CONFIG_KEYS.FORK` 白名单） |

---

## 13. 引用与关联文档

| 文档 | 关系 |
| --- | --- |
| [功能实现方案-改造分析与方案设计](../../../v1_7-流程管理/功能实现方案-改造分析与方案设计.md) | 上游权威方案（§3.3 节点类型与图形、§4 审批操作、§5 节点配置、§9 前端设计） |
| [流程管理v1_7研发任务清单](../../../v1_7-流程管理/流程管理v1_7研发任务清单.md) | 任务卡片 4.4（P17-DOC-004）、Wave 3 的 P17-FE-001~009、P17-QA-003 |
| [流程核心业务](流程核心业务.md) | 业务规则真相（节点全集、14 类操作矩阵、解析规则、通知、异常） |
| [系统模块UI设计文档](../../../系统模块/系统模块UI设计文档.md) | 系统模块层页面承接（§3.3 页面承接总表、§4.3.4 流程 v1_7 改造页面） |
| [前端公共组件](../../../系统模块/前端公共组件.md) | 地址本 `AddressBook`（第一节）、公式设计器 `FormulaDesigner`（第十节）props 契约 |
| `frontend/src/router/index.js`、`HomeView.vue`、`views/process/*`、`components/process/ProcessApprovalPanel.vue`、`api/process.js` | 现状代码基线（路由/meta/菜单/页面/面板/接口封装） |

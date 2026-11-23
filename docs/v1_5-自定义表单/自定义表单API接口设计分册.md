# 自定义表单 API 接口设计分册（v1.5）

## 文档信息

| 项目 | 内容 |
| --- | --- |
| 文档定位 | 自定义表单 v1_5 的 **API 接口契约权威分册**（Wave 0 任务 F15-DOC-002 产出）：接口分组、路径与方法、鉴权动作（`ACTION_*`）、请求/响应字段、错误码 `CS_FORM_*` 全集与在线文档注解口径 |
| 目标版本 | v1.5（自定义表单：组件协议 + 表单设计器 + 动态渲染器 + 规则引擎 + 数据引擎） |
| 模块编号 | F15（模块 key `form`，包 `com.eric.base.sys.form`，前端 `api/form.js` / `components/form/` / `views/form/`） |
| 文档版本 | V1.0 |
| 创建日期 | 2026-11-23 |
| 文档状态 | 评审稿（Wave 0 任务 F15-DOC-002 产出；接口未实现，随 F15-BE-006 编码落地并与 Swagger 注解同批收口） |
| 输入依据 | [自定义表单功能详细设计.md](自定义表单功能详细设计.md)（F15-DOC-001 产出，R01~R04/R08 裁决与四件套协议基线）、[自定义表单开发要点.md](自定义表单开发要点.md) 第 9 步（REST 风格建议，§8 转化为仓库矩阵）、[AGENTS.md](../../AGENTS.md) §3 接口矩阵与「接口文档同步（强制）」、[公式定义器功能设计方案.md](../v1_4-公式定义器/公式定义器功能设计方案.md) §5（错误码风格先例）、[流程v1_7接口清单.md](../设计文档/sys/process/流程v1_7接口清单.md)（文档结构与承载方式先例） |

### 1.1 版本变更记录

| 版本 | 日期 | 变更说明 |
| --- | --- | --- |
| V1.0 | 2026-11-23 | 初版：定义面（9 接口）与数据面（6 接口）契约冻结；`ACTION_*` 全集（15 项）与 resolver 判定口径；错误码 `CS_FORM_*` 全集（16 项）与 `CS_FORMULA_*` 复用映射；功能设计 §11 留给本分册的两个开放点定稿（未知字段=拒绝、有数据禁删）；要点第 9 步 REST 风格到仓库矩阵的转化说明 |

### 1.2 事实源优先级与冲突裁决

| 冲突项 | 裁决 |
| --- | --- |
| 业务流程、版本机制业务规则、校验链语义、Schema/规则协议字段 | 以[自定义表单功能详细设计.md](自定义表单功能详细设计.md)为准（§2 Schema 协议、§3 规则协议、§5 版本机制、§6 数据引擎） |
| 接口路径、HTTP 方法、请求/响应字段、`ACTION_*` 常量、错误码编码 | 以本分册为准（功能设计 §11 已将接口终稿与错误码编码显式移交本分册定稿） |
| REST 风格通用建议 | 输入要点文档第 9 步仅为建议，按 §8 映射表转化为仓库矩阵后不再作为契约依据 |
| 表名、字段类型、索引 | 以[自定义表单数据库设计分册]（F15-DOC-003 产出，待编）为准 |

---

## 2. 全局接口约定

### 2.1 路径与方法规则

- 统一前缀：`/backend/sys/form/{次级模块}/{方法}`；`{次级模块}` ∈ `define`（定义面：表单定义与版本管理）/ `data`（数据面：表单数据引擎）。
- 方法规则（对齐 [AGENTS.md](../../AGENTS.md) §3 接口矩阵，全局禁用 `PUT`）：
  - 单 `id` 或无查询条件读取用 `GET`（`/load?fieldId=`、`/versions?fieldId=`、`/data/schema?formId=`）；
  - 含非 `id` 条件的检索用 `POST`（`/list`）；新建与动作型提交用 `POST`（`/save`、`/publish`、`/rollback`、数据面 `/save`、`/validate`）；
  - 更新、启停用只用 `PATCH`（`/update`、`/enable`），禁止 `PUT`；
  - 删除用 `DELETE`（`/delete?fieldId=`）。
- 编辑、启停用、删除、发布、回滚必传被操作对象 `fieldId`。

### 2.2 请求/响应包装

- 响应统一 `BaseResponse`（`{ code, message, data, metadata }`），HTTP 状态恒 200（全工程惯例），异常交 `GlobalExceptionHandler`。
- 列表请求体统一 `{ body, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }`；总数经 `metadata.total` 返回（先例 [流程v1_7接口清单.md](../设计文档/sys/process/流程v1_7接口清单.md) §2.1）。
- 排序：单条件排序，`sortField` / `sortOrder`（`asc` / `desc`），默认创建时间降序（`sortField=createTime`、`sortOrder=desc`）。
- 新建类接口的 `fieldId` 由前端经 `GET /backend/common/initID` 预生成（AGENTS §6 前端约束；该入口无需鉴权，「登录即可」的填写页同样可用）。

### 2.3 鉴权与文档 ID 规则

- 每个接口必须定义 `ACTION_*` 常量（`SysFormConstants`），Controller 与 `FormResolver` 静态引用，**禁止手写字符串**；Controller 经 `authorize(action, documentId)` 做最终鉴权。
- `documentId` 传递：涉及具体对象读写的接口传被操作对象 ID——定义面 `load` / `update` / `enable` / `delete` / `publish` / `rollback` / `versions` 传表单主档 `fieldId`；数据面 `load` / `delete` 传数据行 `fieldId`；纯动作/检索接口（`list`、`save`、`publish` 之外的批量入口、`schema`、`validate`）不传。
- 失败语义：未登录 → `BaseResponse.code = UNAUTHORIZED`；无权限 → `FORBIDDEN`（HTTP 均 200 + code 体系，全工程惯例）；数据归属越权 → `CS_FORM_DATA_FORBIDDEN`（`code = FORBIDDEN` + message 前缀，见 §7）。
- `SYS_ADMIN` 由 `BaseResolver` 自动放行，resolver 分支不得重复列举。
- **双层控制**：resolver 只做「接口级」准入；数据面的数据归属（本人 / 管理员全量）由 Service 层按会话强制判定与过滤（§4.8），不信任请求体传入的用户标识。

### 2.4 Schema 载荷传递约定

- 表单 Schema 以 [自定义表单功能详细设计.md](自定义表单功能详细设计.md) §2.1 协议 JSON 为唯一载荷形态（`SysFormSchemaDo` 协议 POJO，Jackson 序列化，不手工拼串）：`{ formId, formCode, formName, version, fields[] }`，规则内嵌于字段对象 `rules[]`（ADR-06），字段级权限元数据内嵌于 `props.permission`。
- Schema 随定义面 `save` / `update` 整体传递（全量替换、不做差量）：请求体顶层元数据（`fieldId` / `fieldFormCode` / `fieldFormName`）为权威，服务端据此归一载荷协议头 `formId` / `formCode` / `formName`；`version` 由服务端管理（发布/回滚动作回填，请求携带一律忽略）；`fields[]` 为设计器产出的主体内容。
- 前端不向服务端上传 Schema 以外的东西参与校验：服务端校验一律按版本行锁定 Schema 执行，不信任前端校验结果（功能设计 §3.1 / R09 安全面）。
- 资源上限按功能设计 §2.2 常量收口 `SysFormConstants`（`MAX_FIELD_COUNT` / `MAX_NEST_DEPTH` / `MAX_SCHEMA_JSON_LENGTH` / `MAX_RULE_COUNT` / `MAX_RULE_EXECUTIONS_PER_CYCLE` / `MAX_RULE_CHAIN_DEPTH` / `MAX_DETAIL_ROWS` / `MAX_TEXT_LENGTH`；数值为草案，编码期 F15-BE-001 可复核，语义与超限错误码不变）。

### 2.5 接口总览

| 接口组 | 次级模块 | 接口 | 方法 | 鉴权动作前缀 | 准入 |
| --- | --- | --- | --- | --- | --- |
| 表单定义 | `define` | `/list` | POST | `sys.form.define.list` | `SYS_FORM_ADMIN` |
| 表单定义 | `define` | `/save` | POST | `sys.form.define.save` | `SYS_FORM_ADMIN` |
| 表单定义 | `define` | `/load?fieldId=` | GET | `sys.form.define.load` | `SYS_FORM_ADMIN` |
| 表单定义 | `define` | `/update` | PATCH | `sys.form.define.update` | `SYS_FORM_ADMIN` |
| 表单定义 | `define` | `/enable` | PATCH | `sys.form.define.enable` | `SYS_FORM_ADMIN` |
| 表单定义 | `define` | `/delete?fieldId=` | DELETE | `sys.form.define.delete` | `SYS_FORM_ADMIN` |
| 版本管理 | `define` | `/publish` | POST | `sys.form.define.publish` | `SYS_FORM_ADMIN` |
| 版本管理 | `define` | `/versions?fieldId=` | GET | `sys.form.define.versions` | `SYS_FORM_ADMIN` |
| 版本管理 | `define` | `/rollback` | POST | `sys.form.define.rollback` | `SYS_FORM_ADMIN` |
| 表单数据 | `data` | `/schema?formId=` | GET | `sys.form.data.schema` | 登录即可（R06 已定稿） |
| 表单数据 | `data` | `/save` | POST | `sys.form.data.save` | 登录即可（R06 已定稿） |
| 表单数据 | `data` | `/validate` | POST | `sys.form.data.validate` | 登录即可（R06 已定稿） |
| 表单数据 | `data` | `/load?fieldId=` | GET | `sys.form.data.load` | 登录即可 + 数据归属 |
| 表单数据 | `data` | `/list` | POST | `sys.form.data.list` | 登录即可 + 归属强制过滤 |
| 表单数据 | `data` | `/delete?fieldId=` | DELETE | `sys.form.data.delete` | 登录即可 + 数据归属 |

> 数据面「登录即可 + 数据归属」口径原为 **R06 倾向口径**：登录即可提交 / 查询本人数据（按创建人 `field_creator_id` 判定，先例日程归属判定），`SYS_FORM_ADMIN` 可查询全部。**R06 已定稿（2026-11-23，F15-DOC-005），定稿与上述倾向一致**（一期不增设 `SYS_FORM_DATA_ADMIN`），权威口径见 [自定义表单功能详细设计](自定义表单功能详细设计.md) §12.2；本表准入不变。

---

## 3. 定义面接口组（`/backend/sys/form/define/**`）

表单定义与版本管理（功能设计 §5）。准入权限码 `SYS_FORM_ADMIN`（`FormResolver` 分支 `hasAnyRole(SYS_FORM_ADMIN)`；`SYS_ADMIN` 自动放行不列举）。基础路径 `/backend/sys/form/define`。

### 3.1 接口清单

| 接口 | 方法 | 鉴权动作 | 用途 |
| --- | --- | --- | --- |
| `/list` | POST | `sys.form.define.list` | 表单定义列表（管理页三段式列表区） |
| `/save` | POST | `sys.form.define.save` | 新建表单（主档 + 初始草稿行） |
| `/load?fieldId=` | GET | `sys.form.define.load` | 表单详情（元数据 + 草稿 Schema + 当前发布版本 Schema） |
| `/update` | PATCH | `sys.form.define.update` | 更新表单元数据与草稿 Schema（仅写草稿行） |
| `/enable` | PATCH | `sys.form.define.enable` | 启停用（`{ fieldId, fieldEnable }`，只更新自身一行） |
| `/delete?fieldId=` | DELETE | `sys.form.define.delete` | 删除表单（存在实例数据禁删，§7.4 定稿②） |
| `/publish` | POST | `sys.form.define.publish` | 发布：草稿行冻结为不可变版本行 |
| `/versions?fieldId=` | GET | `sys.form.define.versions` | 版本列表 |
| `/rollback` | POST | `sys.form.define.rollback` | 回滚 = 重发布历史版本副本（不经草稿态） |

### 3.2 `POST /list` 表单定义列表

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.define.list`（仅 `SYS_FORM_ADMIN`），无 `documentId` |
| 请求体 | `{ body, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }` |

请求 `body` 筛选条件（对齐管理页筛选区：编码 / 名称 / 状态 / 启用）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fieldFormCode` | string | 否 | 表单编码，模糊匹配 |
| `fieldFormName` | string | 否 | 表单名称，模糊匹配 |
| `fieldStatus` | string | 否 | 主档状态：`DRAFT`（从未发布）/ `PUBLISHED`（存在当前发布版本） |
| `fieldEnable` | boolean | 否 | 启用状态 |
| `fieldCreateTimeBegin` / `fieldCreateTimeEnd` | date | 否 | 创建时间区间 |

响应 `data = List<SysFormVo>`（行字段，`metadata.total` 为总数）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fieldId` | string | 表单主档 ID（即 Schema 协议头 `formId` 同值） |
| `fieldFormCode` / `fieldFormName` | string | 表单编码（全局唯一）/ 表单名称 |
| `fieldStatus` | string | 主档状态 `DRAFT` / `PUBLISHED` |
| `fieldEnable` | boolean | 启用状态 |
| `fieldCurrentVersionId` | string | 当前发布版本行 ID；从未发布为 null |
| `fieldCurrentVersion` | int | 当前发布版本号；从未发布为 null |
| `hasDraft` | boolean | 是否存在 DRAFT 版本行（管理页「设计 / 发布」按钮预判） |
| `fieldCreatorId` / `fieldCreateTime` / `fieldUpdaterId` / `fieldUpdateTime` | — | 公共字段 |

列表行不携带 Schema 内容（体积大）；Schema 内容经 `/load` 获取。

### 3.3 `POST /save` 新建表单

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.define.save`（仅 `SYS_FORM_ADMIN`），无 `documentId` |
| 请求体 | `SysFormSaveRequest` |

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fieldId` | string | 是 | 前端经 `GET /backend/common/initID` 预生成 32 位 ID |
| `fieldFormCode` | string | 是 | 表单编码，全局唯一，保存期查重 |
| `fieldFormName` | string | 是 | 表单名称 |
| `schema` | object（`SysFormSchemaDo`） | 否 | 初始 Schema（设计器已产出时随建随存）；缺省时服务端以空白草稿初始化 `{ formId, formCode, formName, version: null, fields: [] }` |

服务端动作（事务内）：①编码全局查重（重复 → `CS_FORM_CODE_DUPLICATED`）；②`schema` 非空时执行结构校验（§3.5 状态机约束，失败阻断）；③创建主档行（`field_status = DRAFT`、`field_current_version_id` 为空）+ 至多一行的 DRAFT 版本行（功能设计 §5.2 草稿模型：新建从「空白」初始化草稿行）。

响应：`data = SysFormVo`（同 §3.4 形态，含初始化后的 `draftSchema`）。

### 3.4 `GET /load?fieldId=` 表单详情

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.define.load`（仅 `SYS_FORM_ADMIN`），`documentId = fieldId` |
| 请求参数 | `fieldId`（表单主档 ID，`@Parameter` 必填） |

响应 `data = SysFormVo`：§3.2 全部行字段 + 两个可空 Schema 载荷：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `draftSchema` | object / null | DRAFT 版本行内容；无草稿行（已发布未再编辑）为 null |
| `publishedSchema` | object / null | `field_current_version_id` 指向的当前发布版本行内容；从未发布为 null |

消费口径：设计器页以 `draftSchema` 为编辑对象；`draftSchema` 为 null（再编辑场景）时以 `publishedSchema` 初始化画布，首次 `/update` 由服务端创建草稿行（§3.5）；元数据表单页只消费行字段。

### 3.5 `PATCH /update` 更新表单（元数据 + 草稿 Schema）

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.define.update`（仅 `SYS_FORM_ADMIN`），`documentId = fieldId` |
| 请求体 | `SysFormUpdateRequest` |

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fieldId` | string | 是 | 表单主档 ID |
| `fieldFormName` | string | 否 | 名称更新；不携带即不更新 |
| `schema` | object | 否 | 草稿 Schema 全量重写；不携带即不更新；两字段至少携带一项（全空拒绝，普通参数错误） |

**更新范围与状态机约束（功能设计 §5.1 原则 4 / §5.2）**：

- 本接口**只写 DRAFT 版本行**——已发布（PUBLISHED）/ 已替代（DEPRECATED）版本行内容永不更新（发布快照不可变，ADR-06）；对已发布版本的「修正」语义 = 再编辑 → 新版本发布，无任何接口可回写历史版本行。
- 主档 `DRAFT`（从未发布）：更新唯一 DRAFT 行。
- 主档 `PUBLISHED` 且无草稿行：服务端自动创建 DRAFT 行承载本次提交 Schema（即「再编辑」入口——画布已由前端以 `publishedSchema` 初始化，等价于复制当前发布版本为新草稿后应用变更）。
- 主档 `PUBLISHED` 且已有草稿行：原地更新草稿行。
- 表单停用（`field_enable = false`）不阻断本接口（功能设计 §5.2：停用后定义面保留）。
- **编码不可变更**：本接口不含 `fieldFormCode` 字段（编码为保存后稳定标识，如需调整走删除重建）；状态字段不在更新范围（仅由 `/publish` 与 `/enable` 驱动）。
- `schema` 携带时执行结构校验（先于落库，失败阻断）：字段码合法与层级唯一、type 白名单（未注册 type 拒绝，ADR-10）、容器嵌套深度与「明细表内禁容器」（R04）、级联条件合法性（R08 第 4 条）、规则 operator/动作/事件合法且 target 存在 → 失败 `CS_FORM_SCHEMA_INVALID`（逐条附中文原因）；资源上限超限（字段总数 / 嵌套深度 / Schema 体积 / 规则数 / 文本长度）→ `CS_FORM_SCHEMA_LIMIT_EXCEEDED`。

响应：`data = SysFormVo`（更新后的元数据 + `draftSchema` + `publishedSchema`，设计器以服务端归一后的 Schema 刷新画布）。

### 3.6 `PATCH /enable` 启停用

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.define.enable`（仅 `SYS_FORM_ADMIN`），`documentId = fieldId` |
| 请求体 | `{ fieldId, fieldEnable }`（对齐 AGENTS §3 矩阵先例 org） |

语义：只更新主档自身一行，无级联对象（自定义表单无子级）。停用（`fieldEnable = false`）关闭**填写入口与数据提交**（数据面 `/data/schema`、`/data/save` 拒绝，`CS_FORM_DISABLED`），定义面与数据查询保留；启用恢复。停用不改变 `field_current_version_id` 语义（功能设计 §5.4）。

响应：`data = Void`。

### 3.7 `DELETE /delete?fieldId=` 删除表单

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.define.delete`（仅 `SYS_FORM_ADMIN`），`documentId = fieldId` |
| 请求参数 | `fieldId`（表单主档 ID） |

**删除前置校验（§7.4 定稿②）**：`sys_form_data` 中该 `formId` 存在**任意实例数据**（不分版本、不分启用状态）→ 拒绝删除，返回 `CS_FORM_HAS_DATA`；**不提供级联删除**。

通过校验后（事务内）删除主档行 + 该表单全部版本行（含 DRAFT / PUBLISHED / DEPRECATED——无实例数据时历史版本无保留价值）。管理引导：需保留数据时用「停用」（`/enable`）达成「不再使用」的管理目的，而非删除。

响应：`data = Void`。

### 3.8 `POST /publish` 发布表单

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.define.publish`（仅 `SYS_FORM_ADMIN`），`documentId = fieldId` |
| 请求体 | `SysFormPublishRequest { fieldId }`（发布对象 = 表单，冻结其当前草稿行） |

**发布校验链**（功能设计 §5.3，按序全部通过方可发布，任一失败确定性拒绝，不新增校验项）：

| # | 校验 | 失败错误码 |
| --- | --- | --- |
| P1 | 草稿行存在（「无草稿行时直接发布」为非法流转） | `CS_FORM_NO_DRAFT` |
| P2 | Schema 结构校验（字段码 / type 白名单 / 嵌套与明细规则 / 级联条件 / 规则合法性，同 §3.5） | `CS_FORM_SCHEMA_INVALID` |
| P3 | 资源上限（§2.4 常量） | `CS_FORM_SCHEMA_LIMIT_EXCEEDED` |
| P4 | 公式静态校验：服务内调用 F14 公式校验服务（表达式合法、引用字段存在、依赖图无环），不经 HTTP | 复用 `CS_FORMULA_*`（§7.2 映射表） |

**发布成功动作**（事务内，功能设计 §5.2 / §5.1 原则 1）：草稿行原地冻结（版本号回填 = 现存最大版本号 + 1、状态 `DRAFT → PUBLISHED`、`published_at` 回填）→ 原当前发布版本（若有）`PUBLISHED → DEPRECATED` → 主档 `field_current_version_id` 切换至新版本行、主档状态 `DRAFT → PUBLISHED`（首次发布）。表单停用不阻断发布（定义面动作；发布后填写入口是否开启仍由 `field_enable` 决定）。

响应：`data = { fieldVersionId, fieldVersion }`（新发布版本行 ID 与整数版本号）。

### 3.9 `GET /versions?fieldId=` 版本列表

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.define.versions`（仅 `SYS_FORM_ADMIN`），`documentId = fieldId` |
| 请求参数 | `fieldId`（表单主档 ID） |

**参数名定稿说明**：候选为 `?formId=` 或 `?fieldId=`，按仓库惯例定稿 **`?fieldId=`**——仓库矩阵「单 id 读取 GET + `?fieldId=`」先例（`/load?fieldId=`、`/delete?fieldId=`），`versions` 以同一参数名承接表单主档主键；Schema 协议头 `formId` 与主档 `field_id` 同值，仅在协议 JSON 内部使用。

响应 `data = List<SysFormVersionVo>`（按 `fieldVersion` 降序，最新在前）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fieldId` | string | 版本行 ID（数据行 `field_form_version_id` 指向对象） |
| `fieldVersion` | int | 整数版本号（单调递增 1, 2, 3…） |
| `fieldStatus` | string | `DRAFT`（草稿，至多一行）/ `PUBLISHED`（当前发布）/ `DEPRECATED`（被新版本替代） |
| `fieldPublishedAt` | datetime | 发布/回滚时刻；草稿行为 null |
| `fieldCreatorId` / `fieldCreateTime` | — | 公共字段 |

版本行不返回 Schema 内容（体积大；回滚由服务端复制，历史版本 Schema 预览一期不单设接口，如 UI 分册出现诉求再补版本行详情接口并回写本分册）。

### 3.10 `POST /rollback` 回滚版本

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.define.rollback`（仅 `SYS_FORM_ADMIN`），`documentId = fieldId` |
| 请求体 | `SysFormRollbackRequest` |

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fieldId` | string | 是 | 表单主档 ID |
| `fieldVersionId` | string | 是 | 回滚目标版本行 ID |

约束与动作（功能设计 §5.1 原则 5 / §5.2）：

- 目标版本行不存在 → `CS_FORM_VERSION_NOT_FOUND`；目标为 DRAFT 行（草稿不是「历史发布版本」，以草稿为目标等价于「发布草稿」，语义混用拒绝）→ `CS_FORM_VERSION_STATE_INVALID`；合法目标 ∈ `PUBLISHED` / `DEPRECATED`。
- 回滚 = **复制目标版本内容产生新版本行**（版本号 = 现存最大 + 1、状态 `PUBLISHED`、`published_at` = 回滚时刻）→ 原当前发布版本转 `DEPRECATED` → 主档 `field_current_version_id` 指向新行；**不经草稿态一步发布**、**不产生指针回拨**（DEPRECATED 不可逆的恢复路径即本接口）。
- 回滚**不影响既有草稿行**（草稿为未发布的独立工作副本，保留供继续编辑）。
- 表单停用不阻断回滚（定义面动作）。

响应：`data = { fieldVersionId, fieldVersion }`（回滚产生的新版本行 ID 与版本号）。

### 3.11 版本状态机与非法流转拒绝汇总

```text
表单主档 sys_form：DRAFT ──publish──▶ PUBLISHED；启停用走 field_enable（独立字段，只更新自身）
版本行 sys_form_version：DRAFT ──publish──▶ PUBLISHED ──被新版本替代──▶ DEPRECATED
  再编辑 = update 自动创建草稿行（内容=前端以发布版本初始化的画布）；DEPRECATED 不可逆，恢复走 rollback
```

| 非法流转 | 拒绝语义 | 错误码 |
| --- | --- | --- |
| 无草稿行时发布 | `/publish` 前置校验 P1 | `CS_FORM_NO_DRAFT` |
| 终态版本行（PUBLISHED/DEPRECATED）内容更新 | 契约层不可达（`/update` 只写 DRAFT 行）+ 实现层防御性拒绝（双保险登记） | `CS_FORM_VERSION_STATE_INVALID` |
| PUBLISHED → DRAFT（发布不可撤销） | 不设撤销发布接口；修正走「再编辑 → 新版本」 | —（无接口） |
| DEPRECATED → PUBLISHED（恢复） | 仅经 `/rollback`（重发布副本） | —（合法路径） |
| 草稿 Schema 结构/上限不通过时发布 | `/publish` 前置校验 P2/P3 | `CS_FORM_SCHEMA_INVALID` / `CS_FORM_SCHEMA_LIMIT_EXCEEDED` |

---

## 4. 数据面接口组（`/backend/sys/form/data/**`）

表单数据引擎（功能设计 §6）：JSON 存储、保存 / 回显 / 删除 / 查询、服务端动态校验与字段权限裁剪、隐藏字段裁剪、级联复核、公式重算。数据引擎不承载任何具体业务逻辑（功能设计架构原则 3）。基础路径 `/backend/sys/form/data`。

### 4.1 接口清单

| 接口 | 方法 | 鉴权动作 | 用途 |
| --- | --- | --- | --- |
| `/schema?formId=` | GET | `sys.form.data.schema` | 获取填写用 Schema（当前发布版本） |
| `/save` | POST | `sys.form.data.save` | 保存表单数据（一期仅新增） |
| `/validate` | POST | `sys.form.data.validate` | 独立校验入口（与 save 共用校验引擎，不落库） |
| `/load?fieldId=` | GET | `sys.form.data.load` | 数据详情 / 回显（含实例锁定版本 Schema） |
| `/list` | POST | `sys.form.data.list` | 数据列表（归属强制过滤 + 分页） |
| `/delete?fieldId=` | DELETE | `sys.form.data.delete` | 删除数据（创建人或管理员） |

> **`/schema` 接口登记**：本接口为任务卡 F15-DOC-002 所列五接口之外的**必要新增**——填写页（`/form/fill/:formId`，F15-DOC-004 / F15-FE-009）渲染新实例必须经数据面获取当前发布版本 Schema；定义面 `/define/load` 仅 `SYS_FORM_ADMIN` 可用，填写人（登录即可）不可经其取 Schema。消费方：`api/form.js` 补具名函数（如 `getFillSchema`），随 F15-FE-001 登记。

### 4.2 `GET /schema?formId=` 获取填写用 Schema

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.data.schema`（登录即可，R06 已定稿），无 `documentId` |
| 请求参数 | `formId`（表单主档 ID，`@Parameter` 必填） |

前置拒绝：表单不存在 → `CS_FORM_NOT_FOUND`；表单停用（填写入口关闭）→ `CS_FORM_DISABLED`；无已发布版本（主档 DRAFT）→ `CS_FORM_NO_PUBLISHED_VERSION`。

响应：`data = SysFormSchemaDo`（`field_current_version_id` 指向版本行的完整 Schema，协议头 `version` 回填发布版本号）。数据详情页不经本接口取 Schema（经 `/load` 取实例锁定版本 Schema，原则 2「在途实例用原版本」）。

### 4.3 `POST /save` 保存表单数据

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.data.save`（登录即可，R06 已定稿），无 `documentId` |
| 请求体 | `SysFormDataSaveRequest` |

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fieldId` | string | 是 | 数据行 ID，前端经 `GET /backend/common/initID` 预生成（填写页属新建资源，对齐 AGENTS §6 惯例） |
| `formId` | string | 是 | 表单主档 ID；服务端锁定 `field_current_version_id` 指向版本 Schema 求值（原则 3） |
| `businessId` | string | 否 | 外部业务对象弱关联 ID（功能设计 §6.2）；一期不做唯一性约束 |
| `formData` | object | 是 | 字段值载荷：`{ 字段码: 值 }`，明细表字段值为扁平行对象数组（功能设计 §2.1）；**不含 `formVersionId`**——一期提交入口恒取当前发布版本，显式版本提交（v1.8 BPM 按流程绑定版本）随 BPM 适配层扩展请求体时登记 |

**保存校验链**（功能设计 §6.1 按序执行，全部服务端权威；前置两步为资源类拒绝）：

| # | 步骤 | 失败语义 |
| --- | --- | --- |
| S0 | 资源前置：表单存在且启用、存在发布版本 | `CS_FORM_NOT_FOUND` / `CS_FORM_DISABLED` / `CS_FORM_NO_PUBLISHED_VERSION` |
| S1 | 字段白名单校验：`formData` 键（含明细行内键）必须 ⊆ 版本 Schema 字段码白名单，**未知字段拒绝**（§7.4 定稿①） | `CS_FORM_UNKNOWN_FIELD` |
| S2 | 基础校验：必填 / 类型 / 长度 / 枚举选项值 / 明细行结构与行数上限（`MAX_DETAIL_ROWS`） | 字段级错误进 `errors`（见下） |
| S3 | 显隐复算与提交裁剪：按版本 Schema 复算主表顶层显隐规则（`FormRuleEngine` 同一条件求值器），判定隐藏的字段值裁剪不落库；`permission = hidden` 字段无论渲染态一律裁剪（功能设计 ADR-08 持久层）——**裁剪为正常业务行为，不报错** | — |
| S4 | 动态校验：规则 `setRequired` / `setValidate` 与静态校验合并裁决 | 字段级错误进 `errors` |
| S5 | 级联选项复核：提交值 ∈ 父项当前条件允许选项集（R08 第 3 条，`FormRuleEngine` 服务端复核） | `CS_FORM_CASCADE_OPTION_INVALID` |
| S6 | 公式重算：提取 type=formula 字段（主表 + 明细行内）经 `FormulaEngine` 服务内重算，**以服务端结果为准落库**（ADR-09；前端值仅预览，差额不报错不设码）；执行失败阻断保存 | 复用 `CS_FORMULA_*`（§7.2） |
| S7 | 字段权限裁剪：`permission = readonly` 一期 save 为新增落库、无库内基线可比对，提交值照常落库（值变更拒绝语义挂二期实例更新接口，§10 登记）；`permission = hidden` 已在 S3 裁剪 | — |

**校验失败承载方式**：S2/S4 字段级校验不通过 → 抛业务异常，message 前缀 `CS_FORM_DATA_INVALID`（`BaseResponse.code = BAD_REQUEST`，message 含首条错误中文摘要）；**字段级明细统一经 `/validate` 结构化获取**（前端填写页提交前必经 validate 预检，save 期为兜底权威裁决）。S1 / S5 / S6 / S0 为确定性拒绝，直接以对应错误码返回。

响应：`data = { fieldId, formData }`——`formData` 为**服务端最终落库形态**（公式重算值回填、隐藏字段裁剪后 JSON），宿主以此刷新展示（功能设计 §6.3「保存响应携带的最终数据」）。

**一期范围说明**：数据面 save 仅承载新增，**不设实例更新接口**（`PATCH /data/update` 一期不做）——一期页面形态为填写页（新建）+ 数据详情页（只读），无数据再编辑页；实例更新（含 readonly 字段「提交值变更拒绝」语义）随二期评估，如进入范围须补 PATCH 接口并回写本分册（§10 登记）。

### 4.4 `POST /validate` 独立校验入口

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.data.validate`（登录即可，R06 已定稿），无 `documentId` |
| 请求体 | `SysFormDataValidateRequest { formId, businessId?, formData }`（与 save 同构，无 `fieldId`——不落库） |

用途：填写页提交前预检与业务集成方预检通道——服务端动态校验（规则条件必填 / 条件范围 / 条件正则）、级联复核、公式重算等权威裁决前端无法复算（渲染器 `expose.validate()` 仅本地预演），本接口提供**与 save 完全共用同一校验引擎与校验链（S0~S6，同序同语义）**、不落库的预检入口。

**响应形态（校验结果为正常业务结果，非异常）**：`data = { pass, errors, formData }`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pass` | boolean | S2/S4 字段级校验是否全部通过 |
| `errors` | array | 字段级错误明细，见下表；`pass = true` 时为空数组 |
| `formData` | object / null | `pass = true` 时回带服务端重算/裁剪后的最终数据预览（同 save 落库形态）；`pass = false` 时为 null |

`errors[]` 元素结构（功能设计 §6.1 第⑤步定稿：含字段码 / 规则 ID / 错误文案）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fieldCode` | string | 字段码；明细行内字段错误为行内字段码 |
| `rowIndex` | int / null | 仅明细行内错误携带（0 起始行号）；主表字段错误为 null |
| `ruleId` | string / null | 触发错误的规则 ID；基础校验（必填/类型/长度/枚举/行数）为 null |
| `message` | string | 中文错误文案 |

**承载边界**：S2/S4 字段级结果以 `data.errors` 承载（HTTP 200、`code` 为成功，对齐公式 `validate` / `simulate` 先例）；S0 / S1 / S5 / S6 资源与确定性拒绝仍以对应 `CS_FORM_*` / `CS_FORMULA_*` 错误码异常返回（与 save 同语义）。

### 4.5 `GET /load?fieldId=` 数据详情 / 回显

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.data.load`（登录即可 + 数据归属，R06 已定稿），`documentId = fieldId`（数据行 ID） |
| 请求参数 | `fieldId`（数据行 ID） |

前置拒绝：数据行不存在 → `CS_FORM_DATA_NOT_FOUND`；非本人且非管理员（数据归属判定见 §4.8）→ `CS_FORM_DATA_FORBIDDEN`。

响应 `data = SysFormDataDetailVo`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fieldId` | string | 数据行 ID |
| `formId`（响应字段名 `fieldFormId`） | string | 表单主档 ID |
| `fieldFormVersionId` | string | 实例锁定版本行 ID（原则 2：在途实例用原版本） |
| `fieldBusinessId` | string / null | 弱关联业务 ID |
| `fieldCreatorId` / `fieldCreateTime` / 公共字段 | — | 提交人与时间 |
| `formData` | object | 字段值对象——**读取层裁剪后形态**：提交时刻被裁剪的隐藏字段不下发、`permission = hidden` 字段不下发（功能设计 ADR-08 读取层） |
| `schema` | object | 实例锁定版本（`field_form_version_id` 指向行）的 Schema——渲染器详情态 / 编辑态复用（原则 2：不随主档当前版本漂移） |

### 4.6 `POST /list` 数据列表

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.data.list`（登录即可，R06 已定稿），无 `documentId` |
| 请求体 | `{ body, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }` |

请求 `body` 筛选条件（功能设计 §6.2）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fieldFormId` | string | 是 | 表单主档 ID（数据列表在单一表单维度；不存在 → `CS_FORM_NOT_FOUND`） |
| `fieldBusinessId` | string | 否 | 弱关联业务 ID，精确匹配 |
| `fieldProcessInstanceId` | string | 否 | 流程实例 ID，精确匹配——**v1.8 预留筛选**：`field_process_instance_id` 一期不写入，本条件一期恒空结果（功能设计 §6.2 明示支持该筛选，随 v1.8 写入后生效） |
| `fieldCreatorId` | string | 否 | 创建人筛选——仅 `SYS_FORM_ADMIN`（或 `SYS_ADMIN`）生效；非管理员请求忽略该条件（归属强制过滤优先，不信任请求体用户标识） |
| `fieldCreateTimeBegin` / `fieldCreateTimeEnd` | date | 否 | 创建时间区间 |

响应 `data = List<SysFormDataVo>`（`metadata.total` 为总数）：`fieldId` / `fieldFormId` / `fieldFormVersionId` / `fieldBusinessId` / `fieldCreatorId` / `fieldCreateTime` 等行字段；**列表行不携带 `formData` 字段值内容**（JSON 体积不可控，详情经 `/load`）。

### 4.7 `DELETE /delete?fieldId=` 删除数据

| 项 | 契约 |
| --- | --- |
| 鉴权动作 | `sys.form.data.delete`（登录即可 + 数据归属，R06 已定稿），`documentId = fieldId`（数据行 ID） |
| 请求参数 | `fieldId`（数据行 ID） |

前置拒绝：数据行不存在 → `CS_FORM_DATA_NOT_FOUND`；非创建人且非管理员 → `CS_FORM_DATA_FORBIDDEN`（F15-BE-003：删除为创建人或管理员）。

响应：`data = Void`。

### 4.8 数据归属口径（R06 已定稿，F15-DOC-005）

| 语义 | 口径 |
| --- | --- |
| 提交（save / validate） | 登录即可；数据归属 = 创建人（`field_creator_id` = 会话用户，提交时由服务端绑定，先例日程归属判定） |
| 查询本人数据（load / list / delete 本人数据） | 登录即可；Service 层**强制注入** `field_creator_id = 会话用户` 过滤（不信任请求体传入的用户标识，先例流程 `scope=myCreated` 强制注入） |
| 管理员全量查询 | 会话用户持有 `SYS_FORM_ADMIN`（或 `SYS_ADMIN`）时 list 不过滤归属、load / delete 放行他人数据 |
| 三种拒绝语义 | 未登录 → `UNAUTHORIZED`（鉴权层）；无接口权限 → `FORBIDDEN`（resolver 层，登录即可口径下仅未登录触发）；**非本人非管理员访问他人数据 → `CS_FORM_DATA_FORBIDDEN`（`code = FORBIDDEN` + message 前缀，resolver + Service 双层保证）** |

> 本节原为 **R06 倾向口径**。**R06 已定稿（2026-11-23，F15-DOC-005）：定稿与上表倾向一致**——登录即可范围不变、不增设 `SYS_FORM_DATA_ADMIN` 等数据面权限码、归属按创建人判定（Service 层强制），**本节与 §6 准入表 / resolver 分支不变**；权威口径见 [自定义表单功能详细设计](自定义表单功能详细设计.md) §12.2（三种拒绝语义的可独立读通描述亦在彼处）。

---

## 5. 组件物料说明（一期不设后端组件接口）

**定稿**：一期组件物料为**前端静态注册**（`componentRegistry` + `registerComponent`，功能设计 §1.2，任务 F15-FE-002 落地），本模块**不设任何后端组件接口**；要点文档第 9 步的 `GET /form-components` 不落地（§8 映射表）。`sys_form_component` 注册表二期再评估（R03 / R05 裁减输入）。

**理由（四条）**：

1. **组件全集一期为静态已知集**：R03 定稿一期 19 类物料（12 数据 + 4 内容 + 分组/栅格 + 权限协议位），无运行时增删组件诉求；注册协议是前端启动期事实（registry Map + 重复 type 注册抛错 fail-fast），后端无需感知物料实现。
2. **后端仅需 type 白名单**：结构校验器拒绝未注册 type（ADR-10）所需的白名单 = `SysFormConstants` 常量集 + `SysFormComponentCategory` 枚举，随代码版本发布，静态常量即可，不需要注册表存储与查询接口。
3. **物料七件套不可 JSON 下发**：物料含 Vue 组件引用与函数（运行时组件 / 默认值生成器 / 校验器 / 序列化方法），本就是前端运行时对象，HTTP 接口至多能下发「元数据 + 属性 schema」，对渲染器取组件无增益，反增一层同步成本（前端注册表与后端注册表的双源漂移）。
4. **二期触发条件明确**：自定义 Vue 组件接入（外部物料动态注册，存储物料资源与元数据）才需要 `sys_form_component` 注册表与配套注册 / 查询接口，属二期范围；届时补接口契约并回写本分册。

---

## 6. `ACTION_*` 常量全集与 resolver 判定口径

常量统一定义于 `SysFormConstants`（15 项，静态引用禁手写字符串）；常量名风格对齐 `SysProcessConstants`（`ACTION_{模块}_{次级模块}_{方法}`）。

### 6.1 常量清单

| # | 常量名 | 常量值 | 接口 | 准入 |
| --- | --- | --- | --- | --- |
| 1 | `ACTION_FORM_DEFINE_LIST` | `sys.form.define.list` | `POST /define/list` | `SYS_FORM_ADMIN` |
| 2 | `ACTION_FORM_DEFINE_SAVE` | `sys.form.define.save` | `POST /define/save` | `SYS_FORM_ADMIN` |
| 3 | `ACTION_FORM_DEFINE_LOAD` | `sys.form.define.load` | `GET /define/load?fieldId=` | `SYS_FORM_ADMIN` |
| 4 | `ACTION_FORM_DEFINE_UPDATE` | `sys.form.define.update` | `PATCH /define/update` | `SYS_FORM_ADMIN` |
| 5 | `ACTION_FORM_DEFINE_ENABLE` | `sys.form.define.enable` | `PATCH /define/enable` | `SYS_FORM_ADMIN` |
| 6 | `ACTION_FORM_DEFINE_DELETE` | `sys.form.define.delete` | `DELETE /define/delete?fieldId=` | `SYS_FORM_ADMIN` |
| 7 | `ACTION_FORM_DEFINE_PUBLISH` | `sys.form.define.publish` | `POST /define/publish` | `SYS_FORM_ADMIN` |
| 8 | `ACTION_FORM_DEFINE_VERSIONS` | `sys.form.define.versions` | `GET /define/versions?fieldId=` | `SYS_FORM_ADMIN` |
| 9 | `ACTION_FORM_DEFINE_ROLLBACK` | `sys.form.define.rollback` | `POST /define/rollback` | `SYS_FORM_ADMIN` |
| 10 | `ACTION_FORM_DATA_SCHEMA` | `sys.form.data.schema` | `GET /data/schema?formId=` | 登录即可（R06 已定稿） |
| 11 | `ACTION_FORM_DATA_SAVE` | `sys.form.data.save` | `POST /data/save` | 登录即可（R06 已定稿） |
| 12 | `ACTION_FORM_DATA_VALIDATE` | `sys.form.data.validate` | `POST /data/validate` | 登录即可（R06 已定稿） |
| 13 | `ACTION_FORM_DATA_LOAD` | `sys.form.data.load` | `GET /data/load?fieldId=` | 登录即可 + 数据归属 |
| 14 | `ACTION_FORM_DATA_LIST` | `sys.form.data.list` | `POST /data/list` | 登录即可 + 归属强制过滤 |
| 15 | `ACTION_FORM_DATA_DELETE` | `sys.form.data.delete` | `DELETE /data/delete?fieldId=` | 登录即可 + 数据归属 |
| — | —（无新常量） | — | 未登记 action 一律拒绝（`FormResolver` 兜底，最小授权） | — |

### 6.2 `FormResolver` 分支判定口径

继承 `BaseResolver`，按 `switch (context.getAction())` 判定：

| action 前缀 | 判定 | 说明 |
| --- | --- | --- |
| `sys.form.define.` | `hasAnyRole(SYS_FORM_ADMIN)` | 定义面全部 9 个动作统一分支；`SYS_ADMIN` 由 `BaseResolver` 自动放行，**分支内不重复列举** |
| `sys.form.data.` | 登录即可（`resolveLoggedIn()`，先例公式组件四接口） | 数据面 6 个动作统一分支；数据归属（本人 / 管理员全量）由 Service 层强制判定（§4.8），resolver 不做归属判定 |
| 其他 / 未登记 | 拒绝 | 最小授权兜底 |

> **R06 已定稿（2026-11-23，F15-DOC-005）：不增设 `SYS_FORM_DATA_ADMIN` 等数据面独立权限码**，数据面 6 动作维持登录即可 + Service 层归属控制，本节准入表与 §4.8 不变；详见 [自定义表单功能详细设计](自定义表单功能详细设计.md) §12.2。

---

## 7. 错误码 `CS_FORM_*` 全集

### 7.1 错误码表（16 项）

承载方式（对齐数据源 `CS_DATASOURCE_*` / 公式 `CS_FORMULA_*` / 流程 `CS_PROCESS_*` 口径）：**业务错误码以异常 message 前缀承载，`BaseResponse.code` 统一为 `BAD_REQUEST`（鉴权类除外），前端按 message 前缀识别具体错误码**；`UNAUTHORIZED` / `FORBIDDEN` 为鉴权类 code，直接作为 `BaseResponse.code` 返回（不经 message 前缀）。逐条中文原因随 message 附带（面向配置者 / 填写人可读）。

| # | 错误码 | 触发语义 | 承载接口 |
| --- | --- | --- | --- |
| 1 | `CS_FORM_CODE_DUPLICATED` | 表单编码保存期全局查重失败 | `define/save` |
| 2 | `CS_FORM_NOT_FOUND` | 表单主档不存在 | `define/load` / `define/update` / `define/enable` / `define/delete` / `define/publish` / `define/versions` / `define/rollback` / `data/schema` / `data/save` / `data/validate` / `data/list`（`fieldFormId` 不存在） |
| 3 | `CS_FORM_DISABLED` | 表单已停用——填写入口与数据提交关闭（定义面与数据查询保留，§3.6） | `data/schema` / `data/save` / `data/validate` |
| 4 | `CS_FORM_NO_PUBLISHED_VERSION` | 无已发布版本（主档 DRAFT 未发布），填写与提交入口不可用 | `data/schema` / `data/save` / `data/validate` |
| 5 | `CS_FORM_NO_DRAFT` | 无草稿版本行时直接发布（非法流转，§3.11） | `define/publish` |
| 6 | `CS_FORM_SCHEMA_INVALID` | Schema 结构校验失败：字段码非法 / 层级重复、type 白名单外（未注册 type）、容器嵌套深度违规、明细表内嵌容器（R04）、级联条件非法（父项非数据类字段 / operator 或 value 非法 / 引用自身，R08 第 4 条）、规则 operator / 动作 / 事件非法或 target 不存在——逐条附中文原因 | `define/save` / `define/update` / `define/publish` |
| 7 | `CS_FORM_SCHEMA_LIMIT_EXCEEDED` | Schema 资源上限超限（字段总数 / 嵌套深度 / Schema 体积 / 规则总数 / 文本长度，§2.4 常量） | `define/save` / `define/update` / `define/publish` |
| 8 | `CS_FORM_VERSION_STATE_INVALID` | 版本状态非法流转 / 非法目标：回滚目标为 DRAFT 行；实现层对终态行（PUBLISHED / DEPRECATED）内容更新的防御性拒绝（契约层经 §3.5「只写 DRAFT 行」不可达，双保险登记） | `define/rollback`（防御性：`define/update`） |
| 9 | `CS_FORM_VERSION_NOT_FOUND` | 回滚目标版本行不存在 | `define/rollback` |
| 10 | `CS_FORM_HAS_DATA` | 表单存在实例数据，禁删（§7.4 定稿②；不提供级联删除） | `define/delete` |
| 11 | `CS_FORM_UNKNOWN_FIELD` | 提交数据含 Schema 字段白名单外未知字段（任意层级，含明细行内未知键；§7.4 定稿①） | `data/save` / `data/validate` |
| 12 | `CS_FORM_DATA_INVALID` | 数据基础 / 动态校验失败（必填 / 类型 / 长度 / 枚举选项值 / 明细行数超限 / 规则条件必填与条件校验）；message 前缀 + 首条错误中文摘要，**字段级明细经 `data/validate` 的 `data.errors` 结构化承载**（§4.3 / §4.4） | `data/save`（`data/validate` 的字段级结果以 `data.errors` 正常承载、不抛本码） |
| 13 | `CS_FORM_DATA_NOT_FOUND` | 数据实例不存在 | `data/load` / `data/delete` |
| 14 | `CS_FORM_DATA_FORBIDDEN` | 数据归属越权（非本人且非管理员；§4.8） | `data/load` / `data/delete`——`code = FORBIDDEN` + message 前缀 |
| 15 | `CS_FORM_CASCADE_OPTION_INVALID` | 级联选项复核不通过：提交值 ∉ 父项当前条件允许选项集（R08 第 3 条服务端复核） | `data/save` / `data/validate` |
| 16 | `CS_FORM_RULE_CYCLE_EXCEEDED` | 服务端规则复算熔断：单次求值动作执行次数达 `MAX_RULE_EXECUTIONS_PER_CYCLE` 或规则链深度达 `MAX_RULE_CHAIN_DEPTH`（功能设计 §3.5：服务端复算受同语义常量约束，超限抛确定性错误，不允许无限求值） | `data/save` / `data/validate`（显隐复算 / 动态校验 / 级联复核求值期） |

### 7.2 公式错误复用映射（复用 `CS_FORMULA_*`，不新造）

公式相关失败**不新设 `CS_FORM_*` 码**，直接复用 F14 公式定义器错误码语义（[公式定义器功能设计方案.md](../v1_4-公式定义器/公式定义器功能设计方案.md) §5.3 / [系统模块API接口设计文档](../系统模块/系统模块API接口设计文档.md) 4.11.6），映射关系登记如下：

| 触发场景（本模块） | 复用错误码 | 说明 |
| --- | --- | --- |
| 发布校验链 P4：公式静态校验失败（表达式长度超限 / 语法错误 / 未注册函数 / 返回类型与 `expectedReturnType` 不符 / 变量码或 `${list.field}` 投影路径非法） | `CS_FORMULA_EXPRESSION_TOO_LONG` / `CS_FORMULA_SYNTAX_INVALID` / `CS_FORMULA_FUNCTION_NOT_FOUND` / `CS_FORMULA_TYPE_MISMATCH` / `CS_FORMULA_VARIABLE_INVALID` | 服务内调用 F14 校验服务，校验结果 `errors[]` 的 `code` 原样透传为 message 前缀 |
| 保存 / 校验期公式重算执行失败（除零、空值拒绝、集合规模超限等执行期失败） | `CS_FORMULA_EXECUTE_FAILED` | 复用 `FormulaEngine#execute` 既有语义，阻断保存，不裸异常穿透 |
| 公式重算执行超时 | `CS_FORMULA_EXECUTE_TIMEOUT` | 同上 |

### 7.3 不设码行为登记（正常业务行为，非错误）

| 行为 | 处置策略 | 出处 |
| --- | --- | --- |
| 公式字段服务端重算与前端展示值不一致 | **以服务端重算结果为准落库，差额不报错**——ADR-09 已定稿（前端无 AviatorScript 无法复算、前端值不可信、报错制造「算对了却提交失败」伪故障），**不设 `CS_FORM_FORMULA_MISMATCH`**；最终值经 save / validate 响应 `formData` 回带刷新展示 | 功能设计 §0.4 ADR-09 / §6.3 |
| 隐藏字段提交值 / `permission = hidden` 字段提交值 | 服务端**裁剪不落库、不报错**（ADR-08 持久层；读取层同理不下发）——裁剪为数据卫生行为而非错误，**不设码**；差异经 save / validate 响应 `formData` 体现 | 功能设计 §0.4 ADR-08 / §3.6 |
| `permission = readonly` 字段（一期新增 save） | 无库内基线可比对，提交值照常落库（值变更拒绝语义挂二期实例更新接口，§10 登记） | §4.3 S7 |

### 7.4 功能设计 §11 留给本分册的两个开放点（定稿）

**① 未知字段提交策略：拒绝（定稿）**

- **结论**：`data/save` / `data/validate` 对 Schema 字段白名单外（任意层级，含明细行内）的未知字段**拒绝提交**，返回 `CS_FORM_UNKNOWN_FIELD`；不做静默裁剪。
- **理由**：(a) **服务端不信任前端**是本模块安全面基线（功能设计 R09 / §3.1）——白名单校验与显隐复算、级联复核、公式重算同属服务端权威裁决链，静默裁剪等于放行「前端随意构造载荷」；(b) **裁剪会掩盖 Schema 与数据的漂移**：字段更名 / 删除后，旧页面或旧集成方提交的残留字段被静默丢弃，脏数据问题被掩盖且不可观测；拒绝则把漂移暴露给调用方修复；(c) 拒绝语义确定、可测试（F15-BE-003 单测正反例明确），裁剪 + 告警日志的「告警」无人消费、难以纳入验收。
- **与功能设计的差异登记**：功能设计 §6.1 第②步 / §11 登记的倾向为「裁剪 + 告警日志、不报错」——本分册为接口契约权威，定稿为**拒绝**；建议 F15-DOC-006 文档收口时回写功能设计 §6.1 第②步表述（「字段白名单校验（未知字段拒绝，见 API 分册 §7.4）」）。

**② 表单删除存在实例数据：禁删（定稿）**

- **结论**：`define/delete` 在该 `formId` 于 `sys_form_data` 存在**任意实例数据**时**禁止删除**，返回 `CS_FORM_HAS_DATA`；**不提供级联删除**与「确认级联」选项。
- **理由**：(a) **数据安全优先**——表单数据是业务记录（功能设计架构原则 3：数据归业务侧所有，表单引擎只收集与管理），级联删除不可逆且删除范围随数据量不可预估；(b) 功能设计 §11 已登记倾向「存在实例数据禁删（先例查重阻断语义）」，本定稿与倾向一致；(c) 管理目的可达——「不再使用」的管理诉求由**停用**（`/enable`，关闭填写入口与提交、定义面与数据查询保留）承载，删除仅服务「彻底清除无数据表单」场景（无数据时连版本行一并删除，历史版本无保留价值）。
- **与功能设计的关系**：与功能设计 §11 倾向一致，无差异。

---

## 8. 要点文档第 9 步 REST 风格 → 仓库接口矩阵转化说明

[自定义表单开发要点.md](自定义表单开发要点.md) 第 9 步的 REST 资源风格接口为通用建议；按 [AGENTS.md](../../AGENTS.md) §3 接口矩阵逐条转化为仓库「动作后缀」风格如下（转化后要点文档接口规划不再作为契约依据；包结构 domain/repository 映射见功能设计 §7）：

| 要点第 9 步建议 | 本分册定稿 | 转化说明 |
| --- | --- | --- |
| `POST /forms`（创建表单） | `POST /backend/sys/form/define/save` | 资源集合风格 → 动作后缀风格；新建 `fieldId` 前端经 `GET /backend/common/initID` 预生成，不走路径/资源语义 |
| `PUT /forms/{id}`（修改草稿） | `PATCH /backend/sys/form/define/update` | 全局禁用 `PUT`，更新仅 `PATCH`；无路径参数 `{id}`，被操作对象经请求体 `fieldId` 传递 |
| `GET /forms/{id}`（获取表单） | `GET /backend/sys/form/define/load?fieldId=` | 单 id 读取 GET；主键经 query 参数 `fieldId` 承接 |
| —（要点未列列表接口） | `POST /backend/sys/form/define/list` | 含复合查询条件的检索用 POST，请求体 `{ body, pages }`，总数 `metadata.total` |
| `POST /forms/{id}/publish`（发布） | `POST /backend/sys/form/define/publish` | 动作型 POST；无路径参数，请求体 `{ fieldId }` |
| `GET /forms/{id}/versions`（版本列表） | `GET /backend/sys/form/define/versions?fieldId=` | 单键读取 GET + `?fieldId=`（§3.9 参数名定稿说明） |
| `POST /forms/{id}/rollback`（版本回滚） | `POST /backend/sys/form/define/rollback` | 动作型 POST；请求体 `{ fieldId, fieldVersionId }` |
| `POST /form-data`（保存表单数据） | `POST /backend/sys/form/data/save` | 同上；一期仅新增语义（§4.3） |
| `GET /form-data/{id}`（获取表单数据） | `GET /backend/sys/form/data/load?fieldId=` | 同上；响应扩展实例锁定版本 Schema |
| `POST /form-data/validate`（动态校验） | `POST /backend/sys/form/data/validate` | 保留为独立校验入口定稿（§4.4：与 save 共用校验引擎、不落库、`data.errors` 结构化承载） |
| `GET /form-components`（组件列表） | —（不设接口） | 组件物料一期前端静态注册，后端仅持 type 白名单常量（§5） |
| `GET /forms/{id}/rules`（规则配置） | —（不设接口） | 规则随 Schema 内嵌于字段对象 `rules[]`（ADR-06），经 `define/load` / `data/load` / `data/schema` 随 Schema 整体下发，无独立规则读取通道 |
| —（要点未列） | `PATCH /define/enable`、`DELETE /define/delete?fieldId=`、`DELETE /data/delete?fieldId=`、`POST /data/list` | 仓库矩阵补齐启停用（`{ fieldId, fieldEnable }`）、删除与列表 |
| —（要点未列） | `GET /data/schema?formId=` | 填写页渲染必需的数据面取 Schema 通道（§4.1 登记） |

---

## 9. 在线文档注解口径（`@Tag` / `@Operation` / `@Parameter` / `@Schema`）

对齐 [AGENTS.md](../../AGENTS.md) §3「接口文档同步（强制）」与 [系统模块API接口设计文档](../系统模块/系统模块API接口设计文档.md) §3.5：新增接口与 Swagger 注解**同批完成**，不允许留下与代码不一致的 api-docs；验证 `./mvnw -Dtest=ApiDocsTest test`。

- **Controller 拆分定稿**：按次级模块拆分两个 Controller（先例流程三 Controller）——`SysFormDefineController`（`@ModuleGroup(moduleGroupKey = "form")`，类级 `@Tag(name = "自定义表单-表单定义", description = "表单定义与版本管理：草稿 CRUD、发布、版本列表与回滚；需 SYS_FORM_ADMIN")`）、`SysFormDataController`（同 moduleGroup，类级 `@Tag(name = "自定义表单-表单数据", description = "表单数据引擎：填写 Schema 获取、保存、校验、回显、列表与删除；登录即可，数据归属按创建人判定")`）。
- **`@Operation.description` 文案模板**（统一以「鉴权动作 <ACTION 常量值>（权限说明）；」开头，禁止留空或只写业务描述）：

| 接口 | description 模板 |
| --- | --- |
| `define/save` | `鉴权动作 sys.form.define.save（仅 SYS_FORM_ADMIN）；新建表单主档与初始草稿行，编码全局查重` |
| `define/publish` | `鉴权动作 sys.form.define.publish（仅 SYS_FORM_ADMIN）；草稿行冻结为不可变版本行，发布校验链（结构/上限/公式静态校验）全部通过方可发布` |
| `data/save` | `鉴权动作 sys.form.data.save（登录即可，数据归属按创建人判定）；服务端按当前发布版本 Schema 执行保存校验链，公式以服务端重算为准落库` |
| `data/validate` | `鉴权动作 sys.form.data.validate（登录即可）；与 data/save 共用校验引擎的独立预检入口，不落库，字段级结果经 data.errors 承载` |

- 其余接口按同构模板书写；`@RequestParam`（`fieldId` / `formId`）补 `@Parameter(description=…, required=…)`；请求 / 响应字段（含 `SysFormSchemaDo` 协议 POJO 与 `errors[]` 元素）补 `@Schema(description=…)`。
- 本模块无文件流 / SSE 响应，全部接口依赖全局 `SecurityScheme LoginToken`（`X-Login-Token` 请求头），无需 `@SecurityRequirements` 例外；登录态获取 `initID` 通道的例外已在公共侧登记。

---

## 10. 待确认事项（下游分册 / 收口任务登记）

| # | 开放点 | 归属 | 本分册已给口径 |
| --- | --- | --- | --- |
| 1 | 三表基类归属（主档 `MainModel`；版本行 / 数据行 `MainModel` vs `BaseModel`——版本行只增不改候选流水形态）、草稿行与发布行同表承载形态、`field_schema_json` / `field_form_data` JSON 列与索引清单 | F15-DOC-003（数据库分册，R05） | 请求 / 响应字段已按 `field_` 前缀与功能设计 §5/§6 草案列名登记；索引须支撑本分册查询路径（`/data/list` 按 `field_form_id` + 归属 + 时间，`/define/versions` 按 `field_form_id`，编码唯一约束 `uk_form_code` 与 §3.5「编码禁改」配套）。**已定稿（2026-11-23，F15-DOC-003）**：基类归属 `sys_form` / `sys_form_version` = `MainModel`（版本行否决流水候选——草稿行发布前被 `/define/update` 反复原地重写）、`sys_form_data` = `BaseModel`（一期只增不改 + 物理删除）；草稿行与发布行**同表承载**（`sys_form_version` 单表 `field_status` 区分，草稿行 `field_version` 为 NULL、发布时原地冻结回填）；索引清单 `uk_form_code` / `idx_form_version_form` / `uk_form_version_form_version`（并发发布防护）/ `idx_form_data_form` / `idx_form_data_business`，见[自定义表单数据库设计分册](自定义表单数据库设计分册.md) §4/§5/§9.1 |
| 2 | 数据面口径终稿（R06）：登录即可范围、是否增设 `SYS_FORM_DATA_ADMIN` 等数据面权限码、归属判定细则 | F15-DOC-005 | **已定稿（2026-11-23，F15-DOC-005）**：与 §4.8 / §6 倾向一致——数据面 6 接口登录即可、归属按创建人 `field_creator_id` 判定（Service 层强制过滤 / 拒绝，先例日程归属）、`SYS_FORM_ADMIN` 管理全部表单与数据、一期不增设 `SYS_FORM_DATA_ADMIN`（数据面无独立管理动作面，字段级可见性归 v1.8 BPM 节点权限）；本分册 §2.5 / §4.8 / §6 口径不变，权威口径见功能详细设计 §12.2（含三种拒绝语义逐接口落地描述） |
| 3 | `SYS_FORM_ADMIN` 权限码 NAME 与 `SysFormConstants.ROLE_SYS_FORM_ADMIN` 逐字一致、`moduleGroup.yml`（key `form`）清单固化 | F15-DOC-005 / F15-OPS-001 | **口径已由 F15-DOC-005 定稿（2026-11-23）**：roles.yml 条目形态（`ROLE_LIST` / `ROLE_MAP`，NAME「自定义表单管理员」）与 moduleGroup（key `form`、name「自定义表单」、remark「自定义表单模块（15 接口统一开关）」）见功能详细设计 §12.1 / §12.4；**yml 三件套落地归 F15-OPS-001，落地时须同批扩展 `ModuleGroupYmlParseTest`（keySet 精确断言纳入 `form` + name / remark 断言，F18 / F14 先例教训）**；ACTION 全集（§6.1）即 moduleGroup 接口分组清单的依据 |
| 4 | 未知字段提交策略差异回写：功能设计 §6.1 第②步「倾向裁剪 + 告警」与本分册定稿「拒绝（`CS_FORM_UNKNOWN_FIELD`）」表述统一 | F15-DOC-006（文档收口回查） | 本分册定稿为准（§7.4①）；功能设计 §6.1 第②步建议改写为「字段白名单校验（未知字段拒绝）」 |
| 5 | 一期无数据实例更新接口（`PATCH /data/update` 不设）；若 UI 分册 / 联调期出现「数据再编辑」诉求 | F15-DOC-004 / F15-INT-002 评估 | §4.3 已登记：实例更新须补 PATCH 接口（同保存校验链 + readonly 值变更拒绝语义）并回写本分册 |
| 6 | 历史版本 Schema 内容预览（版本行详情接口）一期不设；UI 分册若出现版本对比 / 预览页面 | F15-DOC-004 评估 | §3.9 已登记：如需再补 `GET /define/version?fieldId=`（版本行详情）并回写本分册 |
| 7 | `field_process_instance_id` 一期不写入；`/data/list` 的 `fieldProcessInstanceId` 筛选一期恒空 | v1.8 BPM 适配层 | §4.6 已登记：随 v1.8 显式版本提交与流程实例写入同批生效 |
| 8 | 资源限制常量草案数值复核（§2.4 引用功能设计 §2.2） | F15-BE-001 | 数值为草案、语义与超限错误码不变；编码期复核后如有变化同批回写本分册 §2.4 |

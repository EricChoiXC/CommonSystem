# 系统模块API接口设计文档

## 1. 文档说明

### 1.1 文档用途与维护规则

- 本文档用于承接 [系统模块需求文档](D:/Program/erp/docs/系统模块/系统模块需求文档.md) 与 [系统模块UI设计文档](D:/Program/erp/docs/系统模块/系统模块UI设计文档.md)，输出系统模块 API 设计基线。
- 当前阶段采用“骨架优先、任务驱动细化”的策略：先冻结模块边界、接口分组、全局规则和接口承接方向，后续再按任务清单逐模块补充详细请求响应。
- 所有新增或调整接口，必须同步校验路径、HTTP 方法、鉴权方式、文档 ID 传递方式与返回对象命名。

### 1.2 文档职责边界

- 本文档负责模块接口分组、接口职责、路径规范、鉴权规则、错误语义与下游契约承接入口。
- 本文档当前不展开字段级请求/响应卡片，不直接替代 OpenAPI、Controller 参数类或前端 API 封装明细。
- 数据表字段、索引与迁移脚本不在本文档展开，由数据库设计文档承接。

### 1.3 AI使用约定

- AI Agent 补全文档时，必须先读取系统模块需求文档与 UI 设计文档，避免脱离页面和业务场景空写接口。
- 未在需求文档或 UI 文档中出现的业务动作，不得擅自加入正式接口清单。
- 如果接口明细尚未冻结，应保留“待后续任务补齐”标识，而不是预填不稳定字段。

## 2. 输入依据与范围

### 2.1 输入依据

- 基线规范：[开发规范](D:/Program/erp/docs/开发规范.md)
- 需求来源：[系统模块需求文档](D:/Program/erp/docs/系统模块/系统模块需求文档.md)
- UI 基线：[系统模块UI设计文档](D:/Program/erp/docs/系统模块/系统模块UI设计文档.md)
- 协作约束：`AGENTS.md`

### 2.2 范围声明

- 当前覆盖 `org`、`attachment`、`process`、`number`、`notify`、`audit`、`dict` 七个系统子模块。
- 本轮输出仅创建 API 主文档骨架、模块依赖顺序、接口族分组与全局约束。
- 当前不输出字段级接口清单，不输出完整错误码表，不输出 OpenAPI YAML。

### 2.3 事实源优先级与冲突裁决

- 业务流程、角色权限、特殊操作前置条件：以需求文档为准。
- 页面入口与操作承接：以 UI 设计文档为准。
- 路径与请求方法命名：以本文档为准。
- 数据对象结构：以后续数据库设计文档为准。

## 3. 全局接口规范

### 3.1 输出对象与承接关系

- 本文档输出对象包括：后端 Controller 路径规划、Service 能力边界、前端 `api` 目录拆分依据、后续 OpenAPI/错误码补齐入口。
- 本文档由以下文档共同约束：
  - [系统模块需求文档](D:/Program/erp/docs/系统模块/系统模块需求文档.md)
  - [系统模块UI设计文档](D:/Program/erp/docs/系统模块/系统模块UI设计文档.md)
  - [系统模块数据库设计](D:/Program/erp/docs/系统模块/系统模块数据库设计.md)

### 3.2 接口路径与方法规则

- 后端统一前缀：`/backend/sys/{模块}/{次级模块}/{请求方法}`
- 通用动作优先使用：
  - `/init` `GET`
  - `/load?fieldId=${id}` `GET`
  - `/list` `POST`
  - `/save` `POST`
  - `/update` `PATCH`
  - `/delete?fieldId=${id}` `DELETE`
  - `/import` `POST`
  - `/export` `POST`
  - `/template` `POST`（导入模板下载，文件流，仅标题行）
  - `/download?fieldId=${id}` `GET`
- 单 `id` 或无查询条件读取使用 `GET`。
- 含复合查询条件的读取与检索使用 `POST`。
- 变更类接口统一使用 `PATCH` 或 `POST`，禁止用 `PUT` 代替更新。

### 3.3 鉴权与文档ID规则

- 所有需要业务鉴权的接口都必须显式定义 `resolver`。
- 单据类、流程类、附件类、待办类等涉及对象访问控制的接口，必须携带“接口标识 + 文档 ID”作为鉴权输入。
- 前端批量鉴权接口仅用于按钮显隐预判，不能替代真实接口执行前的最终鉴权。
- 普通无权限读取接口也需在接口说明中明确“不设权”的依据，避免实现阶段误加限制。

### 3.4 命名与分层约束

- Controller 仅负责参数接收、基础校验、调用 Service 与返回结果。
- Service 承接核心业务逻辑；跨模块调用统一经 `IService` 边界暴露。
- Dao 仅负责与 Mapper 一对一对接，不承载业务分支判断。
- 请求/响应对象命名应采用明确业务语义 + `Request` / `Response` / `Vo`。

### 3.5 在线接口文档（springdoc-openapi）

后端已接入 springdoc-openapi，与本文档形成互补：本文档定义接口分组、路径规范与鉴权规则，在线文档由代码注解实时生成，用于联调与第三方导入。

| 地址 | 说明 |
| --- | --- |
| `GET /swagger-ui.html` | Swagger UI 交互页面，可按 `@Tag` 分组浏览全部接口 |
| `GET /v3/api-docs` | OpenAPI 3 JSON，可导入 Apifox / Postman 等工具 |

- **访问控制**：默认开启，统一开关为环境变量 `CS_API_DOCS_ENABLED`（`application.properties` 中 `springdoc.api-docs.enabled` / `springdoc.swagger-ui.enabled` 均引用该变量）。置为 `false` 时两个端点同时不可访问，生产部署按需关闭。
- **配置归属（偏离说明）**：该开关属部署运行参数，不是业务配置，因此**不写入 `common_setting`**，也不提供前端维护页面，只通过环境变量覆盖。这与「新增配置按 `CS_*` > `SETTING` > 代码兜底三段式」的常规约定不同，属有意偏离，勿在后续任务中把该开关迁入 `SETTING`。
- **鉴权方案**：全局注册 `SecurityScheme`（apiKey / header / `X-Login-Token`），Swagger UI 的 Authorize 按钮可一次性填入登录凭证。登录、登出、`/backend/common/init`、`/backend/common/initID`、`/backend/common/setting/load`、个人资料第三方授权回调 `GET /backend/sys/org/profile/openid/callback`（第三方浏览器 302 回跳，无 Authorization header）与 SSE 订阅（凭证走 URL 参数 `token`）不依赖该请求头，对应方法以 `@SecurityRequirements` 取消全局要求。
- **注解规范**：每个 Controller 必须有 `@Tag`（中文名 + 页面/权限概述）；每个接口方法必须有 `@Operation(summary=…, description="鉴权动作 <ACTION 常量值>；…")`；`@RequestParam`、`MultipartFile` 等非请求体参数补 `@Parameter(description=…)`；公共模型与排序字段用 `@Schema` 说明，字段级请求/响应卡片不在本表逐项维护。
- **编号机制分组**（v1.5）：编号模块四个 Controller（rule / preview / generate / manage）类级统一 `@Tag(name = "编号机制", description = "编号规则维护、预览、取号与特权管理")`，在线文档按该分组浏览；开关仍由 `CS_API_DOCS_ENABLED` 统一控制（见上「访问控制」）。
- **与 SPA 回退的关系**：`SpaWebConfig` 的前端回退已排除 `swagger-ui`、`v3/api-docs`、`webjars` 三个路径前缀，直接访问文档地址不会被前端路由接管。

## 4. 模块接口设计骨架

### 4.1 组织架构模块

#### 4.1.1 模块目标与接口边界

- 提供组织架构、机构、部门、人员、岗位、角色、群组与地址本选择相关接口。
- 同时覆盖公开查询接口、普通读取接口、完整读取接口、维护接口与角色分配接口；v1.1 新增群组维护与地址本群组/角色页签能力；v1.2 新增个人资料（本人自助）接口组——登录用户维护本人基础资料、头像与第三方账号（微信/QQ）绑定，与人员维护接口（管理员视角、`documentId` 鉴权）完全隔离。

#### 4.1.2 模块接口分组

| 接口组 | 建议次级模块 | 说明 |
| --- | --- | --- |
| 组织架构树与节点查询 | `element` | 查询树、读取节点普通信息、读取节点完整信息 |
| 机构维护 | `org` | 机构增删改查、启停用 |
| 部门维护 | `dept` | 部门增删改查、启停用 |
| 人员维护 | `person` | 人员增删改查、本人信息维护、所属组织读取 |
| 岗位维护 | `post` | 岗位增删改查、岗位人员关联 |
| 角色维护 | `role` | 角色查询、角色增删改、角色权限、角色分配；主表写入同事务维护 `sys_org_element` 镜像行（`field_org_type = 5`） |
| 群组维护 | `group` | 群组列表/详情/新建/编辑/删除（`field_org_type = 6`）：接口不设权，可见性与维护权限由 Service 数据级过滤 |
| 地址本选择 | `addressbook` | 树选择、关键字列表（角色页签复用传 `orgTypes=[5]`）、目标回填、群组页签（`groupList` 可见群组分页 / `groupUsers` 群组成员人员） |
| 个人资料（本人自助） | `profile` | 本人资料加载与保存、头像附件ID读取、第三方账号绑定（授权地址生成 / 授权回调 / 解绑）；不设权限码，登录且账号启用即可，callback 无需鉴权 |

#### 4.1.3 当前冻结的接口方向

- `element` 组需区分普通读取与完整读取两个能力。
- `person` 组需单独保留“根据用户 ID 读取所属组织架构”的能力。
- `role` 组需区分“我的角色查询”和“全量角色维护查询”。
- `group` 组不设接口级权限，数据级可见性/维护权限由 `SysOrgGroupService` 承担（管理员可见并维护全部群组——含他人个人群组，普通用户仅个人群组；接口路径以实现回写的 [组织架构接口清单](../设计文档/sys/org/组织架构接口清单.md) 为准）。
- 地址本树与组织管理列表固定排除角色（5）/群组（6）镜像行，两类数据分别走 `role` / `group` 组与地址本专用页签接口。
- `profile` 组为本人自助语义（v1.2）：接口一律不收 userId 参数，操作对象由会话定位（`UserUtil.getCurrentUserId()`），从根上排除「A 修改 B」的越权路径；`save` 请求体 `fieldId` 与会话用户不一致即拒绝（一致性断言）。用户名可编辑并同步 `sys_org_element.field_name` 与拼音两列，登录名只读不接收；头像复用附件公共通道（`modelName=sys_org_user` + `key=profile`，单张语义），本组不新增头像接口；`callback` 为第三方浏览器 302 回跳，无需鉴权，任何失败均 302 带 `fail_reason` 不返回 JSON 错误页。

#### 4.1.4 个人资料接口组（`sys.org.profile`，v1.2）

基础路径：`/backend/sys/org/profile`。接口标识常量定义于 `SysOrgProfileProperties`（`ACTION_LOAD` / `ACTION_SAVE` / `ACTION_BIND` / `ACTION_UNBIND`），`ProfileResolver` 对四个 action 一律「登录且账号启用」放行——**不设权限码、roles.yml 无变更**（本人自助语义，方案 5.6），`SYS_ADMIN` 由 `BaseResolver` 自动放行，resolver 对未注册的未知 action 默认拒绝（最小授权）。

| 接口 | 方法 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| `/load` | GET | `sys.org.profile.load`（登录且启用） | 加载本人资料：基础资料 7 项（`fieldName` / `fieldLoginName` 只读 / `fieldPhone` / `fieldEmail` / `fieldAddress` / `fieldGender` / `fieldBirthday`）+ 只读展示 2 项（`fieldJobStatus` / `fieldAccountStatus`）+ `avatarAttId`（`sys_att_main` 按 `field_model_name='sys_org_user' + field_model_id=当前用户 + field_key='profile'` 过滤 `field_enable=1` 后按 `field_create_time` 降序取最新一条，未上传为 null）+ `openid` 绑定状态（`SysOrgUserOpenidDo` 结构化对象，未绑定渠道内层为 null）+ 三开关 `openidBindEnabled` / `wechatEnabled` / `qqEnabled`（实时读库判定：总开关 `system.openid_bind_enable` + 该渠道凭证齐全，无缓存） |
| `/save` | POST | `sys.org.profile.save`（登录且启用） | 保存可编辑字段，请求体 `SysOrgProfileSaveRequest`；以现行整行为基覆写请求携带（非 null）字段后整行更新；校验规则见下表 |
| `/openid/bindUrl` | POST | `sys.org.profile.bind`（登录且启用） | 请求体 `{ channel: "wechat" \| "qq" }`；校验顺序：渠道合法（`绑定渠道无效`）→ 总开关（`第三方账号绑定未开启`）→ 渠道凭证（`微信绑定未配置` / `QQ绑定未配置`）→ `redirect_base`（`回调地址未配置`）；全部通过后生成 32 位一次性 state 写 Redis（`openid_bind_state:{state}` → `{userId, channel}`，TTL 300 秒，回调取删防重放），返回 `{ bindUrl }`（第三方授权页完整 URL，`redirect_uri = {redirect_base}/backend/sys/org/profile/openid/callback` 已 urlencoded） |
| `/openid/callback` | GET | **无需鉴权** | 第三方浏览器 302 回跳（无 Authorization header），方法以 `@SecurityRequirements` 空注解取消全局 token 要求，不执行 authorize（执行会以未登录误伤正常回调），操作对象由 state 内绑定的 userId 定位。处理顺序：取删 state（`getAndDelete` 原子取删，Redis 服务端 <6.2 无 GETDEL 时自动降级 GET+DEL，值损坏按 expired）→ 复验总开关（关闭 → `disabled`，防关闭开关后存量跳转仍可完成绑定）→ 复验渠道凭证（缺失 → `unconfigured`）→ code 缺失（第三方错误回跳）或换取失败 → `oauth_error` → 查重（计数 > 0 即 `dup`，无本人豁免）→ 写 `field_openid`（查重与写入同事务）。任何失败均 `302` 回跳 `{redirect_base}/profile?bind={channel}:{reason}`，不返回 JSON 错误页；`redirect_base` 未配置时 400「回调地址未配置」 |
| `/openid/unbind` | POST | `sys.org.profile.unbind`（登录且启用） | 请求体 `{ channel }`；移除对应渠道键，两渠道均未绑定时整列置 NULL（专用 SQL `clearOpenid`，通用 update 对 `field_openid` 为条件写落不了 NULL）；解绑未绑定渠道幂等成功；不限制「至少保留一个绑定方式」（本系统不支持第三方登录，解绑不影响登录能力） |

save 校验规则（`SysOrgProfileService#validate`，校验失败抛 `BAD_REQUEST`，文案经 `GlobalExceptionHandler` 统一返回）：

| 字段 | 规则 |
| --- | --- |
| `fieldId` | 必填，与会话用户不一致即拒绝（`提交人员与会话用户不一致`，防并发换号后脏提交） |
| `fieldName`（用户名） | 必填非空（`用户名不能为空`）；与原值不同时同步 `sys_org_element.field_name` 并重算 `field_pinyin` / `field_pinyin_short`（`PinyinUtil` 后端统一重算，前端不传、不可直改，与 `SysOrgElementService#save/update` 幂等口径一致） |
| `fieldPhone` | 非空时校验手机号格式 `^1[3-9]\d{9}$`（`手机号格式不正确`） |
| `fieldEmail` | 非空时校验邮箱格式（`邮箱格式不正确`） |
| `fieldAddress` | 非空时长度 ≤ 500（`联系地址长度不能超过500`） |
| `fieldGender` | 非空时枚举 0/1/2（`性别取值无效`） |
| `fieldBirthday` | 非空时不晚于当天（按本地日历日比较，date-only「明天零点」亦拒绝；`生日不能晚于当天`） |
| `fieldLoginName` | 只读不接收不更新（登录名为账号唯一标识 `uk_org_user_login_name`，仅管理员在组织架构人员维护中修改；请求体不设该字段，携带值被 Jackson 忽略） |

callback 回跳 `bind` 值（`{channel}:{reason}`，前端经 `route.query.bind` 提示后清理 query）：

| reason | 触发条件 |
| --- | --- |
| `success` | 校验全部通过且绑定写入成功 |
| `expired` | state 不存在 / 已过期（TTL 300 秒）/ 已消费 / 伪造 / 缺参 / 值损坏不可读（一次性防重放防 CSRF） |
| `disabled` | state 有效但总开关 `system.openid_bind_enable` 已关闭 |
| `unconfigured` | 总开关开启但该渠道凭证（app_id / app_secret）缺失 |
| `oauth_error` | code 缺失（第三方错误回跳）或以 code 换取第三方用户信息失败 |
| `dup` | 换取成功但该 openid 已被其他账号绑定（计数 > 0 即拒绝，无本人豁免；state 一次性已防重放） |

头像配套（复用附件既有接口，无附件侧代码改动）：上传 `POST /backend/sys/att/main/upload`（multipart：`file` + `modelName=sys_org_user` + `modelId=用户ID` + `key=profile`）、预览 `GET /backend/sys/att/main/view?fieldId=`（前端 axios `responseType: 'blob'` 拉文件流转 blob URL，`<img src>` 直连缺 token 不可用）、删除 `DELETE /backend/sys/att/main/delete?fieldId=`（限上传者本人或附件管理员）；单张语义：上传新头像成功后删除旧头像附件（删除失败不回滚上传，下次上传再清理）。

### 4.2 附件模块

#### 4.2.1 模块目标与接口边界

- 提供通用附件上传、下载、删除能力以及后台附件监管查询能力。

#### 4.2.2 模块接口分组

| 接口组 | 建议次级模块 | 说明 |
| --- | --- | --- |
| 附件通用操作 | `file` | 上传、下载、删除、读取附件元数据 |
| 附件后台监管 | `manage` | 附件列表查询、明细读取、后台删除 |
| 附件操作记录 | `operation` | 记录上传、下载、删除操作轨迹 |

#### 4.2.3 当前冻结的接口方向

- 上传、下载、删除最终权限由来源业务页面或附件管理员共同决定。
- 后台监管删除与前台业务删除应在接口层区分来源，避免权限语义混淆。

### 4.3 流程机制模块

#### 4.3.1 模块目标与接口边界

- 提供流程模板维护、流程实例创建与读取、审批、撤回、特权审批、流转记录读取和待办联动能力。

#### 4.3.2 模块接口分组

| 接口组 | 建议次级模块 | 说明 |
| --- | --- | --- |
| 流程模板维护 | `template` | 模板查询、读取、新建、编辑、启停用 |
| 流程实例 | `main` | 创建实例、查询实例、读取详情 |
| 流程审批 | `audit` | 审批、驳回、特权审批 |
| 流程撤回 | `withdraw` | 提交人撤回、管理员特权撤回 |
| 流转记录 | `workitem` | 流转记录、审批意见、关联待办读取 |

#### 4.3.3 当前冻结的接口方向

- 流程提交由业务模块调用流程接口完成实例创建，流程模块不替业务模块做单据权限判定。
- 审批、撤回、特权审批必须在接口契约中区分操作者身份与允许条件。
- 与待办联动的创建、完成能力需保留清晰的服务边界，避免流程模块与待办模块循环耦合。

### 4.4 编号机制模块

#### 4.4.1 模块目标与接口边界

- 提供编号规则维护（多规则配置、单启用、切换式启用）、编号预览、正式取号（幂等 / 周期 / 重置 / 作废 / 补号）与取号明细查询能力（v1.5 已实现）。
- 模块 key `SYS-NUMBER`；权限码 `SYS_NUMBER_ADMIN`（编号管理员）/ `SYS_NUMBER_RULE_EDITOR`（编号规则维护者）定义于 `properties/sys/number/roles.yml`，接口标识常量（13 个 `ACTION_*`）定义于 `SysNumberConstants`；`NumberResolver` 继承 `BaseResolver` 按动作判定（`SYS_ADMIN` 自动放行，分支不列举），Controller 经 `authorize(action, documentId)` 最终鉴权——未登录抛 `UNAUTHORIZED`、无权限抛 `FORBIDDEN`（HTTP 200 + code 体系，全工程惯例）。
- 基础路径 `/backend/sys/number/{rule|preview|generate|manage}/...`；在线文档四个 Controller 类级 `@Tag(name = "编号机制", …)`，经 springdoc 生成（见 3.5）。

#### 4.4.2 模块接口分组

| 接口组 | 次级模块 | 说明 |
| --- | --- | --- |
| 编号规则维护 | `rule` | 规则查询、读取、新建、编辑、启停用、删除 + 接入项下拉数据源 `modelOptions` |
| 编号测试预览 | `preview` | 规则预览、编号测试（基于当前计数器顺延模拟，不推进计数器、不落库） |
| 编号生成 | `generate` | 业务模块正式取号（幂等、周期切换、失败语义） |
| 取号管理 | `manage` | 取号明细查询、重置、作废、补号（特权操作，落操作日志 module=SYS-NUMBER） |

#### 4.4.3 当前冻结的接口方向

- 编号生成属于被动服务型接口，应重点定义幂等与失败语义。
- 预览测试与正式取号必须接口隔离，避免测试动作污染正式流水。

#### 4.4.4 接口清单（v1.5，四分组 13 接口）

`sys.number.rule.modelOptions` 为 W5 评审补充接口（先例 notify modelOptions，编辑页「所属接入项」下拉数据源）；接口契约明细同 `docs/v1_5-编号功能/编号机制实现方案.md` §3.2。

| 接口 | 方法 | 鉴权动作 | 权限 | 说明 |
| --- | --- | --- | --- | --- |
| `/backend/sys/number/rule/list` | POST | `sys.number.rule.list` | 登录即可 | 规则列表；请求体 `{ body: { fieldName?, fieldCode?, fieldModelField?, fieldEnable? }, pages }`；`field_rule` 原文返回（列表页不解析），固定创建时间降序 |
| `/backend/sys/number/rule/load?fieldId=` | GET | `sys.number.rule.load` | ADMIN or EDITOR | 完整读取；`field_rule` 解析为规则对象返回（前端 NumberRuleComposer 直接消费） |
| `/backend/sys/number/rule/save` | POST | `sys.number.rule.save` | ADMIN or EDITOR | 新建（默认启用）：C1~C5 校验 → 编码查重 → 接入项存在性（否则 `CRM_NUMBER_RULE_MODEL_NOT_FOUND`）；同事务自动停用同接入项原启用规则（切换启用） |
| `/backend/sys/number/rule/update` | PATCH | `sys.number.rule.update` | ADMIN or EDITOR | 编辑：另校验 C7（seq 项 id 锁定）；接入项三字段与启用态以库值不受理 |
| `/backend/sys/number/rule/enable` | PATCH | `sys.number.rule.enable` | ADMIN or EDITOR | 请求体 `{ fieldId, fieldEnable }`；启用先校验（C1~C5）再同事务切换启用，停用仅自身 |
| `/backend/sys/number/rule/delete?fieldId=` | DELETE | `sys.number.rule.delete` | ADMIN or EDITOR | 已产生取号明细拒绝删除（`CRM_NUMBER_RULE_IN_USE`），仅可停用 |
| `/backend/sys/number/rule/modelOptions` | GET | `sys.number.rule.modelOptions` | 登录即可 | 接入项下拉数据源（W5 补充接口）：返回 `numberRule.yml` 进程内缓存列表 `{ key, name, modelName, modelField }` |
| `/backend/sys/number/preview/render` | POST | `sys.number.preview.render` | 登录即可 | 请求 `{ fieldId?, rule?, count? }`（二选一，count 默认 3 上限 10）；响应 `{ samples, errors: [{ code, itemId, message }] }`；顺延模拟、**绝不写库**，周期临近耗尽时样例含越界提示（C6 语义） |
| `/backend/sys/number/generate/acquire` | POST | `sys.number.generate.acquire` | 登录即可（不替代业务鉴权） | 请求 `{ modelName, modelField, modelId }`；响应 `{ code, recordDetailId, reused }`（`reused=true` 幂等命中已有编号）；同进程业务后端建议直注 `SysNumberGenerateService`，本接口保留给跨服务与调试场景 |
| `/backend/sys/number/manage/recordList` | POST | `sys.number.manage.recordList` | 仅 ADMIN | 明细查询 `{ body: { fieldScopeKey?, fieldRuleId?, fieldPeriod?, voidFlag?, recordTimeBegin?, recordTimeEnd? }, pages }`（voidFlag 三态：null 全部 / true 已作废 / false 有效） |
| `/backend/sys/number/manage/reset` | POST | `sys.number.manage.reset` | 仅 ADMIN | 重置计数器：封存当前行（可限定流水项 / 周期），下次取号从 start 重开；会造成重号，落操作日志 |
| `/backend/sys/number/manage/void` | PATCH | `sys.number.manage.void` | 仅 ADMIN | 请求体 `{ fieldId, fieldVoidReason }`；仅更新 void 两字段、不回退计数器；重复作废幂等返回原状态 |
| `/backend/sys/number/manage/reissue` | POST | `sys.number.manage.reissue` | 仅 ADMIN | 请求体 `{ fieldId }`（须已作废）；为原 `field_model_id` 顺延取新号、旧号留作废审计，落操作日志 |

#### 4.4.5 鉴权口径（D6 / D7）

- **登录即可**（D6）：`rule/list`、`preview/render`、`generate/acquire`、`rule/modelOptions` 仅要求登录。预览无副作用、不落库，属纯渲染，NumberRuleComposer 会被「分类编辑页」等宿主复用，宿主操作者通常无编号权限，按需求原口径设权会导致宿主表单实时预览 403（偏差修订见方案 §0.2）；「编号测试」能力由预览接口顺延模拟承担，正式流水不受影响。
- **取号不替代业务鉴权**（D7）：`generate/acquire` 的 `AuthorizationContext` 不携带业务单据语义，业务单据权限由调用方在取号前自行完成，禁止用本接口替代业务鉴权。
- **规则维护五动作**（load / save / update / enable / delete）：`SYS_NUMBER_ADMIN` or `SYS_NUMBER_RULE_EDITOR`。
- **取号管理四动作**（recordList / reset / void / reissue）：仅 `SYS_NUMBER_ADMIN`；reset / void / reissue 落操作日志（module=SYS-NUMBER，审计联动）。

#### 4.4.6 错误码与承载方式

| 错误码 | 场景 |
| --- | --- |
| `CRM_NUMBER_RULE_INVALID` | 规则配置非法（C1~C5、C7） |
| `CRM_NUMBER_RULE_NOT_FOUND` | 规则不存在 / 接入项未配置 |
| `CRM_NUMBER_RULE_MODEL_NOT_FOUND` | 接入项编码不在 `numberRule.yml` 缓存 |
| `CRM_NUMBER_RULE_DISABLED` | 同接入项无启用规则 |
| `CRM_NUMBER_RULE_DUPLICATED` | 规则编码重复（启用唯一性由切换机制保证，不用于启用冲突） |
| `CRM_NUMBER_RULE_IN_USE` | 已产生取号明细，拒绝删除 |
| `CRM_NUMBER_SERIAL_EXHAUSTED` | 流水超出 seq length 容量（C6），取号失败随事务回滚 |

承载方式（联调实测口径）：

- 上述 `CRM_*` 业务错误码以**异常 message 前缀**承载：异常命中 `GlobalExceptionHandler` 既有分支，`BaseResponse.code` 统一为 `BAD_REQUEST`，前端按 message 前缀识别具体错误码；
- 鉴权失败 `BaseResponse.code` 为 `UNAUTHORIZED`（未登录）/ `FORBIDDEN`（无权限），HTTP 状态仍为 200 + code 体系（全工程惯例）。

### 4.5 待办模块

#### 4.5.1 模块目标与接口边界

- 提供我的待办、我的已办、我的通知、我的关注与监管查看能力。

#### 4.5.2 模块接口分组

| 接口组 | 建议次级模块 | 说明 |
| --- | --- | --- |
| 我的待办 | `todo` | 未处理待办查询、读取 |
| 我的已办 | `done` | 已处理记录查询、读取 |
| 我的通知 | `notice` | 通知查询、已读处理 |
| 我的关注 | `star` | 关注查询、关注、取消关注 |
| 待办监管 | `monitor` | 监管查询、监管读取 |
| 待办联动 | `sync` | 供流程/业务模块创建、完成、转移待办 |

#### 4.5.3 当前冻结的接口方向

- “我的”类接口默认以当前登录用户为作用域，不开放跨人查询。
- 监管接口仅允许查看，不开放代办或直接改状态能力。
- 来源业务处理跳转由前端完成，实际业务动作仍由来源模块接口承担。

### 4.6 审计模块

#### 4.6.1 模块目标与接口边界

- 提供审计记录查询与读取能力，供后台管理员监管使用。

#### 4.6.2 模块接口分组

| 接口组 | 建议次级模块 | 说明 |
| --- | --- | --- |
| 审计查询 | `record` | 按模块、单据、时间等条件查询 |
| 审计明细读取 | `detail` | 读取单条审计记录详情 |

#### 4.6.3 当前冻结的接口方向

- 审计模块本身不开放编辑接口。
- 审计写入由各业务模块在增删改动作中统一调用或异步记录，不由前端直接调用。

### 4.7 码值模块

#### 4.7.1 模块目标与接口边界

- 提供码值字典主表与明细配置的查询、读取和维护能力。

#### 4.7.2 模块接口分组

| 接口组 | 建议次级模块 | 说明 |
| --- | --- | --- |
| 字典主表维护 | `main` | 查询、读取、新建、编辑、删除、启停用 |
| 字典明细维护 | `detail` | 明细查询、维护、排序 |
| 字典只读服务 | `lookup` | 面向业务模块的无权限查询接口 |
| 字典明细导入导出 | `export` POST（文件流）、`import` POST（multipart：`file` + `params`）、`template` POST（模板文件流） | v1.1 导入导出公共通道试点（`/backend/sys/dict/*`），鉴权动作 `sys.dict.itemExport` / `sys.dict.itemImport` / `sys.dict.itemTemplate`，仅 SYS_DICT_ADMIN |

#### 4.7.3 当前冻结的接口方向

- 维护类接口仅码值字典管理员可调用。
- 读取、查询型接口不设权，但需要保留启用状态过滤能力。

### 4.8 多语言模块

#### 4.8.1 模块目标与接口边界

- 提供语言清单维护、翻译矩阵维护、翻译部署、前端语言包与用户语言登记能力（v1.1 新增，模块前缀 `/backend/sys/i18n`）。
- 权限码 `SYS_I18N_ADMIN`（多语言管理员），`I18nResolver` 按 ACTION 分支判定；`SYS_ADMIN` 由 `BaseResolver` 自动放行。

#### 4.8.2 模块接口分组

| 接口组 | 路径与方法 | 鉴权 |
| --- | --- | --- |
| 语言维护 | `language/list` GET（免鉴权）、`language/save` POST、`language/update` PATCH、`language/delete?fieldId=` DELETE、`language/setDefault` PATCH | 维护四接口仅 SYS_I18N_ADMIN |
| 模块聚合 | `model/list` GET | 登录即可 |
| 翻译维护 | `translation/list` POST（顶层 `fieldModel`）、`translation/update` PATCH（批量明细）、`translation/reset` PATCH（key + language）、`translation/export` POST（文件流，文件名 `translations_{model}_{yyyyMMddHHmmss}.xlsx`）、`translation/import` POST（multipart） | 仅 SYS_I18N_ADMIN |
| 部署 | `deploy/run` POST | 仅 SYS_I18N_ADMIN |
| 前端语言包 | `/locales/{code}.json` GET | 免鉴权 |
| 用户语言 | `user/language/update` PATCH（body 仅 `fieldLanguage`） | 登录即可 |

#### 4.8.3 当前冻结的接口方向

- 请求体形状：`language/update` / `setDefault` 为 `{ body: { fieldId, … } }`；`translation/list` 的 `fieldModel` 为请求顶层字段（与 `body` / `pages` 平级）；登录契约的 `fieldLanguage` 亦为请求顶层字段（D3）。
- `setDefault` 互斥切换（同事务先清旧默认再设新默认，全局恰一条）；默认语言禁止删除与停用；删除语言级联删翻译、用户语言登记改绑默认语言（D4）。
- 翻译更新仅对提交明细写值并置人工编辑标记；重置清标记后以资源文件当前值覆盖，该语言无对应资源文件时直接删除记录。
- 导入为固定覆盖导入：仅按文件实际包含的 (key, language) 更新；未登记 key、无效语言码列跳过且不插入资源项。
- `deploy/run` 执行与启动同规则的「资源 → DB 同步」并刷新缓存，返回同步资源数与各语言翻译条数；语言包为动态接口无产物落盘（D2），未启用或未知语言码返回 `BAD_REQUEST` 错误信息，不输出空包。
- 免鉴权入口（`language/list`、`/locales/**`）与登录接口一致，以 `@SecurityRequirements` 标注无安全要求；文件流接口以 `APPLICATION_OCTET_STREAM_VALUE` + `format="binary"` 标注。

### 4.9 模块组开关模块（v1.1 新增）

#### 4.9.1 模块目标与接口边界

- 提供模块组（module-group）开关状态的列表查询与整体启停能力（管理面），支撑「模块组级接口开关机制」：管理员不改代码、不重启即可整体启停一组接口。
- 基础路径：`/backend/common/module`；接口标识常量定义于 `CommonModuleProperties`（`ACTION_LIST` / `ACTION_ENABLE`），鉴权由 `CommonModuleController` 调用 `authorize(action, documentId)` 经 `ModuleResolver`（继承 `BaseResolver`）兜底：未登录抛 `UNAUTHORIZED`、无权限抛 `FORBIDDEN`；`SYS_ADMIN` 经 `BaseResolver` 自动放行（模块分支内不重复列举）。
- `CommonModuleController` 自身**不打** `@ModuleGroup`：关闭任意模块组后管理面仍可用（可经本模块接口恢复开启）。
- 开关判定位于业务鉴权之前：`ModuleGroupInterceptor`（注册于 `/backend/**`）先于 Controller `authorize`，被关闭业务模块的接口返回 `MODULE_DISABLED`、不再讨论权限。

#### 4.9.2 模块接口分组

| 接口 | 方法 | 路径 | 鉴权动作 | 鉴权 |
| --- | --- | --- | --- | --- |
| 模块组列表 | POST | `/backend/common/module/list` | `common.module.list` | 登录即可（无角色要求；免登录不可访问——模块组列表暴露系统拓扑，不向匿名开放） |
| 模块组启停 | PATCH | `/backend/common/module/enable` | `common.module.enable` | 仅 SYS_MODULE_ADMIN（模块组管理员） |

- `list` 遵循 list 通道：请求体 `{ body, pages: { pageNo, pageSize, needPage } }`（`body` 为查询条件预留位，首批无筛选字段）；模块组量极小，默认 `needPage=false` **全量返回**，按 `field_key` 排序，`metadata` 不强制携带 `total`；返回字段 `fieldId` / `fieldKey` / `fieldName` / `fieldEnable`（Boolean）/ `fieldRemark` / `fieldUpdateTime`。
- `enable` 请求体 `{ fieldId, fieldEnable }`，与全工程 `/enable` 通道约定一致；缺 `fieldId` 返回 `BAD_REQUEST`，不落入更新分支；成功后更新 DB → 本实例缓存立即刷新 → 尝试 Redis pub/sub 广播失效通知其他实例（best-effort，失败仅记日志、依赖 TTL 收敛，多实例最迟 30 秒一致）；操作日志经既有 `LogService` 记录（`action` 用 `common.module.enable`），日志失败不影响主流程。
- 消费方：模块管理页（`ModuleManageView.vue`）与前端模块状态 store（`stores/module.js`，登录后拉取缓存，供菜单过滤与「模块未开启」空态判定共用）。

#### 4.9.3 错误码与拦截语义（MODULE_DISABLED）

| 项 | 说明 |
| --- | --- |
| 触发场景 | 已登录请求命中**已关闭**模块组的 `@ModuleGroup` 注解（`ModuleGroupInterceptor` 抛 `ModuleDisabledException`） |
| 响应结构 | `BaseResponse.failure("MODULE_DISABLED", "该模块未开启，请联系管理员")`；HTTP 状态仍为 200 + code 体系（全工程惯例） |
| 生效位置 | 拦截器层，请求**不进入** Controller 与 resolver（先于业务鉴权）；注解读取方法级优先、类级回退，`SYS_ADMIN` 不穿透模块开关 |
| 公共模型 | `BaseResponse` 的 code `@Schema` 描述已同批追加 `MODULE_DISABLED` 错误码 |
| 前端处理 | axios 拦截器（`api/index.js`）识别后统一提示，仅提示不跳转（区别于 `UNAUTHORIZED` 清登录态跳登录页） |

- 匿名策略（D2）：未登录请求命中关闭模块组的注解时**放行给最终鉴权**，返回 `UNAUTHORIZED`，不返回 `MODULE_DISABLED`（不向匿名暴露开关状态）；
- fail-open 口径（均不返回错误，按「开启」放行并告警）：注解 `moduleGroupKey` 未在 `common_module_group` 登记（启动期 WARN、运行期放行）；DB 异常且缓存不可用（放行并告警）；DB 异常但有缓存（沿用上次缓存值并告警）；Redis 不可用 / 广播失败仅记日志。

#### 4.9.4 当前冻结的接口方向

- 无注解 Controller 不受开关控制（登录、`/init`、`/initID`、`setting/load`、SSE 订阅、swagger 等公共入口天然放行）；首批标注 `SysCalendarMainController`（类级 `calendar`）与 `SysOrgGroupController`（类级 `org-group`）。
- 后续追加模块组只需「`moduleGroup.yml` 加条目 + Controller 加注解」，接口机制本身无需改动；权限码 `SYS_MODULE_ADMIN`（名称「模块组管理员」）定义于 `properties/common/roles.yml`，与库两处同批提交。

## 5. 待确认事项

- 组织架构模块中“角色分配”和“角色权限”的需求表述存在表名映射不一致，需在详细设计任务中确认接口术语。
- 附件上传是否需要拆分“预签名上传/直传”模式，当前需求未覆盖，暂不纳入骨架。
- 流程模块是否需要单独暴露“抄送读取”接口，当前先并入流程实例读取范围。
- 待办关注相关表在需求文档中写为 `start`，接口次级模块暂按 `star` 预留，需在数据库设计阶段确认。

# AGENTS

本文件把 [docs/开发规范.md](D:/Program/CommonSystem/docs/开发规范.md) 落地为本仓库可执行的约束，只描述当前仓库真实存在的结构与代码。冲突时以 `docs/开发规范.md` 为准；本文未覆盖的细节按相邻现有代码风格补齐；与目录内更具体的 `AGENTS.md` 冲突时以那份为准。

本工程是业务系统（ERP 等）的**公共模块工程**：提供登录认证、组织架构、附件、日程、码值、日志、通知待办、流程审批、定时任务、公共配置。**不含任何 ERP 业务模块**，不新增 `erp` 包。

## 1. 工程事实

```text
CommonSystem/
├─ backend/    # Spring Boot 后端（Maven 单模块）
├─ frontend/   # Vue 3 + Vite；构建产物直出 backend/src/main/resources/static
├─ docs/       # 开发规范、系统模块文档、设计文档分册、迁移说明
└─ README.md
```

| 项 | 值 |
| --- | --- |
| Java 根包 / 启动类 | `com.common.system` / `com.common.system.CommonSystemApplication` |
| Maven artifactId / 前端包名 | `common-system` / `common-system-frontend` |
| 环境变量前缀 | `CS_*`（如 `CS_DB_HOST`、`CS_REDIS_HOST`、`CS_ATT_STORAGE_PATH`） |

技术栈：JDK 21、Spring Boot 3.5.13、MyBatis 3.0.5、Flyway 10.22.0、MySQL Connector 8.4.0、Redis、Quartz、Security、EasyExcel 4.0.3 + POI 5.2.5；Vue 3.5.32、Vite 5.4.21、Router 4.6.4、Pinia 2.3.1、Element Plus 2.13.7、Axios 1.15.0。版本以 `backend/pom.xml`、`frontend/package.json` 为准，勿在本文另立新版本。

```bash
cd backend  && ./mvnw test            # 单元测试（Windows：mvnw.cmd test）
cd backend  && ./mvnw spring-boot:run # 启动服务，默认 8080；运行期需 MySQL、Redis
cd frontend && npm run dev            # 5173，/backend 代理到 http://localhost:8080
cd frontend && npm run build          # 产物直出 backend/src/main/resources/static（emptyOutDir 会先清空该目录）
```

- 前端无 lint / test 脚本，前端改动以 `npm run build` 通过为准。
- 禁止在新代码中出现 `com.project.backend`、`BackendApplication`、`ERP_*`、`artificial/` 等 ERP 工程标识。
- 任务期间自行启动的服务（后端、dev server、构建进程）必须在结束前停止。

后端资源目录：`application.properties`（全局配置）、`db/migration/`（Flyway）、`mappers/{common,sys}/`（与 Java 包路径对应）、`properties/{common,sys}/{模块}/{application,dict,roles}.yml`、`static/`（前端产物）。

## 2. 模块与包结构

只包含以下模块；新增模块须先补设计文档并确认包名、接口路径、表结构与权限模型：

- `com.common.system.common`：`base`（基类）、`cache`、`config`、`exception`、`properties`、`resolver`、`session`、`setting`、`utils`
- `com.common.system.sys`：`org`、`att`、`calendar`、`dict`、`log`、`notify`、`process`、`task`

```text
com.common.system.sys.{模块}
├─ controller   # 只做接收/校验/返回
├─ service      # 业务逻辑；必要时增 task / listeners / event 等次级包
├─ dao          # 与 Mapper 一对一
├─ mapper       # 与 resources/mappers 下同名 XML 对应
├─ model        # 实体，继承 BaseModel（主从用 MainModel）
├─ enums / properties / request / response
└─ resolver     # 继承 BaseResolver
```

`request` / `response` / `mapper` 与 `model` **平级**，不采用 `docs/开发规范.md`「三：系统统一规范」中的 `model/{vos,dos,mappers}` 子包形态。

前端：`api`（按模块拆 `common.js`、`org.js`、`dict.js`、`task.js` 等，统一经 `index.js` 导出实例）、`components`（公共组件 + `process/`）、`router`、`stores`、`styles`（`base.css` / `list-page.css` / `form-page.css`）、`utils`、`views`（`att` / `calendar` / `dict` / `log` / `notify` / `org` / `process` / `setting` / `task`）。公共组件样例页 `views/ui.vue`，契约见 `docs/系统模块/前端公共组件.md`。

## 3. 后端约束

**分层**：`Controller` 继承 `BaseController` / `BaseCrudController`，只接收与校验，不写业务；`Service` 承载业务，模块级直接实现类（仅基础层保留 `IBaseService` / `BaseService`），不得直连 `Mapper`；`Dao` 与 `Mapper` 一对一，仅基础层保留 `IBaseDao` / `BaseDao`；复杂 SQL 写 XML；可复用的小逻辑放 `common/utils`（`IDUtil`、`StringUtil`、`IntegerUtil`、`FieldProjector`、`PasswordUtil`、`RoleUtil`、`UserUtil`、`YmlResourceUtil`）。启动期初始化统一追加到 `sys/task/SysStartupTask#run`，不另建 `ApplicationRunner`。

**接口**：路径 `/backend/{根模块}/{模块}/{次级模块}/{方法}`，`{根模块}` 只有 `common` 与 `sys`。

| 接口 | 方法 | 示例 |
| --- | --- | --- |
| `/init`、`/initID` | GET | `/backend/common/init`、`/backend/common/initID` |
| `/load?fieldId=` | GET | `/backend/sys/org/element/load` |
| `/list`、`/save` | POST | `/backend/sys/org/element/list`、`/backend/sys/att/main/upload` |
| `/update`、`/enable` | PATCH | `/backend/sys/org/element/update`、`/backend/sys/org/element/enable`（请求体 `{ fieldId, fieldEnable }`） |
| `/delete?fieldId=` | DELETE | `/backend/sys/org/element/delete` |
| `/export`、`/import`、`/template` | POST | `/backend/sys/dict/export`、`/backend/sys/dict/import`（multipart：`file` + `params`）、`/backend/sys/dict/template`（导入导出公共通道，先例 dict 试点） |

单 `id` 或无参用 `GET`；含非 `id` 条件用 `POST`；更新只用 `PATCH`（禁 `PUT`）；删除用 `DELETE`。列表请求体 `{ body, pages: { pageNo, pageSize, needPage } }`，总数经 `metadata.total` 返回；响应统一 `BaseResponse`，异常交 `GlobalExceptionHandler`。

**鉴权**：核心参数是「接口标识（action）+ 文档 ID」，由 `AuthorizationContext` 承载。接口标识用 `ACTION_*` 常量定义在模块 `properties` 常量类（如 `SysOrgElementProperties.ACTION_SAVE = "sys.org.element.save"`），Controller 与 resolver 静态引用，禁止手写字符串。每个模块必须有 resolver（`ElementResolver`、`DictResolver`、`TaskResolver` 等）继承 `BaseResolver`，按 `switch (context.getAction())` 判定；`SYS_ADMIN` 已在 `BaseResolver` 中自动放行，模块分支不要再列举。Controller 经 `authorize(action, documentId)` 做最终鉴权，未登录抛 `UNAUTHORIZED`、无权限抛 `FORBIDDEN`。编辑、启停用、删除必须传被操作对象 `fieldId`。

权限码取 `sys_org_role.field_code`，在 `properties/{common,sys}/{模块}/roles.yml` 定义：`SYS_ADMIN`（全权）、`SYS_ORG_*`、`SYS_ATT_MAIN`、`SYS_CALENDAR_SENDLEADER`、`SYS_DICT_ADMIN`、`SYS_LOG_ADMIN`、`SYS_NOTIFY_ADMIN`、`SYS_PROCESS_*`、`SYS_TASK_ADMIN`。批量鉴权接口只供前端按钮预判，不能替代最终鉴权。

**接口文档同步（强制）**：在线文档由代码注解生成（`GET /swagger-ui.html`、`GET /v3/api-docs`），**新增或修改接口必须与代码同批更新文档信息**，不允许留下一份与代码不一致的 api-docs。

| 变更内容 | 必须同批更新的注解 / 文档 |
| --- | --- |
| 新增接口方法 | `@Operation(summary=中文动作名, description="鉴权动作 <ACTION 常量值>；…")`；`@RequestParam`、`MultipartFile` 等非请求体参数补 `@Parameter(description=…)` |
| 新增 Controller | 类级 `@Tag(name=中文模块名, description=页面/权限概述)` |
| 路径、HTTP 方法、参数名或必填性变化 | 同步 `@Operation` 描述与 `@Parameter(required=…)` |
| 请求/响应字段增删或语义变化 | 公共模型（`BaseResponse`/`BaseRequest`/`Pages`/`BaseModel`）与本次涉及字段的 `@Schema(description=…)`；排序字段同步 `allowableValues` 与 `defaultValue` |
| 鉴权方式变化 | 全局 `SecurityScheme LoginToken` 之外的情况在方法上补 `@SecurityRequirements`（如登录、登出、`init`/`initID`、`setting/load`、SSE 订阅） |
| 非 JSON 响应（文件流、SSE） | `@ApiResponse(content=@Content(mediaType=…, schema=@Schema(...)))`，文件流用 `APPLICATION_OCTET_STREAM_VALUE` + `format="binary"` |
| 接口分组/权限/路径规则变化 | 同步 `docs/系统模块/系统模块API接口设计文档.md`（含「3.5 在线接口文档」） |

- `@Operation.description` 统一以「鉴权动作 <ACTION 常量值>（权限说明）；」开头；无 `ACTION_*` 常量的入口写「无需鉴权（…）」，禁止留空或只写中文业务描述。
- 删除接口时同步删除其注解与 `@Tag`（若整个 Controller 移除），并回查文档中是否仍有引用。
- 注解只做增量：不得为补齐文档而改动接口路径、HTTP 方法、`ACTION_*` 常量、resolver 分支或业务逻辑。
- 校验方式：`cd backend && ./mvnw -Dtest=ApiDocsTest test`（断言文档 JSON 可生成并含中文摘要），必要时启动服务人工核对 Swagger UI 分组与参数。

## 4. 数据库与迁移

**字段**：统一 `field_` 前缀。公共字段 `field_id`（`VARCHAR(32)`，毫秒时间戳 + UUID v4，`IDUtil.initID()`）、`field_code`、`field_name` / `field_subject`、`field_creator_id`、`field_create_time`、`field_updater_id`、`field_update_time`、`field_enable`（`BIT(1)` 默认 `b'1'`）。

> `docs/开发规范.md`「三：系统统一规范」中的字段清单写作 `field_updator_id` 是笔误，迁移脚本与实体均为 **`field_updater_id`**。
> 流水/日志类表只增不改，不设 `field_enable` / `field_update_time` / `field_updater_id`（先例 `sys_log_operation`、`sys_task_history`）。

**迁移**：目录 `db/migration/`，命名 `V{大版本}_{小版本}_{序号}__{英文下划线描述}.sql`。当前最新为 `V1_01_038`，新脚本从 `V1_01_039` 起递增，不得复用或跳回旧序号；改表结构必须同时补脚本。与 ERP 工程合并部署时，`V1_01_020` 等 ERP 专属脚本保留在 ERP 侧执行，本仓库不得重复引入，包名反向替换规则见 [docs/迁移说明.md](D:/Program/CommonSystem/docs/迁移说明.md)。

**树形表**：当前只有 `sys_org_element` 一张。字段为 `field_parent_id`（根级 `NULL`）、`field_hierarchy`（`/id1/id2/`）、`field_level`，前两者建索引（`idx_org_element_parent`、`idx_org_element_hierarchy`）。保存时由 `SysOrgElementService#rebuildHierarchy` 计算层级，父级变更后递归修正子树（`rebuildChildren`）；新父级不能是自身或后代（按 `hierarchy` 前缀判定）。`updateEnable` 只更新自身一行、**不级联后代**——若需级联停用，先补需求文档与前端提示再改 Service 与 SQL。

## 5. 配置分层

`properties/{common,sys}/{模块}/` 下三类文件，启动时由 `YmlResourceUtil` 通配 `classpath*:properties/**` 扫描：

| 文件 | 内容 | 消费方 |
| --- | --- | --- |
| `application.yml` | `MODEL_KEY` / `MODEL_NAME` / `version`，可选 `SETTING` 段 | `SysOrgAuthService`、`CommonSettingService` |
| `dict.yml` | 系统码值（导入后 `field_system=true`） | `SysDictService#syncDictFromYml`，增量导入，不覆盖人工码值 |
| `roles.yml` | `ROLE_LIST` + `ROLE_MAP` | `SysOrgAuthService`，增量同步到 `sys_org_auth` |

- 新增权限码必须同时改库与 `roles.yml`，两处同批提交。
- `SETTING` 段层级 `SETTING.${field_type}.${field_key}`，写入 `common_setting`；`database` / `redis` 段同时作为 `application.properties` 连接默认值。
- 覆盖顺序：环境变量 `CS_*` > `SETTING` 默认值 > 代码兜底。新增配置按 `${CS_X:${SETTING.a.b:default}}` 三段式书写，禁止在 Java 里硬编码默认值。
- 不新增未纳入扫描路径的 yml；yml 注释中的 `artificial/docs/...` 是历史残留，改到时顺手修正为 `docs/设计文档/`。

## 6. 前端约束

接口一律经 `src/api/index.js` 的 axios 实例以 `/backend/...` 相对路径请求，由 Vite 代理转发；页面只 import `api/*.js` 的具名函数，不直接写 `axios` 或裸 URL。

**列表页**（基准 `OrgManageView.vue`、`TaskManageView.vue`、`AttManageView.vue`）：三段式「筛选区 → 工具区 → 列表区」。筛选区用 `el-form.filter-form` + `el-col :span="6"` 一行四列，输入框回车触发查询，枚举筛选默认值按业务语义设定。工具区左侧 `sort-area` 仅单条件排序，选中项 `.active` + `.sort-arrow` 显示 `↑`/`↓`，默认创建时间降序；右侧为查询、创建、批量操作，启停用与删除须二次确认。列表首列复选框（有批量操作时）、末列操作列 `fixed="right"`，不展示主键；分页 `layout="total, prev, pager, next, sizes"`、`page-sizes [10,20,50,100]`，切页大小回第 1 页。样式复用 `list-page.css`，不在页面内重复定义。

**新建/编辑/查看**：独立路由 `/{资源}/create`、`/edit/:id`、`/detail/:id`；新建/编辑共用一个表单组件（按 path 区分模式），查看为独立只读组件。新建时在 `onMounted` 调 `api/common.js` 的 `getInitID()`（`GET /backend/common/initID`）取 32 位 ID 赋给 `fieldId`，编辑/查看按路由 id 调 `load` 回填。**当前尚无** `composables/useInitID.js`，规范中的统一封装未实现，新增页面照现有页面直接调 `getInitID()`。布局：容器 100% 宽，默认一行两字段（`label-width="40%"` + `:span="12"`），长内容字段一行一字段（2:8）。详情不用 `el-descriptions` 跨行，改用 `form-page.css` 的 `.detail-grid` / `.detail-item` / `.is-full`。操作按钮放 `el-card` 的 `#header` 内 `.page-header > .header-btns`，不用底部操作区。动态路由设 `meta.tab: 'name'` 复用单标签。新增资源须在 `HomeView.vue` 的 `activeMenu` 补前缀归约、在菜单配置中补菜单项。

**树形页面**：本工程当前**没有分类树形列表页**（ERP 的 `MdmItemList` 与各类分类页均不在此仓库）。唯一含层级的是 `OrgManageView.vue`：左侧 `el-tree`（`label: 'fieldName'`）+ 可拖拽 `org-divider`，右侧为标准三段式表格；点左侧节点作为右侧 `fieldParentId` 条件。新增层级页须先补 UI 文档，勿照搬 ERP 的「层级栈 + 单选行」形态。

## 7. 命名

- 后端：`Sys{模块}{业务}`（`SysOrgElement`、`SysAttMain`、`SysCalendarMain`、`SysTaskMain`）；`{前缀}{业务}Controller|Service|Dao|Mapper`（无 `I` 前缀，仅基础层保留）；`{业务}Resolver`；请求/响应 `XxxRequest` / `XxxVo`、`XxxResponse`；常量类 `{模块}Constants` 或 `{模块}Properties`。
- JSON：固定结构建 POJO（`{业务}{语义}Do`，如 `SysProcessTemplateNodeConfigDo`），用 Jackson 序列化；非固定结构（如 `SysTaskMain.fieldParams`）以 `String` 存原始 JSON，禁止手工拼串。
- 前端：页面 `PascalCase` 且以 `View.vue` 结尾（`{业务}ManageView` / `FormView` / `DetailView`）；组件 `PascalCase.vue`；`api` / `stores` / `utils` / `styles` 用小写或 `kebab-case`；路由 `name` 用 `PascalCase` 语义名。
- 数据库：表名、字段名、索引名下划线风格，字段保留 `field_` 前缀。
- 文档：中文业务名 + 文档类型，不用英文缩写。

## 8. 文档体系

| 类型 | 位置 | 内容 |
| --- | --- | --- |
| 需求 | `docs/系统模块/系统模块需求文档.md`、`docs/设计文档/sys/*/…核心业务*.md` | 业务流程、角色权限、异常处理 |
| UI | `docs/系统模块/系统模块UI设计文档.md`、`docs/设计文档/sys/org/组织架构前端页面设计.md` | 路由、菜单及显示权限、页面布局、交互 |
| API | `docs/系统模块/系统模块API接口设计文档.md`、`docs/设计文档/**/…接口清单.md` | 接口规则、约束、接口内流程 |
| 数据库 | `docs/系统模块/系统模块数据库设计.md`、`docs/设计文档/**/…表结构.md` | 表字段、索引、外键 |

补充：`docs/开发规范.md`（总规范基线）、`公共接口.md`、`前端公共组件.md`、`数据库必填和表单必填字段对比表.md`、`字段级可见性设计方案.md`、`迁移说明.md`（抽取边界与 ERP 接入，改命名或配置键时必须同步）。

在线接口文档由 springdoc-openapi 从代码注解生成：`GET /swagger-ui.html`（交互页面）、`GET /v3/api-docs`（OpenAPI 3 JSON）；统一开关为环境变量 `CS_API_DOCS_ENABLED`（默认开启），属部署运行参数，**不入 `common_setting`**、不设前端入口。规范与偏离说明见 `docs/系统模块/系统模块API接口设计文档.md` 的「3.5 在线接口文档」；新增/修改接口时的注解同步要求见本文第 3 节「接口文档同步（强制）」。

冲突裁决按冲突项归属：业务流程/权限/异常 → 需求；路由菜单/页面布局 → UI；接口规则 → API；表结构与索引 → 数据库。未覆盖时回退 `docs/开发规范.md`；仍不明确时先补文档，不得以现有代码反推业务规则。开发顺序：需求 → UI → API → 数据库 → 代码与迁移脚本，实现导致设计变更时同批更新文档。

## 9. 执行准则

1. 先读 `docs/开发规范.md` 与本次涉及的模块文档，再确认代码归属的模块与分层目录。
2. 实现前核对接口路径、HTTP 方法、`ACTION_*` 与 resolver 分支、表结构与迁移序号。
3. 实现后回查文档、代码、迁移脚本三者一致。
4. 部署验证：`cd frontend && npm run build` 后运行 `CommonSystemApplication`（8080），结束前停止服务。

提交前自检：

- [ ] 新接口：路径与方法合规，有 `ACTION_*` 常量、resolver `case` 分支、Controller 调用 `authorize(...)`。
- [ ] 接口文档：新增/修改的接口已同步 `@Tag` / `@Operation` / `@Parameter` / `@Schema`，`@Operation.description` 以「鉴权动作 …」开头；改动接口分组或路径规则时同步 `docs/系统模块/系统模块API接口设计文档.md`。
- [ ] 新权限码：已同步 `roles.yml` 的 `ROLE_LIST` 与 `ROLE_MAP`。
- [ ] 新表/字段：`field_` 前缀、公共字段齐全、已补迁移脚本且序号接续。
- [ ] 新页面：路由（含 `meta.title`、动态路由 `meta.tab: 'name'`）、`HomeView.vue` 菜单与 `activeMenu`、UI 文档三者同步。
- [ ] 无 ERP 工程标识残留；测试 `./mvnw test` 与 `npm run build` 通过。

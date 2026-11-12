# 接口开关机制API设计分册

> 本文档为接口开关机制（V1.1，模块组级启停）的 API 分册，依据《接口开关机制设计方案-修订稿.md》（下称「修订稿」）第 8.3 / 10 节拆分整理。
> 接口标识（action）常量定义于 `CommonModuleProperties`（`com.eric.base.common.module.properties`），Controller 与 resolver 静态引用，禁止手写字符串；
> 鉴权由 `CommonModuleController` 调用 `authorize(action, documentId)` 经 `ModuleResolver`（继承 `BaseResolver`）兜底：未登录抛 `UNAUTHORIZED`、无权限抛 `FORBIDDEN`，`SYS_ADMIN` 经 `BaseResolver` 自动放行（模块分支内不重复列举）。
> 响应统一 `BaseResponse`（HTTP 200 + code 体系），异常交 `GlobalExceptionHandler`。
> 配套分册：[接口开关机制需求分册.md](接口开关机制需求分册.md)、[接口开关机制UI设计分册.md](接口开关机制UI设计分册.md)、[接口开关机制数据库设计分册.md](接口开关机制数据库设计分册.md)。

## 1. 接口清单总览

基础路径：`/backend/common/module`（`${project.request.prefix}/common/module`）。

| 接口标识 | 方法 | 路径 | 说明 | 鉴权 |
| --- | --- | --- | --- | --- |
| `common.module.list` | POST | `/backend/common/module/list` | 模块组列表全量查询 | 登录即可（无角色要求） |
| `common.module.enable` | PATCH | `/backend/common/module/enable` | 模块组启停 | SYS_MODULE_ADMIN |

- `CommonModuleController` 自身**不打** `@ModuleGroup`：保证关闭任意模块组后管理面仍可用；
- 开关判定位于业务鉴权之前（`ModuleGroupInterceptor` 先于 Controller `authorize`）：关闭模块组下这两个管理接口不受影响（无注解放行），被关闭业务模块的接口返回 `MODULE_DISABLED`、不再讨论权限。

## 2. 模块组列表（common.module.list）

| 项 | 约定 |
| --- | --- |
| 方法 / 路径 | `POST /backend/common/module/list` |
| 鉴权动作 | `common.module.list`（resolver 分支 `isEnabled(user)`：**登录即可访问**，无角色要求；免登录不可访问——模块组列表暴露系统拓扑，不向匿名开放） |
| 请求体 | `{ body: {}, pages: { pageNo, pageSize, needPage } }`（遵循 list 通道；`body` 预留查询条件扩展位，首批无业务筛选字段） |
| 分页口径 | 模块组量极小，默认 `needPage=false` **全量返回**；`metadata` 不强制携带 `total` |
| 排序口径 | 按 `field_key` 排序返回（全量查询 SQL 固定排序） |
| 返回字段 | `fieldId` / `fieldKey` / `fieldName` / `fieldEnable`（Boolean）/ `fieldRemark` / `fieldUpdateTime` |
| 消费方 | 模块管理页（`ModuleManageView.vue`）与前端状态 store（`stores/module.js`，登录后拉取缓存） |

## 3. 模块组启停（common.module.enable）

| 项 | 约定 |
| --- | --- |
| 方法 / 路径 | `PATCH /backend/common/module/enable` |
| 鉴权动作 | `common.module.enable`（resolver 分支 `hasAnyRole(user, SYS_MODULE_ADMIN)`；`SYS_ADMIN` 经 `BaseResolver` 自动放行，分支内不重复列举） |
| 请求体 | `{ fieldId, fieldEnable }`，与全工程 `/enable` 通道约定一致 |
| 参数约束 | 必须携带被操作对象 `fieldId`（未携带时校验失败返回 `BAD_REQUEST`，不落入更新分支）；`fieldEnable` 为目标状态布尔值（`true` 开启 / `false` 关闭） |
| 成功后动作 | 更新 DB → 本实例缓存立即刷新 → 尝试 Redis pub/sub 广播失效通知其他实例（best-effort，失败仅记日志、依赖 TTL 收敛，多实例最迟 30 秒一致） |
| 操作日志 | 切换属低频敏感操作，Service 层经既有 `LogService` 记录，`action` 用 `common.module.enable`，日志失败不影响主流程 |
| 幂等性 | 同值重复切换允许（开启态再传 `true` 等幂等更新，状态与时间戳由 DB 语义决定） |

## 4. 错误码说明

### 4.1 MODULE_DISABLED（新增）

| 项 | 说明 |
| --- | --- |
| 触发场景 | 已登录请求命中**已关闭**模块组的 `@ModuleGroup` 注解（拦截器抛 `ModuleDisabledException`） |
| 响应结构 | `BaseResponse.failure("MODULE_DISABLED", "该模块未开启，请联系管理员")`；HTTP 状态仍为 200 + code 体系（全工程惯例） |
| 生效位置 | 拦截器层，请求**不进入** Controller 与 resolver（先于业务鉴权） |
| 公共模型同步 | `BaseResponse` 的 code `@Schema` 描述同批追加 `MODULE_DISABLED` |
| 前端处理 | axios 拦截器识别后统一提示，仅提示不跳转（详见 UI 分册第 8 节） |

### 4.2 既有错误码（沿用）

| 错误码 | 触发场景（与本机制相关） |
| --- | --- |
| `UNAUTHORIZED` | 未登录/凭证失效。**注意**：未登录请求命中关闭模块组的注解时**不返回本码的模块分支**，而是放行给最终鉴权后由鉴权链路返回（来自修订稿默认决策 D2，不向匿名暴露开关状态） |
| `FORBIDDEN` | 已登录但无权限（如无 `SYS_MODULE_ADMIN` 调 `/enable`） |
| `BAD_REQUEST` | 参数校验失败（如 `/enable` 未携带 `fieldId`） |
| `SUCCESS` | 业务处理成功 |

### 4.3 fail-open 口径（不返回错误）

以下场景按「开启」放行并告警，不返回 `MODULE_DISABLED`：

- 注解 `moduleGroupKey` 未在 `common_module_group` 中登记（启动期记 WARN，运行期放行）；
- DB 异常且缓存不可用（放行并告警）；DB 异常但有缓存（沿用上次缓存值并告警）；
- Redis 不可用 / 广播失败仅记日志，不报错给调用方。

## 5. 鉴权模型与 resolver 分支

`ModuleResolver`（继承 `BaseResolver`）按 `switch (context.getAction())` 判定：

| ACTION 常量 | 值 | resolver 分支 |
| --- | --- | --- |
| `CommonModuleProperties.ACTION_LIST` | `common.module.list` | `isEnabled(user)`：登录即可（无角色要求） |
| `CommonModuleProperties.ACTION_ENABLE` | `common.module.enable` | `hasAnyRole(user, SYS_MODULE_ADMIN)` |

- `SYS_ADMIN` 已在 `BaseResolver` 中自动放行，模块分支不重复列举；但系统管理员**不穿透模块开关**（关闭模块组下同样 `MODULE_DISABLED`）；
- 权限码 `SYS_MODULE_ADMIN`（名称「模块组管理员」）定义于 `properties/common/roles.yml`，与库两处同批提交。

## 6. Swagger 注解同步规则（强制）

新增接口与代码同批更新文档注解，校验方式：`cd backend && ./mvnw -Dtest=ApiDocsTest test`。

| 项 | 要求 |
| --- | --- |
| 类级注解 | `CommonModuleController` 补 `@Tag(name = "模块管理", description = …页面/权限概述…)` |
| 方法级注解 | 每个接口 `@Operation(summary = 中文动作名, description = "鉴权动作 <ACTION 常量值>；…")`；`list` 的 description 写明「登录即可」；`enable` 的 description 写明权限（`SYS_MODULE_ADMIN`，`SYS_ADMIN` 自动放行） |
| 请求体字段 | `/enable` 的 `fieldId` / `fieldEnable` 等请求字段补 `@Schema(description = …)` |
| 公共模型 | `BaseResponse` code `@Schema` 追加 `MODULE_DISABLED` 错误码 |
| SecurityScheme | `list` / `enable` 均走全局 `LoginToken` 头，无免鉴权特例，**无需** `@SecurityRequirements` 特殊声明 |
| 增量原则 | 注解只做增量：不得为补齐文档改动接口路径、HTTP 方法、`ACTION_*` 常量、resolver 分支或业务逻辑 |

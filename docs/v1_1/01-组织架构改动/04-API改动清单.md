# API 改动清单（草案）

> 遵循 AGENTS：路径 `/backend/{根模块}/{模块}/{次级模块}/{方法}`，`{根模块}` 仅 `common` / `sys`；
> 单 `id`/无参用 `GET`，含非 `id` 条件用 `POST`，更新只用 `PATCH`（禁 `PUT`），删除用 `DELETE`；
> 每个接口必须有 `ACTION_*` 常量 + resolver 分支 + Controller `authorize(action, documentId)`；
> 列表请求体 `{ body, pages }`，响应统一 `BaseResponse`，异常交 `GlobalExceptionHandler`。

## 1. 新增：群组维护（`sys.org.group.*`）

基础路径：`/backend/sys/org/group`

常量类：`SysOrgGroupProperties`（新增），放在 `com.common.system.sys.org.properties`。

```java
public static final String ACTION_LIST   = "sys.org.group.list";
public static final String ACTION_LOAD   = "sys.org.group.load";
public static final String ACTION_SAVE   = "sys.org.group.save";
public static final String ACTION_UPDATE = "sys.org.group.update";
public static final String ACTION_DELETE = "sys.org.group.delete";

public static final String ROLE_SYS_ORG_ADMIN       = "SYS_ORG_ADMIN";
public static final String ROLE_SYS_ORG_GROUP_ADMIN = "SYS_ORG_GROUP_ADMIN";
```

### 1.1 接口定义

| 接口标识 | 方法 | 路径 | 请求 | 响应 |
| --- | --- | --- | --- | --- |
| `sys.org.group.list` | POST | `/list` | `{ body: { fieldName, fieldType, fieldIsAllViewer, fieldOwnerId? }, pages, sortField, sortOrder }` | `List<SysOrgGroupVo>` + `metadata.total` |
| `sys.org.group.load` | GET | `/load?fieldId=` | — | `SysOrgGroupVo`（含 `userIds/userNames`、`viewerIds/viewerNames`） |
| `sys.org.group.save` | POST | `/save` | 群组对象（含明细数组） | 保存后的 `SysOrgGroupVo` |
| `sys.org.group.update` | PATCH | `/update` | 群组对象（必须带 `fieldId`） | 更新后的 `SysOrgGroupVo` |
| `sys.org.group.delete` | DELETE | `/delete?fieldId=` | — | `Void` |

- `sortField` 白名单：`createTime` / `updateTime` / `name`，默认 `createTime` 降序。
- 列表返回字段：`fieldId`、`fieldName`、`fieldType`、`fieldOwnerId`、`fieldOwnerName`、`fieldIsAllViewer`、
  `fieldEnable`、`userCount`、`fieldCreateTime`、`fieldUpdateTime`（成员数与所有人名称由 SQL `COUNT` / `JOIN` 得到，避免 N+1）。
- 详情额外返回：`userIds`/`userNames`（与 `sys_org_group_user` 顺序一致）、`viewerIds`/`viewerNames`。
- 保存/更新请求体：

```json
{
  "fieldId": "1730000000000xxxxxxxxxxxxxxxx",
  "fieldName": "项目A组",
  "fieldType": "PERSONAL",
  "userIds": ["id1", "id2"],
  "viewerIds": ["deptId1"],
  "fieldEnable": true
}
```

> `field_owner_id` 不接受前端写入：新建时取当前登录人；编辑时保持原值（管理员维护他人群组也不改归属）。
> `field_is_all_viewer` 由 Service 派生，请求体中的该字段被忽略。

### 1.2 鉴权（`GroupResolver`）

```text
SYS_ADMIN                → 全部放行（BaseResolver.hasAnyRole 自动）
SYS_ORG_ADMIN            → 全部放行
SYS_ORG_GROUP_ADMIN      → 全部放行（可见范围仍需 Service 判定公共群组可见性）
普通用户                  → 允许 list / load / save / update / delete
```

即 resolver 只做「是否登录且启用」的基础校验 + 管理员判定，**具体的归属/可见性校验在 Service 层**（见 2.3 节）。
这与 `ElementResolver` 的「接口级鉴权」分工一致，但群组为「不设权 + 数据级过滤」，必须双层。

> 若希望 resolver 更严格，可让 `ACTION_SAVE` 仅放行管理员，普通用户的新建走单独 `ACTION_SAVE_PERSONAL`。
> 推荐**不拆**，避免接口膨胀；靠 Service 强制 `field_type = PERSONAL`。

## 2. 新增：地址本群组查询

基础路径：`/backend/sys/org/addressbook`（复用 `AddressBookResolver`，仅登录）

| 接口标识 | 方法 | 路径 | 请求 | 响应 |
| --- | --- | --- | --- | --- |
| `sys.org.addressbook.groupList` | POST | `/groupList` | `{ body: { keyword }, pages }` | 当前用户**可见**群组列表（`fieldId`、`fieldName`、`fieldType`、`userCount` 等） |
| `sys.org.addressbook.groupUsers` | POST | `/groupUsers` | `{ groupId, orgTypes? }` | 该群组的成员人员列表（按 `orgTypes` 求交，默认仅 3） |

- `groupList` 内部按 `02-群组功能分析.md` 2.2 的可见性 SQL 过滤。
- `groupUsers` 必须先校验群组对当前用户可见，不可见时返回 `FORBIDDEN`（或空列表，推荐 `FORBIDDEN` 便于排查）。
- 两个接口都只读，不需要 `ACTION` 之外的新权限码；`SysOrgAddressBookProperties` 增加两个常量。

## 3. 调整：组织架构元素接口

| 改动 | 说明 |
| --- | --- |
| `SysOrgElementMapper.xml#VoWhereClause` | 根级分支追加 `AND e.field_org_type NOT IN (5, 6)`；`query.orgType` 若传入 5/6 直接返回空（或由 Service 拒绝） |
| `SysOrgElementService#validateCommon` | 类型校验由 `1..4` 闭区间改为白名单 `Set.of(1,2,3,4)`；角色/群组禁止走 element 接口 |
| `SysOrgElementService#save` / `#update` / `#delete` | switch 增加 `default -> throw`（现状已有），无需改 |
| `SysOrgElementProperties` | 新增 `ORG_TYPE_ROLE = 5`、`ORG_TYPE_GROUP = 6` 常量（仅作语义与排除用，不参与 element 接口写入） |

> 结论：`sys.org.element.*` 接口**不新增**角色/群组分支，角色走 `sys.org.role.*`，群组走 `sys.org.group.*`。

## 4. 调整：地址本树与列表

| 改动 | 说明 |
| --- | --- |
| `SysOrgAddressBookMapper.xml#selectTree` | 根级分支与 `parentId` 分支追加 `AND field_org_type NOT IN (5, 6)`（当 `orgTypes` 未显式包含时）；推荐实现为「永远排除 5/6，除非 `orgTypes` 精确等于 [5] 或 [6]」——更简单的做法是 `orgTypes` 含 5/6 时由前端走专用页签接口，**地址本 tree 永久排除 5/6** |
| `#selectList` / `#countList` | 保持通用：`orgTypes` 含 5 时返回角色（方案 A 下即镜像行），不含时不返回。角色页签复用此接口 |
| `#selectByIds` | 不变（回填不受类型限制） |

> **推荐明确约定**：地址本 `tree` 只服务树形类型（1/2/3/4），角色与群组一律不走 tree。
> 这样无需在 SQL 里写复杂的 `orgTypes` 条件分支，也符合 R1-4 / R2-6「不参与树结构」。

## 5. 调整：角色接口

| 改动 | 方案 A | 方案 B |
| --- | --- | --- |
| `SysOrgRoleService#saveRole` | 同事务写 `sys_org_element` 镜像行（`field_org_type = 5`） | 不变 |
| `SysOrgRoleService#updateRole` | 同事务 upsert 镜像行（名称/编码/启用同步），`field_code` 允许清空同步 | 不变 |
| `SysOrgRoleService#ensureSystemAdminRole` | 同步写镜像行（幂等） | 不变 |
| `SysOrgRoleController` | 不变 | 不变 |
| `SysOrgRoleVo` | 可增加 `fieldOrgType`（只读，恒为 5）便于前端判断 | 无需 |

## 6. 鉴权矩阵汇总

| 接口 | 未登录 | 启用的普通用户 | SYS_ORG_GROUP_ADMIN | SYS_ORG_ADMIN | SYS_ADMIN |
| --- | --- | --- | --- | --- | --- |
| `group.list` | × | ✓（按归属/可见过滤） | ✓（全部） | ✓ | ✓ |
| `group.load` | × | 可见才 ✓ | ✓ | ✓ | ✓ |
| `group.save` | × | ✓（强制 PERSONAL） | ✓ | ✓ | ✓ |
| `group.update` | × | 仅自己的 PERSONAL | ✓ | ✓ | ✓ |
| `group.delete` | × | 仅自己的 PERSONAL | ✓ | ✓ | ✓ |
| `addressbook.tree/list/load` | × | ✓ | ✓ | ✓ | ✓ |
| `addressbook.groupList` | × | ✓（可见过滤） | ✓ | ✓ | ✓ |
| `addressbook.groupUsers` | × | 群组可见才 ✓ | ✓ | ✓ | ✓ |
| `role.*` | × | 需 SYS_ORG_ROLE_ADMIN / SYS_ORG_ADMIN | × | ✓ | ✓ |

## 7. 错误码与提示文案

| 场景 | 错误码 | 文案 |
| --- | --- | --- |
| 群组不存在 | `E_NOT_FOUND` | 未找到该群组 |
| 无维护权限 | `FORBIDDEN` | 无权维护该群组 |
| 普通用户指定公共类型 | `BAD_REQUEST` | 普通用户仅可创建个人群组 |
| 个人类型缺少所有人 | `BAD_REQUEST` | 个人群组必须指定所有人 |
| 成员不是启用人员 | `BAD_REQUEST` | 群组成员必须为启用的人员 |
| 可见范围不是机构/部门 | `BAD_REQUEST` | 可见范围仅支持机构或部门 |
| 群组不可见 | `FORBIDDEN` | 无权查看该群组 |

## 8. Swagger 注解要求

- `@Tag(name = "组织架构-群组", description = "...")` 加在 `SysOrgGroupController`。
- 每个方法 `@Operation(summary, description)`，description 必须写明「鉴权动作 `sys.org.group.xxx`」与请求体结构。
- 参数用 `@Parameter(description, required = true)`。
- 与现有 `SysOrgRoleController` / `SysOrgAddressBookController` 风格保持一致。

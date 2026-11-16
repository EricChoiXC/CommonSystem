package com.eric.base.sys.datasource.properties;

/**
 * 数据源模块常量
 *
 * @author Eric
 * @date 2026/11/13
 * @description 数据源模块接口标识（resolver 鉴权使用）、错误码与角色编码，
 *              供 Controller / Resolver / Service 静态引用，禁止手写字符串。
 *              鉴权口径依据 docs/v1_3-数据源/数据源功能设计方案.md「§5.1 接口清单」：
 *              <ul>
 *              <li>管理六动作（list / load / save / update / enable / delete）：
 *                  SYS_DATASOURCE_ADMIN；</li>
 *              <li>options / targets / query：登录即可，query 的目标接口鉴权由
 *                  SysDatasourceService#execute 在解析目标能力后委托目标 action 完成。</li>
 *              </ul>
 */
public class SysDatasourceConstants {

    /* ==================== 接口标识（resolver 鉴权使用） ==================== */

    /** 自定义数据源列表 */
    public static final String ACTION_LIST = "sys.datasource.list";
    /** 数据源详情 */
    public static final String ACTION_LOAD = "sys.datasource.load";
    /** 新建自定义数据源 */
    public static final String ACTION_SAVE = "sys.datasource.save";
    /** 编辑自定义数据源 */
    public static final String ACTION_UPDATE = "sys.datasource.update";
    /** 自定义数据源启停 */
    public static final String ACTION_ENABLE = "sys.datasource.enable";
    /** 删除自定义数据源 */
    public static final String ACTION_DELETE = "sys.datasource.delete";
    /** 可用数据源下拉（默认 + 自定义启用） */
    public static final String ACTION_OPTIONS = "sys.datasource.options";
    /** 目标查询能力清单（配置表单数据源） */
    public static final String ACTION_TARGETS = "sys.datasource.targets";
    /** 数据源统一执行入口 */
    public static final String ACTION_QUERY = "sys.datasource.query";

    /* ==================== 错误码（方案 §5 错误语义表） ==================== */

    /** 数据源编码不存在或自定义数据源已停用 */
    public static final String CS_DATASOURCE_NOT_FOUND = "CS_DATASOURCE_NOT_FOUND";
    /** 自定义数据源挂接的目标能力编码不在 Registry（Provider 下线残留） */
    public static final String CS_DATASOURCE_TARGET_INVALID = "CS_DATASOURCE_TARGET_INVALID";
    /** 条件 key 不在白名单 / op 不支持 / 字面量被禁止 / 值类型非法 */
    public static final String CS_DATASOURCE_CONDITION_INVALID = "CS_DATASOURCE_CONDITION_INVALID";
    /** 返回字段白名单含目标能力不存在的字段 */
    public static final String CS_DATASOURCE_FIELD_INVALID = "CS_DATASOURCE_FIELD_INVALID";
    /** 保存时编码与既有数据源（含默认数据源）重复 */
    public static final String CS_DATASOURCE_CODE_DUPLICATED = "CS_DATASOURCE_CODE_DUPLICATED";

    /* ==================== 角色编码 ==================== */

    /** 数据源管理权限码（roles.yml 由 F18-OPS-001 落地，权限码取 sys_org_role.field_code） */
    public static final String ROLE_SYS_DATASOURCE_ADMIN = "SYS_DATASOURCE_ADMIN";
}

package com.eric.base.sys.org.properties;

/**
 * 群组接口常量
 *
 * @author Eric
 * @date 2026/11/4
 * @description 群组(group)维护接口的接口标识与鉴权相关常量。
 *              权限规则遵循 docs/设计文档/sys/org/组织架构核心业务设计.md：
 *              群组「不设权 + 数据级过滤」，接口级仅要求已登录且账号启用，
 *              归属与可见性校验由 SysOrgGroupService 承担；
 *              群组管理员 SYS_ORG_GROUP_ADMIN、模块管理员 SYS_ORG_ADMIN
 *              与系统管理员 SYS_ADMIN（BaseResolver 自动放行）可维护全部公共群组。
 */
public class SysOrgGroupProperties {

    public static final String ACTION_LIST = "sys.org.group.list";
    public static final String ACTION_LOAD = "sys.org.group.load";
    public static final String ACTION_SAVE = "sys.org.group.save";
    public static final String ACTION_UPDATE = "sys.org.group.update";
    public static final String ACTION_DELETE = "sys.org.group.delete";

    public static final String ROLE_SYS_ORG_ADMIN = "SYS_ORG_ADMIN";
    public static final String ROLE_SYS_ORG_GROUP_ADMIN = "SYS_ORG_GROUP_ADMIN";

    /** 群组类型：COMMON=公共（管理员维护、按可见范围可见），PERSONAL=个人（仅所有人可见可维护） */
    public static final String GROUP_TYPE_COMMON = "COMMON";
    public static final String GROUP_TYPE_PERSONAL = "PERSONAL";
}

package com.common.system.sys.org.properties;

/**
 * 角色管理与权限预览接口常量
 *
 * @author Eric
 * @date 2026/8/10
 * @description 角色管理(role)与权限预览(preview)接口的接口标识与鉴权相关常量。
 *              权限规则：角色管理（列表/读取/新建/编辑/权限可选项）与权限预览
 *              均要求 SYS_ORG_ROLE_ADMIN（权限管理员），
 *              组织架构模块管理员 SYS_ORG_ADMIN 与系统管理员 SYS_ADMIN 自动放行。
 */
public class SysOrgRoleProperties {

    /* ==================== 角色管理（sys.org.role.*） ==================== */

    public static final String ACTION_LIST = "sys.org.role.list";
    public static final String ACTION_LOAD = "sys.org.role.load";
    public static final String ACTION_SAVE = "sys.org.role.save";
    public static final String ACTION_UPDATE = "sys.org.role.update";
    public static final String ACTION_AUTH_OPTIONS = "sys.org.role.authOptions";

    /* ==================== 权限预览（sys.org.preview.*） ==================== */

    public static final String ACTION_PREVIEW = "sys.org.preview.preview";
    public static final String ACTION_PREVIEW_TREE = "sys.org.preview.treeChildren";

    /* ==================== 角色相关权限码 ==================== */

    public static final String ROLE_SYS_ORG_ADMIN = "SYS_ORG_ADMIN";
    public static final String ROLE_SYS_ORG_ROLE_ADMIN = "SYS_ORG_ROLE_ADMIN";
}

package com.eric.base.sys.org.properties;

/**
 * @author Eric
 * @date 2026/8/4
 * @description
 */
public class SysOrgUserProperties {

    public static final String ACTION_SAVE = "sys.org.user.save";
    public static final String ACTION_UPDATE = "sys.org.user.update";
    public static final String ACTION_DELETE = "sys.org.user.delete";
    public static final String ACTION_LOAD = "sys.org.user.load";
    public static final String ACTION_LIST = "sys.org.user.list";
    public static final String ACTION_CHANGE_PWD = "sys.org.user.changePwd";
    public static final String ACTION_CHECK_PWD_CHANGE = "sys.org.user.checkPwdChange";
    // 导入导出已迁移至 SysOrgElementProperties，见 v1.2 组织架构导入导出
    public static final String ACTION_LOGIN = "sys.org.user.login";
    public static final String ACTION_LOGOUT = "sys.org.user.logout";

    public static final String ROLE_SYS_ORG_ADMIN = "SYS_ORG_ADMIN";
    public static final String ROLE_SYS_ORG_USER_MANAGER = "SYS_ORG_USER_MANAGER";

    public static final String ROLE_VISITOR = "SYS_ORG_USER_VISITOR";
}

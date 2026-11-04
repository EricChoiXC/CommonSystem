package com.common.system.sys.org.properties;

/**
 * 组织架构元素接口常量
 *
 * @author Eric
 * @date 2026/8/10
 * @description 组织架构元素(element)维护接口的接口标识与鉴权相关常量。
 *              权限规则遵循 artificial/docs/设计文档/sys/org/组织架构核心业务设计.md：
 *              查询/普通信息读取不设权（仅要求登录且账号启用）；
 *              新建/删除需要 SYS_ORG_ADMIN / SYS_ORG_ELEMENT_ADMIN / SYS_ORG_ELEMENT_ADD；
 *              编辑/启停用需要 SYS_ORG_ADMIN / SYS_ORG_ELEMENT_ADMIN / SYS_ORG_ELEMENT_EDIT。
 */
public class SysOrgElementProperties {

    public static final String ACTION_LIST = "sys.org.element.list";
    public static final String ACTION_LOAD = "sys.org.element.load";
    public static final String ACTION_SAVE = "sys.org.element.save";
    public static final String ACTION_UPDATE = "sys.org.element.update";
    public static final String ACTION_ENABLE = "sys.org.element.enable";
    public static final String ACTION_DELETE = "sys.org.element.delete";

    public static final String ROLE_SYS_ORG_ADMIN = "SYS_ORG_ADMIN";
    public static final String ROLE_SYS_ORG_ELEMENT_ADMIN = "SYS_ORG_ELEMENT_ADMIN";
    public static final String ROLE_SYS_ORG_ELEMENT_ADD = "SYS_ORG_ELEMENT_ADD";
    public static final String ROLE_SYS_ORG_ELEMENT_EDIT = "SYS_ORG_ELEMENT_EDIT";

    /** 组织架构类型：1=机构 2=部门 3=人员 4=岗位 */
    public static final int ORG_TYPE_ORG = 1;
    public static final int ORG_TYPE_DEPT = 2;
    public static final int ORG_TYPE_USER = 3;
    public static final int ORG_TYPE_POST = 4;

    /** 排序字段白名单：前端传 createTime / updateTime / code */
    public static final String SORT_CREATE_TIME = "createTime";
    public static final String SORT_UPDATE_TIME = "updateTime";
    public static final String SORT_CODE = "code";

    /** 排序方向 */
    public static final String ORDER_ASC = "asc";
    public static final String ORDER_DESC = "desc";
}

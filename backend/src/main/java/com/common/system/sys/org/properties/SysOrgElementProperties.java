package com.common.system.sys.org.properties;

import java.util.Set;

/**
 * 组织架构元素接口常量
 *
 * @author Eric
 * @date 2026/8/10
 * @description 组织架构元素(element)维护接口的接口标识与鉴权相关常量。
 *              权限规则遵循 docs/设计文档/sys/org/组织架构核心业务设计.md：
 *              查询/普通信息读取不设权（仅要求登录且账号启用）；
 *              新建/删除需要 SYS_ORG_ADMIN / SYS_ORG_ELEMENT_ADMIN / SYS_ORG_ELEMENT_ADD；
 *              编辑/启停用需要 SYS_ORG_ADMIN / SYS_ORG_ELEMENT_ADMIN / SYS_ORG_ELEMENT_EDIT；
 *              导出/下载模板需要 SYS_ORG_ADMIN / SYS_ORG_ELEMENT_ADMIN / SYS_ORG_ELEMENT_EXPORT；
 *              导入需要 SYS_ORG_ADMIN / SYS_ORG_ELEMENT_ADMIN / SYS_ORG_ELEMENT_IMPORT
 *              （v1.2 组织架构导入导出，见 docs/v1_2-用户管理中心/组织架构列表页面与导入导出改造方案.md）。
 */
public class SysOrgElementProperties {

    public static final String ACTION_LIST = "sys.org.element.list";
    public static final String ACTION_LOAD = "sys.org.element.load";
    public static final String ACTION_SAVE = "sys.org.element.save";
    public static final String ACTION_UPDATE = "sys.org.element.update";
    public static final String ACTION_ENABLE = "sys.org.element.enable";
    public static final String ACTION_DELETE = "sys.org.element.delete";
    public static final String ACTION_EXPORT = "sys.org.element.export";
    public static final String ACTION_IMPORT = "sys.org.element.import";
    public static final String ACTION_TEMPLATE = "sys.org.element.template";

    public static final String ROLE_SYS_ORG_ADMIN = "SYS_ORG_ADMIN";
    public static final String ROLE_SYS_ORG_ELEMENT_ADMIN = "SYS_ORG_ELEMENT_ADMIN";
    public static final String ROLE_SYS_ORG_ELEMENT_ADD = "SYS_ORG_ELEMENT_ADD";
    public static final String ROLE_SYS_ORG_ELEMENT_EDIT = "SYS_ORG_ELEMENT_EDIT";
    public static final String ROLE_SYS_ORG_ELEMENT_EXPORT = "SYS_ORG_ELEMENT_EXPORT";
    public static final String ROLE_SYS_ORG_ELEMENT_IMPORT = "SYS_ORG_ELEMENT_IMPORT";

    /** 组织架构类型：1=机构 2=部门 3=人员 4=岗位 5=角色 6=群组 */
    public static final int ORG_TYPE_ORG = 1;
    public static final int ORG_TYPE_DEPT = 2;
    public static final int ORG_TYPE_USER = 3;
    public static final int ORG_TYPE_POST = 4;
    /** 角色：仅作类型语义与树/列表排除用，角色数据走 sys.org.role.*（镜像行由角色服务维护） */
    public static final int ORG_TYPE_ROLE = 5;
    /** 群组：仅作类型语义与树/列表排除用，群组数据走 sys.org.group.* */
    public static final int ORG_TYPE_GROUP = 6;

    /**
     * 组织架构元素(element)接口允许写入的类型白名单：1~4。
     * 角色(5)/群组(6) 不参与树，禁止经 element 接口新增或编辑；
     * 树查询与组织管理列表另以 EXCLUDED_ORG_TYPES 排除。
     */
    public static final Set<Integer> ELEMENT_WRITABLE_ORG_TYPES = Set.of(ORG_TYPE_ORG, ORG_TYPE_DEPT, ORG_TYPE_USER, ORG_TYPE_POST);

    /** 不参与组织架构树的类型：角色(5)、群组(6)，树查询与组织管理根级列表须显式排除 */
    public static final Set<Integer> EXCLUDED_ORG_TYPES = Set.of(ORG_TYPE_ROLE, ORG_TYPE_GROUP);

    /** 排序字段白名单：前端传 createTime / updateTime / code */
    public static final String SORT_CREATE_TIME = "createTime";
    public static final String SORT_UPDATE_TIME = "updateTime";
    public static final String SORT_CODE = "code";

    /** 排序方向 */
    public static final String ORDER_ASC = "asc";
    public static final String ORDER_DESC = "desc";
}

package com.common.system.sys.org.request;

import lombok.Data;

/**
 * 组织架构元素查询条件
 *
 * @author Eric
 * @date 2026/8/10
 * @description 组织架构管理页右侧列表的查询条件：
 *              按点中的树节点（parentId，根节点时为空）查询其直接下级，
 *              并可叠加名称/编码模糊、组织架构类型、启用状态筛选。
 */
@Data
public class SysOrgElementQuery {

    /** 上级组织架构元素ID：为空表示查询根级（field_parent_id 为空） */
    private String parentId;

    /** 名称模糊匹配 */
    private String fieldName;

    /** 编码模糊匹配 */
    private String fieldCode;

    /** 组织架构类型：1=机构 2=部门 3=人员 4=岗位，为空表示全部 */
    private Integer orgType;

    /** 启用状态：为空表示全部 */
    private Boolean enable;
}

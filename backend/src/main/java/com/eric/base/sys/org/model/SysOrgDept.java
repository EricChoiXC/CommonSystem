package com.eric.base.sys.org.model;

import lombok.Data;

/**
 * 部门
 * @author Eric
 * @date 2026/8/5
 * @description 部门扩展字段，与 sys_org_dept 表一一对应；
 *              公共字段与层级字段继承自 SysOrgElement(sys_org_element 主表)。
 */
@Data
public class SysOrgDept extends SysOrgElement {

    /** 部门负责人用户ID */
    private String fieldLeaderId;

    /** 联系电话 */
    private String fieldPhone;

    /** 联系地址 */
    private String fieldAddress;
}

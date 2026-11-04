package com.common.system.sys.org.model;

import lombok.Data;

/**
 * 机构
 * @author Eric
 * @date 2026/8/5
 * @description 机构扩展字段，与 sys_org_org 表一一对应；
 *              公共字段与层级字段继承自 SysOrgElement(sys_org_element 主表)。
 */
@Data
public class SysOrgOrg extends SysOrgElement {

    /** 机构负责人用户ID */
    private String fieldLeaderId;

    /** 联系电话 */
    private String fieldPhone;

    /** 联系邮箱 */
    private String fieldEmail;

    /** 联系地址 */
    private String fieldAddress;
}

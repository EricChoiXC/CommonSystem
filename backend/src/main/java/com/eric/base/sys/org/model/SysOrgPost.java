package com.eric.base.sys.org.model;

import lombok.Data;

import java.util.List;

/**
 * 岗位
 * @author Eric
 * @date 2026/8/5
 * @description 岗位扩展字段，与 sys_org_post 表一一对应；
 *              公共字段与层级字段继承自 SysOrgElement(sys_org_element 主表)。
 */
@Data
public class SysOrgPost extends SysOrgElement{

    /** 岗位负责人用户ID */
    private String fieldLeaderId;

    /** 岗位用户 */
    private List<SysOrgUser> users;
}

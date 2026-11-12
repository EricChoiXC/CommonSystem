package com.eric.base.sys.org.request;

import lombok.Data;

/**
 * 角色查询条件
 *
 * @author Eric
 * @date 2026/8/10
 * @description 角色管理列表页的查询条件：角色名称/编码模糊、启用状态筛选。
 *              列表筛选项为：角色名、编码、是否启用（默认「是」）。
 */
@Data
public class SysOrgRoleQuery {

    /** 角色名称模糊匹配 */
    private String fieldName;

    /** 角色编码模糊匹配 */
    private String fieldCode;

    /** 启用状态：为空表示全部 */
    private Boolean enable;
}

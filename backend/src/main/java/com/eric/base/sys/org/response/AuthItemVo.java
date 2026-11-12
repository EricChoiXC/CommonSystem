package com.eric.base.sys.org.response;

import lombok.Data;

/**
 * 权限可选项（角色编辑页 Card 内的单个权限）
 *
 * @author Eric
 * @date 2026/8/10
 * @description fieldName 可能为空（roles.yml 中文缺失时库中不写名称），前端展示时回退 fieldCode。
 */
@Data
public class AuthItemVo {

    /** 权限 ID（sys_org_auth.field_id） */
    private String fieldId;

    /** 权限名称（可能为空） */
    private String fieldName;

    /** 权限编码（如 SYS_ORG_ROLE_ADMIN） */
    private String fieldCode;
}

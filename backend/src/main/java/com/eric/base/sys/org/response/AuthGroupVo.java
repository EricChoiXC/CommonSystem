package com.eric.base.sys.org.response;

import lombok.Data;

import java.util.List;

/**
 * 权限可选项分组（按 sys_org_auth.field_model 分组）
 *
 * @author Eric
 * @date 2026/8/10
 * @description 角色编辑页「权限」字段的可选项：按 field_model 分组、以 Card 方式展示。
 *              分组标题 modelName 优先读取 properties/ 下 application.yml 的 MODEL_NAME
 *              （如 sys/org/application.yml 的 MODEL_NAME=组织架构和权限模块），取不到时回退 field_model 原值。
 */
@Data
public class AuthGroupVo {

    /** sys_org_auth.field_model 值（application.yml 的 MODEL_KEY），如 SYS-ORG / COMMON */
    private String model;

    /** 分组显示名：优先取 application.yml 的 MODEL_NAME，取不到回退 model */
    private String modelName;

    /** 组内权限列表 */
    private List<AuthItemVo> auths;
}

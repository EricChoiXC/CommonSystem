package com.common.system.common.utils;

import com.common.system.sys.org.model.SysOrgRole;
import com.common.system.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.common.system.common.properties.CommonProperties.ROLE_ADMIN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 角色权限判定工具测试
 *
 * @author Eric
 * @date 2026/10/15
 * @description 固化「SYS_ADMIN 自动包含所有权限」的统一契约：
 *              系统管理员命中任一 {@code hasAnyRole} / {@code hasAnyRoleOrName} 判定即放行，
 *              且与具体要求的权限码无关；非超管用户仍按 field_code（或兼容 field_name）匹配。
 */
class RoleUtilTest {

    private static final String SYS_ADMIN = ROLE_ADMIN;
    private static final String OTHER_ROLE = "BIZ_MDM_ITEM";

    @Test
    void superAdminPassesAnyRoleCheck() {
        SysOrgUser user = userWithRoles(SYS_ADMIN);

        // 超管自动包含所有权限：任意权限码（含未登记、业务无关的编码）均应放行
        assertTrue(RoleUtil.hasAnyRole(user, OTHER_ROLE));
        assertTrue(RoleUtil.hasAnyRole(user, "BIZ_INVENTORY_ADMIN", OTHER_ROLE));
        assertTrue(RoleUtil.hasAnyRole(user, "SOME_UNDEFINED_PERMISSION"));
        assertTrue(RoleUtil.hasAnyRoleOrName(user, OTHER_ROLE));
        assertTrue(RoleUtil.isSuperAdmin(user));
    }

    @Test
    void superAdminPassesCheckEvenWithEmptyRequiredRoles() {
        // 超管不应因「未传要求权限」而被拒绝
        assertTrue(RoleUtil.hasAnyRole(userWithRoles(SYS_ADMIN)));
        assertTrue(RoleUtil.hasAnyRoleOrName(userWithRoles(SYS_ADMIN)));
    }

    @Test
    void normalUserMatchesByFieldCodeOnly() {
        SysOrgUser user = userWithRoles(OTHER_ROLE);

        assertTrue(RoleUtil.hasAnyRole(user, OTHER_ROLE));
        assertTrue(RoleUtil.hasAnyRole(user, "BIZ_MDM_ADMIN", OTHER_ROLE));
        assertFalse(RoleUtil.hasAnyRole(user, "BIZ_MDM_ADMIN"));
        assertFalse(RoleUtil.isSuperAdmin(user));
    }

    @Test
    void roleNameOnlyMatchesWhenNameCompatibilityEnabled() {
        SysOrgUser user = userWithNameOnly(OTHER_ROLE);

        // 默认仅匹配 field_code（AGENTS 4.3：权限码取 sys_org_role.field_code）
        assertFalse(RoleUtil.hasAnyRole(user, OTHER_ROLE));
        // 历史配置以角色名称承载权限码的模块显式启用名称兼容
        assertTrue(RoleUtil.hasAnyRoleOrName(user, OTHER_ROLE));
    }

    @Test
    void superAdminWinsOverMissingCodeAndName() {
        // 超管角色仅编码为 SYS_ADMIN、名称为中文时，仍应对任意权限码放行
        SysOrgUser user = userWithRoles(SYS_ADMIN);

        assertTrue(RoleUtil.hasAnyRoleOrName(user, "BIZ_PROJECT_ADMIN"));
        assertTrue(RoleUtil.hasAnyRole(user, "SYS_ORG_ROLE_ADMIN"));
    }

    @Test
    void nullUserOrRolesIsRejected() {
        assertFalse(RoleUtil.isSuperAdmin(null));
        assertFalse(RoleUtil.hasAnyRole(null, OTHER_ROLE));
        assertFalse(RoleUtil.hasAnyRoleOrName(null, OTHER_ROLE));

        SysOrgUser noRoles = new SysOrgUser();
        assertFalse(RoleUtil.isSuperAdmin(noRoles));
        assertFalse(RoleUtil.hasAnyRole(noRoles, SYS_ADMIN));
        assertFalse(RoleUtil.hasAnyRoleOrName(noRoles, SYS_ADMIN));
    }

    @Test
    void disabledUserStillJudgedByRoleForPureRoleCheck() {
        // RoleUtil 只做角色判定，账号启用与否由 BaseResolver.isEnabled 负责
        SysOrgUser user = userWithRoles(SYS_ADMIN);
        user.setFieldEnable(false);

        assertTrue(RoleUtil.isSuperAdmin(user));
    }

    private static SysOrgUser userWithRoles(String... codes) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId("u1");
        user.setFieldEnable(true);
        user.setRoles(List.of(codes).stream().map(RoleUtilTest::role).toList());
        return user;
    }

    private static SysOrgUser userWithNameOnly(String name) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId("u2");
        user.setFieldEnable(true);
        SysOrgRole role = new SysOrgRole();
        role.setFieldCode("SOME_INTERNAL_CODE");
        role.setFieldName(name);
        user.setRoles(List.of(role));
        return user;
    }

    private static SysOrgRole role(String code) {
        SysOrgRole role = new SysOrgRole();
        role.setFieldCode(code);
        return role;
    }
}

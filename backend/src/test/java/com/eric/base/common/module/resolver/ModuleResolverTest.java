package com.eric.base.common.module.resolver;

import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgRole;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static com.eric.base.common.module.properties.CommonModuleProperties.ACTION_ENABLE;
import static com.eric.base.common.module.properties.CommonModuleProperties.ACTION_LIST;
import static com.eric.base.common.module.properties.CommonModuleProperties.ROLE_MODULE_ADMIN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * 模块组管理接口鉴权器测试
 *
 * @author Eric
 * @date 2026/11/12
 * @description 固化 ModuleResolver 鉴权矩阵（任务清单 MOD3-BE-001 验收标准）：
 *              <ul>
 *              <li>list：登录即可访问，无角色要求——登录且启用的用户放行，未登录 / 停用拒绝；</li>
 *              <li>enable：仅 SYS_MODULE_ADMIN——模块组管理员与超管 SYS_ADMIN 放行，
 *                  普通用户 / 未登录 / 停用拒绝；</li>
 *              <li>SYS_ADMIN 由 BaseResolver/RoleUtil 自动放行，分支内不重复列举。</li>
 *              </ul>
 */
class ModuleResolverTest {

    private ModuleResolver resolver;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        resolver = new ModuleResolver();
        userUtil = mockStatic(UserUtil.class);
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    /** 打桩当前用户（null = 未登录） */
    private void login(String... roleCodes) {
        if (null == roleCodes) {
            userUtil.when(UserUtil::getCurrentUser).thenReturn(null);
            return;
        }
        SysOrgUser user = new SysOrgUser();
        user.setFieldId("1".repeat(32));
        user.setFieldEnable(true);
        user.setRoles(List.of(java.util.Arrays.stream(roleCodes)
                .map(code -> {
                    SysOrgRole role = new SysOrgRole();
                    role.setFieldCode(code);
                    role.setFieldName("角色-" + code);
                    return role;
                })
                .toArray(SysOrgRole[]::new)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);
    }

    @Test
    void listRequiresLoginOnly() {
        login((String[]) null);
        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_LIST)));

        // 普通用户（无模块组管理员角色）登录即可
        login("ORDINARY_ROLE");
        assertTrue(resolver.resolve(new AuthorizationContext(ACTION_LIST)));
    }

    @Test
    void enableRequiresModuleAdminRole() {
        login("ORDINARY_ROLE");
        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_ENABLE)));

        login(ROLE_MODULE_ADMIN);
        assertTrue(resolver.resolve(new AuthorizationContext(ACTION_ENABLE)));
    }

    @Test
    void anonymousIsRejectedForAllActions() {
        login((String[]) null);
        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_LIST)));
        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_ENABLE)));
    }

    @Test
    void superAdminPassesEnableWithoutExplicitListing() {
        // SYS_ADMIN 由 BaseResolver.hasAnyRole 统一放行（resolver 分支不重复列举）
        login("SYS_ADMIN");
        assertTrue(resolver.resolve(new AuthorizationContext(ACTION_ENABLE)));
        assertTrue(resolver.resolve(new AuthorizationContext(ACTION_LIST)));
    }

    @Test
    void nullContextIsRejected() {
        login(ROLE_MODULE_ADMIN);
        assertFalse(resolver.resolve((AuthorizationContext) null));
    }

    @Test
    void disabledUserIsRejected() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId("1".repeat(32));
        user.setFieldEnable(false);
        user.setRoles(List.of());
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);

        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_LIST)));
        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_ENABLE)));
    }
}

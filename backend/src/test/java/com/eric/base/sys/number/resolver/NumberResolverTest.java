package com.eric.base.sys.number.resolver;

import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgRole;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static com.eric.base.sys.number.properties.SysNumberConstants.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * 编号机制模块鉴权器测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 固化 NumberResolver 鉴权矩阵（任务 NB3-BE-001 / 方案 §4.2 判定表，
 *              MockedStatic&lt;UserUtil&gt; 造数对齐 I18nResolverTest 先例）：
 *              <ul>
 *              <li>登录即可：编号规则列表 / 预览渲染 / 正式取号——未登录拒绝、普通用户放行；</li>
 *              <li>ADMIN or EDITOR：编号规则维护五动作——普通用户拒绝、两码均放行；</li>
 *              <li>仅 SYS_NUMBER_ADMIN：取号明细与特权管理四动作——规则维护者不可越权；</li>
 *              <li>SYS_ADMIN 由 BaseResolver/RoleUtil 自动放行，resolver 分支不重复列举。</li>
 *              </ul>
 */
class NumberResolverTest {

    private NumberResolver resolver;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        resolver = new NumberResolver();
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
                    // sys_org_role.field_name 非空（NOT NULL）；hasAnyRoleOrName 的
                    // List.of().contains(null) 不接受 null，造数须贴近真实约束
                    role.setFieldName("角色-" + code);
                    return role;
                })
                .toArray(SysOrgRole[]::new)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);
    }

    @Test
    void listPreviewGenerateRequireLoginOnly() {
        List<String> actions = List.of(ACTION_RULE_LIST, ACTION_PREVIEW_RENDER, ACTION_GENERATE_ACQUIRE);

        // 未登录拒绝
        login((String[]) null);
        for (String action : actions) {
            assertFalse(resolver.resolve(new AuthorizationContext(action)), "未登录应被拒绝: " + action);
        }

        // 普通用户登录且启用即放行（列表不设权 / 预览无副作用 / 取号业务鉴权由调用方完成）
        login("ORDINARY_ROLE");
        for (String action : actions) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "登录用户应放行: " + action);
        }
    }

    @Test
    void ruleMaintenanceRequiresNumberAdminOrEditor() {
        List<String> actions = List.of(ACTION_RULE_LOAD, ACTION_RULE_SAVE, ACTION_RULE_UPDATE,
                ACTION_RULE_ENABLE, ACTION_RULE_DELETE);

        login("ORDINARY_ROLE");
        for (String action : actions) {
            assertFalse(resolver.resolve(new AuthorizationContext(action)), "普通用户应被拒绝: " + action);
        }

        login(ROLE_SYS_NUMBER_RULE_EDITOR);
        for (String action : actions) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "规则维护者应放行: " + action);
        }

        login(ROLE_SYS_NUMBER_ADMIN);
        for (String action : actions) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "编号管理员应放行: " + action);
        }
    }

    @Test
    void manageActionsRequireNumberAdminOnly() {
        List<String> actions = List.of(ACTION_MANAGE_RECORD_LIST, ACTION_MANAGE_RESET,
                ACTION_MANAGE_VOID, ACTION_MANAGE_REISSUE);

        login("ORDINARY_ROLE");
        for (String action : actions) {
            assertFalse(resolver.resolve(new AuthorizationContext(action)), "普通用户应被拒绝: " + action);
        }

        // 规则维护者不可越权特权管理（方案 §4.2：仅 ADMIN）
        login(ROLE_SYS_NUMBER_RULE_EDITOR);
        for (String action : actions) {
            assertFalse(resolver.resolve(new AuthorizationContext(action)), "规则维护者应被拒绝: " + action);
        }

        login(ROLE_SYS_NUMBER_ADMIN);
        for (String action : actions) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "编号管理员应放行: " + action);
        }
    }

    @Test
    void superAdminPassesRestrictedActions() {
        // SYS_ADMIN 由 BaseResolver.hasAnyRoleOrName 统一放行（resolver 分支不重复列举）
        login("SYS_ADMIN");
        assertTrue(resolver.resolve(new AuthorizationContext(ACTION_RULE_DELETE)));
        assertTrue(resolver.resolve(new AuthorizationContext(ACTION_MANAGE_RESET)));
    }

    @Test
    void nullContextIsRejected() {
        login(ROLE_SYS_NUMBER_ADMIN);
        assertFalse(resolver.resolve((AuthorizationContext) null));
    }

    @Test
    void disabledUserIsRejected() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId("1".repeat(32));
        user.setFieldEnable(false);
        user.setRoles(List.of());
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);

        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_RULE_LIST)));
        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_RULE_SAVE)));
        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_MANAGE_RESET)));
    }

    @Test
    void resolveStringBaselineIsLoginOnly() {
        // resolve(String) 为登录即可基线（无文档 ID 上下文），真实鉴权走 resolve(AuthorizationContext)
        login((String[]) null);
        assertFalse(resolver.resolve(ACTION_RULE_LIST));

        login("ORDINARY_ROLE");
        assertTrue(resolver.resolve(ACTION_RULE_LIST));
    }
}

package com.eric.base.sys.formula.resolver;

import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgRole;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;

import static com.eric.base.sys.formula.properties.SysFormulaConstants.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * 公式模块鉴权器测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 固化 FormulaResolver 鉴权矩阵（任务 F14-BE-007 / 方案 §5.1、
 *              API 文档 4.11.5，MockedStatic&lt;UserUtil&gt; 造数对齐 DatasourceResolverTest 先例）：
 *              <ul>
 *              <li>管理六动作（list / load / save / update / enable / delete）：仅
 *                  SYS_FORMULA_ADMIN——未登录拒绝、普通用户拒绝、管理员放行；</li>
 *              <li>组件四动作（options / functions / validate / simulate）：登录即可
 *                  （方案 A7——公式组件被各业务页面内嵌使用，消费面不按角色限制，
 *                  数据边界由取数函数委托承担）；</li>
 *              <li>SYS_ADMIN 由 BaseResolver/RoleUtil 自动放行，resolver 分支不重复列举。</li>
 *              </ul>
 */
class FormulaResolverTest {

    private FormulaResolver resolver;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        resolver = new FormulaResolver();
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
        user.setRoles(List.of(Arrays.stream(roleCodes)
                .map(code -> {
                    SysOrgRole role = new SysOrgRole();
                    role.setFieldCode(code);
                    // sys_org_role.field_name 非空（NOT NULL）；hasAnyRole 的
                    // List.of().contains(null) 不接受 null，造数须贴近真实约束
                    role.setFieldName("角色-" + code);
                    return role;
                })
                .toArray(SysOrgRole[]::new)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);
    }

    @Test
    void managementActionsRequireFormulaAdmin() {
        List<String> actions = List.of(ACTION_LIST, ACTION_LOAD, ACTION_SAVE,
                ACTION_UPDATE, ACTION_ENABLE, ACTION_DELETE);

        // 未登录拒绝
        login((String[]) null);
        for (String action : actions) {
            assertFalse(resolver.resolve(new AuthorizationContext(action)), "未登录应被拒绝: " + action);
        }

        // 普通用户拒绝
        login("ORDINARY_ROLE");
        for (String action : actions) {
            assertFalse(resolver.resolve(new AuthorizationContext(action)), "普通用户应被拒绝: " + action);
        }

        // 公式管理员放行
        login(ROLE_SYS_FORMULA_ADMIN);
        for (String action : actions) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "公式管理员应放行: " + action);
        }
    }

    @Test
    void designerActionsRequireLoggedInOnly() {
        // 组件四动作登录即可（方案 A7：v1.5/v1.7 配置者未必持有公式管理角色）
        List<String> actions = List.of(ACTION_OPTIONS, ACTION_FUNCTIONS, ACTION_VALIDATE, ACTION_SIMULATE);

        // 未登录拒绝
        login((String[]) null);
        for (String action : actions) {
            assertFalse(resolver.resolve(new AuthorizationContext(action)), "未登录应被拒绝: " + action);
        }

        // 登录且启用即放行（普通用户可消费公式组件）
        login("ORDINARY_ROLE");
        for (String action : actions) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "登录用户应放行: " + action);
        }

        // 公式管理员同样放行
        login(ROLE_SYS_FORMULA_ADMIN);
        for (String action : actions) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "公式管理员应放行: " + action);
        }
    }

    @Test
    void superAdminPassesRestrictedActions() {
        // SYS_ADMIN 由 BaseResolver.hasAnyRole 统一放行（resolver 分支不重复列举）
        login("SYS_ADMIN");
        for (String action : List.of(ACTION_LIST, ACTION_SAVE, ACTION_DELETE,
                ACTION_OPTIONS, ACTION_VALIDATE, ACTION_SIMULATE)) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "系统管理员应放行: " + action);
        }
    }

    @Test
    void nullContextIsRejected() {
        login(ROLE_SYS_FORMULA_ADMIN);
        assertFalse(resolver.resolve((AuthorizationContext) null));
    }

    @Test
    void disabledUserIsRejected() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId("1".repeat(32));
        user.setFieldEnable(false);
        user.setRoles(List.of());
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);

        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_SIMULATE)));
        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_SAVE)));
    }
}

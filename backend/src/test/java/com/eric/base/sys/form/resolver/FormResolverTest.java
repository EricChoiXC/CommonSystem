package com.eric.base.sys.form.resolver;

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

import static com.eric.base.sys.form.properties.SysFormConstants.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * 自定义表单模块鉴权器测试
 *
 * @author Eric
 * @date 2026/11/24
 * @description 固化 FormResolver 鉴权矩阵（任务 F15-BE-006，API 分册 §6.2、数据面归属定稿
 *              §4.8 / F15-DOC-005 R06，MockedStatic&lt;UserUtil&gt; 造数对齐
 *              FormulaResolverTest 先例）：
 *              <ul>
 *              <li>定义面九动作（list / save / load / update / enable / delete / publish /
 *                  versions / rollback）：仅 SYS_FORM_ADMIN——未登录拒绝、普通用户拒绝、
 *                  表单管理员放行；</li>
 *              <li>数据面六动作（schema / save / validate / load / list / delete）：登录即可
 *                  （R06 定稿——填写页被各业务页面 / 链接内嵌使用，填写人未必持有表单管理
 *                  角色；数据归属本人 / 管理员全量由 Service 层强制判定，resolver 不做）；</li>
 *              <li>未登记 action 一律拒绝（最小授权兜底，分册 §6.2）；</li>
 *              <li>SYS_ADMIN 由 BaseResolver / RoleUtil 自动放行，resolver 分支不重复列举。</li>
 *              </ul>
 */
class FormResolverTest {

    /** 定义面九动作全集（分册 §6.1 #1~#9） */
    private static final List<String> DEFINE_ACTIONS = List.of(
            ACTION_FORM_DEFINE_LIST, ACTION_FORM_DEFINE_SAVE, ACTION_FORM_DEFINE_LOAD,
            ACTION_FORM_DEFINE_UPDATE, ACTION_FORM_DEFINE_ENABLE, ACTION_FORM_DEFINE_DELETE,
            ACTION_FORM_DEFINE_PUBLISH, ACTION_FORM_DEFINE_VERSIONS, ACTION_FORM_DEFINE_ROLLBACK);

    /** 数据面六动作全集（分册 §6.1 #10~#15） */
    private static final List<String> DATA_ACTIONS = List.of(
            ACTION_FORM_DATA_SCHEMA, ACTION_FORM_DATA_SAVE, ACTION_FORM_DATA_VALIDATE,
            ACTION_FORM_DATA_LOAD, ACTION_FORM_DATA_LIST, ACTION_FORM_DATA_DELETE);

    private FormResolver resolver;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        resolver = new FormResolver();
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
    void defineActionsRequireFormAdmin() {
        // 未登录拒绝
        login((String[]) null);
        for (String action : DEFINE_ACTIONS) {
            assertFalse(resolver.resolve(new AuthorizationContext(action)), "未登录应被拒绝: " + action);
        }

        // 普通用户拒绝（登录即可口径不适用于定义面）
        login("ORDINARY_ROLE");
        for (String action : DEFINE_ACTIONS) {
            assertFalse(resolver.resolve(new AuthorizationContext(action)), "普通用户应被拒绝: " + action);
        }

        // 表单管理员放行
        login(ROLE_SYS_FORM_ADMIN);
        for (String action : DEFINE_ACTIONS) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "表单管理员应放行: " + action);
        }
    }

    @Test
    void dataActionsRequireLoggedInOnly() {
        // 未登录拒绝
        login((String[]) null);
        for (String action : DATA_ACTIONS) {
            assertFalse(resolver.resolve(new AuthorizationContext(action)), "未登录应被拒绝: " + action);
        }

        // 登录且启用即放行（R06 定稿：填写页被业务页面 / 链接内嵌使用，不按角色限制；
        // 数据归属本人 / 管理员全量由 Service 层强制判定，resolver 不做）
        login("ORDINARY_ROLE");
        for (String action : DATA_ACTIONS) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "登录用户应放行: " + action);
        }

        // 表单管理员同样放行（管理员全量语义由 Service 层承担）
        login(ROLE_SYS_FORM_ADMIN);
        for (String action : DATA_ACTIONS) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "表单管理员应放行: " + action);
        }
    }

    @Test
    void superAdminPassesAllActions() {
        // SYS_ADMIN 由 BaseResolver.hasAnyRole / isEnabled 统一放行（resolver 分支不重复列举）
        login("SYS_ADMIN");
        for (String action : List.of(ACTION_FORM_DEFINE_LIST, ACTION_FORM_DEFINE_SAVE,
                ACTION_FORM_DEFINE_DELETE, ACTION_FORM_DEFINE_PUBLISH, ACTION_FORM_DEFINE_ROLLBACK,
                ACTION_FORM_DATA_SCHEMA, ACTION_FORM_DATA_SAVE, ACTION_FORM_DATA_DELETE)) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "系统管理员应放行: " + action);
        }
    }

    @Test
    void unknownActionIsRejected() {
        // 未登记 action 一律拒绝（最小授权兜底，分册 §6.2——管理员 / 登录用户均拒绝）
        login(ROLE_SYS_FORM_ADMIN);
        assertFalse(resolver.resolve(new AuthorizationContext("sys.form.other.list")),
                "未登记 action 应被拒绝（管理员）");
        assertFalse(resolver.resolve(new AuthorizationContext("sys.org.element.delete")),
                "跨模块 action 应被拒绝（管理员）");

        login("ORDINARY_ROLE");
        assertFalse(resolver.resolve(new AuthorizationContext("sys.form.other.list")),
                "未登记 action 应被拒绝（登录用户）");
    }

    @Test
    void nullContextIsRejected() {
        login(ROLE_SYS_FORM_ADMIN);
        assertFalse(resolver.resolve((AuthorizationContext) null));
    }

    @Test
    void disabledUserIsRejected() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId("1".repeat(32));
        user.setFieldEnable(false);
        user.setRoles(List.of());
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);

        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_FORM_DATA_SCHEMA)));
        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_FORM_DEFINE_LIST)));
    }
}

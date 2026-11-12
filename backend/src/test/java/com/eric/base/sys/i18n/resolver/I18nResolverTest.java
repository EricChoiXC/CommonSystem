package com.eric.base.sys.i18n.resolver;

import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgRole;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static com.eric.base.sys.i18n.properties.SysI18nProperties.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * 多语言模块鉴权器测试
 *
 * @author Eric
 * @date 2026/11/5
 * @description 固化 I18nResolver 鉴权矩阵（任务清单 I18N3-BE-001 验收标准）：
 *              <ul>
 *              <li>免鉴权：语言列表、/locales 语言包——未登录也放行；</li>
 *              <li>登录即可：模块聚合列表、用户语言更新——登录且启用放行，未登录拒绝；</li>
 *              <li>仅 SYS_I18N_ADMIN：语言增删改/设默认、翻译维护 5 动作、部署——
 *                  多语言管理员与超管 SYS_ADMIN 放行，普通用户拒绝；</li>
 *              <li>SYS_ADMIN 由 BaseResolver/RoleUtil 自动放行，不重复列举。</li>
 *              </ul>
 */
class I18nResolverTest {

    private I18nResolver resolver;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        resolver = new I18nResolver();
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
                    // sys_org_role.field_name 非空（NOT NULL）；RoleUtil.hasAnyRoleOrName 的
                    // List.of().contains(null) 不接受 null，造数须贴近真实约束
                    role.setFieldName("角色-" + code);
                    return role;
                })
                .toArray(SysOrgRole[]::new)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);
    }

    @Test
    void languageListIsOpenToAnonymous() {
        // 免鉴权：登录页语言下拉需要，未登录也放行
        login((String[]) null);
        assertTrue(resolver.resolve(new AuthorizationContext(ACTION_LANGUAGE_LIST)));
        assertTrue(resolver.resolve(new AuthorizationContext(ACTION_LOCALES)));
    }

    @Test
    void modelListAndUserLanguageUpdateRequireLoginOnly() {
        login((String[]) null);
        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_MODEL_LIST)));
        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_USER_LANGUAGE_UPDATE)));

        login("ORDINARY_ROLE");
        assertTrue(resolver.resolve(new AuthorizationContext(ACTION_MODEL_LIST)));
        assertTrue(resolver.resolve(new AuthorizationContext(ACTION_USER_LANGUAGE_UPDATE)));
    }

    @Test
    void maintenanceActionsRequireI18nAdmin() {
        login("ORDINARY_ROLE");
        for (String action : List.of(ACTION_LANGUAGE_SAVE, ACTION_LANGUAGE_UPDATE, ACTION_LANGUAGE_DELETE,
                ACTION_LANGUAGE_SET_DEFAULT, ACTION_TRANSLATION_LIST, ACTION_TRANSLATION_UPDATE,
                ACTION_TRANSLATION_RESET, ACTION_TRANSLATION_EXPORT, ACTION_TRANSLATION_IMPORT,
                ACTION_DEPLOY_RUN)) {
            assertFalse(resolver.resolve(new AuthorizationContext(action)), "普通用户应被拒绝: " + action);
        }

        login(ROLE_SYS_I18N_ADMIN);
        for (String action : List.of(ACTION_LANGUAGE_SAVE, ACTION_LANGUAGE_UPDATE, ACTION_LANGUAGE_DELETE,
                ACTION_LANGUAGE_SET_DEFAULT, ACTION_TRANSLATION_LIST, ACTION_TRANSLATION_UPDATE,
                ACTION_TRANSLATION_RESET, ACTION_TRANSLATION_EXPORT, ACTION_TRANSLATION_IMPORT,
                ACTION_DEPLOY_RUN)) {
            assertTrue(resolver.resolve(new AuthorizationContext(action)), "多语言管理员应放行: " + action);
        }
    }

    @Test
    void superAdminPassesAllMaintenanceActions() {
        // SYS_ADMIN 由 BaseResolver.hasAnyRoleOrName 统一放行（resolver 分支不重复列举）
        login("SYS_ADMIN");
        assertTrue(resolver.resolve(new AuthorizationContext(ACTION_LANGUAGE_DELETE)));
        assertTrue(resolver.resolve(new AuthorizationContext(ACTION_DEPLOY_RUN)));
    }

    @Test
    void nullContextIsRejected() {
        login(ROLE_SYS_I18N_ADMIN);
        assertFalse(resolver.resolve((AuthorizationContext) null));
    }

    @Test
    void disabledUserIsRejected() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId("1".repeat(32));
        user.setFieldEnable(false);
        user.setRoles(List.of());
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);

        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_MODEL_LIST)));
        assertFalse(resolver.resolve(new AuthorizationContext(ACTION_LANGUAGE_SAVE)));
    }
}

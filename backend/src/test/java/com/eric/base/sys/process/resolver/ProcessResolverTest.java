package com.eric.base.sys.process.resolver;

import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgRole;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_VIEWER;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_MY_VIEWER;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_SETTING_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_TEMPLATE_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_TEMPLATE_VIEWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * 流程模块接口鉴权器测试（v1_7 分支扩展）
 *
 * @author Eric
 * @date 2026/11/20
 * @description 覆盖 {@link ProcessResolver} 的前缀/后缀判定口径与判定顺序约束
 *              （docs/设计文档/sys/process/流程v1_7权限与配置设计确认.md §3.2）：
 *              统一操作接口（operate）走 admin/viewer 口径、monitorList/adminOperate 仅管理员、
 *              statistic.admin 仅管理员、statistic.my 登录且启用即可（不叠加角色）、
 *              未登记 action 一律拒绝；SYS_ADMIN 由 BaseResolver 自动放行不在分支列举。
 */
class ProcessResolverTest {

    private static final String U1 = "12345678901234567890123456789001";

    private ProcessResolver resolver;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        resolver = new ProcessResolver();
        userUtil = mockStatic(UserUtil.class);
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    private void login(String... roleCodes) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(U1);
        user.setFieldName("张三");
        user.setFieldEnable(true);
        List<SysOrgRole> roles = new ArrayList<>();
        for (String code : roleCodes) {
            SysOrgRole role = new SysOrgRole();
            role.setFieldCode(code);
            roles.add(role);
        }
        user.setRoles(roles);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);
    }

    @Test
    @DisplayName("[resolver: 未登录/未启用 → 拒绝]")
    void rejectsWhenNotLoggedInOrDisabled() {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(null);
        assertFalse(resolver.resolve(new com.eric.base.common.resolver.AuthorizationContext(
                com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_OPERATE)));

        login(ROLE_SYS_PROCESS_EXAMPLE_VIEWER);
        SysOrgUser current = UserUtil.getCurrentUser();
        current.setFieldEnable(false);
        assertFalse(resolver.resolve(new com.eric.base.common.resolver.AuthorizationContext(
                com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_OPERATE)));
    }

    @Test
    @DisplayName("[resolver: operate 走 admin/viewer 口径] 统一操作接口允许实例管理员或查看者")
    void operateAllowedForExampleAdminOrViewer() {
        login(ROLE_SYS_PROCESS_EXAMPLE_VIEWER);
        assertTrue(resolver.resolve("sys.process.example.operate"));

        login(ROLE_SYS_PROCESS_EXAMPLE_ADMIN);
        assertTrue(resolver.resolve("sys.process.example.operate"));

        // 其他既有实例查看类动作口径不变
        login(ROLE_SYS_PROCESS_EXAMPLE_VIEWER);
        assertTrue(resolver.resolve("sys.process.example.list"));
        assertTrue(resolver.resolve("sys.process.example.load"));
        assertTrue(resolver.resolve("sys.process.example.panel"));
    }

    @Test
    @DisplayName("[resolver: operate 越权 → 拒绝] 无实例角色不得通过")
    void operateRejectedWithoutExampleRole() {
        login("SYS_OTHER_ROLE");
        assertFalse(resolver.resolve("sys.process.example.operate"));
    }

    @Test
    @DisplayName("[resolver: adminOperate/monitorList 仅实例管理员] 查看者被拒绝")
    void adminOperateAndMonitorListRequireExampleAdmin() {
        login(ROLE_SYS_PROCESS_EXAMPLE_ADMIN);
        assertTrue(resolver.resolve("sys.process.example.adminOperate"));
        assertTrue(resolver.resolve("sys.process.example.monitorList"));

        login(ROLE_SYS_PROCESS_EXAMPLE_VIEWER);
        assertFalse(resolver.resolve("sys.process.example.adminOperate"));
        assertFalse(resolver.resolve("sys.process.example.monitorList"));
    }

    @Test
    @DisplayName("[resolver: statistic 前缀] .admin 仅实例管理员；.my 登录且启用即可")
    void statisticBranches() {
        login(ROLE_SYS_PROCESS_EXAMPLE_ADMIN);
        assertTrue(resolver.resolve("sys.process.statistic.admin"));
        assertTrue(resolver.resolve("sys.process.statistic.my"));

        login(ROLE_SYS_PROCESS_EXAMPLE_VIEWER);
        assertFalse(resolver.resolve("sys.process.statistic.admin"));
        assertTrue(resolver.resolve("sys.process.statistic.my"), "我的统计登录即可，不叠加实例角色");

        login("SYS_OTHER_ROLE");
        assertTrue(resolver.resolve("sys.process.statistic.my"));
        assertFalse(resolver.resolve("sys.process.statistic.admin"));
        assertFalse(resolver.resolve("sys.process.statistic.other"), "未登记子动作按最小授权拒绝");
    }

    @Test
    @DisplayName("[resolver: 既有分支不变] setting/module 口径保持")
    void settingAndModuleBranchesUnchanged() {
        login(ROLE_SYS_PROCESS_SETTING_ADMIN);
        assertTrue(resolver.resolve("sys.process.setting.list"));

        login(ROLE_SYS_PROCESS_TEMPLATE_ADMIN);
        assertTrue(resolver.resolve("sys.process.module.save"));
        assertTrue(resolver.resolve("sys.process.module.load"));

        login(ROLE_SYS_PROCESS_TEMPLATE_VIEWER);
        assertTrue(resolver.resolve("sys.process.module.load"));
        assertFalse(resolver.resolve("sys.process.module.save"));
    }

    @Test
    @DisplayName("[resolver: 流程管理员入口统一放行] 任意流程 action 直接通过")
    void processAdminBypassesAllActions() {
        login(ROLE_SYS_PROCESS_ADMIN);
        assertTrue(resolver.resolve("sys.process.example.operate"));
        assertTrue(resolver.resolve("sys.process.statistic.my"));
        assertTrue(resolver.resolve("sys.process.setting.reset"));
    }

    @Test
    @DisplayName("[resolver: 未登记 action 一律拒绝]")
    void unknownActionRejected() {
        login(ROLE_SYS_PROCESS_EXAMPLE_VIEWER, ROLE_SYS_PROCESS_MY_VIEWER);
        assertFalse(resolver.resolve("sys.process.other.list"));
        assertFalse(resolver.resolve(""));
        assertFalse(resolver.resolve((String) null));
    }
}

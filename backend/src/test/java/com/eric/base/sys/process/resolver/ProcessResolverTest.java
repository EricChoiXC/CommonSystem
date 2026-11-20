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

import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_LIST;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_LOAD;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_MODELS;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_MONITOR_LIST;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_OPERATE;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_PANEL;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_ENABLE;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_LOAD;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_LIST;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_MODELS;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_SAVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_SET_DEFAULT;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_UPDATE;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_SETTING_LIST;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_SETTING_RESET;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_SETTING_SAVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_STATISTIC_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_STATISTIC_MY;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_VIEWER;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_MY_VIEWER;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_SETTING_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_TEMPLATE_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_TEMPLATE_VIEWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /* ==================== v1_7 权限矩阵（P17-QA-002：19 ACTION × 10 角色集逐格断言） ==================== */

    /** 19 项 ACTION 全集（setting 3 + module 7 + example 7 + statistic 2，见权限分册 §3.1） */
    private static final List<String> MATRIX_ACTIONS = List.of(
            ACTION_PROCESS_SETTING_LIST, ACTION_PROCESS_SETTING_SAVE, ACTION_PROCESS_SETTING_RESET,
            ACTION_PROCESS_MODULE_MODELS, ACTION_PROCESS_MODULE_LIST, ACTION_PROCESS_MODULE_LOAD,
            ACTION_PROCESS_MODULE_SAVE, ACTION_PROCESS_MODULE_UPDATE, ACTION_PROCESS_MODULE_ENABLE,
            ACTION_PROCESS_MODULE_SET_DEFAULT,
            ACTION_PROCESS_EXAMPLE_MODELS, ACTION_PROCESS_EXAMPLE_LIST, ACTION_PROCESS_EXAMPLE_LOAD,
            ACTION_PROCESS_EXAMPLE_PANEL, ACTION_PROCESS_EXAMPLE_OPERATE,
            ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE, ACTION_PROCESS_EXAMPLE_MONITOR_LIST,
            ACTION_PROCESS_STATISTIC_ADMIN, ACTION_PROCESS_STATISTIC_MY);

    /** 模板写操作后缀（仅 TEMPLATE_ADMIN） */
    private static final List<String> MODULE_WRITE_ACTIONS = List.of(
            ACTION_PROCESS_MODULE_SAVE, ACTION_PROCESS_MODULE_UPDATE,
            ACTION_PROCESS_MODULE_ENABLE, ACTION_PROCESS_MODULE_SET_DEFAULT);

    /** 实例特权动作后缀（仅 EXAMPLE_ADMIN） */
    private static final List<String> EXAMPLE_ADMIN_ONLY_ACTIONS = List.of(
            ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE, ACTION_PROCESS_EXAMPLE_MONITOR_LIST);

    /** 权限矩阵角色集 key（与断言表 expectedResolve 一一对应） */
    private static final List<String> MATRIX_ROLES = List.of(
            "SYS_ADMIN", "SYS_PROCESS_ADMIN", "SYS_PROCESS_TEMPLATE_ADMIN", "SYS_PROCESS_TEMPLATE_VIEWER",
            "SYS_PROCESS_EXAMPLE_ADMIN", "SYS_PROCESS_EXAMPLE_VIEWER", "SYS_PROCESS_SETTING_ADMIN",
            "SYS_PROCESS_MY_VIEWER", "PLAIN_USER", "DISABLED_USER");

    /**
     * 权限矩阵期望表（判定口径以权限分册 §3.1 / §3.2 为准）：
     * SYS_ADMIN 由 BaseResolver 自动放行（全 19 项）；SYS_PROCESS_ADMIN 为流程模块入口统一放行
     * （全 19 项，其中 statistic.my 经「登录即可」口径通过）；模板写操作仅 TEMPLATE_ADMIN、
     * 读操作 ADMIN/VIEWER；example 特权动作（adminOperate/monitorList）仅 EXAMPLE_ADMIN、
     * 其余（含 operate）ADMIN/VIEWER；statistic.admin 仅 EXAMPLE_ADMIN、statistic.my 登录即可；
     * setting 仅 SETTING_ADMIN；未启用用户全部拒绝。
     */
    private static boolean expectedResolve(String action, String roleKey) {
        return switch (roleKey) {
            case "SYS_ADMIN", "SYS_PROCESS_ADMIN" -> true;
            case "SYS_PROCESS_TEMPLATE_ADMIN" ->
                    action.startsWith("sys.process.module.") || action.endsWith(".my");
            case "SYS_PROCESS_TEMPLATE_VIEWER" ->
                    (action.startsWith("sys.process.module.") && !MODULE_WRITE_ACTIONS.contains(action))
                            || action.endsWith(".my");
            case "SYS_PROCESS_EXAMPLE_ADMIN" ->
                    action.startsWith("sys.process.example.") || action.endsWith(".my")
                            || action.endsWith(".admin");
            case "SYS_PROCESS_EXAMPLE_VIEWER" ->
                    (action.startsWith("sys.process.example.") && !EXAMPLE_ADMIN_ONLY_ACTIONS.contains(action))
                            || action.endsWith(".my");
            case "SYS_PROCESS_SETTING_ADMIN" ->
                    action.startsWith("sys.process.setting.") || action.endsWith(".my");
            case "SYS_PROCESS_MY_VIEWER", "PLAIN_USER" -> action.endsWith(".my");
            case "DISABLED_USER" -> false;
            default -> throw new IllegalArgumentException("unknown matrix role: " + roleKey);
        };
    }

    private void loginMatrixRole(String roleKey) {
        switch (roleKey) {
            case "SYS_ADMIN" -> login(com.eric.base.common.properties.CommonProperties.ROLE_ADMIN);
            case "SYS_PROCESS_ADMIN" -> login(ROLE_SYS_PROCESS_ADMIN);
            case "SYS_PROCESS_TEMPLATE_ADMIN" -> login(ROLE_SYS_PROCESS_TEMPLATE_ADMIN);
            case "SYS_PROCESS_TEMPLATE_VIEWER" -> login(ROLE_SYS_PROCESS_TEMPLATE_VIEWER);
            case "SYS_PROCESS_EXAMPLE_ADMIN" -> login(ROLE_SYS_PROCESS_EXAMPLE_ADMIN);
            case "SYS_PROCESS_EXAMPLE_VIEWER" -> login(ROLE_SYS_PROCESS_EXAMPLE_VIEWER);
            case "SYS_PROCESS_SETTING_ADMIN" -> login(ROLE_SYS_PROCESS_SETTING_ADMIN);
            case "SYS_PROCESS_MY_VIEWER" -> login(ROLE_SYS_PROCESS_MY_VIEWER);
            case "PLAIN_USER" -> login();
            case "DISABLED_USER" -> {
                login(ROLE_SYS_PROCESS_EXAMPLE_VIEWER, ROLE_SYS_PROCESS_MY_VIEWER);
                UserUtil.getCurrentUser().setFieldEnable(false);
            }
            default -> throw new IllegalArgumentException("unknown matrix role: " + roleKey);
        }
    }

    @Test
    @DisplayName("[resolver: v1_7 权限矩阵] 19 ACTION × 10 角色集逐格断言放行/拒绝（190 格）")
    void permissionMatrix() {
        for (String roleKey : MATRIX_ROLES) {
            loginMatrixRole(roleKey);
            for (String action : MATRIX_ACTIONS) {
                boolean expected = expectedResolve(action, roleKey);
                assertEquals(expected, resolver.resolve(action),
                        "权限矩阵格 " + roleKey + " × " + action);
            }
        }
    }

    @Test
    @DisplayName("[resolver: 判定顺序负向] 伪造特权后缀 / 未登记 statistic 子动作 / 未登记前缀一律拒绝")
    void negativeOrderingCases() {
        // 伪造 .monitorList / .adminOperate 后缀的复合动作：example 分支先判后缀 → 查看者被拒
        login(ROLE_SYS_PROCESS_EXAMPLE_VIEWER);
        assertFalse(resolver.resolve("sys.process.example.list.monitorList"),
                "伪造 monitorList 后缀的复合动作按特权后缀判定，查看者拒绝");
        assertFalse(resolver.resolve("sys.process.example.list.adminOperate"),
                "伪造 adminOperate 后缀的复合动作按特权后缀判定，查看者拒绝");
        // .operate 属「其余」后缀：查看者放行（口径回归）
        assertTrue(resolver.resolve("sys.process.example.list.operate"));

        // statistic 分支：未登记子动作按最小授权拒绝（即使管理员）；.my 登录即可不受影响
        login(ROLE_SYS_PROCESS_EXAMPLE_ADMIN);
        assertFalse(resolver.resolve("sys.process.statistic.other"), "未登记 statistic 子动作拒绝");
        assertFalse(resolver.resolve("sys.process.statistic.admin.extra"), "伪造复合子动作拒绝");
        assertFalse(resolver.resolve("sys.process.statistic.my.other"), "伪造 .my 复合子动作拒绝");
        assertTrue(resolver.resolve("sys.process.statistic.my"));

        // 未登记前缀：无流程管理员特权的角色一律拒绝（最小授权；SYS_PROCESS_ADMIN 在入口统一放行属既有设计，
        // 见 ProcessResolver 类注释与 processAdminBypassesAllActions 用例）
        login(ROLE_SYS_PROCESS_EXAMPLE_ADMIN);
        assertFalse(resolver.resolve("sys.process.other.list"));
        assertFalse(resolver.resolve("sys.process.other.example.operate"));
        // 无角色普通用户：仅 .my 放行
        login();
        assertFalse(resolver.resolve("sys.process.example.operate"));
        assertTrue(resolver.resolve("sys.process.statistic.my"));
    }
}

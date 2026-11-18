package com.eric.base.sys.formula.service.engine.functions;

import com.eric.base.sys.formula.service.engine.functions.system.SystemFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.system.SystemFunctionProvider.CurrentRoleFunction;
import com.eric.base.sys.org.model.SysOrgRole;
import com.eric.base.sys.org.utils.CurrentUserHolder;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SYSTEM 会话上下文函数测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖 F14-BE-003 验收标准：SYSTEM 函数全部无参（不信任前端传值 §6.3，
 *              带参调用被 Aviator arity 拒绝）、无会话（CurrentUserHolder 清空）确定性
 *              返回 null；角色编码集合拼接（去重保序、空白过滤）经 CurrentRoleFunction
 *              静态方法直接验证。带会话取值路径由 FormulaBuiltinFunctionsIntegrationTest
 *              经 MockedStatic&lt;UserUtil&gt; 覆盖。
 */
class FormulaSystemFunctionsTest {

    private AviatorEvaluatorInstance engine;

    @BeforeEach
    void setUp() {
        engine = FormulaFunctionsTestSupport.newEngine(new SystemFunctionProvider());
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    private static SysOrgRole role(String code) {
        SysOrgRole role = new SysOrgRole();
        role.setFieldCode(code);
        return role;
    }

    // ---------------------------------------------------------------- 无会话确定性 null

    @Test
    void noSessionReturnsNullDeterministically() {
        CurrentUserHolder.clear();
        assertNull(FormulaFunctionsTestSupport.eval(engine, "CURRENT_USER()"));
        assertNull(FormulaFunctionsTestSupport.eval(engine, "CURRENT_DEPT()"));
        assertNull(FormulaFunctionsTestSupport.eval(engine, "CURRENT_ROLE()"));
    }

    // ---------------------------------------------------------------- 无参形态（不信任前端传值）

    @Test
    void functionsRejectAnyArgument() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "CURRENT_USER('前端传值')"));
        assertTrue(String.valueOf(error.getMessage()).contains("Wrong number of args"),
                "带参调用应被 Aviator arity 校验拒绝（无参函数不信任前端传值）");
    }

    // ---------------------------------------------------------------- 角色编码集合拼接（静态方法直接验证）

    @Test
    void currentRoleJoinsDistinctCodesInOrder() {
        List<SysOrgRole> roles = List.of(role("SYS_FORMULA_ADMIN"), role("SYS_ORG_ADMIN"),
                role("SYS_FORMULA_ADMIN"));
        assertEquals("SYS_FORMULA_ADMIN,SYS_ORG_ADMIN", CurrentRoleFunction.joinRoleCodes(roles),
                "角色编码去重保序、逗号分隔");
    }

    @Test
    void currentRoleBlankCodesFiltered() {
        assertNull(CurrentRoleFunction.joinRoleCodes(List.of(role(" "), role(null))));
        assertEquals("A", CurrentRoleFunction.joinRoleCodes(List.of(role(" "), role("A"))));
    }
}

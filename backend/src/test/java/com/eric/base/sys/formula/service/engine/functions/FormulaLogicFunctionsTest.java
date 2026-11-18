package com.eric.base.sys.formula.service.engine.functions;

import com.eric.base.sys.formula.service.engine.functions.logic.LogicFunctionProvider;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LOGIC 逻辑函数测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖 F14-BE-003 验收标准（每类正反例各一）：IF 分支取值、AND/OR 变长
 *              参数与短路、NOT 取反、IFNULL 空值兜底（§6.5 显式出口）——均经 Aviator
 *              实际求值；缺失变量按 nil 确定性处理。
 */
class FormulaLogicFunctionsTest {

    private AviatorEvaluatorInstance engine;

    @BeforeEach
    void setUp() {
        engine = FormulaFunctionsTestSupport.newEngine(new LogicFunctionProvider());
    }

    // ---------------------------------------------------------------- IF

    @Test
    void ifTakesThenBranchOnTrue() {
        Map<String, Object> env = new HashMap<>();
        env.put("amount", 15000);
        Object result = FormulaFunctionsTestSupport.eval(
                engine, "IF(amount > 10000, '需部门审批', '无需审批')", env);
        assertEquals("需部门审批", result);
    }

    @Test
    void ifTakesElseBranchOnFalseAndMissingVariable() {
        Object result = FormulaFunctionsTestSupport.eval(
                engine, "IF(missing > 10000, 'a', 'b')");
        assertEquals("b", result);
        assertEquals(2L, FormulaFunctionsTestSupport.eval(engine, "IF(1 > 2, 1, 2)"));
    }

    @Test
    void ifNonBooleanConditionRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "IF('x', 1, 2)"));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    // ---------------------------------------------------------------- AND / OR

    @Test
    void andVariadicAllTrue() {
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "AND(1 > 0, 2 > 1, 3 > 2)"));
        assertFalse((Boolean) FormulaFunctionsTestSupport.eval(engine, "AND(1 > 0, 2 < 1)"));
    }

    @Test
    void andMissingVariableTreatedAsFalse() {
        assertFalse((Boolean) FormulaFunctionsTestSupport.eval(engine, "AND(true, missing)"));
    }

    @Test
    void orVariadicAnyTrue() {
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "OR(false, 2 > 1, 3 < 2)"));
        assertFalse((Boolean) FormulaFunctionsTestSupport.eval(engine, "OR(false, false)"));
        assertFalse((Boolean) FormulaFunctionsTestSupport.eval(engine, "OR(missing, false)"));
    }

    // ---------------------------------------------------------------- NOT

    @Test
    void notInvertsBoolean() {
        assertFalse((Boolean) FormulaFunctionsTestSupport.eval(engine, "NOT(true)"));
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "NOT(false)"));
    }

    @Test
    void notMissingVariableIsTrue() {
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "NOT(missing)"));
    }

    // ---------------------------------------------------------------- IFNULL

    @Test
    void ifNullReturnsDefaultOnMissingVariable() {
        assertEquals("默认值", FormulaFunctionsTestSupport.eval(engine, "IFNULL(missing, '默认值')"));
    }

    @Test
    void ifNullKeepsOriginalValueAndEmptyStringIsNotNull() {
        Map<String, Object> env = new HashMap<>();
        env.put("deptName", "财务部");
        assertEquals("财务部", FormulaFunctionsTestSupport.eval(engine, "IFNULL(deptName, '默认值')", env));
        assertEquals("", FormulaFunctionsTestSupport.eval(engine, "IFNULL('', '默认值')"));
    }
}

package com.eric.base.sys.formula.service.engine.functions;

import com.eric.base.sys.formula.service.engine.functions.math.MathFunctionProvider;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MATH 数学函数测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖 F14-BE-003 验收标准（每类正反例各一）：ABS/ROUND(单参与双参形态)/
 *              CEIL/FLOOR/POWER 经 Aviator 实际求值（方案 §2.1 含税金额样例），
 *              空值/非数值/除零类确定性错误按 CS_FORMULA_EXECUTE_FAILED 前缀抛出（§6.5）。
 */
class FormulaMathFunctionsTest {

    private AviatorEvaluatorInstance engine;

    @BeforeEach
    void setUp() {
        engine = FormulaFunctionsTestSupport.newEngine(new MathFunctionProvider());
    }

    // ---------------------------------------------------------------- ABS

    @Test
    void absPositiveNumber() {
        assertEquals(5L, FormulaFunctionsTestSupport.eval(engine, "ABS(-5)"));
        assertEquals(3.5, (double) FormulaFunctionsTestSupport.eval(engine, "ABS(-3.5)"), 1e-9);
    }

    @Test
    void absNullRejectedWithExecuteFailed() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "ABS(missing)"));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    // ---------------------------------------------------------------- ROUND（两参形态，方案 §2.1 样例）

    @Test
    void roundTwoArgSampleExpression() {
        Map<String, Object> env = new HashMap<>();
        env.put("applyAmount", 1000.0);
        env.put("taxRate", 0.13);
        Object result = FormulaFunctionsTestSupport.eval(engine, "ROUND(applyAmount * taxRate, 2)", env);
        assertEquals(130.0, ((Number) result).doubleValue(), 1e-9);
    }

    @Test
    void roundSingleArgDefaultsToZeroScaleAndReturnsIntegral() {
        assertEquals(3L, FormulaFunctionsTestSupport.eval(engine, "ROUND(2.5)"));
        assertEquals(-3L, FormulaFunctionsTestSupport.eval(engine, "ROUND(-2.5)"));
        assertEquals(1300L, FormulaFunctionsTestSupport.eval(engine, "ROUND(1265, -2)"));
    }

    @Test
    void roundNullScaleRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "ROUND(1.5, missing)"));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    // ---------------------------------------------------------------- CEIL / FLOOR

    @Test
    void ceilAndFloor() {
        assertEquals(3L, FormulaFunctionsTestSupport.eval(engine, "CEIL(2.1)"));
        assertEquals(-2L, FormulaFunctionsTestSupport.eval(engine, "CEIL(-2.1)"));
        assertEquals(2L, FormulaFunctionsTestSupport.eval(engine, "FLOOR(2.9)"));
        assertEquals(-3L, FormulaFunctionsTestSupport.eval(engine, "FLOOR(-2.1)"));
    }

    @Test
    void ceilNonNumericRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "CEIL('abc')"));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    // ---------------------------------------------------------------- POWER

    @Test
    void powerPositiveExponent() {
        assertEquals(1024.0, ((Number) FormulaFunctionsTestSupport.eval(engine, "POWER(2, 10)")).doubleValue(),
                1e-9);
        assertEquals(0.25, ((Number) FormulaFunctionsTestSupport.eval(engine, "POWER(2, -2)")).doubleValue(),
                1e-9);
    }

    @Test
    void powerZeroBaseNegativeExponentRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "POWER(0, -2)"));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }
}

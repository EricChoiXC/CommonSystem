package com.eric.base.sys.formula.service.engine.functions;

import com.eric.base.sys.formula.service.engine.functions.compare.CompareFunctionProvider;
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
 * COMPARE 比较函数测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖 F14-BE-003 验收标准（每类正反例各一）：EQ/NE 等值口径
 *              （数值跨类型、文本与数值不跨型、null 语义），GT/GTE/LT/LTE 排序口径
 *              （数值跨类型、文本字典序、空值/混合类型确定性拒绝）。
 */
class FormulaCompareFunctionsTest {

    private AviatorEvaluatorInstance engine;

    @BeforeEach
    void setUp() {
        engine = FormulaFunctionsTestSupport.newEngine(new CompareFunctionProvider());
    }

    // ---------------------------------------------------------------- EQ / NE

    @Test
    void eqNumericCrossType() {
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "EQ(1, 1.0)"));
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "EQ(100, 100.00)"));
        assertFalse((Boolean) FormulaFunctionsTestSupport.eval(engine, "EQ(1, 1.5)"));
    }

    @Test
    void eqStringAndNullSemantics() {
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "EQ('a', 'a')"));
        assertFalse((Boolean) FormulaFunctionsTestSupport.eval(engine, "EQ('100', 100)"),
                "文本与数值不跨型匹配（确定性语义）");
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "EQ(missing, missing)"));
        assertFalse((Boolean) FormulaFunctionsTestSupport.eval(engine, "EQ(missing, 1)"));
    }

    @Test
    void neInverseOfEq() {
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "NE('a', 'b')"));
        assertFalse((Boolean) FormulaFunctionsTestSupport.eval(engine, "NE(2, 2.0)"));
    }

    // ---------------------------------------------------------------- GT / GTE / LT / LTE

    @Test
    void gtNumericCrossType() {
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "GT(2, 1.9)"));
        assertFalse((Boolean) FormulaFunctionsTestSupport.eval(engine, "GT(1.9, 2)"));
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "GTE(2, 2.0)"));
    }

    @Test
    void gtStringLexicographic() {
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "GT('b', 'a')"));
        assertFalse((Boolean) FormulaFunctionsTestSupport.eval(engine, "GT('a', 'b')"));
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "LTE('a', 'a')"));
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "LT('a', 'b')"));
    }

    @Test
    void gtNullOrMixedTypeRejected() {
        IllegalArgumentException nullError = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "GT(missing, 1)"));
        assertTrue(nullError.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
        IllegalArgumentException mixedError = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "GT(1, 'a')"));
        assertTrue(mixedError.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    // ---------------------------------------------------------------- 三元等价组合（IF 语义归属 LOGIC，比较面单独验证）

    @Test
    void gtBoundarySemantics() {
        Map<String, Object> env = new HashMap<>();
        env.put("amount", 10000.0);
        assertFalse((Boolean) FormulaFunctionsTestSupport.eval(engine, "GT(amount, 10000)", env),
                "等于不触发 GT");
        assertTrue((Boolean) FormulaFunctionsTestSupport.eval(engine, "GTE(amount, 10000)", env),
                "等于触发 GTE");
    }
}

package com.eric.base.sys.formula.service.engine.functions;

import com.eric.base.sys.formula.service.engine.functions.collection.CollectionFunctionProvider;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COLLECTION 集合函数测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖 F14-BE-003 验收标准（每类正反例各一）：SUM/AVG 数值宽容转换与
 *              空集合确定性返回、MAX/MIN 数值口径与空集合 null、COUNT 多形态、
 *              COUNTIF 等值比较口径（数值跨类型）、SUMIF 同序配对（长度不一致确定性
 *              拒绝、非数值元素拒绝）；消费 F14-BE-004 ${list.field} 投影产物形态
 *              （普通数组变量）。
 */
class FormulaCollectionFunctionsTest {

    private AviatorEvaluatorInstance engine;

    @BeforeEach
    void setUp() {
        engine = FormulaFunctionsTestSupport.newEngine(new CollectionFunctionProvider());
    }

    private Map<String, Object> env(Object... pairs) {
        Map<String, Object> env = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            env.put((String) pairs[i], pairs[i + 1]);
        }
        return env;
    }

    // ---------------------------------------------------------------- SUM / AVG

    @Test
    void sumNumbersAndNumericText() {
        assertEquals(400L, FormulaFunctionsTestSupport.eval(
                engine, "SUM(seq)", env("seq", List.of(100, 200.5, 99.5))));
        assertEquals(3.0, ((Number) FormulaFunctionsTestSupport.eval(
                engine, "SUM(seq)", env("seq", List.of("1", "2")))).doubleValue(), 1e-9);
    }

    @Test
    void sumEmptyIsZeroAndNonNumericElementRejected() {
        assertEquals(0L, FormulaFunctionsTestSupport.eval(engine, "SUM(seq)", env("seq", List.of())),
                "空集合确定性返回 0");
        assertEquals(0L, FormulaFunctionsTestSupport.eval(engine, "SUM(missingVar)", env()),
                "缺失变量（nil）按空集合确定性处理，口径同 COUNT");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "SUM(seq)", env("seq", List.of(1, "abc"))));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    @Test
    void avgAveragesAndEmptyIsZero() {
        assertEquals(133.3333333333, ((Number) FormulaFunctionsTestSupport.eval(
                engine, "AVG(seq)", env("seq", List.of(100, 200, 100)))).doubleValue(), 1e-9);
        assertEquals(0.0, ((Number) FormulaFunctionsTestSupport.eval(engine, "AVG(seq)", env("seq", List.of())))
                .doubleValue(), 1e-9);
    }

    // ---------------------------------------------------------------- MAX / MIN

    @Test
    void maxAndMin() {
        assertEquals(300, ((Number) FormulaFunctionsTestSupport.eval(
                engine, "MAX(seq)", env("seq", List.of(100, 300, 200)))).longValue());
        assertEquals(100, ((Number) FormulaFunctionsTestSupport.eval(
                engine, "MIN(seq)", env("seq", List.of(100, 300, 200)))).longValue());
    }

    @Test
    void maxEmptyReturnsNull() {
        assertNull(FormulaFunctionsTestSupport.eval(engine, "MAX(seq)", env("seq", List.of())));
    }

    // ---------------------------------------------------------------- COUNT

    @Test
    void countListMapString() {
        assertEquals(3L, FormulaFunctionsTestSupport.eval(engine, "COUNT(seq)", env("seq", List.of(1, 2, 3))));
        assertEquals(2L, FormulaFunctionsTestSupport.eval(engine, "COUNT(seq)",
                env("seq", Map.of("a", 1, "b", 2))));
        assertEquals(5L, FormulaFunctionsTestSupport.eval(engine, "COUNT(seq)", env("seq", "hello")));
        assertEquals(0L, FormulaFunctionsTestSupport.eval(engine, "COUNT(missing)"));
    }

    // ---------------------------------------------------------------- COUNTIF

    @Test
    void countIfEqualitySemantics() {
        assertEquals(2L, FormulaFunctionsTestSupport.eval(engine, "COUNTIF(seq, 'A')",
                env("seq", List.of("A", "B", "A"))));
        // 数值跨类型等值（EQ 口径：1 与 1.0 相等）
        assertEquals(3L, FormulaFunctionsTestSupport.eval(engine, "COUNTIF(seq, 1)",
                env("seq", List.of(1, 1.0, 1, 2))));
        assertEquals(0L, FormulaFunctionsTestSupport.eval(engine, "COUNTIF(seq, missing)",
                env("seq", List.of(1, 2))));
    }

    // ---------------------------------------------------------------- SUMIF

    @Test
    void sumIfPairsByPosition() {
        // 方案消费形态：${list.fieldType}/${list.fieldAmount} 投影数组逐位配对
        assertEquals(40.0, ((Number) FormulaFunctionsTestSupport.eval(
                engine, "SUMIF(types, 'A', amounts)",
                env("types", List.of("A", "B", "A"), "amounts", List.of(10, 20, 30)))).doubleValue(), 1e-9);
    }

    @Test
    void sumIfLengthMismatchRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "SUMIF(types, 'A', amounts)",
                        env("types", List.of("A", "B"), "amounts", List.of(10))));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    @Test
    void sumIfNonNumericSumElementRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "SUMIF(types, 'A', amounts)",
                        env("types", List.of("A"), "amounts", List.of(true))));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }
}

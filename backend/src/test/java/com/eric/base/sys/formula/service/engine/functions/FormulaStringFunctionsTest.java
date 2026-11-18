package com.eric.base.sys.formula.service.engine.functions;

import com.eric.base.sys.formula.service.engine.functions.string.StringFunctionProvider;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * STRING 字符串函数测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖 F14-BE-003 验收标准（每类正反例各一）：CONCAT 变长拼接与 null
 *              文本化、SUBSTRING 两参与三参形态及越界确定性截断、LENGTH/TRIM/REPLACE
 *              确定性语义（REPLACE 空匹配原样返回）。
 */
class FormulaStringFunctionsTest {

    private AviatorEvaluatorInstance engine;

    @BeforeEach
    void setUp() {
        engine = FormulaFunctionsTestSupport.newEngine(new StringFunctionProvider());
    }

    // ---------------------------------------------------------------- CONCAT

    @Test
    void concatVariadic() {
        Map<String, Object> env = new HashMap<>();
        env.put("deptName", "财务部");
        env.put("userName", "张三");
        assertEquals("财务部-张三",
                FormulaFunctionsTestSupport.eval(engine, "CONCAT(deptName, '-', userName)", env));
    }

    @Test
    void concatNullToEmptyString() {
        assertEquals("ab", FormulaFunctionsTestSupport.eval(engine, "CONCAT('a', missing, 'b')"));
    }

    // ---------------------------------------------------------------- SUBSTRING

    @Test
    void substringTwoArgAndThreeArg() {
        Map<String, Object> env = new HashMap<>();
        env.put("text", "abcdef");
        assertEquals("cdef", FormulaFunctionsTestSupport.eval(engine, "SUBSTRING(text, 2)", env));
        assertEquals("bc", FormulaFunctionsTestSupport.eval(engine, "SUBSTRING(text, 1, 3)", env));
    }

    @Test
    void substringOutOfBoundsTruncatedDeterministically() {
        assertEquals("", FormulaFunctionsTestSupport.eval(engine, "SUBSTRING('abc', 10)"));
        assertEquals("c", FormulaFunctionsTestSupport.eval(engine, "SUBSTRING('abc', 2, 99)"));
        assertEquals("ab", FormulaFunctionsTestSupport.eval(engine, "SUBSTRING('abc', -1, 2)"));
    }

    @Test
    void substringNonNumericBeginRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "SUBSTRING('abc', 'x')"));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    // ---------------------------------------------------------------- LENGTH / TRIM

    @Test
    void lengthCountsCharacters() {
        Map<String, Object> env = new HashMap<>();
        env.put("text", "财务部");
        assertEquals(3L, FormulaFunctionsTestSupport.eval(engine, "LENGTH(text)", env));
        assertEquals(0L, FormulaFunctionsTestSupport.eval(engine, "LENGTH(missing)"));
    }

    @Test
    void trimRemovesSurroundingWhitespace() {
        assertEquals("abc", FormulaFunctionsTestSupport.eval(engine, "TRIM('  abc  ')"));
        assertEquals("", FormulaFunctionsTestSupport.eval(engine, "TRIM(missing)"));
    }

    // ---------------------------------------------------------------- REPLACE

    @Test
    void replaceAllOccurrences() {
        assertEquals("a-b-c",
                FormulaFunctionsTestSupport.eval(engine, "REPLACE('a,b,c', ',', '-')"));
    }

    @Test
    void replaceEmptyOldTextReturnsOriginal() {
        assertEquals("abc", FormulaFunctionsTestSupport.eval(engine, "REPLACE('abc', '', 'x')"));
    }
}

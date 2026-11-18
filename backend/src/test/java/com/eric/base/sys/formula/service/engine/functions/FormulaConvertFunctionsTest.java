package com.eric.base.sys.formula.service.engine.functions;

import com.eric.base.sys.formula.service.engine.functions.convert.ConvertFunctionProvider;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CONVERT 类型转换函数测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖 F14-BE-003 验收标准（每类正反例各一）：TO_NUMBER 整数/小数/文本
 *              宽容与失败确定性拒绝（任务口径：报 CS_FORMULA_EXECUTE_FAILED）、
 *              TO_STRING null → 空串与 Date 文本化、TO_DATE 两种文本格式与失败拒绝。
 */
class FormulaConvertFunctionsTest {

    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private AviatorEvaluatorInstance engine;

    @BeforeEach
    void setUp() {
        engine = FormulaFunctionsTestSupport.newEngine(new ConvertFunctionProvider());
    }

    // ---------------------------------------------------------------- TO_NUMBER

    @Test
    void toNumberIntegralAndDecimalText() {
        assertEquals(42L, FormulaFunctionsTestSupport.eval(engine, "TO_NUMBER('42')"));
        assertEquals(42.5, (double) FormulaFunctionsTestSupport.eval(engine, "TO_NUMBER('42.5')"), 1e-9);
        assertEquals(7L, FormulaFunctionsTestSupport.eval(engine, "TO_NUMBER(7.0)"),
                "Number 原样归一，整型形态保留");
    }

    @Test
    void toNumberFailureRejectedWithExecuteFailed() {
        IllegalArgumentException nullError = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "TO_NUMBER(missing)"));
        assertTrue(nullError.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
        IllegalArgumentException textError = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "TO_NUMBER('abc')"));
        assertTrue(textError.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
        IllegalArgumentException boolError = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "TO_NUMBER(true)"));
        assertTrue(boolError.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    // ---------------------------------------------------------------- TO_STRING

    @Test
    void toStringNumberBooleanAndNull() {
        assertEquals("42.5", FormulaFunctionsTestSupport.eval(engine, "TO_STRING(42.5)"));
        assertEquals("true", FormulaFunctionsTestSupport.eval(engine, "TO_STRING(true)"));
        assertEquals("", FormulaFunctionsTestSupport.eval(engine, "TO_STRING(missing)"),
                "null → 空串（文本模板确定性兜底）");
    }

    @Test
    void toStringDateFormatting() {
        Date date = new GregorianCalendar(2026, Calendar.NOVEMBER, 18, 8, 30, 0).getTime();
        Map<String, Object> env = new HashMap<>();
        env.put("d", date);
        assertEquals("2026-11-18 08:30:00",
                FormulaFunctionsTestSupport.eval(engine, "TO_STRING(d)", env));
    }

    // ---------------------------------------------------------------- TO_DATE

    @Test
    void toDateTwoTextFormatsAndDatePassthrough() {
        Date dateOnly = assertInstanceOf(Date.class,
                FormulaFunctionsTestSupport.eval(engine, "TO_DATE('2026-11-18')"));
        assertEquals("2026-11-18 00:00:00", FORMAT.format(dateOnly));
        Date dateTime = assertInstanceOf(Date.class,
                FormulaFunctionsTestSupport.eval(engine, "TO_DATE('2026-11-18 08:30:00')"));
        assertEquals("2026-11-18 08:30:00", FORMAT.format(dateTime));
        Date base = new GregorianCalendar(2026, Calendar.NOVEMBER, 18).getTime();
        Map<String, Object> env = new HashMap<>();
        env.put("d", base);
        assertEquals(base, FormulaFunctionsTestSupport.eval(engine, "TO_DATE(d)", env),
                "Date 入参原样归一返回");
    }

    @Test
    void toDateFailureRejectedWithExecuteFailed() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "TO_DATE('2026/11/18')"));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }
}

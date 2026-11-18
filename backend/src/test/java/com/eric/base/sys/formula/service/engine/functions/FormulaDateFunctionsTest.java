package com.eric.base.sys.formula.service.engine.functions;

import com.eric.base.sys.formula.service.engine.functions.date.DateFunctionProvider;
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
 * DATE 日期函数测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖 F14-BE-003 验收标准（每类正反例各一）：NOW/TODAY 服务器时间语义、
 *              DATEADD 三种 arity（days / days+months / days+months+years）、DATEDIFF
 *              自然日差（a-b 含负数）、YEAR/MONTH 取值；日期宽容转换正例（文本两种格式、
 *              Date、时间戳）与不可转换确定性拒绝（§6.5）。
 */
class FormulaDateFunctionsTest {

    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private AviatorEvaluatorInstance engine;

    @BeforeEach
    void setUp() {
        engine = FormulaFunctionsTestSupport.newEngine(new DateFunctionProvider());
    }

    // ---------------------------------------------------------------- NOW / TODAY

    @Test
    void nowReturnsServerTime() {
        Date before = new Date();
        Object result = FormulaFunctionsTestSupport.eval(engine, "NOW()");
        Date after = new Date();
        Date now = assertInstanceOf(Date.class, result);
        assertTrue(!now.before(before) && !now.after(after));
    }

    @Test
    void todayIsMidnightOfCurrentDay() {
        Date today = assertInstanceOf(Date.class, FormulaFunctionsTestSupport.eval(engine, "TODAY()"));
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, calendar.get(Calendar.MINUTE));
        assertEquals(0, calendar.get(Calendar.SECOND));
    }

    // ---------------------------------------------------------------- DATEADD

    @Test
    void dateAddDaysOnly() {
        Date base = new GregorianCalendar(2026, Calendar.JANUARY, 31).getTime();
        Map<String, Object> env = new HashMap<>();
        env.put("d", base);
        Date result = assertInstanceOf(Date.class,
                FormulaFunctionsTestSupport.eval(engine, "DATEADD(d, 1)", env));
        assertEquals("2026-02-01 00:00:00", FORMAT.format(result));
    }

    @Test
    void dateAddMonthsAndYearsWithMonthEndOverflow() {
        Date base = new GregorianCalendar(2024, Calendar.FEBRUARY, 29).getTime();
        Map<String, Object> env = new HashMap<>();
        env.put("d", base);
        // +1 年落 2/28（Calendar 月末溢出，确定性）；再 +1 月落 3/28
        Date result = assertInstanceOf(Date.class,
                FormulaFunctionsTestSupport.eval(engine, "DATEADD(d, 0, 1, 1)", env));
        assertEquals("2025-03-28 00:00:00", FORMAT.format(result));
        // 负数偏移回退
        Date back = assertInstanceOf(Date.class,
                FormulaFunctionsTestSupport.eval(engine, "DATEADD(d, -1)", env));
        assertEquals("2024-02-28 00:00:00", FORMAT.format(back));
        // days + months 两参形态（2/29 + 1 月 → 3/29）
        Date twoArg = assertInstanceOf(Date.class,
                FormulaFunctionsTestSupport.eval(engine, "DATEADD(d, 0, 1)", env));
        assertEquals("2024-03-29 00:00:00", FORMAT.format(twoArg));
    }

    @Test
    void dateAddUnconvertibleDateRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "DATEADD('not-a-date', 1)"));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    // ---------------------------------------------------------------- DATEDIFF

    @Test
    void dateDiffNaturalDaysBothDirections() {
        assertEquals(5L, FormulaFunctionsTestSupport.eval(
                engine, "DATEDIFF('2026-11-18', '2026-11-13')"));
        assertEquals(-5L, FormulaFunctionsTestSupport.eval(
                engine, "DATEDIFF('2026-11-13', '2026-11-18')"));
        // 方案 §2.1 样例语义：DATEDIFF(TODAY(), startDate) > 3
        assertEquals(1L, FormulaFunctionsTestSupport.eval(
                engine, "DATEDIFF('2026-03-01 23:00:00', '2026-02-28 01:00:00')"),
                "自然日差截断时分秒（跨月）");
    }

    @Test
    void dateDiffNullRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "DATEDIFF(missing, '2026-01-01')"));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    // ---------------------------------------------------------------- YEAR / MONTH

    @Test
    void yearAndMonthExtract() {
        assertEquals(2026L, FormulaFunctionsTestSupport.eval(engine, "YEAR('2026-11-18')"));
        assertEquals(11L, FormulaFunctionsTestSupport.eval(engine, "MONTH('2026-11-18')"));
        assertEquals(1L, FormulaFunctionsTestSupport.eval(engine, "MONTH('2026-01-18 08:00:00')"));
    }

    @Test
    void yearNonDateStringRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "YEAR(true)"));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    // ---------------------------------------------------------------- 宽容转换

    @Test
    void lenientConversionAcceptsDatetimeTextAndTimestamp() {
        Date fromDateTime = assertInstanceOf(Date.class,
                FormulaFunctionsTestSupport.eval(engine, "DATEADD('2026-11-18 10:30:00', 0)"));
        assertEquals("2026-11-18 10:30:00", FORMAT.format(fromDateTime));
        Date fromTimestamp = assertInstanceOf(Date.class,
                FormulaFunctionsTestSupport.eval(engine, "DATEADD(0, 0)"));
        assertEquals(0L, fromTimestamp.getTime());
    }
}

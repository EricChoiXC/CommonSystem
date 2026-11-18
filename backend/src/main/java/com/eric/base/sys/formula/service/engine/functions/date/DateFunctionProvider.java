package com.eric.base.sys.formula.service.engine.functions.date;

import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.service.engine.functions.FormulaFunctions;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorLong;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * DATE 日期函数 Provider（方案 §2.2 首批：NOW/TODAY/DATEADD/DATEDIFF/YEAR/MONTH）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 内置函数注册单元之一。日期入参宽容转换（FormulaFunctions#toDate）：
 *              Date / LocalDate / LocalDateTime / 时间戳数值 / 文本
 *              （yyyy-MM-dd、yyyy-MM-dd HH:mm:ss 及 ISO 形态），不可转换抛
 *              CS_FORMULA_EXECUTE_FAILED；NOW/TODAY 取服务器时间，不接受任何入参。
 */
@Component
public class DateFunctionProvider implements FormulaFunctionProvider {

    @Override
    public List<FormulaFunctionDefinition> getFunctions() {
        List<FormulaFunctionDefinition> functions = new ArrayList<>();
        functions.add(FormulaFunctions.definition("NOW", "当前时间", SysFormulaFunctionCategory.DATE,
                "NOW()：当前服务器时间（java.util.Date，yyyy-MM-dd HH:mm:ss 语义），无参数",
                SysFormulaReturnType.DATE, new NowFunction()));
        functions.add(FormulaFunctions.definition("TODAY", "当天日期", SysFormulaFunctionCategory.DATE,
                "TODAY()：当天零点（Date，时分秒为 00:00:00），无参数",
                SysFormulaReturnType.DATE, new TodayFunction()));
        functions.add(FormulaFunctions.definition("DATEADD", "日期偏移", SysFormulaFunctionCategory.DATE,
                "DATEADD(date, days[, months[, years]])：日期按天/月/年依次偏移，"
                        + "months/years 缺省 0；days 可为负数表示回退；"
                        + "日期入参宽容转换（Date/文本 yyyy-MM-dd、yyyy-MM-dd HH:mm:ss/时间戳数值）",
                SysFormulaReturnType.DATE, new DateAddFunction(),
                FormulaFunctions.param("date", "DATE", true, null),
                FormulaFunctions.param("days", "NUMBER", true, null),
                FormulaFunctions.param("months", "NUMBER", false, "0"),
                FormulaFunctions.param("years", "NUMBER", false, "0")));
        functions.add(FormulaFunctions.definition("DATEDIFF", "日期相差天数", SysFormulaFunctionCategory.DATE,
                "DATEDIFF(a, b)：a - b 的天数差（按自然日截断时分秒，a 早于 b 返回负数）",
                SysFormulaReturnType.NUMBER, new DateDiffFunction(),
                FormulaFunctions.param("a", "DATE", true, null),
                FormulaFunctions.param("b", "DATE", true, null)));
        functions.add(FormulaFunctions.definition("YEAR", "取年份", SysFormulaFunctionCategory.DATE,
                "YEAR(date)：取日期的年份（服务器时区）",
                SysFormulaReturnType.NUMBER, new YearFunction(),
                FormulaFunctions.param("date", "DATE", true, null)));
        functions.add(FormulaFunctions.definition("MONTH", "取月份", SysFormulaFunctionCategory.DATE,
                "MONTH(date)：取日期的月份（1~12，服务器时区）",
                SysFormulaReturnType.NUMBER, new MonthFunction(),
                FormulaFunctions.param("date", "DATE", true, null)));
        return functions;
    }

    /** NOW() */
    public static class NowFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "NOW";
        }

        @Override
        public AviatorObject call(Map<String, Object> env) {
            return FormulaFunctions.wrap(new Date());
        }
    }

    /** TODAY() */
    public static class TodayFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "TODAY";
        }

        @Override
        public AviatorObject call(Map<String, Object> env) {
            LocalDate today = LocalDate.now();
            return FormulaFunctions.wrap(Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }
    }

    /** DATEADD(date, days[, months[, years]]) */
    public static class DateAddFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "DATEADD";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject date,
                                  AviatorObject days, AviatorObject months, AviatorObject years) {
            Date base = FormulaFunctions.requireDate(date.getValue(env), "DATEADD", "date");
            long dayOffset = requireOffset(days.getValue(env), "days");
            long monthOffset = requireOffset(months.getValue(env), "months");
            long yearOffset = requireOffset(years.getValue(env), "years");
            return FormulaFunctions.wrap(offset(base, dayOffset, monthOffset, yearOffset));
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject date, AviatorObject days) {
            return call(env, date, days,
                    com.googlecode.aviator.runtime.type.AviatorNil.NIL,
                    com.googlecode.aviator.runtime.type.AviatorNil.NIL);
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject date,
                                  AviatorObject days, AviatorObject months) {
            return call(env, date, days, months,
                    com.googlecode.aviator.runtime.type.AviatorNil.NIL);
        }

        /** 偏移量取值：AviatorNil（缺省形态）按 0 处理，其余数值宽容转换 */
        private long requireOffset(Object value, String paramName) {
            if (null == value) {
                return 0L;
            }
            return FormulaFunctions.requireNumber(value, "DATEADD", paramName).longValue();
        }

        /** 年 → 月 → 天依次偏移（Calendar 月末溢出行为：2/29 加一年落 2/28，确定性） */
        private Date offset(Date base, long days, long months, long years) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(base);
            if (0 != years) {
                calendar.add(Calendar.YEAR, Math.toIntExact(years));
            }
            if (0 != months) {
                calendar.add(Calendar.MONTH, Math.toIntExact(months));
            }
            if (0 != days) {
                calendar.add(Calendar.DAY_OF_MONTH, Math.toIntExact(days));
            }
            return calendar.getTime();
        }
    }

    /** DATEDIFF(a, b)：自然日差（截断时分秒） */
    public static class DateDiffFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "DATEDIFF";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2) {
            Date left = FormulaFunctions.requireDate(arg1.getValue(env), "DATEDIFF", "a");
            Date right = FormulaFunctions.requireDate(arg2.getValue(env), "DATEDIFF", "b");
            LocalDate leftDate = left.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate rightDate = right.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return AviatorLong.valueOf(ChronoUnit.DAYS.between(rightDate, leftDate));
        }
    }

    /** YEAR(date) */
    public static class YearFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "YEAR";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject date) {
            Date value = FormulaFunctions.requireDate(date.getValue(env), "YEAR", "date");
            LocalDateTime dateTime = LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
            return AviatorLong.valueOf(dateTime.getYear());
        }
    }

    /** MONTH(date) */
    public static class MonthFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "MONTH";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject date) {
            Date value = FormulaFunctions.requireDate(date.getValue(env), "MONTH", "date");
            LocalDateTime dateTime = LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
            return AviatorLong.valueOf(dateTime.getMonthValue());
        }
    }
}

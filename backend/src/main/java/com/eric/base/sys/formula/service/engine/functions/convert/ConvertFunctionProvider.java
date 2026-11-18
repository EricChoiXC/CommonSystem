package com.eric.base.sys.formula.service.engine.functions.convert;

import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.service.engine.functions.FormulaFunctions;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * CONVERT 类型转换函数 Provider（方案 §2.2 首批：TO_NUMBER/TO_STRING/TO_DATE）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 内置函数注册单元之一。TO_NUMBER/TO_DATE 转换失败按任务口径抛
 *              CS_FORMULA_EXECUTE_FAILED（确定性拒绝，不返回 null 掩盖错误）；
 *              TO_STRING 对 null 确定性返回空串（文本模板场景友好）。
 */
@Component
public class ConvertFunctionProvider implements FormulaFunctionProvider {

    @Override
    public List<FormulaFunctionDefinition> getFunctions() {
        List<FormulaFunctionDefinition> functions = new ArrayList<>();
        functions.add(FormulaFunctions.definition("TO_NUMBER", "转数值", SysFormulaFunctionCategory.CONVERT,
                "TO_NUMBER(value)：Number 原样返回；文本按数值解析（整数返回整型，"
                        + "含小数返回浮点）；null/布尔/解析失败抛 CS_FORMULA_EXECUTE_FAILED",
                SysFormulaReturnType.NUMBER, new ToNumberFunction(),
                FormulaFunctions.param("value", "ANY", true, null)));
        functions.add(FormulaFunctions.definition("TO_STRING", "转文本", SysFormulaFunctionCategory.CONVERT,
                "TO_STRING(value)：null → 空串；Date → yyyy-MM-dd HH:mm:ss；其余 String.valueOf；"
                        + "不抛错（文本模板场景确定性兜底）",
                SysFormulaReturnType.STRING, new ToStringFunction(),
                FormulaFunctions.param("value", "ANY", true, null)));
        functions.add(FormulaFunctions.definition("TO_DATE", "转日期", SysFormulaFunctionCategory.CONVERT,
                "TO_DATE(value)：Date/LocalDate 原样归一为 Date；文本按 yyyy-MM-dd、"
                        + "yyyy-MM-dd HH:mm:ss 宽容解析；null/布尔/解析失败抛 CS_FORMULA_EXECUTE_FAILED",
                SysFormulaReturnType.DATE, new ToDateFunction(),
                FormulaFunctions.param("value", "ANY", true, null)));
        return functions;
    }

    /** TO_NUMBER(value) */
    public static class ToNumberFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "TO_NUMBER";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject value) {
            Object raw = value.getValue(env);
            if (null == raw) {
                throw FormulaFunctions.executeError("TO_NUMBER 参数不能为空");
            }
            if (raw instanceof Number number) {
                return FormulaFunctions.wrapNumber(new BigDecimal(String.valueOf(number)));
            }
            if (raw instanceof String text) {
                String trimmed = text.trim();
                try {
                    return FormulaFunctions.wrapNumber(new BigDecimal(trimmed));
                } catch (NumberFormatException e) {
                    throw FormulaFunctions.executeError("TO_NUMBER 参数不是数值: " + trimmed);
                }
            }
            throw FormulaFunctions.executeError("TO_NUMBER 参数不是数值或数值文本: "
                    + raw.getClass().getSimpleName());
        }
    }

    /** TO_STRING(value) */
    public static class ToStringFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "TO_STRING";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject value) {
            return new com.googlecode.aviator.runtime.type.AviatorString(
                    FormulaFunctions.stringOf(value.getValue(env)));
        }
    }

    /** TO_DATE(value) */
    public static class ToDateFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "TO_DATE";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject value) {
            Date date = FormulaFunctions.requireDate(value.getValue(env), "TO_DATE", "value");
            return FormulaFunctions.wrap(date);
        }
    }
}

package com.eric.base.sys.formula.service.engine.functions.collection;

import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.service.engine.functions.FormulaFunctions;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorLong;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * COLLECTION 集合函数 Provider（方案 §2.2 首批：SUM/AVG/MAX/MIN/COUNT/COUNTIF/SUMIF）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 内置函数注册单元之一。入参为数组/集合变量或 F14-BE-004 引擎
 *              ${list.field} 路径投影后的元素数组，本层只消费数组不做投影；
 *              不引入 eval 任意表达式（§6.3）：COUNTIF 为「等值比较」实现
 *              （Aviator lambda 受沙箱限制，元数据与说明同步口径），SUMIF 按序配对实现。
 */
@Component
public class CollectionFunctionProvider implements FormulaFunctionProvider {

    @Override
    public List<FormulaFunctionDefinition> getFunctions() {
        List<FormulaFunctionDefinition> functions = new ArrayList<>();
        functions.add(FormulaFunctions.definition("SUM", "求和", SysFormulaFunctionCategory.COLLECTION,
                "SUM(seq)：对数组/集合元素求和，元素须为数值（Number 或数值文本，宽容转换）；"
                        + "空集合返回 0；含非数值元素抛 CS_FORMULA_EXECUTE_FAILED",
                SysFormulaReturnType.NUMBER, new SumFunction(),
                FormulaFunctions.param("seq", "ARRAY", true, null)));
        functions.add(FormulaFunctions.definition("AVG", "平均", SysFormulaFunctionCategory.COLLECTION,
                "AVG(seq)：数组/集合元素平均值（SUM/COUNT），空集合返回 0；口径同 SUM",
                SysFormulaReturnType.NUMBER, new AvgFunction(),
                FormulaFunctions.param("seq", "ARRAY", true, null)));
        functions.add(FormulaFunctions.definition("MAX", "最大值", SysFormulaFunctionCategory.COLLECTION,
                "MAX(seq)：数组/集合元素最大值（数值或同类型可比较元素，口径同 GT 系列）；"
                        + "空集合返回 null",
                SysFormulaReturnType.NUMBER, new MaxFunction(),
                FormulaFunctions.param("seq", "ARRAY", true, null)));
        functions.add(FormulaFunctions.definition("MIN", "最小值", SysFormulaFunctionCategory.COLLECTION,
                "MIN(seq)：数组/集合元素最小值，口径同 MAX；空集合返回 null",
                SysFormulaReturnType.NUMBER, new MinFunction(),
                FormulaFunctions.param("seq", "ARRAY", true, null)));
        functions.add(FormulaFunctions.definition("COUNT", "计数", SysFormulaFunctionCategory.COLLECTION,
                "COUNT(seq)：数组/集合/Map 元素个数；null → 0（确定性）；文本按字符数",
                SysFormulaReturnType.NUMBER, new CountFunction(),
                FormulaFunctions.param("seq", "ARRAY", true, null)));
        functions.add(FormulaFunctions.definition("COUNTIF", "条件计数", SysFormulaFunctionCategory.COLLECTION,
                "COUNTIF(seq, expected)：统计数组/集合中与 expected 等值（EQ 口径）的元素个数；"
                        + "一期为等值比较实现，不支持表达式谓词（沙箱限制，说明口径）",
                SysFormulaReturnType.NUMBER, new CountIfFunction(),
                FormulaFunctions.param("seq", "ARRAY", true, null),
                FormulaFunctions.param("expected", "ANY", true, null)));
        functions.add(FormulaFunctions.definition("SUMIF", "条件求和", SysFormulaFunctionCategory.COLLECTION,
                "SUMIF(seq, expected, sumValues)：seq 中元素等值于 expected（EQ 口径）的位置，"
                        + "对 sumValues（同序数组/集合，与 seq 逐位配对）对应位求和；两序列长度须一致，"
                        + "否则抛 CS_FORMULA_EXECUTE_FAILED",
                SysFormulaReturnType.NUMBER, new SumIfFunction(),
                FormulaFunctions.param("seq", "ARRAY", true, null),
                FormulaFunctions.param("expected", "ANY", true, null),
                FormulaFunctions.param("sumValues", "ARRAY", true, null)));
        return functions;
    }

    /** SUM(seq) */
    public static class SumFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "SUM";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject seq) {
            List<?> elements = FormulaFunctions.sequenceOf(seq.getValue(env), "SUM", "seq");
            BigDecimal total = BigDecimal.ZERO;
            for (Object element : elements) {
                total = total.add(requireElementNumber(element, "SUM"));
            }
            return FormulaFunctions.wrapNumber(total);
        }
    }

    /** AVG(seq)：SUM/COUNT，空集合确定性返回 0 */
    public static class AvgFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "AVG";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject seq) {
            List<?> elements = FormulaFunctions.sequenceOf(seq.getValue(env), "AVG", "seq");
            if (elements.isEmpty()) {
                return FormulaFunctions.wrapNumber(BigDecimal.ZERO);
            }
            BigDecimal total = BigDecimal.ZERO;
            for (Object element : elements) {
                total = total.add(requireElementNumber(element, "AVG"));
            }
            return FormulaFunctions.wrapNumber(total
                    .divide(BigDecimal.valueOf(elements.size()), 10, java.math.RoundingMode.HALF_UP));
        }
    }

    /** MAX(seq) */
    public static class MaxFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "MAX";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject seq) {
            List<?> elements = FormulaFunctions.sequenceOf(seq.getValue(env), "MAX", "seq");
            Object max = null;
            for (Object element : elements) {
                if (null == max || FormulaFunctions.orderedCompare(element, max, "MAX") > 0) {
                    max = element;
                }
            }
            return FormulaFunctions.wrap(max);
        }
    }

    /** MIN(seq) */
    public static class MinFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "MIN";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject seq) {
            List<?> elements = FormulaFunctions.sequenceOf(seq.getValue(env), "MIN", "seq");
            Object min = null;
            for (Object element : elements) {
                if (null == min || FormulaFunctions.orderedCompare(element, min, "MIN") < 0) {
                    min = element;
                }
            }
            return FormulaFunctions.wrap(min);
        }
    }

    /** COUNT(seq) */
    public static class CountFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "COUNT";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject seq) {
            return AviatorLong.valueOf(FormulaFunctions.sizeOf(seq.getValue(env), "COUNT"));
        }
    }

    /** COUNTIF(seq, expected)：等值比较实现 */
    public static class CountIfFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "COUNTIF";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject seq, AviatorObject expected) {
            List<?> elements = FormulaFunctions.sequenceOf(seq.getValue(env), "COUNTIF", "seq");
            Object expectedValue = expected.getValue(env);
            long count = 0L;
            for (Object element : elements) {
                if (FormulaFunctions.valueEquals(element, expectedValue)) {
                    count++;
                }
            }
            return AviatorLong.valueOf(count);
        }
    }

    /** SUMIF(seq, expected, sumValues)：等值位置对同序数组配对求和 */
    public static class SumIfFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "SUMIF";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject seq,
                                  AviatorObject expected, AviatorObject sumValues) {
            List<?> conditions = FormulaFunctions.requireSequence(seq.getValue(env), "SUMIF", "seq");
            List<?> values = FormulaFunctions.requireSequence(sumValues.getValue(env), "SUMIF", "sumValues");
            if (conditions.size() != values.size()) {
                throw FormulaFunctions.executeError(
                        "SUMIF 条件序列与求和序列长度不一致: " + conditions.size() + " 与 " + values.size());
            }
            Object expectedValue = expected.getValue(env);
            BigDecimal total = BigDecimal.ZERO;
            for (int i = 0; i < conditions.size(); i++) {
                if (FormulaFunctions.valueEquals(conditions.get(i), expectedValue)) {
                    total = total.add(requireElementNumber(values.get(i), "SUMIF"));
                }
            }
            return FormulaFunctions.wrapNumber(total);
        }
    }

    /**
     * 集合元素数值宽容转换（Number/数值文本；null 元素视为 0 参与求和，
     * 投影缺失字段即 null，确定性语义），非数值抛确定性错误
     */
    private static BigDecimal requireElementNumber(Object element, String functionCode) {
        if (null == element) {
            return BigDecimal.ZERO;
        }
        if (element instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        if (element instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                throw FormulaFunctions.executeError(functionCode + " 集合元素不是数值: " + text.trim());
            }
        }
        throw FormulaFunctions.executeError(functionCode + " 集合元素不是数值");
    }
}

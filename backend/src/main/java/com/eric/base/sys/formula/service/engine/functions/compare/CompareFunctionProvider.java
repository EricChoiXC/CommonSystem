package com.eric.base.sys.formula.service.engine.functions.compare;

import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.service.engine.functions.FormulaFunctions;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * COMPARE 比较函数 Provider（方案 §2.2 首批：EQ/NE/GT/GTE/LT/LTE）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 内置函数注册单元之一。EQ/NE 用 valueEquals（数值跨类型精确比较、
 *              文本与数值不跨型）；GT/GTE/LT/LTE 用 orderedCompare（数值跨类型或同类型
 *              Comparable，混合类型/空值抛 CS_FORMULA_EXECUTE_FAILED，建议 IFNULL 前置兜底）。
 */
@Component
public class CompareFunctionProvider implements FormulaFunctionProvider {

    @Override
    public List<FormulaFunctionDefinition> getFunctions() {
        List<FormulaFunctionDefinition> functions = new ArrayList<>();
        functions.add(FormulaFunctions.definition("EQ", "相等判断", SysFormulaFunctionCategory.COMPARE,
                "EQ(left, right)：值相等返回 true（数值跨类型按值比较：1 与 1.0 相等；"
                        + "文本与数值不跨型匹配）；任一侧为 null 仅在双侧均为 null 时相等",
                SysFormulaReturnType.BOOLEAN, new EqFunction(),
                FormulaFunctions.param("left", "ANY", true, null),
                FormulaFunctions.param("right", "ANY", true, null)));
        functions.add(FormulaFunctions.definition("NE", "不等判断", SysFormulaFunctionCategory.COMPARE,
                "NE(left, right)：值不等返回 true，口径与 EQ 相反",
                SysFormulaReturnType.BOOLEAN, new NeFunction(),
                FormulaFunctions.param("left", "ANY", true, null),
                FormulaFunctions.param("right", "ANY", true, null)));
        functions.add(FormulaFunctions.definition("GT", "大于", SysFormulaFunctionCategory.COMPARE,
                "GT(left, right)：left > right 返回 true；数值跨类型比较，文本按字典序；"
                        + "空值或混合类型抛 CS_FORMULA_EXECUTE_FAILED（建议 IFNULL 前置兜底）",
                SysFormulaReturnType.BOOLEAN, new GtFunction(),
                FormulaFunctions.param("left", "ANY", true, null),
                FormulaFunctions.param("right", "ANY", true, null)));
        functions.add(FormulaFunctions.definition("GTE", "大于等于", SysFormulaFunctionCategory.COMPARE,
                "GTE(left, right)：left >= right 返回 true，口径同 GT",
                SysFormulaReturnType.BOOLEAN, new GteFunction(),
                FormulaFunctions.param("left", "ANY", true, null),
                FormulaFunctions.param("right", "ANY", true, null)));
        functions.add(FormulaFunctions.definition("LT", "小于", SysFormulaFunctionCategory.COMPARE,
                "LT(left, right)：left < right 返回 true，口径同 GT",
                SysFormulaReturnType.BOOLEAN, new LtFunction(),
                FormulaFunctions.param("left", "ANY", true, null),
                FormulaFunctions.param("right", "ANY", true, null)));
        functions.add(FormulaFunctions.definition("LTE", "小于等于", SysFormulaFunctionCategory.COMPARE,
                "LTE(left, right)：left <= right 返回 true，口径同 GT",
                SysFormulaReturnType.BOOLEAN, new LteFunction(),
                FormulaFunctions.param("left", "ANY", true, null),
                FormulaFunctions.param("right", "ANY", true, null)));
        return functions;
    }

    /** EQ(left, right) */
    public static class EqFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "EQ";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject left, AviatorObject right) {
            return AviatorBoolean.valueOf(
                    FormulaFunctions.valueEquals(left.getValue(env), right.getValue(env)));
        }
    }

    /** NE(left, right) */
    public static class NeFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "NE";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject left, AviatorObject right) {
            return AviatorBoolean.valueOf(
                    !FormulaFunctions.valueEquals(left.getValue(env), right.getValue(env)));
        }
    }

    /**
     * 排序比较函数基类（GT/GTE/LT/LTE）：子类按 compare 结果判定
     */
    public abstract static class AbstractCompareFunction extends AbstractFunction {

        private final String functionCode;

        protected AbstractCompareFunction(String functionCode) {
            this.functionCode = functionCode;
        }

        protected String functionCode() {
            return functionCode;
        }

        /** compare 结果判定（负数/0/正数 = 小于/等于/大于） */
        protected abstract boolean judge(int compare);

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject left, AviatorObject right) {
            int compare = FormulaFunctions.orderedCompare(left.getValue(env), right.getValue(env),
                    functionCode);
            return AviatorBoolean.valueOf(judge(compare));
        }
    }

    /** GT(left, right) */
    public static class GtFunction extends AbstractCompareFunction {

        public GtFunction() {
            super("GT");
        }

        @Override
        public String getName() {
            return "GT";
        }

        @Override
        protected boolean judge(int compare) {
            return compare > 0;
        }
    }

    /** GTE(left, right) */
    public static class GteFunction extends AbstractCompareFunction {

        public GteFunction() {
            super("GTE");
        }

        @Override
        public String getName() {
            return "GTE";
        }

        @Override
        protected boolean judge(int compare) {
            return compare >= 0;
        }
    }

    /** LT(left, right) */
    public static class LtFunction extends AbstractCompareFunction {

        public LtFunction() {
            super("LT");
        }

        @Override
        public String getName() {
            return "LT";
        }

        @Override
        protected boolean judge(int compare) {
            return compare < 0;
        }
    }

    /** LTE(left, right) */
    public static class LteFunction extends AbstractCompareFunction {

        public LteFunction() {
            super("LTE");
        }

        @Override
        public String getName() {
            return "LTE";
        }

        @Override
        protected boolean judge(int compare) {
            return compare <= 0;
        }
    }
}

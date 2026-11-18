package com.eric.base.sys.formula.service.engine.functions.math;

import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.service.engine.functions.FormulaFunctions;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MATH 数学函数 Provider（方案 §2.2 首批：ABS/ROUND/CEIL/FLOOR/POWER）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 内置函数注册单元之一，经 Spring 构造注入被 FormulaFunctionRegistry 收集。
 *              除零等确定性错误按 FormulaFunctions#executeError 抛 CS_FORMULA_EXECUTE_FAILED
 *              前缀异常（§6.5 统一兜底），空值拒绝由用户以 IFNULL 显式兜底。
 */
@Component
public class MathFunctionProvider implements FormulaFunctionProvider {

    @Override
    public List<FormulaFunctionDefinition> getFunctions() {
        List<FormulaFunctionDefinition> functions = new ArrayList<>();
        functions.add(FormulaFunctions.definition("ABS", "绝对值", SysFormulaFunctionCategory.MATH,
                "ABS(number)：取数值的绝对值；参数为空或非数值抛 CS_FORMULA_EXECUTE_FAILED",
                SysFormulaReturnType.NUMBER, new AbsFunction(),
                FormulaFunctions.param("number", "NUMBER", true, null)));
        functions.add(FormulaFunctions.definition("ROUND", "四舍五入", SysFormulaFunctionCategory.MATH,
                "ROUND(number[, scale])：按 scale 位小数四舍五入（HALF_UP， banker 舍入不用），"
                        + "scale 缺省 0 返回整数；scale 为负时向小数点左侧进位",
                SysFormulaReturnType.NUMBER, new RoundFunction(),
                FormulaFunctions.param("number", "NUMBER", true, null),
                FormulaFunctions.param("scale", "NUMBER", false, "0")));
        functions.add(FormulaFunctions.definition("CEIL", "向上取整", SysFormulaFunctionCategory.MATH,
                "CEIL(number)：向上取整（ceil），返回不小于参数的最小整数",
                SysFormulaReturnType.NUMBER, new CeilFunction(),
                FormulaFunctions.param("number", "NUMBER", true, null)));
        functions.add(FormulaFunctions.definition("FLOOR", "向下取整", SysFormulaFunctionCategory.MATH,
                "FLOOR(number)：向下取整（floor），返回不大于参数的最大整数",
                SysFormulaReturnType.NUMBER, new FloorFunction(),
                FormulaFunctions.param("number", "NUMBER", true, null)));
        functions.add(FormulaFunctions.definition("POWER", "幂运算", SysFormulaFunctionCategory.MATH,
                "POWER(base, exponent)：底数的指数次幂；0 的负数次幂抛 CS_FORMULA_EXECUTE_FAILED",
                SysFormulaReturnType.NUMBER, new PowerFunction(),
                FormulaFunctions.param("base", "NUMBER", true, null),
                FormulaFunctions.param("exponent", "NUMBER", true, null)));
        return functions;
    }

    /** ABS(number) */
    public static class AbsFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "ABS";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg) {
            Number number = FormulaFunctions.requireNumber(arg.getValue(env), "ABS", "number");
            return FormulaFunctions.wrapNumber(new BigDecimal(String.valueOf(number)).abs());
        }
    }

    /** ROUND(number[, scale])：HALF_UP 四舍五入，scale 可选缺省 0 */
    public static class RoundFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "ROUND";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2) {
            Number number = FormulaFunctions.requireNumber(arg1.getValue(env), "ROUND", "number");
            Number scale = FormulaFunctions.requireNumber(arg2.getValue(env), "ROUND", "scale");
            return FormulaFunctions.wrapNumber(round(number, scale.intValue()));
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg) {
            Number number = FormulaFunctions.requireNumber(arg.getValue(env), "ROUND", "number");
            return FormulaFunctions.wrapNumber(round(number, 0));
        }

        private BigDecimal round(Number number, int scale) {
            return new BigDecimal(String.valueOf(number))
                    .setScale(scale, RoundingMode.HALF_UP);
        }
    }

    /** CEIL(number) */
    public static class CeilFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "CEIL";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg) {
            Number number = FormulaFunctions.requireNumber(arg.getValue(env), "CEIL", "number");
            return FormulaFunctions.wrapNumber(new BigDecimal(String.valueOf(number))
                    .round(new MathContext(0, RoundingMode.CEILING))
                    .setScale(0, RoundingMode.CEILING));
        }
    }

    /** FLOOR(number) */
    public static class FloorFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "FLOOR";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg) {
            Number number = FormulaFunctions.requireNumber(arg.getValue(env), "FLOOR", "number");
            return FormulaFunctions.wrapNumber(new BigDecimal(String.valueOf(number))
                    .setScale(0, RoundingMode.FLOOR));
        }
    }

    /** POWER(base, exponent) */
    public static class PowerFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "POWER";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2) {
            Number base = FormulaFunctions.requireNumber(arg1.getValue(env), "POWER", "base");
            Number exponent = FormulaFunctions.requireNumber(arg2.getValue(env), "POWER", "exponent");
            double baseDouble = base.doubleValue();
            double exponentDouble = exponent.doubleValue();
            if (baseDouble == 0d && exponentDouble < 0d) {
                throw FormulaFunctions.executeError("POWER 0 的负数次幂无意义");
            }
            return FormulaFunctions.wrap(Math.pow(baseDouble, exponentDouble));
        }
    }
}

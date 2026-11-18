package com.eric.base.sys.formula.service.engine.functions.logic;

import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.service.engine.functions.FormulaFunctions;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LOGIC 逻辑函数 Provider（方案 §2.2 首批：IF/AND/OR/NOT/IFNULL）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 内置函数注册单元之一，经 Spring 构造注入被 FormulaFunctionRegistry 收集。
 *              实参在 Aviator 调用函数前已全部求值（引擎语义），IF/IFNULL 直接返回分支
 *              AviatorObject（取值延迟到消费点，缺失变量解析为 nil）；AND/OR/NOT 对
 *              null 实参按 false 处理（确定性语义）；IFNULL 是 §6.5 空值兜底的显式出口。
 *              元数据口径：返回类型随分支取值确定的函数（IF/IFNULL）returnType 记 ARRAY
 *              表示动态类型，说明中注明。
 */
@Component
public class LogicFunctionProvider implements FormulaFunctionProvider {

    @Override
    public List<FormulaFunctionDefinition> getFunctions() {
        List<FormulaFunctionDefinition> functions = new ArrayList<>();
        functions.add(FormulaFunctions.definition("IF", "条件判断", SysFormulaFunctionCategory.LOGIC,
                "IF(condition, thenValue, elseValue)：条件为真返回 thenValue，否则返回 elseValue；"
                        + "condition 非布尔值抛 CS_FORMULA_EXECUTE_FAILED（缺失变量按 false）；"
                        + "返回类型随分支取值确定（元数据 returnType 记 ARRAY 表示动态类型）",
                SysFormulaReturnType.ARRAY, new IfFunction(),
                FormulaFunctions.param("condition", "BOOLEAN", true, null),
                FormulaFunctions.param("thenValue", "ANY", true, null),
                FormulaFunctions.param("elseValue", "ANY", true, null)));
        functions.add(FormulaFunctions.definition("AND", "并且", SysFormulaFunctionCategory.LOGIC,
                "AND(value1, value2, ...)：变长参数全部为真返回 true，否则 false；缺失变量按 false",
                SysFormulaReturnType.BOOLEAN, new AndFunction(),
                FormulaFunctions.param("value1", "ANY", true, null),
                FormulaFunctions.param("value2", "ANY", true, null)));
        functions.add(FormulaFunctions.definition("OR", "或者", SysFormulaFunctionCategory.LOGIC,
                "OR(value1, value2, ...)：变长参数任一为真返回 true，全假或全缺失返回 false",
                SysFormulaReturnType.BOOLEAN, new OrFunction(),
                FormulaFunctions.param("value1", "ANY", true, null),
                FormulaFunctions.param("value2", "ANY", true, null)));
        functions.add(FormulaFunctions.definition("NOT", "取反", SysFormulaFunctionCategory.LOGIC,
                "NOT(value)：布尔取反；缺失变量按 false（取反为 true）",
                SysFormulaReturnType.BOOLEAN, new NotFunction(),
                FormulaFunctions.param("value", "BOOLEAN", true, null)));
        functions.add(FormulaFunctions.definition("IFNULL", "空值兜底", SysFormulaFunctionCategory.LOGIC,
                "IFNULL(value, defaultValue)：value 为 null（缺失变量）时返回 defaultValue，"
                        + "否则原值；空字符串不视为 null（显式空串是确定性值）；"
                        + "返回类型随分支取值确定（元数据 returnType 记 ARRAY 表示动态类型）",
                SysFormulaReturnType.ARRAY, new IfNullFunction(),
                FormulaFunctions.param("value", "ANY", true, null),
                FormulaFunctions.param("defaultValue", "ANY", true, null)));
        return functions;
    }

    /** IF(condition, thenValue, elseValue) */
    public static class IfFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "IF";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject condition,
                                  AviatorObject thenValue, AviatorObject elseValue) {
            boolean flag = FormulaFunctions.requireBoolean(condition.getValue(env), "IF", "condition");
            return flag ? thenValue : elseValue;
        }
    }

    /** AND(value1, value2, ...)：变长参数全真为真 */
    public static class AndFunction extends com.googlecode.aviator.runtime.function.AbstractVariadicFunction {

        @Override
        public String getName() {
            return "AND";
        }

        @Override
        public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
            boolean result = true;
            for (AviatorObject arg : args) {
                if (!FormulaFunctions.requireBoolean(arg.getValue(env), "AND", "value")) {
                    result = false;
                    break;
                }
            }
            return com.googlecode.aviator.runtime.type.AviatorBoolean.valueOf(result);
        }
    }

    /** OR(value1, value2, ...)：变长参数任一为真 */
    public static class OrFunction extends com.googlecode.aviator.runtime.function.AbstractVariadicFunction {

        @Override
        public String getName() {
            return "OR";
        }

        @Override
        public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
            boolean result = false;
            for (AviatorObject arg : args) {
                if (FormulaFunctions.requireBoolean(arg.getValue(env), "OR", "value")) {
                    result = true;
                    break;
                }
            }
            return com.googlecode.aviator.runtime.type.AviatorBoolean.valueOf(result);
        }
    }

    /** NOT(value) */
    public static class NotFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "NOT";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg) {
            boolean flag = FormulaFunctions.requireBoolean(arg.getValue(env), "NOT", "value");
            return com.googlecode.aviator.runtime.type.AviatorBoolean.valueOf(!flag);
        }
    }

    /** IFNULL(value, defaultValue) */
    public static class IfNullFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "IFNULL";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject value, AviatorObject defaultValue) {
            return null == value.getValue(env) ? defaultValue : value;
        }
    }
}

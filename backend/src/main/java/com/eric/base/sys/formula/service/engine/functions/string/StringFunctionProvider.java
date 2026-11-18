package com.eric.base.sys.formula.service.engine.functions.string;

import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.service.engine.functions.FormulaFunctions;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorLong;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * STRING 字符串函数 Provider（方案 §2.2 首批：CONCAT/SUBSTRING/LENGTH/TRIM/REPLACE）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 内置函数注册单元之一。文本参数非字符串时经 stringOf 宽容文本化
 *              （null → 空串、Date → yyyy-MM-dd HH:mm:ss）；SUBSTRING 越界按确定性
 *              语义截断（begin 超长返回空串、end 越界取文本长度），不抛错。
 */
@Component
public class StringFunctionProvider implements FormulaFunctionProvider {

    @Override
    public List<FormulaFunctionDefinition> getFunctions() {
        List<FormulaFunctionDefinition> functions = new ArrayList<>();
        functions.add(FormulaFunctions.definition("CONCAT", "字符串拼接", SysFormulaFunctionCategory.STRING,
                "CONCAT(text1, text2, ...)：变长参数依序拼接为字符串；null → 空串，"
                        + "非文本值宽容文本化（Date → yyyy-MM-dd HH:mm:ss）",
                SysFormulaReturnType.STRING, new ConcatFunction(),
                FormulaFunctions.param("text1", "STRING", true, null),
                FormulaFunctions.param("text2", "STRING", true, null)));
        functions.add(FormulaFunctions.definition("SUBSTRING", "取子串", SysFormulaFunctionCategory.STRING,
                "SUBSTRING(text, begin[, end])：取 [begin, end) 区间子串，end 缺省到末尾；"
                        + "负数 begin 按 0 处理，越界按确定性语义截断（不抛错）",
                SysFormulaReturnType.STRING, new SubstringFunction(),
                FormulaFunctions.param("text", "STRING", true, null),
                FormulaFunctions.param("begin", "NUMBER", true, null),
                FormulaFunctions.param("end", "NUMBER", false, null)));
        functions.add(FormulaFunctions.definition("LENGTH", "文本长度", SysFormulaFunctionCategory.STRING,
                "LENGTH(text)：返回文本字符数；null → 0；非文本值宽容文本化后计数",
                SysFormulaReturnType.NUMBER, new LengthFunction(),
                FormulaFunctions.param("text", "STRING", true, null)));
        functions.add(FormulaFunctions.definition("TRIM", "去除首尾空白", SysFormulaFunctionCategory.STRING,
                "TRIM(text)：去除文本首尾空白；null → 空串",
                SysFormulaReturnType.STRING, new TrimFunction(),
                FormulaFunctions.param("text", "STRING", true, null)));
        functions.add(FormulaFunctions.definition("REPLACE", "文本替换", SysFormulaFunctionCategory.STRING,
                "REPLACE(text, oldText, newText)：将文本中全部 oldText 替换为 newText；"
                        + "oldText 为空串时原样返回（确定性语义）",
                SysFormulaReturnType.STRING, new ReplaceFunction(),
                FormulaFunctions.param("text", "STRING", true, null),
                FormulaFunctions.param("oldText", "STRING", true, null),
                FormulaFunctions.param("newText", "STRING", true, null)));
        return functions;
    }

    /** CONCAT(text1, text2, ...)：变长拼接 */
    public static class ConcatFunction
            extends com.googlecode.aviator.runtime.function.AbstractVariadicFunction {

        @Override
        public String getName() {
            return "CONCAT";
        }

        @Override
        public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
            StringBuilder builder = new StringBuilder();
            for (AviatorObject arg : args) {
                builder.append(FormulaFunctions.stringOf(arg.getValue(env)));
            }
            return new com.googlecode.aviator.runtime.type.AviatorString(builder.toString());
        }
    }

    /** SUBSTRING(text, begin[, end]) */
    public static class SubstringFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "SUBSTRING";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject text,
                                  AviatorObject begin, AviatorObject end) {
            String value = FormulaFunctions.stringOf(text.getValue(env));
            int beginIndex = FormulaFunctions.requireNumber(begin.getValue(env), "SUBSTRING", "begin")
                    .intValue();
            int endIndex = FormulaFunctions.requireNumber(end.getValue(env), "SUBSTRING", "end")
                    .intValue();
            return FormulaFunctions.wrap(substring(value, beginIndex, endIndex));
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject text, AviatorObject begin) {
            String value = FormulaFunctions.stringOf(text.getValue(env));
            int beginIndex = FormulaFunctions.requireNumber(begin.getValue(env), "SUBSTRING", "begin")
                    .intValue();
            return FormulaFunctions.wrap(substring(value, beginIndex, value.length()));
        }

        /** 确定性截断：负数 begin 归 0，end 越界取长度，begin >= end 返回空串 */
        private String substring(String value, int beginIndex, int endIndex) {
            int safeBegin = Math.max(beginIndex, 0);
            int safeEnd = Math.min(Math.max(endIndex, 0), value.length());
            if (safeBegin >= safeEnd) {
                return "";
            }
            return value.substring(safeBegin, safeEnd);
        }
    }

    /** LENGTH(text) */
    public static class LengthFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "LENGTH";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject text) {
            String value = FormulaFunctions.stringOf(text.getValue(env));
            return AviatorLong.valueOf(value.length());
        }
    }

    /** TRIM(text) */
    public static class TrimFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "TRIM";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject text) {
            return FormulaFunctions.wrap(FormulaFunctions.stringOf(text.getValue(env)).trim());
        }
    }

    /** REPLACE(text, oldText, newText) */
    public static class ReplaceFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "REPLACE";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject text,
                                  AviatorObject oldText, AviatorObject newText) {
            String value = FormulaFunctions.stringOf(text.getValue(env));
            String old = FormulaFunctions.stringOf(oldText.getValue(env));
            String replacement = FormulaFunctions.stringOf(newText.getValue(env));
            if (old.isEmpty()) {
                return FormulaFunctions.wrap(value);
            }
            return FormulaFunctions.wrap(value.replace(old, replacement));
        }
    }
}

package com.eric.base.sys.formula.service.engine.functions;

import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.model.SysFormulaFunctionParameterDo;
import com.eric.base.sys.formula.properties.SysFormulaConstants;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorDouble;
import com.googlecode.aviator.runtime.type.AviatorLong;
import com.googlecode.aviator.runtime.type.AviatorNil;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;
import com.googlecode.aviator.runtime.type.AviatorString;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 内置函数公共支撑
 *
 * @author Eric
 * @date 2026/11/18
 * @description 九类内置函数共享的工具层（F14-BE-003）：错误抛出约定、元数据构造、
 *              参数宽容转换与结果包装。确定性兜底语义（方案 §6.5）统一落点：
 *              <ul>
 *              <li>确定性错误一律 {@code throw new IllegalArgumentException(
 *                  "CS_FORMULA_EXECUTE_FAILED: " + message)}（对齐 SysDatasourceService
 *                  datasourceError 先例），由引擎（F14-BE-004/005）统一捕获转错误响应，
 *                  不裸异常穿透；</li>
 *              <li>空值拒绝：标量函数（MATH/DATE/CONVERT/COMPARE 排序比较）null 入参
 *                  抛 CS_FORMULA_EXECUTE_FAILED，用户以 IFNULL 显式兜底；</li>
 *              <li>宽容转换：数值入参收 Number/文本、日期入参收 Date/LocalDate/
 *                  LocalDateTime/时间戳数值/文本（yyyy-MM-dd、yyyy-MM-dd HH:mm:ss）。</li>
 *              </ul>
 */
public final class FormulaFunctions {

    /** 日期文本解析格式（宽容转换规则，元数据与说明同步） */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 日期时间文本解析格式（宽容转换规则） */
    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 日期输出格式（TO_STRING 等文本化场景） */
    public static final SimpleDateFormat OUTPUT_DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private FormulaFunctions() {
    }

    /* ==================== 错误抛出约定 ==================== */

    /**
     * 构造执行期确定性错误：message 以 {@code CS_FORMULA_EXECUTE_FAILED} 为前缀，
     * 引擎统一按前缀识别转错误响应（先例 SysDatasourceService#datasourceError）
     */
    public static IllegalArgumentException executeError(String message) {
        return new IllegalArgumentException(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED + ": " + message);
    }

    /* ==================== 元数据构造 ==================== */

    /**
     * 构造函数定义（元数据 + 执行体）；functionCode 与执行体 getName() 同源于
     * 各函数类的 FUNCTION_CODE 常量，天然一致
     */
    public static FormulaFunctionDefinition definition(String functionCode, String functionName,
                                                       SysFormulaFunctionCategory category, String description,
                                                       SysFormulaReturnType returnType, Object function,
                                                       SysFormulaFunctionParameterDo... parameters) {
        FormulaFunctionDefinition definition = new FormulaFunctionDefinition();
        definition.setFunctionCode(functionCode);
        definition.setFunctionName(functionName);
        definition.setCategory(category);
        definition.setDescription(description);
        definition.setParameters(List.of(parameters));
        definition.setReturnType(returnType);
        definition.setFunction((com.googlecode.aviator.runtime.type.AviatorFunction) function);
        return definition;
    }

    /**
     * 构造参数元数据（name/type/required/defaultValue 四元组）
     */
    public static SysFormulaFunctionParameterDo param(String name, String type, boolean required,
                                                      String defaultValue) {
        SysFormulaFunctionParameterDo parameter = new SysFormulaFunctionParameterDo();
        parameter.setName(name);
        parameter.setType(type);
        parameter.setRequired(required);
        parameter.setDefaultValue(defaultValue);
        return parameter;
    }

    /* ==================== 参数宽容转换 ==================== */

    /**
     * 数值宽容转换：Number 原样、文本按 BigDecimal 解析；null / 布尔 / 其他类型
     * 抛 CS_FORMULA_EXECUTE_FAILED（空值拒绝）
     */
    public static Number requireNumber(Object value, String functionCode, String paramName) {
        if (null == value) {
            throw executeError(functionCode + " 参数 " + paramName + " 不能为空");
        }
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                throw executeError(functionCode + " 参数 " + paramName + " 不是数值: " + text.trim());
            }
        }
        throw executeError(functionCode + " 参数 " + paramName + " 不是数值");
    }

    /**
     * 布尔宽容取值：Boolean 原样、null 视为 false（缺失变量即 nil，确定性语义），
     * 其他类型抛 CS_FORMULA_EXECUTE_FAILED
     */
    public static boolean requireBoolean(Object value, String functionCode, String paramName) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (null == value) {
            return false;
        }
        throw executeError(functionCode + " 参数 " + paramName + " 不是布尔值");
    }

    /**
     * 日期宽容转换：Date / LocalDate / LocalDateTime / 时间戳数值 / 文本
     * （yyyy-MM-dd、yyyy-MM-dd HH:mm:ss，另兼容 ISO 形态）；不可转换返回 null
     * （调用方按需抛确定性错误）
     */
    public static Date toDate(Object value) {
        if (value instanceof Date date) {
            return date;
        }
        if (value instanceof LocalDate localDate) {
            return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        if (value instanceof LocalDateTime localDateTime) {
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        if (value instanceof Number number) {
            return new Date(number.longValue());
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            try {
                return Date.from(LocalDate.parse(trimmed, DATE_FORMATTER)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
            try {
                return Date.from(LocalDateTime.parse(trimmed, DATETIME_FORMATTER)
                        .atZone(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                // 尝试 ISO 兼容形态
            }
            try {
                return Date.from(LocalDateTime.parse(trimmed).atZone(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                // 尝试 ISO 日期形态
            }
            try {
                return Date.from(LocalDate.parse(trimmed).atStartOfDay(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                // 全部格式失败
            }
            return null;
        }
        return null;
    }

    /**
     * 日期必填转换：空值或不可转换抛 CS_FORMULA_EXECUTE_FAILED（空值拒绝）
     */
    public static Date requireDate(Object value, String functionCode, String paramName) {
        Date date = toDate(value);
        if (null == date) {
            throw executeError(functionCode + " 参数 " + paramName + " 不能为空或不是可识别的日期");
        }
        return date;
    }

    /**
     * 集合宽容收集：Collection / 数组（含基本类型数组）转 List；null → 空列表
     * （聚合函数对缺失变量按空集合确定性处理，口径同 COUNT null → 0）；
     * 其他类型抛 CS_FORMULA_EXECUTE_FAILED
     */
    public static List<?> sequenceOf(Object value, String functionCode, String paramName) {
        if (null == value) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(value, i));
            }
            return list;
        }
        throw executeError(functionCode + " 参数 " + paramName + " 不是数组或集合");
    }

    /**
     * 集合必填收集：null 抛 CS_FORMULA_EXECUTE_FAILED（配对类函数明确要求实序数组）
     */
    public static List<?> requireSequence(Object value, String functionCode, String paramName) {
        if (null == value) {
            throw executeError(functionCode + " 参数 " + paramName + " 不能为空，须为数组或集合");
        }
        return sequenceOf(value, functionCode, paramName);
    }

    /* ==================== 等值与比较 ==================== */

    /**
     * 等值比较（COUNTIF / SUMIF / EQ / NE 共用）：双方为 null 视为相等；数值跨类型
     * 按 BigDecimal 精确比较（Long 100 与 Double 100.0 相等）；其余按 equals。
     * 文本与数值不跨型匹配（"100" 与 100 不等，确定性语义）
     */
    public static boolean valueEquals(Object left, Object right) {
        if (null == left || null == right) {
            return null == left && null == right;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return new BigDecimal(String.valueOf(leftNumber))
                    .compareTo(new BigDecimal(String.valueOf(rightNumber))) == 0;
        }
        return left.equals(right);
    }

    /**
     * 排序比较（GT/GTE/LT/LTE / MAX / MIN 共用）：数值跨类型精确比较；双方同类型
     * 且可比较（String/Date 等）按 compareTo；混合类型或含空值抛 CS_FORMULA_EXECUTE_FAILED
     *
     * @return 负数 / 0 / 正数 = left 小于 / 等于 / 大于 right
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int orderedCompare(Object left, Object right, String functionCode) {
        if (null == left || null == right) {
            throw executeError(functionCode + " 参与排序比较的值存在空值，请先用 IFNULL 兜底");
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            try {
                return new BigDecimal(String.valueOf(leftNumber))
                        .compareTo(new BigDecimal(String.valueOf(rightNumber)));
            } catch (NumberFormatException e) {
                return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
            }
        }
        if (left.getClass() == right.getClass() && left instanceof Comparable) {
            return ((Comparable) left).compareTo(right);
        }
        throw executeError(functionCode + " 两参数类型不可比较: "
                + left.getClass().getSimpleName() + " 与 " + right.getClass().getSimpleName());
    }

    /* ==================== 文本化 ==================== */

    /**
     * 值文本化（CONCAT / TO_STRING / 文本函数兜底）：null → 空串，Date →
     * yyyy-MM-dd HH:mm:ss，其余 String.valueOf
     */
    public static String stringOf(Object value) {
        if (null == value) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Date date) {
            synchronized (OUTPUT_DATETIME_FORMAT) {
                return OUTPUT_DATETIME_FORMAT.format(date);
            }
        }
        return String.valueOf(value);
    }

    /* ==================== 结果包装 ==================== */

    /**
     * 结果包装：null → AviatorNil；String/Boolean/Long/Double 用原生 Aviator 类型，
     * 其余对象（Date/集合/Map/BigDecimal 等）经 AviatorRuntimeJavaType 承载
     */
    public static AviatorObject wrap(Object value) {
        if (null == value) {
            return AviatorNil.NIL;
        }
        if (value instanceof AviatorObject aviatorObject) {
            return aviatorObject;
        }
        if (value instanceof String text) {
            return new AviatorString(text);
        }
        if (value instanceof Boolean bool) {
            return AviatorBoolean.valueOf(bool);
        }
        if (value instanceof Long longValue) {
            return AviatorLong.valueOf(longValue);
        }
        if (value instanceof Double doubleValue) {
            return AviatorDouble.valueOf(doubleValue);
        }
        return AviatorRuntimeJavaType.valueOf(value);
    }

    /**
     * 数值结果包装（保留整型形态）：BigDecimal 无小数部分且可安全转 long →
     * AviatorLong，否则 AviatorDouble（超大整数经 AviatorRuntimeJavaType 承载）
     */
    public static AviatorObject wrapNumber(BigDecimal number) {
        BigDecimal stripped = number.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            try {
                return AviatorLong.valueOf(stripped.longValueExact());
            } catch (ArithmeticException e) {
                return AviatorRuntimeJavaType.valueOf(number);
            }
        }
        return AviatorDouble.valueOf(number.doubleValue());
    }

    /**
     * 判断数值是否整型形态（Byte/Short/Integer/Long/BigInteger）
     */
    public static boolean isIntegral(Number number) {
        return number instanceof Byte || number instanceof Short || number instanceof Integer
                || number instanceof Long || number instanceof java.math.BigInteger;
    }

    /**
     * 集合/数组/Map 元素个数（COUNT 共用）；null → 0（确定性）；文本按字符数；
     * 其他类型抛 CS_FORMULA_EXECUTE_FAILED
     */
    public static long sizeOf(Object value, String functionCode) {
        if (null == value) {
            return 0L;
        }
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        if (value instanceof String text) {
            return text.length();
        }
        throw executeError(functionCode + " 参数不是可计数的集合/数组/Map/文本");
    }
}

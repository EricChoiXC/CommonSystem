package com.eric.base.sys.formula.enums;

import java.util.Arrays;

/**
 * 公式期望返回类型
 *
 * @author Eric
 * @date 2026/11/18
 * @description 命名公式声明的期望返回类型：执行后由引擎按此适配结果类型
 *              （NUMBER→BigDecimal 规整，类型不符报 CS_FORMULA_TYPE_MISMATCH）。
 *              存 sys_formula.field_return_type 列（枚举名，长度 20）。
 */
public enum SysFormulaReturnType {

    /** 数值（BigDecimal 规整） */
    NUMBER,
    /** 字符串 */
    STRING,
    /** 布尔 */
    BOOLEAN,
    /** 日期 */
    DATE,
    /** 数组（集合函数结果） */
    ARRAY;

    /**
     * 解析返回类型文本，不区分大小写；非法返回 null（调用方报 CS_FORMULA_TYPE_MISMATCH）
     */
    public static SysFormulaReturnType of(String value) {
        if (null == value || value.isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst().orElse(null);
    }
}

package com.eric.base.sys.formula.enums;

import java.util.Arrays;

/**
 * 内置函数分类
 *
 * @author Eric
 * @date 2026/11/18
 * @description 内置函数九类（方案 §2.2，一期范围）：函数元数据的 category 字段取本枚举名，
 *              /functions 接口按此分组透出给前端函数面板；DS_QUERY 归 DATA 类。
 */
public enum SysFormulaFunctionCategory {

    /** 数学计算：ABS、ROUND、CEIL、FLOOR、POWER */
    MATH,
    /** 逻辑判断：IF、AND、OR、NOT、IFNULL */
    LOGIC,
    /** 数值/文本比较：EQ、NE、GT、GTE、LT、LTE */
    COMPARE,
    /** 字符串处理：CONCAT、SUBSTRING、LENGTH、TRIM、REPLACE */
    STRING,
    /** 日期运算：NOW、TODAY、DATEADD、DATEDIFF、YEAR、MONTH */
    DATE,
    /** 明细/数组统计：SUM、AVG、MAX、MIN、COUNT、COUNTIF、SUMIF */
    COLLECTION,
    /** 类型转换：TO_NUMBER、TO_STRING、TO_DATE */
    CONVERT,
    /** 会话上下文：CURRENT_USER、CURRENT_DEPT、CURRENT_ROLE（服务端注入，不信任前端传值） */
    SYSTEM,
    /** 经数据源取数：DS_QUERY（委托 SysDatasourceService#execute） */
    DATA;

    /**
     * 解析函数分类文本，不区分大小写；非法返回 null（调用方报 CS_FORMULA_FUNCTION_NOT_FOUND）
     */
    public static SysFormulaFunctionCategory of(String value) {
        if (null == value || value.isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(category -> category.name().equalsIgnoreCase(value))
                .findFirst().orElse(null);
    }
}

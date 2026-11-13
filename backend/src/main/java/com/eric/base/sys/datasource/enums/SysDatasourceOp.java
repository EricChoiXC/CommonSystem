package com.eric.base.sys.datasource.enums;

import java.util.Arrays;

/**
 * 数据源条件操作符
 *
 * @author Eric
 * @date 2026/11/13
 * @description 固定条件与调用方条件可用的操作符全集；Provider 声明的字段
 *              {@code ops} 白名单取本枚举名称的子集。
 */
public enum SysDatasourceOp {

    /** 等于 */
    EQ,
    /** 不等于 */
    NE,
    /** 模糊匹配（前后 %） */
    LIKE,
    /** 大于 */
    GT,
    /** 大于等于 */
    GE,
    /** 小于 */
    LT,
    /** 小于等于 */
    LE,
    /** 在列表内 */
    IN;

    /**
     * 解析操作符文本，不区分大小写；非法返回 null（调用方报 CS_DATASOURCE_CONDITION_INVALID）
     */
    public static SysDatasourceOp of(String value) {
        if (null == value || value.isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(op -> op.name().equalsIgnoreCase(value))
                .findFirst().orElse(null);
    }
}

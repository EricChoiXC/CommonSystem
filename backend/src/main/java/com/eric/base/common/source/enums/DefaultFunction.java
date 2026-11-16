package com.eric.base.common.source.enums;

/**
 * 函数式默认值枚举：与 {@code @ColumnField#defaultValue()} 字面量默认值互斥
 * （扫描器按哨兵判定，同设即 fail-fast）。
 *
 * @author Eric
 * @date 2026/11/16
 * @description 先例：field_create_time DEFAULT CURRENT_TIMESTAMP、
 *              field_update_time DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP。
 */
public enum DefaultFunction {

    /** 无函数默认值（仅 defaultValue 字面量生效） */
    NONE,

    /** DEFAULT CURRENT_TIMESTAMP */
    CURRENT_TIMESTAMP,

    /** DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP */
    ON_UPDATE_CURRENT_TIMESTAMP
}

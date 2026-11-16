package com.eric.base.common.source.enums;

/**
 * 列类型枚举：注解声明的逻辑类型，由 DDL 生成器映射为 MySQL 8.4 物理类型；
 * 必填无默认，杜绝拼写魔法值。
 *
 * @author Eric
 * @date 2026/11/16
 * @description STRING/DECIMAL 必须显式 length（扫描器校验），其余类型忽略 length。
 */
public enum ColumnType {

    /** VARCHAR({length}) */
    STRING,

    /** TEXT */
    TEXT,

    /** INT */
    INT,

    /** BIGINT */
    BIGINT,

    /** DECIMAL({length},{decimalLength}) */
    DECIMAL,

    /** BIT(1)（先例 field_enable） */
    BOOLEAN,

    /** DATE */
    DATE,

    /** DATETIME */
    DATETIME,

    /** TIMESTAMP */
    TIMESTAMP,

    /** JSON（先例 sys_datasource.field_conditions） */
    JSON
}

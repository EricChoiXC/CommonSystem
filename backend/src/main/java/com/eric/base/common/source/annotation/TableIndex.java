package com.eric.base.common.source.annotation;

import com.eric.base.common.source.enums.IndexType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 索引定义注解：仅作为 {@link TableSource#indexes()} 成员使用，不独立标注——
 * 空 Target 在编译期阻断独立应用（对齐 Spring @ComponentScan.Filter 先例）。
 *
 * @author Eric
 * @date 2026/11/16
 * @description 成员注解（RUNTIME）；索引名表内唯一，列支持组合索引、按数组顺序。
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface TableIndex {

    /** 索引名，表内唯一 */
    String name();

    /** 索引列（支持组合索引，按数组顺序） */
    String[] columns();

    /** 是否唯一索引 */
    boolean unique() default false;

    /** 索引类型（预留 FULLTEXT） */
    IndexType type() default IndexType.BTREE;
}

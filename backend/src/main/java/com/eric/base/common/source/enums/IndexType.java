package com.eric.base.common.source.enums;

/**
 * 索引类型枚举：当前仅 BTREE（MySQL 默认索引类型），FULLTEXT 预留
 * （Out of Scope，见设计方案 §10，不开放注解属性）。
 *
 * @author Eric
 * @date 2026/11/16
 * @description 作为 @TableIndex.type() 的取值域。
 */
public enum IndexType {

    /** BTREE 索引 */
    BTREE
}

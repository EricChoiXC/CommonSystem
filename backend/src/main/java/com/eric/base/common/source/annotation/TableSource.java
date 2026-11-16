package com.eric.base.common.source.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 表定义注解：标注在实体类上，声明该表交由注解维护表结构引擎（common.source）管辖。
 * <p>标注类经 SourceTableScanner 扫描归一为 TableDef，是引擎的唯一比对与执行范围；
 * 引擎只管「注解标注的表」，未标注表一律不触碰。</p>
 * <p>ENGINE=InnoDB / CHARSET=utf8mb4 为引擎内建统一默认，不开放表级属性。</p>
 *
 * @author Eric
 * @date 2026/11/16
 * @description 类级注解（RUNTIME / TYPE）；表名与 COMMENT 必填，索引经
 *              {@link TableIndex} 成员声明（不开放独立标注）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TableSource {

    /** 表名（snake_case，全局唯一），必填 */
    String name();

    /** 表 COMMENT，必填（数据库设计文档同步依赖） */
    String comment();

    /** 索引定义 */
    TableIndex[] indexes() default {};
}

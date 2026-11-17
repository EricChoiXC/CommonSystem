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

    /**
     * 主键列集声明（F19-DOC-005 补充评审新增，2026-11-17）：默认空 = 现行为（BaseModel 内建
     * field_id 主键）；非空 = 显式声明主键列集，引擎不再内建 field_id，主键列本身以
     * {@link ColumnField} 业务列声明（nullable 必须为 false），支持组合主键与单列非
     * field_id 主键；数组顺序即 PRIMARY KEY 列序。仅 BaseModel 开放（MainModel + 非空
     * 一律 fail-fast，见设计方案 §3.3/§3.5 规则 12）
     */
    String[] primaryKeyColumns() default {};
}

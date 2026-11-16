package com.eric.base.common.source.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 逻辑数据源归属声明（分组元数据；引擎仅透传到 TableDef 与报告分组，不影响 DDL）。
 * <p>与取数配置表 sys_datasource 语义无关：本注解声明「表的逻辑归属」，sys_datasource
 * 是「取数配置元数据」，互不依赖。</p>
 *
 * @author Eric
 * @date 2026/11/16
 * @description 类级注解（RUNTIME / TYPE）；逻辑数据源名约定与模块 key 对齐（如 calendar）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataSource {

    /** 逻辑数据源名，约定与模块 key 对齐（如 calendar） */
    String name();
}

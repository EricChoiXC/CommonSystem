package com.eric.base.common.base.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 导入导出列标注注解：标注在各模块自定义的导入导出 VO 类的字段上，
 * 由公共导入导出方案（ImportExportService#importExcel / exportExcel）读取，
 * 完成标题行生成、列序编排、码值转换与导入校验。
 * <p>使用约定（方案设计 5.3）：</p>
 * <ul>
 * <li>只标注在各模块自定义的导入导出 VO 类上，不在 BaseModel / 实体类上标注；</li>
 * <li>工程统一走本注解，不引入 EasyExcel 原生注解；</li>
 * <li>本注解只负责列元数据声明，不参与运行时 JSON 序列化。</li>
 * </ul>
 *
 * @author Eric
 * @date 2026/11/9
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Export {

    /**
     * 中文名称：导入文件标题行的标题。
     * <p>不填写时取 Java 字段名（驼峰原样，如 fieldCode），不转下划线。</p>
     */
    String name() default "";

    /**
     * 字典名：dict.yml 的字典 type_code。
     * <p>导入导出时做码值↔显示值转换：转换映射仅取启用项
     * （SysDictService#itemsByTypeCode 口径，键为 field_value、值为 field_name）。
     * 空串表示不做字典转换。</p>
     */
    String dict() default "";

    /**
     * 是否必填（仅导入校验：null 与空串均视为空）。
     * <p>导出无影响；默认 false。</p>
     */
    boolean required() default false;

    /**
     * 日期时间格式（Date / LocalDateTime 字段生效，String 字段忽略）。
     * <p>默认 yyyy-MM-dd HH:mm:ss。</p>
     */
    String dateFormat() default "yyyy-MM-dd HH:mm:ss";

    /**
     * 排序：导入导出文件中的列序。
     * <p>未设置（-1）的字段按声明顺序追加在有值字段之后；
     * order 相同时按声明顺序稳定排序。</p>
     */
    int order() default -1;
}

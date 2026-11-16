package com.eric.base.sys.datasource.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据源字段定义注解
 *
 * @author Eric
 * @date 2026/11/16
 * @description 条件字段标注在查询条件类（如 SysCalendarQuery）字段上、返回字段标注在
 *              结果 VO（如 SysCalendarVo）字段上（A8，方案 §2.4）；属性与
 *              {@link SysDatasourceFieldDo} 一一镜像，双角色字段两类各标一次，
 *              元数据按角色自然分离（Query 上标 ops、Vo 上标 sortable）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DatasourceField {

    /** 显示名（配置表单与前端表头），必填 */
    String label();

    /** 值类型：STRING / NUMBER / DATE / DATETIME；空 = 按 Java 字段类型推导（显式声明优先且须合法） */
    String type() default "";

    /** 允许的操作符（SysDatasourceOp 名称）；返回字段定义时忽略；条件字段为空 fail-fast */
    String[] ops() default {};

    /** 是否允许字面量值；false 时仅接受运行时变量（如 fieldCreatorId 只收 ${CURRENT_USER_ID}，防越权） */
    boolean literalAllowed() default true;

    /** 是否可作为排序列（供 /query sortField 白名单校验；仅返回字段定义使用） */
    boolean sortable() default false;

    /** 输出顺序（Class.getDeclaredFields 不保证声明序，显式控制；公共字段 fieldId 固定最前） */
    int order() default 0;
}

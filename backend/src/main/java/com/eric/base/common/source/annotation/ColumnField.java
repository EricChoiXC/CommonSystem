package com.eric.base.common.source.annotation;

import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 列定义注解：标注在实体字段上声明业务列。
 * <p>主键与审计公共列由引擎按实体基类模板内建（BaseModel → field_id；MainModel →
 * field_id + 审计五列），不开放标注。</p>
 * <p>MainModel 声明的 fieldName / fieldCode 属业务列且各表长度不一，需要入列的实体
 * 在子类重新声明同名字段并标注本注解（字段遮蔽 + Lombok 读写器覆盖父类实现）；
 * 未重声明的继承字段不进列清单。</p>
 *
 * @author Eric
 * @date 2026/11/16
 * @description 字段级注解（RUNTIME / FIELD）；name / comment / type 必填，
 *              defaultValue 与 defaultFunction 互斥（哨兵判定）。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ColumnField {

    /** 列名（field_ 前缀 snake_case，表内唯一），必填 */
    String name();

    /** 列 COMMENT，必填 */
    String comment();

    /** 列类型枚举，必填（无默认，杜绝拼写魔法值） */
    ColumnType type();

    /** 长度：STRING 必填（≥1）；DECIMAL 表达精度；其余类型忽略 */
    int length() default -1;

    /** 小数位：仅 DECIMAL 使用 */
    int decimalLength() default 0;

    /** 是否可空，默认 false */
    boolean nullable() default false;

    /** 字面量默认值；未设置用 {@link #DEFAULT_NONE} 哨兵（区分「无默认值」与「默认空串」） */
    String defaultValue() default DEFAULT_NONE;

    /** 函数式默认值（先例 DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP），与 defaultValue 互斥 */
    DefaultFunction defaultFunction() default DefaultFunction.NONE;

    /** 哨兵常量：字面量需要该值时须走 defaultFunction 扩展或调整哨兵（实践中不可能出现） */
    String DEFAULT_NONE = "__CS_NONE__";
}

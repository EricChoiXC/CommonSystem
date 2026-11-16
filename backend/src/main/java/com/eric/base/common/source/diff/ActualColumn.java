package com.eric.base.common.source.diff;

import lombok.Getter;
import lombok.ToString;

import java.util.Locale;

/**
 * 实际列模型：information_schema.COLUMNS 行归一产物（SourceSchemaDiffer 读取层），
 * 与注解侧 {@code com.eric.base.common.source.model.ColumnDef} 按列名比对。
 *
 * @author Eric
 * @date 2026/11/16
 * @description 不可变 POJO；dataType / columnType 由读取层归一为小写去空白（MySQL 8.4
 *              返回小写，归一防御漂移）；columnDefault 经读取层归一（SQL NULL 与字符串
 *              "NULL" 均归 null、字符串/时间字面量剥外层引号并反转义）；isOnUpdate 由
 *              EXTRA 派生（DEFAULT_GENERATED 等其它标记忽略）。
 */
@Getter
@ToString
public class ActualColumn {

    /** 列名 */
    private final String name;

    /** DATA_TYPE（小写，类型名比对口径：varchar/text/int/bigint/decimal/bit/date/datetime/timestamp/json） */
    private final String dataType;

    /** COLUMN_TYPE（小写去空白，bit(1) / decimal(p,s) 精细比对口径） */
    private final String columnType;

    /** 是否可空（IS_NULLABLE = YES） */
    private final boolean nullable;

    /** 归一后默认值（无默认为 null；函数式 CURRENT_TIMESTAMP 保留原文供比对） */
    private final String columnDefault;

    /** EXTRA 原文（on update CURRENT_TIMESTAMP / DEFAULT_GENERATED 等标记） */
    private final String extra;

    /** COLUMN_COMMENT */
    private final String comment;

    /** CHARACTER_MAXIMUM_LENGTH（非字符列为 null） */
    private final Integer charMaxLength;

    /** NUMERIC_PRECISION（非精确数值列为 null） */
    private final Integer numericPrecision;

    /** NUMERIC_SCALE（非精确数值列为 null） */
    private final Integer numericScale;

    public ActualColumn(String name, String dataType, String columnType, boolean nullable, String columnDefault,
                        String extra, String comment, Integer charMaxLength, Integer numericPrecision, Integer numericScale) {
        this.name = name;
        this.dataType = dataType;
        this.columnType = columnType;
        this.nullable = nullable;
        this.columnDefault = columnDefault;
        this.extra = extra;
        this.comment = comment;
        this.charMaxLength = charMaxLength;
        this.numericPrecision = numericPrecision;
        this.numericScale = numericScale;
    }

    /**
     * EXTRA 是否含 on update CURRENT_TIMESTAMP 子句（大小写不敏感；
     * DEFAULT_GENERATED 等其它标记忽略）
     *
     * @return 实际列携带 ON UPDATE 子句为 true
     */
    public boolean isOnUpdate() {
        return null != extra && extra.toLowerCase(Locale.ROOT).contains("on update current_timestamp");
    }
}

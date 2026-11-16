package com.eric.base.common.source.model;

import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import lombok.Getter;
import lombok.ToString;

/**
 * 列定义模型：SourceTableScanner 归一产物，承载单列的类型 / 可空 / 默认值语义，
 * 供 SourceDdlGenerator 渲染 DDL 与 SourceSchemaDiffer 比对（information_schema.COLUMNS）。
 *
 * @author Eric
 * @date 2026/11/16
 * @description 不可变 POJO；length / decimalLength 仅 STRING / DECIMAL 有意义，其余类型
 *              归一为 0；defaultValue 无默认为 null、与 defaultFunction 互斥（哨兵判定由
 *              扫描器完成）；primaryKey 仅供公共列模板内建 field_id 使用，业务列恒为 false。
 */
@Getter
@ToString
public class ColumnDef {

    /** 列名（field_ 前缀 snake_case，表内唯一） */
    private final String name;

    /** 列 COMMENT */
    private final String comment;

    /** 列类型（逻辑类型，DDL 生成器映射为 MySQL 8.4 物理类型） */
    private final ColumnType type;

    /** 长度：STRING 表字符长度、DECIMAL 表精度；其余类型归一为 0 */
    private final int length;

    /** 小数位：仅 DECIMAL 有意义，其余类型归一为 0 */
    private final int decimalLength;

    /** 是否可空 */
    private final boolean nullable;

    /** 字面量默认值（无默认为 null；函数式默认值时同为 null） */
    private final String defaultValue;

    /** 函数式默认值（DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP） */
    private final DefaultFunction defaultFunction;

    /** 是否主键（公共列模板内建 field_id 专用，业务列恒为 false） */
    private final boolean primaryKey;

    public ColumnDef(String name, String comment, ColumnType type, int length, int decimalLength,
                     boolean nullable, String defaultValue, DefaultFunction defaultFunction, boolean primaryKey) {
        this.name = name;
        this.comment = comment;
        this.type = type;
        this.length = length;
        this.decimalLength = decimalLength;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
        this.defaultFunction = defaultFunction;
        this.primaryKey = primaryKey;
    }
}

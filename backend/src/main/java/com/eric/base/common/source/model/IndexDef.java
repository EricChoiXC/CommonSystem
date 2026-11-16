package com.eric.base.common.source.model;

import com.eric.base.common.source.enums.IndexType;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * 索引定义模型：SourceTableScanner 归一产物，供 SourceDdlGenerator 渲染 ADD INDEX
 * 与 SourceSchemaDiffer 比对（information_schema.STATISTICS）。
 *
 * @author Eric
 * @date 2026/11/16
 * @description 不可变 POJO；columns 为组合索引列（按数组顺序），入参拷贝持有；
 *              主键不在此清单（公共列模板内建 field_id 由 ColumnDef#primaryKey 表达）。
 */
@Getter
@ToString
public class IndexDef {

    /** 索引名（表内唯一） */
    private final String name;

    /** 索引列（组合索引按数组顺序，不可变） */
    private final List<String> columns;

    /** 是否唯一索引 */
    private final boolean unique;

    /** 索引类型（当前仅 BTREE，FULLTEXT 预留） */
    private final IndexType type;

    public IndexDef(String name, List<String> columns, boolean unique, IndexType type) {
        this.name = name;
        this.columns = List.copyOf(columns);
        this.unique = unique;
        this.type = type;
    }
}

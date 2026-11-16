package com.eric.base.common.source.diff;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * 实际索引模型：information_schema.STATISTICS 行按 (TABLE_NAME, INDEX_NAME) 聚合产物
 * （SourceSchemaDiffer 读取层），与注解侧 {@code com.eric.base.common.source.model.IndexDef}
 * 按索引名比对。
 *
 * @author Eric
 * @date 2026/11/16
 * @description 不可变 POJO；主键（PRIMARY）不进本模型——公共列模板内建 field_id 主键由
 *              注解侧 ColumnDef#primaryKey 表达，聚合层直接跳过；columns 为组合索引列
 *              （按 SEQ_IN_INDEX 有序），入参拷贝持有。
 */
@Getter
@ToString
public class ActualIndex {

    /** 索引名（表内唯一） */
    private final String name;

    /** 是否唯一索引（NON_UNIQUE = 0） */
    private final boolean unique;

    /** 索引列（组合索引按 SEQ_IN_INDEX 有序，不可变） */
    private final List<String> columns;

    /** INDEX_TYPE（BTREE / FULLTEXT 等） */
    private final String indexType;

    public ActualIndex(String name, boolean unique, List<String> columns, String indexType) {
        this.name = name;
        this.unique = unique;
        this.columns = List.copyOf(columns);
        this.indexType = indexType;
    }
}

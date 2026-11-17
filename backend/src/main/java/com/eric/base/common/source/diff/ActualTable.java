package com.eric.base.common.source.diff;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * 实际表模型：information_schema 三源（TABLES / COLUMNS / STATISTICS）归一聚合产物
 * （SourceSchemaDiffer 读取层），与注解侧 {@code com.eric.base.common.source.model.TableDef}
 * 按表名比对——SourceSchemaDiffer#compare 的纯函数输入。
 *
 * @author Eric
 * @date 2026/11/16
 * @description 不可变 POJO；charset 不单独建字段，由 tableCollation 前缀推导
 *              （utf8mb4_0900_ai_ci → utf8mb4）；columns 沿 ORDINAL_POSITION 有序、
 *              indexes 沿索引名有序（均由读取层 SQL ORDER BY 保证）；primaryKeyColumns
 *              为实际主键列集（STATISTICS INDEX_NAME='PRIMARY' 行按 SEQ_IN_INDEX 排序，
 *              F19-DOC-005 主键读取源，与二级索引三源读取同一通道；PRIMARY 不进 indexes，
 *              无主键表为空集）。
 */
@Getter
@ToString
public class ActualTable {

    /** 表名 */
    private final String name;

    /** ENGINE（InnoDB 等） */
    private final String engine;

    /** TABLE_COMMENT */
    private final String tableComment;

    /** TABLE_COLLATION（字符集排序规则，前缀即字符集名） */
    private final String tableCollation;

    /** 列清单（ORDINAL_POSITION 有序） */
    private final List<ActualColumn> columns;

    /** 索引清单（主键外的二级索引，索引名有序） */
    private final List<ActualIndex> indexes;

    /** 主键列集（SEQ_IN_INDEX 有序，组合主键按序；无主键表为空集） */
    private final List<String> primaryKeyColumns;

    public ActualTable(String name, String engine, String tableComment, String tableCollation,
                       List<ActualColumn> columns, List<ActualIndex> indexes, List<String> primaryKeyColumns) {
        this.name = name;
        this.engine = engine;
        this.tableComment = tableComment;
        this.tableCollation = tableCollation;
        this.columns = List.copyOf(columns);
        this.indexes = List.copyOf(indexes);
        this.primaryKeyColumns = List.copyOf(primaryKeyColumns);
    }
}

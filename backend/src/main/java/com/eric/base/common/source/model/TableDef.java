package com.eric.base.common.source.model;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * 表定义模型：注解声明层（@TableSource / @DataSource / @ColumnField / @TableIndex）经
 * SourceTableScanner 扫描归一的结果，是引擎比对（SourceSchemaDiffer）与 DDL 生成
 * （SourceDdlGenerator）的唯一输入。
 *
 * @author Eric
 * @date 2026/11/16
 * @description 不可变 POJO；列清单含公共列模板内建列（缺省 field_id 主键固定最前、MainModel
 *              审计五列固定最后；@TableSource.primaryKeyColumns 非空时无任何内建列，全部列
 *              走业务列标注）；primaryKeyColumns 为注解声明的主键列集（缺省空集，保留声明
 *              原样，数组顺序即 PRIMARY KEY 列序），有效主键统一经
 *              {@link #getEffectivePrimaryKeyColumns()} 派生——声明非空用声明列集，否则
 *              公共列模板内建 field_id 单列；dataSourceName 为 @DataSource 逻辑数据源名
 *              （可空，仅透传到报告分组，不影响 DDL）；ENGINE=InnoDB / CHARSET=utf8mb4 为
 *              引擎内建统一默认，不在此模型表达。
 */
@Getter
@ToString
public class TableDef {

    /** 公共列模板内建主键列名（有效主键缺省派生口径，对齐 SourceTableScanner 内建模板） */
    private static final String BUILTIN_PRIMARY_KEY_COLUMN = "field_id";

    /** 表名（snake_case，全局唯一） */
    private final String name;

    /** 表 COMMENT */
    private final String comment;

    /** 逻辑数据源名（@DataSource，未标注为 null） */
    private final String dataSourceName;

    /** 列清单（含顺序：内建主键最前、业务列声明序、内建审计列最后；primaryKeyColumns 非空时全部为业务列） */
    private final List<ColumnDef> columns;

    /** 索引清单（主键外的全部二级索引） */
    private final List<IndexDef> indexes;

    /** 注解声明的主键列集（@TableSource.primaryKeyColumns，缺省空集；声明原样，数组顺序即 PRIMARY KEY 列序） */
    private final List<String> primaryKeyColumns;

    public TableDef(String name, String comment, String dataSourceName,
                    List<ColumnDef> columns, List<IndexDef> indexes, List<String> primaryKeyColumns) {
        this.name = name;
        this.comment = comment;
        this.dataSourceName = dataSourceName;
        this.columns = List.copyOf(columns);
        this.indexes = List.copyOf(indexes);
        this.primaryKeyColumns = List.copyOf(primaryKeyColumns);
    }

    /**
     * 有效主键列集（派生口径，F19-DOC-005）：注解声明非空用声明列集（组合/单列按声明序），
     * 否则公共列模板内建 field_id 单列——DDL 生成（PRIMARY KEY 渲染）与 diff 主键比对
     * 统一经此取口径，缺省行为与扩展前逐字节一致
     *
     * @return 有效主键列集（不可变）
     */
    public List<String> getEffectivePrimaryKeyColumns() {
        return primaryKeyColumns.isEmpty() ? List.of(BUILTIN_PRIMARY_KEY_COLUMN) : primaryKeyColumns;
    }
}

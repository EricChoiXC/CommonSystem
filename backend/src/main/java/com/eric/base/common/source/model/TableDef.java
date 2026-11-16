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
 * @description 不可变 POJO；列清单含公共列模板内建列（field_id 主键固定最前、MainModel
 *              审计五列固定最后）；dataSourceName 为 @DataSource 逻辑数据源名（可空，
 *              仅透传到报告分组，不影响 DDL）；ENGINE=InnoDB / CHARSET=utf8mb4 为引擎
 *              内建统一默认，不在此模型表达。
 */
@Getter
@ToString
public class TableDef {

    /** 表名（snake_case，全局唯一） */
    private final String name;

    /** 表 COMMENT */
    private final String comment;

    /** 逻辑数据源名（@DataSource，未标注为 null） */
    private final String dataSourceName;

    /** 列清单（含顺序：内建主键最前、业务列声明序、内建审计列最后） */
    private final List<ColumnDef> columns;

    /** 索引清单（主键外的全部二级索引） */
    private final List<IndexDef> indexes;

    public TableDef(String name, String comment, String dataSourceName,
                    List<ColumnDef> columns, List<IndexDef> indexes) {
        this.name = name;
        this.comment = comment;
        this.dataSourceName = dataSourceName;
        this.columns = List.copyOf(columns);
        this.indexes = List.copyOf(indexes);
    }
}

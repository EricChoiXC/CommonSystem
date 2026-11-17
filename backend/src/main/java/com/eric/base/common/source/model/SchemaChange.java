package com.eric.base.common.source.model;

import com.eric.base.common.source.enums.ChangeLevel;
import lombok.Getter;
import lombok.ToString;

/**
 * 结构变更条目模型：SourceSchemaDiffer 比对产出（同步语义矩阵分级，设计方案 §4.3），
 * 每条对应矩阵一行的判定结果，供同步引擎（CommonSourceSyncService）按级别分流执行。
 *
 * @author Eric
 * @date 2026/11/16
 * @description 不可变 POJO。changeType 取本类 TYPE 常量（与 §4.3 矩阵行逐一对应，
 *              矩阵未覆盖的补充项单独命名并注明）；ddl 文本一律由 SourceDdlGenerator
 *              生成（Differ 不拼 SQL），告警类条目（L2/L3 及被阻断的 L1）为 null。
 *              引擎永不执行 DROP TABLE / DROP COLUMN / RENAME——本模型承载的任何 ddl
 *              文本均不含删除/改名类语句，由 SourceDdlGenerator 方法面硬收口。
 */
@Getter
@ToString
public class SchemaChange {

    /** 缺失表（矩阵「缺失表」）：L1，DDL = CREATE TABLE */
    public static final String MISSING_TABLE = "MISSING_TABLE";

    /** 缺失列（矩阵「缺失列」）：L1，DDL = ADD COLUMN */
    public static final String MISSING_COLUMN = "MISSING_COLUMN";

    /** 缺失索引（矩阵「缺失索引（含唯一）」）：L1，DDL = ADD INDEX */
    public static final String MISSING_INDEX = "MISSING_INDEX";

    /** VARCHAR 扩长（矩阵「VARCHAR 扩长」，注解 length > 实际）：L1，DDL = MODIFY COLUMN */
    public static final String WIDEN_LENGTH = "WIDEN_LENGTH";

    /** DECIMAL 精度提升（矩阵「DECIMAL 精度提升」，注解精度/小数位 > 实际）：L1，DDL = MODIFY COLUMN */
    public static final String WIDEN_PRECISION = "WIDEN_PRECISION";

    /** 列默认值变更（矩阵「列默认值变更」，字面量或函数式；去除默认值走 MODIFY 全量重定义）：L1 */
    public static final String CHANGE_DEFAULT = "CHANGE_DEFAULT";

    /** ON UPDATE CURRENT_TIMESTAMP 子句变更（「列默认值变更」行任务口径子项，走 MODIFY COLUMN 全量重定义）：L1 */
    public static final String CHANGE_ON_UPDATE = "CHANGE_ON_UPDATE";

    /** 列 COMMENT 变更（矩阵「列 COMMENT 变更」）：L1，DDL = MODIFY COLUMN 完整列定义重写 */
    public static final String CHANGE_COLUMN_COMMENT = "CHANGE_COLUMN_COMMENT";

    /** 表 COMMENT 变更（矩阵「表 COMMENT 变更」）：L1，DDL = ALTER TABLE COMMENT */
    public static final String CHANGE_TABLE_COMMENT = "CHANGE_TABLE_COMMENT";

    /** 列可空收紧（矩阵「列可空收紧」，实际 NULL → 注解 NOT NULL）：L2，无 DDL */
    public static final String TIGHTEN_NULLABLE = "TIGHTEN_NULLABLE";

    /** 列可空放宽（实际 NOT NULL → 注解 NULL，无损变更；矩阵未覆盖的补充项）：L1，DDL = MODIFY COLUMN */
    public static final String LOOSEN_NULLABLE = "LOOSEN_NULLABLE";

    /** 长度收窄 / 类型不兼容（矩阵「长度收窄 / 类型不兼容变更」，映射不等价）：L3，无 DDL */
    public static final String SHRINK_OR_INCOMPATIBLE = "SHRINK_OR_INCOMPATIBLE";

    /** 同表同时出现缺失列 + 孤儿列（矩阵「疑似改名/搬家」）：L3，无 DDL，改名对进明细 */
    public static final String RENAME_SUSPECT = "RENAME_SUSPECT";

    /** 注解中删除的字段（矩阵「注解中删除的字段」，实际孤儿列）：L2，无 DDL */
    public static final String ORPHAN_COLUMN = "ORPHAN_COLUMN";

    /** 索引定义差异（矩阵「索引定义差异」，列集/唯一性/类型）：L2，无 DDL */
    public static final String INDEX_DIFF = "INDEX_DIFF";

    /** 实际多余索引 / 多余索引名（矩阵「实际多余索引」）：L2，无 DDL */
    public static final String EXTRA_INDEX = "EXTRA_INDEX";

    /** ENGINE / CHARSET 与内建默认不一致（矩阵「ENGINE / CHARSET 与内建默认不一致」）：L2，无 DDL */
    public static final String ENGINE_CHARSET_DIFF = "ENGINE_CHARSET_DIFF";

    /** 主键不一致（矩阵「实际主键（列集/顺序）与注解声明不一致」，F19-DOC-005 新增行）：L2，无 DDL，引擎永不修改主键 */
    public static final String PRIMARY_KEY_MISMATCH = "PRIMARY_KEY_MISMATCH";

    /** managed 历史表失去标注（矩阵「managed 表失去标注」，疑似误删注解）：L2，无 DDL */
    public static final String MANAGED_ORPHAN_TABLE = "MANAGED_ORPHAN_TABLE";

    /** 分级（L1 缺失/无损、L2 歧义告警、L3 数据丢失风险） */
    private final ChangeLevel level;

    /** 变更类型（本类常量之一，与设计方案 §4.3 矩阵行对应） */
    private final String changeType;

    /** 涉及表名 */
    private final String tableName;

    /** 涉及列名（表级 / 索引级条目为 null） */
    private final String columnName;

    /** 涉及索引名（表级 / 列级条目为 null） */
    private final String indexName;

    /** 明细说明（两侧值与处理指引，审计报告与 WARN 日志直接消费） */
    private final String detail;

    /** DDL 文本（一律由 SourceDdlGenerator 生成；告警类条目为 null） */
    private final String ddl;

    public SchemaChange(ChangeLevel level, String changeType, String tableName,
                        String columnName, String indexName, String detail, String ddl) {
        this.level = level;
        this.changeType = changeType;
        this.tableName = tableName;
        this.columnName = columnName;
        this.indexName = indexName;
        this.detail = detail;
        this.ddl = ddl;
    }
}

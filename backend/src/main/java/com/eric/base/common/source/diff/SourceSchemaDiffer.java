package com.eric.base.common.source.diff;

import com.eric.base.common.source.ddl.SourceDdlGenerator;
import com.eric.base.common.source.enums.ChangeLevel;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.model.ColumnDef;
import com.eric.base.common.source.model.IndexDef;
import com.eric.base.common.source.model.SchemaChange;
import com.eric.base.common.source.model.TableDef;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 表结构比对器（information_schema 三源读取 + 语义矩阵分级）
 *
 * @author Eric
 * @date 2026/11/16
 * @description 承载设计方案 §4.2/§4.3/§4.4：information_schema 三源读取
 *              （TABLES：TABLE_NAME/ENGINE/TABLE_COMMENT/TABLE_COLLATION；COLUMNS：
 *              COLUMN_NAME/DATA_TYPE/COLUMN_TYPE/IS_NULLABLE/COLUMN_DEFAULT/EXTRA/
 *              COLUMN_COMMENT/CHARACTER_MAXIMUM_LENGTH/NUMERIC_PRECISION/NUMERIC_SCALE；
 *              STATISTICS：INDEX_NAME/NON_UNIQUE/SEQ_IN_INDEX/COLUMN_NAME/INDEX_TYPE），
 *              与注解侧 TableDef 按名比对产出 {@link SchemaChange} 列表。
 *              <p>读取层用构造注入的 Spring JdbcTemplate（mybatis starter 已带
 *              spring-jdbc），三源均整库读取（WHERE TABLE_SCHEMA = DATABASE() 参数化
 *              收口当前库）后内存过滤——比对范围 = 标注表 ∪ managed 历史表，21+ 表开销
 *              毫秒级（§4.2），一次三查询避免逐表 N×3 次往返，managed 孤儿表告警也依赖
 *              全库表清单交集。刻意不注入 javax.sql.DataSource（与 @DataSource 注解
 *              同名冲突，评审确认冲突面收口）。注入形态取 ObjectProvider 惰性构造注入：
 *              运行容器经 mybatis starter 自动装配必含 JdbcTemplate；排除数据源的测试
 *              上下文装配本 Bean 时以 null 注入不阻断加载，仅 {@link #loadActualTables()}
 *              连库读取时显式校验。</p>
 *              <p>比对核心 {@link #compare(TableDef, ActualTable)} 是纯函数：不依赖
 *              数据库与 JdbcTemplate，ActualTable/ActualColumn/ActualIndex 为本包内建
 *              归一模型，单测直接构造两侧对象驱动。</p>
 *              <p>语义矩阵（§4.3）逐行落地：缺失表/列/索引、VARCHAR 扩长、DECIMAL 精度
 *              提升、默认值变更（含 ON UPDATE 子句）、列/表 COMMENT 变更 → L1；
 *              可空收紧、孤儿列、索引差异、多余索引、ENGINE/CHARSET 不一致、主键不一致
 *              （实际主键列集/顺序与有效主键不符，F19-DOC-005 新增行，引擎永不修改主键）、
 *              managed 孤儿表 → L2；长度收窄/类型不兼容、同表缺失列+孤儿列疑似改名 → L3。
 *              类型映射（注解 ColumnType ↔ 实际 DATA_TYPE）：STRING↔varchar、TEXT↔text、
 *              INT↔int、BIGINT↔bigint、DECIMAL↔decimal（比 NUMERIC_PRECISION/SCALE）、
 *              BOOLEAN↔bit（COLUMN_TYPE 比对 bit(1)）、DATE↔date、DATETIME↔datetime、
 *              TIMESTAMP↔timestamp、JSON↔json。</p>
 *              <p>硬约束：本类永不生成 DROP TABLE / DROP COLUMN / RENAME——DDL 文本一律
 *              从 {@link SourceDdlGenerator} 取（其方法面无删除/改名语句），告警类条目
 *              ddl 为 null；managed 表（§4.4）失去标注时永不执行任何 DDL。</p>
 *              <p>实现口径补充（对 §4.3 的定稿细化，主控回写依据）：</p>
 *              <ul>
 *              <li>列可空放宽（实际 NOT NULL → 注解 NULL）矩阵未覆盖：按无损变更落
 *                  LOOSEN_NULLABLE L1（MODIFY 全量重定义）；</li>
 *              <li>ON UPDATE 子句变更落 CHANGE_ON_UPDATE L1（MODIFY 全量重定义，任务口径）；</li>
 *              <li>同列共存差异的 DDL 阻断：L3（收窄/不兼容）时该列全部 DDL 暂缓（整列
 *                  人工裁决）；L2（可空收紧）时仅阻断会隐式执行收紧的 MODIFY 全量重定义类
 *                  DDL（扩长/精度/ON UPDATE/注释/去默认），SET DEFAULT 仅动默认值子句、
 *                  不动可空，保留执行——否则 L1 MODIFY 会隐式执行 L2 收紧动作，违背
 *                  「L2 任何模式只告警」；</li>
 *              <li>ENGINE/CHARSET 与内建默认不一致合并为一条 ENGINE_CHARSET_DIFF L2；</li>
 *              <li>managed 孤儿表若已被人工删除（库中不存在）则无结构可比，不产告警。</li>
 *              </ul>
 */
@Component
public class SourceSchemaDiffer {

    /** information_schema.TABLES 读取（整库，§4.2 读取源一） */
    static final String SQL_TABLES = """
            SELECT TABLE_NAME, ENGINE, TABLE_COMMENT, TABLE_COLLATION
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()""";

    /** information_schema.COLUMNS 读取（整库按表内列序，§4.2 读取源二） */
    static final String SQL_COLUMNS = """
            SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
                   EXTRA, COLUMN_COMMENT, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            ORDER BY TABLE_NAME, ORDINAL_POSITION""";

    /** information_schema.STATISTICS 读取（整库按索引列序，§4.2 读取源三） */
    static final String SQL_STATISTICS = """
            SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, INDEX_TYPE
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
            ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""";

    /** 内建 ENGINE 默认（设计方案 §3.1：不开放表级定制） */
    private static final String DEFAULT_ENGINE = "innodb";

    /** 内建 CHARSET 默认（collation 前缀比对） */
    private static final String DEFAULT_CHARSET = "utf8mb4";

    /** 主键索引名（STATISTICS 中主键读取源：PRIMARY 行按 SEQ_IN_INDEX 排序取列集，F19-DOC-005；
     *  PRIMARY 不进二级索引比对范围——不参与缺失/多余索引比对） */
    private static final String PRIMARY_INDEX = "PRIMARY";

    /** BTREE 索引类型（注解侧唯一合法类型） */
    private static final String BTREE = "btree";

    /** 产出排序：表名 → 列名 → 索引名 → 变更类型 → 明细（null 列/索引名排最前，全键确定序） */
    private static final Comparator<SchemaChange> CHANGE_ORDER = Comparator
            .comparing(SchemaChange::getTableName)
            .thenComparing(SchemaChange::getColumnName, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(SchemaChange::getIndexName, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(SchemaChange::getChangeType)
            .thenComparing(SchemaChange::getDetail);

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造注入 JdbcTemplate（ObjectProvider 惰性形态）：运行容器必含 JdbcTemplate；
     * 排除数据源的测试上下文装配时 getIfAvailable 返回 null，不阻断 Bean 加载，
     * 连库读取由 {@link #loadActualTables()} 显式校验
     *
     * @param jdbcTemplateProvider JdbcTemplate 提供者
     */
    public SourceSchemaDiffer(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    /**
     * 比对主入口：information_schema 三源整库读取后与标注表逐表比对（设计方案 §4.2/§4.4）。
     * 标注表在实际库中缺失 → MISSING_TABLE L1（CREATE TABLE）；managed 历史表（不在标注
     * 清单且库中仍存在）→ MANAGED_ORPHAN_TABLE L2 告警（永不执行任何 DDL）；产出按表名 /
     * 列名升序稳定排序
     *
     * @param annotatedTables       标注表定义清单（SourceTableScanner 产物）
     * @param managedHistoryTables  managed 历史表名（common_source_sync_log.field_tables
     *                              出现过的历史标注表；空集/null 表示无历史表）
     * @return 变更条目列表（不可变，稳定排序）
     */
    public List<SchemaChange> diff(List<TableDef> annotatedTables, Set<String> managedHistoryTables) {
        Map<String, ActualTable> actualTables = loadActualTables();
        List<SchemaChange> changes = new ArrayList<>();
        Set<String> annotatedNames = new HashSet<>();
        for (TableDef table : annotatedTables) {
            annotatedNames.add(table.getName());
            ActualTable actual = actualTables.get(table.getName());
            if (null == actual) {
                changes.add(new SchemaChange(ChangeLevel.L1, SchemaChange.MISSING_TABLE, table.getName(),
                        null, null, "标注表在实际库中不存在，按注解全量建表",
                        SourceDdlGenerator.createTable(table)));
            } else {
                changes.addAll(compare(table, actual));
            }
        }
        for (ActualTable actual : actualTables.values()) {
            if (!annotatedNames.contains(actual.getName())
                    && null != managedHistoryTables && managedHistoryTables.contains(actual.getName())) {
                changes.add(new SchemaChange(ChangeLevel.L2, SchemaChange.MANAGED_ORPHAN_TABLE, actual.getName(),
                        null, null, "managed 历史表失去 @TableSource 标注（疑似误删注解），引擎永不执行任何 DDL，"
                        + "恢复标注后自动重新纳入比对；如确认废弃请人工处置", null));
            }
        }
        changes.sort(CHANGE_ORDER);
        return List.copyOf(changes);
    }

    /**
     * information_schema 三源读取：整库读取（WHERE TABLE_SCHEMA = DATABASE()）后内存
     * 组装为 ActualTable 归一模型
     *
     * @return 实际表模型（key = 表名）
     */
    public Map<String, ActualTable> loadActualTables() {
        if (null == jdbcTemplate) {
            throw new IllegalStateException("Spring JdbcTemplate 不可用（数据源未装配），无法读取 information_schema");
        }
        return assemble(jdbcTemplate.queryForList(SQL_TABLES),
                jdbcTemplate.queryForList(SQL_COLUMNS),
                jdbcTemplate.queryForList(SQL_STATISTICS));
    }

    /**
     * 比对核心（纯函数）：注解表定义 vs 实际表结构，语义矩阵 §4.3 逐行判定，
     * 不依赖数据库——单测直接构造两侧对象驱动
     *
     * @param expected 注解表定义（SourceTableScanner 归一产物）
     * @param actual   实际表结构（information_schema 归一产物）
     * @return 变更条目列表（按表名/列名升序稳定排序；完全一致时为空）
     */
    public List<SchemaChange> compare(TableDef expected, ActualTable actual) {
        List<SchemaChange> changes = new ArrayList<>();
        if (!expected.getName().equals(actual.getName())) {
            throw new IllegalStateException("比对表名不一致: " + expected.getName() + " vs " + actual.getName());
        }
        // 表 COMMENT 变更（矩阵 L1）
        if (!commentEquals(expected.getComment(), actual.getTableComment())) {
            changes.add(new SchemaChange(ChangeLevel.L1, SchemaChange.CHANGE_TABLE_COMMENT, expected.getName(),
                    null, null, "表 COMMENT 变更：注解 '" + expected.getComment() + "' ↔ 实际 '"
                    + actual.getTableComment() + "'", SourceDdlGenerator.alterTableComment(expected)));
        }
        // ENGINE / CHARSET 与内建默认不一致（矩阵 L2）
        compareEngineAndCharset(expected, actual, changes);
        // 主键比对（矩阵「实际主键（列集/顺序）与注解声明不一致」，F19-DOC-005 新增行）：
        // L2 告警不执行（引擎永不修改主键）
        comparePrimaryKey(expected, actual, changes);
        // 列比对：缺失列收集后统一产出（改名检测需要全量清单）
        Map<String, ActualColumn> actualColumns = new HashMap<>();
        for (ActualColumn column : actual.getColumns()) {
            actualColumns.put(column.getName(), column);
        }
        List<ColumnDef> missingColumns = new ArrayList<>();
        for (ColumnDef column : expected.getColumns()) {
            ActualColumn running = actualColumns.get(column.getName());
            if (null == running) {
                missingColumns.add(column);
            } else {
                compareColumn(expected, column, running, changes);
            }
        }
        for (ColumnDef column : missingColumns) {
            changes.add(new SchemaChange(ChangeLevel.L1, SchemaChange.MISSING_COLUMN, expected.getName(),
                    column.getName(), null, "注解声明列在实际库中不存在", SourceDdlGenerator.addColumn(expected, column)));
        }
        Set<String> expectedColumnNames = expected.getColumns().stream()
                .map(ColumnDef::getName).collect(Collectors.toSet());
        List<ActualColumn> orphanColumns = actual.getColumns().stream()
                .filter(column -> !expectedColumnNames.contains(column.getName())).toList();
        if (!missingColumns.isEmpty() && !orphanColumns.isEmpty()) {
            // 同表同时出现缺失列 + 孤儿列：疑似改名/搬家 → L3（缺失列仍报 L1，孤儿列并入本条不再单独报 ORPHAN_COLUMN）
            changes.add(renameSuspect(expected, missingColumns, orphanColumns));
        } else {
            for (ActualColumn orphan : orphanColumns) {
                changes.add(new SchemaChange(ChangeLevel.L2, SchemaChange.ORPHAN_COLUMN, expected.getName(),
                        orphan.getName(), null, "注解中不存在该列（注解已删除或历史遗留），引擎永不 DROP COLUMN；"
                        + "如确认废弃请人工处置", null));
            }
        }
        // 索引比对
        Map<String, ActualIndex> actualIndexes = new HashMap<>();
        for (ActualIndex index : actual.getIndexes()) {
            actualIndexes.put(index.getName(), index);
        }
        for (IndexDef index : expected.getIndexes()) {
            ActualIndex running = actualIndexes.get(index.getName());
            if (null == running) {
                changes.add(new SchemaChange(ChangeLevel.L1, SchemaChange.MISSING_INDEX, expected.getName(),
                        null, index.getName(), "缺失索引：" + describe(index),
                        SourceDdlGenerator.addIndex(expected, index)));
            } else {
                compareIndex(expected, index, running, changes);
            }
        }
        Set<String> expectedIndexNames = expected.getIndexes().stream()
                .map(IndexDef::getName).collect(Collectors.toSet());
        for (ActualIndex running : actual.getIndexes()) {
            if (!expectedIndexNames.contains(running.getName())) {
                changes.add(new SchemaChange(ChangeLevel.L2, SchemaChange.EXTRA_INDEX, expected.getName(),
                        null, running.getName(), "实际库存在注解未声明的索引 " + running.getName() + " ("
                        + String.join(", ", running.getColumns()) + ") unique=" + running.isUnique()
                        + " type=" + running.getIndexType() + "，引擎不删索引", null));
            }
        }
        changes.sort(CHANGE_ORDER);
        return changes;
    }

    /* ==================== 内部：矩阵逐行判定 ==================== */

    /**
     * 单列比对（矩阵行 4~10）：类型等价 → 长度/精度（扩长 L1 / 收窄 L3）→ 可空
     * （收紧 L2 / 放宽 L1）→ ON UPDATE 子句与默认值（L1）→ COMMENT（L1）；
     * 类型映射不等价直接落 L3 并跳过该列其余维度（以人工处置为准，避免噪音）
     */
    private void compareColumn(TableDef table, ColumnDef expected, ActualColumn actual, List<SchemaChange> changes) {
        if (!typeCompatible(expected, actual)) {
            changes.add(new SchemaChange(ChangeLevel.L3, SchemaChange.SHRINK_OR_INCOMPATIBLE, table.getName(),
                    expected.getName(), null, "类型不兼容：注解 " + expected.getType() + " ↔ 实际 "
                    + actual.getColumnType() + "（映射不等价，引擎不改列；请以手工 SQL 或 DataMigration 显式处置）", null));
            return;
        }
        // 长度 / 精度差异（仅类型等价时有意义）
        String shrinkDetail = null;
        String widenType = null;
        String widenDetail = null;
        if (ColumnType.STRING == expected.getType() && null != actual.getCharMaxLength()) {
            if (expected.getLength() < actual.getCharMaxLength()) {
                shrinkDetail = "长度收窄：注解 " + expected.getLength() + " ↔ 实际 " + actual.getCharMaxLength();
            } else if (expected.getLength() > actual.getCharMaxLength()) {
                widenType = SchemaChange.WIDEN_LENGTH;
                widenDetail = "VARCHAR 扩长：注解 " + expected.getLength() + " ↔ 实际 " + actual.getCharMaxLength();
            }
        } else if (ColumnType.DECIMAL == expected.getType() && null != actual.getNumericPrecision()) {
            Integer actualScale = actual.getNumericScale();
            boolean shrink = expected.getLength() < actual.getNumericPrecision()
                    || (null != actualScale && expected.getDecimalLength() < actualScale);
            boolean widen = expected.getLength() > actual.getNumericPrecision()
                    || (null != actualScale && expected.getDecimalLength() > actualScale);
            String annotated = "(" + expected.getLength() + "," + expected.getDecimalLength() + ")";
            String running = "(" + actual.getNumericPrecision() + "," + (null == actualScale ? "?" : actualScale) + ")";
            if (shrink) {
                shrinkDetail = "DECIMAL 精度收窄：注解 " + annotated + " ↔ 实际 " + running;
            } else if (widen) {
                widenType = SchemaChange.WIDEN_PRECISION;
                widenDetail = "DECIMAL 精度提升：注解 " + annotated + " ↔ 实际 " + running;
            }
        }
        // 可空差异
        boolean tighten = actual.isNullable() && !expected.isNullable();
        boolean loosen = !actual.isNullable() && expected.isNullable();
        // ON UPDATE 子句与默认值差异（ON UPDATE 变更涵盖默认值全量重定义，只产一条避免重复执行）
        boolean expectedOnUpdate = DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP == expected.getDefaultFunction();
        boolean onUpdateDiff = expectedOnUpdate != actual.isOnUpdate();
        boolean defaultDiff = isDefaultDiff(expected, actual);
        // COMMENT 差异
        boolean commentDiff = !commentEquals(expected.getComment(), actual.getComment());

        if (tighten) {
            changes.add(new SchemaChange(ChangeLevel.L2, SchemaChange.TIGHTEN_NULLABLE, table.getName(),
                    expected.getName(), null, "列可空收紧：实际 NULL → 注解 NOT NULL（存量 NULL 数据将阻断收紧，"
                    + "请人工裁决；同列 MODIFY 类 DDL 已暂缓防隐式收紧）", null));
        }
        if (loosen) {
            boolean blocked = null != shrinkDetail;
            changes.add(new SchemaChange(ChangeLevel.L1, SchemaChange.LOOSEN_NULLABLE, table.getName(),
                    expected.getName(), null, "列可空放宽：实际 NOT NULL → 注解 NULL（无损变更）"
                    + (blocked ? "（同列存在收窄差异，DDL 暂缓待人工裁决）" : ""),
                    blocked ? null : SourceDdlGenerator.modifyColumn(table, expected)));
        }
        if (null != shrinkDetail) {
            changes.add(new SchemaChange(ChangeLevel.L3, SchemaChange.SHRINK_OR_INCOMPATIBLE, table.getName(),
                    expected.getName(), null, shrinkDetail + "（数据丢失风险，引擎不改列；该列全部 DDL 暂缓，"
                    + "请以手工 SQL 或 DataMigration 显式处置）", null));
        } else if (null != widenType) {
            boolean blocked = tighten;
            changes.add(new SchemaChange(ChangeLevel.L1, widenType, table.getName(),
                    expected.getName(), null, widenDetail
                    + (blocked ? "（同列存在可空收紧差异，DDL 暂缓待人工裁决）" : ""),
                    blocked ? null : SourceDdlGenerator.modifyColumn(table, expected)));
        }
        if (onUpdateDiff) {
            boolean blocked = tighten || null != shrinkDetail;
            changes.add(new SchemaChange(ChangeLevel.L1, SchemaChange.CHANGE_ON_UPDATE, table.getName(),
                    expected.getName(), null, "ON UPDATE CURRENT_TIMESTAMP 子句变更：注解 "
                    + (expectedOnUpdate ? "有" : "无") + " ↔ 实际 " + (actual.isOnUpdate() ? "有" : "无")
                    + "，走 MODIFY COLUMN 全量重定义" + (blocked ? "（同列存在可空收紧/收窄差异，DDL 暂缓待人工裁决）" : ""),
                    blocked ? null : SourceDdlGenerator.modifyColumn(table, expected)));
        } else if (defaultDiff) {
            if (null == expected.getDefaultValue() && DefaultFunction.NONE == expected.getDefaultFunction()) {
                boolean blocked = tighten || null != shrinkDetail;
                changes.add(new SchemaChange(ChangeLevel.L1, SchemaChange.CHANGE_DEFAULT, table.getName(),
                        expected.getName(), null, "列默认值变更：注解 无默认值 ↔ 实际 " + describeDefault(actual)
                        + "，去除默认值走 MODIFY COLUMN 全量重定义"
                        + (blocked ? "（同列存在可空收紧/收窄差异，DDL 暂缓待人工裁决）" : ""),
                        blocked ? null : SourceDdlGenerator.modifyColumn(table, expected)));
            } else {
                // SET DEFAULT 仅变更默认值子句、不动可空：L2 可空收紧不阻断，仅 L3 收窄整列暂缓
                boolean blocked = null != shrinkDetail;
                changes.add(new SchemaChange(ChangeLevel.L1, SchemaChange.CHANGE_DEFAULT, table.getName(),
                        expected.getName(), null, "列默认值变更：注解 " + describeDefault(expected)
                        + " ↔ 实际 " + describeDefault(actual)
                        + (blocked ? "（同列存在收窄差异，DDL 暂缓待人工裁决）" : ""),
                        blocked ? null : SourceDdlGenerator.alterColumnDefault(table, expected)));
            }
        }
        if (commentDiff) {
            boolean blocked = tighten || null != shrinkDetail;
            changes.add(new SchemaChange(ChangeLevel.L1, SchemaChange.CHANGE_COLUMN_COMMENT, table.getName(),
                    expected.getName(), null, "列 COMMENT 变更：注解 '" + expected.getComment() + "' ↔ 实际 '"
                    + actual.getComment() + "'" + (blocked ? "（同列存在可空收紧/收窄差异，DDL 暂缓待人工裁决）" : ""),
                    blocked ? null : SourceDdlGenerator.alterColumnComment(table, expected)));
        }
    }

    /**
     * 主键比对（矩阵「实际主键（列集/顺序）与注解声明不一致」L2，F19-DOC-005 新增行）：
     * 实际主键列集或顺序与有效主键（注解声明非空用声明集、缺省公共列模板内建 field_id）
     * 不一致时仅告警，无 DDL——引擎永不修改主键（任何模式仅告警不执行），改主键走人工 SQL；
     * 缺省表实际主键仍为 field_id 时不产变更
     */
    private void comparePrimaryKey(TableDef expected, ActualTable actual, List<SchemaChange> changes) {
        List<String> annotated = expected.getEffectivePrimaryKeyColumns();
        List<String> running = actual.getPrimaryKeyColumns();
        if (running.equals(annotated)) {
            return;
        }
        changes.add(new SchemaChange(ChangeLevel.L2, SchemaChange.PRIMARY_KEY_MISMATCH, expected.getName(),
                null, null, "主键不一致：注解 (" + String.join(", ", annotated) + ") ↔ 实际 ("
                + String.join(", ", running) + ")，引擎永不修改主键（任何模式仅告警不执行），请以人工 SQL 处置",
                null));
    }

    /**
     * 单索引比对（矩阵「索引定义差异」L2）：列集（组合索引按序）/ 唯一性 / 类型逐项核对，
     * 重建 = 删 + 加，引擎不自动执行
     */
    private void compareIndex(TableDef table, IndexDef expected, ActualIndex actual, List<SchemaChange> changes) {
        List<String> diffs = new ArrayList<>();
        if (!expected.getColumns().equals(actual.getColumns())) {
            diffs.add("列集：注解 (" + String.join(", ", expected.getColumns()) + ") ↔ 实际 ("
                    + String.join(", ", actual.getColumns()) + ")");
        }
        if (expected.isUnique() != actual.isUnique()) {
            diffs.add("唯一性：注解 unique=" + expected.isUnique() + " ↔ 实际 unique=" + actual.isUnique());
        }
        if (null == actual.getIndexType() || !BTREE.equals(actual.getIndexType().toLowerCase(Locale.ROOT))) {
            diffs.add("索引类型：注解 BTREE ↔ 实际 " + actual.getIndexType());
        }
        if (!diffs.isEmpty()) {
            changes.add(new SchemaChange(ChangeLevel.L2, SchemaChange.INDEX_DIFF, table.getName(),
                    null, expected.getName(), "索引定义差异：" + String.join("；", diffs)
                    + "（重建=删+加，引擎不自动执行，请人工处置）", null));
        }
    }

    /**
     * ENGINE / CHARSET 与内建默认不一致（矩阵 L2，一条合并告警）：
     * ENGINE 精确比对 InnoDB（大小写不敏感）、CHARSET 由 collation 前缀推导比对 utf8mb4
     */
    private void compareEngineAndCharset(TableDef table, ActualTable actual, List<SchemaChange> changes) {
        List<String> diffs = new ArrayList<>();
        if (null == actual.getEngine() || !DEFAULT_ENGINE.equalsIgnoreCase(actual.getEngine())) {
            diffs.add("ENGINE=" + actual.getEngine());
        }
        if (!DEFAULT_CHARSET.equals(charsetOf(actual.getTableCollation()))) {
            diffs.add("CHARSET=" + charsetOf(actual.getTableCollation()));
        }
        if (!diffs.isEmpty()) {
            changes.add(new SchemaChange(ChangeLevel.L2, SchemaChange.ENGINE_CHARSET_DIFF, table.getName(),
                    null, null, "实际 " + String.join(" / ", diffs)
                    + " 与内建默认 InnoDB/utf8mb4 不一致，引擎不改建", null));
        }
    }

    /**
     * 改名检测（矩阵 L3）：同表同时出现缺失列 + 孤儿列，按双方声明序配对改名对进明细；
     * 缺失列仍由 compare 逐条报 L1（ADD COLUMN），孤儿列并入本条不再单独报 ORPHAN_COLUMN
     */
    private SchemaChange renameSuspect(TableDef table, List<ColumnDef> missing, List<ActualColumn> orphans) {
        StringBuilder detail = new StringBuilder("同表同时出现缺失列与孤儿列，疑似改名/搬家：");
        int pairCount = Math.min(missing.size(), orphans.size());
        if (pairCount > 0) {
            detail.append("疑似改名对 ");
            for (int i = 0; i < pairCount; i++) {
                if (i > 0) {
                    detail.append('、');
                }
                detail.append('[').append(missing.get(i).getName()).append(" → ")
                        .append(orphans.get(i).getName()).append(']');
            }
            detail.append('；');
        }
        detail.append("缺失列: ").append(missing.stream().map(ColumnDef::getName).collect(Collectors.joining(", ")));
        detail.append("；孤儿列: ").append(orphans.stream().map(ActualColumn::getName).collect(Collectors.joining(", ")));
        detail.append("。改名必须显式 DataMigration（引擎永不 RENAME/DROP），请核对语义后人工处置");
        return new SchemaChange(ChangeLevel.L3, SchemaChange.RENAME_SUSPECT, table.getName(), null, null,
                detail.toString(), null);
    }

    /* ==================== 内部：读取层归一（information_schema 行 → 模型） ==================== */

    /**
     * 三源行组装：TABLES 行建表元数据、COLUMNS 行按 ORDINAL_POSITION 挂列、STATISTICS 行
     * 聚合索引（PRIMARY 跳过、SEQ_IN_INDEX 排序）与主键列集（PRIMARY 行按 SEQ_IN_INDEX
     * 排序，F19-DOC-005 主键读取源），产出表名 → ActualTable
     *
     * @param tableRows  TABLES 查询行
     * @param columnRows COLUMNS 查询行
     * @param indexRows  STATISTICS 查询行
     * @return 实际表模型（key = 表名）
     */
    static Map<String, ActualTable> assemble(List<Map<String, Object>> tableRows,
                                             List<Map<String, Object>> columnRows,
                                             List<Map<String, Object>> indexRows) {
        Map<String, ActualTable> metas = new LinkedHashMap<>();
        Map<String, List<ActualColumn>> columnsByTable = new LinkedHashMap<>();
        for (Map<String, Object> row : tableRows) {
            ActualTable meta = mapTableRow(row);
            if (null != meta) {
                metas.put(meta.getName(), meta);
                columnsByTable.put(meta.getName(), new ArrayList<>());
            }
        }
        for (Map<String, Object> row : columnRows) {
            List<ActualColumn> columns = columnsByTable.get(str(row, "TABLE_NAME"));
            if (null != columns) {
                ActualColumn column = mapColumnRow(row);
                if (null != column) {
                    columns.add(column);
                }
            }
        }
        Map<String, List<ActualIndex>> indexesByTable = groupIndexRows(indexRows);
        Map<String, List<String>> primaryKeysByTable = groupPrimaryKeyRows(indexRows);
        Map<String, ActualTable> tables = new LinkedHashMap<>();
        for (Map.Entry<String, ActualTable> entry : metas.entrySet()) {
            ActualTable meta = entry.getValue();
            tables.put(entry.getKey(), new ActualTable(meta.getName(), meta.getEngine(), meta.getTableComment(),
                    meta.getTableCollation(), columnsByTable.get(entry.getKey()),
                    indexesByTable.getOrDefault(entry.getKey(), List.of()),
                    primaryKeysByTable.getOrDefault(entry.getKey(), List.of())));
        }
        return tables;
    }

    /**
     * TABLES 行 → 表元数据 ActualTable（columns / indexes / 主键待组装，占位空清单）
     */
    static ActualTable mapTableRow(Map<String, Object> row) {
        String name = str(row, "TABLE_NAME");
        if (null == name) {
            return null;
        }
        return new ActualTable(name, str(row, "ENGINE"), str(row, "TABLE_COMMENT"),
                str(row, "TABLE_COLLATION"), List.of(), List.of(), List.of());
    }

    /**
     * STATISTICS 行聚合 → 表名 → 主键列集（F19-DOC-005 主键读取源）：INDEX_NAME='PRIMARY'
     * 行按 SEQ_IN_INDEX 升序拼主键列（与既有索引三源读取同一通道）；无 PRIMARY 行的表
     * 不入 Map（无主键表归一为空集）
     */
    static Map<String, List<String>> groupPrimaryKeyRows(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String tableName = str(row, "TABLE_NAME");
            String indexName = str(row, "INDEX_NAME");
            if (null == tableName || null == indexName || !PRIMARY_INDEX.equalsIgnoreCase(indexName)) {
                continue;
            }
            grouped.computeIfAbsent(tableName, key -> new ArrayList<>()).add(row);
        }
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            List<Map<String, Object>> primaryKeyRows = entry.getValue();
            primaryKeyRows.sort(Comparator.comparingInt(row -> {
                Integer seq = num(row, "SEQ_IN_INDEX");
                return null == seq ? Integer.MAX_VALUE : seq;
            }));
            result.put(entry.getKey(), primaryKeyRows.stream().map(row -> str(row, "COLUMN_NAME")).toList());
        }
        return result;
    }

    /**
     * COLUMNS 行 → ActualColumn 归一：DATA_TYPE / COLUMN_TYPE 小写去空白、IS_NULLABLE=YES →
     * 可空、COLUMN_DEFAULT 经 normalizeColumnDefault、EXTRA 原文保留（isOnUpdate 派生）
     */
    static ActualColumn mapColumnRow(Map<String, Object> row) {
        String name = str(row, "COLUMN_NAME");
        if (null == name) {
            return null;
        }
        return new ActualColumn(name, normalizeType(str(row, "DATA_TYPE")), normalizeType(str(row, "COLUMN_TYPE")),
                "YES".equalsIgnoreCase(str(row, "IS_NULLABLE")),
                normalizeColumnDefault(row.get("COLUMN_DEFAULT")), str(row, "EXTRA"),
                str(row, "COLUMN_COMMENT"), num(row, "CHARACTER_MAXIMUM_LENGTH"),
                num(row, "NUMERIC_PRECISION"), num(row, "NUMERIC_SCALE"));
    }

    /**
     * STATISTICS 行聚合 → 表名 → 索引清单：PRIMARY 主键跳过（公共列模板内建表达）、
     * 同索引多行按 SEQ_IN_INDEX 升序拼组合列、NON_UNIQUE / INDEX_TYPE 取首行值、
     * 索引名升序保证稳定序
     */
    static Map<String, List<ActualIndex>> groupIndexRows(List<Map<String, Object>> rows) {
        Map<String, Map<String, List<Map<String, Object>>>> grouped = new TreeMap<>();
        for (Map<String, Object> row : rows) {
            String tableName = str(row, "TABLE_NAME");
            String indexName = str(row, "INDEX_NAME");
            if (null == tableName || null == indexName || PRIMARY_INDEX.equalsIgnoreCase(indexName)) {
                continue;
            }
            grouped.computeIfAbsent(tableName, key -> new TreeMap<>())
                    .computeIfAbsent(indexName, key -> new ArrayList<>())
                    .add(row);
        }
        Map<String, List<ActualIndex>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, List<Map<String, Object>>>> tableEntry : grouped.entrySet()) {
            List<ActualIndex> indexes = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> indexEntry : tableEntry.getValue().entrySet()) {
                List<Map<String, Object>> indexRows = indexEntry.getValue();
                indexRows.sort(Comparator.comparingInt(row -> {
                    Integer seq = num(row, "SEQ_IN_INDEX");
                    return null == seq ? Integer.MAX_VALUE : seq;
                }));
                boolean unique = false;
                Integer nonUnique = num(indexRows.get(0), "NON_UNIQUE");
                if (null != nonUnique) {
                    unique = 0 == nonUnique;
                }
                indexes.add(new ActualIndex(indexEntry.getKey(), unique,
                        indexRows.stream().map(row -> str(row, "COLUMN_NAME")).toList(),
                        str(indexRows.get(0), "INDEX_TYPE")));
            }
            result.put(tableEntry.getKey(), List.copyOf(indexes));
        }
        return result;
    }

    /* ==================== 内部：工具 ==================== */

    /**
     * 注解类型 ↔ 实际 DATA_TYPE 映射等价判定（§4.3 类型映射）；BOOLEAN 额外用
     * COLUMN_TYPE 比对 bit(1)（BIT 按长度判定，bit(2) 等一律不兼容）
     */
    private static boolean typeCompatible(ColumnDef expected, ActualColumn actual) {
        return switch (expected.getType()) {
            case STRING -> "varchar".equals(actual.getDataType());
            case TEXT -> "text".equals(actual.getDataType());
            case INT -> "int".equals(actual.getDataType());
            case BIGINT -> "bigint".equals(actual.getDataType());
            case DECIMAL -> "decimal".equals(actual.getDataType());
            case BOOLEAN -> "bit".equals(actual.getDataType()) && "bit(1)".equals(actual.getColumnType());
            case DATE -> "date".equals(actual.getDataType());
            case DATETIME -> "datetime".equals(actual.getDataType());
            case TIMESTAMP -> "timestamp".equals(actual.getDataType());
            case JSON -> "json".equals(actual.getDataType());
        };
    }

    /**
     * 默认值差异判定：函数式（CURRENT_TIMESTAMP / ON_UPDATE）比对实际 CURRENT_TIMESTAMP
     * 字面量（大小写不敏感）；字面量比对归一后的实际默认值；注解无默认 ↔ 实际有默认同为差异
     */
    private static boolean isDefaultDiff(ColumnDef expected, ActualColumn actual) {
        if (DefaultFunction.NONE != expected.getDefaultFunction()) {
            String running = actual.getColumnDefault();
            return null == running || !"CURRENT_TIMESTAMP".equalsIgnoreCase(running);
        }
        if (null != expected.getDefaultValue()) {
            return !expected.getDefaultValue().equals(actual.getColumnDefault());
        }
        return null != actual.getColumnDefault();
    }

    /**
     * COMMENT 比对（null 归空串；表与列共用）
     */
    private static boolean commentEquals(String expected, String actual) {
        String left = null == expected ? "" : expected;
        String right = null == actual ? "" : actual;
        return left.equals(right);
    }

    /**
     * 默认值展示（明细用）：函数式渲染语义名、字面量原样、无默认渲染「无默认值」
     */
    private static String describeDefault(ColumnDef column) {
        if (DefaultFunction.NONE != column.getDefaultFunction()) {
            return DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP == column.getDefaultFunction()
                    ? "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" : "CURRENT_TIMESTAMP";
        }
        return null == column.getDefaultValue() ? "无默认值" : column.getDefaultValue();
    }

    /**
     * 实际列默认值展示（明细用）
     */
    private static String describeDefault(ActualColumn column) {
        return null == column.getColumnDefault() ? "无默认值" : column.getColumnDefault();
    }

    /**
     * 索引定义描述（明细用）
     */
    private static String describe(IndexDef index) {
        return index.getName() + " (" + String.join(", ", index.getColumns()) + ") unique=" + index.isUnique();
    }

    /**
     * collation → charset（前缀推导）：utf8mb4_0900_ai_ci → utf8mb4；无下划线视为已
     * 是字符集名原样返回；null 原样返回（比对不通过产告警）
     */
    private static String charsetOf(String collation) {
        if (null == collation) {
            return null;
        }
        int underscore = collation.indexOf('_');
        return underscore < 0 ? collation : collation.substring(0, underscore);
    }

    /**
     * 类型名归一：小写 + 去空白（MySQL 8.4 返回小写，归一防御漂移）
     */
    private static String normalizeType(String type) {
        return null == type ? null : type.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    /**
     * COLUMN_DEFAULT 归一：SQL NULL 与字符串 "NULL"（NOT NULL 无默认的 8.0+ 显示口径）
     * 均归 null；成对首尾单引号的字符串/时间字面量剥外层引号并反转义（'' → '、\\ → \，
     * 兼容带引号/不带引号两种版本显示口径）；其余原样（数字、b'1'、CURRENT_TIMESTAMP）
     */
    private static String normalizeColumnDefault(Object raw) {
        if (null == raw) {
            return null;
        }
        String value = String.valueOf(raw);
        if ("NULL".equals(value)) {
            return null;
        }
        if (value.length() >= 2 && '\'' == value.charAt(0) && '\'' == value.charAt(value.length() - 1)) {
            return value.substring(1, value.length() - 1).replace("''", "'").replace("\\\\", "\\");
        }
        return value;
    }

    /**
     * 行取字符串值（queryForList 返回 LinkedCaseInsensitiveMap 本身忽略大小写，此处再做
     * 一次忽略大小写回退，防御驱动差异）
     */
    private static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (null == value) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (key.equalsIgnoreCase(entry.getKey())) {
                    value = entry.getValue();
                    if (null != value) {
                        break;
                    }
                }
            }
        }
        return null == value ? null : String.valueOf(value);
    }

    /**
     * 行取整数值（MySQL JDBC 数值列返回 Long/BigInteger/BigDecimal 等 Number 实现）
     */
    private static Integer num(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? Integer.valueOf(number.intValue()) : null;
    }
}

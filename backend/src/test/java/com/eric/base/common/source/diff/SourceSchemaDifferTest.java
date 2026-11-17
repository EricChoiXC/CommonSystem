package com.eric.base.common.source.diff;

import com.eric.base.common.source.enums.ChangeLevel;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.enums.IndexType;
import com.eric.base.common.source.model.ColumnDef;
import com.eric.base.common.source.model.IndexDef;
import com.eric.base.common.source.model.SchemaChange;
import com.eric.base.common.source.model.TableDef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 表结构比对器测试
 *
 * @author Eric
 * @date 2026/11/16
 * @description 覆盖任务 F19-BE-004 验收标准：语义矩阵（设计方案 §4.3）14 行逐行至少一个
 *              用例（内存构造 TableDef × ActualTable，断言级别/变更类型/DDL 文本）；改名
 *              检测（同表缺失列 + 孤儿列 → L3）；managed 孤儿表告警；「永不 DROP/RENAME」
 *              对全部产出变更遍历断言；稳定排序（两次比对结果一致）。比对核心为纯函数
 *              （verifyNoInteractions 断言不触库）；information_schema 读取层以模拟行驱动
 *              assemble 归一 + SQL 文本断言，全类不连 MySQL。补充 F19-BE-011：矩阵新行
 *              「实际主键（列集/顺序）与注解声明不一致」→ L2 PRIMARY_KEY_MISMATCH 仅告警
 *              不执行（引擎永不修改主键）；实际主键读取源 = STATISTICS PRIMARY 行按
 *              SEQ_IN_INDEX 排序；PRIMARY 不落入缺失/多余索引比对。
 */
class SourceSchemaDifferTest {

    // ---------------------------------------------------------------- 基准常量与构造工具

    /** 测试表名（注解侧与实际侧统一） */
    private static final String TABLE = "diff_check_table";

    /** 业务列构造（primaryKey 恒为 false） */
    private static ColumnDef column(String name, String comment, ColumnType type, int length, int decimalLength,
                                    boolean nullable, String defaultValue, DefaultFunction defaultFunction) {
        return new ColumnDef(name, comment, type, length, decimalLength, nullable, defaultValue, defaultFunction, false);
    }

    /** 公共列模板内建主键（与 Scanner 内建口径一致） */
    private static ColumnDef primaryKey() {
        return new ColumnDef("field_id", "主键ID", ColumnType.STRING, 32, 0, false, null, DefaultFunction.NONE, true);
    }

    /** 注解侧表构造：COMMENT 固定「diff 比对测试表」，列 = 内建主键 + 业务列 */
    private static TableDef annotated(List<ColumnDef> businessColumns, List<IndexDef> indexes) {
        List<ColumnDef> columns = new ArrayList<>();
        columns.add(primaryKey());
        columns.addAll(businessColumns);
        return new TableDef(TABLE, "diff 比对测试表", null, columns, indexes, List.of());
    }

    private static TableDef annotated(List<ColumnDef> businessColumns) {
        return annotated(businessColumns, List.of());
    }

    /** 注解侧表构造（自定义表 COMMENT） */
    private static TableDef annotated(String comment, List<ColumnDef> businessColumns, List<IndexDef> indexes) {
        List<ColumnDef> columns = new ArrayList<>();
        columns.add(primaryKey());
        columns.addAll(businessColumns);
        return new TableDef(TABLE, comment, null, columns, indexes, List.of());
    }

    /** 注解侧表构造（primaryKeyColumns 声明主键列集，F19-DOC-005）：列 = 业务列，有效主键 = 声明集 */
    private static TableDef annotatedWithDeclaredPrimaryKey(List<String> primaryKeyColumns,
                                                            List<ColumnDef> columns, List<IndexDef> indexes) {
        return new TableDef(TABLE, "diff 比对测试表", null, columns, indexes, primaryKeyColumns);
    }

    /** 实际列全字段构造 */
    private static ActualColumn actualColumn(String name, String dataType, String columnType, boolean nullable,
                                             String columnDefault, String extra, String comment,
                                             Integer charMaxLength, Integer precision, Integer scale) {
        return new ActualColumn(name, dataType, columnType, nullable, columnDefault, extra, comment,
                charMaxLength, precision, scale);
    }

    /** varchar 实际列便捷构造（无 EXTRA、无精度） */
    private static ActualColumn actualVarchar(String name, String comment, int length, boolean nullable,
                                              String columnDefault) {
        return actualColumn(name, "varchar", "varchar(" + length + ")", nullable, columnDefault, "", comment,
                length, null, null);
    }

    /** 与内建主键匹配的实际列 */
    private static ActualColumn actualFieldId() {
        return actualColumn("field_id", "varchar", "varchar(32)", false, null, "", "主键ID", 32, null, null);
    }

    /** 实际表构造：ENGINE/CHARSET/COMMENT 与注解侧一致（无差异基线），主键缺省 field_id */
    private static ActualTable actual(List<ActualColumn> columns, List<ActualIndex> indexes) {
        return actual(columns, List.of("field_id"), indexes);
    }

    /** 实际表构造（显式主键列集，F19-DOC-005） */
    private static ActualTable actual(List<ActualColumn> columns, List<String> primaryKeyColumns,
                                      List<ActualIndex> indexes) {
        return new ActualTable(TABLE, "InnoDB", "diff 比对测试表", "utf8mb4_0900_ai_ci",
                columns, indexes, primaryKeyColumns);
    }

    private static ActualTable actual(List<ActualColumn> columns) {
        return actual(columns, List.of());
    }

    /** 实际索引构造 */
    private static ActualIndex actualIndex(String name, boolean unique, List<String> columns, String indexType) {
        return new ActualIndex(name, unique, columns, indexType);
    }

    /**
     * 构造比对器（ObjectProvider 惰性注入桥接，对齐生产构造器形态）
     */
    private static SourceSchemaDiffer differ(JdbcTemplate jdbcTemplate) {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        return new SourceSchemaDiffer(provider);
    }

    /** 按变更类型过滤 */
    private static List<SchemaChange> byType(List<SchemaChange> changes, String changeType) {
        return changes.stream().filter(change -> changeType.equals(change.getChangeType())).toList();
    }

    /** 断言某变更类型恰有一条并返回 */
    private static SchemaChange single(List<SchemaChange> changes, String changeType) {
        List<SchemaChange> found = byType(changes, changeType);
        assertEquals(1, found.size(), "变更条目唯一性: " + changeType + " -> " + changes);
        return found.get(0);
    }

    /** 按索引名取变更条目 */
    private static SchemaChange byIndexName(List<SchemaChange> changes, String indexName) {
        return changes.stream().filter(change -> indexName.equals(change.getIndexName())).findFirst().orElseThrow();
    }

    /** 按索引名取实际索引 */
    private static ActualIndex byName(List<ActualIndex> indexes, String name) {
        return indexes.stream().filter(index -> name.equals(index.getName())).findFirst().orElseThrow();
    }

    /** 读取层模拟行：TABLES */
    private static Map<String, Object> tableRow(String name, String engine, String comment, String collation) {
        Map<String, Object> row = new HashMap<>();
        row.put("TABLE_NAME", name);
        row.put("ENGINE", engine);
        row.put("TABLE_COMMENT", comment);
        row.put("TABLE_COLLATION", collation);
        return row;
    }

    /** 读取层模拟行：COLUMNS */
    private static Map<String, Object> columnRow(String table, String name, String dataType, String columnType,
                                                 String nullable, String columnDefault, String extra, String comment,
                                                 Integer charMaxLength, Integer precision, Integer scale) {
        Map<String, Object> row = new HashMap<>();
        row.put("TABLE_NAME", table);
        row.put("COLUMN_NAME", name);
        row.put("DATA_TYPE", dataType);
        row.put("COLUMN_TYPE", columnType);
        row.put("IS_NULLABLE", nullable);
        row.put("COLUMN_DEFAULT", columnDefault);
        row.put("EXTRA", extra);
        row.put("COLUMN_COMMENT", comment);
        row.put("CHARACTER_MAXIMUM_LENGTH", charMaxLength);
        row.put("NUMERIC_PRECISION", precision);
        row.put("NUMERIC_SCALE", scale);
        return row;
    }

    /** 读取层模拟行：STATISTICS */
    private static Map<String, Object> statRow(String table, String indexName, int seq, int nonUnique,
                                               String column, String indexType) {
        Map<String, Object> row = new HashMap<>();
        row.put("TABLE_NAME", table);
        row.put("INDEX_NAME", indexName);
        row.put("SEQ_IN_INDEX", seq);
        row.put("NON_UNIQUE", nonUnique);
        row.put("COLUMN_NAME", column);
        row.put("INDEX_TYPE", indexType);
        return row;
    }

    // ---------------------------------------------------------------- 矩阵行 1：缺失表（经 diff 主入口）

    @Test
    void missingTableProducesCreateTableLevel1() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(SourceSchemaDiffer.SQL_TABLES)).thenReturn(List.of());
        when(jdbcTemplate.queryForList(SourceSchemaDiffer.SQL_COLUMNS)).thenReturn(List.of());
        when(jdbcTemplate.queryForList(SourceSchemaDiffer.SQL_STATISTICS)).thenReturn(List.of());
        SourceSchemaDiffer differ = differ(jdbcTemplate);
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE)));

        List<SchemaChange> changes = differ.diff(List.of(expected), Set.of());

        SchemaChange change = single(changes, SchemaChange.MISSING_TABLE);
        assertEquals(ChangeLevel.L1, change.getLevel());
        assertNull(change.getColumnName());
        assertTrue(change.getDdl().startsWith("CREATE TABLE IF NOT EXISTS " + TABLE + " ("),
                "缺失表 L1 生成 CREATE TABLE: " + change.getDdl());
        assertTrue(change.getDdl().endsWith("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='diff 比对测试表'"));
    }

    // ---------------------------------------------------------------- 矩阵行 2：缺失列

    @Test
    void missingColumnProducesAddColumnLevel1() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE),
                column("field_remark", "备注", ColumnType.STRING, 500, 0, true, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId()));

        List<SchemaChange> changes = differ.compare(expected, running);

        List<SchemaChange> missing = byType(changes, SchemaChange.MISSING_COLUMN);
        assertEquals(2, missing.size(), "两个缺失列各一条");
        assertEquals("field_name", missing.get(0).getColumnName(), "缺失列按列名升序输出");
        assertEquals(ChangeLevel.L1, missing.get(0).getLevel());
        assertEquals("ALTER TABLE " + TABLE + " ADD COLUMN field_name VARCHAR(200) NOT NULL COMMENT '名称'",
                missing.get(0).getDdl());
        assertEquals("ALTER TABLE " + TABLE + " ADD COLUMN field_remark VARCHAR(500) DEFAULT NULL COMMENT '备注'",
                missing.get(1).getDdl());
    }

    // ---------------------------------------------------------------- 矩阵行 3：缺失索引（含唯一）

    @Test
    void missingIndexProducesAddIndexLevel1() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_code", "编码", ColumnType.STRING, 100, 0, false, null, DefaultFunction.NONE),
                column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE)),
                List.of(new IndexDef("uk_check_code", List.of("field_code"), true, IndexType.BTREE),
                        new IndexDef("idx_check_name", List.of("field_name"), false, IndexType.BTREE)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualVarchar("field_code", "编码", 100, false, null),
                actualVarchar("field_name", "名称", 200, false, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange unique = byIndexName(changes, "uk_check_code");
        assertEquals(ChangeLevel.L1, unique.getLevel());
        assertEquals(SchemaChange.MISSING_INDEX, unique.getChangeType());
        assertEquals("ALTER TABLE " + TABLE + " ADD UNIQUE INDEX uk_check_code (field_code)", unique.getDdl(),
                "唯一索引用 ADD UNIQUE INDEX");
        SchemaChange normal = byIndexName(changes, "idx_check_name");
        assertEquals("ALTER TABLE " + TABLE + " ADD INDEX idx_check_name (field_name)", normal.getDdl());
    }

    // ---------------------------------------------------------------- 矩阵行 4：VARCHAR 扩长 / DECIMAL 精度提升

    @Test
    void widenLengthProducesModifyLevel1() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SourceSchemaDiffer differ = differ(jdbcTemplate);
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 300, 0, false, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(), actualVarchar("field_name", "名称", 200, false, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.WIDEN_LENGTH);
        assertEquals(ChangeLevel.L1, change.getLevel());
        assertEquals("ALTER TABLE " + TABLE + " MODIFY COLUMN field_name VARCHAR(300) NOT NULL COMMENT '名称'",
                change.getDdl(), "VARCHAR 扩长走 MODIFY COLUMN 全量重定义");
        assertTrue(change.getDetail().contains("注解 300 ↔ 实际 200"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void widenPrecisionProducesModifyLevel1() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_amount", "金额", ColumnType.DECIMAL, 15, 2, false, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualColumn("field_amount", "decimal", "decimal(10,2)", false, null, "", "金额", null, 10, 2)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.WIDEN_PRECISION);
        assertEquals(ChangeLevel.L1, change.getLevel());
        assertEquals("ALTER TABLE " + TABLE + " MODIFY COLUMN field_amount DECIMAL(15,2) NOT NULL COMMENT '金额'",
                change.getDdl(), "DECIMAL 精度提升走 MODIFY COLUMN 全量重定义");
    }

    // ---------------------------------------------------------------- 矩阵行 5：列默认值变更

    @Test
    void literalDefaultDiffProducesSetDefaultLevel1() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_status", "状态", ColumnType.STRING, 20, 0, false, "Y", DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualVarchar("field_status", "状态", 20, false, "N")));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.CHANGE_DEFAULT);
        assertEquals(ChangeLevel.L1, change.getLevel());
        assertEquals("ALTER TABLE " + TABLE + " ALTER COLUMN field_status SET DEFAULT 'Y'", change.getDdl(),
                "字面量默认值变更走 SET DEFAULT");
        assertTrue(change.getDetail().contains("注解 Y ↔ 实际 N"),
                "ActualColumn 契约为归一后默认值（剥引号在读取层 mapColumnRow，assemble 用例覆盖）");
    }

    @Test
    void functionalDefaultDiffProducesSetDefaultLevel1() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_create_time", "创建时间", ColumnType.TIMESTAMP, 0, 0, false, null,
                        DefaultFunction.CURRENT_TIMESTAMP)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualColumn("field_create_time", "timestamp", "timestamp", false, null, "", "创建时间",
                        null, null, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.CHANGE_DEFAULT);
        assertEquals("ALTER TABLE " + TABLE + " ALTER COLUMN field_create_time SET DEFAULT CURRENT_TIMESTAMP",
                change.getDdl(), "函数式默认值变更走 SET DEFAULT CURRENT_TIMESTAMP");
    }

    @Test
    void onUpdateClauseDiffProducesModifyLevel1() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_update_time", "修改时间", ColumnType.TIMESTAMP, 0, 0, true, null,
                        DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualColumn("field_update_time", "timestamp", "timestamp", true, "CURRENT_TIMESTAMP",
                        "DEFAULT_GENERATED", "修改时间", null, null, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.CHANGE_ON_UPDATE);
        assertEquals(ChangeLevel.L1, change.getLevel());
        assertEquals("ALTER TABLE " + TABLE + " MODIFY COLUMN field_update_time TIMESTAMP NULL DEFAULT"
                        + " CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间'", change.getDdl(),
                "ON UPDATE 子句变更走 MODIFY COLUMN 全量重定义（补齐 on update 子句）");
        assertEquals(0, byType(changes, SchemaChange.CHANGE_DEFAULT).size(),
                "ON UPDATE 变更涵盖默认值全量重定义，不再重复产 CHANGE_DEFAULT");
    }

    @Test
    void defaultRemovalProducesModifyLevel1() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_remark", "备注", ColumnType.STRING, 100, 0, true, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualVarchar("field_remark", "备注", 100, true, "abc")));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.CHANGE_DEFAULT);
        assertEquals("ALTER TABLE " + TABLE + " MODIFY COLUMN field_remark VARCHAR(100) DEFAULT NULL COMMENT '备注'",
                change.getDdl(), "去除默认值走 MODIFY COLUMN 全量重定义（生成器不产 DROP DEFAULT）");
    }

    // ---------------------------------------------------------------- 矩阵行 6：列 COMMENT / 表 COMMENT 变更

    @Test
    void columnCommentDiffProducesModifyLevel1() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_name", "名称（新注释）", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(), actualVarchar("field_name", "名称", 200, false, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.CHANGE_COLUMN_COMMENT);
        assertEquals(ChangeLevel.L1, change.getLevel());
        assertEquals("ALTER TABLE " + TABLE + " MODIFY COLUMN field_name VARCHAR(200) NOT NULL COMMENT '名称（新注释）'",
                change.getDdl(), "列 COMMENT 变更走 MODIFY COLUMN 完整列定义重写");
    }

    @Test
    void tableCommentDiffProducesAlterCommentLevel1() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated("新表注释",
                List.of(column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE)),
                List.of());
        ActualTable running = actual(List.of(actualFieldId(), actualVarchar("field_name", "名称", 200, false, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.CHANGE_TABLE_COMMENT);
        assertEquals(ChangeLevel.L1, change.getLevel());
        assertNull(change.getColumnName());
        assertEquals("ALTER TABLE " + TABLE + " COMMENT='新表注释'", change.getDdl(), "表 COMMENT 变更走 ALTER COMMENT");
    }

    // ---------------------------------------------------------------- 矩阵行 7：列可空收紧（NULL → NOT NULL）

    @Test
    void tightenNullableProducesWarnWithoutDdl() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(), actualVarchar("field_name", "名称", 200, true, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.TIGHTEN_NULLABLE);
        assertEquals(ChangeLevel.L2, change.getLevel());
        assertNull(change.getDdl(), "L2 可空收紧不产 DDL");
        assertTrue(change.getDetail().contains("实际 NULL → 注解 NOT NULL"));
    }

    // ---------------------------------------------------------------- 矩阵行 8：长度收窄 / 类型不兼容

    @Test
    void shrinkLengthProducesFailFastLevel3() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 100, 0, false, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(), actualVarchar("field_name", "名称", 200, false, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.SHRINK_OR_INCOMPATIBLE);
        assertEquals(ChangeLevel.L3, change.getLevel());
        assertNull(change.getDdl(), "L3 长度收窄不产 DDL");
        assertTrue(change.getDetail().contains("长度收窄：注解 100 ↔ 实际 200"));
        assertTrue(change.getDetail().contains("DataMigration"), "明细给处理指引");
    }

    @Test
    void incompatibleTypeProducesFailFastLevel3() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_count", "数量", ColumnType.INT, 0, 0, false, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualVarchar("field_count", "数量", 50, false, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.SHRINK_OR_INCOMPATIBLE);
        assertEquals(ChangeLevel.L3, change.getLevel());
        assertNull(change.getDdl(), "L3 类型不兼容不产 DDL");
        assertTrue(change.getDetail().contains("类型不兼容"));
        assertTrue(change.getDetail().contains("注解 INT ↔ 实际 varchar(50)"), "映射不等价明细");
    }

    // ---------------------------------------------------------------- 矩阵行 9：缺失列 + 孤儿列（疑似改名）

    @Test
    void renameSuspectProducesLevel3AndKeepsMissingColumnLevel1() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_a", "列A", ColumnType.STRING, 100, 0, false, null, DefaultFunction.NONE),
                column("field_b", "列B", ColumnType.STRING, 100, 0, false, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualVarchar("field_x", "列A", 100, false, null),
                actualVarchar("field_y", "列B", 100, false, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange suspect = single(changes, SchemaChange.RENAME_SUSPECT);
        assertEquals(ChangeLevel.L3, suspect.getLevel());
        assertNull(suspect.getDdl(), "L3 疑似改名不产 DDL");
        assertTrue(suspect.getDetail().contains("[field_a → field_x]、[field_b → field_y]"),
                "改名对按声明序配对进明细: " + suspect.getDetail());
        assertTrue(suspect.getDetail().contains("DataMigration"), "改名必须显式 DataMigration 的指引进明细");

        List<SchemaChange> missing = byType(changes, SchemaChange.MISSING_COLUMN);
        assertEquals(2, missing.size(), "缺失列仍逐条报 L1");
        for (SchemaChange change : missing) {
            assertEquals(ChangeLevel.L1, change.getLevel());
            assertNotNull(change.getDdl(), "缺失列 ADD COLUMN DDL 保留");
        }
        assertEquals(0, byType(changes, SchemaChange.ORPHAN_COLUMN).size(),
                "孤儿列项并入 RENAME_SUSPECT，不再单独报 ORPHAN_COLUMN（收敛重复噪音）");
    }

    // ---------------------------------------------------------------- 矩阵行 10：注解中删除的字段（孤儿列）

    @Test
    void orphanColumnProducesWarnWithoutDdl() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualVarchar("field_name", "名称", 200, false, null),
                actualVarchar("field_legacy", "历史列", 100, true, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.ORPHAN_COLUMN);
        assertEquals(ChangeLevel.L2, change.getLevel());
        assertEquals("field_legacy", change.getColumnName());
        assertNull(change.getDdl(), "L2 孤儿列不产 DDL（永不 DROP COLUMN）");
    }

    // ---------------------------------------------------------------- 矩阵行 11：索引定义差异

    @Test
    void indexDiffProducesWarnWithoutDdl() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE),
                column("field_code", "编码", ColumnType.STRING, 100, 0, false, null, DefaultFunction.NONE),
                column("field_remark", "备注", ColumnType.STRING, 500, 0, true, null, DefaultFunction.NONE)),
                List.of(new IndexDef("idx_cols", List.of("field_name"), false, IndexType.BTREE),
                        new IndexDef("idx_uni", List.of("field_code"), true, IndexType.BTREE),
                        new IndexDef("idx_type", List.of("field_remark"), false, IndexType.BTREE)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualVarchar("field_name", "名称", 200, false, null),
                actualVarchar("field_code", "编码", 100, false, null),
                actualVarchar("field_remark", "备注", 500, true, null)),
                List.of(actualIndex("idx_cols", false, List.of("field_code"), "BTREE"),
                        actualIndex("idx_uni", false, List.of("field_code"), "BTREE"),
                        actualIndex("idx_type", false, List.of("field_remark"), "FULLTEXT")));

        List<SchemaChange> changes = differ.compare(expected, running);

        assertEquals(3, byType(changes, SchemaChange.INDEX_DIFF).size(), "列集/唯一性/类型三处差异各一条");
        SchemaChange columns = byIndexName(changes, "idx_cols");
        assertEquals(ChangeLevel.L2, columns.getLevel());
        assertTrue(columns.getDetail().contains("列集：注解 (field_name) ↔ 实际 (field_code)"));
        assertNull(columns.getDdl(), "L2 索引差异不产 DDL（重建=删+加，不自动）");
        assertTrue(byIndexName(changes, "idx_uni").getDetail().contains("唯一性：注解 unique=true ↔ 实际 unique=false"));
        assertTrue(byIndexName(changes, "idx_type").getDetail().contains("索引类型：注解 BTREE ↔ 实际 FULLTEXT"));
    }

    // ---------------------------------------------------------------- 矩阵行 12：实际多余索引

    @Test
    void extraIndexProducesWarnWithoutDdl() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(), actualVarchar("field_name", "名称", 200, false, null)),
                List.of(actualIndex("idx_extra", false, List.of("field_name"), "BTREE")));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.EXTRA_INDEX);
        assertEquals(ChangeLevel.L2, change.getLevel());
        assertEquals("idx_extra", change.getIndexName());
        assertNull(change.getDdl(), "L2 多余索引不产 DDL（引擎不删索引）");
    }

    // ---------------------------------------------------------------- 矩阵行 13：ENGINE / CHARSET 不一致

    @Test
    void engineCharsetDiffProducesWarnWithoutDdl() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE)));
        ActualTable both = new ActualTable(TABLE, "MyISAM", "diff 比对测试表", "latin1_swedish_ci",
                List.of(actualFieldId(), actualVarchar("field_name", "名称", 200, false, null)),
                List.of(), List.of("field_id"));
        List<SchemaChange> changes = differ.compare(expected, both);

        SchemaChange change = single(changes, SchemaChange.ENGINE_CHARSET_DIFF);
        assertEquals(ChangeLevel.L2, change.getLevel());
        assertNull(change.getDdl(), "L2 ENGINE/CHARSET 不一致不产 DDL（引擎不改建）");
        assertTrue(change.getDetail().contains("ENGINE=MyISAM") && change.getDetail().contains("CHARSET=latin1"),
                "ENGINE 与 CHARSET 合并为一条告警: " + change.getDetail());

        ActualTable engineOnly = new ActualTable(TABLE, "MyISAM", "diff 比对测试表", "utf8mb4_0900_ai_ci",
                List.of(actualFieldId(), actualVarchar("field_name", "名称", 200, false, null)),
                List.of(), List.of("field_id"));
        SchemaChange engine = single(differ.compare(expected, engineOnly), SchemaChange.ENGINE_CHARSET_DIFF);
        assertTrue(engine.getDetail().contains("ENGINE=MyISAM"));
        assertFalse(engine.getDetail().contains("CHARSET"), "CHARSET 一致时不进明细");
    }

    // ---------------------------------------------------------------- 矩阵新行：主键不一致（F19-DOC-005，任何模式仅告警不执行）

    @Test
    void primaryKeyOrderMismatchProducesWarnWithoutDdl() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotatedWithDeclaredPrimaryKey(
                List.of("field_user_id", "field_post_id"),
                List.of(column("field_user_id", "人员ID", ColumnType.STRING, 32, 0, false, null, DefaultFunction.NONE),
                        column("field_post_id", "岗位ID", ColumnType.STRING, 32, 0, false, null, DefaultFunction.NONE)),
                List.of());
        ActualTable running = actual(
                List.of(actualVarchar("field_user_id", "人员ID", 32, false, null),
                        actualVarchar("field_post_id", "岗位ID", 32, false, null)),
                List.of("field_post_id", "field_user_id"), List.of());

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.PRIMARY_KEY_MISMATCH);
        assertEquals(ChangeLevel.L2, change.getLevel());
        assertNull(change.getColumnName());
        assertNull(change.getIndexName());
        assertNull(change.getDdl(), "L2 主键不一致不产 DDL（引擎永不修改主键，改主键走人工 SQL）");
        assertTrue(change.getDetail().contains("注解 (field_user_id, field_post_id) ↔ 实际 (field_post_id, field_user_id)"),
                "明细写两侧列集与顺序: " + change.getDetail());
        assertTrue(change.getDetail().contains("永不修改主键"), "明细给处理指引");
    }

    @Test
    void primaryKeyColumnSetMismatchProducesWarnWithoutDdl() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotatedWithDeclaredPrimaryKey(
                List.of("field_user_id", "field_post_id"),
                List.of(column("field_user_id", "人员ID", ColumnType.STRING, 32, 0, false, null, DefaultFunction.NONE),
                        column("field_post_id", "岗位ID", ColumnType.STRING, 32, 0, false, null, DefaultFunction.NONE)),
                List.of());
        // 实际为缺省单列主键（field_id）→ 列集不一致
        ActualTable running = actual(
                List.of(actualFieldId(),
                        actualVarchar("field_user_id", "人员ID", 32, false, null),
                        actualVarchar("field_post_id", "岗位ID", 32, false, null)),
                List.of("field_id"), List.of());

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.PRIMARY_KEY_MISMATCH);
        assertEquals(ChangeLevel.L2, change.getLevel());
        assertTrue(change.getDetail().contains("注解 (field_user_id, field_post_id) ↔ 实际 (field_id)"),
                "列集不一致明细: " + change.getDetail());
    }

    @Test
    void primaryKeyMatchProducesNoPrimaryKeyChange() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotatedWithDeclaredPrimaryKey(
                List.of("field_user_id", "field_post_id"),
                List.of(column("field_user_id", "人员ID", ColumnType.STRING, 32, 0, false, null, DefaultFunction.NONE),
                        column("field_post_id", "岗位ID", ColumnType.STRING, 32, 0, false, null, DefaultFunction.NONE)),
                List.of());
        ActualTable running = actual(
                List.of(actualVarchar("field_user_id", "人员ID", 32, false, null),
                        actualVarchar("field_post_id", "岗位ID", 32, false, null)),
                List.of("field_user_id", "field_post_id"), List.of());

        assertTrue(differ.compare(expected, running).isEmpty(),
                "实际主键（列集与顺序）与声明一致时零 diff（含主键维度）");
    }

    @Test
    void defaultTablePrimaryKeyUnchangedProducesNoPrimaryKeyChange() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(), actualVarchar("field_name", "名称", 200, false, null)),
                List.of("field_id"), List.of());

        List<SchemaChange> changes = differ.compare(expected, running);

        assertEquals(0, byType(changes, SchemaChange.PRIMARY_KEY_MISMATCH).size(),
                "缺省（field_id）表实际主键仍为 field_id → 不产主键变更（回归断言）");
    }

    // ---------------------------------------------------------------- 矩阵行 14：managed 表失去标注

    @Test
    void managedOrphanTableProducesWarnWithoutDdl() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(SourceSchemaDiffer.SQL_TABLES))
                .thenReturn(List.of(tableRow("legacy_table", "InnoDB", "历史标注表", "utf8mb4_0900_ai_ci")));
        when(jdbcTemplate.queryForList(SourceSchemaDiffer.SQL_COLUMNS)).thenReturn(List.of());
        when(jdbcTemplate.queryForList(SourceSchemaDiffer.SQL_STATISTICS)).thenReturn(List.of());
        SourceSchemaDiffer differ = differ(jdbcTemplate);
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE)));

        List<SchemaChange> changes = differ.diff(List.of(expected), Set.of("legacy_table"));

        SchemaChange change = single(changes, SchemaChange.MANAGED_ORPHAN_TABLE);
        assertEquals(ChangeLevel.L2, change.getLevel());
        assertEquals("legacy_table", change.getTableName());
        assertNull(change.getDdl(), "managed 孤儿表永不执行任何 DDL");

        // managed 表已被人工删除（库中不存在）→ 无结构可比，不产告警
        JdbcTemplate empty = mock(JdbcTemplate.class);
        when(empty.queryForList(SourceSchemaDiffer.SQL_TABLES)).thenReturn(List.of());
        when(empty.queryForList(SourceSchemaDiffer.SQL_COLUMNS)).thenReturn(List.of());
        when(empty.queryForList(SourceSchemaDiffer.SQL_STATISTICS)).thenReturn(List.of());
        List<SchemaChange> removed = differ(empty).diff(List.of(expected), Set.of("legacy_table"));
        assertEquals(0, byType(removed, SchemaChange.MANAGED_ORPHAN_TABLE).size());
    }

    // ---------------------------------------------------------------- 永不 DROP / RENAME（遍历断言）

    @Test
    void neverDropsOrRenamesAcrossAllChanges() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SourceSchemaDiffer differ = differ(jdbcTemplate);
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 300, 0, false, null, DefaultFunction.NONE),
                column("field_old", "旧列", ColumnType.STRING, 100, 0, false, null, DefaultFunction.NONE)),
                List.of(new IndexDef("uk_name", List.of("field_name"), true, IndexType.BTREE)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualVarchar("field_name", "名称", 200, true, null),
                actualVarchar("field_new", "新列", 100, false, null)),
                List.of(actualIndex("idx_extra", false, List.of("field_name"), "BTREE")));

        List<SchemaChange> changes = differ.compare(expected, running);

        assertTrue(changes.size() >= 6, "多差异场景应产出完整变更集: " + changes);
        assertNotNull(single(changes, SchemaChange.MISSING_COLUMN));
        assertNotNull(single(changes, SchemaChange.RENAME_SUSPECT));
        assertNotNull(single(changes, SchemaChange.TIGHTEN_NULLABLE));
        assertNotNull(single(changes, SchemaChange.WIDEN_LENGTH));
        assertNotNull(single(changes, SchemaChange.MISSING_INDEX));
        assertNotNull(single(changes, SchemaChange.EXTRA_INDEX));
        for (SchemaChange change : changes) {
            assertTrue(null == change.getDdl()
                            || (!change.getDdl().toUpperCase().contains("DROP")
                            && !change.getDdl().toUpperCase().contains("RENAME")),
                    "引擎永不生成 DROP/RENAME 类 DDL: " + change.getChangeType() + " -> " + change.getDdl());
        }
        verifyNoInteractions(jdbcTemplate);
    }

    // ---------------------------------------------------------------- 稳定排序（两次比对一致）

    @Test
    void stableOrderingAcrossRuns() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_b", "列B", ColumnType.STRING, 100, 0, false, null, DefaultFunction.NONE),
                column("field_a", "列A", ColumnType.STRING, 100, 0, false, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualVarchar("field_n", "列N", 100, false, null),
                actualVarchar("field_m", "列M", 100, false, null)));

        List<SchemaChange> first = differ.compare(expected, running);
        List<SchemaChange> second = differ.compare(expected, running);

        assertEquals(first.toString(), second.toString(), "两次比对结果完全一致（稳定序）");
        List<String> missingNames = byType(first, SchemaChange.MISSING_COLUMN).stream()
                .map(SchemaChange::getColumnName).toList();
        assertEquals(List.of("field_a", "field_b"), missingNames, "缺失列按列名升序输出（非注解声明序）");
        SchemaChange suspect = single(first, SchemaChange.RENAME_SUSPECT);
        assertTrue(suspect.getDetail().contains("[field_b → field_n]、[field_a → field_m]"),
                "改名对按注解声明序配对（确定性）");
        List<String> tableOrder = first.stream().map(SchemaChange::getTableName).distinct().toList();
        assertEquals(List.of(TABLE), tableOrder, "单表场景表名一致");
    }

    // ---------------------------------------------------------------- 完全一致（零 diff）

    @Test
    void noDiffProducesEmptyChanges() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE),
                column("field_remark", "备注", ColumnType.STRING, 500, 0, true, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(),
                actualVarchar("field_name", "名称", 200, false, null),
                actualVarchar("field_remark", "备注", 500, true, null)),
                List.of());

        assertTrue(differ.compare(expected, running).isEmpty(), "注解与实际完全一致时零 diff");
    }

    // ---------------------------------------------------------------- 补充定稿项：可空放宽 + DDL 阻断口径

    @Test
    void loosenNullableProducesModifyLevel1() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(
                column("field_remark", "备注", ColumnType.STRING, 500, 0, true, null, DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(), actualVarchar("field_remark", "备注", 500, false, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange change = single(changes, SchemaChange.LOOSEN_NULLABLE);
        assertEquals(ChangeLevel.L1, change.getLevel());
        assertEquals("ALTER TABLE " + TABLE + " MODIFY COLUMN field_remark VARCHAR(500) DEFAULT NULL COMMENT '备注'",
                change.getDdl(), "可空放宽为无损变更，走 MODIFY COLUMN（矩阵未覆盖的补充项）");
    }

    @Test
    void tightenBlocksModifyButKeepsSetDefault() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(column("field_name", "名称", ColumnType.STRING, 300, 0,
                false, "Y", DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(), actualVarchar("field_name", "名称", 200, true, null)));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange widen = single(changes, SchemaChange.WIDEN_LENGTH);
        assertNull(widen.getDdl(), "同列存在 L2 可空收紧时，MODIFY 全量重定义会隐式收紧，DDL 暂缓");
        assertTrue(widen.getDetail().contains("DDL 暂缓"));
        SchemaChange tighten = single(changes, SchemaChange.TIGHTEN_NULLABLE);
        assertNull(tighten.getDdl());
        SchemaChange defaultChange = single(changes, SchemaChange.CHANGE_DEFAULT);
        assertEquals("ALTER TABLE " + TABLE + " ALTER COLUMN field_name SET DEFAULT 'Y'", defaultChange.getDdl(),
                "SET DEFAULT 仅动默认值子句、不动可空，不被 L2 收紧阻断");
    }

    @Test
    void shrinkBlocksAllColumnDdl() {
        SourceSchemaDiffer differ = differ(mock(JdbcTemplate.class));
        TableDef expected = annotated(List.of(column("field_name", "新注释", ColumnType.STRING, 100, 0,
                false, "Y", DefaultFunction.NONE)));
        ActualTable running = actual(List.of(actualFieldId(), actualVarchar("field_name", "旧注释", 200, false, "N")));

        List<SchemaChange> changes = differ.compare(expected, running);

        SchemaChange shrink = single(changes, SchemaChange.SHRINK_OR_INCOMPATIBLE);
        assertEquals(ChangeLevel.L3, shrink.getLevel());
        SchemaChange defaultChange = single(changes, SchemaChange.CHANGE_DEFAULT);
        assertNull(defaultChange.getDdl(), "L3 收窄时整列 DDL 暂缓（含 SET DEFAULT）");
        SchemaChange comment = single(changes, SchemaChange.CHANGE_COLUMN_COMMENT);
        assertNull(comment.getDdl(), "L3 收窄时列 COMMENT 变更 DDL 暂缓");
    }

    // ---------------------------------------------------------------- 读取层归一（不连库，模拟行驱动）

    @Test
    void assembleNormalizesRowsFromThreeSources() {
        List<Map<String, Object>> tableRows = List.of(
                tableRow(TABLE, "InnoDB", "读取层测试表", "utf8mb4_0900_ai_ci"));
        List<Map<String, Object>> columnRows = List.of(
                columnRow(TABLE, "field_id", "VARCHAR", "varchar(32)", "NO", "NULL", "", "主键ID", 32, null, null),
                columnRow(TABLE, "field_name", "VARCHAR", "varchar(100)", "NO", "'Y'", "DEFAULT_GENERATED",
                        "名称", 100, null, null),
                columnRow(TABLE, "field_update_time", "TIMESTAMP", "timestamp", "YES", "CURRENT_TIMESTAMP",
                        "DEFAULT_GENERATED on update CURRENT_TIMESTAMP", "修改时间", null, null, null),
                columnRow(TABLE, "field_amount", "DECIMAL", "decimal(10,2)", "NO", "0", "", "金额", null, 10, 2));
        List<Map<String, Object>> indexRows = List.of(
                statRow(TABLE, "PRIMARY", 1, 0, "field_id", "BTREE"),
                statRow(TABLE, "idx_multi", 2, 1, "field_b", "BTREE"),
                statRow(TABLE, "idx_multi", 1, 1, "field_a", "BTREE"),
                statRow(TABLE, "uk_code", 1, 0, "field_code", "BTREE"));

        Map<String, ActualTable> tables = SourceSchemaDiffer.assemble(tableRows, columnRows, indexRows);
        assertEquals(1, tables.size());
        ActualTable actual = tables.get(TABLE);
        assertEquals("InnoDB", actual.getEngine());
        assertEquals("读取层测试表", actual.getTableComment());
        assertEquals("utf8mb4_0900_ai_ci", actual.getTableCollation());

        assertEquals(4, actual.getColumns().size(), "列序沿 ORDINAL_POSITION（行序）");
        ActualColumn fieldId = actual.getColumns().get(0);
        assertEquals("field_id", fieldId.getName());
        assertEquals("varchar", fieldId.getDataType());
        assertEquals("varchar(32)", fieldId.getColumnType());
        assertFalse(fieldId.isNullable());
        assertNull(fieldId.getColumnDefault(), "字符串 \"NULL\"（NOT NULL 无默认显示口径）归一为 null");

        ActualColumn name = actual.getColumns().get(1);
        assertEquals("Y", name.getColumnDefault(), "字符串字面量默认值剥外层引号归一");
        assertEquals("名称", name.getComment());

        ActualColumn updateTime = actual.getColumns().get(2);
        assertTrue(updateTime.isOnUpdate(), "EXTRA 含 on update CURRENT_TIMESTAMP 派生为 ON UPDATE 子句");
        assertEquals("CURRENT_TIMESTAMP", updateTime.getColumnDefault(), "函数式默认值保留原文比对");

        ActualColumn amount = actual.getColumns().get(3);
        assertEquals(10, amount.getNumericPrecision().intValue());
        assertEquals(2, amount.getNumericScale().intValue());

        assertEquals(2, actual.getIndexes().size(), "PRIMARY 主键跳过（公共列模板内建表达）");
        ActualIndex multi = byName(actual.getIndexes(), "idx_multi");
        assertEquals(List.of("field_a", "field_b"), multi.getColumns(), "组合索引按 SEQ_IN_INDEX 排序");
        assertFalse(multi.isUnique());
        assertEquals("BTREE", multi.getIndexType());
        assertTrue(byName(actual.getIndexes(), "uk_code").isUnique(), "NON_UNIQUE = 0 归一为唯一索引");
    }

    @Test
    void assembleExtractsPrimaryKeyColumnsFromStatisticsPrimaryRows() {
        List<Map<String, Object>> tableRows = List.of(
                tableRow(TABLE, "InnoDB", "读取层测试表", "utf8mb4_0900_ai_ci"),
                tableRow("no_pk_table", "InnoDB", "无主键表", "utf8mb4_0900_ai_ci"));
        List<Map<String, Object>> columnRows = List.of();
        // PRIMARY 行乱序给出（SEQ_IN_INDEX 恢复列序），无主键表无 PRIMARY 行
        List<Map<String, Object>> indexRows = List.of(
                statRow(TABLE, "PRIMARY", 2, 0, "field_post_id", "BTREE"),
                statRow(TABLE, "idx_post_user_post", 1, 1, "field_post_id", "BTREE"),
                statRow(TABLE, "PRIMARY", 1, 0, "field_user_id", "BTREE"));

        Map<String, ActualTable> tables = SourceSchemaDiffer.assemble(tableRows, columnRows, indexRows);

        assertEquals(List.of("field_user_id", "field_post_id"), tables.get(TABLE).getPrimaryKeyColumns(),
                "实际主键列集按 SEQ_IN_INDEX 排序（STATISTICS PRIMARY 行，与索引三源读取同一通道）");
        assertEquals(1, tables.get(TABLE).getIndexes().size(),
                "PRIMARY 不落入二级索引比对范围（不参与缺失/多余索引比对）");
        assertTrue(tables.get("no_pk_table").getPrimaryKeyColumns().isEmpty(), "无 PRIMARY 行的表主键为空集");
    }

    // ---------------------------------------------------------------- SQL 文本口径（参数化收口）

    @Test
    void sqlConstantsUseParameterizedSchemaScope() {
        assertTrue(SourceSchemaDiffer.SQL_TABLES.contains("WHERE TABLE_SCHEMA = DATABASE()"),
                "TABLES 读取以 DATABASE() 收口当前库");
        assertTrue(SourceSchemaDiffer.SQL_COLUMNS.contains("WHERE TABLE_SCHEMA = DATABASE()"),
                "COLUMNS 读取以 DATABASE() 收口当前库");
        assertTrue(SourceSchemaDiffer.SQL_STATISTICS.contains("WHERE TABLE_SCHEMA = DATABASE()"),
                "STATISTICS 读取以 DATABASE() 收口当前库");
        assertTrue(SourceSchemaDiffer.SQL_COLUMNS.contains("COLUMN_DEFAULT")
                        && SourceSchemaDiffer.SQL_COLUMNS.contains("EXTRA")
                        && SourceSchemaDiffer.SQL_COLUMNS.contains("CHARACTER_MAXIMUM_LENGTH")
                        && SourceSchemaDiffer.SQL_COLUMNS.contains("NUMERIC_PRECISION")
                        && SourceSchemaDiffer.SQL_COLUMNS.contains("NUMERIC_SCALE"),
                "COLUMNS 字段与 §4.2 读取源清单一致");
        assertTrue(SourceSchemaDiffer.SQL_STATISTICS.contains("NON_UNIQUE")
                        && SourceSchemaDiffer.SQL_STATISTICS.contains("SEQ_IN_INDEX")
                        && SourceSchemaDiffer.SQL_STATISTICS.contains("INDEX_TYPE"),
                "STATISTICS 字段与 §4.2 读取源清单一致");
    }
}

package com.eric.base.common.source.scanner;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.base.model.MainModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.DataSource;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.enums.IndexType;
import com.eric.base.common.source.model.ColumnDef;
import com.eric.base.common.source.model.IndexDef;
import com.eric.base.common.source.model.TableDef;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表定义扫描器测试
 *
 * @author Eric
 * @date 2026/11/16
 * @description 覆盖任务 F19-BE-002 验收标准：§3.5 九条 fail-fast 规则逐条用例、
 *              公共列模板按基类内建（MainModel 审计五列 / BaseModel 仅主键）、
 *              字段重声明遮蔽（D5，子类定义入列 + Lombok 读写器覆盖父类实现）、
 *              进程内缓存与结果不可变。类发现经可注入的 parse / scan(List) 验证，
 *              不耦合 classpath 扫描行为（scan() 为薄发现层）。
 */
class SourceTableScannerTest {

    // ---------------------------------------------------------------- 扫描桩类（正常路径）

    /** BaseModel 模板桩：仅内建主键，无审计列（流水类口径） */
    @TableSource(name = "mock_scan_base", comment = "扫描测试流水表（BaseModel 模板）")
    static class MockBaseEntity extends BaseModel {

        @ColumnField(name = "field_remark", comment = "备注", type = ColumnType.STRING, length = 200, nullable = true)
        private String fieldRemark;
    }

    /**
     * MainModel 模板桩：内建主键最前 + 审计五列最后；业务列含 STRING/DECIMAL/字面量默认值、
     * 重声明 fieldCode（D5）、索引含唯一索引与「业务列 + 内建审计列」组合索引
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    @TableSource(name = "mock_scan_main", comment = "扫描测试主档表（MainModel 模板）",
            indexes = {
                    @TableIndex(name = "uk_scan_main_code", columns = "field_code", unique = true),
                    @TableIndex(name = "idx_scan_main_remark_enable", columns = {"field_remark", "field_enable"})})
    @DataSource(name = "scan")
    static class MockMainEntity extends MainModel {

        /** 重声明：覆盖 MainModel 继承列的表级定义（D5） */
        @ColumnField(name = "field_code", comment = "编码", type = ColumnType.STRING, length = 100)
        private String fieldCode;

        @ColumnField(name = "field_remark", comment = "备注", type = ColumnType.STRING, length = 500, nullable = true)
        private String fieldRemark;

        @ColumnField(name = "field_amount", comment = "金额", type = ColumnType.DECIMAL, length = 12, decimalLength = 2)
        private BigDecimal fieldAmount;

        @ColumnField(name = "field_default_time", comment = "默认生效时间", type = ColumnType.TIMESTAMP,
                defaultValue = "1970-01-01 00:00:01")
        private Date fieldDefaultTime;

        @ColumnField(name = "field_deleted", comment = "删除位", type = ColumnType.BOOLEAN, defaultValue = "b'0'")
        private Boolean fieldDeleted;
    }

    /** 遮蔽父类桩：自有列 + 继承 MainModel 的 fieldName / fieldCode */
    static class ShadowParent extends MainModel {

        @ColumnField(name = "field_parent", comment = "父类自有列", type = ColumnType.STRING, length = 100)
        private String fieldParent;
    }

    /** 遮蔽子类桩：重声明 fieldName（String，与被遮蔽的 MainModel 字段类型一致）并标注 @ColumnField */
    @Data
    @EqualsAndHashCode(callSuper = true)
    @TableSource(name = "mock_scan_shadow", comment = "扫描测试遮蔽表")
    static class ShadowChild extends ShadowParent {

        @ColumnField(name = "field_name", comment = "名称", type = ColumnType.STRING, length = 200)
        private String fieldName;
    }

    // ---------------------------------------------------------------- 扫描桩类（fail-fast 违例）

    /** 规则 1：表名违反标识符正则 */
    @TableSource(name = "Mock_Scan_Bad", comment = "规则 1 违例表")
    static class BadTableNameEntity extends BaseModel {
    }

    /** 规则 1：列名违反标识符正则 */
    @TableSource(name = "mock_scan_bad_col", comment = "规则 1 违例表")
    static class BadColumnNameEntity extends BaseModel {

        @ColumnField(name = "FieldName", comment = "非法列名", type = ColumnType.STRING, length = 32)
        private String fieldName;
    }

    /** 规则 1：列名缺 field_ 前缀 */
    @TableSource(name = "mock_scan_no_prefix", comment = "规则 1 违例表")
    static class NoPrefixColumnEntity extends BaseModel {

        @ColumnField(name = "remark", comment = "缺前缀列名", type = ColumnType.STRING, length = 32)
        private String fieldRemark;
    }

    /** 规则 1：索引名违反标识符正则（索引列引用内建列，先行通过规则 6 前置） */
    @TableSource(name = "mock_scan_bad_idx_name", comment = "规则 1 违例表",
            indexes = @TableIndex(name = "IDX_BAD", columns = "field_id"))
    static class BadIndexNameEntity extends BaseModel {
    }

    /** 规则 2：列名表内重复（两个不同字段声明同名列） */
    @TableSource(name = "mock_scan_dup_col", comment = "规则 2 违例表")
    static class DuplicateColumnEntity extends BaseModel {

        @ColumnField(name = "field_dup", comment = "第一处", type = ColumnType.STRING, length = 32)
        private String fieldFirst;

        @ColumnField(name = "field_dup", comment = "第二处", type = ColumnType.STRING, length = 64)
        private String fieldSecond;
    }

    /** 规则 2：列名与内建主键冲突 */
    @TableSource(name = "mock_scan_pk_collision", comment = "规则 2 违例表")
    static class FieldIdCollisionEntity extends BaseModel {

        @ColumnField(name = "field_id", comment = "主键重声明", type = ColumnType.STRING, length = 32)
        private String fieldId;
    }

    /** 规则 2：列名与内建审计列冲突 */
    @TableSource(name = "mock_scan_audit_collision", comment = "规则 2 违例表")
    static class EnableCollisionEntity extends MainModel {

        @ColumnField(name = "field_enable", comment = "启用位重声明", type = ColumnType.BOOLEAN)
        private Boolean fieldEnable;
    }

    /** 规则 2：索引名表内重复 */
    @TableSource(name = "mock_scan_dup_idx", comment = "规则 2 违例表",
            indexes = {@TableIndex(name = "uk_dup_idx", columns = "field_id"),
                    @TableIndex(name = "uk_dup_idx", columns = "field_id")})
    static class DuplicateIndexNameEntity extends BaseModel {
    }

    /** 规则 2：表名全局重复 A */
    @TableSource(name = "mock_scan_dup_table", comment = "全局重名表 A")
    static class DuplicateTableEntityA extends BaseModel {
    }

    /** 规则 2：表名全局重复 B */
    @TableSource(name = "mock_scan_dup_table", comment = "全局重名表 B")
    static class DuplicateTableEntityB extends BaseModel {
    }

    /** 规则 3：表 COMMENT 空白 */
    @TableSource(name = "mock_scan_blank_table_comment", comment = " ")
    static class BlankTableCommentEntity extends BaseModel {
    }

    /** 规则 3：列 COMMENT 空白 */
    @TableSource(name = "mock_scan_blank_col_comment", comment = "规则 3 违例表")
    static class BlankColumnCommentEntity extends BaseModel {

        @ColumnField(name = "field_remark", comment = " ", type = ColumnType.STRING, length = 32)
        private String fieldRemark;
    }

    /** 规则 4：STRING 未显式 length */
    @TableSource(name = "mock_scan_str_no_len", comment = "规则 4 违例表")
    static class StringNoLengthEntity extends BaseModel {

        @ColumnField(name = "field_str", comment = "缺长度", type = ColumnType.STRING)
        private String fieldStr;
    }

    /** 规则 4：DECIMAL 未显式 length */
    @TableSource(name = "mock_scan_dec_no_len", comment = "规则 4 违例表")
    static class DecimalNoLengthEntity extends BaseModel {

        @ColumnField(name = "field_amount", comment = "缺精度", type = ColumnType.DECIMAL)
        private BigDecimal fieldAmount;
    }

    /** 规则 5：defaultValue 与 defaultFunction 互斥 */
    @TableSource(name = "mock_scan_default_conflict", comment = "规则 5 违例表")
    static class ConflictingDefaultEntity extends BaseModel {

        @ColumnField(name = "field_start_time", comment = "起始时间", type = ColumnType.TIMESTAMP,
                defaultValue = "1970-01-01", defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
        private Date fieldStartTime;
    }

    /** 规则 5：数字列字面量默认值非数字 */
    @TableSource(name = "mock_scan_bad_int_default", comment = "规则 5 违例表")
    static class IntLiteralMismatchEntity extends BaseModel {

        @ColumnField(name = "field_count", comment = "数量", type = ColumnType.INT, defaultValue = "abc")
        private Integer fieldCount;
    }

    /** 规则 5：时间列字面量默认值非日期格式 */
    @TableSource(name = "mock_scan_bad_date_default", comment = "规则 5 违例表")
    static class DateLiteralMismatchEntity extends BaseModel {

        @ColumnField(name = "field_biz_date", comment = "业务日期", type = ColumnType.DATE, defaultValue = "abc")
        private Date fieldBizDate;
    }

    /** 规则 6：索引列不在该表列集合 */
    @TableSource(name = "mock_scan_unknown_idx_col", comment = "规则 6 违例表",
            indexes = @TableIndex(name = "idx_unknown_col", columns = "field_missing"))
    static class UnknownIndexColumnEntity extends BaseModel {
    }

    /** 规则 7：未继承 BaseModel / MainModel */
    @TableSource(name = "mock_scan_not_base", comment = "规则 7 违例表")
    static class NotBaseModelEntity {

        @ColumnField(name = "field_any", comment = "任意列", type = ColumnType.STRING, length = 32)
        private String fieldAny;
    }

    /** 规则 8：static 字段标注 @ColumnField */
    @TableSource(name = "mock_scan_static_field", comment = "规则 8 违例表")
    static class StaticColumnFieldEntity extends BaseModel {

        @ColumnField(name = "field_static", comment = "静态列", type = ColumnType.STRING, length = 32)
        private static String fieldStatic;
    }

    /** 规则 9 违例父类：继承 MainModel（fieldName 被遮蔽方为 String） */
    static class BadShadowParent extends MainModel {

        @ColumnField(name = "field_parent", comment = "父类自有列", type = ColumnType.STRING, length = 100)
        private String fieldParent;
    }

    /** 规则 9：重声明 fieldName 改变 Java 类型（Integer != String） */
    @TableSource(name = "mock_scan_bad_shadow", comment = "规则 9 违例表")
    static class BadShadowChild extends BadShadowParent {

        @ColumnField(name = "field_name", comment = "名称", type = ColumnType.STRING, length = 200)
        private Integer fieldName;
    }

    /** 未标注 @TableSource（完整性校验） */
    static class PlainEntity {
    }

    // ---------------------------------------------------------------- 公共列模板

    @Test
    void parseBaseModelTemplate() {
        TableDef table = SourceTableScanner.parse(MockBaseEntity.class);

        assertEquals("mock_scan_base", table.getName());
        assertEquals("扫描测试流水表（BaseModel 模板）", table.getComment());
        assertNull(table.getDataSourceName(), "@DataSource 未标注时逻辑数据源名为 null");
        assertEquals(List.of("field_id", "field_remark"), table.getColumns().stream()
                .map(ColumnDef::getName).toList(), "BaseModel 模板仅内建主键固定最前，无审计列");
        assertTrue(table.getIndexes().isEmpty(), "未声明索引时索引清单为空");

        ColumnDef fieldId = table.getColumns().get(0);
        assertEquals("主键ID", fieldId.getComment());
        assertEquals(ColumnType.STRING, fieldId.getType());
        assertEquals(32, fieldId.getLength());
        assertFalse(fieldId.isNullable());
        assertNull(fieldId.getDefaultValue());
        assertEquals(DefaultFunction.NONE, fieldId.getDefaultFunction());
        assertTrue(fieldId.isPrimaryKey(), "内建主键 field_id");

        ColumnDef remark = table.getColumns().get(1);
        assertEquals(ColumnType.STRING, remark.getType());
        assertEquals(200, remark.getLength());
        assertEquals(0, remark.getDecimalLength(), "STRING 列 decimalLength 归一为 0");
        assertTrue(remark.isNullable());
        assertNull(remark.getDefaultValue());
        assertEquals(DefaultFunction.NONE, remark.getDefaultFunction());
        assertFalse(remark.isPrimaryKey(), "业务列非主键");
    }

    @Test
    void parseMainModelTemplate() {
        TableDef table = SourceTableScanner.parse(MockMainEntity.class);

        assertEquals("mock_scan_main", table.getName());
        assertEquals("scan", table.getDataSourceName(), "@DataSource 逻辑数据源名透传");
        assertEquals(List.of("field_id", "field_code", "field_remark", "field_amount", "field_default_time",
                        "field_deleted", "field_creator_id", "field_create_time", "field_updater_id",
                        "field_update_time", "field_enable"),
                table.getColumns().stream().map(ColumnDef::getName).toList(),
                "列清单 = 内建主键（最前）+ 业务列（声明序）+ 内建审计五列（最后）");

        // 内建审计五列属性（§3.3 评审修正口径，对齐 V1_01_046）
        ColumnDef creatorId = column(table, "field_creator_id");
        assertEquals("创建者ID", creatorId.getComment());
        assertEquals(ColumnType.STRING, creatorId.getType());
        assertEquals(32, creatorId.getLength());
        assertTrue(creatorId.isNullable());
        assertNull(creatorId.getDefaultValue());
        assertEquals(DefaultFunction.NONE, creatorId.getDefaultFunction());
        assertFalse(creatorId.isPrimaryKey());

        ColumnDef createTime = column(table, "field_create_time");
        assertEquals(ColumnType.TIMESTAMP, createTime.getType());
        assertFalse(createTime.isNullable());
        assertNull(createTime.getDefaultValue());
        assertEquals(DefaultFunction.CURRENT_TIMESTAMP, createTime.getDefaultFunction());

        ColumnDef updaterId = column(table, "field_updater_id");
        assertEquals(ColumnType.STRING, updaterId.getType());
        assertEquals(32, updaterId.getLength());
        assertTrue(updaterId.isNullable());

        ColumnDef updateTime = column(table, "field_update_time");
        assertEquals(ColumnType.TIMESTAMP, updateTime.getType());
        assertTrue(updateTime.isNullable(), "field_update_time 可空（人工更新标记）");
        assertNull(updateTime.getDefaultValue());
        assertEquals(DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP, updateTime.getDefaultFunction());

        ColumnDef enable = column(table, "field_enable");
        assertEquals(ColumnType.BOOLEAN, enable.getType());
        assertFalse(enable.isNullable());
        assertEquals("b'1'", enable.getDefaultValue(), "BOOLEAN 字面量默认值 b'1'（渲染由 DDL 生成器负责）");
        assertEquals(DefaultFunction.NONE, enable.getDefaultFunction());

        // 业务列属性
        ColumnDef amount = column(table, "field_amount");
        assertEquals(ColumnType.DECIMAL, amount.getType());
        assertEquals(12, amount.getLength());
        assertEquals(2, amount.getDecimalLength());
        assertFalse(amount.isNullable());

        ColumnDef defaultTime = column(table, "field_default_time");
        assertEquals(ColumnType.TIMESTAMP, defaultTime.getType());
        assertFalse(defaultTime.isNullable());
        assertEquals("1970-01-01 00:00:01", defaultTime.getDefaultValue(), "时间列合法字面量默认值透传");
        assertEquals(DefaultFunction.NONE, defaultTime.getDefaultFunction());

        ColumnDef deleted = column(table, "field_deleted");
        assertEquals(ColumnType.BOOLEAN, deleted.getType());
        assertEquals("b'0'", deleted.getDefaultValue(), "BOOLEAN 业务列字面量默认值透传");

        // 索引归一
        assertEquals(2, table.getIndexes().size());
        IndexDef uk = table.getIndexes().get(0);
        assertEquals("uk_scan_main_code", uk.getName());
        assertEquals(List.of("field_code"), uk.getColumns());
        assertTrue(uk.isUnique());
        assertEquals(IndexType.BTREE, uk.getType());
        IndexDef combined = table.getIndexes().get(1);
        assertEquals(List.of("field_remark", "field_enable"), combined.getColumns(),
                "索引列可引用内建审计列（规则 6 列集合含内建列）");
        assertFalse(combined.isUnique());
    }

    // ---------------------------------------------------------------- 字段重声明遮蔽（D5）

    @Test
    void shadowChildTakesOwnDefinitionWithLombokOverride() throws Exception {
        TableDef table = SourceTableScanner.parse(ShadowChild.class);

        assertEquals(List.of("field_id", "field_name", "field_parent", "field_creator_id",
                        "field_create_time", "field_updater_id", "field_update_time", "field_enable"),
                table.getColumns().stream().map(ColumnDef::getName).toList(),
                "子类重声明 fieldName 以子类定义入列（子类先于父类），父类同名字段不重复");
        assertFalse(table.getColumns().stream().anyMatch(def -> def.getName().equals("field_code")),
                "未重声明的 MainModel 继承字段（fieldCode）不进列清单");
        assertEquals(200, column(table, "field_name").getLength(), "列定义取子类重声明，非父类魔法默认");
        assertEquals(100, column(table, "field_parent").getLength(), "父类自有标注列正常入列");

        // Lombok 读写器覆盖父类实现（D5 / R05 行为兼容验证）
        assertEquals(ShadowChild.class, ShadowChild.class.getMethod("getFieldName").getDeclaringClass(),
                "Lombok 为子类重声明字段生成同名读写器");
        MainModel reference = new ShadowChild();
        reference.setFieldName("遮蔽值");
        assertEquals("遮蔽值", reference.getFieldName(), "父类引用经子类读写器访问子类字段");
        assertEquals(ShadowChild.class, ShadowChild.class.getMethod("setFieldName", String.class).getDeclaringClass());
    }

    // ---------------------------------------------------------------- 缓存与不可变

    @Test
    void scanResultCachedSortedAndImmutable() {
        assertSame(SourceTableScanner.parse(MockMainEntity.class), SourceTableScanner.parse(MockMainEntity.class),
                "parse 进程内缓存只归一一次");

        List<TableDef> first = SourceTableScanner.scan(List.of(MockMainEntity.class, MockBaseEntity.class));
        List<TableDef> second = SourceTableScanner.scan(List.of(MockBaseEntity.class, MockMainEntity.class));
        assertEquals(List.of("mock_scan_base", "mock_scan_main"), first.stream().map(TableDef::getName).toList(),
                "scan(List) 按表名升序输出，与入参顺序无关");
        assertEquals(first, second, "表名升序归一后结果一致");

        TableDef extra = new TableDef("mock_scan_extra", "多余表", null, List.of(), List.of());
        assertThrows(UnsupportedOperationException.class, () -> first.add(extra), "扫描结果不可变");
        ColumnDef extraColumn = new ColumnDef("field_extra", "多余列", ColumnType.STRING, 1, 0,
                false, null, DefaultFunction.NONE, false);
        assertThrows(UnsupportedOperationException.class, () -> first.get(0).getColumns().add(extraColumn),
                "列清单不可变");
        assertThrows(UnsupportedOperationException.class, () -> first.get(1).getIndexes().add(
                        new IndexDef("idx_extra", List.of("field_id"), false, IndexType.BTREE)),
                "索引清单不可变");
    }

    // ---------------------------------------------------------------- §3.5 规则 1：标识符正则与前缀

    @Test
    void tableNameAgainstIdentifierPatternFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(BadTableNameEntity.class));
        assertTrue(error.getMessage().contains("表名不符合标识符规范"));
        assertTrue(error.getMessage().contains("Mock_Scan_Bad"));
    }

    @Test
    void columnNameAgainstIdentifierPatternFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(BadColumnNameEntity.class));
        assertTrue(error.getMessage().contains("列名不符合标识符规范"));
        assertTrue(error.getMessage().contains("FieldName"));
    }

    @Test
    void columnNameWithoutFieldPrefixFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(NoPrefixColumnEntity.class));
        assertTrue(error.getMessage().contains("列名必须以 field_ 为前缀"));
    }

    @Test
    void indexNameAgainstIdentifierPatternFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(BadIndexNameEntity.class));
        assertTrue(error.getMessage().contains("索引名不符合标识符规范"));
        assertTrue(error.getMessage().contains("IDX_BAD"));
    }

    // ---------------------------------------------------------------- §3.5 规则 2：唯一性

    @Test
    void duplicateColumnNameFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(DuplicateColumnEntity.class));
        assertTrue(error.getMessage().contains("列名表内唯一违例"));
        assertTrue(error.getMessage().contains("field_dup"));
    }

    @Test
    void builtinPrimaryKeyCollisionFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(FieldIdCollisionEntity.class));
        assertTrue(error.getMessage().contains("列名表内唯一违例"), "业务列不得与内建主键同名");
    }

    @Test
    void builtinAuditColumnCollisionFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(EnableCollisionEntity.class));
        assertTrue(error.getMessage().contains("列名表内唯一违例"), "业务列不得与内建审计列同名");
    }

    @Test
    void duplicateIndexNameFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(DuplicateIndexNameEntity.class));
        assertTrue(error.getMessage().contains("索引名表内唯一违例"));
    }

    @Test
    void duplicateTableNameFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.scan(List.of(DuplicateTableEntityA.class, DuplicateTableEntityB.class)));
        assertTrue(error.getMessage().contains("表名全局唯一违例"));
        assertTrue(error.getMessage().contains("mock_scan_dup_table"));
    }

    // ---------------------------------------------------------------- §3.5 规则 3：comment 必填非空

    @Test
    void blankTableCommentFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(BlankTableCommentEntity.class));
        assertTrue(error.getMessage().contains("表 COMMENT 必填非空"));
    }

    @Test
    void blankColumnCommentFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(BlankColumnCommentEntity.class));
        assertTrue(error.getMessage().contains("列 COMMENT 必填非空"));
    }

    // ---------------------------------------------------------------- §3.5 规则 4：显式 length

    @Test
    void stringColumnWithoutLengthFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(StringNoLengthEntity.class));
        assertTrue(error.getMessage().contains("STRING 列必须显式 length ≥ 1"));
    }

    @Test
    void decimalColumnWithoutLengthFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(DecimalNoLengthEntity.class));
        assertTrue(error.getMessage().contains("DECIMAL 列必须显式 length ≥ 1"));
    }

    // ---------------------------------------------------------------- §3.5 规则 5：默认值

    @Test
    void defaultValueAndFunctionConflictFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(ConflictingDefaultEntity.class));
        assertTrue(error.getMessage().contains("defaultValue 与 defaultFunction 互斥"));
    }

    @Test
    void numericLiteralMismatchFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(IntLiteralMismatchEntity.class));
        assertTrue(error.getMessage().contains("defaultValue 字面量与列类型不匹配"));
    }

    @Test
    void timeLiteralMismatchFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(DateLiteralMismatchEntity.class));
        assertTrue(error.getMessage().contains("defaultValue 字面量与列类型不匹配"));
    }

    // ---------------------------------------------------------------- §3.5 规则 6：索引列子集

    @Test
    void unknownIndexColumnFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(UnknownIndexColumnEntity.class));
        assertTrue(error.getMessage().contains("索引列必须是该表列集合的子集"));
        assertTrue(error.getMessage().contains("field_missing"));
    }

    // ---------------------------------------------------------------- §3.5 规则 7：基类约束

    @Test
    void notExtendingBaseModelFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(NotBaseModelEntity.class));
        assertTrue(error.getMessage().contains("标注类必须继承 BaseModel/MainModel"));
    }

    // ---------------------------------------------------------------- §3.5 规则 8：static/synthetic

    @Test
    void staticColumnFieldFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(StaticColumnFieldEntity.class));
        assertTrue(error.getMessage().contains("@ColumnField 不允许标注在 static/synthetic 字段"));
    }

    // ---------------------------------------------------------------- §3.5 规则 9：重声明类型一致

    @Test
    void shadowedFieldTypeMismatchFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(BadShadowChild.class));
        assertTrue(error.getMessage().contains("重声明字段类型与被遮蔽的父类字段不一致"));
        assertTrue(error.getMessage().contains("fieldName"));
    }

    // ---------------------------------------------------------------- 完整性

    @Test
    void parseWithoutTableSourceFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SourceTableScanner.parse(PlainEntity.class));
        assertTrue(error.getMessage().contains("未标注 @TableSource"));
    }

    /**
     * 按列名取列定义（断言辅助）
     */
    private static ColumnDef column(TableDef table, String name) {
        return table.getColumns().stream().filter(def -> def.getName().equals(name)).findFirst().orElseThrow();
    }
}

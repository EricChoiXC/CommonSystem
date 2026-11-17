package com.eric.base.common.source.tool;

import com.eric.base.common.source.diff.ActualTable;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.model.ColumnDef;
import com.eric.base.common.source.model.IndexDef;
import com.eric.base.common.source.model.TableDef;
import com.eric.base.common.source.scanner.SourceTableScanner;
import com.eric.base.sys.datasource.model.SysDatasource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 反向生成工具守卫式 DB 等价性校验（本机 MySQL common 开发库，只读）
 *
 * @author Eric
 * @date 2026/11/17
 * @description 覆盖任务 F19-BE-008 验收标准「对试点表输出片段与手写示例等价」的机器口径：
 *              经 DriverManagerDataSource 直连本机 MySQL common 开发库（root/root），
 *              information_schema 三源读取整体复用 {@link SourceSchemaDiffer}（经
 *              {@link SourceSchemaExportTool#loadActualTables}），对试点表 sys_datasource
 *              断言生成片段与 {@link SourceTableScanner} 扫描归一的 TableDef 逐列逐索引
 *              等价——每个业务列存在属性吻合的 @ColumnField（name/comment/type/length/
 *              decimalLength/nullable/defaultValue/defaultFunction 全对上）、主键与审计
 *              五列不出现在片段、两个 @TableIndex 名称/列/unique 吻合、片段无任何警示。
 *              <p>再抽三张真实表宽松断言跑通：sys_dict_item（bit 双默认值 + 部分审计列
 *              field_enable + 双普通索引）、sys_process_template（生成列 field_default_unique
 *              警示 + MainModel 审计剔除 + 审计列与模板偏差警示）、sys_number_record
 *              （BaseModel 流水表 + 复合唯一索引 + 业务列 CURRENT_TIMESTAMP 函数默认值）。
 *              <p><strong>守卫</strong>：MySQL 连不上（未监听/凭据不符）时 assumeTrue 跳过，
 *              保证其他环境 {@code mvnw test} 不红；不启动整个 Spring 应用（无 Spring 上下文，
 *              对齐 SysProcessTemplateDefaultBackfillMigrationDbCheckTest 先例）。
 *              <strong>只读</strong>：全部语句为 information_schema 与 SELECT，不写任何数据、
 *              不建临时 schema（information_schema 查询本身无副作用）。</p>
 */
class SourceSchemaExportToolDbCheckTest {

    /** 本机 MySQL common 开发库连接（只读使用） */
    private static final String DB_URL = "jdbc:mysql://localhost:3306/common"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";

    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";

    /** 整库 ActualTable（@BeforeAll 一次读取，只读） */
    private static Map<String, ActualTable> actualTables;

    /** 只读 JdbcTemplate（缺省表清单查询用） */
    private static JdbcTemplate jdbcTemplate;

    /** MySQL 可用标记（@BeforeAll 探测，各 @Test 经 assumeTrue 守卫） */
    private static boolean available;

    /** 连接失败原因（skip 报告可读性） */
    private static String unavailableReason;

    @BeforeAll
    static void loadActualSchema() {
        available = false;
        try {
            jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD));
            // 探活（DriverManagerDataSource 惰性建连，须执行一条 SELECT 才暴露连接问题）
            jdbcTemplate.queryForList("SELECT 1");
        } catch (DataAccessException e) {
            unavailableReason = e.getMessage();
            return;
        }
        actualTables = SourceSchemaExportTool.loadActualTables(jdbcTemplate);
        available = true;
    }

    /* ==================== 试点表等价性（机器口径） ==================== */

    @Test
    void sysDatasourceSnippetEquivalentToScannerTableDef() {
        assumeTrue(available, () -> "本机 MySQL 不可用（" + unavailableReason + "），等价性校验跳过");

        TableDef expected = SourceTableScanner.parse(SysDatasource.class);
        ActualTable actual = actualTables.get("sys_datasource");
        assertNotNull(actual, "common 开发库应存在试点表 sys_datasource");
        SourceSchemaExportTool.TableSnippet snippet = SourceSchemaExportTool.buildSnippet(actual);

        // 表名与 COMMENT 吻合（Scanner 表 COMMENT 取自注解、片段取自 TABLES.TABLE_COMMENT）
        assertEquals(expected.getName(), snippet.getTableName());
        assertEquals(expected.getComment(), snippet.getTableComment());

        // 公共列不混入：主键与审计五列不产 @ColumnField（引擎公共列模板内建）
        List<String> builtin = new ArrayList<>(SourceSchemaExportTool.AUDIT_COLUMNS);
        builtin.add("field_id");
        for (SourceSchemaExportTool.ColumnSnippet column : snippet.getColumns()) {
            assertFalse(builtin.contains(column.getName()),
                    "公共列不得出现在片段: " + column.getName());
        }

        // 逐列等价：片段业务列 ↔ TableDef 业务列（属性全对上）
        Map<String, ColumnDef> business = expected.getColumns().stream()
                .filter(column -> !column.isPrimaryKey() && !SourceSchemaExportTool.AUDIT_COLUMNS.contains(column.getName()))
                .collect(Collectors.toMap(ColumnDef::getName, Function.identity()));
        assertEquals(business.keySet(),
                snippet.getColumns().stream().map(SourceSchemaExportTool.ColumnSnippet::getName).collect(Collectors.toSet()),
                "片段业务列与注解侧业务列集合一致");
        for (SourceSchemaExportTool.ColumnSnippet column : snippet.getColumns()) {
            ColumnDef running = business.get(column.getName());
            assertEquals(running.getComment(), column.getComment(), column.getName() + " COMMENT");
            assertEquals(running.getType(), column.getType(), column.getName() + " type");
            assertEquals(running.getLength(), column.getLength(), column.getName() + " length");
            assertEquals(running.getDecimalLength(), column.getDecimalLength(), column.getName() + " decimalLength");
            assertEquals(running.isNullable(), column.isNullable(), column.getName() + " nullable");
            assertEquals(running.getDefaultValue(), column.getDefaultValue(), column.getName() + " defaultValue");
            assertEquals(running.getDefaultFunction(), column.getDefaultFunction(), column.getName() + " defaultFunction");
        }

        // 逐索引等价：名称/列（含组合序）/unique 吻合，数量一致（PRIMARY 已剔除）
        Map<String, IndexDef> indexDefs = expected.getIndexes().stream()
                .collect(Collectors.toMap(IndexDef::getName, Function.identity()));
        assertEquals(indexDefs.keySet(),
                snippet.getIndexes().stream().map(SourceSchemaExportTool.IndexSnippet::getName).collect(Collectors.toSet()),
                "片段索引与注解侧索引集合一致");
        for (SourceSchemaExportTool.IndexSnippet index : snippet.getIndexes()) {
            IndexDef running = indexDefs.get(index.getName());
            assertEquals(running.getColumns(), index.getColumns(), index.getName() + " columns");
            assertEquals(running.isUnique(), index.isUnique(), index.getName() + " unique");
        }

        // 试点表审计列与模板逐维一致、无不可表达列 → 片段无警示
        assertTrue(snippet.getWarnings().isEmpty(), "sys_datasource 片段不应有警示: " + snippet.getWarnings());

        // 渲染文本抽查：类级注解/索引/列注解/字段声明/建议基类行
        String rendered = SourceSchemaExportTool.renderTable(snippet);
        assertTrue(rendered.contains("@TableSource(name = \"sys_datasource\", comment = \"数据源表（自定义数据源配置）\","));
        assertTrue(rendered.contains("@TableIndex(name = \"uk_datasource_code\", columns = \"field_code\", unique = true)"));
        assertTrue(rendered.contains("@TableIndex(name = \"idx_datasource_target\", columns = \"field_target\")"));
        assertTrue(rendered.contains("@ColumnField(name = \"field_conditions\", comment = \"固定条件 JSON：[{key,op,value}]，"
                + "value 支持 ${CURRENT_USER_ID} 等运行时变量\", type = ColumnType.JSON, nullable = true)"));
        assertTrue(rendered.contains("@ColumnField(name = \"field_remark\", comment = \"备注\", "
                + "type = ColumnType.STRING, length = 500, nullable = true)"));
        assertTrue(rendered.contains("public class SysDatasource extends MainModel {"));
        assertTrue(rendered.contains("private String fieldCode;"));
        assertTrue(rendered.contains("private Object fieldConditions;"));
    }

    /* ==================== 抽验表宽松断言 ==================== */

    @Test
    void sampleTablesGenerateWithGeneratedColumnBitAndCompositeIndexCoverage() {
        assumeTrue(available, () -> "本机 MySQL 不可用（" + unavailableReason + "），抽验跳过");

        /* sys_dict_item：bit 双默认值列 + 部分审计列（field_enable）+ 双普通索引 */
        SourceSchemaExportTool.TableSnippet dictItem = buildSnippet("sys_dict_item");
        assertEquals("BaseModel", dictItem.getSuggestedBaseClass(), "sys_dict_item 无完整审计列");
        assertTrue(dictItem.isBaseClassMismatch(), "仅含 field_enable 部分审计列，与两模板均不匹配");
        assertEquals(List.of("field_type_id", "field_value", "field_name", "field_enable", "field_system"),
                dictItem.getColumns().stream().map(SourceSchemaExportTool.ColumnSnippet::getName).toList(),
                "业务列沿 ORDINAL_POSITION 序，field_enable 按业务列标注（剔除必产缺失列 diff）");
        SourceSchemaExportTool.ColumnSnippet enable = column(dictItem, "field_enable");
        assertEquals(ColumnType.BOOLEAN, enable.getType());
        assertEquals("b'1'", enable.getDefaultValue());
        assertFalse(enable.isNullable());
        SourceSchemaExportTool.ColumnSnippet system = column(dictItem, "field_system");
        assertEquals("b'0'", system.getDefaultValue());
        assertEquals(2, dictItem.getIndexes().size());
        String dictItemText = SourceSchemaExportTool.renderTable(dictItem);
        assertTrue(dictItemText.contains("@TableIndex(name = \"idx_dict_item_type\", columns = \"field_type_id\")"));

        /* sys_process_template：生成列警示 + MainModel 审计剔除 + 审计列偏差警示 */
        SourceSchemaExportTool.TableSnippet processTemplate = buildSnippet("sys_process_template");
        assertEquals("MainModel", processTemplate.getSuggestedBaseClass());
        List<String> templateNames = processTemplate.getColumns().stream()
                .map(SourceSchemaExportTool.ColumnSnippet::getName).toList();
        assertEquals(List.of("field_name", "field_model_name", "field_code", "field_version", "field_remark",
                        "field_default"), templateNames,
                "审计五列剔除、生成列 field_default_unique 不产 @ColumnField");
        assertTrue(templateNames.stream().noneMatch(SourceSchemaExportTool.AUDIT_COLUMNS::contains),
                "无审计列混入");
        assertTrue(processTemplate.getWarnings().stream().anyMatch(w -> w.contains("// 生成列：field_default_unique")
                        && w.contains("引擎暂不支持生成列标注")),
                "生成列警示注释行，实际警示: " + processTemplate.getWarnings());
        assertTrue(processTemplate.getWarnings().stream().anyMatch(w -> w.contains("field_update_time")
                        && w.contains("与公共列模板（设计方案 §3.3）不一致")),
                "实测该表 field_update_time 无 DEFAULT/ON UPDATE，模板偏差须警示");
        SourceSchemaExportTool.ColumnSnippet defaultColumn = column(processTemplate, "field_default");
        assertEquals(ColumnType.BOOLEAN, defaultColumn.getType());
        assertEquals("b'0'", defaultColumn.getDefaultValue(), "bit DEFAULT 0 经库归一为 b'0'");
        assertTrue(defaultColumn.isNullable());
        assertEquals("1", column(processTemplate, "field_version").getDefaultValue());
        SourceSchemaExportTool.IndexSnippet uk = processTemplate.getIndexes().stream()
                .filter(index -> index.getName().equals("uk_sys_process_template_default")).findFirst().orElseThrow();
        assertTrue(uk.isUnique());
        assertEquals(List.of("field_default_unique"), uk.getColumns());
        assertTrue(uk.isReferencesGenerated(), "引用生成列的索引须标记人工处置");
        String templateText = SourceSchemaExportTool.renderTable(processTemplate);
        assertTrue(templateText.contains("// 引用生成列（引擎暂不支持生成列标注），请人工处置"));
        assertTrue(templateText.contains("@TableIndex(name = \"idx_template_enable\", columns = \"field_enable\")"),
                "索引可引用内建审计列（扫描器规则 6 含内建列）");

        /* sys_number_record：BaseModel 流水表 + 复合唯一索引 + 业务列 CURRENT_TIMESTAMP */
        SourceSchemaExportTool.TableSnippet numberRecord = buildSnippet("sys_number_record");
        assertEquals("BaseModel", numberRecord.getSuggestedBaseClass());
        assertFalse(numberRecord.isBaseClassMismatch());
        assertEquals(List.of("field_rule_id", "field_scope_key", "field_serial_id", "field_period",
                        "field_serial_no", "field_record_time", "field_record_finish_time"),
                numberRecord.getColumns().stream().map(SourceSchemaExportTool.ColumnSnippet::getName).toList(),
                "流水表无审计列，业务列沿 ORDINAL_POSITION 序");
        SourceSchemaExportTool.ColumnSnippet recordTime = column(numberRecord, "field_record_time");
        assertEquals(ColumnType.TIMESTAMP, recordTime.getType());
        assertEquals(DefaultFunction.CURRENT_TIMESTAMP, recordTime.getDefaultFunction(),
                "业务列函数式默认值（DEFAULT CURRENT_TIMESTAMP）");
        SourceSchemaExportTool.ColumnSnippet finishTime = column(numberRecord, "field_record_finish_time");
        assertEquals(DefaultFunction.NONE, finishTime.getDefaultFunction());
        assertNull(finishTime.getDefaultValue(), "封存时间 NULL=使用中，无默认值");
        assertTrue(finishTime.isNullable());
        SourceSchemaExportTool.IndexSnippet scopeUk = numberRecord.getIndexes().stream()
                .filter(index -> index.getName().equals("uk_number_record_scope")).findFirst().orElseThrow();
        assertTrue(scopeUk.isUnique());
        assertEquals(List.of("field_scope_key", "field_serial_id", "field_period"), scopeUk.getColumns(),
                "复合索引按 SEQ_IN_INDEX 序");
        String recordText = SourceSchemaExportTool.renderTable(numberRecord);
        assertTrue(recordText.contains("@TableIndex(name = \"uk_number_record_scope\", "
                + "columns = {\"field_scope_key\", \"field_serial_id\", \"field_period\"}, unique = true)"),
                "多列以注解 String[] 属性为准，实际: " + recordText);
        assertTrue(recordText.contains("private LocalDateTime fieldRecordTime;"));
        assertTrue(recordText.contains("@ColumnField(name = \"field_scope_key\", comment = \"取号作用域键（固定接入=规则field_id，"
                + "非固定接入={modelName}:{宿主记录ID}）\", type = ColumnType.STRING, length = 200)"));
    }

    /* ==================== 缺省表清单排除口径 ==================== */

    @Test
    void defaultTableListExcludesReservedTables() {
        assumeTrue(available, () -> "本机 MySQL 不可用（" + unavailableReason + "），清单口径校验跳过");

        List<String> names = SourceSchemaExportTool.resolveTableNames(
                SourceSchemaExportTool.parseArgs(new String[0], Map.of()), jdbcTemplate);
        assertTrue(names.contains("sys_datasource"));
        assertFalse(names.contains("flyway_schema_history"), "Flyway 历史留档表不进缺省清单");
        assertFalse(names.contains("common_source_sync_log"), "引擎审计表不进缺省清单（§5.3 元数据表不纳入注解系统）");
        assertFalse(names.contains("common_source_migration_log"), "引擎审计表不进缺省清单");
        assertEquals(names.stream().sorted().toList(), names, "缺省清单按表名升序稳定输出");
    }

    /* ==================== 断言辅助 ==================== */

    /** 取单表片段（不存在即失败） */
    private SourceSchemaExportTool.TableSnippet buildSnippet(String tableName) {
        ActualTable actual = actualTables.get(tableName);
        assertNotNull(actual, "common 开发库应存在表 " + tableName);
        return SourceSchemaExportTool.buildSnippet(actual);
    }

    /** 按列名取列片段 */
    private static SourceSchemaExportTool.ColumnSnippet column(SourceSchemaExportTool.TableSnippet snippet,
                                                               String name) {
        return snippet.getColumns().stream().filter(column -> column.getName().equals(name)).findFirst().orElseThrow();
    }
}

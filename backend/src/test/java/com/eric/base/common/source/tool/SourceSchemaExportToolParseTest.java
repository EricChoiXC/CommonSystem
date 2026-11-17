package com.eric.base.common.source.tool;

import com.eric.base.common.source.diff.ActualColumn;
import com.eric.base.common.source.diff.ActualIndex;
import com.eric.base.common.source.diff.ActualTable;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 反向生成工具纯逻辑测试（不连 MySQL）
 *
 * @author Eric
 * @date 2026/11/17
 * @description 覆盖任务 F19-BE-008 的纯逻辑验收面：类型字符串解析（varchar 带长/不带长、
 *              decimal 精度标度、json/text、bit、timestamp 及超集类型不可表达）、默认值
 *              解析（字面量/引号转义/CURRENT_TIMESTAMP 含 () 形态/ON UPDATE/NULL 无默认/
 *              bit b'0' 字面量）、索引渲染（单列/多列/unique/PRIMARY 防御剔除）、公共列
 *              剔除（MainModel 模板五列 / BaseModel 仅主键 / 部分审计列按业务列标注）、
 *              生成列警示（DEFAULT_GENERATED 不误判——MySQL 8.0+ 函数式默认值也带该标记）、
 *              camelCase/PascalCase 转换、参数解析优先级（命令行 &gt; 环境变量 &gt; 兜底）。
 *              全部以内存构造 ActualTable/ActualColumn/ActualIndex 驱动
 *              {@link SourceSchemaExportTool#buildSnippet}（纯函数）。
 */
class SourceSchemaExportToolParseTest {

    /* ==================== 构造工具（对齐 SourceSchemaDifferTest 风格） ==================== */

    /** 实际列全字段构造 */
    private static ActualColumn col(String name, String columnType, boolean nullable, String columnDefault,
                                    String extra, String comment, Integer charMax, Integer precision, Integer scale) {
        return new ActualColumn(name, baseType(columnType), columnType, nullable, columnDefault, extra,
                comment, charMax, precision, scale);
    }

    /** DATA_TYPE 推导（COLUMN_TYPE 括号前类型名） */
    private static String baseType(String columnType) {
        int paren = columnType.indexOf('(');
        return paren < 0 ? columnType : columnType.substring(0, paren);
    }

    /** varchar 实际列便捷构造 */
    private static ActualColumn varchar(String name, int length, boolean nullable, String columnDefault,
                                        String comment) {
        return col(name, "varchar(" + length + ")", nullable, columnDefault, "", comment, length, null, null);
    }

    /** 与公共列模板一致的 field_id 主键列 */
    private static ActualColumn fieldId() {
        return col("field_id", "varchar(32)", false, null, "", "主键ID", 32, null, null);
    }

    /** 与公共列模板（设计方案 §3.3）逐一一致的审计五列（实测库形态，含 DEFAULT_GENERATED 标记） */
    private static List<ActualColumn> auditFive() {
        return List.of(
                col("field_creator_id", "varchar(32)", true, null, "", "创建者ID", 32, null, null),
                col("field_create_time", "timestamp", false, "CURRENT_TIMESTAMP", "DEFAULT_GENERATED", "创建时间",
                        null, null, null),
                col("field_updater_id", "varchar(32)", true, null, "", "修改者ID", 32, null, null),
                col("field_update_time", "timestamp", true, "CURRENT_TIMESTAMP",
                        "DEFAULT_GENERATED on update CURRENT_TIMESTAMP", "修改时间", null, null, null),
                col("field_enable", "bit(1)", false, "b'1'", "", "是否启用", null, null, null));
    }

    /** 实际表构造 */
    private static ActualTable table(String name, String comment, List<ActualColumn> columns,
                                     List<ActualIndex> indexes) {
        return new ActualTable(name, "InnoDB", comment, "utf8mb4_0900_ai_ci", columns, indexes);
    }

    /** 实际索引构造 */
    private static ActualIndex index(String name, boolean unique, String... columns) {
        return new ActualIndex(name, unique, List.of(columns), "BTREE");
    }

    /** 单业务列最小表（BaseModel 形态：field_id + 单业务列，无索引） */
    private static SourceSchemaExportTool.TableSnippet snippetOf(ActualColumn business) {
        List<ActualColumn> columns = new ArrayList<>();
        columns.add(fieldId());
        columns.add(business);
        return SourceSchemaExportTool.buildSnippet(table("snippet_check", "片段测试表", columns, List.of()));
    }

    /* ==================== 类型字符串解析 ==================== */

    @Test
    void parseTypeCoversAnnotatableTypeSet() {
        // varchar 带长 → STRING + length；不带长（实测不可能，防御）不可表达
        SourceSchemaExportTool.ParsedType varchar = SourceSchemaExportTool.parseType("varchar(100)");
        assertNotNull(varchar);
        assertEquals(ColumnType.STRING, varchar.getType());
        assertEquals(100, varchar.getLength());
        assertNull(SourceSchemaExportTool.parseType("varchar"), "STRING 必须显式长度（扫描器规则 4），不带长不可表达");
        // decimal 精度标度
        SourceSchemaExportTool.ParsedType decimal = SourceSchemaExportTool.parseType("decimal(10,2)");
        assertNotNull(decimal);
        assertEquals(ColumnType.DECIMAL, decimal.getType());
        assertEquals(10, decimal.getLength());
        assertEquals(2, decimal.getDecimalLength());
        SourceSchemaExportTool.ParsedType decimalNoScale = SourceSchemaExportTool.parseType("decimal(10)");
        assertNotNull(decimalNoScale);
        assertEquals(10, decimalNoScale.getLength());
        assertEquals(0, decimalNoScale.getDecimalLength());
        // json / text / bit / timestamp / datetime / date / int / bigint
        assertEquals(ColumnType.JSON, SourceSchemaExportTool.parseType("json").getType());
        assertEquals(ColumnType.TEXT, SourceSchemaExportTool.parseType("text").getType());
        assertEquals(ColumnType.BOOLEAN, SourceSchemaExportTool.parseType("bit(1)").getType());
        assertEquals(ColumnType.TIMESTAMP, SourceSchemaExportTool.parseType("timestamp").getType());
        assertEquals(ColumnType.DATETIME, SourceSchemaExportTool.parseType("datetime").getType());
        assertEquals(ColumnType.DATE, SourceSchemaExportTool.parseType("date").getType());
        assertEquals(ColumnType.INT, SourceSchemaExportTool.parseType("int").getType());
        assertEquals(ColumnType.BIGINT, SourceSchemaExportTool.parseType("bigint").getType());
        // 超集类型不可表达（实测库 sys_log_operation.field_before/field_after longtext）
        assertNull(SourceSchemaExportTool.parseType("longtext"));
        assertNull(SourceSchemaExportTool.parseType("bit(2)"), "BOOLEAN 仅 bit(1)（Differ 精细比对口径）");
        assertNull(SourceSchemaExportTool.parseType("tinyint"));
        assertNull(SourceSchemaExportTool.parseType("intunsigned"));
    }

    @Test
    void unsupportedTypeColumnYieldsWarningInsteadOfAnnotation() {
        // longtext 列：不产 @ColumnField，输出警示注释行提示人工处置
        SourceSchemaExportTool.TableSnippet snippet = snippetOf(
                col("field_before", "longtext", true, null, "", "变更前JSON(按需)", null, null, null));
        assertTrue(snippet.getColumns().isEmpty(), "不可表达列不产 @ColumnField");
        assertEquals(1, snippet.getWarnings().size());
        assertTrue(snippet.getWarnings().get(0).contains("field_before")
                && snippet.getWarnings().get(0).contains("longtext")
                && snippet.getWarnings().get(0).contains("超出注解类型集"));
    }

    /* ==================== 默认值解析 ==================== */

    @Test
    void parseDefaultsCoversLiteralFunctionAndNullForms() {
        // 数字字面量（DEFAULT 1）
        SourceSchemaExportTool.ColumnSnippet intDefault = snippetOf(
                col("field_version", "int", false, "1", "", "版本号", null, null, null)).getColumns().get(0);
        assertEquals("1", intDefault.getDefaultValue());
        assertEquals(DefaultFunction.NONE, intDefault.getDefaultFunction());
        // 字符串字面量（Differ 归一后原文，如 'PERSONAL' → PERSONAL）
        SourceSchemaExportTool.ColumnSnippet stringDefault = snippetOf(
                varchar("field_type", 20, true, "PERSONAL", "类型")).getColumns().get(0);
        assertEquals("PERSONAL", stringDefault.getDefaultValue());
        // bit 字面量（b'0'）
        SourceSchemaExportTool.ColumnSnippet bitDefault = snippetOf(
                col("field_default", "bit(1)", true, "b'0'", "", "是否默认", null, null, null)).getColumns().get(0);
        assertEquals("b'0'", bitDefault.getDefaultValue());
        // 空串默认值（区分哨兵 DEFAULT_NONE 的语义场景）
        SourceSchemaExportTool.ColumnSnippet emptyDefault = snippetOf(
                varchar("field_key", 50, true, "", "键")).getColumns().get(0);
        assertEquals("", emptyDefault.getDefaultValue());
        // CURRENT_TIMESTAMP（含 current_timestamp() 形态）
        SourceSchemaExportTool.ColumnSnippet functionDefault = snippetOf(
                col("field_record_time", "timestamp", false, "CURRENT_TIMESTAMP", "DEFAULT_GENERATED", "取号时间",
                        null, null, null)).getColumns().get(0);
        assertEquals(DefaultFunction.CURRENT_TIMESTAMP, functionDefault.getDefaultFunction());
        assertNull(functionDefault.getDefaultValue(), "函数式默认值与字面量互斥");
        assertEquals(DefaultFunction.CURRENT_TIMESTAMP, snippetOf(
                col("field_time2", "timestamp", false, "current_timestamp()", "DEFAULT_GENERATED", "时间",
                        null, null, null)).getColumns().get(0).getDefaultFunction());
        // ON UPDATE 子句（EXTRA 含 on update CURRENT_TIMESTAMP）
        SourceSchemaExportTool.ColumnSnippet onUpdate = snippetOf(
                col("field_update_time", "timestamp", true, "CURRENT_TIMESTAMP",
                        "DEFAULT_GENERATED on update CURRENT_TIMESTAMP", "修改时间", null, null, null))
                .getColumns().get(0);
        assertEquals(DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP, onUpdate.getDefaultFunction());
        // NULL 无默认（TEXT/JSON 的 COLUMN_DEFAULT 为 NULL 表示无默认）
        SourceSchemaExportTool.ColumnSnippet noDefault = snippetOf(
                col("field_conditions", "json", true, null, "", "固定条件", null, null, null)).getColumns().get(0);
        assertNull(noDefault.getDefaultValue());
        assertEquals(DefaultFunction.NONE, noDefault.getDefaultFunction());
    }

    @Test
    void quoteEscapedDefaultRendersJavaEscapedLiteral() {
        // 字面量含双引号/反斜杠：结构化值保留原文，渲染时做 Java 转义（round-trip 依赖
        // Differ 归一 + DDL 渲染再转义的对称口径）
        SourceSchemaExportTool.TableSnippet snippet = snippetOf(
                varchar("field_expr", 100, true, "a\"b\\c", "表达式"));
        assertEquals("a\"b\\c", snippet.getColumns().get(0).getDefaultValue());
        String rendered = SourceSchemaExportTool.renderTable(snippet);
        assertTrue(rendered.contains("defaultValue = \"a\\\"b\\\\c\""),
                "Java 字面量转义：\" → \\\"、\\ → \\\\，实际输出: " + rendered);
    }

    /* ==================== 索引渲染 ==================== */

    @Test
    void indexRenderingCoversSingleMultiUniqueAndSkipsPrimary() {
        List<ActualColumn> columns = new ArrayList<>();
        columns.add(fieldId());
        columns.add(varchar("field_code", 100, false, null, "编码"));
        columns.add(varchar("field_a", 100, false, null, "列A"));
        columns.add(varchar("field_b", 100, false, null, "列B"));
        ActualTable actual = table("index_check", "索引测试表", columns, List.of(
                index("PRIMARY", true, "field_id"),
                index("idx_check_a", false, "field_a", "field_b"),
                index("uk_check_code", true, "field_code")));
        SourceSchemaExportTool.TableSnippet snippet = SourceSchemaExportTool.buildSnippet(actual);
        assertEquals(2, snippet.getIndexes().size(), "PRIMARY 防御剔除（differ 聚合层已滤，此处双保险）");
        assertEquals("idx_check_a", snippet.getIndexes().get(0).getName());
        assertTrue(snippet.getIndexes().get(0).getColumns().contains("field_a")
                && snippet.getIndexes().get(0).getColumns().contains("field_b"));
        assertEquals("uk_check_code", snippet.getIndexes().get(1).getName());
        assertTrue(snippet.getIndexes().get(1).isUnique());
        String rendered = SourceSchemaExportTool.renderTable(snippet);
        // 多列以注解 String[] 属性为准；unique 仅 true 时输出；注解括号闭合（indexes = {...} 后单右括号）
        assertTrue(rendered.contains("@TableIndex(name = \"idx_check_a\", columns = {\"field_a\", \"field_b\"})"));
        assertTrue(rendered.contains("@TableIndex(name = \"uk_check_code\", columns = \"field_code\", unique = true)})"),
                "@TableSource 括号闭合形态（索引注解右括号 + indexes 数组右花括号 + 注解右括号），实际: " + rendered);
        assertFalse(rendered.contains("\"PRIMARY\""), "主键索引不输出");
    }

    /* ==================== 公共列剔除 ==================== */

    @Test
    void commonColumnsExcludedForMainModelTemplate() {
        List<ActualColumn> columns = new ArrayList<>();
        columns.add(fieldId());
        columns.addAll(auditFive());
        columns.add(varchar("field_remark", 500, true, null, "备注"));
        columns.add(col("field_version", "int", false, "1", "", "版本号", null, null, null));
        SourceSchemaExportTool.TableSnippet snippet = SourceSchemaExportTool.buildSnippet(
                table("common_check", "公共列测试表", columns, List.of()));
        // 仅业务列保留，field_id 与审计五列不产 @ColumnField（引擎公共列模板内建）
        assertEquals(List.of("field_remark", "field_version"),
                snippet.getColumns().stream().map(SourceSchemaExportTool.ColumnSnippet::getName).toList());
        assertTrue(snippet.getWarnings().isEmpty(), "审计五列与模板一致时无警示");
        String rendered = SourceSchemaExportTool.renderTable(snippet);
        assertTrue(rendered.contains("public class CommonCheck extends MainModel {"));
        assertTrue(rendered.contains("// field_id（主键）与审计五列由公共列模板内建（设计方案 §3.3），无需标注"));
        assertFalse(rendered.contains("fieldCreatorId"), "审计列不产字段声明行");
    }

    /* ==================== 生成列警示 ==================== */

    @Test
    void generatedColumnYieldsWarningAndIndexReferenceMarking() {
        // 镜像实测 sys_process_template：field_default（bit b'0'）+ field_default_unique
        // （STORED GENERATED 生成列）+ 生成列唯一键
        List<ActualColumn> columns = new ArrayList<>();
        columns.add(fieldId());
        columns.addAll(auditFive());
        columns.add(col("field_default", "bit(1)", true, "b'0'", "", "是否默认模板（每分类最多一个）", null, null, null));
        columns.add(col("field_default_unique", "varchar(50)", true, null, "STORED GENERATED",
                "默认模板分类唯一键（生成列）", 50, null, null));
        ActualTable actual = table("gen_check", "生成列测试表", columns, List.of(
                index("uk_gen_check_default", true, "field_default_unique")));
        SourceSchemaExportTool.TableSnippet snippet = SourceSchemaExportTool.buildSnippet(actual);
        // 生成列不产 @ColumnField
        assertEquals(List.of("field_default"),
                snippet.getColumns().stream().map(SourceSchemaExportTool.ColumnSnippet::getName).toList());
        // 警示注释行（// 生成列）提示人工处置
        assertTrue(snippet.getWarnings().stream().anyMatch(w -> w.contains("// 生成列：field_default_unique")
                && w.contains("STORED GENERATED") && w.contains("引擎暂不支持生成列标注")));
        // 引用生成列的索引一并标记
        assertTrue(snippet.getIndexes().get(0).isReferencesGenerated());
        String rendered = SourceSchemaExportTool.renderTable(snippet);
        assertTrue(rendered.contains("// 引用生成列（引擎暂不支持生成列标注），请人工处置"));
    }

    @Test
    void defaultGeneratedMarkerIsNotMistakenAsGeneratedColumn() {
        // MySQL 8.0+ 函数式默认值列的 EXTRA 为 DEFAULT_GENERATED——剔除该标记后判定，
        // 避免把 DEFAULT CURRENT_TIMESTAMP 误判为生成列
        assertFalse(SourceSchemaExportTool.isGenerated(col("field_create_time", "timestamp", false,
                "CURRENT_TIMESTAMP", "DEFAULT_GENERATED", "创建时间", null, null, null)));
        assertFalse(SourceSchemaExportTool.isGenerated(col("field_update_time", "timestamp", true,
                "CURRENT_TIMESTAMP", "DEFAULT_GENERATED on update CURRENT_TIMESTAMP", "修改时间", null, null, null)));
        assertFalse(SourceSchemaExportTool.isGenerated(col("field_x", "int", false, null, "", "x", null, null, null)));
        assertTrue(SourceSchemaExportTool.isGenerated(col("field_x", "varchar(50)", true, null,
                "STORED GENERATED", "生成列", 50, null, null)));
        assertTrue(SourceSchemaExportTool.isGenerated(col("field_y", "varchar(50)", true, null,
                "VIRTUAL GENERATED", "虚拟生成列", 50, null, null)));
    }

    /* ==================== 基类推断与审计列偏差 ==================== */

    @Test
    void baseClassInferenceCoversMainModelBaseModelAndPartialAudit() {
        // 审计五列齐备 → MainModel
        List<ActualColumn> full = new ArrayList<>();
        full.add(fieldId());
        full.addAll(auditFive());
        full.add(varchar("field_remark", 500, true, null, "备注"));
        SourceSchemaExportTool.TableSnippet mainModel = SourceSchemaExportTool.buildSnippet(
                table("t_main", "主档表", full, List.of()));
        assertEquals("MainModel", mainModel.getSuggestedBaseClass());
        assertFalse(mainModel.isBaseClassMismatch());
        // 完全无审计列 → BaseModel
        List<ActualColumn> none = new ArrayList<>();
        none.add(fieldId());
        none.add(varchar("field_value", 50, false, null, "码值"));
        SourceSchemaExportTool.TableSnippet baseModel = SourceSchemaExportTool.buildSnippet(
                table("t_base", "明细表", none, List.of()));
        assertEquals("BaseModel", baseModel.getSuggestedBaseClass());
        assertFalse(baseModel.isBaseClassMismatch());
        // 部分审计列（实测 sys_dict_type/sys_dict_item 仅 field_enable）→ 与两模板均不匹配，
        // 建议 BaseModel 并警示，未内建列按业务列标注（保证 adopt 零 diff 可达）
        List<ActualColumn> partial = new ArrayList<>();
        partial.add(fieldId());
        partial.add(col("field_enable", "bit(1)", false, "b'1'", "", "是否启用", null, null, null));
        partial.add(varchar("field_desc", 200, true, null, "描述"));
        SourceSchemaExportTool.TableSnippet mismatch = SourceSchemaExportTool.buildSnippet(
                table("t_partial", "部分审计表", partial, List.of()));
        assertEquals("BaseModel", mismatch.getSuggestedBaseClass());
        assertTrue(mismatch.isBaseClassMismatch());
        assertEquals(List.of("field_enable"), mismatch.getPartialAuditColumns());
        String rendered = SourceSchemaExportTool.renderTable(mismatch);
        assertTrue(rendered.contains("// 建议基类：extends BaseModel（表含部分审计列 field_enable，"
                        + "与 MainModel/BaseModel 公共列模板均不匹配——未内建列已按业务列标注，请人工核对基类取舍）"),
                "部分审计列在建议行中列明，实际: " + rendered);
        assertTrue(mismatch.getWarnings().isEmpty(), "部分审计列口径在建议行说明，不重复产警示");
        assertEquals(List.of("field_enable", "field_desc"),
                mismatch.getColumns().stream().map(SourceSchemaExportTool.ColumnSnippet::getName).toList(),
                "部分审计列不被剔除（BaseModel 模板不内建，剔除必产缺失列 diff）");
    }

    @Test
    void auditDeviationWarningForTemplateMismatch() {
        // 实测 sys_process_template.field_update_time：timestamp NULL 无默认无 ON UPDATE，
        // 与公共列模板（NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP）不一致
        List<ActualColumn> columns = new ArrayList<>();
        columns.add(fieldId());
        columns.add(col("field_creator_id", "varchar(32)", true, null, "", "创建人", 32, null, null));
        columns.add(col("field_create_time", "timestamp", false, "CURRENT_TIMESTAMP", "DEFAULT_GENERATED",
                "创建时间", null, null, null));
        columns.add(col("field_updater_id", "varchar(32)", true, null, "", "修改人", 32, null, null));
        columns.add(col("field_update_time", "timestamp", true, null, "", "修改时间", null, null, null));
        columns.add(col("field_enable", "bit(1)", false, "b'1'", "", "是否启用", null, null, null));
        SourceSchemaExportTool.TableSnippet snippet = SourceSchemaExportTool.buildSnippet(
                table("t_deviation", "偏差表", columns, List.of()));
        assertTrue(snippet.getWarnings().stream().anyMatch(w -> w.contains("field_update_time")
                        && w.contains("与公共列模板（设计方案 §3.3）不一致")
                        && w.contains("默认值：模板 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ↔ 实际 无默认值")),
                "审计列偏差警示，实际警示: " + snippet.getWarnings());
    }

    /* ==================== 名称转换与渲染风格 ==================== */

    @Test
    void javaNameCamelizeAndClassNamePascal() {
        assertEquals("fieldDefaultUnique", SourceSchemaExportTool.toJavaFieldName("field_default_unique"));
        assertEquals("fieldId", SourceSchemaExportTool.toJavaFieldName("field_id"));
        assertEquals("fieldName", SourceSchemaExportTool.toJavaFieldName("field_name"));
        assertEquals("SysProcessTemplate", SourceSchemaExportTool.toJavaClassName("sys_process_template"));
        assertEquals("SysI18nKey", SourceSchemaExportTool.toJavaClassName("sys_i18n_key"));
        assertEquals("SysDatasource", SourceSchemaExportTool.toJavaClassName("sys_datasource"));
    }

    @Test
    void columnAnnotationAttributeOrderMatchesPilotStyle() {
        // 属性顺序对齐 SysDatasource 手写风格：name、comment、type、length、decimalLength、
        // nullable（仅 true）、defaultValue（仅有）、defaultFunction（仅有）
        SourceSchemaExportTool.TableSnippet snippet = snippetOf(
                col("field_amount", "decimal(10,2)", true, "1.5", "", "金额", null, 10, 2));
        String rendered = SourceSchemaExportTool.renderTable(snippet);
        assertTrue(rendered.contains("@ColumnField(name = \"field_amount\", comment = \"金额\", "
                        + "type = ColumnType.DECIMAL, length = 10, decimalLength = 2, nullable = true, "
                        + "defaultValue = \"1.5\")"),
                "DECIMAL 全属性形态，实际: " + rendered);
        // 非 STRING/DECIMAL 不输出 length；非可空不输出 nullable
        SourceSchemaExportTool.TableSnippet notNullInt = snippetOf(
                col("field_no", "int", false, null, "", "序号", null, null, null));
        String intLine = SourceSchemaExportTool.renderTable(notNullInt);
        assertTrue(intLine.contains("@ColumnField(name = \"field_no\", comment = \"序号\", type = ColumnType.INT)"));
        assertFalse(intLine.contains("length = "), "非 STRING/DECIMAL 忽略 length");
        assertFalse(intLine.contains("nullable"), "非可空不输出 nullable");
        // escapeJava 覆盖注释中的特殊字符
        SourceSchemaExportTool.TableSnippet quoted = snippetOf(
                varchar("field_q", 100, false, null, "含\"引号\"与\\斜杠"));
        String quotedLine = SourceSchemaExportTool.renderTable(quoted);
        assertTrue(quotedLine.contains("comment = \"含\\\"引号\\\"与\\\\斜杠\""));
    }

    /* ==================== 参数解析 ==================== */

    @Test
    void parseArgsResolvesCommandLineEnvAndDefaultChain() {
        // 无参无环境变量 → 代码兜底（与 application.properties 默认链一致）
        SourceSchemaExportTool.ExportOptions defaults =
                SourceSchemaExportTool.parseArgs(new String[0], Map.of());
        assertEquals("localhost", defaults.getHost());
        assertEquals("3306", defaults.getPort());
        assertEquals("common", defaults.getDb());
        assertEquals("root", defaults.getUser());
        assertEquals("root", defaults.getPassword());
        assertNull(defaults.getTables(), "缺省表清单");
        // 环境变量填充（CS_DB_*）
        SourceSchemaExportTool.ExportOptions fromEnv = SourceSchemaExportTool.parseArgs(new String[0], Map.of(
                "CS_DB_HOST", "db.env", "CS_DB_PORT", "3307", "CS_DB_NAME", "envdb",
                "CS_DB_USER", "envuser", "CS_DB_PASSWORD", "envpwd"));
        assertEquals("db.env", fromEnv.getHost());
        assertEquals("3307", fromEnv.getPort());
        assertEquals("envdb", fromEnv.getDb());
        assertEquals("envuser", fromEnv.getUser());
        assertEquals("envpwd", fromEnv.getPassword());
        // 命令行 > 环境变量
        SourceSchemaExportTool.ExportOptions argsOverEnv = SourceSchemaExportTool.parseArgs(
                new String[]{"--host", "db.arg", "--db", "argdb"}, Map.of("CS_DB_HOST", "db.env"));
        assertEquals("db.arg", argsOverEnv.getHost());
        assertEquals("argdb", argsOverEnv.getDb());
        assertEquals("3306", argsOverEnv.getPort(), "未覆盖维度回落环境变量/兜底");
        // --tables 拆分（去空白）
        SourceSchemaExportTool.ExportOptions withTables = SourceSchemaExportTool.parseArgs(
                new String[]{"--tables", " a , b ,c "}, Map.of());
        assertEquals(List.of("a", "b", "c"), withTables.getTables());
    }

    @Test
    void parseArgsRejectsUnknownFlagMissingValueAndEmptyTables() {
        assertThrows(IllegalArgumentException.class,
                () -> SourceSchemaExportTool.parseArgs(new String[]{"--unknown", "x"}, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> SourceSchemaExportTool.parseArgs(new String[]{"--host"}, Map.of()), "缺值");
        assertThrows(IllegalArgumentException.class,
                () -> SourceSchemaExportTool.parseArgs(new String[]{"--tables", " , "}, Map.of()), "无有效表名");
    }
}

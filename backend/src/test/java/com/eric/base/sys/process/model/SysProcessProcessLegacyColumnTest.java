package com.eric.base.sys.process.model;

import com.eric.base.common.source.ddl.SourceDdlGenerator;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.model.ColumnDef;
import com.eric.base.common.source.model.IndexDef;
import com.eric.base.common.source.model.TableDef;
import com.eric.base.common.source.scanner.SourceTableScanner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程存量表列变更实体标注测试（P17-DB-004，不连 MySQL、不启 Spring 上下文）
 *
 * @author Eric
 * @date 2026/11/19
 * @description 对真实实体直接断言 SourceTableScanner.parse 归一产物与《流程v1_7数据库设计分册》
 *              §4.2 / §4.3 / §4.4 / §4.5 / §4.6 处置项、§7.7 审批记录类型全集一致：
 *              ①4 处新增列（列名/类型/长度/可空/COMMENT 逐字）与既有列不受影响；
 *              ②`sys_process_audit_note.field_handle_type` 扩长 VARCHAR(20) → VARCHAR(50)
 *              且 COMMENT 补全集 21 值；③索引清单与改造前一致（不新增索引，§4.2~§4.5 明确）；
 *              ④SourceDdlGenerator.addColumn / modifyColumn 生成文本与「L1 新增列 / L1 扩长」
 *              口径一致——这是「dev 库 APPLY 自动 ADD COLUMN / MODIFY，VALIDATE 零 diff」
 *              验收的静态前置（口径对齐先例 {@code SysProcessTemplateLineEntityTest}）；
 *              ⑤Mapper XML 同步改动可解析断言（BaseColumns 列清单 / resultMap 列-属性映射 /
 *              insert 占位符个数与取值，读 classpath 文本断言，不引入额外测试依赖）。
 *              <p>XML 覆盖口径说明：本测试不做 MyBatis {@code XMLMapperBuilder} 全文解析
 *              （mapper 语法错误由全量测试中加载 Spring 上下文的用例覆盖），仅对本次改动的
 *              三处结构性片段做文本断言，避免引入额外测试依赖。{@code SysProcessAuditNoteMapper}
 *              无列名变化（§4.6），仅断言 {@code field_handle_type} 仍在 BaseColumns 中。</p>
 *              <p>与分册的长度记载偏差：§4.6 处置项与 §7.7 列宽说明均记 `HANDLER_TRANSFER_RETRACT`
 *              为 23 字符，实测 24 字符（差 1），结论「超 VARCHAR(20)、取 50」不变，本测试按
 *              实测 24 断言并在交付说明中报告，未改动设计文档。</p>
 */
class SysProcessProcessLegacyColumnTest {

    /** §4.2 / §4.3 节点业务键列 COMMENT（逐字对齐分册） */
    private static final String COMMENT_TEMPLATE_NODE_KEY =
            "节点业务键(模板内唯一,固定节点 N1=开始 N2=起草 N3=结束,其余顺延)";
    private static final String COMMENT_EXAMPLE_NODE_KEY = "节点业务键(模板节点快照,模板内唯一)";
    /** §4.4 节点运行态列 COMMENT */
    private static final String COMMENT_EXAMPLE_NODE_RUNTIME = "节点运行态(JSON对象,FORK激活分支集合/JOIN到达进度)";
    /** §4.5 来源工作项 id 列 COMMENT */
    private static final String COMMENT_FROM_WORKITEM_ID =
            "来源工作项id(转办/转办收回/已审批人收回生成的新工作项追溯)";
    /** §7.7 审批记录类型全集（21 值，表格 1~21 顺序） */
    private static final List<String> HANDLE_TYPES = List.of(
            "DRAFTER_SUBMIT=起草人提交",
            "DRAFTER_BACK=起草人收回",
            "DRAFTER_ABANDON=起草人废弃",
            "HANDLER_PASS=处理人通过",
            "HANDLER_RETURN=处理人驳回",
            "AUTO_PASS=自动跳过",
            "ADMIN_PAUSE=管理员暂停",
            "ADMIN_RESUME=管理员恢复",
            "ADMIN_JUMP=管理员跳转",
            "ADMIN_ABANDON=管理员作废",
            "ADMIN_FINISH=管理员办结",
            "HANDLER_REJECT=处理人驳回(新语义:可指定驳回目标+resumeMode)",
            "CC_AUTO=抄送节点自动办结留痕",
            "DRAFTER_EXPEDITE=起草人催办",
            "HANDLER_CC=审批人抄送",
            "HANDLER_TRANSFER=审批人转办",
            "HANDLER_TRANSFER_RETRACT=转办收回",
            "PROCESSED_RETRACT=已审批人收回",
            "PROCESSED_EXPEDITE=已审批人催办",
            "ADMIN_CHANGE_HANDLER=管理员更换审批人",
            "SYSTEM_ERROR=流程异常(附错误摘要)");

    private static TableDef templateNode() {
        return SourceTableScanner.parse(SysProcessTemplateNode.class);
    }

    private static TableDef exampleNode() {
        return SourceTableScanner.parse(SysProcessExampleNode.class);
    }

    private static TableDef workitem() {
        return SourceTableScanner.parse(SysProcessExampleWorkitem.class);
    }

    private static TableDef auditNote() {
        return SourceTableScanner.parse(SysProcessAuditNote.class);
    }

    /** 按列名取列定义（断言辅助） */
    private static ColumnDef column(TableDef table, String name) {
        return table.getColumns().stream().filter(def -> def.getName().equals(name)).findFirst().orElseThrow();
    }

    /** 列检查：列名/COMMENT/类型/长度/可空 + 无默认值（新增列分册均未声明默认值） */
    private static void assertNewColumn(TableDef table, String name, String comment, ColumnType type,
                                        int length, boolean nullable) {
        ColumnDef def = column(table, name);
        assertEquals(name, def.getName());
        assertEquals(comment, def.getComment());
        assertEquals(type, def.getType());
        assertEquals(length, def.getLength());
        assertEquals(nullable, def.isNullable(), "分册 §4.1 存量行一律允许 NULL: " + name);
        assertNull(def.getDefaultValue(), "新增列未声明字面量默认值: " + name);
        assertEquals(DefaultFunction.NONE, def.getDefaultFunction(), "新增列无函数式默认值: " + name);
        assertFalse(def.isPrimaryKey(), "业务列非主键: " + name);
    }

    /** 索引清单断言：索引名序列 + 逐索引列序 + 非唯一（分册 §4.2~§4.5 均不新增索引） */
    private static void assertIndexesUnchanged(TableDef table, List<String> names) {
        assertEquals(names, table.getIndexes().stream().map(IndexDef::getName).toList(),
                table.getName() + " 索引清单与改造前一致（不新增索引）");
        for (IndexDef index : table.getIndexes()) {
            assertFalse(index.isUnique(), "既有索引均为普通 BTREE: " + index.getName());
        }
    }

    /** 生成 ADD COLUMN 语句（分册 L1 新增列口径） */
    private static String addColumn(TableDef table, String columnName) {
        return SourceDdlGenerator.addColumn(table, column(table, columnName));
    }

    /** 读 classpath 下的 Mapper XML 文本（XML 同步改动断言辅助） */
    private static String xml(String fileName) throws IOException {
        try (InputStream in = SysProcessProcessLegacyColumnTest.class
                .getResourceAsStream("/mappers/sys/process/" + fileName)) {
            if (null == in) {
                throw new IllegalStateException("classpath 下缺少 Mapper XML: " + fileName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** BaseColumns 片段的列清单（顺序保留；去掉 <sql> 起标记本身） */
    private static List<String> baseColumns(String mapperXml) {
        String sql = section(mapperXml, "<sql id=\"BaseColumns\">", "</sql>");
        return columnsOf(sql.substring(sql.indexOf('>') + 1));
    }

    /** resultMap 片段中「属性名 → 列名」映射（顺序保留，id + result 两类） */
    private static List<String> resultMapMappings(String mapperXml) {
        List<String> mappings = new ArrayList<>();
        String resultMap = section(mapperXml, "ResultMap\"", "</resultMap>");
        for (String line : resultMap.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("<id ") && !trimmed.startsWith("<result ")) {
                continue;
            }
            mappings.add(attribute(trimmed, "property") + "<-" + attribute(trimmed, "column"));
        }
        return mappings;
    }

    /** insert 语句 VALUES 占位符清单（顺序保留） */
    private static List<String> insertValues(String mapperXml) {
        String insert = section(mapperXml, "<insert id=\"insert\"", "</insert>");
        String values = insert.substring(insert.indexOf("VALUES"));
        int open = values.indexOf('(');
        int close = values.indexOf(')', open);
        return placeholders(values.substring(open + 1, close));
    }

    /** 取起止标记之间的片段（含起标记之后到止标记之前） */
    private static String section(String text, String from, String to) {
        int start = text.indexOf(from);
        assertTrue(start >= 0, "缺失结构片段: " + from);
        int end = text.indexOf(to, start);
        assertTrue(end > start, "结构片段未闭合: " + to);
        return text.substring(start, end);
    }

    /** 逗号拆分列名（去首尾空白，忽略空项） */
    private static List<String> columnsOf(String text) {
        List<String> columns = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                columns.add(trimmed);
            }
        }
        return columns;
    }

    /** 提取 #{...} 或 ${...} 占位符表达式（顺序保留） */
    private static List<String> placeholders(String text) {
        List<String> values = new ArrayList<>();
        int position = 0;
        while (true) {
            int open = text.indexOf("#{", position);
            int dollar = text.indexOf("${", position);
            if (open < 0 || (dollar >= 0 && dollar < open)) {
                open = dollar;
            }
            if (open < 0) {
                return values;
            }
            int close = text.indexOf('}', open);
            values.add(text.substring(open + 2, close));
            position = close + 1;
        }
    }

    /** 取 XML 标签属性值 */
    private static String attribute(String element, String name) {
        String prefix = name + "=\"";
        int start = element.indexOf(prefix);
        assertTrue(start >= 0, "缺少属性 " + name + ": " + element);
        return element.substring(start + prefix.length(), element.indexOf('"', start + prefix.length()));
    }

    /* ==================== §4.2 sys_process_template_node.field_node_key ==================== */

    @Test
    void templateNodeAddsFieldNodeKeyColumn() {
        TableDef table = templateNode();

        assertEquals("sys_process_template_node", table.getName());
        assertNewColumn(table, "field_node_key", COMMENT_TEMPLATE_NODE_KEY, ColumnType.STRING, 50, true);
        assertTrue(table.getColumns().stream().anyMatch(def -> "field_node_key".equals(def.getName())),
                "列清单含新列 field_node_key");

        // 旧列逐列不受影响（挑 3 列核对：类型/长度/可空/COMMENT 逐字）
        ColumnDef templateId = column(table, "field_template_id");
        assertEquals("模板id", templateId.getComment());
        assertEquals(ColumnType.STRING, templateId.getType());
        assertEquals(32, templateId.getLength());
        assertFalse(templateId.isNullable());

        ColumnDef nodeNo = column(table, "field_no");
        assertEquals("节点编号", nodeNo.getComment());
        assertEquals(ColumnType.INT, nodeNo.getType());
        assertFalse(nodeNo.isNullable());

        ColumnDef nodeConfig = column(table, "field_node_config");
        assertEquals("节点配置(JSON对象)", nodeConfig.getComment());
        assertEquals(ColumnType.JSON, nodeConfig.getType());
        assertTrue(nodeConfig.isNullable());

        // 索引清单不变（§4.2：不新增索引）
        assertIndexesUnchanged(table, List.of("idx_template_node_no", "idx_template_node_template"));
        assertEquals(List.of("field_template_id", "field_no"),
                table.getIndexes().get(0).getColumns(), "idx_template_node_no 列序照旧");
        assertEquals(List.of("field_template_id"),
                table.getIndexes().get(1).getColumns(), "idx_template_node_template 列序照旧");
    }

    @Test
    void templateNodeAddColumnDdlMatchesDesign() {
        assertEquals("ALTER TABLE sys_process_template_node ADD COLUMN field_node_key VARCHAR(50) DEFAULT NULL COMMENT '"
                        + COMMENT_TEMPLATE_NODE_KEY + "'",
                addColumn(templateNode(), "field_node_key"),
                "L1 新增列：ADD COLUMN 追加表尾、可空无默认值渲染 DEFAULT NULL（不带 AFTER）");
        assertFalse(addColumn(templateNode(), "field_node_key").contains("DROP"));
    }

    /* ==================== §4.3 / §4.4 sys_process_example_node ==================== */

    @Test
    void exampleNodeAddsFieldNodeKeyAndFieldRuntimeColumns() {
        TableDef table = exampleNode();

        assertEquals("sys_process_example_node", table.getName());
        assertNewColumn(table, "field_node_key", COMMENT_EXAMPLE_NODE_KEY, ColumnType.STRING, 50, true);
        assertNewColumn(table, "field_runtime", COMMENT_EXAMPLE_NODE_RUNTIME, ColumnType.JSON, 0, true);

        // 旧列逐列不受影响
        ColumnDef exampleId = column(table, "field_example_id");
        assertEquals("流程实例id", exampleId.getComment());
        assertEquals(ColumnType.STRING, exampleId.getType());
        assertEquals(32, exampleId.getLength());
        assertFalse(exampleId.isNullable());

        ColumnDef status = column(table, "field_status");
        assertEquals(ColumnType.STRING, status.getType());
        assertEquals(20, status.getLength());
        assertEquals("WAITING", status.getDefaultValue(), "既有默认值不受改造影响");

        ColumnDef nodeConfig = column(table, "field_node_config");
        assertEquals("节点配置(JSON对象,模板节点快照)", nodeConfig.getComment());
        assertEquals(ColumnType.JSON, nodeConfig.getType());
        assertTrue(nodeConfig.isNullable());

        // 索引清单不变（§4.3 / §4.4：不新增索引）
        assertIndexesUnchanged(table, List.of("idx_example_node_example", "idx_example_node_no"));
        assertEquals(List.of("field_example_id"), table.getIndexes().get(0).getColumns());
        assertEquals(List.of("field_example_id", "field_node_no"), table.getIndexes().get(1).getColumns());
    }

    @Test
    void exampleNodeAddColumnDdlMatchesDesign() {
        assertEquals("ALTER TABLE sys_process_example_node ADD COLUMN field_node_key VARCHAR(50) DEFAULT NULL COMMENT '"
                        + COMMENT_EXAMPLE_NODE_KEY + "'",
                addColumn(exampleNode(), "field_node_key"));
        assertEquals("ALTER TABLE sys_process_example_node ADD COLUMN field_runtime JSON DEFAULT NULL COMMENT '"
                        + COMMENT_EXAMPLE_NODE_RUNTIME + "'",
                addColumn(exampleNode(), "field_runtime"),
                "JSON 列可空、无默认值（分册 §4.4：JSON 列禁止默认值）");
    }

    /* ==================== §4.5 sys_process_example_workitem.field_from_workitem_id ==================== */

    @Test
    void workitemAddsFieldFromWorkitemIdColumn() {
        TableDef table = workitem();

        assertEquals("sys_process_example_workitem", table.getName());
        assertNewColumn(table, "field_from_workitem_id", COMMENT_FROM_WORKITEM_ID, ColumnType.STRING, 32, true);

        // 旧列逐列不受影响
        ColumnDef handlerId = column(table, "field_handler_id");
        assertEquals("处理人id", handlerId.getComment());
        assertEquals(ColumnType.STRING, handlerId.getType());
        assertEquals(32, handlerId.getLength());
        assertFalse(handlerId.isNullable());

        ColumnDef expectHandlerId = column(table, "field_expect_handler_id");
        assertEquals("期望处理人id(原配置审批人,跳过时与处理人不一致)", expectHandlerId.getComment());
        assertTrue(expectHandlerId.isNullable());

        ColumnDef status = column(table, "field_status");
        assertEquals("PROCESSING", status.getDefaultValue());

        // 索引清单不变（§4.5：4 个既有索引，不新增索引）
        assertIndexesUnchanged(table, List.of("idx_workitem_example", "idx_workitem_handler",
                "idx_workitem_node", "idx_workitem_status"));
    }

    @Test
    void workitemAddColumnDdlMatchesDesign() {
        assertEquals("ALTER TABLE sys_process_example_workitem ADD COLUMN field_from_workitem_id VARCHAR(32) DEFAULT NULL COMMENT '"
                        + COMMENT_FROM_WORKITEM_ID + "'",
                addColumn(workitem(), "field_from_workitem_id"));
    }

    /* ==================== §4.6 处置项 / §7.7 审批记录类型全集 ==================== */

    @Test
    void auditNoteHandleTypeColumnWidenedTo50WithFullEnumComment() {
        TableDef table = auditNote();

        ColumnDef handleType = column(table, "field_handle_type");
        assertEquals("field_handle_type", handleType.getName());
        assertEquals(ColumnType.STRING, handleType.getType());
        assertEquals(50, handleType.getLength(), "§4.6 处置项：VARCHAR(20) → VARCHAR(50)");
        assertFalse(handleType.isNullable(), "既有列保持 NOT NULL，不随扩长放宽");
        assertNull(handleType.getDefaultValue(), "既有列无默认值，不受改造影响");
        assertEquals(DefaultFunction.NONE, handleType.getDefaultFunction());

        // COMMENT 含全集 21 值（逐值断言）与关键中文
        String comment = handleType.getComment();
        for (String pair : HANDLE_TYPES) {
            assertTrue(comment.contains(pair), "COMMENT 缺少枚举值: " + pair);
        }
        assertTrue(comment.startsWith("处理操作: "), "COMMENT 前缀与既有列口径一致: " + comment);
        assertTrue(comment.contains("管理员更换审批人"), "COMMENT 缺少关键中文: ADMIN_CHANGE_HANDLER");
        assertTrue(comment.contains("流程异常"), "COMMENT 缺少关键中文: SYSTEM_ERROR");
        assertEquals(21, HANDLE_TYPES.size(), "§7.7 全集为 21 值 = 既有 11 + 新增 10");

        // 最长新值 24 字符（分册 §4.6 / §7.7 记载为 23），均已超出 VARCHAR(20)，50 有余
        String longest = "HANDLER_TRANSFER_RETRACT";
        assertEquals(24, longest.length(),
                "该新值实际 24 字符：分册 §4.6 处置项与 §7.7 列宽说明均记作 23（差 1），"
                        + "结论「超出 VARCHAR(20)、取 50 足够」不受影响，长度记载偏差见测试类 javadoc / 交付说明");
        assertTrue(longest.length() > 20 && longest.length() <= handleType.getLength());
        assertEquals(24, HANDLE_TYPES.stream().map(pair -> pair.substring(0, pair.indexOf('=')))
                        .mapToInt(String::length).max().orElseThrow(),
                "全集枚举值最长项即 HANDLER_TRANSFER_RETRACT（24 字符），扩长后 50 有余量");

        // 索引与其他列不受影响
        assertIndexesUnchanged(table, List.of("idx_audit_example", "idx_audit_workitem"));
        assertEquals(200, column(table, "field_content").getLength());
        assertEquals(32, column(table, "field_example_id").getLength());
    }

    @Test
    void auditNoteModifyColumnDdlRendersVarchar50() {
        TableDef table = auditNote();
        String ddl = SourceDdlGenerator.modifyColumn(table, column(table, "field_handle_type"));

        assertTrue(ddl.startsWith("ALTER TABLE sys_process_audit_note MODIFY COLUMN field_handle_type "),
                "L1 扩长走 MODIFY COLUMN 全量重定义: " + ddl);
        assertTrue(ddl.contains("VARCHAR(50)"), "注解 > 实际 = VARCHAR 扩长（APPLY 自动 MODIFY）: " + ddl);
        assertFalse(ddl.contains("VARCHAR(20)"), "不再出现旧列宽: " + ddl);
        assertTrue(ddl.contains("NOT NULL"), "可空性不变: " + ddl);
        assertEquals(ddl, SourceDdlGenerator.alterColumnComment(table, column(table, "field_handle_type")),
                "COMMENT 变更与 MODIFY COLUMN 文本一致（列 COMMENT 同批补全集）");
    }

    /* ==================== Mapper XML 同步改动（可解析文本断言） ==================== */

    @Test
    void templateNodeMapperXmlExposesFieldNodeKey() throws IOException {
        String mapper = xml("SysProcessTemplateNodeMapper.xml");

        assertEquals(List.of("field_id", "field_template_id", "field_no", "field_node_name",
                        "field_node_type", "field_node_config", "field_node_key"),
                baseColumns(mapper), "BaseColumns 列清单与列序");
        assertEquals(List.of("fieldId<-field_id", "fieldTemplateId<-field_template_id", "fieldNo<-field_no",
                        "fieldNodeName<-field_node_name", "fieldNodeType<-field_node_type",
                        "fieldNodeConfig<-field_node_config", "fieldNodeKey<-field_node_key"),
                resultMapMappings(mapper), "resultMap 列-属性映射与列序");
        assertEquals(List.of("model.fieldId", "model.fieldTemplateId", "model.fieldNo", "model.fieldNodeName",
                        "model.fieldNodeType", "model.fieldNodeConfig", "model.fieldNodeKey"),
                insertValues(mapper), "insert VALUES 占位符与 BaseColumns 一一对应（含新列）");
        assertTrue(mapper.contains("<if test=\"model.fieldNodeKey != null\">field_node_key = #{model.fieldNodeKey},</if>"),
                "update 语句新增 field_node_key 分支");
        assertTrue(mapper.contains("AND field_node_key = #{model.fieldNodeKey}"), "selectList 条件新增 field_node_key");
        assertTrue(mapper.contains("#{node.fieldNodeKey}"), "insertBatch 批量插入含新列");
    }

    @Test
    void exampleNodeMapperXmlExposesFieldNodeKeyAndRuntime() throws IOException {
        String mapper = xml("SysProcessExampleNodeMapper.xml");

        assertEquals(List.of("field_id", "field_example_id", "field_node_no", "field_node_name",
                        "field_node_type", "field_node_config", "field_status", "field_node_key", "field_runtime"),
                baseColumns(mapper), "BaseColumns 列清单与列序");
        assertEquals(List.of("fieldId<-field_id", "fieldExampleId<-field_example_id", "fieldNodeNo<-field_node_no",
                        "fieldNodeName<-field_node_name", "fieldNodeType<-field_node_type",
                        "fieldNodeConfig<-field_node_config", "fieldStatus<-field_status",
                        "fieldNodeKey<-field_node_key", "fieldRuntime<-field_runtime"),
                resultMapMappings(mapper), "resultMap 列-属性映射与列序");
        assertEquals(List.of("model.fieldId", "model.fieldExampleId", "model.fieldNodeNo", "model.fieldNodeName",
                        "model.fieldNodeType", "model.fieldNodeConfig", "model.fieldStatus",
                        "model.fieldNodeKey", "model.fieldRuntime"),
                insertValues(mapper), "insert VALUES 占位符与 BaseColumns 一一对应（含新列）");
        assertTrue(mapper.contains("<if test=\"model.fieldNodeKey != null\">field_node_key = #{model.fieldNodeKey},</if>"),
                "update 语句新增 field_node_key 分支");
        assertTrue(mapper.contains("<if test=\"model.fieldRuntime != null\">field_runtime = #{model.fieldRuntime},</if>"),
                "update 语句新增 field_runtime 分支");
        assertTrue(mapper.contains("AND field_node_key = #{model.fieldNodeKey}"), "selectList 条件新增 field_node_key");
        assertTrue(mapper.contains("#{node.fieldNodeKey}") && mapper.contains("#{node.fieldRuntime}"),
                "insertBatch 批量插入含新列");
    }

    @Test
    void workitemMapperXmlExposesFieldFromWorkitemId() throws IOException {
        String mapper = xml("SysProcessExampleWorkitemMapper.xml");

        assertEquals(List.of("field_id", "field_example_id", "field_node_id", "field_handler_id",
                        "field_expect_handler_id", "field_create_time", "field_finish_time", "field_status",
                        "field_config", "field_from_workitem_id"),
                baseColumns(mapper), "BaseColumns 列清单与列序");
        assertEquals(List.of("fieldId<-field_id", "fieldExampleId<-field_example_id", "fieldNodeId<-field_node_id",
                        "fieldHandlerId<-field_handler_id", "fieldExpectHandlerId<-field_expect_handler_id",
                        "fieldCreateTime<-field_create_time", "fieldFinishTime<-field_finish_time",
                        "fieldStatus<-field_status", "fieldConfig<-field_config",
                        "fieldFromWorkitemId<-field_from_workitem_id"),
                resultMapMappings(mapper), "resultMap 列-属性映射与列序");
        assertEquals(List.of("model.fieldId", "model.fieldExampleId", "model.fieldNodeId", "model.fieldHandlerId",
                        "model.fieldExpectHandlerId", "model.fieldCreateTime", "model.fieldFinishTime",
                        "model.fieldStatus", "model.fieldConfig", "model.fieldFromWorkitemId"),
                insertValues(mapper), "insert VALUES 占位符与 BaseColumns 一一对应（含新列）");
        assertTrue(mapper.contains(
                        "<if test=\"model.fieldFromWorkitemId != null\">field_from_workitem_id = #{model.fieldFromWorkitemId},</if>"),
                "update 语句新增 field_from_workitem_id 分支");
        assertFalse(mapper.contains("AND field_from_workitem_id"), "分册 §4.5 未要求按来源列检索，selectList 不新增该条件");
    }

    /** §4.6 / §7.7：审批记录 mapper 无列名变化，不改 XML（field_handle_type 仍在 BaseColumns 中） */
    @Test
    void auditNoteMapperXmlUnchanged() throws IOException {
        String mapper = xml("SysProcessAuditNoteMapper.xml");
        assertTrue(baseColumns(mapper).contains("field_handle_type"), "列名未变，XML 无需改动");
    }
}

package com.eric.base.sys.process.model;

import com.eric.base.common.source.ddl.SourceDdlGenerator;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.model.ColumnDef;
import com.eric.base.common.source.model.IndexDef;
import com.eric.base.common.source.model.TableDef;
import com.eric.base.common.source.scanner.SourceTableScanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程实例连线实体标注扫描测试（P17-DB-002，不连 MySQL、不启 Spring 上下文）
 *
 * @author Eric
 * @date 2026/11/19
 * @description 对真实实体 {@link SysProcessExampleLine} 直接断言 SourceTableScanner.parse
 *              归一产物与《流程v1_7数据库设计分册》§3.2.3 逐列字段表、§3.2.4 索引清单一致
 *              （列序 = 内建 field_id 主键最前 + 8 业务列声明序，无 field_enable /
 *              field_update_time / field_updater_id 审计列；逐列类型/长度/可空/默认值/
 *              COMMENT；2 个索引名与列序），并经 SourceDdlGenerator.createTable 全量建表文本
 *              与按分册列定义书写的期望 DDL 空白归一化后整段等价——这是「dev 库 VALIDATE
 *              零 diff」验收的静态前置（口径对齐先例 SysProcessTemplateLineEntityTest）。
 */
class SysProcessExampleLineEntityTest {

    /** 按分册 §3.2.3 列定义书写的期望建表语句（比对时空白归一化并去掉结尾分号） */
    private static final String EXPECTED_SYS_PROCESS_EXAMPLE_LINE_DDL = """
            CREATE TABLE IF NOT EXISTS sys_process_example_line (
                field_id           VARCHAR(32)  NOT NULL COMMENT '主键ID',
                field_example_id   VARCHAR(32)  NOT NULL COMMENT '流程实例id',
                field_from_key     VARCHAR(50)  NOT NULL COMMENT '起始节点键',
                field_to_key       VARCHAR(50)  NOT NULL COMMENT '目标节点键',
                field_line_type    VARCHAR(20)  NOT NULL COMMENT '连线类型: NORMAL=普通 CONDITION=条件分支 PARALLEL=并行分支 MANUAL=人工决策',
                field_branch_order INT          NOT NULL COMMENT '分支序号(条件/并行分支判断顺序,上移下移即改此值)',
                field_branch_name  VARCHAR(200) DEFAULT NULL COMMENT '分支名称',
                field_condition    TEXT         DEFAULT NULL COMMENT '分支条件公式(条件分支/并行启动条件)',
                field_is_default   BIT(1)       NOT NULL COMMENT '是否默认分支(条件分支节点有且仅有一条)',
                PRIMARY KEY (field_id),
                KEY idx_example_line_example (field_example_id),
                KEY idx_example_line_from (field_example_id, field_from_key)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例连线快照表';
            """;

    /** 空白归一化：连续空白折叠为单空格并去掉结尾分号（期望语句带分号、生成语句不带） */
    private static String normalize(String ddl) {
        String trimmed = ddl.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.replaceAll("\\s+", " ");
    }

    /** 按列名取列定义（断言辅助） */
    private static ColumnDef column(TableDef table, String name) {
        return table.getColumns().stream().filter(def -> def.getName().equals(name)).findFirst().orElseThrow();
    }

    private static void assertColumn(ColumnDef def, String name, String comment, ColumnType type,
                                     int length, boolean nullable) {
        assertEquals(name, def.getName());
        assertEquals(comment, def.getComment());
        assertEquals(type, def.getType());
        assertEquals(length, def.getLength());
        assertEquals(nullable, def.isNullable());
        assertNull(def.getDefaultValue(), "分册 §3.2.3 未为业务列声明默认值: " + name);
        assertEquals(DefaultFunction.NONE, def.getDefaultFunction(), "业务列无函数式默认值: " + name);
        assertFalse(def.isPrimaryKey(), "业务列非主键: " + name);
    }

    /* ==================== 逐列一致 ==================== */

    @Test
    void exampleLineAnnotationMatchesDesignColumnByColumn() {
        TableDef table = SourceTableScanner.parse(SysProcessExampleLine.class);

        assertEquals("sys_process_example_line", table.getName());
        assertEquals("流程实例连线快照表", table.getComment());
        assertNull(table.getDataSourceName(), "未标注 @DataSource 时逻辑数据源名为 null");
        assertTrue(table.getPrimaryKeyColumns().isEmpty(), "不声明 primaryKeyColumns（用引擎内建 field_id 主键）");
        assertEquals(List.of("field_id"), table.getEffectivePrimaryKeyColumns());

        // 列序 = 内建主键（最前）+ 8 业务列（声明序），流水结构无审计列
        assertEquals(List.of("field_id", "field_example_id", "field_from_key", "field_to_key",
                        "field_line_type", "field_branch_order", "field_branch_name", "field_condition",
                        "field_is_default"),
                table.getColumns().stream().map(ColumnDef::getName).toList(),
                "列清单与列序与分册 §3.2.3 一致");
        assertEquals(9, table.getColumns().size(), "内建主键 1 列 + 业务列 8 列");
        for (String audit : new String[]{"field_enable", "field_update_time", "field_updater_id",
                "field_creator_id", "field_create_time"}) {
            assertFalse(table.getColumns().stream().anyMatch(def -> def.getName().equals(audit)),
                    "流水结构不得出现公共审计列: " + audit);
        }

        // 内建主键列（引擎公共列模板，固定最前）
        ColumnDef fieldId = column(table, "field_id");
        assertEquals("主键ID", fieldId.getComment());
        assertEquals(ColumnType.STRING, fieldId.getType());
        assertEquals(32, fieldId.getLength());
        assertFalse(fieldId.isNullable());
        assertNull(fieldId.getDefaultValue());
        assertEquals(DefaultFunction.NONE, fieldId.getDefaultFunction());
        assertTrue(fieldId.isPrimaryKey());

        // 8 业务列逐列断言（类型/长度/可空/COMMENT，文案逐字对齐分册 §3.2.3）
        assertColumn(column(table, "field_example_id"), "field_example_id", "流程实例id",
                ColumnType.STRING, 32, false);
        assertColumn(column(table, "field_from_key"), "field_from_key", "起始节点键",
                ColumnType.STRING, 50, false);
        assertColumn(column(table, "field_to_key"), "field_to_key", "目标节点键",
                ColumnType.STRING, 50, false);
        assertColumn(column(table, "field_line_type"), "field_line_type",
                "连线类型: NORMAL=普通 CONDITION=条件分支 PARALLEL=并行分支 MANUAL=人工决策",
                ColumnType.STRING, 20, false);
        assertColumn(column(table, "field_branch_order"), "field_branch_order",
                "分支序号(条件/并行分支判断顺序,上移下移即改此值)", ColumnType.INT, 0, false);
        assertColumn(column(table, "field_branch_name"), "field_branch_name", "分支名称",
                ColumnType.STRING, 200, true);
        assertColumn(column(table, "field_condition"), "field_condition",
                "分支条件公式(条件分支/并行启动条件)", ColumnType.TEXT, 0, true);
        assertColumn(column(table, "field_is_default"), "field_is_default",
                "是否默认分支(条件分支节点有且仅有一条)", ColumnType.BOOLEAN, 0, false);

        // 两索引（分册 §3.2.4）：普通 BTREE，列序照抄声明序
        assertEquals(2, table.getIndexes().size());
        IndexDef byExample = table.getIndexes().get(0);
        assertEquals("idx_example_line_example", byExample.getName());
        assertEquals(List.of("field_example_id"), byExample.getColumns());
        assertFalse(byExample.isUnique());
        IndexDef byFromKey = table.getIndexes().get(1);
        assertEquals("idx_example_line_from", byFromKey.getName());
        assertEquals(List.of("field_example_id", "field_from_key"), byFromKey.getColumns());
        assertFalse(byFromKey.isUnique());
    }

    /* ==================== 建表文本等价（强断言） ==================== */

    @Test
    void exampleLineCreateTableDdlMatchesDesignBaseline() {
        TableDef table = SourceTableScanner.parse(SysProcessExampleLine.class);
        String normalized = normalize(SourceDdlGenerator.createTable(table));

        assertEquals(normalize(EXPECTED_SYS_PROCESS_EXAMPLE_LINE_DDL), normalized,
                "实体注解经扫描归一 + DDL 生成后与按分册 §3.2.3/§3.2.4 书写的建表语句整段逐字等价"
                        + "（列序/类型/长度/可空/默认值/COMMENT/索引/ENGINE/CHARSET）");

        // 语义片段出现顺序（列序与索引序额外显式校验）
        List<String> fragments = List.of(
                "field_id VARCHAR(32) NOT NULL COMMENT '主键ID'",
                "field_example_id VARCHAR(32) NOT NULL COMMENT '流程实例id'",
                "field_from_key VARCHAR(50) NOT NULL COMMENT '起始节点键'",
                "field_to_key VARCHAR(50) NOT NULL COMMENT '目标节点键'",
                "field_line_type VARCHAR(20) NOT NULL COMMENT '连线类型: NORMAL=普通 CONDITION=条件分支 PARALLEL=并行分支 MANUAL=人工决策'",
                "field_branch_order INT NOT NULL COMMENT '分支序号(条件/并行分支判断顺序,上移下移即改此值)'",
                "field_branch_name VARCHAR(200) DEFAULT NULL COMMENT '分支名称'",
                "field_condition TEXT DEFAULT NULL COMMENT '分支条件公式(条件分支/并行启动条件)'",
                "field_is_default BIT(1) NOT NULL COMMENT '是否默认分支(条件分支节点有且仅有一条)'",
                "PRIMARY KEY (field_id)",
                "KEY idx_example_line_example (field_example_id)",
                "KEY idx_example_line_from (field_example_id, field_from_key)");
        int position = -1;
        for (String fragment : fragments) {
            int found = normalized.indexOf(fragment);
            assertTrue(found >= 0, "缺失语义片段: " + fragment);
            assertTrue(found > position, "列序/索引序错位: " + fragment);
            position = found;
        }
        assertTrue(normalized.startsWith("CREATE TABLE IF NOT EXISTS sys_process_example_line ("));
        assertTrue(normalized.endsWith(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例连线快照表'"),
                "ENGINE/CHARSET/表 COMMENT 对齐既有脚本风格");
        assertFalse(normalized.contains("DROP"), "引擎永不生成 DROP 类语句");
    }
}

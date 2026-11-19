package com.eric.base.sys.process.model;

import com.eric.base.common.source.ddl.SourceDdlGenerator;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.model.ColumnDef;
import com.eric.base.common.source.model.IndexDef;
import com.eric.base.common.source.model.TableDef;
import com.eric.base.common.source.scanner.SourceTableScanner;
import com.eric.base.sys.process.dao.SysProcessTaskLogDao;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程任务执行日志实体标注扫描测试（P17-DB-003，不连 MySQL、不启 Spring 上下文）
 *
 * @author Eric
 * @date 2026/11/19
 * @description 对真实实体 {@link SysProcessTaskLog} 直接断言 SourceTableScanner.parse
 *              归一产物与《流程v1_7数据库设计分册》§3.3.3 逐列字段表、§3.3.4 索引清单一致
 *              （列序 = 内建 field_id 主键最前 + 12 业务列声明序，无 field_enable /
 *              field_update_time / field_updater_id 审计列；逐列类型/长度/可空/默认值/
 *              COMMENT；含 field_create_time 的 DefaultFunction.CURRENT_TIMESTAMP；
 *              1 个索引 idx_task_log_example），并经 SourceDdlGenerator.createTable 全量建表
 *              文本与按分册列定义书写的期望 DDL 空白归一化后整段等价——这是「dev 库 VALIDATE
 *              零 diff」验收的静态前置（口径对齐先例 SysProcessTemplateLineEntityTest）；
 *              另硬断言 Dao 自声明方法面只有写入留痕与查询，体现「只增不改」。
 */
class SysProcessTaskLogEntityTest {

    /** 按分册 §3.3.3 列定义书写的期望建表语句（比对时空白归一化并去掉结尾分号） */
    private static final String EXPECTED_SYS_PROCESS_TASK_LOG_DDL = """
            CREATE TABLE IF NOT EXISTS sys_process_task_log (
                field_id           VARCHAR(32)  NOT NULL COMMENT '主键ID',
                field_example_id   VARCHAR(32)  NOT NULL COMMENT '流程实例id',
                field_node_id      VARCHAR(32)  DEFAULT NULL COMMENT '节点id(流程实例节点id,StartProcess/EndProcess 步骤可空)',
                field_workitem_id  VARCHAR(32)  DEFAULT NULL COMMENT '工作项id(工作项级任务留痕,节点/流程级可空)',
                field_task_name    VARCHAR(200) NOT NULL COMMENT '任务名称',
                field_task_bean    VARCHAR(200) NOT NULL COMMENT '任务实现类/bean 标识',
                field_task_type    VARCHAR(50)  NOT NULL COMMENT '执行步骤(ProcessTaskType): CreateProcess/StartProcess/StartNode/StartWorkitem/SendTodo/EndWorkitem/EndNode/EndProcess',
                field_params       JSON         DEFAULT NULL COMMENT '任务参数(JSON对象)',
                field_result       JSON         DEFAULT NULL COMMENT '执行结果摘要(JSON对象)',
                field_success      BIT(1)       NOT NULL COMMENT '是否成功: 1=成功 0=失败',
                field_error_msg    TEXT         DEFAULT NULL COMMENT '失败信息',
                field_cost_ms      BIGINT       NOT NULL COMMENT '耗时毫秒',
                field_create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                PRIMARY KEY (field_id),
                KEY idx_task_log_example (field_example_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程任务执行日志表';
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
        assertNull(def.getDefaultValue(), "分册 §3.3.3 未为业务列声明默认值: " + name);
        assertEquals(DefaultFunction.NONE, def.getDefaultFunction(), "业务列无函数式默认值: " + name);
        assertFalse(def.isPrimaryKey(), "业务列非主键: " + name);
    }

    /* ==================== 逐列一致 ==================== */

    @Test
    void taskLogAnnotationMatchesDesignColumnByColumn() {
        TableDef table = SourceTableScanner.parse(SysProcessTaskLog.class);

        assertEquals("sys_process_task_log", table.getName());
        assertEquals("流程任务执行日志表", table.getComment());
        assertNull(table.getDataSourceName(), "未标注 @DataSource 时逻辑数据源名为 null");
        assertTrue(table.getPrimaryKeyColumns().isEmpty(), "不声明 primaryKeyColumns（用引擎内建 field_id 主键）");
        assertEquals(List.of("field_id"), table.getEffectivePrimaryKeyColumns());

        // 列序 = 内建主键（最前）+ 12 业务列（声明序），流水结构无审计列
        assertEquals(List.of("field_id", "field_example_id", "field_node_id", "field_workitem_id",
                        "field_task_name", "field_task_bean", "field_task_type", "field_params",
                        "field_result", "field_success", "field_error_msg", "field_cost_ms",
                        "field_create_time"),
                table.getColumns().stream().map(ColumnDef::getName).toList(),
                "列清单与列序与分册 §3.3.3 一致");
        assertEquals(13, table.getColumns().size(), "内建主键 1 列 + 业务列 12 列");
        for (String audit : new String[]{"field_enable", "field_update_time", "field_updater_id",
                "field_creator_id"}) {
            assertFalse(table.getColumns().stream().anyMatch(def -> def.getName().equals(audit)),
                    "流水表不得出现公共审计列: " + audit);
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

        // 12 业务列逐列断言（类型/长度/可空/COMMENT，文案逐字对齐分册 §3.3.3）
        assertColumn(column(table, "field_example_id"), "field_example_id", "流程实例id",
                ColumnType.STRING, 32, false);
        assertColumn(column(table, "field_node_id"), "field_node_id",
                "节点id(流程实例节点id,StartProcess/EndProcess 步骤可空)", ColumnType.STRING, 32, true);
        assertColumn(column(table, "field_workitem_id"), "field_workitem_id",
                "工作项id(工作项级任务留痕,节点/流程级可空)", ColumnType.STRING, 32, true);
        assertColumn(column(table, "field_task_name"), "field_task_name", "任务名称",
                ColumnType.STRING, 200, false);
        assertColumn(column(table, "field_task_bean"), "field_task_bean", "任务实现类/bean 标识",
                ColumnType.STRING, 200, false);
        assertColumn(column(table, "field_task_type"), "field_task_type",
                "执行步骤(ProcessTaskType): CreateProcess/StartProcess/StartNode/StartWorkitem/SendTodo/EndWorkitem/EndNode/EndProcess",
                ColumnType.STRING, 50, false);
        assertColumn(column(table, "field_params"), "field_params", "任务参数(JSON对象)",
                ColumnType.JSON, 0, true);
        assertColumn(column(table, "field_result"), "field_result", "执行结果摘要(JSON对象)",
                ColumnType.JSON, 0, true);
        assertColumn(column(table, "field_success"), "field_success", "是否成功: 1=成功 0=失败",
                ColumnType.BOOLEAN, 0, false);
        assertColumn(column(table, "field_error_msg"), "field_error_msg", "失败信息",
                ColumnType.TEXT, 0, true);
        assertColumn(column(table, "field_cost_ms"), "field_cost_ms", "耗时毫秒",
                ColumnType.BIGINT, 0, false);

        // field_create_time 按业务列标注 + 函数式默认值（BaseModel 不内建审计列）
        ColumnDef createTime = column(table, "field_create_time");
        assertEquals("field_create_time", createTime.getName());
        assertEquals("创建时间", createTime.getComment());
        assertEquals(ColumnType.TIMESTAMP, createTime.getType());
        assertFalse(createTime.isNullable());
        assertNull(createTime.getDefaultValue(), "函数式默认值不落 defaultValue");
        assertEquals(DefaultFunction.CURRENT_TIMESTAMP, createTime.getDefaultFunction());
        assertFalse(createTime.isPrimaryKey());

        // 索引清单（分册 §3.3.4）：普通 BTREE，本期不加 field_task_name / field_success 索引
        assertEquals(1, table.getIndexes().size());
        IndexDef byExample = table.getIndexes().get(0);
        assertEquals("idx_task_log_example", byExample.getName());
        assertEquals(List.of("field_example_id"), byExample.getColumns());
        assertFalse(byExample.isUnique());
    }

    /* ==================== 建表文本等价（强断言） ==================== */

    @Test
    void taskLogCreateTableDdlMatchesDesignBaseline() {
        TableDef table = SourceTableScanner.parse(SysProcessTaskLog.class);
        String normalized = normalize(SourceDdlGenerator.createTable(table));

        assertEquals(normalize(EXPECTED_SYS_PROCESS_TASK_LOG_DDL), normalized,
                "实体注解经扫描归一 + DDL 生成后与按分册 §3.3.3/§3.3.4 书写的建表语句整段逐字等价"
                        + "（列序/类型/长度/可空/默认值/COMMENT/索引/ENGINE/CHARSET）");

        // 语义片段出现顺序（列序与索引序额外显式校验）
        List<String> fragments = List.of(
                "field_id VARCHAR(32) NOT NULL COMMENT '主键ID'",
                "field_example_id VARCHAR(32) NOT NULL COMMENT '流程实例id'",
                "field_node_id VARCHAR(32) DEFAULT NULL COMMENT '节点id(流程实例节点id,StartProcess/EndProcess 步骤可空)'",
                "field_workitem_id VARCHAR(32) DEFAULT NULL COMMENT '工作项id(工作项级任务留痕,节点/流程级可空)'",
                "field_task_name VARCHAR(200) NOT NULL COMMENT '任务名称'",
                "field_task_bean VARCHAR(200) NOT NULL COMMENT '任务实现类/bean 标识'",
                "field_task_type VARCHAR(50) NOT NULL COMMENT '执行步骤(ProcessTaskType): CreateProcess/StartProcess/StartNode/StartWorkitem/SendTodo/EndWorkitem/EndNode/EndProcess'",
                "field_params JSON DEFAULT NULL COMMENT '任务参数(JSON对象)'",
                "field_result JSON DEFAULT NULL COMMENT '执行结果摘要(JSON对象)'",
                "field_success BIT(1) NOT NULL COMMENT '是否成功: 1=成功 0=失败'",
                "field_error_msg TEXT DEFAULT NULL COMMENT '失败信息'",
                "field_cost_ms BIGINT NOT NULL COMMENT '耗时毫秒'",
                "field_create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'",
                "PRIMARY KEY (field_id)",
                "KEY idx_task_log_example (field_example_id)");
        int position = -1;
        for (String fragment : fragments) {
            int found = normalized.indexOf(fragment);
            assertTrue(found >= 0, "缺失语义片段: " + fragment);
            assertTrue(found > position, "列序/索引序错位: " + fragment);
            position = found;
        }
        assertTrue(normalized.startsWith("CREATE TABLE IF NOT EXISTS sys_process_task_log ("));
        assertTrue(normalized.endsWith(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程任务执行日志表'"),
                "ENGINE/CHARSET/表 COMMENT 对齐既有脚本风格");
        assertFalse(normalized.contains("DROP"), "引擎永不生成 DROP 类语句");
    }

    /* ==================== 只增不改（Dao 方法面硬约束） ==================== */

    @Test
    void taskLogDaoExposesInsertAndQueryOnly() {
        // Dao 自声明方法面只允许写入留痕与查询；UPDATE / DELETE 由基类接口契约带入（同表族先例），
        // 本类不新增任何更新/删除业务方法
        List<String> declared = java.util.Arrays.stream(SysProcessTaskLogDao.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .sorted()
                .toList();
        assertEquals(List.of("insertLog", "selectListByExampleId", "selectListByExampleIdAndTaskType"),
                declared, "Dao 自声明公开方法只有插入与查询，无 UPDATE / DELETE 语义方法");
    }
}

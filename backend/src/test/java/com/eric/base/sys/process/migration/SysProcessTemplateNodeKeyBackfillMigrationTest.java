package com.eric.base.sys.process.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 流程模板节点业务键回填迁移测试（DM-20261119-001，纯 Mockito 单测，不连 MySQL、不启 Spring 上下文）
 *
 * @author Eric
 * @date 2026/11/19
 * @description 覆盖任务 P17-DB-005（分册 §5.2 / §5.5）的三块：1 契约测试——code 匹配
 *              DM-\d{8}-\d{3} 全匹配（且为本任务登记的 DM-20261119-001）、name 中文非空且
 *              与分册 §5.5 迁移登记表逐字一致、order=101；2 SQL 常量断言——经反射读取 private
 *              常量，断言含幂等守护（`WHERE n.field_node_key IS NULL`）、确定性排序
 *              （`ROW_NUMBER() OVER (PARTITION BY` + `ORDER BY field_no ASC, field_id ASC`）
 *              与 `CONCAT('N'` 键格式；3 migrate() 逻辑——mock JdbcTemplate（经 mock 的
 *              ObjectProvider 注入）以 ArgumentCaptor 捕获实参并断言为同一常量，JdbcTemplate 为
 *              null 时 WARN 跳过、不抛异常、零 JDBC 交互。
 */
@ExtendWith(MockitoExtension.class)
class SysProcessTemplateNodeKeyBackfillMigrationTest {

    /** 迁移 SQL 常量字段名（private static final，测试经反射读取，避免为测试放宽可见性） */
    private static final String SQL_FIELD_NAME = "NODE_KEY_BACKFILL_SQL";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    /* ==================== 契约测试（分册 §5.5 迁移登记表） ==================== */

    @Test
    void codeMatchesDmFormatAndRegisteredValue() {
        assertEquals("DM-20261119-001", migration().code(), "任务 P17-DB-005 登记的迁移编码");
        assertTrue(migration().code().matches("DM-\\d{8}-\\d{3}"),
                "code 须匹配 DM-yyyyMMdd-nnn 全匹配（CommonSourceMigrationService 构造期校验口径）");
    }

    @Test
    void nameIsChineseAndMatchesRegistry() {
        String name = migration().name();
        assertFalse(name.isBlank(), "name 中文非空");
        assertTrue(name.matches(".*[\\u4e00-\\u9fa5].*"), "name 须含中文（审计与排障用）");
        assertEquals("流程模板节点业务键回填（node_key backfill）", name,
                "与分册 §5.5 迁移登记表逐字一致");
    }

    @Test
    void orderIs101() {
        assertEquals(101, migration().order(), "分册 §5.1：首例占 100，本批自 101 起递增");
    }

    /* ==================== SQL 常量断言 ==================== */

    @Test
    void sqlConstantCarriesIdempotentGuardAndDeterministicOrder() throws Exception {
        String sql = migrationSqlConstant();
        assertTrue(sql.contains("UPDATE sys_process_template_node"),
                "目标表为 sys_process_template_node（分册 §5.2 目标对象）");
        assertTrue(sql.contains("WHERE field_node_key IS NULL"),
                "派生表须带 SQL 自身幂等守护（仅对未回填模板计算序号）");
        assertTrue(sql.contains("WHERE n.field_node_key IS NULL"),
                "外层须带 `WHERE n.field_node_key IS NULL` 守护（回填后命中 0 行、结果不变）");
        assertTrue(sql.contains("ROW_NUMBER() OVER (PARTITION BY"),
                "含 ROW_NUMBER 窗口（按 field_template_id 分组编号）");
        assertTrue(sql.contains("PARTITION BY field_template_id"),
                "分组键为 field_template_id（每个模板内 N1..Nn）");
        assertTrue(sql.contains("ORDER BY field_no ASC, field_id ASC"),
                "按 field_no 升序、并列按 field_id 字典序（确定性 tie-break）");
        assertTrue(sql.contains("CONCAT('N'"), "键格式为 N1..Nn");
    }

    /* ==================== migrate() 逻辑 ==================== */

    @Test
    void migrateExecutesBackfillSql() throws Exception {
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbcTemplate);

        migration().migrate();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture());
        assertEquals(migrationSqlConstant(), sql.getValue(), "实际执行语句即类内 SQL 常量（无手工拼串）");
    }

    @Test
    void migrateSkipsQuietlyWhenJdbcTemplateMissing() {
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(null);

        migration().migrate();

        // JdbcTemplate 不可用（排除数据源的测试上下文）：WARN 跳过，不抛异常、零 JDBC 交互
        verifyNoInteractions(jdbcTemplate);
    }

    /* ==================== 工具 ==================== */

    /** 构造被测迁移（ObjectProvider 以 mock 注入，契约/SQL 断言用例不触达其取值路径） */
    private SysProcessTemplateNodeKeyBackfillMigration migration() {
        return new SysProcessTemplateNodeKeyBackfillMigration(jdbcTemplateProvider);
    }

    /** 反射读取 private SQL 常量（避免为测试放宽生产类可见性） */
    private static String migrationSqlConstant() throws ReflectiveOperationException {
        Field field = SysProcessTemplateNodeKeyBackfillMigration.class.getDeclaredField(SQL_FIELD_NAME);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}

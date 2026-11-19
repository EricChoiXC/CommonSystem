package com.eric.base.sys.process.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 流程模板线性连线生成迁移测试（DM-20261119-002，纯 Mockito 单测，不连 MySQL、不启 Spring 上下文）
 *
 * @author Eric
 * @date 2026/11/19
 * @description 覆盖任务 P17-DB-005（分册 §5.3 / §5.5）的三块：1 契约测试——code 匹配
 *              DM-\d{8}-\d{3} 全匹配（且为本任务登记的 DM-20261119-002）、name 中文非空且
 *              与分册 §5.5 迁移登记表逐字一致、order=102；2 SQL 常量断言——经反射读取两个
 *              private 常量，断言清单查询含幂等守护（`LEFT JOIN` + `WHERE l.field_id IS NULL`）、
 *              确定性排序（`ROW_NUMBER() OVER (PARTITION BY` + `ORDER BY field_no ASC, field_id ASC`）
 *              与相邻对条件（`rn + 1`），插入语句含 `NOT EXISTS` 守护哨兵与字段口径
 *              （`'NORMAL'` / `b'0'` / `field_branch_order` 写 0）；3 migrate() 逻辑——mock
 *              JdbcTemplate（经 mock 的 ObjectProvider 注入）：缺失清单为空时零插入（不触发
 *              batchUpdate）、清单有行时批处理参数正确（field_id 为 IDUtil 生成的 32 位、from/to
 *              取自结果集）、JdbcTemplate 为 null 时 WARN 跳过、不抛异常、零 JDBC 交互。
 */
@ExtendWith(MockitoExtension.class)
class SysProcessTemplateLineBackfillMigrationTest {

    /** 缺失连线清单 SQL 常量字段名（private static final，测试经反射读取） */
    private static final String MISSING_SQL_FIELD_NAME = "MISSING_LINE_SQL";

    /** 线性连线插入 SQL 常量字段名（private static final，测试经反射读取） */
    private static final String INSERT_SQL_FIELD_NAME = "INSERT_LINE_SQL";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Mock
    private ResultSet resultSet;

    /* ==================== 契约测试（分册 §5.5 迁移登记表） ==================== */

    @Test
    void codeMatchesDmFormatAndRegisteredValue() {
        assertEquals("DM-20261119-002", migration().code(), "任务 P17-DB-005 登记的迁移编码");
        assertTrue(migration().code().matches("DM-\\d{8}-\\d{3}"),
                "code 须匹配 DM-yyyyMMdd-nnn 全匹配（CommonSourceMigrationService 构造期校验口径）");
    }

    @Test
    void nameIsChineseAndMatchesRegistry() {
        String name = migration().name();
        assertFalse(name.isBlank(), "name 中文非空");
        assertTrue(name.matches(".*[\\u4e00-\\u9fa5].*"), "name 须含中文（审计与排障用）");
        assertEquals("流程模板线性连线生成（模板连线 backfill）", name,
                "与分册 §5.5 迁移登记表逐字一致");
    }

    @Test
    void orderIs102() {
        assertEquals(102, migration().order(), "分册 §5.5：须晚于 001（101）以消费其回填的 field_node_key");
    }

    /* ==================== SQL 常量断言 ==================== */

    @Test
    void missingLineSqlCarriesGuardAndDeterministicOrder() throws Exception {
        String sql = sqlConstant(MISSING_SQL_FIELD_NAME);
        assertTrue(sql.contains("FROM sys_process_template_node"),
                "清单从模板节点表派生（分册 §5.3）");
        assertTrue(sql.contains("LEFT JOIN sys_process_template_line"),
                "LEFT JOIN 目标连线表以识别缺失连线");
        assertTrue(sql.contains("WHERE l.field_id IS NULL"),
                "仅取尚无连线行的相邻对（SQL 自身幂等守护之清单侧）");
        assertTrue(sql.contains("ROW_NUMBER() OVER (PARTITION BY"),
                "含 ROW_NUMBER 窗口（模板内节点序列编号）");
        assertTrue(sql.contains("PARTITION BY t.field_template_id"),
                "按 field_template_id 分组取序列");
        assertTrue(sql.contains("ORDER BY t.field_no ASC, t.field_id ASC"),
                "按 field_no 升序、并列按 field_id 字典序（与 001 的 tie-break 一致）");
        assertTrue(sql.contains("nxt.rn = available.rn + 1"),
                "以 rn + 1 取相邻对（N(i) → N(i+1)；单节点模板天然不命中）");
        assertTrue(sql.contains("WHERE t.field_node_key IS NOT NULL"),
                "节点键为 NULL 的行不参与（001 未完成时 JOIN 不命中并 WARN 计数）");
    }

    @Test
    void insertLineSqlCarriesNotExistsGuardAndFieldSemantics() throws Exception {
        String sql = sqlConstant(INSERT_SQL_FIELD_NAME);
        assertTrue(sql.contains("INSERT INTO sys_process_template_line"),
                "写入目标表 sys_process_template_line（分册 §5.3 目标对象）");
        assertTrue(sql.contains("NOT EXISTS"),
                "插入语句以 NOT EXISTS 为 SQL 自身幂等守护（双重：清单过滤 + 插入守护）");
        assertTrue(sql.contains("l.field_template_id = ?"),
                "NOT EXISTS 守护按「模板 + 起始键 + 目标键」判重");
        assertTrue(sql.contains("'NORMAL'"), "field_line_type 口径为 'NORMAL'");
        assertTrue(sql.contains("b'0'"), "field_is_default 口径为 b'0'");
        assertTrue(sql.contains("field_branch_order, field_branch_name, field_condition"),
                "列清单含分支序号/分支名/分支条件（口径 NULL / 0）");
        // 禁止使用 MySQL UUID()：field_id 由应用侧 IDUtil.initID() 生成
        assertFalse(sql.contains("UUID("), "field_id 不得使用 MySQL UUID()（应用侧 IDUtil.initID()）");
    }

    /* ==================== migrate() 逻辑 ==================== */

    @Test
    void migrateSkipsInsertWhenNoMissingLine() throws Exception {
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbcTemplate);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class))).thenReturn(0);
        // 清单为空：extractor 被调用但结果集无行（已生成过的连线不在清单内）
        when(resultSet.next()).thenReturn(false);
        doAnswer(invocation -> {
            ((ResultSetExtractor<?>) invocation.getArgument(1)).extractData(resultSet);
            return null;
        }).when(jdbcTemplate).query(any(String.class), any(ResultSetExtractor.class));

        migration().migrate();

        // 清单为空 → 不触发批处理（重复执行命中 0 行、SQL 自身幂等）
        verify(jdbcTemplate, never()).batchUpdate(any(String.class), any(List.class));
    }

    @Test
    void migrateBatchInsertsMissingLinesWithGeneratedIds() throws Exception {
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbcTemplate);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class))).thenReturn(0);
        // 结果集一行：模板 tpl-1 的 N1 → N2 缺失
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("template_id")).thenReturn("tpl-1");
        when(resultSet.getString("from_key")).thenReturn("N1");
        when(resultSet.getString("to_key")).thenReturn("N2");
        // 捕获 query 的 extractor 实参并以 mock 结果集驱动（等价于真实 JDBC 回调）
        doAnswer(invocation -> {
            ((ResultSetExtractor<?>) invocation.getArgument(1)).extractData(resultSet);
            return null;
        }).when(jdbcTemplate).query(any(String.class), any(ResultSetExtractor.class));
        when(jdbcTemplate.batchUpdate(any(String.class), any(List.class)))
                .thenReturn(new int[]{1});

        migration().migrate();

        ArgumentCaptor<List<Object[]>> batchArgs = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(eq(sqlConstant(INSERT_SQL_FIELD_NAME)), batchArgs.capture());
        List<Object[]> rows = batchArgs.getValue();
        assertEquals(1, rows.size(), "缺失清单 1 条 → 批处理 1 行");
        assertEquals(7, rows.get(0).length, "参数口径：field_id + 4 列值 + NOT EXISTS 判重 3 参");
        assertEquals(32, ((String) rows.get(0)[0]).length(), "field_id 由 IDUtil.initID() 生成（32 位）");
        assertEquals("tpl-1", rows.get(0)[1], "field_template_id 取自清单 template_id");
        assertEquals("N1", rows.get(0)[2], "field_from_key 取自清单 from_key");
        assertEquals("N2", rows.get(0)[3], "field_to_key 取自清单 to_key");
        assertEquals("tpl-1", rows.get(0)[4], "NOT EXISTS 判重参数：field_template_id");
        assertEquals("N1", rows.get(0)[5], "NOT EXISTS 判重参数：field_from_key");
        assertEquals("N2", rows.get(0)[6], "NOT EXISTS 判重参数：field_to_key");
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
    private SysProcessTemplateLineBackfillMigration migration() {
        return new SysProcessTemplateLineBackfillMigration(jdbcTemplateProvider);
    }

    /** 反射读取 private SQL 常量（避免为测试放宽生产类可见性） */
    private static String sqlConstant(String fieldName) throws ReflectiveOperationException {
        Field field = SysProcessTemplateLineBackfillMigration.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}

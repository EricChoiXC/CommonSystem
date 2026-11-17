package com.eric.base.sys.process.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 流程模板默认标记补录迁移测试（纯 Mockito 单测，不连 MySQL、不启 Spring 上下文）
 *
 * @author Eric
 * @date 2026/11/17
 * @description 覆盖任务 F19-BE-007 验收标准的三块：1 契约测试——code 匹配
 *              DM-yyyyMMdd-nnn 全匹配（且为本任务登记的当日首例 DM-20261117-001）、
 *              name 中文非空、order=100（首位迁移预留序号空间约定）；2 等价性断言——
 *              从 test classpath 读取 V1_01_038 存档脚本（src/main/resources 主资源），
 *              提取其中的 UPDATE 语句文本，按「去注释行 + 压缩空白 + 去结尾分号」归一化后
 *              与迁移类 private 常量（反射读取）逐字比对——这是「效果与原 SQL 脚本等价」
 *              的机器可验证口径；3 migrate() 逻辑——mock JdbcTemplate 验证执行了该语句
 *              （捕获实参同样与原脚本归一化比对，双通道防漂移），JdbcTemplate 为 null 时
 *              WARN 跳过不抛异常且零交互。
 */
@ExtendWith(MockitoExtension.class)
class SysProcessTemplateDefaultBackfillMigrationTest {

    /** V1_01_038 存档脚本（test classpath 可见：src/main/resources 主资源） */
    private static final String ARCHIVE_SCRIPT = "db/migration/V1_01_038__sys_process_template_default_backfill.sql";

    /** 迁移 SQL 常量字段名（private static final，测试经反射读取，避免为测试放宽可见性） */
    private static final String SQL_FIELD_NAME = "DEFAULT_BACKFILL_SQL";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    /* ==================== 契约测试 ==================== */

    @Test
    void codeMatchesDmFormatAndRegisteredValue() {
        assertEquals("DM-20261117-001", migration().code(),
                "任务 F19-BE-007 登记的当日首例编码");
        assertTrue(migration().code().matches("DM-\\d{8}-\\d{3}"),
                "code 须匹配 DM-yyyyMMdd-nnn 全匹配（CommonSourceMigrationService 构造期校验口径）");
    }

    @Test
    void nameIsChineseAndNotBlank() {
        String name = migration().name();
        assertFalse(name.isBlank(), "name 中文非空");
        assertTrue(name.matches(".*[\\u4e00-\\u9fa5].*"), "name 须含中文（审计与排障用）");
        assertEquals("流程模板默认标记补录（默认模板 backfill）", name, "登记的中文名");
    }

    @Test
    void orderIs100AsFirstMigration() {
        assertEquals(100, migration().order(),
                "首位迁移占 100（后续迁移依次递增，需排在本例之前的取 1~99）");
    }

    /* ==================== 等价性断言（与 V1_01_038 存档脚本逐字等价） ==================== */

    @Test
    void sqlConstantIsVerbatimEquivalentToArchiveScript() throws Exception {
        String constant = migrationSqlConstant();
        String expected = normalizeSql(loadArchiveUpdateSql());
        assertEquals(expected, normalizeSql(constant),
                "迁移 SQL 常量与 V1_01_038 原脚本 UPDATE 语句必须逐字等价（去注释行 + 压缩空白 + 去结尾分号后比对）");
        // 关键语义片段显式复核（ROW_NUMBER 窗口、default_cnt = 0 守护条件、置默认赋值）
        assertTrue(constant.contains("ROW_NUMBER() OVER (PARTITION BY field_model_name"),
                "含 ROW_NUMBER 窗口（每分类最新创建的启用模板）");
        assertTrue(constant.contains("d.default_cnt = 0"), "含 default_cnt = 0 守护条件（生成列唯一键兜底口径）");
        assertTrue(constant.contains("SET t.field_default = 1"), "含置默认赋值");
    }

    /* ==================== migrate() 逻辑 ==================== */

    @Test
    void migrateExecutesVerbatimBackfillSql() throws Exception {
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbcTemplate);

        migration().migrate();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture());
        assertEquals(normalizeSql(loadArchiveUpdateSql()), normalizeSql(sql.getValue()),
                "实际执行的语句与 V1_01_038 原脚本 UPDATE 语句逐字等价（执行通道二次防漂移）");
    }

    @Test
    void migrateSkipsQuietlyWhenJdbcTemplateMissing() {
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(null);

        migration().migrate();

        // JdbcTemplate 不可用（排除数据源的测试上下文）：WARN 跳过，不抛异常、零 JDBC 交互
        verifyNoInteractions(jdbcTemplate);
    }

    /* ==================== 工具 ==================== */

    /** 构造被测迁移（ObjectProvider 以 mock 注入，契约/等价性用例不触达其取值路径） */
    private SysProcessTemplateDefaultBackfillMigration migration() {
        return new SysProcessTemplateDefaultBackfillMigration(jdbcTemplateProvider);
    }

    /** 反射读取 private SQL 常量（避免为测试放宽生产类可见性） */
    private static String migrationSqlConstant() throws ReflectiveOperationException {
        Field field = SysProcessTemplateDefaultBackfillMigration.class.getDeclaredField(SQL_FIELD_NAME);
        field.setAccessible(true);
        return (String) field.get(null);
    }

    /**
     * 从 test classpath 读取 V1_01_038 存档脚本并提取其中的 UPDATE 语句文本
     * （自首个非注释的 UPDATE 行起至文件尾——该脚本仅此一条语句）
     */
    private static String loadArchiveUpdateSql() throws Exception {
        try (InputStream in = SysProcessTemplateDefaultBackfillMigrationTest.class.getClassLoader()
                .getResourceAsStream(ARCHIVE_SCRIPT)) {
            assertNotNull(in, "V1_01_038 存档脚本必须出现在 test classpath（src/main/resources 主资源）");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            StringBuilder statement = new StringBuilder();
            boolean started = false;
            for (String line : text.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (!started) {
                    if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                        continue;
                    }
                    assertTrue(trimmed.toUpperCase(Locale.ROOT).startsWith("UPDATE"),
                            "V1_01_038 存档脚本的首条语句应为 UPDATE: " + trimmed);
                    started = true;
                }
                statement.append(line).append('\n');
            }
            assertTrue(started, "V1_01_038 存档脚本中必须存在 UPDATE 语句");
            return statement.toString();
        }
    }

    /**
     * 归一化（「逐字一致」的机器可验证口径）：去注释行与空行、压缩空白为单空格、
     * 去结尾分号——两侧同口径后逐字比对，空白与注释差异不掩盖任何语义差异
     */
    private static String normalizeSql(String sql) {
        StringBuilder normalized = new StringBuilder();
        for (String line : sql.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (!normalized.isEmpty()) {
                normalized.append(' ');
            }
            normalized.append(trimmed);
        }
        String result = normalized.toString().replaceAll("\\s+", " ").trim();
        if (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}

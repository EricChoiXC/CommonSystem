package com.eric.base.common.source.service;

import com.eric.base.common.source.dao.CommonSourceSyncLogDao;
import com.eric.base.common.source.diff.SourceSchemaDiffer;
import com.eric.base.common.source.enums.ChangeLevel;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.model.ColumnDef;
import com.eric.base.common.source.model.SchemaChange;
import com.eric.base.common.source.model.TableDef;
import com.eric.base.common.source.scanner.SourceTableScanner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 表结构注解同步引擎测试（纯 Mockito 单测，不连 MySQL、不启 Spring 上下文）
 *
 * @author Eric
 * @date 2026/11/16
 * @description 覆盖任务 F19-BE-005 验收标准的引擎行为面：VALIDATE 零变更 DDL 执行（仅
 *              两条审计表自举）；INSTALL 仅执行缺失对象新增子集（L3 不 fail-fast）；
 *              APPLY L1 逐条执行 + L2 不执行 + 组合差异口径暂缓的 L1（ddl=null）不执行；
 *              APPLY 任一 L3 存在时先收集后执行（零执行）并抛含「手工 SQL / DataMigration」
 *              指引的异常；GET_LOCK 未获锁 WARN 跳过（无扫描无审计无释放锁）；RELEASE_LOCK
 *              finally 释放（含扫描异常路径）；审计行 SUCCESS / FAILED 两种落库（FAILED 时
 *              changes JSON 与异常链 error 内容断言）；执行器 DDL 黑名单（手工构造含 DROP
 *              的变更 → 抛异常零执行）；sync 结果 Do 的 JSON 序列化形状
 *              （changes 元素含 level/type/table/detail/sql）。
 *              SourceTableScanner 为静态工具经 MockedStatic 桩定；运行期联验（dev 库三模式）
 *              由主控执行。
 */
@ExtendWith(MockitoExtension.class)
class CommonSourceSyncServiceTest {

    @Mock
    private SourceSchemaDiffer differ;

    @Mock
    private CommonSourceSyncLogDao syncLogDao;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    /* ==================== 测试桩 ==================== */

    /** 仅含内建主键的最小表定义（differ 已 mock，内容不参与比对语义） */
    private static TableDef tableDef() {
        return new TableDef("mock_table", "同步引擎测试表", null,
                List.of(new ColumnDef("field_id", "主键ID", ColumnType.STRING, 32, 0,
                        false, null, DefaultFunction.NONE, true)),
                List.of(), List.of());
    }

    /** 构造变更条目（SchemaChange 入参序：level/type/table/column/index/detail/ddl） */
    private static SchemaChange change(ChangeLevel level, String type, String table,
                                       String column, String index, String detail, String ddl) {
        return new SchemaChange(level, type, table, column, index, detail, ddl);
    }

    /** 桩定 JdbcTemplate 提供者返回 mock 模板并构造服务 */
    private CommonSourceSyncService service(String mode) {
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbcTemplate);
        return new CommonSourceSyncService(differ, syncLogDao, jdbcTemplateProvider, mode);
    }

    /** 桩定获锁成功（RELEASE_LOCK 不打桩：finally 调用返回 null 走 WARN 分支，不影响语义） */
    private void lockAcquired() {
        when(jdbcTemplate.queryForObject(CommonSourceSyncService.SQL_GET_LOCK, Long.class,
                CommonSourceSyncService.LOCK_NAME)).thenReturn(1L);
    }

    /* ==================== VALIDATE：只报告零执行 ==================== */

    @Test
    void validateModeReportsWithoutExecutingChangeDdl() {
        CommonSourceSyncService service = service("VALIDATE");
        lockAcquired();
        List<SchemaChange> changes = List.of(
                change(ChangeLevel.L1, SchemaChange.MISSING_TABLE, "mock_table", null, null,
                        "标注表在实际库中不存在，按注解全量建表", "CREATE TABLE IF NOT EXISTS mock_table (field_id VARCHAR(32) NOT NULL COMMENT '主键ID')"),
                change(ChangeLevel.L2, SchemaChange.ORPHAN_COLUMN, "mock_table", "field_old", null,
                        "注解中不存在该列（历史遗留），引擎永不 DROP COLUMN", null),
                change(ChangeLevel.L2, SchemaChange.MANAGED_ORPHAN_TABLE, "old_table", null, null,
                        "managed 历史表失去 @TableSource 标注（疑似误删注解）", null));
        when(differ.diff(anyList(), anySet())).thenReturn(changes);

        CommonSourceSyncResultDo result;
        try (MockedStatic<SourceTableScanner> scanner = mockStatic(SourceTableScanner.class)) {
            scanner.when(SourceTableScanner::scan).thenReturn(List.of(tableDef()));
            result = service.sync();
        }

        assertNotNull(result);
        assertEquals("VALIDATE", result.getMode());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(1, result.getTableTotal());
        assertEquals(3, result.getChangeTotal());
        assertEquals(1, result.getL1Total());
        assertEquals(2, result.getL2Total());
        assertEquals(0, result.getL3Total());

        // 零变更 DDL 执行：仅两条审计表自举 CREATE TABLE IF NOT EXISTS（引擎自举 ≠ 变更 DDL）
        verify(jdbcTemplate, times(2)).execute(anyString());
        verify(jdbcTemplate).execute(argThatBootstrap(CommonSourceSyncService.BOOTSTRAP_SYNC_LOG_DDL));
        verify(jdbcTemplate).execute(argThatBootstrap(CommonSourceSyncService.BOOTSTRAP_MIGRATION_LOG_DDL));

        // RELEASE_LOCK finally 释放（成功路径）
        verify(jdbcTemplate).queryForObject(CommonSourceSyncService.SQL_RELEASE_LOCK, Long.class,
                CommonSourceSyncService.LOCK_NAME);

        // SUCCESS 审计行落库（mode/status/计数/changes JSON/error=null/creator=引擎系统身份）
        ArgumentCaptor<String> changesJson = ArgumentCaptor.forClass(String.class);
        verify(syncLogDao).insert(anyString(), eq("VALIDATE"), eq("SUCCESS"), eq(1), eq(3),
                changesJson.capture(), isNull(), anyLong(), eq(CommonSourceSyncService.SYSTEM_CREATOR_ID));
        String json = changesJson.getValue();
        assertTrue(json.contains("\"level\":\"L1\"") && json.contains("\"type\":\"MISSING_TABLE\"")
                        && json.contains("\"table\":\"mock_table\""),
                "changes JSON 明细含分级/类型/表名");
        assertTrue(json.contains("MANAGED_ORPHAN_TABLE") && json.contains("old_table"),
                "managed 孤儿表告警进入审计明细");
    }

    /* ==================== INSTALL：仅缺失对象新增子集 ==================== */

    @Test
    void installModeExecutesOnlyMissingObjectSubset() {
        CommonSourceSyncService service = service("INSTALL");
        lockAcquired();
        List<SchemaChange> changes = List.of(
                change(ChangeLevel.L1, SchemaChange.MISSING_TABLE, "mock_table", null, null, "缺失表",
                        "CREATE TABLE IF NOT EXISTS mock_table (field_id VARCHAR(32) NOT NULL COMMENT '主键ID')"),
                change(ChangeLevel.L1, SchemaChange.MISSING_COLUMN, "mock_table", "field_new", null, "缺失列",
                        "ALTER TABLE mock_table ADD COLUMN field_new VARCHAR(100) NOT NULL COMMENT '新增列'"),
                change(ChangeLevel.L1, SchemaChange.MISSING_INDEX, "mock_table", null, "idx_new", "缺失索引",
                        "ALTER TABLE mock_table ADD INDEX idx_new (field_id)"),
                change(ChangeLevel.L1, SchemaChange.WIDEN_LENGTH, "mock_table", "field_name", null, "VARCHAR 扩长",
                        "ALTER TABLE mock_table MODIFY COLUMN field_name VARCHAR(300) NOT NULL COMMENT '名称'"),
                change(ChangeLevel.L2, SchemaChange.TIGHTEN_NULLABLE, "mock_table", "field_name", null,
                        "列可空收紧：实际 NULL → 注解 NOT NULL", null),
                change(ChangeLevel.L3, SchemaChange.SHRINK_OR_INCOMPATIBLE, "mock_table", "field_code", null,
                        "长度收窄：注解 100 ↔ 实际 200", null),
                change(ChangeLevel.L2, SchemaChange.MANAGED_ORPHAN_TABLE, "old_table", null, null,
                        "managed 历史表失去标注", null));
        when(differ.diff(anyList(), anySet())).thenReturn(changes);

        CommonSourceSyncResultDo result;
        try (MockedStatic<SourceTableScanner> scanner = mockStatic(SourceTableScanner.class)) {
            scanner.when(SourceTableScanner::scan).thenReturn(List.of(tableDef()));
            result = service.sync();
        }

        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(7, result.getChangeTotal());

        // 仅缺失对象子集执行：2 条自举 + 3 条缺失对象 DDL；修改类 L1 / L2 / L3 一律不执行
        verify(jdbcTemplate, times(5)).execute(anyString());
        verify(jdbcTemplate).execute("ALTER TABLE mock_table ADD COLUMN field_new VARCHAR(100) NOT NULL COMMENT '新增列'");
        verify(jdbcTemplate).execute("ALTER TABLE mock_table ADD INDEX idx_new (field_id)");
        verify(jdbcTemplate, never()).execute(contains("MODIFY COLUMN"));
        // INSTALL 对 L3 只告警不 fail-fast（§4.3 INSTALL 列：不执行）
        verify(jdbcTemplate, never()).execute(contains("DROP TABLE"));

        ArgumentCaptor<String> changesJson = ArgumentCaptor.forClass(String.class);
        verify(syncLogDao).insert(anyString(), eq("INSTALL"), eq("SUCCESS"), eq(1), eq(7),
                changesJson.capture(), isNull(), anyLong(), eq(CommonSourceSyncService.SYSTEM_CREATOR_ID));
        assertTrue(changesJson.getValue().contains("WIDEN_LENGTH"),
                "INSTALL 未执行的修改类变更仍进审计明细");
    }

    /* ==================== APPLY：L1 逐条执行 + L2 告警 + 组合差异口径 ==================== */

    @Test
    void applyModeExecutesAllExecutableL1AndSkipsL2AndBlockedL1() {
        CommonSourceSyncService service = service("APPLY");
        lockAcquired();
        List<SchemaChange> changes = List.of(
                change(ChangeLevel.L1, SchemaChange.MISSING_COLUMN, "mock_table", "field_new", null, "缺失列",
                        "ALTER TABLE mock_table ADD COLUMN field_new VARCHAR(100) NOT NULL COMMENT '新增列'"),
                change(ChangeLevel.L1, SchemaChange.CHANGE_DEFAULT, "mock_table", "field_status", null, "列默认值变更",
                        "ALTER TABLE mock_table ALTER COLUMN field_status SET DEFAULT 'Y'"),
                change(ChangeLevel.L1, SchemaChange.WIDEN_LENGTH, "mock_table", "field_name", null, "VARCHAR 扩长",
                        "ALTER TABLE mock_table MODIFY COLUMN field_name VARCHAR(300) NOT NULL COMMENT '名称'"),
                change(ChangeLevel.L1, SchemaChange.LOOSEN_NULLABLE, "mock_table", "field_remark", null,
                        "列可空放宽（同列存在收窄差异，DDL 暂缓待人工裁决）", null),
                change(ChangeLevel.L2, SchemaChange.EXTRA_INDEX, "mock_table", null, "idx_old",
                        "实际库存在注解未声明的索引，引擎不删索引", null));
        when(differ.diff(anyList(), anySet())).thenReturn(changes);

        CommonSourceSyncResultDo result;
        try (MockedStatic<SourceTableScanner> scanner = mockStatic(SourceTableScanner.class)) {
            scanner.when(SourceTableScanner::scan).thenReturn(List.of(tableDef()));
            result = service.sync();
        }

        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(4, result.getL1Total(), "L1 计数含被组合差异口径暂缓的 L1（ddl=null）");
        assertEquals(1, result.getL2Total());

        // L1 DDL 逐条执行：2 条自举 + 3 条可执行 L1；仅动默认值子句的 SET DEFAULT 保留执行（组合差异口径）
        verify(jdbcTemplate, times(5)).execute(anyString());
        verify(jdbcTemplate).execute("ALTER TABLE mock_table ADD COLUMN field_new VARCHAR(100) NOT NULL COMMENT '新增列'");
        verify(jdbcTemplate).execute("ALTER TABLE mock_table ALTER COLUMN field_status SET DEFAULT 'Y'");
        verify(jdbcTemplate).execute("ALTER TABLE mock_table MODIFY COLUMN field_name VARCHAR(300) NOT NULL COMMENT '名称'");
        // L2 不执行；被暂缓的 L1（ddl=null，组合差异口径）不执行
        verify(jdbcTemplate, never()).execute(contains("DROP INDEX"));
        verify(syncLogDao).insert(anyString(), eq("APPLY"), eq("SUCCESS"), eq(1), eq(5),
                ArgumentCaptor.forClass(String.class).capture(), isNull(), anyLong(),
                eq(CommonSourceSyncService.SYSTEM_CREATOR_ID));
    }

    /* ==================== APPLY：L3 fail-fast（先收集后执行） ==================== */

    @Test
    void applyModeFailsFastOnAnyL3BeforeExecutingAnyDdl() {
        CommonSourceSyncService service = service("APPLY");
        lockAcquired();
        List<SchemaChange> changes = List.of(
                change(ChangeLevel.L1, SchemaChange.WIDEN_LENGTH, "mock_table", "field_name", null, "VARCHAR 扩长",
                        "ALTER TABLE mock_table MODIFY COLUMN field_name VARCHAR(300) NOT NULL COMMENT '名称'"),
                change(ChangeLevel.L3, SchemaChange.SHRINK_OR_INCOMPATIBLE, "mock_table", "field_code", null,
                        "长度收窄：注解 100 ↔ 实际 200（数据丢失风险）", null),
                change(ChangeLevel.L2, SchemaChange.ORPHAN_COLUMN, "mock_table", "field_old", null, "孤儿列", null));
        when(differ.diff(anyList(), anySet())).thenReturn(changes);

        IllegalStateException error;
        try (MockedStatic<SourceTableScanner> scanner = mockStatic(SourceTableScanner.class)) {
            scanner.when(SourceTableScanner::scan).thenReturn(List.of(tableDef()));
            error = assertThrows(IllegalStateException.class, service::sync);
        }

        // 异常信息含处理指引（手工 SQL 或 DataMigration）与 L3 明细
        assertTrue(error.getMessage().contains("手工 SQL"), "处理指引：手工 SQL");
        assertTrue(error.getMessage().contains("DataMigration"), "处理指引：DataMigration");
        assertTrue(error.getMessage().contains("SHRINK_OR_INCOMPATIBLE"), "L3 明细进异常信息");

        // 先收集后执行：存在任一 L3 一条 DDL 都不执行（仅 2 条审计表自举）
        verify(jdbcTemplate, times(2)).execute(anyString());
        verify(jdbcTemplate, never()).execute(contains("MODIFY COLUMN"));

        // RELEASE_LOCK finally 释放（异常路径）
        verify(jdbcTemplate).queryForObject(CommonSourceSyncService.SQL_RELEASE_LOCK, Long.class,
                CommonSourceSyncService.LOCK_NAME);

        // FAILED 审计行：已知 diff 明细照记 + field_error 记异常链
        ArgumentCaptor<String> changesJson = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> errorText = ArgumentCaptor.forClass(String.class);
        verify(syncLogDao).insert(anyString(), eq("APPLY"), eq("FAILED"), eq(1), eq(3),
                changesJson.capture(), errorText.capture(), anyLong(), eq(CommonSourceSyncService.SYSTEM_CREATOR_ID));
        String json = changesJson.getValue();
        assertTrue(json.contains("\"level\":\"L3\"") && json.contains("SHRINK_OR_INCOMPATIBLE"),
                "FAILED 审计行的 changes JSON 含 L3 明细");
        assertTrue(json.contains("WIDEN_LENGTH"), "未执行的 L1 明细照记");
        assertTrue(errorText.getValue().contains("IllegalStateException")
                        && errorText.getValue().contains("手工 SQL") && errorText.getValue().contains("DataMigration"),
                "field_error 记异常链（含类名与处理指引）");
    }

    /* ==================== 执行器 DDL 黑名单（双层收口第二层） ==================== */

    @Test
    void ddlBlacklistBlocksForbiddenStatement() {
        CommonSourceSyncService service = service("APPLY");
        lockAcquired();
        List<SchemaChange> changes = List.of(
                change(ChangeLevel.L1, SchemaChange.MISSING_TABLE, "mock_table", null, null, "缺失表（手工构造违例）",
                        "DROP TABLE mock_table"));
        when(differ.diff(anyList(), anySet())).thenReturn(changes);

        IllegalStateException error;
        try (MockedStatic<SourceTableScanner> scanner = mockStatic(SourceTableScanner.class)) {
            scanner.when(SourceTableScanner::scan).thenReturn(List.of(tableDef()));
            error = assertThrows(IllegalStateException.class, service::sync);
        }

        assertTrue(error.getMessage().contains("DROP TABLE"), "黑名单命中提示禁用关键字");
        verify(jdbcTemplate, times(2)).execute(anyString());
        verify(jdbcTemplate, never()).execute(contains("DROP TABLE"));

        ArgumentCaptor<String> errorText = ArgumentCaptor.forClass(String.class);
        verify(syncLogDao).insert(anyString(), eq("APPLY"), eq("FAILED"), eq(1), eq(1),
                ArgumentCaptor.forClass(String.class).capture(), errorText.capture(), anyLong(),
                eq(CommonSourceSyncService.SYSTEM_CREATOR_ID));
        assertTrue(errorText.getValue().contains("DROP TABLE"), "field_error 记黑名单违例详情");
    }

    /* ==================== 锁语义 ==================== */

    @Test
    void lockTimeoutSkipsWithoutScanAuditOrRelease() {
        CommonSourceSyncService service = service("VALIDATE");
        when(jdbcTemplate.queryForObject(CommonSourceSyncService.SQL_GET_LOCK, Long.class,
                CommonSourceSyncService.LOCK_NAME)).thenReturn(0L);

        CommonSourceSyncResultDo result;
        try (MockedStatic<SourceTableScanner> scanner = mockStatic(SourceTableScanner.class)) {
            result = service.sync();
            // 无扫描
            scanner.verify(SourceTableScanner::scan, never());
        }

        assertNull(result, "未获锁跳过返回 null");
        verifyNoInteractions(differ, syncLogDao);
        verify(jdbcTemplate, never()).execute(anyString());
        // 未获锁不释放他人持有的锁
        verify(jdbcTemplate, never()).queryForObject(CommonSourceSyncService.SQL_RELEASE_LOCK, Long.class,
                CommonSourceSyncService.LOCK_NAME);
    }

    @Test
    void releaseLockCalledInFinallyOnScanFailure() {
        CommonSourceSyncService service = service("VALIDATE");
        lockAcquired();

        IllegalStateException error;
        try (MockedStatic<SourceTableScanner> scanner = mockStatic(SourceTableScanner.class)) {
            scanner.when(SourceTableScanner::scan).thenThrow(new IllegalStateException("扫描违例: 列 COMMENT 必填非空"));
            error = assertThrows(IllegalStateException.class, service::sync);
        }
        assertTrue(error.getMessage().contains("扫描违例"));

        // 自举已完成（扫描在自举之后），RELEASE_LOCK finally 释放（异常路径）
        verify(jdbcTemplate, times(2)).execute(anyString());
        verify(jdbcTemplate).queryForObject(CommonSourceSyncService.SQL_RELEASE_LOCK, Long.class,
                CommonSourceSyncService.LOCK_NAME);

        // FAILED 审计行：diff 未执行 → changes JSON 为 null、tableTotal 为 0、error 记异常链
        ArgumentCaptor<String> errorText = ArgumentCaptor.forClass(String.class);
        verify(syncLogDao).insert(anyString(), eq("VALIDATE"), eq("FAILED"), eq(0), eq(0),
                isNull(), errorText.capture(), anyLong(), eq(CommonSourceSyncService.SYSTEM_CREATOR_ID));
        assertTrue(errorText.getValue().contains("IllegalStateException") && errorText.getValue().contains("扫描违例"));
    }

    /* ==================== 模式解析 ==================== */

    @Test
    void invalidModeFailsFastWithLegalValuesHint() {
        CommonSourceSyncService badMode = service("BOGUS");

        IllegalStateException error;
        try (MockedStatic<SourceTableScanner> scanner = mockStatic(SourceTableScanner.class)) {
            error = assertThrows(IllegalStateException.class, badMode::sync);
            scanner.verify(SourceTableScanner::scan, never());
        }

        assertTrue(error.getMessage().contains("VALIDATE") && error.getMessage().contains("INSTALL")
                        && error.getMessage().contains("APPLY"),
                "非法值 fail-fast 提示合法取值");
        // 模式解析在获锁之前，未触达任何基础设施
        verifyNoInteractions(jdbcTemplate, differ, syncLogDao);
    }

    @Test
    void modeParsingTrimsAndUpperCases() {
        CommonSourceSyncService service = service(" apply ");
        lockAcquired();
        when(differ.diff(anyList(), anySet())).thenReturn(List.of());

        CommonSourceSyncResultDo result;
        try (MockedStatic<SourceTableScanner> scanner = mockStatic(SourceTableScanner.class)) {
            scanner.when(SourceTableScanner::scan).thenReturn(List.of(tableDef()));
            result = service.sync();
        }

        assertNotNull(result);
        assertEquals("APPLY", result.getMode(), "模式解析大小写不敏感并去空白");
        assertEquals(0, result.getChangeTotal());
        verify(jdbcTemplate, times(2)).execute(anyString());
    }

    /* ==================== 结果 Do JSON 形状 ==================== */

    @Test
    void syncResultDoSerializesAuditChangeShape() throws Exception {
        CommonSourceSyncService service = service("VALIDATE");
        lockAcquired();
        when(differ.diff(anyList(), anySet())).thenReturn(List.of(
                change(ChangeLevel.L1, SchemaChange.MISSING_TABLE, "mock_table", null, null,
                        "标注表在实际库中不存在，按注解全量建表",
                        "CREATE TABLE IF NOT EXISTS mock_table (field_id VARCHAR(32) NOT NULL COMMENT '主键ID')")));

        CommonSourceSyncResultDo result;
        try (MockedStatic<SourceTableScanner> scanner = mockStatic(SourceTableScanner.class)) {
            scanner.when(SourceTableScanner::scan).thenReturn(List.of(tableDef()));
            result = service.sync();
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(mapper.writeValueAsString(result));
        assertEquals("VALIDATE", root.get("mode").asText());
        assertEquals("SUCCESS", root.get("status").asText());
        assertTrue(root.get("tableTotal").isInt());
        assertTrue(root.get("changeTotal").isInt());
        assertTrue(root.get("costMs").isNumber());
        JsonNode changes = root.get("changes");
        assertTrue(changes.isArray() && 1 == changes.size());
        JsonNode item = changes.get(0);
        // §5.3 field_changes 口径：[{level,type,table,detail,sql}]
        assertTrue(item.has("level") && item.has("type") && item.has("table")
                && item.has("detail") && item.has("sql"), "changes 元素含 level/type/table/detail/sql");
        assertEquals("L1", item.get("level").asText());
        assertEquals("MISSING_TABLE", item.get("type").asText());
        assertEquals("mock_table", item.get("table").asText());
        assertEquals("CREATE TABLE IF NOT EXISTS mock_table (field_id VARCHAR(32) NOT NULL COMMENT '主键ID')",
                item.get("sql").asText());
    }

    /* ==================== 内部工具 ==================== */

    /**
     * 自举 DDL 匹配器：断言执行语句为审计表自举 CREATE TABLE IF NOT EXISTS（排除变更 DDL 混入）
     */
    private static String argThatBootstrap(String expected) {
        return org.mockito.ArgumentMatchers.argThat(sql -> null != sql && expected.equals(sql));
    }
}

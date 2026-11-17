package com.eric.base.common.source.service;

import com.eric.base.common.source.dao.CommonSourceMigrationLogDao;
import com.eric.base.common.source.spi.SourceDataMigration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 数据迁移执行引擎测试（纯 Mockito 单测，不连 MySQL、不启 Spring 上下文）
 *
 * @author Eric
 * @date 2026/11/17
 * @description 覆盖任务 F19-BE-006 验收标准的引擎行为面：构造期 code 格式校验
 *              （DM-yyyyMMdd-nnn 全匹配，合法构造成功 / 非法各形态 fail-fast）与
 *              code 全局唯一 fail-fast；执行顺序确定性（order 升序、并列按 code 字典序）；
 *              幂等跳过（审计表存在 SUCCESS 行不执行 migrate、不落审计）；SUCCESS 审计行
 *              落库（code/name/status/起止时刻/耗时/引擎系统身份/32 位主键）；FAILED 落审计
 *              后原异常重新抛出（阻断启动、后续迁移不再执行、finally 释放锁）；审计写入自身
 *              失败仅记日志不吞原始异常；GET_LOCK 未获锁跳过（不自举不查幂等不落审计不释放
 *              锁）；JdbcTemplate 缺失跳过；无已注册迁移跳过（不取锁）。
 *              SourceDataMigration 以手写桩承载（执行即按序登记 code，供执行顺序断言）。
 */
@ExtendWith(MockitoExtension.class)
class CommonSourceMigrationServiceTest {

    @Mock
    private CommonSourceMigrationLogDao migrationLogDao;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    /** 执行记录：迁移执行即按实际顺序登记 code（手写桩写入，供执行顺序/跳过断言） */
    private final List<String> executionOrder = new ArrayList<>();

    /* ==================== 测试桩 ==================== */

    /** 测试桩：可编程的最小迁移实现（执行时先按序登记 code 再执行动作） */
    private static final class StubMigration implements SourceDataMigration {

        private final String code;
        private final String name;
        private final int order;
        private final Runnable action;

        private StubMigration(String code, String name, int order, Runnable action) {
            this.code = code;
            this.name = name;
            this.order = order;
            this.action = action;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public void migrate() {
            action.run();
        }
    }

    /** 空动作迁移桩（执行登记 code） */
    private SourceDataMigration migration(String code, String name, int order) {
        return new StubMigration(code, name, order, () -> executionOrder.add(code));
    }

    /** 桩定 JdbcTemplate 提供者返回 mock 模板并构造服务 */
    private CommonSourceMigrationService service(SourceDataMigration... migrations) {
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbcTemplate);
        return new CommonSourceMigrationService(Arrays.asList(migrations), migrationLogDao, jdbcTemplateProvider);
    }

    /** 桩定获锁成功（RELEASE_LOCK 不打桩：finally 调用返回 null 走 WARN 分支，不影响语义） */
    private void lockAcquired() {
        when(jdbcTemplate.queryForObject(CommonSourceSyncService.SQL_GET_LOCK, Long.class,
                CommonSourceMigrationService.MIGRATION_LOCK_NAME)).thenReturn(1L);
    }

    /** 桩定全部迁移均无 SUCCESS 审计行（未执行过） */
    private void noneExecuted() {
        when(migrationLogDao.existsSuccess(anyString())).thenReturn(false);
    }

    /* ==================== 构造期：code 格式与全局唯一 fail-fast ==================== */

    @Test
    void constructorAcceptsValidCodes() {
        CommonSourceMigrationService service = service(
                migration("DM-20261117-001", "首例迁移", 1),
                migration("DM-20261118-002", "次日迁移", 2));
        assertNotNull(service);
    }

    @Test
    void constructorRejectsInvalidCodeFormats() {
        List<String> invalidCodes = List.of(
                "MIG-20261117-001",       // 前缀非 DM
                "dm-20261117-001",        // 小写
                "DM-20261117-1",          // 序号不足三位
                "DM-202611170-01",        // 日期段位数不对
                "DM-20261117-001 ",       // 尾随空白（须全匹配）
                "DM-2026111a-001",        // 日期段含字母
                "");
        for (String code : invalidCodes) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> new CommonSourceMigrationService(
                            List.of(new StubMigration(code, "非法编码迁移", 1, () -> { })),
                            migrationLogDao, jdbcTemplateProvider),
                    "非法编码应阻断启动: [" + code + "]");
            assertTrue(error.getMessage().contains("DM-yyyyMMdd-nnn"),
                    "异常信息含格式要求: [" + code + "]");
        }
        // 非法编码在构造期即被拒绝，未触达审计设施
        verifyNoInteractions(migrationLogDao);
    }

    @Test
    void constructorRejectsDuplicateCode() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new CommonSourceMigrationService(
                        List.of(migration("DM-20261117-001", "迁移甲", 1),
                                migration("DM-20261117-001", "迁移乙", 2)),
                        migrationLogDao, jdbcTemplateProvider));
        assertTrue(error.getMessage().contains("全局唯一") && error.getMessage().contains("DM-20261117-001"),
                "异常信息含唯一性要求与重复 code");
        verifyNoInteractions(migrationLogDao);
    }

    /* ==================== 执行顺序确定性 ==================== */

    @Test
    void executesInOrderThenCodeSequence() {
        CommonSourceMigrationService service = service(
                migration("DM-20261117-002", "order2 编码002", 2),
                migration("DM-20261117-003", "order1", 1),
                migration("DM-20261117-001", "order2 编码001", 2));
        lockAcquired();
        noneExecuted();

        service.migrate();

        // 执行顺序确定性：order 升序，并列 order 按 code 字典序（003(order1) → 001 → 002）
        assertEquals(List.of("DM-20261117-003", "DM-20261117-001", "DM-20261117-002"), executionOrder,
                "order 升序、并列按 code 字典序");
        // 自举审计表一次
        verify(jdbcTemplate).execute(CommonSourceSyncService.BOOTSTRAP_MIGRATION_LOG_DDL);
    }

    /* ==================== 幂等跳过 ==================== */

    @Test
    void idempotentSkipsWhenSuccessRecordExists() {
        CommonSourceMigrationService service = service(migration("DM-20261117-001", "已执行迁移", 1));
        lockAcquired();
        when(migrationLogDao.existsSuccess("DM-20261117-001")).thenReturn(true);

        service.migrate();

        // 存在 SUCCESS 行：不执行 migrate、不落审计行（自举仍执行）
        assertTrue(executionOrder.isEmpty(), "存在 SUCCESS 行的迁移幂等跳过不执行");
        verify(migrationLogDao, never()).insert(anyString(), anyString(), anyString(), anyString(),
                any(), any(), anyString(), anyLong(), anyString());
        verify(jdbcTemplate).execute(CommonSourceSyncService.BOOTSTRAP_MIGRATION_LOG_DDL);
    }

    /* ==================== SUCCESS 审计行 ==================== */

    @Test
    void executedMigrationWritesSuccessAuditRow() {
        CommonSourceMigrationService service = service(migration("DM-20261117-001", "首例迁移", 1));
        lockAcquired();
        noneExecuted();

        service.migrate();

        assertEquals(List.of("DM-20261117-001"), executionOrder, "迁移已执行");
        ArgumentCaptor<String> fieldId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Date> startTime = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> endTime = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Long> costMs = ArgumentCaptor.forClass(Long.class);
        verify(migrationLogDao).insert(fieldId.capture(), eq("DM-20261117-001"), eq("首例迁移"),
                eq("SUCCESS"), startTime.capture(), endTime.capture(), isNull(),
                costMs.capture(), eq(CommonSourceSyncService.SYSTEM_CREATOR_ID));
        assertEquals(32, fieldId.getValue().length(), "field_id 由 IDUtil.initID 生成（32 位）");
        assertNotNull(startTime.getValue(), "开始时刻由 Service 显式写入");
        assertNotNull(endTime.getValue(), "结束时刻由 Service 显式写入");
        assertTrue(costMs.getValue() >= 0, "耗时毫秒非负");
    }

    /* ==================== FAILED 落审计后重抛（对齐 Flyway 失败语义） ==================== */

    @Test
    void failedMigrationWritesFailedAuditAndRethrows() {
        IllegalStateException failure = new IllegalStateException("模拟迁移失败");
        StubMigration broken = new StubMigration("DM-20261117-001", "失败迁移", 1,
                () -> { executionOrder.add("DM-20261117-001"); throw failure; });
        CommonSourceMigrationService service = service(broken,
                migration("DM-20261117-002", "后续迁移", 2));
        lockAcquired();
        noneExecuted();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, service::migrate);
        assertSame(failure, thrown, "原异常重新抛出（不包装不吞）");

        // FAILED 审计行：field_error 记异常链（类全名 + 消息），起止时刻与耗时照写
        ArgumentCaptor<String> errorText = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Date> startTime = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> endTime = ArgumentCaptor.forClass(Date.class);
        verify(migrationLogDao).insert(anyString(), eq("DM-20261117-001"), eq("失败迁移"),
                eq("FAILED"), startTime.capture(), endTime.capture(), errorText.capture(),
                anyLong(), eq(CommonSourceSyncService.SYSTEM_CREATOR_ID));
        String error = errorText.getValue();
        assertTrue(error.contains("java.lang.IllegalStateException") && error.contains("模拟迁移失败"),
                "field_error 记异常链（类全名: 消息）");
        assertNotNull(startTime.getValue());
        assertNotNull(endTime.getValue());

        // 失败阻断启动：后续迁移不再执行
        assertEquals(List.of("DM-20261117-001"), executionOrder, "失败后后续迁移不再执行");
        // RELEASE_LOCK finally 释放（异常路径）
        verify(jdbcTemplate).queryForObject(CommonSourceSyncService.SQL_RELEASE_LOCK, Long.class,
                CommonSourceMigrationService.MIGRATION_LOCK_NAME);
    }

    @Test
    void auditWriteFailureDoesNotSwallowOriginalError() {
        IllegalStateException failure = new IllegalStateException("模拟迁移失败");
        StubMigration broken = new StubMigration("DM-20261117-001", "失败迁移", 1, () -> { throw failure; });
        CommonSourceMigrationService service = service(broken);
        lockAcquired();
        noneExecuted();
        when(migrationLogDao.insert(anyString(), anyString(), anyString(), anyString(),
                any(), any(), anyString(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("审计表写入失败"));

        // 审计写入自身失败仅记日志，原始异常继续抛出（不吞不替换）
        IllegalStateException thrown = assertThrows(IllegalStateException.class, service::migrate);
        assertSame(failure, thrown, "审计写入失败不吞原始异常");
    }

    /* ==================== 锁语义 ==================== */

    @Test
    void lockTimeoutSkipsWithoutBootstrapIdempotenceOrAudit() {
        CommonSourceMigrationService service = service(migration("DM-20261117-001", "首例迁移", 1));
        when(jdbcTemplate.queryForObject(CommonSourceSyncService.SQL_GET_LOCK, Long.class,
                CommonSourceMigrationService.MIGRATION_LOCK_NAME)).thenReturn(0L);

        service.migrate();

        // 未获锁：不自举、不查幂等、不执行、不落审计、不释放他人持有的锁
        assertTrue(executionOrder.isEmpty());
        verify(jdbcTemplate, never()).execute(anyString());
        verifyNoInteractions(migrationLogDao);
        verify(jdbcTemplate, never()).queryForObject(CommonSourceSyncService.SQL_RELEASE_LOCK, Long.class,
                CommonSourceMigrationService.MIGRATION_LOCK_NAME);
    }

    /* ==================== 跳过分支 ==================== */

    @Test
    void jdbcTemplateMissingSkipsQuietly() {
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(null);
        CommonSourceMigrationService service = new CommonSourceMigrationService(
                List.of(migration("DM-20261117-001", "首例迁移", 1)), migrationLogDao, jdbcTemplateProvider);

        service.migrate();

        // JdbcTemplate 不可用：WARN 跳过，不比对不落审计、迁移不执行
        assertTrue(executionOrder.isEmpty());
        verifyNoInteractions(migrationLogDao, jdbcTemplate);
    }

    @Test
    void noRegisteredMigrationsSkipsWithoutLock() {
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbcTemplate);
        CommonSourceMigrationService service = new CommonSourceMigrationService(
                List.of(), migrationLogDao, jdbcTemplateProvider);

        service.migrate();

        // 无已注册迁移：INFO 跳过，不必取锁、不自举、不落审计
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), anyString());
        verify(jdbcTemplate, never()).execute(anyString());
        verifyNoInteractions(migrationLogDao);
    }
}

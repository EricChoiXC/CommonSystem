package com.eric.base.sys.number.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.log.enums.SysLogResult;
import com.eric.base.sys.log.enums.SysLogType;
import com.eric.base.sys.log.service.LogRecord;
import com.eric.base.sys.log.service.LogService;
import com.eric.base.sys.number.dao.SysNumberRecordDao;
import com.eric.base.sys.number.dao.SysNumberRecordDetailDao;
import com.eric.base.sys.number.dao.SysNumberRuleDao;
import com.eric.base.sys.number.model.SysNumberAcquireResultDo;
import com.eric.base.sys.number.model.SysNumberRecordDetail;
import com.eric.base.sys.number.model.SysNumberRule;
import com.eric.base.sys.number.request.SysNumberManageQuery;
import com.eric.base.sys.number.request.SysNumberResetRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;

import static com.eric.base.sys.number.properties.SysNumberConstants.ACTION_MANAGE_RESET;
import static com.eric.base.sys.number.properties.SysNumberConstants.ACTION_MANAGE_REISSUE;
import static com.eric.base.sys.number.properties.SysNumberConstants.ACTION_MANAGE_VOID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 编号取号明细管理与特权操作服务测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆盖任务 NB3-BE-005 验收标准（Mockito 纯单测，SQL 口径由
 *              SysNumberMapperXmlTest 断言）：
 *              <ul>
 *              <li>recordList / count：条件归一与 fieldRuleId → fieldScopeKey 翻译
 *                  （明细表无 field_rule_id 列，固定接入作用域 = 规则 field_id）、
 *                  voidFlag 三态与时间范围透传、分页参数归一；</li>
 *              <li>reset 定位：fieldRuleId 校验规则存在（NOT_FOUND）/ fieldScopeKey 直用 /
 *                  双空报错；sealByScope 影响行数分支（无限定 0 行报错、有限定 0 行幂等）；</li>
 *              <li>voidDetail：未作废成功仅更新 void 两字段（不触碰计数器与规则）、
 *                  已作废幂等不再更新、明细不存在报错、影响行数 ≠ 1 报错；</li>
 *              <li>reissue：未作废被拒、非固定接入（作用域未命中规则）被拒、
 *                  成功委托 acquireByModel 且参数为规则模型 + 原明细单据 ID；</li>
 *              <li>操作日志：三操作成功记 SUCCESS（module=SYS-NUMBER、action=ACTION_MANAGE_*、
 *                  businessId 含目标标识、after 含关键参数），操作失败记 FAILED 及原因后原样抛出。</li>
 *              </ul>
 */
class SysNumberManageServiceTest {

    private static final String RULE_ID = "R".repeat(32);

    private static final String DETAIL_ID = "E".repeat(32);

    private static final String MODEL_DOCUMENT_ID = "D".repeat(32);

    private static final String NON_FIXED_SCOPE = "ErpContract:CAT-1";

    private SysNumberRuleDao ruleDao;
    private SysNumberRecordDao recordDao;
    private SysNumberRecordDetailDao detailDao;
    private SysNumberGenerateService generateService;
    private LogService logService;
    private SysNumberManageService service;

    @BeforeEach
    void setUp() {
        ruleDao = mock(SysNumberRuleDao.class);
        recordDao = mock(SysNumberRecordDao.class);
        detailDao = mock(SysNumberRecordDetailDao.class);
        generateService = mock(SysNumberGenerateService.class);
        logService = mock(LogService.class);
        service = new SysNumberManageService(ruleDao, recordDao, detailDao, generateService, logService);
    }

    // ---------------------------------------------------------------- 构造辅助

    private SysNumberRule storedRule() {
        SysNumberRule rule = new SysNumberRule();
        rule.setFieldId(RULE_ID);
        rule.setFieldCode("CG-RULE");
        rule.setFieldName("采购编号规则");
        rule.setFieldModelId("process-example-code");
        rule.setFieldModelName("SysProcessExample");
        rule.setFieldModelField("fieldCode");
        rule.setFieldEnable(Boolean.TRUE);
        return rule;
    }

    private SysNumberRecordDetail detail(boolean voided) {
        SysNumberRecordDetail detail = new SysNumberRecordDetail();
        detail.setFieldId(DETAIL_ID);
        detail.setFieldScopeKey(RULE_ID);
        detail.setFieldPeriod("20261113");
        detail.setFieldSerialId("ci-seq");
        detail.setFieldSerialNo(6);
        detail.setFieldModelName("SysProcessExample");
        detail.setFieldModelId(MODEL_DOCUMENT_ID);
        detail.setFieldCode("CG20261113006");
        detail.setFieldRecordTime(new Date());
        detail.setFieldCreatorId("U".repeat(32));
        if (voided) {
            detail.setFieldVoidTime(new Date());
            detail.setFieldVoidReason("原始作废");
        }
        return detail;
    }

    private SysNumberResetRequest resetRequest(String ruleId, String scopeKey, String serialId, String period) {
        SysNumberResetRequest request = new SysNumberResetRequest();
        request.setFieldRuleId(ruleId);
        request.setFieldScopeKey(scopeKey);
        request.setFieldSerialId(serialId);
        request.setFieldPeriod(period);
        return request;
    }

    private LogRecord capturedLog() {
        ArgumentCaptor<LogRecord> captor = ArgumentCaptor.forClass(LogRecord.class);
        verify(logService, atLeastOnce()).record(captor.capture());
        List<LogRecord> records = captor.getAllValues();
        return records.get(records.size() - 1);
    }

    private void assertOperationBase(LogRecord record, String action, String businessId, String result) {
        assertEquals(SysLogType.OPERATION.getValue(), record.getLogType());
        assertEquals("SYS-NUMBER", record.getModule());
        assertEquals(action, record.getAction());
        assertEquals(businessId, record.getBusinessId());
        assertEquals(result, record.getResult());
        if (SysLogResult.SUCCESS.getValue().equals(result)) {
            assertNull(record.getFailReason(), "成功日志无失败原因");
        } else {
            assertNotNull(record.getFailReason(), "失败日志须含失败原因");
        }
    }

    // ---------------------------------------------------------------- recordList / count

    @Test
    void recordListTranslatesRuleIdToScopeAndNormalizes() {
        SysNumberManageQuery query = new SysNumberManageQuery();
        query.setFieldRuleId(" " + RULE_ID + " ");
        query.setFieldPeriod(" 20261113 ");
        Pages pages = new Pages();
        pages.setPageNo(0);
        pages.setPageSize(0);
        pages.setNeedPage(true);
        when(detailDao.selectQueryList(any(SysNumberManageQuery.class), any(Pages.class)))
                .thenReturn(List.of(detail(false)));

        List<SysNumberRecordDetail> result = service.recordList(query, pages);

        assertEquals(1, result.size());
        ArgumentCaptor<SysNumberManageQuery> queryCaptor = ArgumentCaptor.forClass(SysNumberManageQuery.class);
        ArgumentCaptor<Pages> pagesCaptor = ArgumentCaptor.forClass(Pages.class);
        verify(detailDao).selectQueryList(queryCaptor.capture(), pagesCaptor.capture());
        // fieldRuleId 翻译为作用域键（明细表无 field_rule_id 列，固定接入作用域 = 规则 field_id）
        assertEquals(RULE_ID, queryCaptor.getValue().getFieldScopeKey());
        assertEquals(RULE_ID, queryCaptor.getValue().getFieldRuleId());
        assertEquals("20261113", queryCaptor.getValue().getFieldPeriod());
        assertNull(queryCaptor.getValue().getVoidFlag(), "voidFlag 缺省不过滤");
        assertNull(queryCaptor.getValue().getRecordTimeBegin());
        assertNull(queryCaptor.getValue().getRecordTimeEnd());
        assertEquals(1, pagesCaptor.getValue().getPageNo(), "页码小于 1 修正为 1");
        assertEquals(10, pagesCaptor.getValue().getPageSize(), "页大小小于 1 修正为 10");
        verifyNoInteractions(logService, recordDao);
    }

    @Test
    void recordListRuleIdWinsOverScopeKeyAndFlagsPassThrough() {
        SysNumberManageQuery query = new SysNumberManageQuery();
        query.setFieldRuleId(RULE_ID);
        query.setFieldScopeKey(NON_FIXED_SCOPE);
        query.setVoidFlag(Boolean.FALSE);
        Date begin = new Date();
        Date end = new Date();
        query.setRecordTimeBegin(begin);
        query.setRecordTimeEnd(end);
        when(detailDao.selectQueryList(any(SysNumberManageQuery.class), any(Pages.class)))
                .thenReturn(List.of());

        service.recordList(query, new Pages());

        ArgumentCaptor<SysNumberManageQuery> queryCaptor = ArgumentCaptor.forClass(SysNumberManageQuery.class);
        verify(detailDao).selectQueryList(queryCaptor.capture(), any(Pages.class));
        assertEquals(RULE_ID, queryCaptor.getValue().getFieldScopeKey(),
                "fieldRuleId 与 fieldScopeKey 同时传入以 fieldRuleId 优先");
        assertEquals(Boolean.FALSE, queryCaptor.getValue().getVoidFlag(), "voidFlag 三态透传");
        assertSame(begin, queryCaptor.getValue().getRecordTimeBegin(), "时间范围透传");
        assertSame(end, queryCaptor.getValue().getRecordTimeEnd(), "时间范围透传");
    }

    @Test
    void recordListKeepsPlainScopeKeyWhenRuleIdAbsent() {
        SysNumberManageQuery query = new SysNumberManageQuery();
        query.setFieldScopeKey(" " + NON_FIXED_SCOPE + " ");
        query.setVoidFlag(Boolean.TRUE);
        when(detailDao.selectQueryList(any(SysNumberManageQuery.class), any(Pages.class)))
                .thenReturn(List.of());

        service.recordList(query, null);

        ArgumentCaptor<SysNumberManageQuery> queryCaptor = ArgumentCaptor.forClass(SysNumberManageQuery.class);
        verify(detailDao).selectQueryList(queryCaptor.capture(), any(Pages.class));
        assertEquals(NON_FIXED_SCOPE, queryCaptor.getValue().getFieldScopeKey(), "无 fieldRuleId 时保留原作用域键");
        assertNull(queryCaptor.getValue().getFieldRuleId());
        assertEquals(Boolean.TRUE, queryCaptor.getValue().getVoidFlag());
    }

    @Test
    void countDelegatesNormalizedConditions() {
        when(detailDao.countQueryList(any(SysNumberManageQuery.class))).thenReturn(9L);

        long total = service.count(null);

        assertEquals(9L, total);
        ArgumentCaptor<SysNumberManageQuery> queryCaptor = ArgumentCaptor.forClass(SysNumberManageQuery.class);
        verify(detailDao).countQueryList(queryCaptor.capture());
        assertNotNull(queryCaptor.getValue(), "空条件兜底为空查询对象");
        assertNull(queryCaptor.getValue().getFieldScopeKey());
    }

    // ---------------------------------------------------------------- reset

    @Test
    void resetResolvesScopeFromRuleIdAfterRuleExistenceCheck() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule());
        when(recordDao.sealByScope(RULE_ID, "ci-seq", "20261113")).thenReturn(1);

        Integer sealed = service.reset(resetRequest(RULE_ID, null, "ci-seq", "20261113"));

        assertEquals(1, sealed);
        verify(recordDao).sealByScope(RULE_ID, "ci-seq", "20261113");
        LogRecord record = capturedLog();
        assertOperationBase(record, ACTION_MANAGE_RESET, RULE_ID, SysLogResult.SUCCESS.getValue());
        assertTrue(record.getAfter().contains("\"sealedCount\":1"), "after 含封存行数：" + record.getAfter());
    }

    @Test
    void resetWithUnknownRuleThrowsNotFound() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(null);

        NumberRuleNotFoundException exception = assertThrows(NumberRuleNotFoundException.class,
                () -> service.reset(resetRequest(RULE_ID, null, null, null)));

        assertEquals(NumberRuleNotFoundException.CODE, exception.getCode());
        verifyNoInteractions(recordDao, logService, detailDao, generateService);
    }

    @Test
    void resetResolvesScopeFromScopeKeyWhenRuleIdAbsent() {
        when(recordDao.sealByScope(NON_FIXED_SCOPE, null, null)).thenReturn(1);

        Integer sealed = service.reset(resetRequest(null, NON_FIXED_SCOPE, null, null));

        assertEquals(1, sealed, "非固定接入作用域原样使用，规则查重不执行");
        verify(ruleDao, never()).selectById(anyString());
        verify(recordDao).sealByScope(NON_FIXED_SCOPE, null, null);
    }

    @Test
    void resetRequiresScope() {
        IllegalArgumentException emptyBoth = assertThrows(IllegalArgumentException.class,
                () -> service.reset(resetRequest(null, null, null, null)));
        assertTrue(emptyBoth.getMessage().contains("fieldRuleId 与 fieldScopeKey 至少传一个"));
        IllegalArgumentException emptyBody = assertThrows(IllegalArgumentException.class,
                () -> service.reset(null));
        assertTrue(emptyBody.getMessage().contains("fieldRuleId 与 fieldScopeKey 至少传一个"));
        verifyNoInteractions(recordDao, logService);
    }

    @Test
    void resetZeroAffectedRowsWithoutLimitsThrowsAndLogsFailed() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule());
        when(recordDao.sealByScope(RULE_ID, null, null)).thenReturn(0);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.reset(resetRequest(RULE_ID, null, null, null)),
                "无任何限定且 0 行 = 该作用域无使用中计数行，报错");

        assertTrue(exception.getMessage().contains("无可重置的使用中计数行"));
        LogRecord record = capturedLog();
        assertOperationBase(record, ACTION_MANAGE_RESET, RULE_ID, SysLogResult.FAILED.getValue());
        assertTrue(record.getFailReason().contains("无可重置的使用中计数行"));
    }

    @Test
    void resetZeroAffectedRowsWithLimitsReturnsZeroSilently() {
        // 有 fieldSerialId / fieldPeriod 限定时 0 行可能 = 本就无该行，幂等返回 sealed=0
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule());
        when(recordDao.sealByScope(RULE_ID, "ci-seq", "20261113")).thenReturn(0);

        Integer sealed = service.reset(resetRequest(RULE_ID, null, "ci-seq", "20261113"));

        assertEquals(0, sealed);
        LogRecord record = capturedLog();
        assertOperationBase(record, ACTION_MANAGE_RESET, RULE_ID, SysLogResult.SUCCESS.getValue());
        assertTrue(record.getAfter().contains("\"sealedCount\":0"));
    }

    @Test
    void resetLogsFailedAndRethrowsWhenSealFails() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule());
        when(recordDao.sealByScope(RULE_ID, null, null)).thenThrow(new IllegalStateException("db down"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.reset(resetRequest(RULE_ID, null, null, null)));

        assertEquals("db down", exception.getMessage());
        LogRecord record = capturedLog();
        assertOperationBase(record, ACTION_MANAGE_RESET, RULE_ID, SysLogResult.FAILED.getValue());
        assertEquals("db down", record.getFailReason());
    }

    // ---------------------------------------------------------------- void

    @Test
    void voidDetailUpdatesOnlyVoidFieldsAndReturnsRefreshedState() {
        SysNumberRecordDetail exist = detail(false);
        SysNumberRecordDetail refreshed = detail(true);
        refreshed.setFieldVoidReason("录入错误");
        when(detailDao.selectById(DETAIL_ID)).thenReturn(exist, refreshed);
        when(detailDao.updateVoid(anyString(), any(Date.class), anyString())).thenReturn(1);

        SysNumberRecordDetail result = service.voidDetail(DETAIL_ID, " 录入错误 ");

        assertSame(refreshed, result);
        ArgumentCaptor<Date> voidTimeCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(detailDao).updateVoid(eq(DETAIL_ID), voidTimeCaptor.capture(), reasonCaptor.capture());
        assertNotNull(voidTimeCaptor.getValue(), "作废时间必填");
        assertEquals("录入错误", reasonCaptor.getValue(), "作废原因 trim 后写入");
        // D8：不回退计数器、不改任何业务字段（仅 void 两字段语句，计数器 Dao 不被触碰）
        verifyNoInteractions(recordDao, generateService);
        LogRecord record = capturedLog();
        assertOperationBase(record, ACTION_MANAGE_VOID, DETAIL_ID, SysLogResult.SUCCESS.getValue());
        assertTrue(record.getBefore().contains("\"voided\":false"), "before 记录作废前状态：" + record.getBefore());
        assertTrue(record.getAfter().contains("\"voided\":true"), "after 记录作废后状态：" + record.getAfter());
    }

    @Test
    void voidDetailIdempotentWhenAlreadyVoided() {
        SysNumberRecordDetail exist = detail(true);
        when(detailDao.selectById(DETAIL_ID)).thenReturn(exist);

        SysNumberRecordDetail result = service.voidDetail(DETAIL_ID, "重复作废");

        assertSame(exist, result, "已作废明细幂等返回原状态");
        verify(detailDao, never()).updateVoid(anyString(), any(Date.class), anyString());
        LogRecord record = capturedLog();
        assertOperationBase(record, ACTION_MANAGE_VOID, DETAIL_ID, SysLogResult.SUCCESS.getValue());
    }

    @Test
    void voidDetailRejectsMissingDetail() {
        when(detailDao.selectById(DETAIL_ID)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.voidDetail(DETAIL_ID, "任意原因"));

        assertTrue(exception.getMessage().contains("取号明细不存在"));
        verifyNoInteractions(logService, recordDao);
    }

    @Test
    void voidDetailThrowsWhenAffectedRowsNotOneAndLogsFailed() {
        when(detailDao.selectById(DETAIL_ID)).thenReturn(detail(false));
        when(detailDao.updateVoid(anyString(), any(Date.class), anyString())).thenReturn(0);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.voidDetail(DETAIL_ID, null));

        assertTrue(exception.getMessage().contains("void expected one affected row"));
        LogRecord record = capturedLog();
        assertOperationBase(record, ACTION_MANAGE_VOID, DETAIL_ID, SysLogResult.FAILED.getValue());
        verify(detailDao, times(1)).selectById(DETAIL_ID);
    }

    // ---------------------------------------------------------------- reissue

    @Test
    void reissueRejectsNotVoidedDetail() {
        when(detailDao.selectById(DETAIL_ID)).thenReturn(detail(false));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.reissue(DETAIL_ID));

        assertTrue(exception.getMessage().contains("未作废"), "仅已作废明细可补号：" + exception.getMessage());
        verifyNoInteractions(ruleDao, generateService, logService);
    }

    @Test
    void reissueRejectsNonFixedScopeDetail() {
        SysNumberRecordDetail detail = detail(true);
        detail.setFieldScopeKey(NON_FIXED_SCOPE);
        when(detailDao.selectById(DETAIL_ID)).thenReturn(detail);
        when(ruleDao.selectById(NON_FIXED_SCOPE)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.reissue(DETAIL_ID));

        assertTrue(exception.getMessage().contains("非固定接入明细不支持补号"), exception.getMessage());
        verifyNoInteractions(generateService, logService);
    }

    @Test
    void reissueRejectsMissingDetail() {
        when(detailDao.selectById(DETAIL_ID)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.reissue(DETAIL_ID));

        assertTrue(exception.getMessage().contains("取号明细不存在"));
        verifyNoInteractions(ruleDao, generateService, logService);
    }

    @Test
    void reissueDelegatesToAcquireByModelWithRuleModelAndLogsSuccess() {
        when(detailDao.selectById(DETAIL_ID)).thenReturn(detail(true));
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule());
        SysNumberAcquireResultDo acquire = new SysNumberAcquireResultDo();
        acquire.setCode("CG20261113007");
        acquire.setRecordDetailId("N".repeat(32));
        acquire.setReused(false);
        when(generateService.acquireByModel("SysProcessExample", "fieldCode", MODEL_DOCUMENT_ID))
                .thenReturn(acquire);

        SysNumberAcquireResultDo result = service.reissue(DETAIL_ID);

        assertSame(acquire, result, "响应同 generate 形态");
        // 顺延取新号：模型名 / 字段取自规则，单据 ID 取自原明细（幂等锚点）
        verify(generateService).acquireByModel("SysProcessExample", "fieldCode", MODEL_DOCUMENT_ID);
        // 旧号留审计：不更新原明细任何字段、不触碰计数器 Dao（取号由 GenerateService 内部落明细）
        verify(detailDao, never()).updateVoid(anyString(), any(Date.class), anyString());
        verifyNoInteractions(recordDao);
        LogRecord record = capturedLog();
        assertOperationBase(record, ACTION_MANAGE_REISSUE, DETAIL_ID, SysLogResult.SUCCESS.getValue());
        assertTrue(record.getAfter().contains("\"code\":\"CG20261113007\""), "after 含新编号：" + record.getAfter());
        assertTrue(record.getAfter().contains("\"originalDetailId\":\"" + DETAIL_ID + "\""),
                "after 含原明细 ID：" + record.getAfter());
    }

    @Test
    void reissueLogsFailedAndRethrowsWhenAcquireFails() {
        when(detailDao.selectById(DETAIL_ID)).thenReturn(detail(true));
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule());
        when(generateService.acquireByModel(anyString(), anyString(), anyString()))
                .thenThrow(new NumberRuleDisabledException("接入项无启用编号规则"));

        NumberRuleDisabledException exception = assertThrows(NumberRuleDisabledException.class,
                () -> service.reissue(DETAIL_ID));

        assertEquals(NumberRuleDisabledException.CODE, exception.getCode());
        LogRecord record = capturedLog();
        assertOperationBase(record, ACTION_MANAGE_REISSUE, DETAIL_ID, SysLogResult.FAILED.getValue());
        assertTrue(record.getFailReason().contains("CRM_NUMBER_RULE_DISABLED"), record.getFailReason());
    }
}

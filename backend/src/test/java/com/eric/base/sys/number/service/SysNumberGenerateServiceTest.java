package com.eric.base.sys.number.service;

import com.eric.base.sys.number.dao.SysNumberRecordDao;
import com.eric.base.sys.number.dao.SysNumberRecordDetailDao;
import com.eric.base.sys.number.dao.SysNumberRuleDao;
import com.eric.base.sys.number.model.SysNumberAcquireResultDo;
import com.eric.base.sys.number.model.SysNumberRecord;
import com.eric.base.sys.number.model.SysNumberRecordDetail;
import com.eric.base.sys.number.model.SysNumberRule;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo.Item;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 编号取号核心服务测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆盖任务 NB2-BE-002 验收标准（Mockito 单测，并发原子性由
 *              uk + ODKU 行锁在 DB 层保证、SQL 口径由 SysNumberMapperXmlTest 断言）：
 *              <ul>
 *              <li>双入口：固定接入按启用规则定位（无规则 NOT_FOUND / 无启用 DISABLED）、
 *                  非固定接入 acquireByConfig 作用域与 field_rule_id 空落明细；</li>
 *              <li>取号主链路：首号按 start 初始化、计数推进、明细落库字段齐全；</li>
 *              <li>幂等：预检命中 reused=true 不推进计数器；行锁等待后并发同单取号
 *                  经锁定读二次确认命中即回滚（不落明细）；</li>
 *              <li>C6 容量：超出 length 容量抛 CRM_NUMBER_SERIAL_EXHAUSTED 且不落明细；</li>
 *              <li>渲染引擎：text/datetime/seq 补零/random 双分支；周期键派生对齐
 *                  方案 §5.1 示例（WEEK 用 ISO-8601 周年）；</li>
 *              <li>并发编排：多线程取号流水号互异（服务编排无共享可变态）。</li>
 *              </ul>
 */
class SysNumberGenerateServiceTest {

    /** 固定取号时刻：2026-11-12 10:30:40（方案 §5.1 周期表示例日期，ISO 第 46 周） */
    private static final Date RECORD_TIME = Date.from(
            LocalDateTime.of(2026, 11, 12, 10, 30, 40).atZone(ZoneId.systemDefault()).toInstant());

    private static final String SCOPE = "R".repeat(32);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 当日周期键（DAY=yyyyMMdd）：取号入口的取号时刻为服务运行时 new Date()，
     * 断言按当日动态计算，避免固定日期漂移（今天即 2026-11-13 口径）。
     */
    private static String todayPeriod() {
        return LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private SysNumberRecordDao recordDao;
    private SysNumberRecordDetailDao detailDao;
    private SysNumberRuleDao ruleDao;
    private SysNumberGenerateService service;

    @BeforeEach
    void setUp() {
        recordDao = mock(SysNumberRecordDao.class);
        detailDao = mock(SysNumberRecordDetailDao.class);
        ruleDao = mock(SysNumberRuleDao.class);
        service = new SysNumberGenerateService(recordDao, detailDao, ruleDao, new SysNumberRuleValidator());
    }

    // ---------------------------------------------------------------- 构造辅助

    private Item item(String id, String type, String name) {
        Item item = new Item();
        item.setId(id);
        item.setType(type);
        item.setName(name);
        return item;
    }

    /** 经典三段规则：前缀 + 日期 + 流水（方案 §2.1 示例形态） */
    private SysNumberRuleConfigDo classicConfig() {
        Item text = item("ci-text", SysNumberRuleConfigDo.TYPE_TEXT, "前缀");
        text.setText("CG");
        Item datetime = item("ci-date", SysNumberRuleConfigDo.TYPE_DATETIME, "日期");
        datetime.setFormat("yyyyMMdd");
        Item seq = item("ci-seq", SysNumberRuleConfigDo.TYPE_SEQ, "流水号");
        seq.setStart(1);
        seq.setLength(3);
        seq.setPadZero(true);
        seq.setResetPeriod(SysNumberRuleConfigDo.RESET_DAY);
        SysNumberRuleConfigDo config = new SysNumberRuleConfigDo();
        config.setItems(new ArrayList<>(List.of(text, datetime, seq)));
        return config;
    }

    /** 存储态规则（field_rule 为 Jackson 序列化字符串） */
    private SysNumberRule storedRule(SysNumberRuleConfigDo config, boolean enable) {
        SysNumberRule rule = new SysNumberRule();
        rule.setFieldId(SCOPE);
        rule.setFieldCode("CG-RULE");
        rule.setFieldName("采购编号规则");
        rule.setFieldModelId("process-example-code");
        rule.setFieldModelName("SysProcessExample");
        rule.setFieldModelField("fieldCode");
        rule.setFieldEnable(enable);
        try {
            rule.setFieldRule(JSON_MAPPER.writeValueAsString(config));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return rule;
    }

    private void stubHappyPath(int currentSerialNo) {
        when(detailDao.selectValidByScopeAndModel(anyString(), anyString())).thenReturn(null);
        when(recordDao.sealOtherPeriods(anyString(), anyString(), anyString())).thenReturn(0);
        when(recordDao.upsertSerial(any(SysNumberRecord.class))).thenReturn(1);
        when(recordDao.selectCurrentSerialNo(anyString(), anyString(), anyString())).thenReturn(currentSerialNo);
        when(detailDao.lockValidByScopeAndModel(anyString(), anyString())).thenReturn(null);
        when(detailDao.insert(any(SysNumberRecordDetail.class))).thenReturn(1);
    }

    // ---------------------------------------------------------------- 固定接入定位

    @Test
    void acquireByModelWithoutAnyRuleThrowsNotFound() {
        when(ruleDao.selectByModel("SysProcessExample", "fieldCode")).thenReturn(List.of());

        NumberRuleNotFoundException exception = assertThrows(NumberRuleNotFoundException.class,
                () -> service.acquireByModel("SysProcessExample", "fieldCode", "D".repeat(32)));

        assertEquals(NumberRuleNotFoundException.CODE, exception.getCode());
        verifyNoInteractions(recordDao, detailDao);
    }

    @Test
    void acquireByModelWithoutEnabledRuleThrowsDisabled() {
        when(ruleDao.selectByModel("SysProcessExample", "fieldCode"))
                .thenReturn(List.of(storedRule(classicConfig(), false)));

        NumberRuleDisabledException exception = assertThrows(NumberRuleDisabledException.class,
                () -> service.acquireByModel("SysProcessExample", "fieldCode", "D".repeat(32)));

        assertEquals(NumberRuleDisabledException.CODE, exception.getCode());
        verifyNoInteractions(recordDao, detailDao);
    }

    // ---------------------------------------------------------------- 取号主链路

    @Test
    void acquireByModelInitializesCounterFromStart() {
        when(ruleDao.selectByModel("SysProcessExample", "fieldCode"))
                .thenReturn(List.of(storedRule(classicConfig(), true)));
        stubHappyPath(1);

        SysNumberAcquireResultDo result =
                service.acquireByModel("SysProcessExample", "fieldCode", "D".repeat(32));

        assertFalse(result.isReused());
        assertEquals("CG" + todayPeriod() + "001", result.getCode(), "首号按 start 初始化：前缀+日期+补零流水");
        assertNotNull(result.getRecordDetailId());

        // 计数行：作用域 = 规则 field_id，周期键按取号时刻派生，起始流水号 = start
        ArgumentCaptor<SysNumberRecord> counterCaptor = ArgumentCaptor.forClass(SysNumberRecord.class);
        verify(recordDao).upsertSerial(counterCaptor.capture());
        SysNumberRecord counter = counterCaptor.getValue();
        assertEquals(SCOPE, counter.getFieldScopeKey());
        assertEquals(SCOPE, counter.getFieldRuleId(), "固定接入计数行回填 field_rule_id");
        assertEquals("ci-seq", counter.getFieldSerialId());
        assertEquals(todayPeriod(), counter.getFieldPeriod(), "DAY 周期键 = yyyyMMdd");
        assertEquals(1, counter.getFieldSerialNo());

        // 明细行：字段齐全
        ArgumentCaptor<SysNumberRecordDetail> detailCaptor = ArgumentCaptor.forClass(SysNumberRecordDetail.class);
        verify(detailDao).insert(detailCaptor.capture());
        SysNumberRecordDetail detail = detailCaptor.getValue();
        assertEquals(SCOPE, detail.getFieldScopeKey());
        assertEquals(todayPeriod(), detail.getFieldPeriod());
        assertEquals("ci-seq", detail.getFieldSerialId());
        assertEquals(1, detail.getFieldSerialNo());
        assertEquals("SysProcessExample", detail.getFieldModelName());
        assertEquals("D".repeat(32), detail.getFieldModelId());
        assertEquals("CG" + todayPeriod() + "001", detail.getFieldCode());
        assertNotNull(detail.getFieldRecordTime());
        assertNotNull(detail.getFieldId());
    }

    @Test
    void acquireByModelIncrementsExistingCounter() {
        when(ruleDao.selectByModel("SysProcessExample", "fieldCode"))
                .thenReturn(List.of(storedRule(classicConfig(), true)));
        stubHappyPath(6);

        SysNumberAcquireResultDo result =
                service.acquireByModel("SysProcessExample", "fieldCode", "D".repeat(32));

        assertEquals("CG" + todayPeriod() + "006", result.getCode());
        assertFalse(result.isReused());
    }

    @Test
    void acquireByConfigUsesGivenScopeWithoutRuleId() {
        stubHappyPath(1);

        SysNumberAcquireResultDo result =
                service.acquireByConfig(classicConfig(), "ErpContract:CAT-1", "ErpContract", "D".repeat(32));

        assertEquals("CG" + todayPeriod() + "001", result.getCode());
        ArgumentCaptor<SysNumberRecord> counterCaptor = ArgumentCaptor.forClass(SysNumberRecord.class);
        verify(recordDao).upsertSerial(counterCaptor.capture());
        assertEquals("ErpContract:CAT-1", counterCaptor.getValue().getFieldScopeKey());
        assertNull(counterCaptor.getValue().getFieldRuleId(), "非固定接入 field_rule_id 空");

        ArgumentCaptor<SysNumberRecordDetail> detailCaptor = ArgumentCaptor.forClass(SysNumberRecordDetail.class);
        verify(detailDao).insert(detailCaptor.capture());
        assertEquals("ErpContract:CAT-1", detailCaptor.getValue().getFieldScopeKey());
        assertEquals("ErpContract", detailCaptor.getValue().getFieldModelName());
    }

    // ---------------------------------------------------------------- 幂等

    @Test
    void acquireByIdempotentHitReturnsOriginalWithoutAdvancing() {
        SysNumberRecordDetail existing = new SysNumberRecordDetail();
        existing.setFieldId("E".repeat(32));
        existing.setFieldCode("CG20261112-005");
        when(detailDao.selectValidByScopeAndModel(anyString(), anyString())).thenReturn(existing);

        SysNumberAcquireResultDo result =
                service.acquireByConfig(classicConfig(), SCOPE, "ErpContract", "D".repeat(32));

        assertTrue(result.isReused());
        assertEquals("CG20261112-005", result.getCode(), "幂等命中返回原编号");
        assertEquals("E".repeat(32), result.getRecordDetailId());
        verify(recordDao, never()).upsertSerial(any(SysNumberRecord.class));
        verify(recordDao, never()).sealOtherPeriods(anyString(), anyString(), anyString());
        verify(detailDao, never()).insert(any(SysNumberRecordDetail.class));
    }

    @Test
    void acquireDetectsConcurrentDuplicateDocumentAfterLockWait() {
        // 预检未命中，计数器行锁等待期间并发同单取号已提交：二次确认（锁定读）命中即回滚
        SysNumberRecordDetail concurrent = new SysNumberRecordDetail();
        concurrent.setFieldId("E".repeat(32));
        concurrent.setFieldCode("CG20261112-005");
        when(detailDao.selectValidByScopeAndModel(anyString(), anyString())).thenReturn(null);
        when(detailDao.lockValidByScopeAndModel(anyString(), anyString())).thenReturn(concurrent);
        when(recordDao.sealOtherPeriods(anyString(), anyString(), anyString())).thenReturn(0);
        when(recordDao.upsertSerial(any(SysNumberRecord.class))).thenReturn(1);
        when(recordDao.selectCurrentSerialNo(anyString(), anyString(), anyString())).thenReturn(6);

        assertThrows(IllegalStateException.class,
                () -> service.acquireByConfig(classicConfig(), SCOPE, "ErpContract", "D".repeat(32)));

        verify(detailDao, never()).insert(any(SysNumberRecordDetail.class));
    }

    // ---------------------------------------------------------------- C6 容量

    @Test
    void acquireBeyondSerialCapacityThrowsExhaustedWithoutDetail() {
        when(ruleDao.selectByModel("SysProcessExample", "fieldCode"))
                .thenReturn(List.of(storedRule(classicConfig(), true)));
        when(detailDao.selectValidByScopeAndModel(anyString(), anyString())).thenReturn(null);
        when(recordDao.sealOtherPeriods(anyString(), anyString(), anyString())).thenReturn(0);
        when(recordDao.upsertSerial(any(SysNumberRecord.class))).thenReturn(1);
        // 计数器已推进到 1000（length=3 容量 999），C6 取号失败回滚
        when(recordDao.selectCurrentSerialNo(anyString(), anyString(), anyString())).thenReturn(1000);

        NumberSerialExhaustedException exception = assertThrows(NumberSerialExhaustedException.class,
                () -> service.acquireByModel("SysProcessExample", "fieldCode", "D".repeat(32)));

        assertEquals(NumberSerialExhaustedException.CODE, exception.getCode());
        verify(detailDao, never()).insert(any(SysNumberRecordDetail.class));
        verify(detailDao, never()).lockValidByScopeAndModel(anyString(), anyString());
    }

    @Test
    void seqCapacityMatchesPowerOfTenMinusOne() {
        Item seq = item("ci-seq", SysNumberRuleConfigDo.TYPE_SEQ, "流水号");
        seq.setLength(3);
        assertEquals(999, service.seqCapacity(seq));
        seq.setLength(1);
        assertEquals(9, service.seqCapacity(seq));
        seq.setLength(9);
        assertEquals(999_999_999, service.seqCapacity(seq));
    }

    // ---------------------------------------------------------------- 渲染引擎

    @Test
    void renderCombinesTextDatetimeAndSeq() {
        assertEquals("CG20261112001", service.render(classicConfig(), 1, RECORD_TIME));
        assertEquals("CG20261112999", service.render(classicConfig(), 999, RECORD_TIME));
    }

    @Test
    void renderSeqWithoutPadZeroOutputsRawValue() {
        Item seq = item("ci-seq", SysNumberRuleConfigDo.TYPE_SEQ, "流水号");
        seq.setStart(1);
        seq.setLength(4);
        seq.setPadZero(false);
        seq.setResetPeriod(SysNumberRuleConfigDo.RESET_NONE);
        SysNumberRuleConfigDo config = new SysNumberRuleConfigDo();
        config.setItems(new ArrayList<>(List.of(seq)));

        assertEquals("7", service.render(config, 7, RECORD_TIME), "padZero=false 不补零、不截断");
    }

    @Test
    void renderRandomNumericOnlyStaysInBoundsAndWidth() {
        Item random = item("ci-r", SysNumberRuleConfigDo.TYPE_RANDOM, "随机码");
        random.setMin(100);
        random.setMax(999);
        random.setLength(4);
        random.setPadZero(true);
        random.setNumericOnly(true);
        SysNumberRuleConfigDo config = new SysNumberRuleConfigDo();
        config.setItems(new ArrayList<>(List.of(random)));

        for (int i = 0; i < 100; i++) {
            String code = service.render(config, 1, RECORD_TIME);
            assertEquals(4, code.length(), "numericOnly + padZero 按 length 补零");
            int value = Integer.parseInt(code);
            assertTrue(value >= 100 && value <= 999, "随机值落在 [min, max]：" + value);
        }
    }

    @Test
    void renderRandomAlphanumericUsesFixedLengthAndCharset() {
        Item random = item("ci-r", SysNumberRuleConfigDo.TYPE_RANDOM, "随机码");
        random.setLength(8);
        random.setNumericOnly(false);
        SysNumberRuleConfigDo config = new SysNumberRuleConfigDo();
        config.setItems(new ArrayList<>(List.of(random)));

        String code = service.render(config, 1, RECORD_TIME);
        assertEquals(8, code.length());
        assertTrue(code.chars().allMatch(c -> Character.isLetterOrDigit(c) && c < 128),
                "numericOnly=false 字符集 A-Za-z0-9：" + code);
    }

    @Test
    void derivePeriodKeysMatchPlanExamples() {
        assertEquals("ALL", service.derivePeriodKey(SysNumberRuleConfigDo.RESET_NONE, RECORD_TIME));
        assertEquals("20261112", service.derivePeriodKey(SysNumberRuleConfigDo.RESET_DAY, RECORD_TIME));
        assertEquals("2026W46", service.derivePeriodKey(SysNumberRuleConfigDo.RESET_WEEK, RECORD_TIME),
                "WEEK 按 ISO-8601 周年编号（A2，2026-11-12 为第 46 周）");
        assertEquals("202611", service.derivePeriodKey(SysNumberRuleConfigDo.RESET_MONTH, RECORD_TIME));
        assertEquals("2026", service.derivePeriodKey(SysNumberRuleConfigDo.RESET_YEAR, RECORD_TIME));
    }

    @Test
    void deriveWeekPeriodSpansYearBoundary() {
        // 2027-01-01（周五）属 2026 年第 53 个 ISO 周，验证周年取 WEEK_BASED_YEAR 而非自然年
        Date crossYear = Date.from(
                LocalDateTime.of(2027, 1, 1, 0, 0, 0).atZone(ZoneId.systemDefault()).toInstant());
        assertEquals("2026W53", service.derivePeriodKey(SysNumberRuleConfigDo.RESET_WEEK, crossYear));
    }

    // ---------------------------------------------------------------- 防御校验

    @Test
    void acquireByConfigRejectsInvalidConfigWithoutTouchingDb() {
        // 缺 seq（C1）的纯文本规则
        Item text = item("ci-text", SysNumberRuleConfigDo.TYPE_TEXT, "前缀");
        text.setText("CG");
        SysNumberRuleConfigDo config = new SysNumberRuleConfigDo();
        config.setItems(new ArrayList<>(List.of(text)));

        NumberRuleInvalidException exception = assertThrows(NumberRuleInvalidException.class,
                () -> service.acquireByConfig(config, SCOPE, "ErpContract", "D".repeat(32)));

        assertEquals(NumberRuleInvalidException.CODE, exception.getCode());
        verifyNoInteractions(recordDao, detailDao);
    }

    @Test
    void acquireRejectsRenderedCodeBeyondMaxLength() {
        // C5 估算 56 通过（text 32 + datetime 14 + seq 9 + random 1），实际渲染 65——
        // random max 达 int 上限 2147483647（10 位）超出 length 1 估算（C0 校验盲区），
        // 取号侧组装后二次兜底校验（R4）拦截；seq 取值 999999999 恰在 length=9 容量内
        Item text = item("ci-text", SysNumberRuleConfigDo.TYPE_TEXT, "前缀");
        text.setText("T".repeat(32));
        Item datetime = item("ci-date", SysNumberRuleConfigDo.TYPE_DATETIME, "日期");
        datetime.setFormat("yyyyMMddHHmmss");
        Item seq = item("ci-seq", SysNumberRuleConfigDo.TYPE_SEQ, "流水号");
        seq.setStart(1);
        seq.setLength(9);
        seq.setPadZero(true);
        seq.setResetPeriod(SysNumberRuleConfigDo.RESET_YEAR);
        Item random = item("ci-r", SysNumberRuleConfigDo.TYPE_RANDOM, "随机码");
        random.setMin(2147483647);
        random.setMax(2147483647);
        random.setLength(1);
        random.setPadZero(true);
        random.setNumericOnly(true);
        SysNumberRuleConfigDo config = new SysNumberRuleConfigDo();
        config.setItems(new ArrayList<>(List.of(text, datetime, seq, random)));

        when(detailDao.selectValidByScopeAndModel(anyString(), anyString())).thenReturn(null);
        when(recordDao.sealOtherPeriods(anyString(), anyString(), anyString())).thenReturn(0);
        when(recordDao.upsertSerial(any(SysNumberRecord.class))).thenReturn(1);
        when(recordDao.selectCurrentSerialNo(anyString(), anyString(), anyString())).thenReturn(999_999_999);

        NumberRuleInvalidException exception = assertThrows(NumberRuleInvalidException.class,
                () -> service.acquireByConfig(config, SCOPE, "ErpContract", "D".repeat(32)));

        assertTrue(exception.getMessage().contains("超过上限"), "实际 65 位超 64 应报 C5 兜底：" + exception.getMessage());
        verify(detailDao, never()).insert(any(SysNumberRecordDetail.class));
    }

    // ---------------------------------------------------------------- 并发编排

    @Test
    void concurrentAcquireAssignsDistinctSerials() throws Exception {
        // seq 无周期重置（period=ALL），length=4 容量 9999
        Item text = item("ci-text", SysNumberRuleConfigDo.TYPE_TEXT, "前缀");
        text.setText("T");
        Item seq = item("ci-seq", SysNumberRuleConfigDo.TYPE_SEQ, "流水号");
        seq.setStart(1);
        seq.setLength(4);
        seq.setPadZero(false);
        seq.setResetPeriod(SysNumberRuleConfigDo.RESET_NONE);
        SysNumberRuleConfigDo config = new SysNumberRuleConfigDo();
        config.setItems(new ArrayList<>(List.of(text, seq)));

        // AtomicInteger 模拟计数器 ODKU 原子推进（uk + 行锁的 DB 侧行为由 SQL 保证）
        AtomicInteger counter = new AtomicInteger(0);
        when(detailDao.selectValidByScopeAndModel(anyString(), anyString())).thenReturn(null);
        when(detailDao.lockValidByScopeAndModel(anyString(), anyString())).thenReturn(null);
        when(recordDao.sealOtherPeriods(anyString(), anyString(), anyString())).thenReturn(0);
        when(recordDao.upsertSerial(any(SysNumberRecord.class))).thenReturn(1);
        when(recordDao.selectCurrentSerialNo(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> counter.incrementAndGet());
        ConcurrentLinkedQueue<SysNumberRecordDetail> inserted = new ConcurrentLinkedQueue<>();
        when(detailDao.insert(any(SysNumberRecordDetail.class))).thenAnswer(invocation -> {
            inserted.add(invocation.getArgument(0));
            return 1;
        });

        int threads = 32;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<SysNumberAcquireResultDo> results = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < threads; i++) {
            String documentId = String.format("%032d", i + 1);
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    results.add(service.acquireByConfig(config, SCOPE, "ErpContract", documentId));
                } catch (Exception e) {
                    // 不应有异常；由结果数量断言暴露
                }
            });
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        assertEquals(threads, results.size(), "全部取号成功");
        Set<Integer> serials = inserted.stream()
                .map(SysNumberRecordDetail::getFieldSerialNo)
                .collect(Collectors.toSet());
        assertEquals(threads, serials.size(), "流水号互异（不重号）");
        assertEquals(threads, new HashSet<>(results).size(), "编号互异");
        assertEquals(threads, counter.get(), "计数推进次数与取号次数一致（不跳号）");
        verify(recordDao, times(threads)).upsertSerial(any(SysNumberRecord.class));
    }
}

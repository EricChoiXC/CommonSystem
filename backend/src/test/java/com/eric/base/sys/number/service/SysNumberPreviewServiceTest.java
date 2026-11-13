package com.eric.base.sys.number.service;

import com.eric.base.sys.number.dao.SysNumberRecordDao;
import com.eric.base.sys.number.dao.SysNumberRecordDetailDao;
import com.eric.base.sys.number.dao.SysNumberRuleDao;
import com.eric.base.sys.number.model.SysNumberPreviewVo;
import com.eric.base.sys.number.model.SysNumberRecord;
import com.eric.base.sys.number.model.SysNumberRule;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo.Item;
import com.eric.base.sys.number.model.SysNumberRuleErrorDo;
import com.eric.base.sys.number.request.SysNumberPreviewRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 编号预览渲染服务测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆盖任务 NB3-BE-004 验收标准（Mockito 单测，渲染引擎复用真实
 *              {@link SysNumberGenerateService}，R3 预览与取号同引擎）：
 *              <ul>
 *              <li>已保存规则入口：按当前计数顺延渲染样例（scope = 规则 field_id）；</li>
 *              <li>未保存 JSON 入口：无计数行从 start 起（scope 传 null）；</li>
 *              <li>只读不落库：重复调用同输入结果一致，recordDao 仅 selectCurrentSerialNo
 *                  被调用，upsertSerial / sealOtherPeriods 等写方法零调用；</li>
 *              <li>count 归一：缺省 3 / 上限 10 / 下限 1；</li>
 *              <li>C1~C5 非法规则：errors 就地返回且 samples 空，不抛异常；</li>
 *              <li>C6 越界：errors 含 C6、样例截断到容量内、恰好容量边界不提示；</li>
 *              <li>封存行 / 无行（selectCurrentSerialNo 返回 null）视同从 start 重开
 *                  （与取号侧口径一致）；</li>
 *              <li>fieldId 与 rule 均空抛 IllegalArgumentException。</li>
 *              </ul>
 */
class SysNumberPreviewServiceTest {

    /** 已保存规则 ID（同时作为预览作用域 field_scope_key） */
    private static final String FIELD_ID = "R".repeat(32);

    private SysNumberRuleService ruleService;

    private SysNumberRecordDao recordDao;

    private SysNumberPreviewService service;

    @BeforeEach
    void setUp() {
        ruleService = mock(SysNumberRuleService.class);
        recordDao = mock(SysNumberRecordDao.class);
        // 渲染引擎复用真实 SysNumberGenerateService（R3），其 Dao 依赖以 mock 隔离
        SysNumberRecordDetailDao detailDao = mock(SysNumberRecordDetailDao.class);
        SysNumberRuleDao ruleDao = mock(SysNumberRuleDao.class);
        SysNumberGenerateService generateService =
                new SysNumberGenerateService(recordDao, detailDao, ruleDao, new SysNumberRuleValidator());
        service = new SysNumberPreviewService(ruleService, recordDao, generateService, new SysNumberRuleValidator());
    }

    // ---------------------------------------------------------------- 构造辅助

    private Item item(String id, String type, String name) {
        Item item = new Item();
        item.setId(id);
        item.setType(type);
        item.setName(name);
        return item;
    }

    /** 经典三段规则：前缀 + 日期 + 流水（方案 §2.1 示例形态，seq start=1 length=3 补零） */
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

    /** load 返回形态的已保存规则（fieldRule 已解析为配置对象） */
    private SysNumberRule loadedRule(SysNumberRuleConfigDo config) {
        SysNumberRule rule = new SysNumberRule();
        rule.setFieldId(FIELD_ID);
        rule.setFieldCode("CG-RULE");
        rule.setFieldName("采购编号规则");
        rule.setFieldModelId("process-example-code");
        rule.setFieldModelName("SysProcessExample");
        rule.setFieldModelField("fieldCode");
        rule.setFieldEnable(Boolean.TRUE);
        rule.setFieldRule(config);
        return rule;
    }

    private SysNumberPreviewRequest request(String fieldId, SysNumberRuleConfigDo rule, Integer count) {
        SysNumberPreviewRequest request = new SysNumberPreviewRequest();
        request.setFieldId(fieldId);
        request.setRule(rule);
        request.setCount(count);
        return request;
    }

    /**
     * 当日周期键（DAY=yyyyMMdd）：预览入口取号时刻为服务运行时 new Date()，
     * 断言按当日动态计算（对齐 SysNumberGenerateServiceTest 先例）。
     */
    private static String todayPeriod() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private static String padded(int serialNo) {
        return String.format(Locale.ROOT, "%03d", serialNo);
    }

    // ---------------------------------------------------------------- 已保存规则入口

    @Test
    void savedRuleRendersFollowUpSamplesFromCurrentCounter() {
        when(ruleService.load(FIELD_ID)).thenReturn(loadedRule(classicConfig()));
        when(recordDao.selectCurrentSerialNo(anyString(), anyString(), anyString())).thenReturn(6);

        SysNumberPreviewVo vo = service.render(request(FIELD_ID, null, null));

        assertEquals(List.of("CG" + todayPeriod() + "007", "CG" + todayPeriod() + "008",
                "CG" + todayPeriod() + "009"), vo.getSamples(), "按当前计数顺延模拟：6 → 7/8/9");
        assertTrue(vo.getErrors().isEmpty(), "校验通过且容量充足时无错误提示");
        // 作用域 = 规则 field_id，周期键 = 当前 DAY 周期（与取号侧口径一致 R3）
        verify(ruleService).load(FIELD_ID);
        verify(recordDao).selectCurrentSerialNo(eq(FIELD_ID), eq("ci-seq"), eq(todayPeriod()));
    }

    @Test
    void sealedOrMissingCounterRestartsFromStartForSavedRule() {
        // 封存行 / 无行：selectCurrentSerialNo 返回 null（封存行周期键不同不会命中），
        // 视为 start-1，样例从 start 重开（与取号侧「封存行按 start 重开」口径一致）
        when(ruleService.load(FIELD_ID)).thenReturn(loadedRule(classicConfig()));
        when(recordDao.selectCurrentSerialNo(anyString(), anyString(), anyString())).thenReturn(null);

        SysNumberPreviewVo vo = service.render(request(FIELD_ID, null, 3));

        assertEquals(List.of("CG" + todayPeriod() + "001", "CG" + todayPeriod() + "002",
                "CG" + todayPeriod() + "003"), vo.getSamples(), "计数为空视为 start-1，从 start 起渲染");
    }

    // ---------------------------------------------------------------- 未保存 JSON 入口

    @Test
    void unsavedRuleStartsFromStartWithoutCounter() {
        when(recordDao.selectCurrentSerialNo(any(), any(), any())).thenReturn(null);

        SysNumberPreviewVo vo = service.render(request(null, classicConfig(), 3));

        assertEquals(List.of("CG" + todayPeriod() + "001", "CG" + todayPeriod() + "002",
                "CG" + todayPeriod() + "003"), vo.getSamples(), "未保存规则无作用域计数，从 start 起");
        // 未保存 config scope 传 null（无计数行 → 从 start 起）
        verify(recordDao).selectCurrentSerialNo(isNull(), eq("ci-seq"), eq(todayPeriod()));
        verify(ruleService, never()).load(anyString());
    }

    @Test
    void fieldIdWinsWhenBothInputsPresent() {
        when(ruleService.load(FIELD_ID)).thenReturn(loadedRule(classicConfig()));
        when(recordDao.selectCurrentSerialNo(anyString(), anyString(), anyString())).thenReturn(1);

        // rule 传另一套配置（无前缀纯流水），fieldId 优先按已保存规则渲染
        Item seq = item("ci-other", SysNumberRuleConfigDo.TYPE_SEQ, "流水号");
        seq.setStart(1);
        seq.setLength(3);
        seq.setPadZero(true);
        seq.setResetPeriod(SysNumberRuleConfigDo.RESET_NONE);
        SysNumberRuleConfigDo otherConfig = new SysNumberRuleConfigDo();
        otherConfig.setItems(new ArrayList<>(List.of(seq)));

        SysNumberPreviewVo vo = service.render(request(FIELD_ID, otherConfig, 1));

        assertEquals(List.of("CG" + todayPeriod() + "002"), vo.getSamples(),
                "fieldId 与 rule 同时传入以 fieldId 优先");
        verify(recordDao).selectCurrentSerialNo(eq(FIELD_ID), eq("ci-seq"), eq(todayPeriod()));
    }

    // ---------------------------------------------------------------- 只读不落库

    @Test
    void repeatedRenderIsDeterministicAndNeverWrites() {
        when(ruleService.load(FIELD_ID)).thenReturn(loadedRule(classicConfig()));
        when(recordDao.selectCurrentSerialNo(anyString(), anyString(), anyString())).thenReturn(6);

        SysNumberPreviewVo first = service.render(request(FIELD_ID, null, 3));
        SysNumberPreviewVo second = service.render(request(FIELD_ID, null, 3));

        assertEquals(first.getSamples(), second.getSamples(), "同输入重复调用结果一致");
        // recordDao 仅 selectCurrentSerialNo 被调用，任何写方法零调用（预览绝不落库）
        verify(recordDao, times(2)).selectCurrentSerialNo(anyString(), anyString(), anyString());
        verify(recordDao, never()).upsertSerial(any(SysNumberRecord.class));
        verify(recordDao, never()).sealOtherPeriods(anyString(), anyString(), anyString());
        verifyNoMoreInteractions(recordDao);
        // 不复用会写库的 acquire 链路：明细 Dao 不被间接触碰（真实 GenerateService 仅被调纯渲染方法）
        verify(ruleService, times(2)).load(FIELD_ID);
        verifyNoMoreInteractions(ruleService);
    }

    // ---------------------------------------------------------------- count 归一

    @Test
    void countNormalizesDefaultCapAndFloor() {
        Item seq = item("ci-seq", SysNumberRuleConfigDo.TYPE_SEQ, "流水号");
        seq.setStart(1);
        seq.setLength(4);
        seq.setPadZero(true);
        seq.setResetPeriod(SysNumberRuleConfigDo.RESET_NONE);
        SysNumberRuleConfigDo config = new SysNumberRuleConfigDo();
        config.setItems(new ArrayList<>(List.of(seq)));

        assertEquals(3, service.render(request(null, config, null)).getSamples().size(), "count 缺省 3");
        assertEquals(10, service.render(request(null, config, 99)).getSamples().size(), "count 超上限归一为 10");
        assertEquals(1, service.render(request(null, config, 0)).getSamples().size(), "count 小于 1 归一为 1");
        assertEquals(1, service.render(request(null, config, -5)).getSamples().size(), "count 负数归一为 1");

        List<String> capped = service.render(request(null, config, 99)).getSamples();
        assertEquals("0001", capped.get(0), "上限样例自 start 顺延");
        assertEquals("0010", capped.get(9), "上限样例止于第 10 个");
    }

    // ---------------------------------------------------------------- C1~C5 校验就地返回

    @Test
    void invalidRuleReturnsErrorsWithEmptySamplesWithoutThrowing() {
        // 缺 seq（C1）的纯文本规则：预览不抛异常，errors 就地返回
        Item text = item("ci-text", SysNumberRuleConfigDo.TYPE_TEXT, "前缀");
        text.setText("CG");
        SysNumberRuleConfigDo config = new SysNumberRuleConfigDo();
        config.setItems(new ArrayList<>(List.of(text)));

        SysNumberPreviewVo vo = service.render(request(null, config, 3));

        assertTrue(vo.getSamples().isEmpty(), "校验不过样例为空");
        assertEquals(1, vo.getErrors().size());
        assertEquals("C1", vo.getErrors().get(0).getCode());
        // 校验不过不触碰计数读取（只读零调用）
        verifyNoInteractions(recordDao);
    }

    @Test
    void savedRuleWithInvalidConfigReturnsErrorsWithoutThrowing() {
        // 已保存规则入口加载到非法配置（缺 seq 的 C1 规则）：就地返回 errors 不抛异常
        Item text = item("ci-text", SysNumberRuleConfigDo.TYPE_TEXT, "前缀");
        text.setText("CG");
        SysNumberRuleConfigDo invalid = new SysNumberRuleConfigDo();
        invalid.setItems(new ArrayList<>(List.of(text)));
        when(ruleService.load(FIELD_ID)).thenReturn(loadedRule(invalid));

        SysNumberPreviewVo vo = service.render(request(FIELD_ID, null, null));

        assertTrue(vo.getSamples().isEmpty(), "校验不过样例为空");
        assertEquals(1, vo.getErrors().size());
        assertEquals("C1", vo.getErrors().get(0).getCode());
        verify(ruleService).load(FIELD_ID);
        // 校验不过不触碰计数读取（只读零调用）
        verifyNoInteractions(recordDao);
    }

    // ---------------------------------------------------------------- C6 容量边界

    @Test
    void c6OverflowTruncatesSamplesAndAppendsHint() {
        when(ruleService.load(FIELD_ID)).thenReturn(loadedRule(classicConfig()));
        // length=3 容量 999，当前已用 997：顺延 5 个样例仅 998/999 在容量内
        when(recordDao.selectCurrentSerialNo(anyString(), anyString(), anyString())).thenReturn(997);

        SysNumberPreviewVo vo = service.render(request(FIELD_ID, null, 5));

        assertEquals(List.of("CG" + todayPeriod() + "998", "CG" + todayPeriod() + "999"),
                vo.getSamples(), "样例只渲染到容量内的部分");
        assertEquals(1, vo.getErrors().size());
        SysNumberRuleErrorDo error = vo.getErrors().get(0);
        assertEquals("C6", error.getCode());
        assertEquals("ci-seq", error.getItemId());
        assertTrue(error.getMessage().contains("自第 3 个样例起超出容量 3 位（上限 999）"),
                "C6 提示含越界起点与容量上限：" + error.getMessage());
        assertTrue(error.getMessage().contains("CRM_NUMBER_SERIAL_EXHAUSTED"),
                "C6 提示注明正式取号将报的错误码：" + error.getMessage());
    }

    @Test
    void c6ExactCapacityBoundaryDoesNotHint() {
        when(ruleService.load(FIELD_ID)).thenReturn(loadedRule(classicConfig()));
        // 恰好用满容量（997 + 2 = 999 = 上限）：不提示 C6
        when(recordDao.selectCurrentSerialNo(anyString(), anyString(), anyString())).thenReturn(997);

        SysNumberPreviewVo vo = service.render(request(FIELD_ID, null, 2));

        assertEquals(List.of("CG" + todayPeriod() + "998", "CG" + todayPeriod() + "999"), vo.getSamples());
        assertTrue(vo.getErrors().isEmpty(), "恰好容量边界不提示 C6");
    }

    // ---------------------------------------------------------------- 入参守卫

    @Test
    void bothFieldIdAndRuleEmptyThrowsIllegalArgument() {
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> service.render(request(null, null, 3)), "两者都空抛 BAD_REQUEST 语义异常");
        assertTrue(empty.getMessage().contains("fieldId 与 rule 至少传一个"));
        assertThrows(IllegalArgumentException.class, () -> service.render(null), "请求体为空一并拦截");
    }
}

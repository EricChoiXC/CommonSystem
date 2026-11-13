package com.eric.base.sys.number.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.number.dao.SysNumberRecordDetailDao;
import com.eric.base.sys.number.dao.SysNumberRuleDao;
import com.eric.base.sys.number.model.SysNumberModelDo;
import com.eric.base.sys.number.model.SysNumberRule;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo.Item;
import com.eric.base.sys.number.request.SysNumberRuleQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 编号规则维护服务测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆盖任务 NB3-BE-002 验收标准（Mockito 纯单测，校验组件用真实
 *              SysNumberRuleValidator，与 SysNumberGenerateServiceTest 风格一致）：
 *              <ul>
 *              <li>save 成功链：校验 → 查重 → 接入项存在性 → 默认启用 →
 *                  同事务停用同接入项其他规则（D1）→ 收尾自检（R2），
 *                  LinkedHashMap 形态的 fieldRule 经 convertValue 归一；</li>
 *              <li>save 拒绝：编码重复（D4）/ 接入项不存在（R6）/ 非法规则（C1）；</li>
 *              <li>update：C7 seq 项 id 变更被拒（D5）；成功时模型三字段与启用态
 *                  保持库值（D3，不受理变更）；接入项失效被拒；规则不存在被拒；</li>
 *              <li>enable：启用切换（停用他人 + 置自身 + 自检）/ 停用仅自身
 *                  （不校验规则、不联动）；非法规则启用被拒；fieldEnable 必填；</li>
 *              <li>delete：有明细拒绝（D9 IN_USE）/ 无明细删除；</li>
 *              <li>load：field_rule 解析回填 / 不存在 NOT_FOUND / JSON 损坏非法；</li>
 *              <li>list / count：条件归一与分页委托；</li>
 *              <li>validateConfig 直通；modelOptions 委托缓存。</li>
 *              </ul>
 */
class SysNumberRuleServiceTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final String RULE_ID = "R".repeat(32);

    private static final String MODEL_ID = "process-example-code";

    private SysNumberRuleDao ruleDao;
    private SysNumberRecordDetailDao detailDao;
    private SysNumberModelCacheService modelCacheService;
    private SysNumberRuleService service;

    @BeforeEach
    void setUp() {
        ruleDao = mock(SysNumberRuleDao.class);
        detailDao = mock(SysNumberRecordDetailDao.class);
        modelCacheService = mock(SysNumberModelCacheService.class);
        service = new SysNumberRuleService(ruleDao, detailDao, modelCacheService, new SysNumberRuleValidator());
    }

    // ---------------------------------------------------------------- 构造辅助

    private Item item(String id, String type, String name) {
        Item item = new Item();
        item.setId(id);
        item.setType(type);
        item.setName(name);
        return item;
    }

    /** 经典三段规则：前缀 + 日期 + 流水（items 索引 2 为 seq 项） */
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
        rule.setFieldId(RULE_ID);
        rule.setFieldCode("CG-RULE");
        rule.setFieldName("采购编号规则");
        rule.setFieldModelId(MODEL_ID);
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

    /** 请求体基形（fieldId 经 getInitID 预取） */
    private SysNumberRule baseBody(Object fieldRule) {
        SysNumberRule body = new SysNumberRule();
        body.setFieldId(RULE_ID);
        body.setFieldCode("CG-RULE");
        body.setFieldName("采购编号规则");
        body.setFieldModelId(MODEL_ID);
        body.setFieldRule(fieldRule);
        return body;
    }

    /** 模拟请求体经 Jackson 反序列化产生的 LinkedHashMap 形态 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> rawRuleMap(SysNumberRuleConfigDo config) throws Exception {
        return JSON_MAPPER.readValue(JSON_MAPPER.writeValueAsString(config), Map.class);
    }

    private SysNumberModelDo modelOption() {
        SysNumberModelDo option = new SysNumberModelDo();
        option.setKey(MODEL_ID);
        option.setName("流程示例编号");
        option.setModelName("SysProcessExample");
        option.setModelField("fieldCode");
        return option;
    }

    // ---------------------------------------------------------------- save 成功链

    @Test
    void savePersistsEnabledRuleAndSwitchesEnabledRule() throws Exception {
        SysNumberRuleConfigDo config = classicConfig();
        // fieldRule 为 Object：JSON 反序列化产物为 LinkedHashMap，经 convertValue 归一
        SysNumberRule body = baseBody(rawRuleMap(config));
        // 入参携带的模型名称字段应被缓存带出值覆盖
        body.setFieldModelName("Hacked");
        body.setFieldModelField("hackedField");

        when(ruleDao.selectByCode("CG-RULE")).thenReturn(null);
        when(modelCacheService.containsModel(MODEL_ID)).thenReturn(true);
        when(modelCacheService.getModel(MODEL_ID)).thenReturn(modelOption());
        when(ruleDao.insert(any(SysNumberRule.class))).thenReturn(1);
        when(ruleDao.disableOtherEnabledByModel(any(), any(), any(), any())).thenReturn(2);
        when(ruleDao.countEnabledByModel(MODEL_ID)).thenReturn(1L);
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule(config, true));

        SysNumberRule result = service.save(body);

        // 落库模型：fieldId 沿用预取值、默认启用、field_rule 归一为 JSON 字符串、
        // 接入项名称字段由缓存带出（不采信请求体）
        ArgumentCaptor<SysNumberRule> insertCaptor = ArgumentCaptor.forClass(SysNumberRule.class);
        verify(ruleDao).insert(insertCaptor.capture());
        SysNumberRule inserted = insertCaptor.getValue();
        assertEquals(RULE_ID, inserted.getFieldId());
        assertEquals("CG-RULE", inserted.getFieldCode());
        assertEquals("采购编号规则", inserted.getFieldName());
        assertEquals(MODEL_ID, inserted.getFieldModelId());
        assertEquals("SysProcessExample", inserted.getFieldModelName(), "field_model_name 由缓存带出");
        assertEquals("fieldCode", inserted.getFieldModelField(), "field_model_field 由缓存带出");
        assertEquals(Boolean.TRUE, inserted.getFieldEnable(), "新建默认启用");
        assertTrue(inserted.getFieldRule() instanceof String, "落库为 JSON 字符串");
        assertTrue(((String) inserted.getFieldRule()).contains("ci-seq"));
        assertNotNull(inserted.getFieldCreateTime());
        assertNotNull(inserted.getFieldUpdateTime());

        // D1 切换式启用：停用同接入项其他已启用规则（排除自身）
        verify(ruleDao).disableOtherEnabledByModel(
                eq(MODEL_ID), eq(RULE_ID), eq(inserted.getFieldUpdaterId()), eq(inserted.getFieldUpdateTime()));
        // R2 收尾自检：同接入项启用计数恰为 1
        verify(ruleDao).countEnabledByModel(MODEL_ID);

        // 返回 load 解析后的实体
        assertTrue(result.getFieldRule() instanceof SysNumberRuleConfigDo);
        assertEquals(Boolean.TRUE, result.getFieldEnable());
    }

    // ---------------------------------------------------------------- save 拒绝分支

    @Test
    void saveRejectsDuplicatedCode() {
        when(ruleDao.selectByCode("CG-RULE")).thenReturn(storedRule(classicConfig(), false));

        NumberRuleDuplicatedException exception = assertThrows(NumberRuleDuplicatedException.class,
                () -> service.save(baseBody(classicConfig())));

        assertEquals(NumberRuleDuplicatedException.CODE, exception.getCode());
        verify(ruleDao, never()).insert(any(SysNumberRule.class));
        verifyNoInteractions(detailDao, modelCacheService);
    }

    @Test
    void saveRejectsUnknownModel() {
        when(ruleDao.selectByCode("CG-RULE")).thenReturn(null);
        when(modelCacheService.containsModel(MODEL_ID)).thenReturn(false);

        NumberModelNotFoundException exception = assertThrows(NumberModelNotFoundException.class,
                () -> service.save(baseBody(classicConfig())));

        assertEquals(NumberModelNotFoundException.CODE, exception.getCode());
        verify(ruleDao, never()).insert(any(SysNumberRule.class));
        verifyNoInteractions(detailDao);
    }

    @Test
    void saveRejectsInvalidConfigBeforeDuplicationCheck() {
        // 缺 seq（C1）的纯文本规则
        Item text = item("ci-text", SysNumberRuleConfigDo.TYPE_TEXT, "前缀");
        text.setText("CG");
        SysNumberRuleConfigDo config = new SysNumberRuleConfigDo();
        config.setItems(new ArrayList<>(List.of(text)));

        NumberRuleInvalidException exception = assertThrows(NumberRuleInvalidException.class,
                () -> service.save(baseBody(config)));

        assertEquals(NumberRuleInvalidException.CODE, exception.getCode());
        verify(ruleDao, never()).selectByCode(anyString());
        verify(ruleDao, never()).insert(any(SysNumberRule.class));
        verifyNoInteractions(modelCacheService, detailDao);
    }

    @Test
    void saveRejectsNullRule() {
        NumberRuleInvalidException exception = assertThrows(NumberRuleInvalidException.class,
                () -> service.save(baseBody(null)));

        assertTrue(exception.getMessage().contains("不能为空"), exception.getMessage());
        verifyNoInteractions(ruleDao, modelCacheService, detailDao);
    }

    // ---------------------------------------------------------------- update

    @Test
    void updateRejectsSeqIdChange() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule(classicConfig(), true));

        // 新规则把 seq 项 id 从 ci-seq 改为 ci-seq2（计数器锚点变更，C7/D5）
        SysNumberRuleConfigDo changed = classicConfig();
        changed.getItems().get(2).setId("ci-seq2");
        SysNumberRule body = baseBody(changed);
        body.setFieldCode("CG-RULE-2");

        NumberRuleInvalidException exception = assertThrows(NumberRuleInvalidException.class,
                () -> service.update(body));

        assertEquals(NumberRuleInvalidException.CODE, exception.getCode());
        assertTrue(exception.getMessage().contains("不可变更"), exception.getMessage());
        verify(ruleDao, never()).selectByCode(anyString());
        verify(ruleDao, never()).update(any(SysNumberRule.class));
    }

    @Test
    void updateKeepsModelStateAndEnableUnchanged() {
        SysNumberRuleConfigDo config = classicConfig();
        SysNumberRule exist = storedRule(config, true);
        when(ruleDao.selectById(RULE_ID)).thenReturn(exist);
        when(ruleDao.selectByCode("CG-RULE-2")).thenReturn(null);
        when(modelCacheService.containsModel(MODEL_ID)).thenReturn(true);
        when(ruleDao.update(any(SysNumberRule.class))).thenReturn(1);

        SysNumberRule body = baseBody(classicConfig());
        body.setFieldCode("CG-RULE-2");
        body.setFieldName("改后的名称");
        // D3：入参携带模型三字段与停用标记，一律不受理
        body.setFieldModelId("hacked-model");
        body.setFieldModelName("Hacked");
        body.setFieldModelField("hackedField");
        body.setFieldEnable(false);

        SysNumberRule result = service.update(body);

        ArgumentCaptor<SysNumberRule> updateCaptor = ArgumentCaptor.forClass(SysNumberRule.class);
        verify(ruleDao).update(updateCaptor.capture());
        SysNumberRule updated = updateCaptor.getValue();
        assertEquals(RULE_ID, updated.getFieldId());
        assertEquals("CG-RULE-2", updated.getFieldCode());
        assertEquals("改后的名称", updated.getFieldName());
        assertTrue(updated.getFieldRule() instanceof String);
        // D3：模型三字段不进入更新模型（以库中旧值为准）
        assertNull(updated.getFieldModelId(), "field_model_id 不受理变更");
        assertNull(updated.getFieldModelName(), "field_model_name 不受理变更");
        assertNull(updated.getFieldModelField(), "field_model_field 不受理变更");
        // 启用态保持不变：忽略入参 fieldEnable
        assertNull(updated.getFieldEnable(), "启用态保持库值");
        assertNotNull(updated.getFieldUpdateTime());

        // 接入项存在性按库中旧值校验（而非入参）
        verify(modelCacheService).containsModel(MODEL_ID);
        verify(modelCacheService, never()).getModel(anyString());

        // 返回库值形态（启用态 / 模型三字段 / 解析后的规则）
        assertEquals(MODEL_ID, result.getFieldModelId());
        assertEquals(Boolean.TRUE, result.getFieldEnable());
        assertTrue(result.getFieldRule() instanceof SysNumberRuleConfigDo);
    }

    @Test
    void updateExcludesSelfFromCodeCheckButRejectsMissingModel() {
        SysNumberRule exist = storedRule(classicConfig(), true);
        when(ruleDao.selectById(RULE_ID)).thenReturn(exist);
        // 同编码规则为自身：查重排除自身后通过；接入项失效（R6）拦截更新
        when(ruleDao.selectByCode("CG-RULE")).thenReturn(exist);
        when(modelCacheService.containsModel(MODEL_ID)).thenReturn(false);

        NumberModelNotFoundException exception = assertThrows(NumberModelNotFoundException.class,
                () -> service.update(baseBody(classicConfig())));

        assertEquals(NumberModelNotFoundException.CODE, exception.getCode());
        verify(ruleDao, never()).update(any(SysNumberRule.class));
    }

    @Test
    void updateRejectsMissingRule() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(null);
        assertThrows(NumberRuleNotFoundException.class, () -> service.update(baseBody(classicConfig())));
        verify(ruleDao, never()).update(any(SysNumberRule.class));
    }

    // ---------------------------------------------------------------- enable

    @Test
    void enableSwitchesOtherEnabledRulesOffAndSelfOn() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule(classicConfig(), false));
        when(modelCacheService.containsModel(MODEL_ID)).thenReturn(true);
        when(ruleDao.disableOtherEnabledByModel(any(), any(), any(), any())).thenReturn(1);
        when(ruleDao.update(any(SysNumberRule.class))).thenReturn(1);
        when(ruleDao.countEnabledByModel(MODEL_ID)).thenReturn(1L);

        service.enable(RULE_ID, true);

        // D1：先停用同接入项其他已启用规则（排除自身），再置自身启用
        verify(ruleDao).disableOtherEnabledByModel(eq(MODEL_ID), eq(RULE_ID), any(), any());
        ArgumentCaptor<SysNumberRule> updateCaptor = ArgumentCaptor.forClass(SysNumberRule.class);
        verify(ruleDao).update(updateCaptor.capture());
        assertEquals(Boolean.TRUE, updateCaptor.getValue().getFieldEnable());
        // R2 收尾自检
        verify(ruleDao).countEnabledByModel(MODEL_ID);
    }

    @Test
    void enableDisableTouchesOnlySelf() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule(classicConfig(), true));
        when(ruleDao.update(any(SysNumberRule.class))).thenReturn(1);

        service.enable(RULE_ID, false);

        ArgumentCaptor<SysNumberRule> updateCaptor = ArgumentCaptor.forClass(SysNumberRule.class);
        verify(ruleDao).update(updateCaptor.capture());
        assertEquals(Boolean.FALSE, updateCaptor.getValue().getFieldEnable());
        verify(ruleDao, never()).disableOtherEnabledByModel(any(), any(), any(), any());
        verify(ruleDao, never()).countEnabledByModel(anyString());
        verifyNoInteractions(modelCacheService);
    }

    @Test
    void enableDisableDoesNotValidateStoredRule() {
        // 停用不解析、不校验规则（含存储态 JSON 损坏的规则）
        SysNumberRule exist = storedRule(classicConfig(), true);
        exist.setFieldRule("{broken");
        when(ruleDao.selectById(RULE_ID)).thenReturn(exist);
        when(ruleDao.update(any(SysNumberRule.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.enable(RULE_ID, false));
        verify(ruleDao).update(any(SysNumberRule.class));
    }

    @Test
    void enableRejectsInvalidStoredRule() {
        // 库中规则缺 items（C1），启用前强制校验拒绝
        SysNumberRule exist = storedRule(classicConfig(), true);
        exist.setFieldRule("{}");
        when(ruleDao.selectById(RULE_ID)).thenReturn(exist);

        NumberRuleInvalidException exception = assertThrows(NumberRuleInvalidException.class,
                () -> service.enable(RULE_ID, true));

        assertEquals(NumberRuleInvalidException.CODE, exception.getCode());
        verify(ruleDao, never()).disableOtherEnabledByModel(any(), any(), any(), any());
        verify(ruleDao, never()).update(any(SysNumberRule.class));
        verify(ruleDao, never()).countEnabledByModel(anyString());
    }

    @Test
    void enableRequiresFieldEnableNonNull() {
        assertThrows(IllegalArgumentException.class, () -> service.enable(RULE_ID, null));
        verifyNoInteractions(ruleDao, modelCacheService, detailDao);
    }

    @Test
    void enableRejectsMissingRule() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(null);
        assertThrows(NumberRuleNotFoundException.class, () -> service.enable(RULE_ID, true));
    }

    // ---------------------------------------------------------------- delete

    @Test
    void deleteRejectsRuleWithDetails() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule(classicConfig(), true));
        when(detailDao.countByScopeKey(RULE_ID)).thenReturn(3L);

        NumberRuleInUseException exception = assertThrows(NumberRuleInUseException.class,
                () -> service.delete(RULE_ID));

        assertEquals(NumberRuleInUseException.CODE, exception.getCode());
        verify(ruleDao, never()).deleteById(anyString());
    }

    @Test
    void deleteRemovesRuleWithoutDetails() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule(classicConfig(), false));
        when(detailDao.countByScopeKey(RULE_ID)).thenReturn(0L);

        service.delete(RULE_ID);

        verify(ruleDao).deleteById(RULE_ID);
    }

    @Test
    void deleteRejectsMissingRule() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(null);
        assertThrows(NumberRuleNotFoundException.class, () -> service.delete(RULE_ID));
        verifyNoInteractions(detailDao);
    }

    // ---------------------------------------------------------------- load

    @Test
    void loadParsesStoredRuleIntoConfig() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(storedRule(classicConfig(), true));

        SysNumberRule result = service.load(RULE_ID);

        assertTrue(result.getFieldRule() instanceof SysNumberRuleConfigDo, "field_rule 解析回填为配置对象");
        SysNumberRuleConfigDo parsed = (SysNumberRuleConfigDo) result.getFieldRule();
        assertEquals(3, parsed.getItems().size());
        assertEquals("ci-seq", parsed.getItems().get(2).getId());
        assertEquals(MODEL_ID, result.getFieldModelId());
    }

    @Test
    void loadRejectsMissingRule() {
        when(ruleDao.selectById(RULE_ID)).thenReturn(null);

        NumberRuleNotFoundException exception = assertThrows(NumberRuleNotFoundException.class,
                () -> service.load(RULE_ID));

        assertEquals(NumberRuleNotFoundException.CODE, exception.getCode());
    }

    @Test
    void loadRejectsCorruptedRuleJson() {
        SysNumberRule exist = storedRule(classicConfig(), true);
        exist.setFieldRule("{broken");
        when(ruleDao.selectById(RULE_ID)).thenReturn(exist);

        NumberRuleInvalidException exception = assertThrows(NumberRuleInvalidException.class,
                () -> service.load(RULE_ID));

        assertTrue(exception.getMessage().contains("field_rule 无法解析"), exception.getMessage());
    }

    // ---------------------------------------------------------------- list / count

    @Test
    void listNormalizesConditionsAndDelegatesPaging() {
        SysNumberRuleQuery query = new SysNumberRuleQuery();
        query.setFieldName(" 订单 ");
        query.setFieldCode(" CG ");
        query.setFieldModelId(" model ");
        query.setFieldModelField(" fieldCode ");
        query.setFieldEnable(true);
        Pages pages = new Pages();
        pages.setPageNo(0);
        pages.setPageSize(0);
        pages.setNeedPage(true);
        when(ruleDao.selectQueryList(any(SysNumberRuleQuery.class), any(Pages.class)))
                .thenReturn(List.of(storedRule(classicConfig(), true)));

        List<SysNumberRule> result = service.list(query, pages);

        assertFalse(result.isEmpty());
        ArgumentCaptor<SysNumberRuleQuery> queryCaptor = ArgumentCaptor.forClass(SysNumberRuleQuery.class);
        ArgumentCaptor<Pages> pagesCaptor = ArgumentCaptor.forClass(Pages.class);
        verify(ruleDao).selectQueryList(queryCaptor.capture(), pagesCaptor.capture());
        assertEquals("订单", queryCaptor.getValue().getFieldName());
        assertEquals("CG", queryCaptor.getValue().getFieldCode());
        assertEquals("model", queryCaptor.getValue().getFieldModelId());
        assertEquals("fieldCode", queryCaptor.getValue().getFieldModelField());
        assertEquals(Boolean.TRUE, queryCaptor.getValue().getFieldEnable());
        assertEquals(1, pagesCaptor.getValue().getPageNo(), "页码小于 1 修正为 1");
        assertEquals(10, pagesCaptor.getValue().getPageSize(), "页大小小于 1 修正为 10");
    }

    @Test
    void countDelegatesNormalizedConditions() {
        when(ruleDao.countQueryList(any(SysNumberRuleQuery.class))).thenReturn(7L);

        long total = service.count(null);

        assertEquals(7L, total);
        ArgumentCaptor<SysNumberRuleQuery> queryCaptor = ArgumentCaptor.forClass(SysNumberRuleQuery.class);
        verify(ruleDao).countQueryList(queryCaptor.capture());
        assertNull(queryCaptor.getValue().getFieldName(), "空条件兜底为空查询对象");
    }

    // ---------------------------------------------------------------- 公共入口

    @Test
    void validateConfigPassesValidRule() {
        assertDoesNotThrow(() -> service.validateConfig(classicConfig()));
    }

    @Test
    void validateConfigRejectsInvalidRule() {
        SysNumberRuleConfigDo config = new SysNumberRuleConfigDo();

        NumberRuleInvalidException exception = assertThrows(NumberRuleInvalidException.class,
                () -> service.validateConfig(config));

        assertEquals(NumberRuleInvalidException.CODE, exception.getCode());
    }

    @Test
    void modelOptionsDelegatesToCache() {
        SysNumberModelDo option = modelOption();
        when(modelCacheService.getModelOptions()).thenReturn(List.of(option));

        List<SysNumberModelDo> options = service.modelOptions();

        assertEquals(List.of(option), options);
        verifyNoInteractions(ruleDao, detailDao);
    }
}

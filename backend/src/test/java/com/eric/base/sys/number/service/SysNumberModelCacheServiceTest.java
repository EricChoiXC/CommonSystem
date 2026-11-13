package com.eric.base.sys.number.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.eric.base.sys.number.dao.SysNumberRuleDao;
import com.eric.base.sys.number.model.SysNumberModelDo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 编号接入项缓存服务测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆盖任务 NB2-BE-001 验收标准：真实 numberRule.yml（classpath 上
 *              properties/sys/process/numberRule.yml）解析后缓存含 process-example-code；
 *              条目解析必填校验（name / modelName / modelField 缺失告警跳过）与同 key
 *              覆盖告警；下拉数据源读取入口（getModelOptions 顺序稳定）；失效接入项
 *              告警（sys_number_rule.field_model_id 不在缓存 → WARN 日志）与查询失败兜底；
 *              缓存读取访问器（getModel / containsModel）边界。
 */
class SysNumberModelCacheServiceTest {

    private SysNumberRuleDao ruleDao;
    private SysNumberModelCacheService service;

    /** 日志捕获器（失效告警验收：人为造失效 field_model_id 输出告警） */
    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        ruleDao = mock(SysNumberRuleDao.class);
        service = new SysNumberModelCacheService(ruleDao);
        serviceLogger = (Logger) LoggerFactory.getLogger(SysNumberModelCacheService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logAppender);
    }

    // ---------------------------------------------------------------- 真实 yml 解析

    @Test
    void syncFromYmlBuildsCacheFromRealNumberRuleYml() {
        // classpath 上存在 properties/sys/process/numberRule.yml（W1 落位），失效告警查询无规则引用
        when(ruleDao.selectDistinctModelIds()).thenReturn(List.of());

        service.syncFromYml();

        SysNumberModelDo model = service.getModel("process-example-code");
        assertNotNull(model, "启动后接入项缓存应含 process-example-code（验收标准）");
        assertEquals("流程示例编号", model.getName());
        assertEquals("SysProcessExample", model.getModelName());
        assertEquals("fieldCode", model.getModelField());
        assertTrue(service.containsModel("process-example-code"));
    }

    @Test
    void syncFromYmlWarnsOnInvalidRuleModelIds() {
        // 人为造失效 field_model_id：purchase-order-code 未在本仓库 yml 中配置
        when(ruleDao.selectDistinctModelIds()).thenReturn(
                List.of("process-example-code", "purchase-order-code"));

        service.syncFromYml();

        assertTrue(service.containsModel("process-example-code"));
        assertTrue(logAppender.list.stream()
                        .anyMatch(event -> event.getLevel() == Level.WARN
                                && String.valueOf(event.getFormattedMessage()).contains("purchase-order-code")),
                "失效接入项应输出 WARN 告警日志");
        assertFalse(logAppender.list.stream()
                        .anyMatch(event -> event.getLevel() == Level.WARN
                                && String.valueOf(event.getFormattedMessage()).contains("process-example-code")),
                "有效接入项不应告警");
    }

    @Test
    void syncFromYmlKeepsCacheWhenRuleDaoQueryFails() {
        when(ruleDao.selectDistinctModelIds()).thenThrow(new RuntimeException("db down"));

        service.syncFromYml();

        // 告警查询失败不阻断缓存构建、不抛出
        assertTrue(service.containsModel("process-example-code"));
    }

    // ---------------------------------------------------------------- 条目解析

    @Test
    void parseEntriesSkipsMissingRequiredFields() {
        Map<String, Object> numberRule = new LinkedHashMap<>();
        numberRule.put("ok-key", Map.of("name", "名称", "modelName", "Model", "modelField", "fieldCode"));
        numberRule.put("missing-name", Map.of("modelName", "Model", "modelField", "fieldCode"));
        numberRule.put("missing-model-name", Map.of("name", "名称", "modelField", "fieldCode"));
        numberRule.put("missing-model-field", Map.of("name", "名称", "modelName", "Model"));
        numberRule.put("", Map.of("name", "名称", "modelName", "Model", "modelField", "fieldCode"));
        numberRule.put("not-a-map", "text");

        Map<String, SysNumberModelDo> models = new LinkedHashMap<>();
        service.parseEntries(numberRule, "test-resource", models);

        assertEquals(1, models.size(), "key 为空、非 Map 主体、属性缺失的条目应告警跳过");
        SysNumberModelDo model = models.get("ok-key");
        assertNotNull(model);
        assertEquals("名称", model.getName());
        assertEquals("Model", model.getModelName());
        assertEquals("fieldCode", model.getModelField());
        assertTrue(logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .anyMatch(event -> String.valueOf(event.getFormattedMessage()).contains("skipped")));
    }

    @Test
    void parseEntriesLaterEntryWinsOnDuplicateKey() {
        // 多文件同 key：两个 yml 各含一个同 key 条目，后加载覆盖先加载（ModelCacheService putAll 口径）
        Map<String, Object> firstFile = Map.of(
                "dup-key", Map.of("name", "首个", "modelName", "ModelA", "modelField", "fieldCode"));
        Map<String, Object> secondFile = Map.of(
                "dup-key", Map.of("name", "后个", "modelName", "ModelB", "modelField", "fieldCode"));

        Map<String, SysNumberModelDo> models = new HashMap<>();
        service.parseEntries(firstFile, "first-resource", models);
        service.parseEntries(secondFile, "second-resource", models);

        assertEquals("后个", models.get("dup-key").getName(), "多文件同 key 后加载覆盖先加载");
        assertTrue(logAppender.list.stream()
                .anyMatch(event -> String.valueOf(event.getFormattedMessage()).contains("duplicate number model key")));
    }

    // ---------------------------------------------------------------- 读取访问器

    @Test
    void getModelOptionsReturnsEntriesInDeclarationOrder() {
        when(ruleDao.selectDistinctModelIds()).thenReturn(List.of());
        service.syncFromYml();

        List<SysNumberModelDo> options = service.getModelOptions();

        assertEquals(1, options.size());
        assertEquals("process-example-code", options.get(0).getKey());
    }

    @Test
    void accessorsHandleBlankAndUnknownKeys() {
        assertNull(service.getModel(null));
        assertNull(service.getModel(""));
        assertNull(service.getModel("unknown-key"));
        assertFalse(service.containsModel("unknown-key"));
        assertFalse(service.containsModel(null));
        assertTrue(service.getModelOptions().isEmpty(), "启动前缓存为空");
    }
}

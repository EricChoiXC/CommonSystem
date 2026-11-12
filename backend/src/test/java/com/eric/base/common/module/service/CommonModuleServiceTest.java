package com.eric.base.common.module.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.eric.base.common.module.annotation.ModuleGroup;
import com.eric.base.common.module.dao.CommonModuleDao;
import com.eric.base.common.module.model.CommonModule;
import com.eric.base.sys.log.service.LogRecord;
import com.eric.base.sys.log.service.LogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.eric.base.common.module.properties.CommonModuleProperties.ACTION_ENABLE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模块组开关状态业务服务测试
 *
 * @author Eric
 * @date 2026/11/12
 * @description 覆盖启动同步的幂等三边界（不存在插入默认开启、已存在只回写元信息且绝不触碰
 *              field_enable——「管理员关闭后重启不被 yml 重置」用例固化、yml 移除保留）、
 *              moduleGroup.yml 必填校验（name/remark 缺失跳过并告警）、重复同步幂等、
 *              启动期注解校验（已知 key 无告警、未知 key 告警不阻断）与「同步 → 校验 → 预热」
 *              全链路失败不阻断启动（mock 依赖，不真连 DB）。
 */
class CommonModuleServiceTest {

    private CommonModuleDao dao;
    private ModuleGroupCacheService cacheService;
    private RequestMappingHandlerMapping requestMappingHandlerMapping;
    private LogService logService;
    private CommonModuleService service;

    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        dao = mock(CommonModuleDao.class);
        cacheService = mock(ModuleGroupCacheService.class);
        requestMappingHandlerMapping = mock(RequestMappingHandlerMapping.class);
        logService = mock(LogService.class);
        when(dao.selectAll()).thenReturn(List.of());
        when(requestMappingHandlerMapping.getHandlerMethods()).thenReturn(Map.of());
        service = new CommonModuleService(dao, cacheService, requestMappingHandlerMapping, logService);

        serviceLogger = (Logger) LoggerFactory.getLogger(CommonModuleService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logAppender);
    }

    /* ==================== 幂等边界 1：不存在 → 插入（默认开启） ==================== */

    @Test
    void insertsModuleWhenAbsentWithDefaultEnabled() {
        when(dao.selectByKey("calendar")).thenReturn(null);
        when(dao.insert(any(CommonModule.class))).thenReturn(1);

        service.syncModules(List.of(entry("calendar", "日程", "日程模块")));

        verify(dao).insert(argThat(module ->
                "calendar".equals(module.getFieldKey())
                        && "日程".equals(module.getFieldName())
                        && "日程模块".equals(module.getFieldRemark())
                        && Boolean.TRUE.equals(module.getFieldEnable())
                        && null != module.getFieldId()
                        && 32 == module.getFieldId().length()));
        verify(dao, never()).batchUpdateMeta(any());
    }

    /* ============ 幂等边界 2：已存在 → 只回写元信息，field_enable 一律不动 ============ */

    @Test
    void updatesMetaOnlyForExistingAndNeverTouchesEnable() {
        // 固化机制成立前提：库中已有「管理员关闭」的记录（field_enable = false），
        // 重启同步后仍为 false，不被 yml 重置
        CommonModule exist = module("calendar", false);
        when(dao.selectByKey("calendar")).thenReturn(exist);
        when(dao.batchUpdateMeta(any())).thenReturn(1);

        service.syncModules(List.of(entry("calendar", "日程（改名）", "日程模块描述更新")));

        // 不走任何会触碰 field_enable 的通道（insert / update / updateEnable）
        verify(dao, never()).insert(any(CommonModule.class));
        verify(dao, never()).update(any(CommonModule.class));
        verify(dao, never()).updateEnable(anyString(), any());
        // 只经 batchUpdateMeta 通道回写 name / remark（该通道 SQL 绝不动 field_enable）
        verify(dao).batchUpdateMeta(argThat(modules ->
                1 == modules.size()
                        && "calendar".equals(modules.get(0).getFieldKey())
                        && "日程（改名）".equals(modules.get(0).getFieldName())
                        && "日程模块描述更新".equals(modules.get(0).getFieldRemark())
                        && null == modules.get(0).getFieldEnable()));
        assertFalse(exist.getFieldEnable());
    }

    /* ============ 幂等边界 3：yml 移除的 key → 库中保留（不删除不改状态） ============ */

    @Test
    void keepsRowsRemovedFromYmlUntouched() {
        // yml 中只剩 calendar；库中另有 org-group（yml 已移除）
        when(dao.selectByKey("calendar")).thenReturn(module("calendar", true));
        when(dao.batchUpdateMeta(any())).thenReturn(1);

        service.syncModules(List.of(entry("calendar", "日程", "日程模块")));

        // 同步只感知 yml 条目：不查询、不删除、不更新 yml 之外的 key
        verify(dao).selectByKey("calendar");
        verify(dao, never()).selectByKey("org-group");
        verify(dao, never()).deleteById(anyString());
        verify(dao, never()).updateEnable(anyString(), any());
        verify(dao, never()).update(any(CommonModule.class));
        verify(dao).batchUpdateMeta(argThat(modules ->
                1 == modules.size() && "calendar".equals(modules.get(0).getFieldKey())));
    }

    /* ==================== moduleGroup.yml 必填校验：缺失跳过并告警 ==================== */

    @Test
    void skipsEntriesWithMissingNameOrRemarkAndWarns() {
        Map<String, Object> moduleGroups = new LinkedHashMap<>();
        moduleGroups.put("calendar", Map.of("name", "日程", "remark", "日程模块"));
        moduleGroups.put("no-name", Map.of("remark", "缺名称"));
        moduleGroups.put("blank-name", Map.of("name", "   ", "remark", "名称空白"));
        moduleGroups.put("no-remark", Map.of("name", "缺描述"));
        moduleGroups.put("", Map.of("name", "名称", "remark", "空 key"));
        moduleGroups.put("scalar", "日程");

        List<CommonModule> entries = service.parseModuleGroups(moduleGroups, "unit-test");

        // 只有合法条目被保留；field_enable 不在解析期预设（由同步核心按边界写入）
        assertEquals(1, entries.size());
        assertEquals("calendar", entries.get(0).getFieldKey());
        assertEquals("日程", entries.get(0).getFieldName());
        assertEquals("日程模块", entries.get(0).getFieldRemark());
        assertNull(entries.get(0).getFieldEnable());
        // 被跳过条目逐一告警（含非法 key 值）
        assertWarnContaining("no-name");
        assertWarnContaining("blank-name");
        assertWarnContaining("no-remark");
        assertWarnContaining("scalar");
        assertWarnContaining("key is required");
    }

    /* ==================== 重复同步幂等 ==================== */

    @Test
    void repeatedSyncIsIdempotent() {
        // 用内存集合模拟库中已存在的 key，验证第二次同步不再产生新插入
        Set<String> knownKeys = new HashSet<>();
        when(dao.selectByKey(anyString())).thenAnswer(invocation ->
                knownKeys.contains(invocation.getArgument(0)) ? new CommonModule() : null);
        when(dao.insert(any(CommonModule.class))).thenAnswer(invocation -> {
            CommonModule module = invocation.getArgument(0);
            knownKeys.add(module.getFieldKey());
            return 1;
        });
        when(dao.batchUpdateMeta(any())).thenReturn(1);

        List<CommonModule> entries = List.of(
                entry("calendar", "日程", "日程模块"),
                entry("org-group", "组织架构-群组", "组织架构-群组模块"));

        service.syncModules(entries);
        service.syncModules(entries);

        // 两个条目各只插入一次；第二次同步全部走「已存在 → 元信息回写」分支
        // （首轮 toUpdateMeta 为空，batchUpdateMeta 被跳过，仅第二轮调用一次）
        verify(dao, times(2)).insert(any(CommonModule.class));
        verify(dao, times(1)).batchUpdateMeta(any());
        verify(dao, never()).deleteById(anyString());
        verify(dao, never()).updateEnable(anyString(), any());
    }

    /* ==================== 启动期注解校验 ==================== */

    @Test
    void collectModuleGroupKeysPrefersMethodLevelAndFallsBackToClassLevel() {
        Map<RequestMappingInfo, HandlerMethod> handlers = Map.of(
                info("/known/one"), handler(new KnownController(), "one"),
                info("/known/two"), handler(new KnownController(), "two"),
                info("/override/one"), handler(new MethodOverrideController(), "one"),
                info("/plain/one"), handler(new PlainController(), "one"));
        when(requestMappingHandlerMapping.getHandlerMethods()).thenReturn(handlers);

        Set<String> keys = service.collectModuleGroupKeys();

        // 类级注解按 HandlerMethod 汇总去重；方法级注解覆盖类级；无注解的接口不收集
        assertEquals(Set.of("calendar", "org-group"), keys);
    }

    @Test
    void validationPassesForKnownKeysWithoutWarn() {
        when(requestMappingHandlerMapping.getHandlerMethods()).thenReturn(Map.of(
                info("/known/one"), handler(new KnownController(), "one"),
                info("/plain/one"), handler(new PlainController(), "one")));
        when(dao.selectAll()).thenReturn(List.of(module("calendar", true)));

        service.validateModuleGroupAnnotations();

        assertTrue(warnMessages().isEmpty(), () -> "unexpected warns: " + warnMessages());
        verify(dao, never()).selectByKey(anyString());
    }

    @Test
    void validationWarnsForUnknownKeyWithoutBlocking() {
        when(requestMappingHandlerMapping.getHandlerMethods()).thenReturn(Map.of(
                info("/known/one"), handler(new KnownController(), "one"),
                info("/unknown/one"), handler(new UnknownController(), "one")));
        when(dao.selectAll()).thenReturn(List.of(module("calendar", true)));

        assertDoesNotThrow(service::validateModuleGroupAnnotations);

        // 未知 key 记 WARN（含非法 key 值），已知 key 不告警，不抛异常
        assertWarnContaining("unknown-key");
        assertNoWarnContaining("calendar");
    }

    /* ============ 全链路（同步 → 校验 → 预热）：失败只记日志不阻断启动 ============ */

    @Test
    void syncFromYmlWarmsCacheAfterSync() {
        // 当前 classpath 无 moduleGroup.yml（首批 yml 由 MOD3-BE-002 落地），同步为空集；
        // 全链路仍需完成注解校验与缓存预热
        service.syncFromYml();

        verify(cacheService).refresh();
    }

    @Test
    void syncFromYmlContinuesWhenDbUnavailable() {
        // 注解校验阶段 DB 不可用（selectAll 抛出）：WARN 后继续预热，不阻断应用启动
        when(requestMappingHandlerMapping.getHandlerMethods()).thenReturn(Map.of(
                info("/known/one"), handler(new KnownController(), "one")));
        when(dao.selectAll()).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(service::syncFromYml);

        verify(cacheService).refresh();
        assertFalse(warnMessages().isEmpty());
    }

    @Test
    void syncFromYmlContinuesWhenValidationFails() {
        when(requestMappingHandlerMapping.getHandlerMethods())
                .thenThrow(new RuntimeException("handler mapping down"));

        assertDoesNotThrow(service::syncFromYml);

        // 校验失败只告警，不阻断后续缓存预热
        verify(cacheService).refresh();
        assertFalse(warnMessages().isEmpty());
    }

    @Test
    void syncFromYmlContinuesWhenRefreshFails() {
        doThrow(new RuntimeException("cache down")).when(cacheService).refresh();

        assertDoesNotThrow(service::syncFromYml);
    }

    /* ==================== 管理面：模块组列表 ==================== */

    @Test
    void listModulesReturnsFullListFromDao() {
        List<CommonModule> expected = List.of(module("calendar", true), module("org-group", false));
        when(dao.selectAll()).thenReturn(expected);

        List<CommonModule> modules = service.listModules();

        assertEquals(expected, modules);
        verify(dao).selectAll();
    }

    /* ==================== 管理面：启停用（/enable 通道） ==================== */

    @Test
    void updateEnableUpdatesRowRefreshesCacheAndRecordsSuccessLog() {
        when(dao.selectById("m1")).thenReturn(module("calendar", true));
        when(dao.updateEnable("m1", false)).thenReturn(1);

        service.updateEnable("m1", false);

        verify(dao).updateEnable("m1", false);
        // 切换成功路径：本实例刷新 + Redis 广播（best-effort）
        verify(cacheService).refreshAndBroadcast();
        ArgumentCaptor<LogRecord> captor = ArgumentCaptor.forClass(LogRecord.class);
        verify(logService).record(captor.capture());
        LogRecord record = captor.getValue();
        assertEquals("1", record.getLogType());
        assertEquals("COMMON-MODULE", record.getModule());
        assertEquals(ACTION_ENABLE, record.getAction());
        assertEquals("m1", record.getBusinessId());
        assertEquals("{\"fieldEnable\":true}", record.getBefore());
        assertEquals("{\"fieldEnable\":false}", record.getAfter());
        assertEquals("SUCCESS", record.getResult());
        assertNull(record.getFailReason());
    }

    @Test
    void updateEnableRejectsBlankFieldId() {
        assertThrows(IllegalArgumentException.class, () -> service.updateEnable("  ", false));
        assertThrows(IllegalArgumentException.class, () -> service.updateEnable(null, false));

        verify(dao, never()).selectById(anyString());
        verify(dao, never()).updateEnable(anyString(), any());
        verify(cacheService, never()).refreshAndBroadcast();
        verify(logService, never()).record(any());
    }

    @Test
    void updateEnableRejectsNullFieldEnable() {
        assertThrows(IllegalArgumentException.class, () -> service.updateEnable("m1", null));

        verify(dao, never()).selectById(anyString());
        verify(dao, never()).updateEnable(anyString(), any());
        verify(cacheService, never()).refreshAndBroadcast();
        verify(logService, never()).record(any());
    }

    @Test
    void updateEnableRejectsUnknownModule() {
        when(dao.selectById("m1")).thenReturn(null);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateEnable("m1", true));

        assertEquals("模块组不存在或已删除", e.getMessage());
        verify(dao, never()).updateEnable(anyString(), any());
        verify(cacheService, never()).refreshAndBroadcast();
        verify(logService, never()).record(any());
    }

    @Test
    void updateEnableFailsWhenNoRowAffectedAndRecordsFailureLog() {
        when(dao.selectById("m1")).thenReturn(module("calendar", true));
        when(dao.updateEnable("m1", false)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.updateEnable("m1", false));

        // 影响行数为 0：不刷新缓存，记 FAILED 日志后原样抛出
        verify(cacheService, never()).refreshAndBroadcast();
        ArgumentCaptor<LogRecord> captor = ArgumentCaptor.forClass(LogRecord.class);
        verify(logService).record(captor.capture());
        LogRecord record = captor.getValue();
        assertEquals("FAILED", record.getResult());
        assertEquals(ACTION_ENABLE, record.getAction());
        assertEquals("m1", record.getBusinessId());
        assertEquals("{\"fieldEnable\":true}", record.getBefore());
        assertEquals("{\"fieldEnable\":false}", record.getAfter());
        assertTrue(record.getFailReason().contains("expected one affected row"));
    }

    @Test
    void updateEnableCompletesWhenLogRecordingFails() {
        when(dao.selectById("m1")).thenReturn(module("calendar", false));
        when(dao.updateEnable("m1", true)).thenReturn(1);
        doThrow(new RuntimeException("log down")).when(logService).record(any());

        // 日志记录失败不影响主流程：更新与缓存刷新照常完成
        assertDoesNotThrow(() -> service.updateEnable("m1", true));
        verify(dao).updateEnable("m1", true);
        verify(cacheService).refreshAndBroadcast();
    }

    /* ==================== 辅助 ==================== */

    private static CommonModule entry(String fieldKey, String fieldName, String fieldRemark) {
        CommonModule module = new CommonModule();
        module.setFieldKey(fieldKey);
        module.setFieldName(fieldName);
        module.setFieldRemark(fieldRemark);
        return module;
    }

    private static CommonModule module(String fieldKey, boolean fieldEnable) {
        CommonModule module = new CommonModule();
        module.setFieldKey(fieldKey);
        module.setFieldEnable(fieldEnable);
        return module;
    }

    private static RequestMappingInfo info(String path) {
        return RequestMappingInfo.paths(path).build();
    }

    private static HandlerMethod handler(Object controller, String methodName) {
        try {
            return new HandlerMethod(controller, controller.getClass().getMethod(methodName));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> warnMessages() {
        return logAppender.list.stream()
                .filter(event -> Level.WARN.equals(event.getLevel()))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private void assertWarnContaining(String fragment) {
        assertTrue(warnMessages().stream().anyMatch(message -> message.contains(fragment)),
                "expected a WARN log containing [" + fragment + "] but got: " + warnMessages());
    }

    private void assertNoWarnContaining(String fragment) {
        assertTrue(warnMessages().stream().noneMatch(message -> message.contains(fragment)),
                "unexpected WARN log containing [" + fragment + "]: " + warnMessages());
    }

    /* ==================== 测试用 Controller（仅供注解读取，不参与 Spring 扫描） ==================== */

    @Controller
    @ModuleGroup(moduleGroupKey = "calendar")
    static class KnownController {

        @GetMapping("/test/known/one")
        public void one() {
        }

        @GetMapping("/test/known/two")
        public void two() {
        }
    }

    @Controller
    @ModuleGroup(moduleGroupKey = "calendar")
    static class MethodOverrideController {

        @ModuleGroup(moduleGroupKey = "org-group")
        @GetMapping("/test/override/one")
        public void one() {
        }
    }

    @Controller
    static class PlainController {

        @GetMapping("/test/plain/one")
        public void one() {
        }
    }

    @Controller
    @ModuleGroup(moduleGroupKey = "unknown-key")
    static class UnknownController {

        @GetMapping("/test/unknown/one")
        public void one() {
        }
    }
}

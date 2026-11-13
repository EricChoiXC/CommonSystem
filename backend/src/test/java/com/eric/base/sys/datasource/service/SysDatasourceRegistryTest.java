package com.eric.base.sys.datasource.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.eric.base.sys.datasource.spi.SysDatasourceDefaultDo;
import com.eric.base.sys.datasource.spi.SysDatasourceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据源注册表测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆盖任务 F18-BE-002 验收标准：重复 target / 默认数据源编码输出 WARN 且
 *              首个生效（方案 A3）、编码占用判定（默认数据源编码 + 目标能力编码全集）、
 *              三个进程内索引的读取访问器与保序语义。
 */
class SysDatasourceRegistryTest {

    /** 日志捕获器（重复编码 WARN 告警验收） */
    private ListAppender<ILoggingEvent> logAppender;
    private Logger registryLogger;

    @BeforeEach
    void setUp() {
        registryLogger = (Logger) LoggerFactory.getLogger(SysDatasourceRegistry.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        registryLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        registryLogger.detachAppender(logAppender);
    }

    // ---------------------------------------------------------------- 构造 Provider 桩

    /** 构造仅声明目标能力编码的 Provider 桩（未桩方法由 Mockito 返回空列表/null） */
    private SysDatasourceProvider providerOf(String targetCode, SysDatasourceDefaultDo... defaults) {
        SysDatasourceProvider provider = mock(SysDatasourceProvider.class);
        when(provider.getTargetCode()).thenReturn(targetCode);
        when(provider.getDefaultDatasources()).thenReturn(List.of(defaults));
        return provider;
    }

    private SysDatasourceDefaultDo defaultOf(String code, String name) {
        SysDatasourceDefaultDo def = new SysDatasourceDefaultDo();
        def.setCode(code);
        def.setName(name);
        return def;
    }

    // ---------------------------------------------------------------- 三个索引读取

    @Test
    void resolvesProvidersAndDefaults() {
        SysDatasourceProvider provider = providerOf("calendar.list");
        SysDatasourceDefaultDo def = defaultOf("calendar.list", "日程列表");
        when(provider.getDefaultDatasources()).thenReturn(List.of(def));

        SysDatasourceRegistry registry = new SysDatasourceRegistry(List.of(provider));

        assertSame(provider, registry.getProviderByTarget("calendar.list"));
        assertSame(def, registry.getDefault("calendar.list"));
        assertSame(provider, registry.getProviderByDefaultCode("calendar.list"));
        assertEquals(List.of("calendar.list"), registry.listDefaultCodes());
    }

    @Test
    void accessorsReturnNullOnMissingOrBlankKeys() {
        SysDatasourceRegistry registry = new SysDatasourceRegistry(List.of());

        assertNull(registry.getProviderByTarget(null));
        assertNull(registry.getProviderByTarget("absent.target"));
        assertNull(registry.getDefault(null));
        assertNull(registry.getDefault("absent.code"));
        assertNull(registry.getProviderByDefaultCode(null));
        assertNull(registry.getProviderByDefaultCode("absent.code"));
    }

    // ---------------------------------------------------------------- 重复编码：WARN + 首个生效（A3）

    @Test
    void duplicateTargetCodeWarnsAndFirstProviderWins() {
        SysDatasourceProvider first = providerOf("calendar.list");
        SysDatasourceProvider second = providerOf("calendar.list");

        SysDatasourceRegistry registry = new SysDatasourceRegistry(List.of(first, second));

        assertSame(first, registry.getProviderByTarget("calendar.list"), "重复 target 编码应首个 Provider 生效");
        assertEquals(1, registry.listProviders().size());
        assertTrue(logAppender.list.stream()
                        .anyMatch(event -> event.getLevel() == Level.WARN
                                && String.valueOf(event.getFormattedMessage()).contains("calendar.list")),
                "重复 target 编码应输出 WARN 告警日志");
    }

    @Test
    void duplicateDefaultCodeWarnsAndFirstDefinitionWins() {
        SysDatasourceProvider first = providerOf("calendar.list", defaultOf("calendar.list", "第一个"));
        SysDatasourceProvider second = providerOf("calendar.other", defaultOf("calendar.list", "第二个"));

        SysDatasourceRegistry registry = new SysDatasourceRegistry(List.of(first, second));

        SysDatasourceDefaultDo resolved = registry.getDefault("calendar.list");
        assertEquals("第一个", resolved.getName(), "重复默认数据源编码应首个定义生效");
        assertSame(first, registry.getProviderByDefaultCode("calendar.list"));
        assertEquals(1, registry.listDefaults().size());
        assertTrue(logAppender.list.stream()
                        .anyMatch(event -> event.getLevel() == Level.WARN
                                && String.valueOf(event.getFormattedMessage()).contains("first definition wins")),
                "重复默认数据源编码应输出 WARN 告警日志");
    }

    @Test
    void duplicateDefaultCodeInsideSameProviderWarnsAndFirstWins() {
        SysDatasourceProvider provider = providerOf("calendar.list",
                defaultOf("calendar.list", "第一个"), defaultOf("calendar.list", "第二个"));

        SysDatasourceRegistry registry = new SysDatasourceRegistry(List.of(provider));

        assertEquals("第一个", registry.getDefault("calendar.list").getName());
        assertEquals(1, registry.listDefaults().size());
        assertTrue(logAppender.list.stream()
                        .anyMatch(event -> event.getLevel() == Level.WARN
                                && String.valueOf(event.getFormattedMessage()).contains("first definition wins")));
    }

    // ---------------------------------------------------------------- 编码占用判定

    @Test
    void isCodeOccupiedCoversDefaultAndTargetCodes() {
        SysDatasourceProvider provider = providerOf("calendar.my-list");
        SysDatasourceDefaultDo def = defaultOf("calendar.list", "日程列表");
        when(provider.getDefaultDatasources()).thenReturn(List.of(def));

        SysDatasourceRegistry registry = new SysDatasourceRegistry(List.of(provider));

        assertTrue(registry.isCodeOccupied("calendar.list"), "默认数据源编码应占用");
        assertTrue(registry.isCodeOccupied("calendar.my-list"), "目标能力编码应占用");
        assertFalse(registry.isCodeOccupied("calendar.my-created"), "未注册编码不占用");
        assertFalse(registry.isCodeOccupied(null), "空编码不占用");
        assertFalse(registry.isCodeOccupied(""), "空文本编码不占用");
    }

    @Test
    void isCodeOccupiedByDefaultOnlyCoversDefaults() {
        SysDatasourceProvider provider = providerOf("calendar.my-list");
        SysDatasourceDefaultDo def = defaultOf("calendar.list", "日程列表");
        when(provider.getDefaultDatasources()).thenReturn(List.of(def));

        SysDatasourceRegistry registry = new SysDatasourceRegistry(List.of(provider));

        assertTrue(registry.isCodeOccupiedByDefault("calendar.list"));
        assertFalse(registry.isCodeOccupiedByDefault("calendar.my-list"),
                "仅有目标能力、无默认数据源的编码不属于默认占用");
        assertFalse(registry.isCodeOccupiedByDefault(null));
    }

    // ---------------------------------------------------------------- 保序与空注册

    @Test
    void listProvidersAndDefaultsKeepRegistrationOrder() {
        SysDatasourceProvider p1 = providerOf("calendar.list", defaultOf("calendar.list", "日程列表"));
        SysDatasourceProvider p2 = providerOf("notify.todo", defaultOf("notify.todo", "待办列表"));
        SysDatasourceProvider p3 = providerOf("dict.items");

        SysDatasourceRegistry registry = new SysDatasourceRegistry(List.of(p1, p2, p3));

        assertEquals(List.of(p1, p2, p3), registry.listProviders(), "targets 下拉应按注册顺序稳定展示");
        assertEquals(List.of("calendar.list", "notify.todo"), registry.listDefaultCodes());
        assertEquals(2, registry.listDefaults().size());
    }

    @Test
    void emptyProviderListBuildsEmptyIndexes() {
        SysDatasourceRegistry registry = new SysDatasourceRegistry(List.of());

        assertTrue(registry.listProviders().isEmpty());
        assertTrue(registry.listDefaults().isEmpty());
        assertTrue(registry.listDefaultCodes().isEmpty());
        assertFalse(registry.isCodeOccupied("calendar.list"));
    }

    // ---------------------------------------------------------------- 来源类型标记

    @Test
    void typeOfMapsSourceType() {
        assertEquals("CODE", SysDatasourceRegistry.typeOf(false));
        assertEquals("CUSTOM", SysDatasourceRegistry.typeOf(true));
    }
}

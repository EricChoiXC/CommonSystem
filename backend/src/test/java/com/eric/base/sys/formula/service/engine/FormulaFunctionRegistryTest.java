package com.eric.base.sys.formula.service.engine;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.model.SysFormulaFunctionParameterDo;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 公式函数注册中心测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖任务 F14-BE-002 验收标准：重复函数码输出 WARN 且首个定义生效
 *              （对齐 SysDatasourceRegistry 先例，不抛异常）、SPI Provider 构造注入收集、
 *              元数据导出完整性（getAllFunctions 含元数据字段，AviatorFunction 执行体经
 *              @JsonIgnore 不参与 Jackson 序列化）、索引读取访问器与保序语义。
 *              纯单测不依赖 Spring 上下文：Registry 构造函数直接传入 Provider 列表。
 */
class FormulaFunctionRegistryTest {

    /** 日志捕获器（重复函数码 WARN 告警验收） */
    private ListAppender<ILoggingEvent> logAppender;
    private Logger registryLogger;

    @BeforeEach
    void setUp() {
        registryLogger = (Logger) LoggerFactory.getLogger(FormulaFunctionRegistry.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        registryLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        registryLogger.detachAppender(logAppender);
    }

    // ---------------------------------------------------------------- Provider / 定义桩

    /** Aviator 执行体桩（最小实现：无参形态，其余 arity 由 AbstractFunction 默认抛 arity 错） */
    private static class StubFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "stub";
        }

        @Override
        public AviatorObject call(Map<String, Object> env) {
            return null;
        }
    }

    /** 构造指定编码的函数定义（ROUND 样例形态：两参，一可选带默认值） */
    private FormulaFunctionDefinition definitionOf(String functionCode, String functionName) {
        FormulaFunctionDefinition definition = new FormulaFunctionDefinition();
        definition.setFunctionCode(functionCode);
        definition.setFunctionName(functionName);
        definition.setCategory(SysFormulaFunctionCategory.MATH);
        definition.setDescription("四舍五入");
        SysFormulaFunctionParameterDo value = new SysFormulaFunctionParameterDo();
        value.setName("value");
        value.setType("NUMBER");
        value.setRequired(true);
        SysFormulaFunctionParameterDo precision = new SysFormulaFunctionParameterDo();
        precision.setName("precision");
        precision.setType("NUMBER");
        precision.setRequired(false);
        precision.setDefaultValue("2");
        definition.setParameters(List.of(value, precision));
        definition.setReturnType(SysFormulaReturnType.NUMBER);
        definition.setFunction(new StubFunction());
        return definition;
    }

    /** 构造返回给定定义列表的 Provider 桩（Arrays.asList 允许 null 元素，覆盖 null 定义跳过场景） */
    private FormulaFunctionProvider providerOf(FormulaFunctionDefinition... definitions) {
        FormulaFunctionProvider provider = mock(FormulaFunctionProvider.class);
        when(provider.getFunctions()).thenReturn(java.util.Arrays.asList(definitions));
        return provider;
    }

    // ---------------------------------------------------------------- 重复函数码：WARN + 首个生效

    @Test
    void duplicateFunctionCodeWarnsAndFirstDefinitionWins() {
        FormulaFunctionDefinition first = definitionOf("ROUND", "四舍五入一");
        FormulaFunctionDefinition second = definitionOf("ROUND", "四舍五入二");

        FormulaFunctionRegistry registry = new FormulaFunctionRegistry(
                List.of(providerOf(first), providerOf(second)));

        assertSame(first, registry.getFunction("ROUND"), "重复函数码应首个定义生效");
        assertEquals("四舍五入一", registry.getFunction("ROUND").getFunctionName());
        assertEquals(1, registry.getAllFunctions().size());
        assertTrue(logAppender.list.stream()
                        .anyMatch(event -> event.getLevel() == Level.WARN
                                && String.valueOf(event.getFormattedMessage()).contains("ROUND")),
                "重复函数码应输出 WARN 告警日志");
    }

    @Test
    void duplicateFunctionCodeInsideSameProviderWarnsAndFirstWins() {
        FormulaFunctionDefinition first = definitionOf("ROUND", "四舍五入一");
        FormulaFunctionDefinition second = definitionOf("ROUND", "四舍五入二");

        FormulaFunctionRegistry registry = new FormulaFunctionRegistry(List.of(providerOf(first, second)));

        assertSame(first, registry.getFunction("ROUND"));
        assertEquals(1, registry.getAllFunctions().size());
        assertTrue(logAppender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.WARN
                        && String.valueOf(event.getFormattedMessage()).contains("first definition wins")));
    }

    @Test
    void blankOrMissingCodeSkippedWithWarn() {
        FormulaFunctionDefinition blank = definitionOf("", "空编码");
        FormulaFunctionDefinition noCode = new FormulaFunctionDefinition();
        noCode.setFunctionName("无编码");

        FormulaFunctionRegistry registry = new FormulaFunctionRegistry(List.of(providerOf(blank, noCode, null)));

        assertTrue(registry.getAllFunctions().isEmpty(), "无编码/空编码/null 定义应跳过不注册");
        assertTrue(logAppender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN));
    }

    // ---------------------------------------------------------------- SPI Provider 收集

    @Test
    void collectsFunctionsFromMultipleProviders() {
        FormulaFunctionDefinition round = definitionOf("ROUND", "四舍五入");
        FormulaFunctionDefinition dsQuery = definitionOf("DS_QUERY", "数据源取数");
        dsQuery.setCategory(SysFormulaFunctionCategory.DATA);

        FormulaFunctionRegistry registry = new FormulaFunctionRegistry(
                List.of(providerOf(round), providerOf(dsQuery)));

        assertSame(round, registry.getFunction("ROUND"));
        assertSame(dsQuery, registry.getFunction("DS_QUERY"));
        assertEquals(2, registry.getAllFunctions().size());
    }

    @Test
    void providerReturningEmptyListContributesNothing() {
        FormulaFunctionProvider empty = mock(FormulaFunctionProvider.class);
        when(empty.getFunctions()).thenReturn(List.of());

        FormulaFunctionRegistry registry = new FormulaFunctionRegistry(List.of(empty));

        assertTrue(registry.getAllFunctions().isEmpty());
    }

    @Test
    void accessorsReturnNullOnMissingOrNullKeys() {
        FormulaFunctionRegistry registry =
                new FormulaFunctionRegistry(List.of(providerOf(definitionOf("ROUND", "四舍五入"))));

        assertNull(registry.getFunction(null));
        assertNull(registry.getFunction("ABSENT_FUNC"));
    }

    // ---------------------------------------------------------------- 元数据导出完整性与序列化

    @Test
    void metadataCarriesAllFieldsAndExecutionBody() {
        FormulaFunctionDefinition definition = definitionOf("ROUND", "四舍五入");

        FormulaFunctionRegistry registry = new FormulaFunctionRegistry(List.of(providerOf(definition)));
        FormulaFunctionDefinition resolved = registry.getFunction("ROUND");

        assertEquals("ROUND", resolved.getFunctionCode());
        assertEquals("四舍五入", resolved.getFunctionName());
        assertEquals(SysFormulaFunctionCategory.MATH, resolved.getCategory());
        assertEquals("四舍五入", resolved.getDescription());
        assertEquals(SysFormulaReturnType.NUMBER, resolved.getReturnType());
        assertEquals(2, resolved.getParameters().size());
        assertEquals("value", resolved.getParameters().get(0).getName());
        assertEquals("NUMBER", resolved.getParameters().get(0).getType());
        assertTrue(resolved.getParameters().get(0).isRequired());
        assertTrue(resolved.getParameters().get(1).isRequired() == false);
        assertEquals("2", resolved.getParameters().get(1).getDefaultValue());
        assertTrue(null != resolved.getFunction(), "执行体应随定义一并可取（引擎注册用）");
    }

    @Test
    void getAllFunctionsKeepsRegistrationOrderAndUnmodifiable() {
        FormulaFunctionDefinition round = definitionOf("ROUND", "四舍五入");
        FormulaFunctionDefinition abs = definitionOf("ABS", "绝对值");

        FormulaFunctionRegistry registry = new FormulaFunctionRegistry(List.of(providerOf(round, abs)));

        assertEquals(List.of("ROUND", "ABS"),
                registry.getAllFunctions().stream().map(FormulaFunctionDefinition::getFunctionCode).toList(),
                "/functions 元数据应按注册顺序稳定展示");
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> registry.getAllFunctions().add(definitionOf("X", "X")),
                "全量导出应为不可变列表");
    }

    @Test
    void metadataSerializationExcludesFunctionField() throws Exception {
        FormulaFunctionRegistry registry =
                new FormulaFunctionRegistry(List.of(providerOf(definitionOf("ROUND", "四舍五入"))));

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(registry.getAllFunctions());

        assertTrue(json.contains("\"functionCode\":\"ROUND\""), "序列化应含函数元数据 functionCode");
        assertTrue(json.contains("\"parameters\""), "序列化应含参数元数据");
        assertTrue(json.contains("\"returnType\":\"NUMBER\""), "序列化应含返回类型元数据");
        assertFalse(json.contains("\"function\":"), "@JsonIgnore 应排除 AviatorFunction 执行体字段"
                + "（\"functionCode\" 是元数据字段，不算执行体泄漏）");
        // 元数据 JSON 应可反序列化回 SysFormulaFunctionDo（前后端共用同一份元数据口径）
        com.eric.base.sys.formula.model.SysFormulaFunctionDo[] deserialized = mapper.readValue(json,
                com.eric.base.sys.formula.model.SysFormulaFunctionDo[].class);
        assertEquals(1, deserialized.length);
        assertEquals("ROUND", deserialized[0].getFunctionCode());
        assertEquals(SysFormulaFunctionCategory.MATH, deserialized[0].getCategory());
    }

    @Test
    void emptyProviderListBuildsEmptyRegistry() {
        FormulaFunctionRegistry registry = new FormulaFunctionRegistry(List.of());

        assertTrue(registry.getAllFunctions().isEmpty());
        assertNull(registry.getFunction("ROUND"));
    }
}

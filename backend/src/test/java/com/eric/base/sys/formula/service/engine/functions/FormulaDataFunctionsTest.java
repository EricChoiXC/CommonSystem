package com.eric.base.sys.formula.service.engine.functions;

import com.eric.base.sys.formula.service.engine.functions.data.DataFunctionProvider;
import com.eric.base.sys.datasource.service.SysDatasourceService;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DATA 取数函数（DS_QUERY）测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖 F14-BE-003 验收标准：DS_QUERY 委托 SysDatasourceService#execute
 *              （mock 验证透传参数与返回行数组）、body 以 Map（seq.map 字面量形态）/JSON
 *              对象文本/null 多形态宽容解析、鉴权异常与数据源错误原样透出（不吞不改）、
 *              非法入参确定性拒绝；函数经 Aviator 实际求值（集成路径）。
 */
class FormulaDataFunctionsTest {

    private SysDatasourceService datasourceService;
    private DataFunctionProvider provider;
    private AviatorEvaluatorInstance engine;

    @BeforeEach
    void setUp() {
        datasourceService = mock(SysDatasourceService.class);
        provider = new DataFunctionProvider(datasourceService);
        engine = FormulaFunctionsTestSupport.newEngine(provider);
    }

    private static SysDatasourceService.SysDatasourceExecution execution(List<Map<String, Object>> rows) {
        return new SysDatasourceService.SysDatasourceExecution(rows, rows.size(), List.of("fieldId"));
    }

    // ---------------------------------------------------------------- 委托与透传

    @Test
    void delegatesToDatasourceExecuteAndReturnsRows() {
        List<Map<String, Object>> rows = List.of(new LinkedHashMap<>(Map.of("fieldId", "1")));
        when(datasourceService.execute(eq("calendar.my-created"), any(), isNull(), isNull(), isNull()))
                .thenReturn(execution(rows));

        Map<String, Object> env = new HashMap<>();
        env.put("body", new LinkedHashMap<>(Map.of("fieldName", "周报")));
        Object result = FormulaFunctionsTestSupport.eval(
                engine, "DS_QUERY('calendar.my-created', body)", env);

        assertSame(rows, result, "行数组原样返回，供 COUNT/SUM 等集合函数消费");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(datasourceService).execute(eq("calendar.my-created"), bodyCaptor.capture(),
                isNull(), isNull(), isNull());
        assertEquals("周报", bodyCaptor.getValue().get("fieldName"), "调用方条件透传（固定条件覆盖语义随数据源）");
    }

    @Test
    void bodyAsSeqMapLiteralAndJsonTextAndNull() {
        when(datasourceService.execute(anyString(), any(), isNull(), isNull(), isNull()))
                .thenReturn(execution(List.of()));
        // seq.map 字面量形态（Aviator 表达式内构造 Map）
        FormulaFunctionsTestSupport.eval(
                engine, "DS_QUERY('src', seq.map('fieldName', '周报'))");
        // JSON 对象文本形态
        FormulaFunctionsTestSupport.eval(
                engine, "DS_QUERY('src', '{\"fieldName\":\"周报\"}')");
        // null / 缺省形态 → 空条件
        FormulaFunctionsTestSupport.eval(engine, "DS_QUERY('src', missing)");
        verify(datasourceService).execute(eq("src"), eq(new HashMap<>()), isNull(), isNull(), isNull());
    }

    // ---------------------------------------------------------------- 鉴权与数据源错误原样透出

    @Test
    void authorizationExceptionFromDatasourcePropagatesAsIs() {
        when(datasourceService.execute(anyString(), any(), isNull(), isNull(), isNull()))
                .thenThrow(new com.eric.base.common.exception.AuthorizationException("FORBIDDEN", "无权限"));
        com.eric.base.common.exception.AuthorizationException error =
                assertThrows(com.eric.base.common.exception.AuthorizationException.class,
                        () -> FormulaFunctionsTestSupport.eval(engine, "DS_QUERY('src', missing)"));
        assertEquals("FORBIDDEN", error.getCode(), "鉴权异常原样透出，不吞不改（§6.4）");
    }

    @Test
    void datasourceErrorCodeExceptionPropagatesAsIs() {
        when(datasourceService.execute(anyString(), any(), isNull(), isNull(), isNull()))
                .thenThrow(new IllegalArgumentException("CS_DATASOURCE_NOT_FOUND: 数据源不存在或已停用: src"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "DS_QUERY('src', missing)"));
        assertTrue(error.getMessage().startsWith("CS_DATASOURCE_NOT_FOUND: "),
                "数据源错误码异常原样透出（引擎按前缀统一识别）");
    }

    // ---------------------------------------------------------------- 非法入参确定性拒绝

    @Test
    void blankOrNonTextCodeRejected() {
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "DS_QUERY('  ', missing)"));
        assertTrue(blank.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
        IllegalArgumentException nonText = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "DS_QUERY(123, missing)"));
        assertTrue(nonText.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    @Test
    void malformedBodyJsonRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "DS_QUERY('src', 'not-json')"));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }

    @Test
    void nonMapBodyRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FormulaFunctionsTestSupport.eval(engine, "DS_QUERY('src', 123)"));
        assertTrue(error.getMessage().startsWith("CS_FORMULA_EXECUTE_FAILED: "));
    }
}

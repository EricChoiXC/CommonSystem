package com.eric.base.sys.formula.service.engine;

import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.datasource.service.SysDatasourceService;
import com.eric.base.sys.formula.service.engine.functions.collection.CollectionFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.compare.CompareFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.convert.ConvertFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.data.DataFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.date.DateFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.logic.LogicFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.math.MathFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.string.StringFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.system.SystemFunctionProvider;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 引擎测试公共支撑（F14-BE-004）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 构造真实九类内置函数注册的 FormulaEngine（DS_QUERY 以 mock
 *              SysDatasourceService 注入假数据），并经 MockedStatic 构造 UserUtil
 *              会话态供系统变量注入（形态对齐 FormulaBuiltinFunctionsIntegrationTest）。
 *              另提供错误码断言小件：引擎错误约定为
 *              IllegalArgumentException(错误码 + ": " + message)，前缀即 CS_FORMULA_*。
 */
class FormulaEngineTestSupport {

    /** DS_QUERY 假数据源服务（默认返回空行数组，用例自行覆盖返回值） */
    protected SysDatasourceService datasourceService;

    /** UserUtil 静态 mock（系统变量注入来源，会话用户「张三」/「财务部」/SYS_FORMULA_ADMIN） */
    protected MockedStatic<UserUtil> userUtilMock;

    @BeforeEach
    void setUpSupport() {
        datasourceService = mock(SysDatasourceService.class);
        when(datasourceService.execute(any(), any(), any(), any(), any()))
                .thenReturn(new SysDatasourceService.SysDatasourceExecution(List.of(), 0, List.of()));
        userUtilMock = Mockito.mockStatic(UserUtil.class);
        userUtilMock.when(UserUtil::getCurrentUser).thenReturn(sessionUser());
    }

    @AfterEach
    void tearDownSupport() {
        userUtilMock.close();
    }

    /** 会话用户（系统变量注入来源；字段口径对齐 FormulaBuiltinFunctionsIntegrationTest） */
    protected static com.eric.base.sys.org.model.SysOrgUser sessionUser() {
        com.eric.base.sys.org.model.SysOrgUser user = new com.eric.base.sys.org.model.SysOrgUser();
        user.setFieldId("1".repeat(32));
        user.setFieldName("张三");
        user.setFieldParentId("2".repeat(32));
        user.setFieldParentName("财务部");
        com.eric.base.sys.org.model.SysOrgRole role = new com.eric.base.sys.org.model.SysOrgRole();
        role.setFieldCode("SYS_FORMULA_ADMIN");
        user.setRoles(List.of(role));
        return user;
    }

    /** 内置九类 Provider 全集（DS_QUERY 挂 mock 数据源服务） */
    protected static List<FormulaFunctionProvider> builtinProviders(SysDatasourceService datasourceService) {
        return List.of(
                new MathFunctionProvider(),
                new LogicFunctionProvider(),
                new CompareFunctionProvider(),
                new StringFunctionProvider(),
                new DateFunctionProvider(),
                new CollectionFunctionProvider(),
                new ConvertFunctionProvider(),
                new SystemFunctionProvider(),
                new DataFunctionProvider(datasourceService));
    }

    /** 生产形态引擎（常量超时上限） */
    protected FormulaEngine newEngine() {
        return new FormulaEngine(new FormulaFunctionRegistry(builtinProviders(datasourceService)));
    }

    /** 测试形态引擎（注入超时毫秒数，R04 风险缓解） */
    protected FormulaEngine newEngine(long timeoutMillis) {
        return new FormulaEngine(new FormulaFunctionRegistry(builtinProviders(datasourceService)), timeoutMillis);
    }

    /**
     * 测试形态引擎（注入超时毫秒数 + 追加测试专用函数 Provider——慢函数/探针函数
     * 注册进测试 Registry，不污染内置函数面）
     */
    protected FormulaEngine newEngine(long timeoutMillis, FormulaFunctionProvider... extraProviders) {
        List<FormulaFunctionProvider> providers = new java.util.ArrayList<>(builtinProviders(datasourceService));
        providers.addAll(List.of(extraProviders));
        return new FormulaEngine(new FormulaFunctionRegistry(providers), timeoutMillis);
    }

    /** 测试专用函数 Provider 桩（mockito 形态对齐 FormulaFunctionRegistryTest 先例） */
    protected static FormulaFunctionProvider providerOf(FormulaFunctionDefinition definition) {
        FormulaFunctionProvider provider = Mockito.mock(FormulaFunctionProvider.class);
        Mockito.when(provider.getFunctions()).thenReturn(List.of(definition));
        return provider;
    }

    /** 空上下文 */
    protected static FormulaContext emptyContext() {
        return new FormulaContext(Map.of());
    }

    /** 变量上下文 */
    protected static FormulaContext contextOf(Map<String, Object> vars) {
        return new FormulaContext(vars);
    }

    /** 断言 IllegalArgumentException 且 message 以期望错误码为前缀，返回完整消息 */
    protected static String assertFormulaError(Runnable runnable, String expectedCode) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, runnable::run);
        assertTrue(null != error.getMessage() && error.getMessage().startsWith(expectedCode + ":"),
                "错误码应为 " + expectedCode + "，实际: " + error.getMessage());
        return error.getMessage();
    }

    /** 变量便捷构造 */
    protected static Map<String, Object> vars(Object... keyValues) {
        Map<String, Object> vars = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            vars.put((String) keyValues[i], keyValues[i + 1]);
        }
        return vars;
    }
}

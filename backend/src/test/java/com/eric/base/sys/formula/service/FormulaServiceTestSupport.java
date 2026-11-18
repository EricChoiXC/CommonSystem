package com.eric.base.sys.formula.service;

import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.datasource.service.SysDatasourceService;
import com.eric.base.sys.formula.service.engine.FormulaEngine;
import com.eric.base.sys.formula.service.engine.FormulaFunctionRegistry;
import com.eric.base.sys.formula.service.engine.functions.collection.CollectionFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.compare.CompareFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.convert.ConvertFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.data.DataFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.date.DateFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.logic.LogicFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.math.MathFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.string.StringFunctionProvider;
import com.eric.base.sys.formula.service.engine.functions.system.SystemFunctionProvider;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 公式 Service 三件测试公共支撑（F14-BE-007）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 构造真实九类内置函数注册的 FormulaEngine（DS_QUERY 以 mock
 *              SysDatasourceService 注入假数据，形态对齐引擎侧 FormulaEngineTestSupport
 *              先例；该支撑类位于 engine 测试包内不可跨包复用，此处按同形态落 service
 *              测试包），并经 MockedStatic 构造 UserUtil 会话态——管理面审计字段
 *              （UserUtil.getCurrentUserId）与模拟系统变量注入（FormulaContext →
 *              UserUtil.getCurrentUser）共用同一会话用户。另提供错误码断言小件：
 *              管理面错误约定为 IllegalArgumentException(错误码 + ": " + message)，
 *              前缀即 CS_FORMULA_*。
 */
class FormulaServiceTestSupport {

    /** 会话用户 ID（32 位，避免 BaseModel.setFieldId 触发重新生成） */
    protected static final String USER_ID = "1".repeat(32);

    /** DS_QUERY 假数据源服务（默认返回空行数组，用例自行覆盖返回值） */
    protected SysDatasourceService datasourceService;

    /** UserUtil 静态 mock（审计字段与系统变量注入来源） */
    protected MockedStatic<UserUtil> userUtilMock;

    @BeforeEach
    void setUpSupport() {
        datasourceService = mock(SysDatasourceService.class);
        when(datasourceService.execute(any(), any(), any(), any(), any()))
                .thenReturn(new SysDatasourceService.SysDatasourceExecution(List.of(), 0, List.of()));
        userUtilMock = Mockito.mockStatic(UserUtil.class);
        userUtilMock.when(UserUtil::getCurrentUser).thenReturn(sessionUser());
        userUtilMock.when(UserUtil::getCurrentUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDownSupport() {
        userUtilMock.close();
    }

    /** 会话用户（系统变量注入来源：用户/部门 ID + SYS_FORMULA_ADMIN 角色） */
    protected static SysOrgUser sessionUser() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(USER_ID);
        user.setFieldName("张三");
        user.setFieldParentId("2".repeat(32));
        user.setFieldParentName("财务部");
        com.eric.base.sys.org.model.SysOrgRole role = new com.eric.base.sys.org.model.SysOrgRole();
        role.setFieldCode("SYS_FORMULA_ADMIN");
        role.setFieldName("公式管理员");
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

    /** 生产形态引擎（常量超时上限；真实注册中心 + 九类内置函数） */
    protected FormulaEngine newEngine() {
        return new FormulaEngine(new FormulaFunctionRegistry(builtinProviders(datasourceService)));
    }

    /** 断言 IllegalArgumentException 且 message 以期望错误码为前缀，返回完整消息 */
    protected static String assertFormulaError(Runnable runnable, String expectedCode) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, runnable::run);
        assertTrue(null != error.getMessage() && error.getMessage().startsWith(expectedCode + ":"),
                "错误码应为 " + expectedCode + "，实际: " + error.getMessage());
        return error.getMessage();
    }
}

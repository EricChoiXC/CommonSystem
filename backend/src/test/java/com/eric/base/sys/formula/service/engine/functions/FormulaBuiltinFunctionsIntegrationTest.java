package com.eric.base.sys.formula.service.engine.functions;

import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.datasource.service.SysDatasourceService;
import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
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
import com.eric.base.sys.org.model.SysOrgRole;
import com.eric.base.sys.org.model.SysOrgUser;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 内置函数九类首批集成性验证（F14-BE-003）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 九类 Provider 全量装配到 Aviator 执行实例（F14-BE-004 引擎同形态：
 *              new AviatorEvaluatorInstance → addFunction → execute），验证方案 §2.2
 *              首批函数全集：① 注册名与执行体 getName() 一致、② 全部函数名经参数化
 *              最小表达式实际可调用、③ Provider 元数据（九类分类/名称/说明/参数/返回类型）
 *              齐全、④ 组合形态（DS_QUERY 行数组交集合函数、CONCAT+YEAR）走通。
 *              数量口径：§2.2 枚举函数为 5+5+6+5+6+7+3+3+1 = 41 个（任务卡片笔误 33）。
 */
class FormulaBuiltinFunctionsIntegrationTest {

    /** 方案 §2.2 首批函数全集（九类，逐字对齐枚举清单） */
    private static final List<String> EXPECTED_ALL = List.of(
            // MATH (5)
            "ABS", "ROUND", "CEIL", "FLOOR", "POWER",
            // LOGIC (5)
            "IF", "AND", "OR", "NOT", "IFNULL",
            // COMPARE (6)
            "EQ", "NE", "GT", "GTE", "LT", "LTE",
            // STRING (5)
            "CONCAT", "SUBSTRING", "LENGTH", "TRIM", "REPLACE",
            // DATE (6)
            "NOW", "TODAY", "DATEADD", "DATEDIFF", "YEAR", "MONTH",
            // COLLECTION (7)
            "SUM", "AVG", "MAX", "MIN", "COUNT", "COUNTIF", "SUMIF",
            // CONVERT (3)
            "TO_NUMBER", "TO_STRING", "TO_DATE",
            // SYSTEM (3)
            "CURRENT_USER", "CURRENT_DEPT", "CURRENT_ROLE",
            // DATA (1)
            "DS_QUERY");

    /** 5+5+6+5+6+7+3+3+1 */
    private static final int EXPECTED_SIZE = 41;

    private SysDatasourceService datasourceService;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        datasourceService = mock(SysDatasourceService.class);
        when(datasourceService.execute(any(), any(), any(), any(), any()))
                .thenReturn(new SysDatasourceService.SysDatasourceExecution(List.of(), 0, List.of()));
        // SYSTEM 函数会话语义：集成验证取服务端注入值（§6.3），单测经 MockedStatic 构造登录态
        userUtil = Mockito.mockStatic(UserUtil.class);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(sessionUser());
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    private static SysOrgUser sessionUser() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId("1".repeat(32));
        user.setFieldName("张三");
        user.setFieldParentName("财务部");
        SysOrgRole role = new SysOrgRole();
        role.setFieldCode("SYS_FORMULA_ADMIN");
        user.setRoles(List.of(role));
        return user;
    }

    private List<FormulaFunctionProvider> buildProviders() {
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

    @Test
    void allFunctionsRegisteredWithCompleteMetadata() {
        Map<String, FormulaFunctionDefinition> byCode = new LinkedHashMap<>();
        for (FormulaFunctionProvider provider : buildProviders()) {
            for (FormulaFunctionDefinition definition : provider.getFunctions()) {
                byCode.put(definition.getFunctionCode(), definition);
            }
        }
        assertEquals(EXPECTED_SIZE, byCode.size(), "九类首批 41 函数须全部注册");
        assertEquals(Set.copyOf(EXPECTED_ALL), byCode.keySet(), "函数码清单与方案 §2.2 逐字一致");
        for (FormulaFunctionDefinition definition : byCode.values()) {
            assertEquals(definition.getFunctionCode(), definition.getFunction().getName(),
                    "functionCode 与执行体 getName() 须一致: " + definition.getFunctionCode());
            assertTrue(null != definition.getCategory() && null != definition.getFunctionName()
                            && null != definition.getDescription() && null != definition.getReturnType()
                            && null != definition.getParameters(),
                    "元数据齐全: " + definition.getFunctionCode());
        }
        // 九类分类全覆盖
        Set<String> categories = byCode.values().stream()
                .map(def -> def.getCategory().name()).collect(Collectors.toSet());
        assertEquals(Set.of(java.util.Arrays.stream(SysFormulaFunctionCategory.values())
                        .map(Enum::name).toArray(String[]::new)),
                categories, "九类分类须全部出现");
    }

    /** 41 个函数逐一以最小表达式实际调用（可调用性 + 基本语义冒烟） */
    @Test
    void allFunctionsCallableViaAviatorExpression() {
        AviatorEvaluatorInstance engine = FormulaFunctionsTestSupport
                .newEngine(buildProviders().toArray(FormulaFunctionProvider[]::new));

        Map<String, Object> env = new HashMap<>();
        env.put("seq", List.of(1, 2, 3));
        env.put("seqA", List.of("A", "B", "A"));
        env.put("seqAmount", List.of(10, 20, 30));
        env.put("text", "abcdef");
        env.put("d", "2026-11-18");

        Map<String, String> minimalExpressions = new LinkedHashMap<>();
        minimalExpressions.put("ABS", "ABS(-5)");
        minimalExpressions.put("ROUND", "ROUND(1.26, 1)");
        minimalExpressions.put("CEIL", "CEIL(1.2)");
        minimalExpressions.put("FLOOR", "FLOOR(1.8)");
        minimalExpressions.put("POWER", "POWER(2, 3)");
        minimalExpressions.put("IF", "IF(1 > 0, 'a', 'b')");
        minimalExpressions.put("AND", "AND(1 > 0, 2 > 1)");
        minimalExpressions.put("OR", "OR(1 < 0, 2 > 1)");
        minimalExpressions.put("NOT", "NOT(false)");
        minimalExpressions.put("IFNULL", "IFNULL(novar, 1)");
        minimalExpressions.put("EQ", "EQ(1, 1)");
        minimalExpressions.put("NE", "NE(1, 2)");
        minimalExpressions.put("GT", "GT(2, 1)");
        minimalExpressions.put("GTE", "GTE(1, 1)");
        minimalExpressions.put("LT", "LT(1, 2)");
        minimalExpressions.put("LTE", "LTE(1, 1)");
        minimalExpressions.put("CONCAT", "CONCAT('a', 'b')");
        minimalExpressions.put("SUBSTRING", "SUBSTRING(text, 0, 2)");
        minimalExpressions.put("LENGTH", "LENGTH(text)");
        minimalExpressions.put("TRIM", "TRIM(' x ')");
        minimalExpressions.put("REPLACE", "REPLACE('a,b', ',', '-')");
        minimalExpressions.put("NOW", "NOW()");
        minimalExpressions.put("TODAY", "TODAY()");
        minimalExpressions.put("DATEADD", "DATEADD(d, 1)");
        minimalExpressions.put("DATEDIFF", "DATEDIFF(d, d)");
        minimalExpressions.put("YEAR", "YEAR(d)");
        minimalExpressions.put("MONTH", "MONTH(d)");
        minimalExpressions.put("SUM", "SUM(seq)");
        minimalExpressions.put("AVG", "AVG(seq)");
        minimalExpressions.put("MAX", "MAX(seq)");
        minimalExpressions.put("MIN", "MIN(seq)");
        minimalExpressions.put("COUNT", "COUNT(seq)");
        minimalExpressions.put("COUNTIF", "COUNTIF(seqA, 'A')");
        minimalExpressions.put("SUMIF", "SUMIF(seqA, 'A', seqAmount)");
        minimalExpressions.put("TO_NUMBER", "TO_NUMBER('3')");
        minimalExpressions.put("TO_STRING", "TO_STRING(3)");
        minimalExpressions.put("TO_DATE", "TO_DATE('2026-11-18')");
        minimalExpressions.put("CURRENT_USER", "CURRENT_USER()");
        minimalExpressions.put("CURRENT_DEPT", "CURRENT_DEPT()");
        minimalExpressions.put("CURRENT_ROLE", "CURRENT_ROLE()");
        minimalExpressions.put("DS_QUERY", "DS_QUERY('src', novar)");

        assertEquals(EXPECTED_ALL.size(), minimalExpressions.size(),
                "41 函数须逐一有最小求值表达式");
        for (String functionCode : EXPECTED_ALL) {
            String expression = minimalExpressions.get(functionCode);
            assertTrue(null != expression, "缺少最小表达式: " + functionCode);
            try {
                Object result = FormulaFunctionsTestSupport.eval(engine, expression, env);
                assertTrue(null != result, "函数应有非 null 求值结果: " + functionCode
                        + "（NOW/TODAY/DS_QUERY 空行数组等均为确定性非 null 值）");
            } catch (RuntimeException e) {
                throw new AssertionError("函数经表达式调用失败: " + functionCode
                        + " expr=" + expression + " err=" + e.getMessage(), e);
            }
        }
    }

    /** SYSTEM 函数会话语义（§6.3 服务端注入，不信任前端传值——函数无入参形态） */
    @Test
    void systemFunctionsReadServerSessionOnly() {
        AviatorEvaluatorInstance engine = FormulaFunctionsTestSupport
                .newEngine(new SystemFunctionProvider());
        assertEquals("张三", FormulaFunctionsTestSupport.eval(engine, "CURRENT_USER()"));
        assertEquals("财务部", FormulaFunctionsTestSupport.eval(engine, "CURRENT_DEPT()"));
        assertEquals("SYS_FORMULA_ADMIN", FormulaFunctionsTestSupport.eval(engine, "CURRENT_ROLE()"));
    }

    /** 方案 §2.1 样例组合形态（DS_QUERY 行数组交集合函数、CONCAT+YEAR） */
    @Test
    void sampleCompositionExpressions() {
        when(datasourceService.execute(any(), any(), any(), any(), any()))
                .thenReturn(new SysDatasourceService.SysDatasourceExecution(
                        List.of(Map.of("fieldAmount", 100), Map.of("fieldAmount", 200)),
                        2, List.of("fieldAmount")));
        AviatorEvaluatorInstance engine = FormulaFunctionsTestSupport
                .newEngine(buildProviders().toArray(FormulaFunctionProvider[]::new));

        Map<String, Object> env = new HashMap<>();
        env.put("amounts", List.of(100, 200));
        assertEquals(300L, FormulaFunctionsTestSupport.eval(engine, "SUM(amounts)", env));
        assertEquals(2L, FormulaFunctionsTestSupport.eval(
                engine, "COUNT(DS_QUERY('calendar.my-created', novar))", env));
        assertEquals("张三-2026", FormulaFunctionsTestSupport.eval(
                engine, "CONCAT(CURRENT_USER(), '-', YEAR('2026-01-01'))", env));
    }
}

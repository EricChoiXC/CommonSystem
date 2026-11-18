package com.eric.base.sys.formula.service;

import com.eric.base.sys.formula.dao.SysFormulaDao;
import com.eric.base.sys.formula.model.SysFormula;
import com.eric.base.sys.formula.request.SysFormulaSimulateRequest;
import com.eric.base.sys.formula.response.SysFormulaSimulateResponse;
import com.eric.base.sys.formula.service.engine.FormulaEngine;
import com.eric.base.sys.formula.service.engine.FormulaFunctionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED;
import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_NOT_FOUND;
import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_SYNTAX_INVALID;
import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 公式模拟计算服务测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖任务 F14-BE-007 验收标准（真实 FormulaEngine + 真实
 *              SysFormulaService（Dao mock 桩），MockedStatic 会话态见
 *              FormulaServiceTestSupport）：
 *              <ul>
 *              <li>内联路径（HTTP /simulate 契约）：方案 §3.2 含税金额样例
 *                  （success + result 130 + resultType NUMBER）、期望类型缺省时按结果
 *                  Java 类型推断（STRING/BOOLEAN/DATE）、执行失败与语法失败以 errors
 *                  数组承载（HTTP 仍 200，success=false 不抛异常）、期望返回类型不合法
 *                  短路（CS_FORMULA_TYPE_MISMATCH）；</li>
 *              <li>命名公式路径（simulateByCode，服务内执行入口）：启用公式按存储
 *                  表达式执行；编码不存在 / 已停用抛 CS_FORMULA_NOT_FOUND（执行 /
 *                  试算侧程序化判定，方案 §3.3 / §5.3）；</li>
 *              <li>安全语义：系统变量（SYS_CURRENT_*）服务端注入优先，调用方同名
 *                  赋值被忽略（方案 §6.3 不信任前端传值）。</li>
 *              </ul>
 */
class SysFormulaSimulateServiceTest extends FormulaServiceTestSupport {

    /** 32 位测试公式 ID（避免 BaseModel.setFieldId 触发重新生成） */
    private static final String FORMULA_ID = "f".repeat(32);
    private static final String FORMULA_CODE = "tax.total";

    /** 方案 §3.2 含税金额样例表达式 */
    private static final String TAX_TOTAL_EXPRESSION = "ROUND(${applyAmount} * ${taxRate}, 2)";

    private SysFormulaDao dao;
    private SysFormulaSimulateService service;

    @BeforeEach
    void setUpService() {
        dao = mock(SysFormulaDao.class);
        FormulaEngine engine = newEngine();
        SysFormulaService formulaService = new SysFormulaService(dao,
                new SysFormulaValidateService(engine),
                new FormulaFunctionRegistry(builtinProviders(datasourceService)));
        service = new SysFormulaSimulateService(engine, formulaService);
    }

    // ---------------------------------------------------------------- 内联路径（HTTP /simulate 契约）

    @Test
    void inlineSimulateReturnsAdaptedNumber() {
        // 方案 §3.2 时序样例：vars = { applyAmount: 1000, taxRate: 0.13 } → 130.00（NUMBER 经 BigDecimal 规整）
        SysFormulaSimulateResponse response = service.simulate(request(
                TAX_TOTAL_EXPRESSION, Map.of("applyAmount", 1000, "taxRate", 0.13), "NUMBER"));

        assertTrue(response.isSuccess());
        assertTrue(response.getErrors().isEmpty());
        assertTrue(response.getResult() instanceof BigDecimal, "NUMBER 结果应经 BigDecimal 规整");
        assertEquals(0, new BigDecimal("130.00").compareTo((BigDecimal) response.getResult()));
        assertEquals("NUMBER", response.getResultType());
    }

    @Test
    void inlineSimulateInfersResultTypeWithoutExpected() {
        SysFormulaSimulateResponse response = service.simulate(request(
                "CONCAT(${deptName}, \"-\", \"A\")", Map.of("deptName", "财务部"), null));

        assertTrue(response.isSuccess());
        assertEquals("财务部-A", response.getResult());
        assertEquals("STRING", response.getResultType(), "期望类型缺省时按结果 Java 类型推断");
    }

    @Test
    void inlineSimulateInfersBooleanAndDateTypes() {
        SysFormulaSimulateResponse boolResponse = service.simulate(request(
                "${amount} > 100", Map.of("amount", 500), null));
        assertTrue(boolResponse.isSuccess());
        assertEquals(Boolean.TRUE, boolResponse.getResult());
        assertEquals("BOOLEAN", boolResponse.getResultType());

        SysFormulaSimulateResponse dateResponse = service.simulate(request("NOW()", Map.of(), null));
        assertTrue(dateResponse.isSuccess());
        assertEquals("DATE", dateResponse.getResultType());
    }

    @Test
    void executionFailureReportedInErrorsWithoutThrowing() {
        // 缺失变量参与算术运算 → 引擎确定性失败 → errors 数组承载（HTTP 仍 200，不抛异常）
        SysFormulaSimulateResponse response = service.simulate(request(
                "${missingVar} + 1", Map.of(), null));

        assertFalse(response.isSuccess());
        assertNull(response.getResult());
        assertNull(response.getResultType());
        assertEquals(1, response.getErrors().size());
        assertEquals(CS_FORMULA_EXECUTE_FAILED, response.getErrors().get(0).getCode());
    }

    @Test
    void syntaxFailureReportedInErrors() {
        SysFormulaSimulateResponse response = service.simulate(request(
                "ROUND(${a} * ${b}, 2", Map.of("a", 1, "b", 2), null));

        assertFalse(response.isSuccess());
        assertEquals(CS_FORMULA_SYNTAX_INVALID, response.getErrors().get(0).getCode());
    }

    @Test
    void invalidExpectedReturnTypeShortCircuits() {
        SysFormulaSimulateResponse response = service.simulate(request(
                "${a} + 1", Map.of("a", 1), "TEXT"));

        assertFalse(response.isSuccess());
        assertEquals(CS_FORMULA_TYPE_MISMATCH, response.getErrors().get(0).getCode());
        assertTrue(response.getErrors().get(0).getMessage().contains("TEXT"));
    }

    @Test
    void blankExpressionReportedAsSyntaxInvalid() {
        SysFormulaSimulateResponse response = service.simulate(request("  ", Map.of(), null));

        assertFalse(response.isSuccess());
        assertEquals(CS_FORMULA_SYNTAX_INVALID, response.getErrors().get(0).getCode());
    }

    // ---------------------------------------------------------------- 命名公式路径（simulateByCode，服务内执行入口）

    @Test
    void simulateByCodeExecutesStoredExpression() {
        when(dao.selectByCode(FORMULA_CODE))
                .thenReturn(storedFormula(FORMULA_CODE, TAX_TOTAL_EXPRESSION, "NUMBER", true));

        SysFormulaSimulateResponse response = service.simulateByCode(FORMULA_CODE,
                Map.of("applyAmount", 1000, "taxRate", 0.13), "NUMBER");

        assertTrue(response.isSuccess());
        assertEquals(0, new BigDecimal("130.00").compareTo((BigDecimal) response.getResult()));
        assertEquals("NUMBER", response.getResultType());
    }

    @Test
    void simulateByCodeRejectsMissingFormula() {
        when(dao.selectByCode(FORMULA_CODE)).thenReturn(null);

        assertFormulaError(() -> service.simulateByCode(FORMULA_CODE, Map.of(), null), CS_FORMULA_NOT_FOUND);
    }

    @Test
    void simulateByCodeRejectsDisabledFormula() {
        // 命名公式停用后执行报 CS_FORMULA_NOT_FOUND（供执行/试算侧判断，方案 §3.3 / §5.3）
        when(dao.selectByCode(FORMULA_CODE))
                .thenReturn(storedFormula(FORMULA_CODE, TAX_TOTAL_EXPRESSION, "NUMBER", false));

        String message = assertFormulaError(() -> service.simulateByCode(FORMULA_CODE, Map.of(), null),
                CS_FORMULA_NOT_FOUND);
        assertTrue(message.contains("停用"), "停用场景消息应说明已停用: " + message);
    }

    // ---------------------------------------------------------------- 安全语义（方案 §6.3）

    @Test
    void systemVariableNotOverriddenByCallerVars() {
        // SYS_CURRENT_* 服务端注入优先：调用方同名赋值被忽略（不信任前端传值）
        SysFormulaSimulateResponse response = service.simulate(request(
                "${SYS_CURRENT_USER_ID}", Map.of("SYS_CURRENT_USER_ID", "fake-from-caller"), null));

        assertTrue(response.isSuccess());
        assertEquals(USER_ID, response.getResult(), "系统变量应取服务端会话值，调用方赋值被忽略");
        assertEquals("STRING", response.getResultType());
    }

    // ---------------------------------------------------------------- 桩构造

    private static SysFormulaSimulateRequest request(String expression, Map<String, Object> vars,
                                                     String expectedReturnType) {
        SysFormulaSimulateRequest request = new SysFormulaSimulateRequest();
        request.setExpression(expression);
        request.setVars(vars);
        request.setExpectedReturnType(expectedReturnType);
        return request;
    }

    /** 存储态命名公式行 */
    private static SysFormula storedFormula(String code, String expression, String returnType, boolean enable) {
        SysFormula row = new SysFormula();
        row.setFieldId(FORMULA_ID);
        row.setFieldCode(code);
        row.setFieldName("含税金额");
        row.setFieldType("FIELD");
        row.setFieldExpression(expression);
        row.setFieldReturnType(returnType);
        row.setFieldEnable(enable);
        row.setFieldCreatorId(USER_ID);
        row.setFieldCreateTime(new Date());
        return row;
    }
}

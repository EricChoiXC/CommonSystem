package com.eric.base.sys.formula.service;

import com.eric.base.sys.formula.model.SysFormulaErrorDo;
import com.eric.base.sys.formula.request.SysFormulaValidateRequest;
import com.eric.base.sys.formula.response.SysFormulaValidateResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_FUNCTION_NOT_FOUND;
import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_SYNTAX_INVALID;
import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH;
import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公式校验服务测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖任务 F14-BE-007 验收标准（真实 FormulaEngine + 九类内置函数，
 *              MockedStatic 会话态见 FormulaServiceTestSupport）：宽松模式（vars 缺省 /
 *              空列表——变量视为未知，仅语法与函数校验，引用未声明变量不报错）、
 *              严格模式（引用 ⇄ 声明一致性 CS_FORMULA_VARIABLE_INVALID、类型声明合法性
 *              CS_FORMULA_TYPE_MISMATCH）、语法错误 / 未注册函数以 errors 数组承载
 *              （HTTP 仍 200，不抛异常）、期望返回类型不合法短路、dependencies 失败态
 *              兜底提取、errors 元素 { code, message } 形态。
 */
class SysFormulaValidateServiceTest extends FormulaServiceTestSupport {

    private final SysFormulaValidateService service = new SysFormulaValidateService(newEngine());

    // ---------------------------------------------------------------- 宽松模式

    @Test
    void lenientModePassesUnknownVars() {
        // vars 缺省：变量视为未知，仅语法与函数校验——引用未声明变量不报错（方案 §5.2）
        SysFormulaValidateResponse response = service.validate(request(
                "ROUND(${applyAmount} * ${taxRate}, 2)", "NUMBER", (SysFormulaValidateRequest.Var[]) null));

        assertTrue(response.isSyntax());
        assertTrue(response.getErrors().isEmpty(), "宽松模式引用未知变量不应报错");
        assertEquals(List.of("applyAmount", "taxRate"), response.getDependencies());
        assertEquals("NUMBER", response.getReturnType());
    }

    @Test
    void lenientModeWithEmptyVarsList() {
        // vars 空列表与缺省同语义（宽松）
        SysFormulaValidateResponse response = service.validate(request(
                "CONCAT(${deptName}, \"-\", ${applicantName})", "STRING", List.of()));

        assertTrue(response.isSyntax());
        assertTrue(response.getErrors().isEmpty());
        assertEquals(List.of("deptName", "applicantName"), response.getDependencies());
    }

    @Test
    void lenientModeWithMixedCaseExpectedReturnType() {
        // expectedReturnType 大小写不敏感，响应回显规整后的枚举名
        SysFormulaValidateResponse response = service.validate(request(
                "${amount} > 100", "boolean", (SysFormulaValidateRequest.Var[]) null));

        assertTrue(response.isSyntax());
        assertEquals("BOOLEAN", response.getReturnType());
    }

    @Test
    void returnTypeAbsentWhenExpectedNotProvided() {
        SysFormulaValidateResponse response = service.validate(request(
                "${amount} + 1", null, (SysFormulaValidateRequest.Var[]) null));

        assertTrue(response.isSyntax());
        assertNull(response.getReturnType());
    }

    // ---------------------------------------------------------------- 严格模式

    @Test
    void strictModePassesConsistentDeclarations() {
        SysFormulaValidateResponse response = service.validate(request(
                "ROUND(${applyAmount} * ${taxRate}, 2)", "NUMBER", List.of(
                        var("applyAmount", "NUMBER"), var("taxRate", "NUMBER"))));

        assertTrue(response.isSyntax());
        assertTrue(response.getErrors().isEmpty());
        assertEquals(List.of("applyAmount", "taxRate"), response.getDependencies());
    }

    @Test
    void strictModeRejectsUndeclaredReference() {
        SysFormulaValidateResponse response = service.validate(request(
                "${applyAmount} * ${taxRate}", null, List.of(var("applyAmount", "NUMBER"))));

        assertFalse(response.isSyntax());
        assertEquals(1, response.getErrors().size());
        assertEquals(CS_FORMULA_VARIABLE_INVALID, response.getErrors().get(0).getCode());
        assertTrue(response.getErrors().get(0).getMessage().contains("taxRate"));
    }

    @Test
    void strictModeRejectsUnusedDeclaration() {
        SysFormulaValidateResponse response = service.validate(request(
                "${applyAmount} + 1", null, List.of(
                        var("applyAmount", "NUMBER"), var("unusedVar", "STRING"))));

        assertFalse(response.isSyntax());
        assertEquals(CS_FORMULA_VARIABLE_INVALID, response.getErrors().get(0).getCode());
        assertTrue(response.getErrors().get(0).getMessage().contains("unusedVar"));
    }

    @Test
    void strictModeRejectsInvalidVarType() {
        SysFormulaValidateResponse response = service.validate(request(
                "${applyAmount} > 100", null, List.of(var("applyAmount", "TEXT"))));

        assertFalse(response.isSyntax());
        assertEquals(CS_FORMULA_TYPE_MISMATCH, response.getErrors().get(0).getCode());
        assertTrue(response.getErrors().get(0).getMessage().contains("applyAmount"));
    }

    // ---------------------------------------------------------------- errors 数组形态（HTTP 仍 200）

    @Test
    void syntaxErrorReportedInErrorsWithoutThrowing() {
        // 语法失败不抛异常：errors 承载 CS_FORMULA_SYNTAX_INVALID（附引擎错误信息），
        // dependencies 兜底提取供前端展示变量引用面
        SysFormulaValidateResponse response = service.validate(request(
                "ROUND(${applyAmount} * ${taxRate}, 2", null, (SysFormulaValidateRequest.Var[]) null));

        assertFalse(response.isSyntax());
        assertEquals(1, response.getErrors().size());
        SysFormulaErrorDo error = response.getErrors().get(0);
        assertEquals(CS_FORMULA_SYNTAX_INVALID, error.getCode());
        assertNotNull(error.getMessage());
        assertEquals(List.of("applyAmount", "taxRate"), response.getDependencies(), "失败态 dependencies 兜底提取");
    }

    @Test
    void unregisteredFunctionReportedInErrors() {
        SysFormulaValidateResponse response = service.validate(request(
                "NOT_A_FUNCTION(${amount})", null, (SysFormulaValidateRequest.Var[]) null));

        assertFalse(response.isSyntax());
        assertEquals(CS_FORMULA_FUNCTION_NOT_FOUND, response.getErrors().get(0).getCode());
    }

    @Test
    void invalidExpectedReturnTypeShortCircuits() {
        SysFormulaValidateResponse response = service.validate(request(
                "${amount} + 1", "TEXT", (SysFormulaValidateRequest.Var[]) null));

        assertFalse(response.isSyntax());
        assertEquals(1, response.getErrors().size());
        assertEquals(CS_FORMULA_TYPE_MISMATCH, response.getErrors().get(0).getCode());
        assertTrue(response.getErrors().get(0).getMessage().contains("TEXT"));
    }

    @Test
    void blankExpressionReportedAsSyntaxInvalid() {
        SysFormulaValidateResponse response = service.validate(request("  ", null, (SysFormulaValidateRequest.Var[]) null));

        assertFalse(response.isSyntax());
        assertEquals(CS_FORMULA_SYNTAX_INVALID, response.getErrors().get(0).getCode());
    }

    @Test
    void nullRequestReportedAsSyntaxInvalid() {
        SysFormulaValidateResponse response = service.validate(null);

        assertFalse(response.isSyntax());
        assertEquals(CS_FORMULA_SYNTAX_INVALID, response.getErrors().get(0).getCode());
    }

    @Test
    void errorElementCarriesCodeAndMessage() {
        // errors 元素 { code, message } 契约：code ∈ CS_FORMULA_* 且 message 非空
        SysFormulaValidateResponse response = service.validate(request(
                "${1bad}", null, (SysFormulaValidateRequest.Var[]) null));

        assertFalse(response.isSyntax());
        SysFormulaErrorDo error = response.getErrors().get(0);
        assertTrue(error.getCode().startsWith("CS_FORMULA_"), "错误码应为 CS_FORMULA_*: " + error.getCode());
        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().isBlank());
    }

    // ---------------------------------------------------------------- 桩构造

    private static SysFormulaValidateRequest request(String expression, String expectedReturnType,
                                                     List<SysFormulaValidateRequest.Var> vars) {
        SysFormulaValidateRequest request = new SysFormulaValidateRequest();
        request.setExpression(expression);
        request.setExpectedReturnType(expectedReturnType);
        request.setVars(vars);
        return request;
    }

    private static SysFormulaValidateRequest request(String expression, String expectedReturnType,
                                                     SysFormulaValidateRequest.Var... vars) {
        return request(expression, expectedReturnType,
                null == vars ? null : List.of(vars));
    }

    private static SysFormulaValidateRequest.Var var(String code, String type) {
        SysFormulaValidateRequest.Var var = new SysFormulaValidateRequest.Var();
        var.setCode(code);
        var.setType(type);
        return var;
    }
}

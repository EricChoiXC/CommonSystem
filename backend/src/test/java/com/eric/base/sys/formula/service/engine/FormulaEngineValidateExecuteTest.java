package com.eric.base.sys.formula.service.engine;

import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.properties.SysFormulaConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * validate / execute 错误语义与类型适配用例（F14-BE-004）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖：宽松/严格校验、未知变量语义、类型不符 CS_FORMULA_TYPE_MISMATCH、
 *              编译错误行号透出、上下文不可变与系统变量服务端优先。
 */
class FormulaEngineValidateExecuteTest extends FormulaEngineTestSupport {

    /* ==================== validate：宽松 vs 严格 ==================== */

    /** 宽松模式：语法 + 函数存在性 + 变量码合法性；未声明变量不阻断 */
    @Test
    void validateLooseAllowsUndeclaredVars() {
        FormulaEngine engine = newEngine();
        assertEquals(List.of("applyAmount", "taxRate"),
                engine.validate("ROUND(${applyAmount} * ${taxRate}, 2)", null,
                        SysFormulaReturnType.NUMBER, false));
        // strict=false 时 declaredVars 给了也只核对函数与语法
        assertEquals(List.of("applyAmount"),
                engine.validate("ROUND(${applyAmount} * ${applyAmount}, 2)", Map.of("taxRate", "NUMBER"),
                        null, false));
    }

    /** 函数存在性：未注册函数 validate 即报（Aviator 编译期不查函数面，引擎前置收口） */
    @Test
    void validateRejectsUnknownFunction() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.validate("NOPE(${a}) + 1", null, null, false),
                SysFormulaConstants.CS_FORMULA_FUNCTION_NOT_FOUND);
    }

    /** 严格模式：引用未声明变量 → CS_FORMULA_VARIABLE_INVALID */
    @Test
    void validateStrictRejectsUndeclaredReference() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.validate("ROUND(${applyAmount} * ${taxRate}, 2)",
                        Map.of("applyAmount", "NUMBER"), SysFormulaReturnType.NUMBER, true),
                SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID);
    }

    /** 严格模式：声明未引用变量 → CS_FORMULA_VARIABLE_INVALID */
    @Test
    void validateStrictRejectsUnusedDeclaration() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.validate("ROUND(${applyAmount}, 2)",
                        Map.of("applyAmount", "NUMBER", "taxRate", "NUMBER"), null, true),
                SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID);
    }

    /** 严格模式：类型声明非法 → CS_FORMULA_TYPE_MISMATCH */
    @Test
    void validateStrictRejectsIllegalTypeDeclaration() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.validate("ROUND(${applyAmount}, 2)",
                        Map.of("applyAmount", "MONEY"), null, true),
                SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH);
    }

    /** 表达式长度上限 → CS_FORMULA_EXPRESSION_TOO_LONG（validate 与 execute 同门禁） */
    @Test
    void expressionTooLongRejected() {
        FormulaEngine engine = newEngine();
        String longExpression = "1 + " + "1 + ".repeat(1500) + "1";
        assertFormulaError(() -> engine.validate(longExpression, null, null, false),
                SysFormulaConstants.CS_FORMULA_EXPRESSION_TOO_LONG);
        assertFormulaError(() -> engine.execute(longExpression, emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_EXPRESSION_TOO_LONG);
    }

    /* ==================== execute：缺失变量 / 未知函数 / 类型适配 ==================== */

    /** 执行期缺失变量参与算术 → CS_FORMULA_EXECUTE_FAILED（Aviator nil 参与运算确定性失败） */
    @Test
    void executeMissingVariableFailsDeterministically() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.execute("${missing} + 1", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED);
    }

    /** 执行期未知函数 → CS_FORMULA_FUNCTION_NOT_FOUND（validate 漏网/直接执行的兜底） */
    @Test
    void executeUnknownFunctionTranslates() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.execute("NOPE(1) + 1", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_FUNCTION_NOT_FOUND);
    }

    /** 编译错误消息含行号与原因（多行表达式定位 lineNumber: 2） */
    @Test
    void compileErrorCarriesLineNumber() {
        FormulaEngine engine = newEngine();
        String message = assertFormulaError(
                () -> engine.execute("1 + 1\nb * + 2", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_SYNTAX_INVALID);
        assertTrue(message.contains("lineNumber: 2"), "编译错误应透出 Aviator 行号: " + message);
        assertTrue(message.contains("b * + 2"), "应附原始文本出错行内容: " + message);
    }

    /** 空白表达式 → CS_FORMULA_SYNTAX_INVALID */
    @Test
    void blankExpressionRejected() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.execute("   ", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_SYNTAX_INVALID);
        assertFormulaError(() -> engine.validate(null, null, null, false),
                SysFormulaConstants.CS_FORMULA_SYNTAX_INVALID);
    }

    /** 类型适配：NUMBER → BigDecimal 规整（整型/浮点/函数结果各形态） */
    @Test
    void adaptNumberToBigDecimal() {
        FormulaEngine engine = newEngine();
        assertInstanceOf(BigDecimal.class,
                engine.execute("${a} + ${b}", contextOf(vars("a", 1, "b", 2)), SysFormulaReturnType.NUMBER));
        assertEquals(new BigDecimal("0.1"),
                engine.execute("0.1", emptyContext(), SysFormulaReturnType.NUMBER),
                "浮点经十进制最短表示规整，无二进制尾巴");
        assertEquals(new BigDecimal("7"),
                engine.execute("TO_NUMBER('7')", emptyContext(), SysFormulaReturnType.NUMBER));
    }

    /** 类型不符 → CS_FORMULA_TYPE_MISMATCH（各目标类型） */
    @Test
    void typeMismatchRejected() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.execute("CONCAT('a', 'b')", emptyContext(), SysFormulaReturnType.NUMBER),
                SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH);
        assertFormulaError(() -> engine.execute("1 + 1", emptyContext(), SysFormulaReturnType.STRING),
                SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH);
        assertFormulaError(() -> engine.execute("1 + 1", emptyContext(), SysFormulaReturnType.BOOLEAN),
                SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH);
        assertFormulaError(() -> engine.execute("1 + 1", emptyContext(), SysFormulaReturnType.ARRAY),
                SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH);
        assertFormulaError(() -> engine.execute("1 + 1", emptyContext(), SysFormulaReturnType.DATE),
                SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH);
    }

    /** expectedReturnType = null 跳过类型校验（原样返回） */
    @Test
    void nullExpectedReturnTypeSkipsAdaptation() {
        FormulaEngine engine = newEngine();
        assertEquals("ab", engine.execute("CONCAT('a', 'b')", emptyContext(), null));
        assertEquals(2L, engine.execute("1 + 1", emptyContext(), null));
    }

    /** BOOLEAN/STRING/DATE/ARRAY 适配正例 */
    @Test
    void adaptOtherTypes() {
        FormulaEngine engine = newEngine();
        assertEquals(Boolean.TRUE, engine.execute("1 < 2", emptyContext(), SysFormulaReturnType.BOOLEAN));
        assertEquals("ab", engine.execute("CONCAT('a', 'b')", emptyContext(), SysFormulaReturnType.STRING));
        assertInstanceOf(java.util.Date.class,
                engine.execute("TODAY()", emptyContext(), SysFormulaReturnType.DATE));
        assertInstanceOf(List.class,
                engine.execute("DS_QUERY('src', novar)", emptyContext(), SysFormulaReturnType.ARRAY));
    }

    /* ==================== 编译缓存 ==================== */

    /** 同一表达式重复执行命中编译缓存（结果一致即语义无损；容量内反复进出不抛错） */
    @Test
    void compileCacheReusedAcrossExecutions() {
        FormulaEngine engine = newEngine();
        for (int i = 0; i < 300; i++) {
            Object result = engine.execute("ROUND(${amount} + " + (i % 50) + ", 2)",
                    contextOf(vars("amount", 1)), SysFormulaReturnType.NUMBER);
            assertEquals(0, ((BigDecimal) result).compareTo(BigDecimal.valueOf((i % 50) + 1)));
        }
        // 超容量轮换（LRU 淘汰不抛错、结果仍正确）
        for (int i = 0; i < 300; i++) {
            Object result = engine.execute("1 + " + i, emptyContext(), SysFormulaReturnType.NUMBER);
            assertEquals(i + 1, ((BigDecimal) result).intValue());
        }
    }

    /* ==================== FormulaContext ==================== */

    /** 上下文不可变：getVars() put 抛 UnsupportedOperationException；构造后改原 Map 不影响上下文 */
    @Test
    void contextImmutable() {
        Map<String, Object> callerVars = new java.util.HashMap<>();
        callerVars.put("a", 1);
        FormulaContext context = new FormulaContext(callerVars);
        assertThrows(UnsupportedOperationException.class, () -> context.getVars().put("b", 2));
        assertThrows(UnsupportedOperationException.class, () -> context.getSystemVariables().put("x", 2));
        callerVars.put("a", 999);
        assertEquals(1, context.getVariable("a"), "构造后调用方修改原 Map 不应影响上下文");
    }

    /** 系统变量服务端注入：调用方同名变量被忽略（服务端优先） */
    @Test
    void systemVariablesWinOverCallerVars() {
        Map<String, Object> injected = Map.of(
                FormulaContext.SYS_CURRENT_USER_ID, "server-user-id",
                FormulaContext.SYS_CURRENT_ROLE, "SYS_FORMULA_ADMIN");
        FormulaContext context = new FormulaContext(
                Map.of(FormulaContext.SYS_CURRENT_USER_ID, "caller-forged-id"), injected);
        assertEquals("server-user-id", context.getVariable(FormulaContext.SYS_CURRENT_USER_ID),
                "系统变量不可被调用方 vars 覆盖");
        assertEquals("server-user-id", context.getVars().get(FormulaContext.SYS_CURRENT_USER_ID));
        assertEquals("SYS_FORMULA_ADMIN", context.getVariable(FormulaContext.SYS_CURRENT_ROLE));
    }

    /** 生产构造：UserUtil 会话系统变量注入（用户/部门 ID、角色集合）；未登录不注入 */
    @Test
    void productionContextInjectsSessionSystemVariables() {
        FormulaContext context = new FormulaContext(Map.of());
        assertEquals("1".repeat(32), context.getVariable(FormulaContext.SYS_CURRENT_USER_ID));
        assertEquals("2".repeat(32), context.getVariable(FormulaContext.SYS_CURRENT_DEPT_ID));
        assertEquals("SYS_FORMULA_ADMIN", context.getVariable(FormulaContext.SYS_CURRENT_ROLE));
        // 系统变量在公式中可用：CONCAT 引用 SYS_CURRENT_ROLE
        FormulaEngine engine = newEngine();
        assertEquals("角色:SYS_FORMULA_ADMIN", engine.execute("CONCAT('角色:', ${SYS_CURRENT_ROLE})",
                context, SysFormulaReturnType.STRING));
        // 未登录：不注入系统变量，引用得缺失变量（CONCAT 文本化 null 为空串）
        userUtilMock.when(UserUtil::getCurrentUser).thenReturn(null);
        FormulaContext anonymous = new FormulaContext(Map.of());
        assertNull(anonymous.getVariable(FormulaContext.SYS_CURRENT_USER_ID));
        assertEquals("角色:", engine.execute("CONCAT('角色:', ${SYS_CURRENT_ROLE})",
                anonymous, SysFormulaReturnType.STRING));
    }

    /** 引擎执行消费系统变量参与比较运算（v1.7 条件分支预演） */
    @Test
    void systemVariablesUsableInLogic() {
        FormulaEngine engine = newEngine();
        Object result = engine.execute(
                "IF(${SYS_CURRENT_ROLE} == \"SYS_FORMULA_ADMIN\", \"管理员\", \"普通\")",
                new FormulaContext(Map.of()), SysFormulaReturnType.STRING);
        assertEquals("管理员", result);
    }

    /** 常量口径防漂移：编译缓存容量与超时常量在位 */
    @Test
    void constantsAligned() {
        assertEquals(256, SysFormulaConstants.COMPILE_CACHE_CAPACITY);
        assertEquals(5000L, SysFormulaConstants.EXECUTE_TIMEOUT_MILLIS);
    }
}

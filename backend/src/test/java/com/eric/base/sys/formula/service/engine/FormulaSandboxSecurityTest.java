package com.eric.base.sys.formula.service.engine;

import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.properties.SysFormulaConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 沙箱裁剪与资源限制安全用例（F14-BE-005，方案 §6 安全设计 1/2/5/6、§3.2 时序 3/6）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 验证 FormulaEngine 沙箱裁剪面（Options.FEATURE_SET 关闭
 *              StaticMethods/StaticFields/NewInstance/Use/Module + ENABLE_PROPERTY_SYNTAX_SUGAR
 *              关闭 + ALLOWED_CLASS_SET 类白名单）与资源限制前置检查链
 *              （表达式长度 → 变量个数 → 变量值序列化体积）及循环上限封顶。
 *              禁用类调用实测拒绝语义（Aviator 5.4.3，探测结论固化）：
 *              <ul>
 *              <li>静态方法调用（System.currentTimeMillis / Class.forName）：
 *                  StaticMethods 关闭后函数表无注册 → 执行期 FunctionNotFoundException
 *                  → CS_FORMULA_FUNCTION_NOT_FOUND（validate 期由函数存在性检查前置拦截）；</li>
 *              <li>new 构造（new java.io.File）与 use 引类：NewInstance/Use 关闭，
 *                  编译期 UnsupportedFeatureException → CS_FORMULA_SYNTAX_INVALID；</li>
 *              <li># 静态方法引用（#Long.parseLong）：FunctionNotFoundException →
 *                  CS_FORMULA_FUNCTION_NOT_FOUND；# 静态字段引用（#Long.MAX_VALUE）
 *                  在属性语法糖关闭下为 nil 语义（值不可达、无异常）；</li>
 *              <li>循环超限（for/reduce 超 MAX_LOOP_COUNT）：Overflow max loop count
 *                  ExpressionRuntimeException → CS_FORMULA_EXECUTE_FAILED。</li>
 *              </ul>
 *              超时用例已在 {@link FormulaEngineTimeoutTest} 覆盖，此处不重复。
 */
class FormulaSandboxSecurityTest extends FormulaEngineTestSupport {

    /* ==================== 资源限制前置检查链（长度 → 变量个数 → 序列化体积） ==================== */

    /** 检查链第一道：表达式长度超上限 → CS_FORMULA_EXPRESSION_TOO_LONG（execute / validate 同门禁） */
    @Test
    void expressionTooLongRejected() {
        FormulaEngine engine = newEngine();
        String longExpression = "1 + " + "1 + ".repeat(1500) + "1";
        assertFormulaError(() -> engine.execute(longExpression, emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_EXPRESSION_TOO_LONG);
        assertFormulaError(() -> engine.validate(longExpression, null, null, false),
                SysFormulaConstants.CS_FORMULA_EXPRESSION_TOO_LONG);
    }

    /** 检查链第二道：引用变量个数超上限 → CS_FORMULA_EXECUTE_FAILED（消息说明变量个数超限） */
    @Test
    void referenceCountOverLimitRejected() {
        FormulaEngine engine = newEngine();
        StringBuilder expression = new StringBuilder();
        for (int i = 0; i <= SysFormulaConstants.MAX_VARIABLE_COUNT; i++) {
            if (i > 0) {
                expression.append(" + ");
            }
            expression.append("${v").append(i).append("}");
        }
        String text = expression.toString();
        String message = assertFormulaError(() -> engine.execute(text, emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED);
        assertTrue(message.contains("引用变量个数超上限"),
                "消息应说明变量个数超限: " + message);
        assertTrue(message.contains(String.valueOf(SysFormulaConstants.MAX_VARIABLE_COUNT)),
                "消息应含上限值: " + message);
        assertFormulaError(() -> engine.validate(text, null, null, false),
                SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED);
    }

    /** 检查链第三道：变量值序列化体积超上限 → CS_FORMULA_EXECUTE_FAILED（Jackson 测长） */
    @Test
    void varsSerializeSizeOverLimitRejected() {
        FormulaEngine engine = newEngine();
        Map<String, Object> oversized = new HashMap<>();
        oversized.put("bigPayload", "x".repeat(SysFormulaConstants.MAX_VARS_SERIALIZE_BYTES));
        String message = assertFormulaError(() -> engine.execute("${bigPayload}", contextOf(oversized), null),
                SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED);
        assertTrue(message.contains("变量值序列化体积超上限"),
                "消息应说明变量值体积超限: " + message);
    }

    /** 体积门禁不误伤：大但合规的 vars（含日期类型，JavaTimeModule 兼容）正常执行 */
    @Test
    void largeButLegalVarsPassSizeGate() {
        FormulaEngine engine = newEngine();
        Map<String, Object> legal = new HashMap<>();
        legal.put("text", "y".repeat(60000));
        legal.put("bizDate", LocalDate.of(2026, 11, 18));
        Object result = engine.execute("LENGTH(${text}) + YEAR(${bizDate})",
                contextOf(legal), SysFormulaReturnType.NUMBER);
        assertEquals(0, new BigDecimal("62026").compareTo((BigDecimal) result));
    }

    /* ==================== 禁用类调用面（方案 §6.1） ==================== */

    /** 静态方法调用：System.currentTimeMillis() → 拒绝（validate 前置 + execute 兜底同错误码） */
    @Test
    void staticMethodCallRejected() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.validate("System.currentTimeMillis() > 0", null, null, false),
                SysFormulaConstants.CS_FORMULA_FUNCTION_NOT_FOUND);
        assertFormulaError(() -> engine.execute("System.currentTimeMillis() > 0", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_FUNCTION_NOT_FOUND);
    }

    /** new 构造语法：new java.io.File('/tmp') → 编译期拒绝 CS_FORMULA_SYNTAX_INVALID */
    @Test
    void newInstanceSyntaxRejected() {
        FormulaEngine engine = newEngine();
        String message = assertFormulaError(
                () -> engine.execute("new java.io.File('/tmp')", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_SYNTAX_INVALID);
        assertTrue(message.contains("NewInstance") || message.contains("not enabled"),
                "应透出 Feature 关闭信息: " + message);
    }

    /** # 静态方法引用形态：#Long.parseLong('123') → 执行期拒绝 CS_FORMULA_FUNCTION_NOT_FOUND */
    @Test
    void hashStaticMethodRefRejected() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.execute("#Long.parseLong('123')", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_FUNCTION_NOT_FOUND);
    }

    /** # 静态字段引用形态：#Long.MAX_VALUE 在沙箱下值不可达（nil 语义，不泄漏字段值） */
    @Test
    void hashStaticFieldRefYieldsNil() {
        FormulaEngine engine = newEngine();
        assertNull(engine.execute("#Long.MAX_VALUE", emptyContext(), null),
                "# 静态字段引用应不可达（nil），不得泄漏字段值");
    }

    /** 反射形态：Class.forName('java.lang.Runtime') → 拒绝 CS_FORMULA_FUNCTION_NOT_FOUND */
    @Test
    void reflectionClassForNameRejected() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.execute("Class.forName('java.lang.Runtime') != nil", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_FUNCTION_NOT_FOUND);
    }

    /** use 引类语句：use java.lang.Runtime → 编译期拒绝 CS_FORMULA_SYNTAX_INVALID */
    @Test
    void useImportStatementRejected() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.execute("use java.lang.Runtime; 1", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_SYNTAX_INVALID);
    }

    /** Runtime 实例逃逸形态：Runtime.getRuntime() → 拒绝（静态方法面已关闭） */
    @Test
    void runtimeEscapeAttemptRejected() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.execute("Runtime.getRuntime() != nil", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_FUNCTION_NOT_FOUND);
    }

    /* ==================== 循环上限封顶（MAX_LOOP_COUNT） ==================== */

    /** for 循环迭代超 MAX_LOOP_COUNT → CS_FORMULA_EXECUTE_FAILED（确定性，消息含 loop 超限信息） */
    @Test
    void loopStatementOverflowFails() {
        FormulaEngine engine = newEngine();
        String message = assertFormulaError(
                () -> engine.execute("s = 0; for x in range(0, 50000) { s = s + 1 }; return s;",
                        emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED);
        assertTrue(message.contains("loop"), "应透出 Aviator 循环超限信息: " + message);
    }

    /** 变量 seq 循环（reduce）超 MAX_LOOP_COUNT → CS_FORMULA_EXECUTE_FAILED（确定性） */
    @Test
    void seqReduceOverflowFails() {
        FormulaEngine engine = newEngine();
        List<Integer> big = new ArrayList<>();
        for (int i = 0; i < 20000; i++) {
            big.add(i % 10);
        }
        assertFormulaError(() -> engine.execute("reduce(${seq}, +, 0)",
                contextOf(Map.of("seq", big)), null),
                SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED);
    }

    /* ==================== 合法面回归（沙箱不误伤注册函数与普通表达式） ==================== */

    /** 方案 §2.1 样例与注册函数在沙箱下语义不变（六样例全量回归见 FormulaEngineSampleTest） */
    @Test
    void legalExpressionFaceUnaffected() {
        FormulaEngine engine = newEngine();
        Object rounded = engine.execute("ROUND(${applyAmount} * ${taxRate}, 2)",
                contextOf(vars("applyAmount", 1000, "taxRate", 0.13)), SysFormulaReturnType.NUMBER);
        assertEquals(0, new BigDecimal("130").compareTo((BigDecimal) rounded));
        Object passed = engine.execute("IF(${applyAmount} > 10000, \"需部门审批\", \"无需审批\")",
                contextOf(vars("applyAmount", 15000)), SysFormulaReturnType.STRING);
        assertEquals("需部门审批", passed);
        Object sum = engine.execute("SUM(${expenseDetails.fieldAmount})",
                contextOf(vars("expenseDetails", List.of(
                        Map.of("fieldAmount", 100),
                        Map.of("fieldAmount", 200.5),
                        Map.of("fieldAmount", 99.5)))),
                SysFormulaReturnType.NUMBER);
        assertEquals(0, new BigDecimal("400").compareTo((BigDecimal) sum));
    }

    /** 表达式脚本面（赋值 / for 循环 / return / lambda / seq 函数）在沙箱下保留 */
    @Test
    void scriptFaceStillWorks() {
        FormulaEngine engine = newEngine();
        Object loopSum = engine.execute("s = 0; for x in ${seq} { s = s + x }; return s;",
                contextOf(Map.of("seq", List.of(1, 2, 3))), null);
        assertEquals(6L, ((Number) loopSum).longValue());
        Object lambda = engine.execute("f = lambda(x) -> x * 2 end; f(21)", emptyContext(), null);
        assertEquals(42L, ((Number) lambda).longValue());
        Object projected = engine.execute("COUNT(${details.amount})",
                contextOf(vars("details", List.of(
                        Map.of("amount", 1), Map.of("amount", 2)))),
                SysFormulaReturnType.NUMBER);
        assertEquals(2L, ((Number) projected).longValue());
    }

    /* ==================== 白名单常量固化 ==================== */

    /** 类白名单常量：语义必需类在列、System/Runtime/IO/网络/反射/脚本类一律不在列 */
    @Test
    void whitelistConstantGuardsDangerousClasses() {
        Set<Class<?>> allowed = SysFormulaConstants.SANDBOX_ALLOWED_CLASSES;
        assertTrue(allowed.contains(BigDecimal.class));
        assertTrue(allowed.contains(java.math.BigInteger.class));
        assertTrue(allowed.contains(java.util.Date.class));
        assertTrue(allowed.contains(LocalDate.class));
        assertTrue(allowed.contains(java.time.LocalDateTime.class));
        assertTrue(allowed.contains(String.class));
        assertTrue(allowed.contains(List.class));
        assertTrue(allowed.contains(Map.class));
        List<String> bannedPrefixes = List.of(
                "java.lang.System", "java.lang.Runtime", "java.lang.Process", "java.lang.Thread",
                "java.lang.ClassLoader", "java.lang.reflect", "javax.script",
                "java.io.", "java.nio.", "java.net.", "jdk.", "sun.", "com.sun.");
        for (Class<?> clazz : allowed) {
            for (String prefix : bannedPrefixes) {
                assertFalse(clazz.getName().startsWith(prefix),
                        "白名单不得包含危险类: " + clazz.getName());
            }
        }
    }
}

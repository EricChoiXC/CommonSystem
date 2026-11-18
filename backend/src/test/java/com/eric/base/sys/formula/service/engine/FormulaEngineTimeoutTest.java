package com.eric.base.sys.formula.service.engine;

import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.properties.SysFormulaConstants;
import com.eric.base.sys.formula.service.engine.functions.FormulaFunctions;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorLong;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 受限执行与超时用例（F14-BE-004，R04 风险缓解验证）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 超时以注入的小超时值 + 测试专用慢函数（注册进测试 Registry，不污染内置函数）
 *              触发，不留真实等待（方案建议的「大 seq 循环 reduce」在 MAX_LOOP_COUNT=10000
 *              封顶下必然先触发循环超限而非超时，故改用慢函数注入，属对方案的确定性偏差）；
 *              另覆盖循环上限触发 CS_FORMULA_EXECUTE_FAILED 与独立执行线程名。
 */
class FormulaEngineTimeoutTest extends FormulaEngineTestSupport {

    /** 测试专用慢函数（SLOW_DOUBLE：sleep 后返回；执行体仅测试用） */
    public static class SlowDoubleFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "SLOW_DOUBLE";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw FormulaFunctions.executeError("SLOW_DOUBLE 被中断");
            }
            Number value = FormulaFunctions.requireNumber(arg.getValue(env), "SLOW_DOUBLE", "arg");
            return AviatorLong.valueOf(value.longValue() * 2);
        }
    }

    /** 测试专用慢函数 Provider */
    private FormulaFunctionProvider slowProvider() {
        return providerOf(FormulaFunctions.definition("SLOW_DOUBLE", "慢函数（测试用）",
                SysFormulaFunctionCategory.SYSTEM, "sleep 500ms 后返回 2 倍值，供超时用例注入",
                SysFormulaReturnType.NUMBER, new SlowDoubleFunction(),
                FormulaFunctions.param("arg", "NUMBER", true, null)));
    }

    /** 超时：小超时值 + 慢函数 → CS_FORMULA_EXECUTE_TIMEOUT（Future cancel(true) 中断执行线程） */
    @Test
    void executeTimeoutTriggered() {
        FormulaEngine engine = newEngine(100, slowProvider());
        long start = System.currentTimeMillis();
        String message = assertFormulaError(
                () -> engine.execute("SLOW_DOUBLE(${a}) + SLOW_DOUBLE(${b})",
                        contextOf(vars("a", 1, "b", 2)), null),
                SysFormulaConstants.CS_FORMULA_EXECUTE_TIMEOUT);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 3000, "注入 100ms 超时应快速失败，实际 " + elapsed + "ms");
        assertTrue(message.contains("100"), "错误信息应含注入的超时上限: " + message);
    }

    /** 循环超限（MAX_LOOP_COUNT 封顶）→ CS_FORMULA_EXECUTE_FAILED（确定性兜底，非裸异常穿透） */
    @Test
    void loopCountCapFailsDeterministically() {
        FormulaEngine engine = newEngine();
        String message = assertFormulaError(
                () -> engine.execute("reduce(range(0, 50000), +, 0)", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED);
        assertTrue(message.contains("loop"), "应透出 Aviator 循环超限信息: " + message);
    }

    /** 正常执行不受超时门禁误伤：慢函数在超时内完成，结果正确 */
    @Test
    void slowFunctionWithinTimeoutSucceeds() {
        FormulaEngine engine = newEngine(5000, slowProvider());
        Object result = engine.execute("SLOW_DOUBLE(${a})",
                contextOf(vars("a", 21)), SysFormulaReturnType.NUMBER);
        assertInstanceOf(java.math.BigDecimal.class, result);
        assertEquals(0, ((java.math.BigDecimal) result).compareTo(java.math.BigDecimal.valueOf(42)));
    }

    /** 线程池命名：受限执行发生在 formula-engine-executor 线程（模块命名约束） */
    @Test
    void executionRunsOnFormulaEngineExecutorThread() {
        final List<String> threadNames = new java.util.ArrayList<>();
        FormulaFunctionDefinition capture = FormulaFunctions.definition("THREAD_NAME", "取线程名",
                SysFormulaFunctionCategory.SYSTEM, "测试用",
                SysFormulaReturnType.STRING, new AbstractFunction() {
                    @Override
                    public String getName() {
                        return "THREAD_NAME";
                    }

                    @Override
                    public AviatorObject call(Map<String, Object> env) {
                        synchronized (threadNames) {
                            threadNames.add(Thread.currentThread().getName());
                        }
                        return AviatorRuntimeJavaType.valueOf(Thread.currentThread().getName());
                    }
                });
        FormulaEngine custom = new FormulaEngine(new FormulaFunctionRegistry(List.of(providerOf(capture))));
        try {
            Object result = custom.execute("THREAD_NAME()", emptyContext(), SysFormulaReturnType.STRING);
            assertEquals("formula-engine-executor", result);
            assertEquals(List.of("formula-engine-executor"), threadNames);
        } finally {
            custom.destroy();
        }
    }
}

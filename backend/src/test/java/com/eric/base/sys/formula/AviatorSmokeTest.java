package com.eric.base.sys.formula;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.exception.ExpressionSyntaxErrorException;
import com.googlecode.aviator.Options;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AviatorScript 冒烟测试（F14-BE-001）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 以方案 §2.1 六个样例公式对 Aviator 5.4.3 词法/语法/求值面做冒烟，
 *              不依赖 Spring 上下文；结论供 F14-BE-003（内置函数注册）、F14-BE-004（引擎编排）、
 *              F14-BE-005（沙箱裁剪）参考，实测事实（5.4.3）：
 *              <ul>
 *              <li>内置函数无 IF / CONCAT / SUM / ROUND(x, scale) 等业务命名函数——
 *                  内置函数名为带点形态（math.round / count / reduce / string.join /
 *                  string_to_date / date_to_string 等）；math.round 仅单参（四舍五入到整数），
 *                  不支持小数位参数；IF 语义以三元运算符等效验证、SUM 以 reduce(seq, +, 0)
 *                  等效验证（参数序 seq→fn→init，运算符可直接作函数引用）、CONCAT 以 + 运算符
 *                  等效验证——ROUND/IF/CONCAT/SUM 业务命名函数由 F14-BE-003 经注册中心
 *                  注册补齐；</li>
 *              <li>默认配置下 Java 方法调用面开放：System.currentTimeMillis() 静态调用、
 *                  new java.util.ArrayList() 构造均可执行——确认 F14-BE-005 沙箱裁剪
 *                  （ALLOWED_CLASS_SET 白名单 + 关闭反射调用面）必要；</li>
 *              <li>编译错误抛 ExpressionSyntaxErrorException，信息形如
 *                  「Syntax error: illegal token at 4, lineNumber: 1, token : null,」——
 *                  含字符偏移位置与行号、不含列号，行号定位能力有限，F14-BE-004 透出行号
 *                  需自行按表达式行拆分定位（如按 \n 切行编译定位法）。</li>
 *              </ul>
 */
class AviatorSmokeTest {

    /** 样例同款配置（MAX_LOOP_COUNT 封顶），F14-BE-004/005 沿用 */
    private AviatorEvaluatorInstance newEngine() {
        AviatorEvaluatorInstance instance = AviatorEvaluator.newInstance();
        instance.setOption(Options.MAX_LOOP_COUNT, 10000L);
        return instance;
    }

    /** 例 1：数值计算——内置 math.round 仅单参（到整数），ROUND(x, 2) 由 F14-BE-003 注册；此处以放大取整等效验证运算面 */
    @Test
    void smoke1_roundNumeric() {
        Map<String, Object> env = new HashMap<>();
        env.put("a", 1000.0);
        env.put("b", 0.13);
        Object result = newEngine().execute("math.round(a * b * 100) / 100.0", env);
        assertEquals(130.0, (double) result, 1e-9);
    }

    /** 例 2：逻辑判断——Aviator 无内置 IF 函数，三元运算符等效验证（业务 IF 由 F14-BE-003 注册） */
    @Test
    void smoke2_ifLogic() {
        Map<String, Object> env = new HashMap<>();
        env.put("a", 15000);
        Object pass = newEngine().execute("a > 10000 ? '需部门审批' : '无需审批'", env);
        assertEquals("需部门审批", pass);
        env.put("a", 8000);
        Object reject = newEngine().execute("a > 10000 ? '需部门审批' : '无需审批'", env);
        assertEquals("无需审批", reject);
    }

    /** 例 3：明细汇总——无内置 SUM，以 reduce(seq, +, 0) 等效验证 seq 聚合面（业务 SUM 由 F14-BE-003 注册） */
    @Test
    void smoke3_sumSeq() {
        List<Number> amounts = List.of(100, 200.5, 99.5);
        Map<String, Object> env = new HashMap<>();
        env.put("seq", amounts);
        assertEquals(3L, newEngine().execute("count(seq)", env));
        assertEquals(400.0, (double) newEngine().execute("reduce(seq, +, 0)", env), 1e-9);
    }

    /** 例 4：字符串整合——无内置 CONCAT，+ 运算符等效验证（业务 CONCAT 由 F14-BE-003 注册） */
    @Test
    void smoke4_concatString() {
        Map<String, Object> env = new HashMap<>();
        env.put("a", "财务部");
        env.put("b", "张三");
        assertEquals("财务部-张三", newEngine().execute("a + '-' + b", env));
    }

    /** 例 5：日期比较——string_to_date 解析日期字面量后用比较运算符验证（业务 DATEDIFF/TODAY 由 F14-BE-003 注册） */
    @Test
    void smoke5_dateCompare() {
        Map<String, Object> env = new HashMap<>();
        env.put("today", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        env.put("startDate", LocalDate.now().minusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE));
        assertTrue((Boolean) newEngine()
                .execute("string_to_date(startDate, 'yyyy-MM-dd') < string_to_date(today, 'yyyy-MM-dd')", env));
    }

    /** 例 6：编译错误透出——记录异常信息格式（是否带行号/位置），供 F14-BE-004 决定行号透出方案 */
    @Test
    void smoke6_compileErrorCarriesPosition() {
        try {
            newEngine().execute("a * ");
            throw new IllegalStateException("unreachable");
        } catch (ExpressionSyntaxErrorException e) {
            String message = e.getMessage();
            assertTrue(null != message && !message.isEmpty());
            System.out.println("[AviatorSmokeTest] compile error message = " + message);
        }
    }

    /** Java 方法调用面默认行为：默认配置下开放（System.currentTimeMillis 可调、new ArrayList 可建）——F14-BE-005 必须裁剪 */
    @Test
    void smoke7_javaMethodCallSurfaceOpenByDefault() {
        // 静态方法调用：System.currentTimeMillis()
        Object millis = newEngine().execute("System.currentTimeMillis()", new HashMap<>());
        assertTrue(((Number) millis).longValue() <= System.currentTimeMillis());
        // 构造 + seq 函数：new java.util.ArrayList() 后 count
        Object size = newEngine().execute("count(new java.util.ArrayList())", new HashMap<>());
        assertEquals(0L, ((Number) size).longValue());
        System.out.println("[AviatorSmokeTest] default java method call surface OPEN — sandbox trim required (F14-BE-005)");
    }
}

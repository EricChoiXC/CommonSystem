package com.eric.base.sys.formula.service.engine;

import com.eric.base.sys.datasource.service.SysDatasourceService;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.properties.SysFormulaConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 方案 §2.1 六个样例公式执行（F14-BE-004 验收主线，对齐方案 §3.2 时序）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 六例逐一以 `${}` 形态经引擎执行（九类内置函数 + mock DS_QUERY），
 *              覆盖数值计算/逻辑判断/路径投影明细汇总/字符串整合/日期比较/数据源取数。
 */
class FormulaEngineSampleTest extends FormulaEngineTestSupport {

    /** 例 1：数值计算 ROUND(${applyAmount} * ${taxRate}, 2) → 130（方案 §3.2 时序验收；
     *  数值规整口径：ROUND 整值结果以整型承载，BigDecimal scale=0，130.00 展示形态由消费方格式化） */
    @Test
    void sample1_roundNumeric() {
        FormulaEngine engine = newEngine();
        Object result = engine.execute("ROUND(${applyAmount} * ${taxRate}, 2)",
                contextOf(vars("applyAmount", 1000, "taxRate", 0.13)), SysFormulaReturnType.NUMBER);
        assertInstanceOf(BigDecimal.class, result);
        assertEquals(0, new BigDecimal("130.00").compareTo((BigDecimal) result));
    }

    /** 例 2：逻辑判断 IF(${applyAmount} > 10000, ...) → 两分支 */
    @Test
    void sample2_ifLogic() {
        FormulaEngine engine = newEngine();
        Object pass = engine.execute("IF(${applyAmount} > 10000, \"需部门审批\", \"无需审批\")",
                contextOf(vars("applyAmount", 15000)), SysFormulaReturnType.STRING);
        assertEquals("需部门审批", pass);
        Object reject = engine.execute("IF(${applyAmount} > 10000, \"需部门审批\", \"无需审批\")",
                contextOf(vars("applyAmount", 8000)), SysFormulaReturnType.STRING);
        assertEquals("无需审批", reject);
    }

    /** 例 3：明细汇总 SUM(${expenseDetails.fieldAmount})——路径投影 + SUM（§2.1 两级投影） */
    @Test
    void sample3_sumPathProjection() {
        FormulaEngine engine = newEngine();
        Object result = engine.execute("SUM(${expenseDetails.fieldAmount})",
                contextOf(vars("expenseDetails", List.of(
                        Map.of("fieldAmount", 100),
                        Map.of("fieldAmount", 200.5),
                        Map.of("fieldAmount", 99.5)))),
                SysFormulaReturnType.NUMBER);
        assertInstanceOf(BigDecimal.class, result);
        assertEquals(0, new BigDecimal("400").compareTo((BigDecimal) result));
    }

    /** 例 4：字符串整合 CONCAT(${deptName}, "-", ${applicantName}) */
    @Test
    void sample4_concatString() {
        FormulaEngine engine = newEngine();
        Object result = engine.execute("CONCAT(${deptName}, \"-\", ${applicantName})",
                contextOf(vars("deptName", "财务部", "applicantName", "张三")), SysFormulaReturnType.STRING);
        assertEquals("财务部-张三", result);
    }

    /** 例 5：日期比较 DATEDIFF(TODAY(), ${startDate}) > 3 → BOOLEAN */
    @Test
    void sample5_dateCompare() {
        FormulaEngine engine = newEngine();
        String startDate = LocalDate.now().minusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE);
        Object result = engine.execute("DATEDIFF(TODAY(), ${startDate}) > 3",
                contextOf(vars("startDate", startDate)), SysFormulaReturnType.BOOLEAN);
        assertEquals(Boolean.TRUE, result);
    }

    /** 例 6：数据源取数 COUNT(DS_QUERY(...))——DS_QUERY 假数据 + 集合函数消费 */
    @Test
    void sample6_dsQueryCount() {
        when(datasourceService.execute(any(), any(), any(), any(), any()))
                .thenReturn(new SysDatasourceService.SysDatasourceExecution(
                        List.of(Map.of("fieldName", "周报一"), Map.of("fieldName", "周报二")),
                        2, List.of("fieldName")));
        FormulaEngine engine = newEngine();
        Object result = engine.execute(
                "COUNT(DS_QUERY(\"calendar.my-created\", seq.map(\"fieldName\", \"周报\")))",
                emptyContext(), SysFormulaReturnType.NUMBER);
        assertEquals(0, new BigDecimal("2").compareTo((BigDecimal) result));
    }

    /** 执行期不再携带 `${}` 形态：显示/存储层原样保留，引擎只消费替换后文本 */
    @Test
    void dollarBraceTextPreservedForDisplay() {
        String expression = "ROUND(${applyAmount} * ${taxRate}, 2)";
        assertEquals(List.of("applyAmount", "taxRate"),
                newEngine().extractDependencies(expression));
        // 原文本不含合法 `${}` 时引擎报语法/变量错误，而非静默替换
        assertFormulaError(() -> newEngine().execute("ROUND(1 + ${", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID);
    }
}

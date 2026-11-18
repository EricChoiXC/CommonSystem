package com.eric.base.sys.formula.service.engine;

import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.properties.SysFormulaConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ${} 替换可逆/无歧义与路径投影用例（F14-BE-004，R03 风险收口）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖：同码多次出现、码与函数名形如 `${ROUND}` 与 ROUND 函数共存、
 *              `${if}` 保留字形态、路径投影求和与确定性报错、字符串字面量内 `${}` 不替换。
 */
class FormulaEngineSubstitutionTest extends FormulaEngineTestSupport {

    /** 同一变量码多次出现：各出现点独立替换（序号递增），语义一致 */
    @Test
    void sameCodeMultipleOccurrences() {
        FormulaEngine engine = newEngine();
        Object result = engine.execute("${amount} + ${amount} * 0.1 + ROUND(${amount}, 0)",
                contextOf(vars("amount", 100)), SysFormulaReturnType.NUMBER);
        assertInstanceOf(BigDecimal.class, result);
        assertEquals(new BigDecimal("210.0"), result);
    }

    /** R03：变量码与函数名同名——`${ROUND}` 与 ROUND 函数共存且互不干扰 */
    @Test
    void variableNamedLikeFunction() {
        FormulaEngine engine = newEngine();
        Object result = engine.execute("ROUND(${ROUND} * ${taxRate}, 2)",
                contextOf(vars("ROUND", 1000, "taxRate", 0.13)), SysFormulaReturnType.NUMBER);
        assertInstanceOf(BigDecimal.class, result);
        assertEquals(0, new BigDecimal("130.00").compareTo((BigDecimal) result));
    }

    /** R03：保留字形态变量码 `${if}`——带序号前缀替换后不与 Aviator 保留字冲突 */
    @Test
    void variableNamedLikeReservedWord() {
        FormulaEngine engine = newEngine();
        Object result = engine.execute("IF(${if} > 10, \"大\", \"小\")",
                contextOf(vars("if", 11)), SysFormulaReturnType.STRING);
        assertEquals("大", result);
    }

    /** 路径投影 + 集合函数组合：同一根码的普通引用与投影引用共存 */
    @Test
    void projectionCoexistsWithPlainReference() {
        FormulaEngine engine = newEngine();
        List<Map<String, Object>> details = List.of(
                Map.of("amount", 10, "label", "a"),
                Map.of("amount", 20, "label", "b"));
        Object sum = engine.execute("SUM(${details.amount}) * COUNT(${details.amount})",
                contextOf(vars("details", details)), SysFormulaReturnType.NUMBER);
        assertEquals(0, new BigDecimal("60").compareTo((BigDecimal) sum));
        Object labels = engine.execute("CONCAT(${details.label})", contextOf(vars("details", details)),
                SysFormulaReturnType.STRING);
        assertEquals("[a, b]", labels, "投影数组整体文本化为 List 的 String.valueOf 形态");
    }

    /** 路径投影：非数组根变量确定性报错（CS_FORMULA_EXECUTE_FAILED） */
    @Test
    void projectionRootNotArrayFails() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.execute("SUM(${details.amount})",
                        contextOf(vars("details", "not-a-list")), null),
                SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED);
    }

    /** 路径投影：元素非 Map 确定性报错（CS_FORMULA_EXECUTE_FAILED） */
    @Test
    void projectionElementNotMapFails() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.execute("SUM(${details.amount})",
                        contextOf(vars("details", List.of(1, 2, 3))), null),
                SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED);
    }

    /** 路径投影：根变量缺失按空数组处理（缺失变量 nil 语义，SUM 空集 = 0） */
    @Test
    void projectionRootMissingYieldsEmptyArray() {
        FormulaEngine engine = newEngine();
        Object result = engine.execute("SUM(${details.amount})", emptyContext(), SysFormulaReturnType.NUMBER);
        assertEquals(new BigDecimal("0"), result);
    }

    /** 字符串字面量内的 `${...}` 不替换（VAR_PATTERN 语义层替换，不做文本层盲替） */
    @Test
    void dollarBraceInsideStringLiteralUntouched() {
        FormulaEngine engine = newEngine();
        Object result = engine.execute("CONCAT(\"单价 ${price} 元\", \"-\", ${price})",
                contextOf(vars("price", 5)), SysFormulaReturnType.STRING);
        assertEquals("单价 ${price} 元-5", result);
    }

    /** 不合法变量码（`${9abc}` / `${a-b}` / 残缺 `${`）→ CS_FORMULA_VARIABLE_INVALID */
    @Test
    void invalidVarCodeRejected() {
        FormulaEngine engine = newEngine();
        assertFormulaError(() -> engine.execute("${9abc} + 1", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID);
        assertFormulaError(() -> engine.execute("${a-b} + 1", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID);
        assertFormulaError(() -> engine.execute("1 + ${", emptyContext(), null),
                SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID);
    }

    /** 依赖提取：普通变量取码、路径投影取根码、去重保序（validate/save 共用口径） */
    @Test
    void extractDependenciesDedupAndOrder() {
        FormulaEngine engine = newEngine();
        assertEquals(
                List.of("applyAmount", "taxRate", "details"),
                engine.extractDependencies("ROUND(${applyAmount} * ${taxRate}, 2) + SUM(${details.amount}) + ${applyAmount}"));
        assertEquals(List.of(), engine.extractDependencies("ROUND(1.26, 1)"));
        assertEquals(List.of(), engine.extractDependencies(""));
        assertEquals(List.of(), engine.extractDependencies(null));
    }
}

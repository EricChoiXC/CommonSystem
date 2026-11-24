package com.eric.base.sys.form.service.engine;

import com.eric.base.sys.datasource.service.SysDatasourceService;
import com.eric.base.sys.form.model.SysFormFieldDo;
import com.eric.base.sys.form.model.SysFormSchemaDo;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 表单公式字段服务端重算引擎测试（F15-BE-005）
 *
 * @author Eric
 * @date 2026/11/24
 * @description 覆盖任务验证方式全项（真实 FormulaEngine——九类内置函数注册中心装配、
 *              非 mock，F14 联通用例：行内金额=数量×单价 与 SUM(${list.field}) 投影 +
 *              COLLECTION 合计均经真实引擎受限执行）：
 *              <ul>
 *              <li>行内公式逐行重算（行上下文 row：行内字段码直接 ${code} 引用，行内
 *                  同码覆盖主表值——行上下文优先）；</li>
 *              <li>主表公式 ${list.field} 投影 + COLLECTION 合计（行内公式先于主表公式
 *                  求值，跨层级依赖拓扑序）；</li>
 *              <li>公式链拓扑序（主表公式引用主表公式，Schema 逆序声明仍正确求值）；</li>
 *              <li>不一致策略（ADR-09 定稿）：前端任意值被服务端重算结果覆盖，不报错
 *                  不设 CS_FORM_FORMULA_MISMATCH——公式字段不落前端任意值；</li>
 *              <li>执行失败确定性错误码：缺变量 / 语法错误 → CS_FORMULA_EXECUTE_FAILED /
 *                  CS_FORMULA_SYNTAX_INVALID（复用 F14 语义映射，不裸异常穿透）；null
 *                  结果按键移除（不带空落库）；</li>
 *              <li>隐藏主表公式（显隐终态 ∪ permission=hidden）：结果参与依赖链求值但
 *                  不回写 payload（ADR-08 hidden 一律裁剪不落库）；</li>
 *              <li>P4 发布静态校验：表达式合法 / 期望返回类型 / 引用字段存在（含
 *                  ${list.field} 投影根码与行内字段）/ 依赖图无环 / 系统变量豁免。</li>
 *              </ul>
 */
class FormFormulaEvaluatorTest {

    private FormFormulaEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new FormFormulaEvaluator(newFormulaEngine());
    }

    // ---------------------------------------------------------------- F14 联通用例（真实 FormulaEngine，非 mock）

    /** 行内金额=数量×单价 逐行重算 + 前端任意值被服务端结果覆盖（ADR-09 不一致策略） */
    @Test
    void rowInlineFormulaRecalculatedPerRowWithServerAuthoritativeValue() {
        SysFormFieldDo detail = detailTable("purchaseDetails",
                field("productName", "input", "商品名称"),
                field("quantity", "number", "数量"),
                field("price", "number", "单价"),
                formulaField("amount", "ROUND(${quantity} * ${price}, 2)"));
        SysFormSchemaDo schema = schemaOf(detail);

        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("productName", "电脑");
        row1.put("quantity", 2);
        row1.put("price", 3.5);
        row1.put("amount", 999); // 前端提交值（任意值，服务端不信任）
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("productName", "显示器");
        row2.put("quantity", 4);
        row2.put("price", 2.25);
        Map<String, Object> payload = payload("applicant", "张三",
                "purchaseDetails", List.of(row1, row2));

        evaluator.evaluate(schema, payload, null);

        List<?> rows = assertInstanceOf(List.class, payload.get("purchaseDetails"), "明细行数组原位");
        Map<?, ?> recalculatedRow1 = assertInstanceOf(Map.class, rows.get(0), "行对象");
        assertNumEquals(7, recalculatedRow1.get("amount"),
                "行内金额=数量×单价（第 1 行 2×3.5，前端任意值 999 被服务端结果覆盖）");
        Map<?, ?> recalculatedRow2 = assertInstanceOf(Map.class, rows.get(1), "行对象");
        assertNumEquals(9, recalculatedRow2.get("amount"), "第 2 行 4×2.25（行内公式逐行求值）");
    }

    /** 行内公式 + 主表 SUM(${list.field}) 投影 COLLECTION 合计：行内先算、主表取重算后行值 */
    @Test
    void mainTotalProjectsRecalculatedRowValuesViaCollectionFunction() {
        SysFormFieldDo detail = detailTable("purchaseDetails",
                field("quantity", "number", "数量"),
                field("price", "number", "单价"),
                formulaField("amount", "ROUND(${quantity} * ${price}, 2)"));
        SysFormFieldDo total = formulaField("totalAmount", "SUM(${purchaseDetails.amount})");
        SysFormSchemaDo schema = schemaOf(detail, total);

        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("quantity", 2);
        row1.put("price", 3.5);
        row1.put("amount", 888);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("quantity", 4);
        row2.put("price", 2.25);
        row2.put("amount", 888);
        Map<String, Object> payload = payload("purchaseDetails", List.of(row1, row2), "totalAmount", 0);

        evaluator.evaluate(schema, payload, null);

        assertNumEquals(7, ((Map<?, ?>) ((List<?>) payload.get("purchaseDetails")).get(0)).get("amount"),
                "行内公式重算值");
        assertNumEquals(9, ((Map<?, ?>) ((List<?>) payload.get("purchaseDetails")).get(1)).get("amount"),
                "行内公式重算值");
        assertNumEquals(16, payload.get("totalAmount"),
                "主表合计 = SUM(重算后行值) = 7 + 9（COLLECTION 投影）");
    }

    /** 公式链拓扑序：主表公式引用主表公式，Schema 逆序声明仍按依赖序求值 */
    @Test
    void chainedMainFormulasResolveInTopologicalOrder() {
        // grandTotal 声明在前、依赖后声明的 totalAmount——拓扑序保证 totalAmount 先算
        SysFormFieldDo grandTotal = formulaField("grandTotal", "ROUND(${totalAmount} * 1.13, 2)");
        SysFormFieldDo total = formulaField("totalAmount", "SUM(${purchaseDetails.amount})");
        SysFormFieldDo detail = detailTable("purchaseDetails", field("amount", "number", "金额"));
        SysFormSchemaDo schema = schemaOf(grandTotal, total, detail);

        Map<String, Object> payload = payload(
                "purchaseDetails", List.of(rowOf("amount", 10), rowOf("amount", 20)),
                "grandTotal", 1, "totalAmount", 2);

        evaluator.evaluate(schema, payload, null);

        assertNumEquals(30, payload.get("totalAmount"), "合计公式先算（SUM 投影）");
        assertNumEquals(new BigDecimal("33.9"), payload.get("grandTotal"), "含税公式后算（依赖重算值）");
    }

    /** 行上下文注入主表字段值（行内公式引用主表字段）与行内同码覆盖主表值（行上下文优先） */
    @Test
    void rowFormulaSeesMainFieldValueAndRowCodeTakesPrecedence() {
        SysFormFieldDo discount = field("discount", "number", "折扣");
        SysFormFieldDo taxRate = field("taxRate", "number", "主表税率");
        SysFormFieldDo detail = detailTable("purchaseDetails",
                field("quantity", "number", "数量"),
                field("taxRate", "number", "行内税率（与主表同码）"),
                formulaField("taxAmount", "ROUND(${quantity} * ${taxRate}, 2)"),
                formulaField("discounted", "ROUND(${quantity} * ${discount}, 2)"));
        SysFormSchemaDo schema = schemaOf(discount, taxRate, detail);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("quantity", 10);
        row.put("taxRate", 0.1);
        Map<String, Object> payload = payload("discount", 0.9, "taxRate", 0.13,
                "purchaseDetails", List.of(row));

        evaluator.evaluate(schema, payload, null);

        assertNumEquals(1.0, ((Map<?, ?>) ((List<?>) payload.get("purchaseDetails")).get(0)).get("taxAmount"),
                "行内同码优先：10 × 行内税率 0.1");
        assertNumEquals(9.0, ((Map<?, ?>) ((List<?>) payload.get("purchaseDetails")).get(0)).get("discounted"),
                "行上下文回落主表：10 × 主表折扣 0.9");
    }

    // ---------------------------------------------------------------- 执行失败确定性错误码（复用 CS_FORMULA_*，不裸异常穿透）

    /** 缺失变量参与运算：确定性 CS_FORMULA_EXECUTE_FAILED（引擎 nil 语义），不裸异常穿透 */
    @Test
    void missingVariableFailsDeterministically() {
        SysFormSchemaDo schema = schemaOf(formulaField("total", "${nope} + 1"));
        Map<String, Object> payload = new LinkedHashMap<>();

        String message = assertFormulaError(() -> evaluator.evaluate(schema, payload, null),
                "CS_FORMULA_EXECUTE_FAILED");
        assertTrue(message.contains("total"), "错误定位公式字段: " + message);
        assertFalse(payload.containsKey("total"), "执行失败不落值（阻断保存由服务层承载）");
    }

    /** 语法错误表达式：确定性 CS_FORMULA_SYNTAX_INVALID（引擎校验语义原样透出） */
    @Test
    void syntaxErrorMapsToFormulaSyntaxInvalid() {
        SysFormSchemaDo schema = schemaOf(
                field("quantity", "number", "数量"),
                formulaField("total", "${quantity} +* 2"));
        Map<String, Object> payload = payload("quantity", 2);

        assertFormulaError(() -> evaluator.evaluate(schema, payload, null), "CS_FORMULA_SYNTAX_INVALID");
    }

    /** null 结果按键移除（未声明返回类型公式产出 null → 「无值」形态，不允许带空落库） */
    @Test
    void nullResultRemovesPayloadKey() {
        SysFormFieldDo total = field("total", "formula", "公式-total");
        total.setProps(props("expression", "nil"));
        SysFormSchemaDo schema = schemaOf(total);
        Map<String, Object> payload = payload("total", "残留值");

        evaluator.evaluate(schema, payload, null);

        assertFalse(payload.containsKey("total"), "null 结果移除键（不带空落库）");
    }

    /** NUMBER 公式产出 null：引擎确定性拒绝（数值结果为空，不允许带空落库） */
    @Test
    void nullNumberResultFailsDeterministically() {
        SysFormSchemaDo schema = schemaOf(formulaField("total", "nil"));
        Map<String, Object> payload = payload("total", "残留值");

        String message = assertFormulaError(() -> evaluator.evaluate(schema, payload, null),
                "CS_FORMULA_EXECUTE_FAILED");
        assertTrue(message.contains("total"), "错误定位公式字段: " + message);
        assertEquals("残留值", payload.get("total"), "执行失败 payload 不改写（异常阻断保存由服务层承载）");
    }

    /** 期望返回类型 NUMBER 适配：结果规整为 BigDecimal（引擎结果类型适配语义） */
    @Test
    void expectedReturnTypeAdaptsResultToBigDecimal() {
        SysFormSchemaDo schema = schemaOf(
                field("quantity", "number", "数量"),
                field("price", "number", "单价"),
                formulaField("total", "${quantity} * ${price}"));
        Map<String, Object> payload = payload("quantity", 2, "price", 1.5, "total", "junk");

        evaluator.evaluate(schema, payload, null);

        assertInstanceOf(BigDecimal.class, payload.get("total"), "NUMBER 期望类型经引擎适配");
        assertNumEquals(3.0, payload.get("total"), "服务端重算值 2 × 1.5");
    }

    // ---------------------------------------------------------------- 隐藏主表公式（ADR-08 hidden 不落库）

    /** 隐藏主表公式：结果参与依赖链求值但不回写 payload（显隐终态隐藏集入参承载） */
    @Test
    void hiddenMainFormulaEvaluatedForDependentsButNotWrittenBack() {
        SysFormFieldDo hiddenBase = formulaField("hiddenBase", "SUM(${purchaseDetails.value})");
        hiddenBase.setProps(props("expression", "SUM(${purchaseDetails.value})",
                "expectedReturnType", "NUMBER", "permission", "hidden"));
        SysFormFieldDo dependent = formulaField("doubleBase", "${hiddenBase} * 2");
        SysFormFieldDo detail = detailTable("purchaseDetails", field("value", "number", "数值"));
        SysFormSchemaDo schema = schemaOf(hiddenBase, dependent, detail);

        Map<String, Object> payload = payload(
                "purchaseDetails", List.of(rowOf("value", 10), rowOf("value", 15)),
                "doubleBase", 1);

        evaluator.evaluate(schema, payload, Set.of("hiddenBase"));

        assertFalse(payload.containsKey("hiddenBase"), "隐藏主表公式结果不回写（ADR-08 hidden 一律裁剪不落库）");
        assertNumEquals(50, payload.get("doubleBase"), "依赖链求值取隐藏公式重算值（25 × 2）");
    }

    // ---------------------------------------------------------------- P4 发布静态校验（分册 §7.2 复用映射）

    /** 合法公式 Schema 通过 P4（行内公式 + 主表投影 + 系统变量豁免） */
    @Test
    void publishableValidFormulaSchemaPasses() {
        SysFormFieldDo detail = detailTable("purchaseDetails",
                field("quantity", "number", "数量"),
                field("price", "number", "单价"),
                formulaField("amount", "ROUND(${quantity} * ${price}, 2)"));
        SysFormFieldDo total = formulaField("totalAmount", "SUM(${purchaseDetails.amount})");
        SysFormFieldDo owner = formulaField("ownerName", "${SYS_CURRENT_USER_ID}");
        SysFormSchemaDo schema = schemaOf(detail, total, owner);

        evaluator.assertPublishable(schema); // 不抛即通过
    }

    /** 表达式为空：CS_FORMULA_SYNTAX_INVALID（引擎校验语义原样透出） */
    @Test
    void publishRejectsBlankExpression() {
        SysFormSchemaDo schema = schemaOf(formulaField("total", "   "));

        assertFormulaError(() -> evaluator.assertPublishable(schema), "CS_FORMULA_SYNTAX_INVALID");
    }

    /** 主表公式引用不存在的字段：CS_FORMULA_VARIABLE_INVALID */
    @Test
    void publishRejectsUnknownReference() {
        SysFormSchemaDo schema = schemaOf(formulaField("total", "${nope} + 1"));

        String message = assertFormulaError(() -> evaluator.assertPublishable(schema),
                "CS_FORMULA_VARIABLE_INVALID");
        assertTrue(message.contains("nope"), "错误定位未知引用: " + message);
    }

    /** 主表公式引用行内专属字段（主表上下文不可解析）：CS_FORMULA_VARIABLE_INVALID */
    @Test
    void publishRejectsRowOnlyReferenceFromMainFormula() {
        SysFormFieldDo detail = detailTable("purchaseDetails", field("quantity", "number", "数量"));
        SysFormSchemaDo schema = schemaOf(formulaField("total", "${quantity}"), detail);

        assertFormulaError(() -> evaluator.assertPublishable(schema), "CS_FORMULA_VARIABLE_INVALID");
    }

    /** 投影根码不是明细表字段：CS_FORMULA_VARIABLE_INVALID（分册 §7.2 投影路径非法族） */
    @Test
    void publishRejectsProjectionRootNotDetailTable() {
        SysFormSchemaDo schema = schemaOf(
                field("quantity", "number", "数量"),
                formulaField("total", "SUM(${quantity.value})"));

        assertFormulaError(() -> evaluator.assertPublishable(schema), "CS_FORMULA_VARIABLE_INVALID");
    }

    /** 投影行内字段不存在：CS_FORMULA_VARIABLE_INVALID */
    @Test
    void publishRejectsProjectionUnknownRowField() {
        SysFormFieldDo detail = detailTable("purchaseDetails", field("quantity", "number", "数量"));
        SysFormSchemaDo schema = schemaOf(detail, formulaField("total", "SUM(${purchaseDetails.unknown})"));

        String message = assertFormulaError(() -> evaluator.assertPublishable(schema),
                "CS_FORMULA_VARIABLE_INVALID");
        assertTrue(message.contains("unknown"), "错误定位投影行内字段: " + message);
    }

    /** 依赖图存在环：CS_FORMULA_EXECUTE_FAILED（族内确定性兜底，运行期拓扑熔断同码） */
    @Test
    void publishRejectsDependencyCycle() {
        SysFormFieldDo a = formulaField("formulaA", "${formulaB} + 1");
        SysFormFieldDo b = formulaField("formulaB", "${formulaA} + 1");
        SysFormSchemaDo schema = schemaOf(a, b);

        String message = assertFormulaError(() -> evaluator.assertPublishable(schema),
                "CS_FORMULA_EXECUTE_FAILED");
        assertTrue(message.contains("循环依赖"), "环定位信息: " + message);
    }

    /** 期望返回类型不合法：CS_FORMULA_TYPE_MISMATCH（对齐 SysFormulaValidateService 规整语义） */
    @Test
    void publishRejectsInvalidExpectedReturnType() {
        SysFormFieldDo total = formulaField("total", "${quantity}");
        total.setProps(props("expression", "${quantity}", "expectedReturnType", "TEXT"));
        SysFormSchemaDo schema = schemaOf(field("quantity", "number", "数量"), total);

        assertFormulaError(() -> evaluator.assertPublishable(schema), "CS_FORMULA_TYPE_MISMATCH");
    }

    /** 运行期拓扑兜底：存在环的重算请求确定性失败（设计期 P4 已拒绝，防御性口径 §6.3） */
    @Test
    void evaluateRejectsDependencyCycleAtRuntime() {
        SysFormFieldDo a = formulaField("formulaA", "${formulaB} + 1");
        SysFormFieldDo b = formulaField("formulaB", "${formulaA} + 1");
        SysFormSchemaDo schema = schemaOf(a, b);

        assertFormulaError(() -> evaluator.evaluate(schema, new LinkedHashMap<>(), null),
                "CS_FORMULA_EXECUTE_FAILED");
    }

    // ---------------------------------------------------------------- 测试小件

    /**
     * 真实 FormulaEngine（九类内置函数注册中心装配，DS_QUERY 挂 mock 数据源服务；
     * 形态对齐 formula 模块 FormulaEngineTestSupport 先例——非 mock 引擎，F14 联通用例
     * 经真实受限执行通道）
     */
    private static FormulaEngine newFormulaEngine() {
        SysDatasourceService datasourceService = mock(SysDatasourceService.class);
        when(datasourceService.execute(any(), any(), any(), any(), any()))
                .thenReturn(new SysDatasourceService.SysDatasourceExecution(List.of(), 0, List.of()));
        List<FormulaFunctionProvider> providers = List.of(
                new MathFunctionProvider(),
                new LogicFunctionProvider(),
                new CompareFunctionProvider(),
                new StringFunctionProvider(),
                new DateFunctionProvider(),
                new CollectionFunctionProvider(),
                new ConvertFunctionProvider(),
                new SystemFunctionProvider(),
                new DataFunctionProvider(datasourceService));
        return new FormulaEngine(new FormulaFunctionRegistry(providers));
    }

    private static void assertNumEquals(Object expected, Object actual, String message) {
        assertNotNull(actual, message);
        BigDecimal actualNumber = new BigDecimal(String.valueOf(actual));
        BigDecimal expectedNumber = new BigDecimal(String.valueOf(expected));
        assertTrue(expectedNumber.compareTo(actualNumber) == 0,
                message + "（期望 " + expectedNumber + "，实际 " + actualNumber + "）");
    }

    private static String assertFormulaError(Runnable runnable, String expectedCode) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, runnable::run);
        assertTrue(null != error.getMessage() && error.getMessage().startsWith(expectedCode + ":"),
                "错误码应为 " + expectedCode + "，实际: " + error.getMessage());
        return error.getMessage();
    }

    private static SysFormFieldDo field(String code, String type, String label) {
        SysFormFieldDo field = new SysFormFieldDo();
        field.setField(code);
        field.setType(type);
        field.setLabel(label);
        return field;
    }

    /** 公式字段（props：expression + expectedReturnType=NUMBER） */
    private static SysFormFieldDo formulaField(String code, String expression) {
        SysFormFieldDo field = field(code, "formula", "公式-" + code);
        field.setProps(props("expression", expression, "expectedReturnType", "NUMBER"));
        return field;
    }

    private static SysFormFieldDo detailTable(String code, SysFormFieldDo... children) {
        SysFormFieldDo detail = field(code, "detailTable", "明细-" + code);
        detail.setProps(props("rowMin", 1, "rowMax", 50));
        detail.setChildren(List.of(children));
        return detail;
    }

    private static Map<String, Object> props(Object... keyValues) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            props.put((String) keyValues[i], keyValues[i + 1]);
        }
        return props;
    }

    private static Map<String, Object> rowOf(String key, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(key, value);
        return row;
    }

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            payload.put((String) keyValues[i], keyValues[i + 1]);
        }
        return payload;
    }

    private static SysFormSchemaDo schemaOf(SysFormFieldDo... fields) {
        SysFormSchemaDo schema = new SysFormSchemaDo();
        schema.setFormId("f".repeat(32));
        schema.setFormCode("purchase_apply");
        schema.setFormName("采购申请表");
        schema.setVersion(1);
        schema.setFields(new ArrayList<>(List.of(fields)));
        return schema;
    }
}

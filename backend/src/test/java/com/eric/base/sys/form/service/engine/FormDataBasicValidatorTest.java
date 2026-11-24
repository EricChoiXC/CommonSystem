package com.eric.base.sys.form.service.engine;

import com.eric.base.sys.form.model.SysFormDataErrorDo;
import com.eric.base.sys.form.model.SysFormFieldDo;
import com.eric.base.sys.form.model.SysFormSchemaDo;
import com.eric.base.sys.form.properties.SysFormConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表单数据基础校验器测试
 *
 * @author Eric
 * @date 2026/11/24
 * @description 覆盖任务 F15-BE-003 验收标准中「schema 基础校验」段（对齐
 *              FormSchemaValidatorTest 先例，纯引擎无依赖）：S1 字段白名单（未知字段拒绝
 *              CS_FORM_UNKNOWN_FIELD，任意层级含明细行内未知键，分册 §7.4 定稿①不做静默
 *              裁剪）、S2 基础校验正反例（必填 / 类型 / 长度 / 枚举选项值 / 明细行结构与
 *              行数上限 MAX_DETAIL_ROWS）、明细行数据结构归一与 Schema 字段序归一
 *              （normalize）、公式字段值不参与校验（ADR-09）。
 */
class FormDataBasicValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final FormDataBasicValidator validator = new FormDataBasicValidator();

    // ---------------------------------------------------------------- S1 字段白名单（未知字段拒绝）

    @Test
    void validateRejectsUnknownMainField() {
        SysFormSchemaDo schema = schema(
                field("applicant", "input", "申请人"),
                field("payType", "select", "付款方式"));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicant", "张三");
        formData.put("hackerField", "注入值");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(schema, formData));
        assertTrue(error.getMessage().startsWith(SysFormConstants.CS_FORM_UNKNOWN_FIELD + ":"),
                "未知字段拒绝为确定性拒绝（分册 §7.4①），实际: " + error.getMessage());
        assertTrue(error.getMessage().contains("hackerField"), error.getMessage());
    }

    @Test
    void validateRejectsUnknownRowField() {
        SysFormFieldDo detail = field("purchaseDetails", "detailTable", "采购明细");
        detail.setChildren(List.of(
                field("productName", "input", "商品名称"),
                field("quantity", "number", "数量")));
        SysFormSchemaDo schema = schema(detail);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productName", "电脑");
        row.put("rowUnknown", "行内未知键");
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("purchaseDetails", List.of(row));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(schema, formData));
        assertTrue(error.getMessage().startsWith(SysFormConstants.CS_FORM_UNKNOWN_FIELD + ":"),
                "明细行内未知键同样拒绝（任意层级），实际: " + error.getMessage());
        assertTrue(error.getMessage().contains("rowUnknown"), error.getMessage());
        assertTrue(error.getMessage().contains("purchaseDetails"), "错误定位含明细表字段码: " + error.getMessage());
    }

    @Test
    void validatePassesWhitelistedPayload() {
        SysFormFieldDo detail = field("purchaseDetails", "detailTable", "采购明细");
        detail.setChildren(List.of(field("productName", "input", "商品名称")));
        SysFormSchemaDo schema = schema(field("applicant", "input", "申请人"), detail);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productName", "电脑");
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicant", "张三");
        formData.put("purchaseDetails", List.of(row));

        List<SysFormDataErrorDo> errors = validator.validate(schema, formData);
        assertTrue(errors.isEmpty(), "白名单内载荷通过: " + errors);
    }

    // ---------------------------------------------------------------- S2 必填

    @Test
    void validateRejectsMissingRequiredField() {
        SysFormSchemaDo schema = schema(
                fieldWithProps("applicant", "input", "申请人", Map.of("required", true)),
                field("reason", "textarea", "采购原因"));

        List<SysFormDataErrorDo> errors = validator.validate(schema, new LinkedHashMap<>());
        assertEquals(1, errors.size(), "缺失必填项报错: " + errors);
        assertEquals("applicant", errors.get(0).getFieldCode());
        assertNull(errors.get(0).getRuleId(), "基础校验错误 ruleId 恒 null（分册 §4.4）");
        assertNull(errors.get(0).getRowIndex(), "主表字段错误 rowIndex 为 null");
        assertTrue(errors.get(0).getMessage().contains("申请人"), errors.get(0).getMessage());
    }

    @Test
    void validateAcceptsOptionalFieldAbsent() {
        SysFormSchemaDo schema = schema(field("reason", "textarea", "采购原因"));

        assertTrue(validator.validate(schema, new LinkedHashMap<>()).isEmpty(),
                "非必填字段缺省通过");
    }

    @Test
    void validateTreatsBlankAndEmptyCollectionAsEmpty() {
        SysFormSchemaDo schema = schema(
                fieldWithProps("applicant", "input", "申请人", Map.of("required", true)),
                fieldWithProps("tags", "choice", "标签", Map.of("required", true, "multi", true,
                        "options", List.of(option("a", "A"), option("b", "B")))));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicant", "   ");
        formData.put("tags", List.of());
        List<SysFormDataErrorDo> errors = validator.validate(schema, formData);
        assertEquals(2, errors.size(), "空白串与空集合均按无值裁决: " + errors);
    }

    // ---------------------------------------------------------------- S2 类型

    @Test
    void validateRejectsTypeMismatchPerFieldType() {
        SysFormSchemaDo schema = schema(
                field("amount", "number", "金额"),
                field("agreed", "switch", "是否同意"),
                field("applyDate", "date", "申请日期"));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("amount", "not-a-number");
        formData.put("agreed", "yes");
        formData.put("applyDate", 123);
        List<SysFormDataErrorDo> errors = validator.validate(schema, formData);
        assertEquals(3, errors.size(), "类型违规逐条报错: " + errors);
        assertTrue(errors.get(0).getMessage().contains("数值"), errors.get(0).getMessage());
        assertTrue(errors.get(1).getMessage().contains("布尔"), errors.get(1).getMessage());
        assertTrue(errors.get(2).getMessage().contains("文本"), errors.get(2).getMessage());
    }

    @Test
    void validateAcceptsLegalScalarTypes() {
        SysFormSchemaDo schema = schema(
                field("amount", "number", "金额"),
                field("agreed", "switch", "是否同意"),
                field("applyDate", "date", "申请日期"),
                field("remark", "textarea", "备注"));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("amount", 12000.5);
        formData.put("agreed", Boolean.TRUE);
        formData.put("applyDate", "2026-11-24");
        formData.put("remark", "办公设备采购");
        assertTrue(validator.validate(schema, formData).isEmpty(), "合法标量值域通过");
    }

    @Test
    void validateChecksMultiChoiceArrayShape() {
        SysFormSchemaDo schema = schema(
                fieldWithProps("tags", "choice", "标签", Map.of("multi", true,
                        "options", List.of(option("a", "A"), option("b", "B")))),
                fieldWithProps("payType", "select", "付款方式", Map.of("multi", false,
                        "options", List.of(option("cash", "现金"), option("transfer", "转账")))));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("tags", "a");
        formData.put("payType", List.of("cash"));
        List<SysFormDataErrorDo> errors = validator.validate(schema, formData);
        assertEquals(2, errors.size(), "multi=true 须数组、multi=false 须标量: " + errors);
        assertTrue(errors.get(0).getMessage().contains("数组"), errors.get(0).getMessage());
        assertTrue(errors.get(1).getMessage().contains("标量"), errors.get(1).getMessage());
    }

    // ---------------------------------------------------------------- S2 长度与枚举选项值

    @Test
    void validateRejectsTextOverMaxlength() {
        SysFormSchemaDo schema = schema(
                fieldWithProps("applicant", "input", "申请人", Map.of("maxlength", 4)));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicant", "张三李四王五");
        List<SysFormDataErrorDo> errors = validator.validate(schema, formData);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("长度 6 超过上限 4"), errors.get(0).getMessage());
    }

    @Test
    void validateRejectsValueOutsideOptions() {
        SysFormSchemaDo schema = schema(
                fieldWithProps("payType", "select", "付款方式", Map.of(
                        "options", List.of(option("cash", "现金"), option("transfer", "转账")))));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("payType", "bitcoin");
        List<SysFormDataErrorDo> errors = validator.validate(schema, formData);
        assertEquals(1, errors.size(), "枚举选项值校验：提交值 ∉ options[].value 报错: " + errors);
        assertTrue(errors.get(0).getMessage().contains("bitcoin"), errors.get(0).getMessage());
    }

    @Test
    void validateChecksEachElementOfMultiChoiceAgainstOptions() {
        SysFormSchemaDo schema = schema(
                fieldWithProps("tags", "choice", "标签", Map.of("multi", true,
                        "options", List.of(option("a", "A"), option("b", "B")))));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("tags", List.of("a", "c"));
        List<SysFormDataErrorDo> errors = validator.validate(schema, formData);
        assertEquals(1, errors.size(), "multi 逐元素枚举校验: " + errors);
        assertTrue(errors.get(0).getMessage().contains("c"), errors.get(0).getMessage());
    }

    @Test
    void validateSkipsOptionCheckWithoutConfiguredOptions() {
        SysFormSchemaDo schema = schema(fieldWithProps("payType", "select", "付款方式", Map.of()));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("payType", "any");
        assertTrue(validator.validate(schema, formData).isEmpty(),
                "未配置静态 options 不做枚举校验（datasource/addressbook 动态选项集）");
    }

    // ---------------------------------------------------------------- S2 明细行结构与行数上限

    @Test
    void validateRejectsRowMinViolation() {
        SysFormFieldDo detail = detailTable("purchaseDetails", "采购明细",
                Map.of("rowMin", 1, "rowMax", 5), field("productName", "input", "商品名称"));
        SysFormSchemaDo schema = schema(detail);

        List<SysFormDataErrorDo> errors = validator.validate(schema, new LinkedHashMap<>());
        assertEquals(1, errors.size(), "rowMin 必填语义 = 至少一行: " + errors);
        assertTrue(errors.get(0).getMessage().contains("不足下限 1"), errors.get(0).getMessage());
    }

    @Test
    void validateRejectsRowsOverSchemaRowMax() {
        SysFormFieldDo detail = detailTable("purchaseDetails", "采购明细",
                Map.of("rowMin", 1, "rowMax", 2), field("productName", "input", "商品名称"));
        SysFormSchemaDo schema = schema(detail);

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("purchaseDetails", List.of(row("productName", "电脑"), row("productName", "显示器"),
                row("productName", "键盘")));
        List<SysFormDataErrorDo> errors = validator.validate(schema, formData);
        assertEquals(1, errors.size(), "明细行数超过 props.rowMax 报错: " + errors);
        assertTrue(errors.get(0).getMessage().contains("超过上限 2"), errors.get(0).getMessage());
    }

    @Test
    void validateRejectsRowsOverAbsoluteMaxDetailRows() {
        SysFormFieldDo detail = detailTable("purchaseDetails", "采购明细", Map.of(),
                field("productName", "input", "商品名称"));
        SysFormSchemaDo schema = schema(detail);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i <= SysFormConstants.MAX_DETAIL_ROWS; i++) {
            rows.add(row("productName", "商品" + i));
        }
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("purchaseDetails", rows);
        List<SysFormDataErrorDo> errors = validator.validate(schema, formData);
        assertEquals(1, errors.size(), "rowMax 缺省按 MAX_DETAIL_ROWS 绝对上限裁决: " + errors);
        assertTrue(errors.get(0).getMessage().contains("超过上限 " + SysFormConstants.MAX_DETAIL_ROWS),
                errors.get(0).getMessage());
    }

    @Test
    void validateRejectsNonArrayDetailValue() {
        SysFormFieldDo detail = detailTable("purchaseDetails", "采购明细", Map.of(),
                field("productName", "input", "商品名称"));
        SysFormSchemaDo schema = schema(detail);

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("purchaseDetails", "not-an-array");
        List<SysFormDataErrorDo> errors = validator.validate(schema, formData);
        assertEquals(1, errors.size(), "明细表值须为扁平行对象数组（结构归一口径）: " + errors);
        assertTrue(errors.get(0).getMessage().contains("行对象数组"), errors.get(0).getMessage());
    }

    @Test
    void validateChecksRowFieldsInRowContext() {
        SysFormFieldDo detail = detailTable("purchaseDetails", "采购明细",
                Map.of("rowMin", 1),
                fieldWithProps("productName", "input", "商品名称", Map.of("required", true, "maxlength", 2)),
                fieldWithProps("quantity", "number", "数量", Map.of()));
        SysFormSchemaDo schema = schema(detail);

        Map<String, Object> badRow = new LinkedHashMap<>();
        badRow.put("quantity", 2);
        Map<String, Object> longRow = row("productName", "超长商品名称一");
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("purchaseDetails", List.of(badRow, longRow));

        List<SysFormDataErrorDo> errors = validator.validate(schema, formData);
        assertEquals(2, errors.size(), "行内必填与行内长度按行上下文校验: " + errors);
        assertEquals("productName", errors.get(0).getFieldCode());
        assertEquals(Integer.valueOf(0), errors.get(0).getRowIndex(), "明细行内错误携带 0 起始行号");
        assertTrue(errors.get(0).getMessage().contains("必填"), errors.get(0).getMessage());
        assertEquals(Integer.valueOf(1), errors.get(1).getRowIndex(), "第二行错误行号为 1");
        assertTrue(errors.get(1).getMessage().contains("长度"), errors.get(1).getMessage());
    }

    @Test
    void validateRowAllowsSameCodeAsMainScope() {
        // 行上下文隔离（§2.1）：行内字段码可与主表同码
        SysFormFieldDo detail = detailTable("purchaseDetails", "采购明细", Map.of(),
                field("productName", "input", "行内商品名"));
        SysFormSchemaDo schema = schema(
                field("productName", "input", "主表商品名"),
                detail);

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("productName", "主表值");
        formData.put("purchaseDetails", List.of(row("productName", "行内值")));
        assertTrue(validator.validate(schema, formData).isEmpty(), "行内字段码可与主表同码（行上下文隔离）");
    }

    // ---------------------------------------------------------------- 公式字段与归一化

    @Test
    void validateSkipsFormulaFieldValue() {
        // 公式字段值不参与校验（ADR-09：服务端重算值落库，重算随 F15-BE-005 并联）
        SysFormSchemaDo schema = schema(
                fieldWithProps("totalAmount", "formula", "合计金额",
                        Map.of("expression", "SUM(${purchaseDetails.amount})", "expectedReturnType", "NUMBER")));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("totalAmount", "前端任意预览值");
        assertTrue(validator.validate(schema, formData).isEmpty(), "公式字段提交值不参与校验");
    }

    @Test
    void normalizeReordersKeysBySchemaAndNormalizesRows() throws Exception {
        SysFormFieldDo detail = detailTable("purchaseDetails", "采购明细", Map.of(),
                field("productName", "input", "商品名称"),
                field("quantity", "number", "数量"),
                field("price", "number", "单价"));
        SysFormSchemaDo schema = schema(
                field("applicant", "input", "申请人"),
                fieldWithProps("totalAmount", "formula", "合计金额", Map.of()),
                detail);

        // 载荷键序打乱（quantity 在 productName 前、applicant 最后），行内键序同样打乱
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("price", 5000);
        row.put("quantity", 2);
        row.put("productName", "电脑");
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("purchaseDetails", List.of(row));
        formData.put("totalAmount", 10000);
        formData.put("applicant", "张三");

        Map<String, Object> normalized = validator.normalize(schema, formData);

        // 顶层键按 Schema 字段序（applicant → totalAmount → purchaseDetails），未知键不透传
        assertEquals(List.of("applicant", "totalAmount", "purchaseDetails"),
                new ArrayList<>(normalized.keySet()), "顶层键按 Schema 字段序重排");
        assertEquals("张三", normalized.get("applicant"));
        assertTrue(JSON.writeValueAsString(normalized).contains("\"totalAmount\":10000"),
                "公式字段值透传（重算随 F15-BE-005 并联）");

        // 明细行为扁平行对象数组、行内键按行上下文字段序重排
        Object detailValue = normalized.get("purchaseDetails");
        assertTrue(detailValue instanceof List<?>);
        Map<?, ?> normalizedRow = (Map<?, ?>) ((List<?>) detailValue).get(0);
        assertEquals(List.of("productName", "quantity", "price"),
                new ArrayList<>(normalizedRow.keySet()), "明细行内键按 Schema 行内字段序重排（结构归一）");
    }

    @Test
    void normalizeDropsKeysOutsideSchemaDefensively() {
        // 防御性口径：normalize 须在 validate 通过后调用；未保证的形态原样透传、白名单外键不带入
        SysFormSchemaDo schema = schema(field("applicant", "input", "申请人"));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicant", "张三");
        formData.put("suspicious", "值");
        Map<String, Object> normalized = validator.normalize(schema, formData);
        assertFalse(normalized.containsKey("suspicious"), "Schema 外键不进入归一产物");
        assertEquals("张三", normalized.get("applicant"));
    }

    @Test
    void validateHandlesNullSchemaAndNullPayload() {
        // null Schema：任意键均判未知字段；空载荷通过（空白表单合法）
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("anything", "值");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(null, formData));
        assertTrue(error.getMessage().startsWith(SysFormConstants.CS_FORM_UNKNOWN_FIELD + ":"),
                error.getMessage());
        assertTrue(validator.validate(null, new LinkedHashMap<>()).isEmpty(), "空白载荷对空白 Schema 合法");
        assertNotNull(validator.normalize(null, null), "null 载荷归一为空对象");
    }

    // ---------------------------------------------------------------- 测试小件

    private static SysFormSchemaDo schema(SysFormFieldDo... fields) {
        SysFormSchemaDo schema = new SysFormSchemaDo();
        schema.setFormId("f".repeat(32));
        schema.setFormCode("purchase_apply");
        schema.setFormName("采购申请表");
        schema.setVersion(1);
        schema.setFields(List.of(fields));
        return schema;
    }

    private static SysFormFieldDo field(String code, String type, String label) {
        SysFormFieldDo field = new SysFormFieldDo();
        field.setField(code);
        field.setType(type);
        field.setLabel(label);
        return field;
    }

    private static SysFormFieldDo fieldWithProps(String code, String type, String label,
                                                 Map<String, Object> props) {
        SysFormFieldDo field = field(code, type, label);
        field.setProps(new LinkedHashMap<>(props));
        return field;
    }

    private static SysFormFieldDo detailTable(String code, String label, Map<String, Object> props,
                                              SysFormFieldDo... rowFields) {
        SysFormFieldDo field = fieldWithProps(code, "detailTable", label, props);
        field.setChildren(List.of(rowFields));
        return field;
    }

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return row;
    }

    private static Map<String, Object> option(String value, String label) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", label);
        return option;
    }
}

package com.eric.base.sys.form.service.engine;

import com.eric.base.sys.form.model.SysFormSchemaDo;
import com.eric.base.sys.form.model.SysFormSchemaErrorDo;
import com.eric.base.sys.form.properties.SysFormConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表单 Schema 结构校验器测试（F15-BE-001）
 *
 * @author Eric
 * @date 2026/11/24
 * @description 正例取功能设计 §2.3 完整示例 Schema（采购申请表，零错误）；反例逐项覆盖
 *              任务卡与 API 分册 §7.1 第 6/7 项登记的校验项：字段码非法 / 同层级重复、
 *              未注册 type、code / permission 拒绝、嵌套超深、group/grid 相互嵌套超一层、
 *              明细表内嵌容器（R04）、明细行内非数据类组件、operator 非法 / in 缺数组 /
 *              gt 缺数值、动作 type 非法 / target 不存在、onSubmit 带 setValue（§3.4）、
 *              ruleId 重复、规则总数超限、字段总数超限、级联 parentField 非数据类 /
 *              自引用、级联 showCondition 非法（R08 第 4 条）、grid span 非法、
 *              maxlength 超 MAX_TEXT_LENGTH、rowMax 超 MAX_DETAIL_ROWS、Schema 体积超限
 *              （CS_FORM_SCHEMA_LIMIT_EXCEEDED）、非法 JSON（解析失败）。
 */
class FormSchemaValidatorTest {

    private static FormSchemaValidator validator;

    /** 功能设计 §2.3 完整示例 Schema（采购申请表）——与 SysFormSchemaDoTest 同源样例 */
    private static final String SAMPLE_SCHEMA_JSON = """
            {
              "formId": "1a2b3c4d5e6f708192a3b4c5d6e7f809",
              "formCode": "purchase_apply",
              "formName": "采购申请表",
              "version": 1,
              "fields": [
                { "field": "applicant", "type": "input", "label": "申请人",
                  "props": { "required": true, "maxlength": 50, "permission": "editable" } },
                { "field": "applyDate", "type": "date", "label": "申请日期",
                  "props": { "dateType": "DATE", "format": "YYYY-MM-DD", "required": true } },
                { "field": "purchaseDetails", "type": "detailTable", "label": "采购明细",
                  "props": { "rowMin": 1, "rowMax": 50, "summary": [ { "field": "amount", "fn": "sum" } ] },
                  "children": [
                    { "field": "productName", "type": "input", "label": "商品名称",
                      "props": { "required": true, "maxlength": 100 } },
                    { "field": "quantity", "type": "number", "label": "数量",
                      "props": { "numberType": "INTEGER", "min": 1, "allowNegative": false } },
                    { "field": "price", "type": "number", "label": "单价（元）",
                      "props": { "numberType": "AMOUNT", "precision": 2, "min": 0, "separator": "THOUSAND" } },
                    { "field": "amount", "type": "formula", "label": "金额（元）",
                      "props": { "expression": "ROUND(${quantity} * ${price}, 2)", "expectedReturnType": "NUMBER" } }
                  ] },
                { "field": "totalAmount", "type": "formula", "label": "合计金额（元）",
                  "props": { "expression": "SUM(${purchaseDetails.amount})", "expectedReturnType": "NUMBER" },
                  "rules": [
                    { "ruleId": "rule_003", "event": "onChange", "triggerField": "totalAmount",
                      "condition": { "operator": "gt", "value": 10000 },
                      "actions": [ { "type": "show", "target": "gmComment" } ] },
                    { "ruleId": "rule_004", "event": "onChange", "triggerField": "totalAmount",
                      "condition": { "operator": "lte", "value": 10000 },
                      "actions": [ { "type": "hide", "target": "gmComment" } ] }
                  ] },
                { "field": "payType", "type": "select", "label": "付款方式",
                  "props": { "multi": false, "required": true,
                    "options": [ { "label": "现金", "value": "cash" }, { "label": "转账", "value": "transfer" } ] },
                  "rules": [
                    { "ruleId": "rule_001", "event": "onChange", "triggerField": "payType",
                      "condition": { "operator": "equal", "value": "transfer" },
                      "actions": [
                        { "type": "setRequired", "target": "bankAccount", "value": true },
                        { "type": "show", "target": "bankAccount" } ] },
                    { "ruleId": "rule_002", "event": "onChange", "triggerField": "payType",
                      "condition": { "operator": "notEqual", "value": "transfer" },
                      "actions": [
                        { "type": "setRequired", "target": "bankAccount", "value": false },
                        { "type": "hide", "target": "bankAccount" } ] }
                  ] },
                { "field": "bankAccount", "type": "input", "label": "银行账号",
                  "props": { "maxlength": 32, "hidden": true } },
                { "field": "reason", "type": "textarea", "label": "采购原因",
                  "props": { "maxlength": 500 } },
                { "field": "gmComment", "type": "textarea", "label": "总经理审批说明",
                  "props": { "maxlength": 500, "hidden": true } }
              ]
            }""";

    @BeforeAll
    static void setUp() {
        validator = new FormSchemaValidator();
    }

    /* ==================== 工具 ==================== */

    /** 错误码集合（断言用） */
    private static List<String> codes(List<SysFormSchemaErrorDo> errors) {
        return errors.stream().map(SysFormSchemaErrorDo::getCode).collect(Collectors.toList());
    }

    /** 断言错误清单恰含指定错误码，并回传匹配错误（供文案断言） */
    private static List<SysFormSchemaErrorDo> assertHasCode(List<SysFormSchemaErrorDo> errors, String code) {
        List<SysFormSchemaErrorDo> matched = errors.stream()
                .filter(e -> code.equals(e.getCode())).collect(Collectors.toList());
        assertTrue(!matched.isEmpty(), "应含错误码 " + code + "，实际："
                + errors.stream().map(e -> e.getCode() + " " + e.getMessage()).collect(Collectors.joining("; ")));
        return matched;
    }

    /** 断言错误清单不含指定错误码（超限类反例的隔离断言用） */
    private static void assertNotHasCode(List<SysFormSchemaErrorDo> errors, String code) {
        assertTrue(errors.stream().noneMatch(e -> code.equals(e.getCode())),
                "不应含错误码 " + code + "，实际：" + errors.stream()
                        .map(e -> e.getCode() + " " + e.getMessage()).collect(Collectors.joining("; ")));
    }

    /* ==================== 正例 ==================== */

    @Test
    void sampleSchemaPasses() {
        // §2.3 样例 Schema：结构校验零错误
        List<SysFormSchemaErrorDo> errors = validator.validate(SAMPLE_SCHEMA_JSON);
        assertTrue(errors.isEmpty(), "样例 Schema 应零错误，实际："
                + errors.stream().map(e -> e.getCode() + " " + e.getMessage()).collect(Collectors.joining("; ")));
    }

    @Test
    void objectOverloadPasses() throws Exception {
        // validate(SysFormSchemaDo) 重载：调用方已持有反序列化结果时直走
        SysFormSchemaDo schema = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(SAMPLE_SCHEMA_JSON, SysFormSchemaDo.class);
        assertTrue(validator.validate(schema).isEmpty());
    }

    @Test
    void groupGridNestingWithinOneLevelPasses() {
        // group/grid 相互嵌套最多一层：group 套 grid（容器链 = 2）合法
        String schema = """
                { "fields": [
                  { "type": "group", "label": "分组",
                    "children": [
                      { "type": "grid", "label": "栅格", "children": [
                        { "span": 12, "fields": [
                          { "field": "name", "type": "input", "label": "姓名" } ] } ] } ] } ] }""";
        assertTrue(validator.validate(schema).isEmpty());
    }

    @Test
    void detailRowFormulaAndRulePasses() {
        // 明细表行内 formula（数据类 12 之一）+ 行内规则 target 限行内字段，合法
        String schema = """
                { "fields": [
                  { "field": "details", "type": "detailTable", "label": "明细",
                    "props": { "rowMin": 1, "rowMax": 10 },
                    "children": [
                      { "field": "qty", "type": "number", "label": "数量" },
                      { "field": "amount", "type": "formula", "label": "金额",
                        "props": { "expression": "${qty} * 2", "expectedReturnType": "NUMBER" },
                        "rules": [
                          { "ruleId": "r1", "event": "onChange", "triggerField": "amount",
                            "actions": [ { "type": "setValue", "target": "qty", "value": 1 } ] } ] } ] } ] }""";
        assertTrue(validator.validate(schema).isEmpty());
    }

    @Test
    void cascadeWithinDataTypesPasses() {
        // 级联 parentField 指向已配置数据类字段 + showCondition 合法（R08）
        String schema = """
                { "fields": [
                  { "field": "city", "type": "input", "label": "城市" },
                  { "field": "district", "type": "cascade", "label": "区域",
                    "props": { "parentField": "city",
                      "options": [ { "label": "A区", "value": "a",
                        "showCondition": { "operator": "equal", "value": "北京" } } ] } } ] }""";
        assertTrue(validator.validate(schema).isEmpty());
    }

    @Test
    void emptyChildrenAndNoConditionRulePass() {
        // 空分组合法；condition 缺省 = 无条件规则（事件即触发）；onAdd 行初始化规则宿主为
        // 明细表（triggerField = 明细表字段码，target 为行内字段码，§3.1 行上下文）
        String schema = """
                { "fields": [
                  { "type": "group", "label": "空分组" },
                  { "field": "details", "type": "detailTable", "label": "明细",
                    "rules": [ { "ruleId": "r1", "event": "onAdd", "triggerField": "details",
                      "actions": [ { "type": "setValue", "target": "item", "value": "默认品名" } ] } ],
                    "children": [
                      { "field": "item", "type": "input", "label": "品名" } ] } ] }""";
        assertTrue(validator.validate(schema).isEmpty());
    }

    /* ==================== 反例：字段码 ==================== */

    @Test
    void illegalFieldCodeRejected() {
        String schema = """
                { "fields": [ { "field": "1abc", "type": "input", "label": "申请人" } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void duplicateFieldCodeAtSameLevelRejected() {
        // 同一数据层级唯一：布局容器（group）不产生新层级，group 内与顶层同码即重复
        String schema = """
                { "fields": [
                  { "field": "name", "type": "input", "label": "姓名" },
                  { "type": "group", "label": "分组",
                    "children": [ { "field": "name", "type": "input", "label": "重名" } ] } ] }""";
        List<SysFormSchemaErrorDo> errors = validator.validate(schema);
        assertHasCode(errors, SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void duplicateFieldCodeInDifferentDetailRowsAllowed() {
        // 明细行内为独立行上下文：不同明细表的行内字段可与主表同码（§2.1 行上下文隔离）
        String schema = """
                { "fields": [
                  { "field": "name", "type": "input", "label": "姓名" },
                  { "field": "d1", "type": "detailTable", "label": "明细1",
                    "children": [ { "field": "name", "type": "input", "label": "行内同码" } ] } ] }""";
        assertTrue(validator.validate(schema).isEmpty());
    }

    @Test
    void dataComponentMissingFieldCodeRejected() {
        String schema = """
                { "fields": [ { "type": "input", "label": "无字段码" } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void nonDataComponentWithFieldCodeRejected() {
        // group/table/text/image 不承载数据，不应配置字段码（§1.1 固定属性项按类别裁剪）
        String schema = """
                { "fields": [ { "field": "grp", "type": "group", "label": "分组" } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void blankLabelRejected() {
        String schema = """
                { "fields": [ { "field": "name", "type": "input" } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    /* ==================== 反例：type 白名单 ==================== */

    @Test
    void unregisteredTypeRejected() {
        String schema = """
                { "fields": [ { "field": "map1", "type": "unknownWidget", "label": "未知" } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void codeTypeRejectedWithSpecialMessage() {
        // R02：代码块组件一期仅协议位、不执行——专门拒绝文案
        String schema = """
                { "fields": [ { "field": "c1", "type": "code", "label": "代码块" } ] }""";
        List<SysFormSchemaErrorDo> matched = assertHasCode(
                validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
        assertTrue(matched.stream().anyMatch(e -> e.getMessage().contains("R02")),
                "code 拒绝文案应含 R02 说明");
    }

    @Test
    void permissionTypeRejectedWithSpecialMessage() {
        // 权限组件仅协议位不进 fields（字段级权限由 props.permission 承载）
        String schema = """
                { "fields": [ { "field": "p1", "type": "permission", "label": "权限" } ] }""";
        List<SysFormSchemaErrorDo> matched = assertHasCode(
                validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
        assertTrue(matched.stream().anyMatch(e -> e.getMessage().contains("permission")),
                "permission 拒绝文案应含协议位说明");
    }

    @Test
    void phaseTwoTypeRejected() {
        // 二期组件：richtext / tabs / collapse / map / relation / serialNumber
        for (String type : List.of("richtext", "tabs", "collapse", "map", "relation", "serialNumber")) {
            String schema = "{\"fields\": [ { \"field\": \"x1\", \"type\": \"" + type
                    + "\", \"label\": \"二期\" } ] }";
            assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
        }
    }

    /* ==================== 反例：嵌套与容器 ==================== */

    @Test
    void nestDepthExceeded() {
        // 容器链不超限（group+grid 两个容器）但深度超限：group → grid → detailTable 行内字段
        // 深度 4 > MAX_NEST_DEPTH(3)，报资源上限
        String schema = """
                { "fields": [
                  { "type": "group", "label": "g1",
                    "children": [
                      { "type": "grid", "label": "g2", "children": [
                        { "span": 12, "fields": [
                          { "field": "details", "type": "detailTable", "label": "明细",
                            "children": [
                              { "field": "deep", "type": "input", "label": "超深" } ] } ] } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED);
    }

    @Test
    void containerChainMoreThanOneLevelRejected() {
        // group/grid 相互嵌套最多一层：group → group（容器链 = 3）拒绝
        String schema = """
                { "fields": [
                  { "type": "group", "label": "g1",
                    "children": [
                      { "type": "group", "label": "g2",
                        "children": [ { "type": "group", "label": "g3" } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void containerInsideDetailTableRejected() {
        // R04：明细表内禁嵌套任何容器组件（group 也不行）与明细表
        String schema = """
                { "fields": [
                  { "field": "details", "type": "detailTable", "label": "明细",
                    "children": [
                      { "type": "group", "label": "行内分组",
                        "children": [ { "field": "x", "type": "input", "label": "行内" } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void nestedDetailTableRejected() {
        // R04：明细表内禁嵌套明细表（一期一层明细）
        String schema = """
                { "fields": [
                  { "field": "outer", "type": "detailTable", "label": "外层明细",
                    "children": [
                      { "field": "inner", "type": "detailTable", "label": "内层明细" } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void nonDataComponentInDetailRowRejected() {
        // R04：明细行内仅数据类组件（text/table/image 等内容类不允许）
        String schema = """
                { "fields": [
                  { "field": "details", "type": "detailTable", "label": "明细",
                    "children": [
                      { "type": "text", "label": "行内文本" } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void nonContainerWithChildrenRejected() {
        String schema = """
                { "fields": [
                  { "field": "name", "type": "input", "label": "姓名",
                    "children": [ { "field": "x", "type": "input", "label": "非法子字段" } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    /* ==================== 反例：grid 列对象 ==================== */

    @Test
    void illegalGridSpanRejected() {
        for (int span : new int[]{0, 25, -1}) {
            String schema = """
                    { "fields": [
                      { "type": "grid", "label": "栅格", "children": [
                        { "span": %d, "fields": [ { "field": "name", "type": "input", "label": "姓名" } ] } ] } ] }"""
                    .formatted(span);
            assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
        }
    }

    @Test
    void gridColumnMissingSpanRejected() {
        String schema = """
                { "fields": [
                  { "type": "grid", "label": "栅格", "children": [
                    { "fields": [ { "field": "name", "type": "input", "label": "姓名" } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void gridColumnOutsideGridRejected() {
        // 栅格列对象仅允许出现在 type=grid 的 children 中
        String schema = """
                { "fields": [
                  { "span": 12, "fields": [ { "field": "name", "type": "input", "label": "姓名" } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void plainObjectInsideGridChildrenRejected() {
        // grid 的 children 须为列对象 { span, fields[] }：混入普通字段对象即形态违规
        String schema = """
                { "fields": [
                  { "type": "grid", "label": "栅格", "children": [
                    { "field": "name", "type": "input", "label": "姓名" } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    /* ==================== 反例：条件 operator / value ==================== */

    private static final String RULE_TEMPLATE = """
            { "fields": [
              { "field": "trigger", "type": "input", "label": "触发" },
              { "field": "target1", "type": "input", "label": "目标",
                "rules": [ { "ruleId": "r1", "event": "onChange", "triggerField": "trigger",
                  "condition": %s,
                  "actions": [ { "type": "show", "target": "target1" } ] } ] } ] }""";

    @Test
    void illegalOperatorRejected() {
        // 全集 10 个之外（matches 为一期不开放的正则类 operator）
        String schema = RULE_TEMPLATE.formatted("{ \"operator\": \"matches\", \"value\": \"^a\" }");
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void inOperatorRequiresArrayValue() {
        String schema = RULE_TEMPLATE.formatted("{ \"operator\": \"in\", \"value\": \"a\" }");
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void notInOperatorRequiresArrayValue() {
        String schema = RULE_TEMPLATE.formatted("{ \"operator\": \"notIn\", \"value\": 1 }");
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void gtOperatorRequiresNumberOrDateString() {
        // gt 的 value 为布尔 → 非数值 / 日期字符串
        String schema = RULE_TEMPLATE.formatted("{ \"operator\": \"gt\", \"value\": true }");
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void gtOperatorWithoutValueRejected() {
        String schema = RULE_TEMPLATE.formatted("{ \"operator\": \"gt\" }");
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void equalOperatorWithoutValueRejected() {
        String schema = RULE_TEMPLATE.formatted("{ \"operator\": \"equal\" }");
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void emptyOperatorWithValueRejected() {
        // empty/notEmpty：value 省略
        String schema = RULE_TEMPLATE.formatted("{ \"operator\": \"empty\", \"value\": \"x\" }");
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void missingOperatorRejected() {
        String schema = RULE_TEMPLATE.formatted("{ \"value\": \"x\" }");
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    /* ==================== 反例：动作 ==================== */

    @Test
    void illegalActionTypeRejected() {
        // script 类动作一期不开放（R02）
        String schema = """
                { "fields": [
                  { "field": "trigger", "type": "input", "label": "触发" },
                  { "field": "target1", "type": "input", "label": "目标",
                    "rules": [ { "ruleId": "r1", "event": "onChange", "triggerField": "trigger",
                      "actions": [ { "type": "script", "target": "target1" } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void actionTargetNotExistRejected() {
        String schema = """
                { "fields": [
                  { "field": "trigger", "type": "input", "label": "触发",
                    "rules": [ { "ruleId": "r1", "event": "onChange", "triggerField": "trigger",
                      "actions": [ { "type": "show", "target": "ghost" } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void actionMissingTargetRejected() {
        String schema = """
                { "fields": [
                  { "field": "trigger", "type": "input", "label": "触发",
                    "rules": [ { "ruleId": "r1", "event": "onChange", "triggerField": "trigger",
                      "actions": [ { "type": "hide" } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void setValueRequiresValuePayload() {
        String schema = """
                { "fields": [
                  { "field": "trigger", "type": "input", "label": "触发" },
                  { "field": "target1", "type": "input", "label": "目标",
                    "rules": [ { "ruleId": "r1", "event": "onChange", "triggerField": "trigger",
                      "actions": [ { "type": "setValue", "target": "target1" } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void setRequiredRequiresBooleanValue() {
        String schema = """
                { "fields": [
                  { "field": "trigger", "type": "input", "label": "触发" },
                  { "field": "target1", "type": "input", "label": "目标",
                    "rules": [ { "ruleId": "r1", "event": "onChange", "triggerField": "trigger",
                      "actions": [ { "type": "setRequired", "target": "target1", "value": "yes" } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void onSubmitWithSetValueRejected() {
        // §3.4：onSubmit / onValidate 仅支持 setRequired/setValidate/show/hide，禁 setValue/clearValue
        for (String event : List.of("onSubmit", "onValidate")) {
            String schema = """
                    { "fields": [
                      { "field": "trigger", "type": "input", "label": "触发",
                        "rules": [ { "ruleId": "r1", "event": "%s", "triggerField": "trigger",
                          "actions": [ { "type": "setValue", "target": "target1", "value": "x" } ] } ] },
                      { "field": "target1", "type": "input", "label": "目标" } ] }"""
                    .formatted(event);
            List<SysFormSchemaErrorDo> errors = validator.validate(schema);
            List<SysFormSchemaErrorDo> matched =
                    assertHasCode(errors, SysFormConstants.CS_FORM_SCHEMA_INVALID);
            assertTrue(matched.stream().anyMatch(e -> e.getMessage().contains("onSubmit/onValidate")),
                    event + " 禁 setValue 文案应含事件说明");
        }
    }

    @Test
    void onSubmitWithClearValueRejected() {
        String schema = """
                { "fields": [
                  { "field": "trigger", "type": "input", "label": "触发",
                    "rules": [ { "ruleId": "r1", "event": "onSubmit", "triggerField": "trigger",
                      "actions": [ { "type": "clearValue", "target": "target1" } ] } ] },
                  { "field": "target1", "type": "input", "label": "目标" } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void onSubmitWithAllowedActionsPasses() {
        // onSubmit 上 setRequired / setValidate / show / hide 合法（§3.4 白名单）
        String schema = """
                { "fields": [
                  { "field": "trigger", "type": "input", "label": "触发",
                    "rules": [ { "ruleId": "r1", "event": "onSubmit", "triggerField": "trigger",
                      "actions": [
                        { "type": "setRequired", "target": "target1", "value": true },
                        { "type": "show", "target": "target1" } ] } ] },
                  { "field": "target1", "type": "input", "label": "目标" } ] }""";
        assertTrue(validator.validate(schema).isEmpty());
    }

    @Test
    void emptyActionsRejected() {
        String schema = """
                { "fields": [
                  { "field": "trigger", "type": "input", "label": "触发",
                    "rules": [ { "ruleId": "r1", "event": "onChange", "triggerField": "trigger",
                      "actions": [] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    /* ==================== 反例：规则要素与数量 ==================== */

    @Test
    void illegalEventRejected() {
        // onFocus / onBlur 仅事件透传，声明式规则一期不开放配置（§3.4 消费范围）
        String schema = """
                { "fields": [
                  { "field": "trigger", "type": "input", "label": "触发",
                    "rules": [ { "ruleId": "r1", "event": "onFocus", "triggerField": "trigger",
                      "actions": [ { "type": "show", "target": "trigger" } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void unknownEventRejected() {
        String schema = """
                { "fields": [
                  { "field": "trigger", "type": "input", "label": "触发",
                    "rules": [ { "ruleId": "r1", "event": "onDoubleClick", "triggerField": "trigger",
                      "actions": [ { "type": "show", "target": "trigger" } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void triggerFieldMustMatchHost() {
        // 规则随触发字段存放（ADR-06）：triggerField 必须等于宿主字段码
        String schema = """
                { "fields": [
                  { "field": "a", "type": "input", "label": "A" },
                  { "field": "b", "type": "input", "label": "B",
                    "rules": [ { "ruleId": "r1", "event": "onChange", "triggerField": "a",
                      "actions": [ { "type": "show", "target": "a" } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void duplicateRuleIdRejected() {
        String schema = """
                { "fields": [
                  { "field": "a", "type": "input", "label": "A",
                    "rules": [ { "ruleId": "dup", "event": "onChange", "triggerField": "a",
                      "actions": [ { "type": "show", "target": "a" } ] } ] },
                  { "field": "b", "type": "input", "label": "B",
                    "rules": [ { "ruleId": "dup", "event": "onChange", "triggerField": "b",
                      "actions": [ { "type": "show", "target": "b" } ] } ] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void ruleCountExceeded() {
        // 规则总数 > MAX_RULE_COUNT(100)：主表 101 条 + 明细行内 2 条 = 103 条
        StringBuilder rules = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            if (i > 0) {
                rules.append(", ");
            }
            rules.append("{ \"ruleId\": \"r").append(i).append("\", \"event\": \"onChange\", ")
                    .append("\"triggerField\": \"trigger\", ")
                    .append("\"actions\": [ { \"type\": \"show\", \"target\": \"trigger\" } ] }");
        }
        String schema = """
                { "fields": [
                  { "field": "trigger", "type": "input", "label": "触发", "rules": [%s] },
                  { "field": "details", "type": "detailTable", "label": "明细",
                    "children": [
                      { "field": "qty", "type": "number", "label": "数量",
                        "rules": [ { "ruleId": "row_1", "event": "onChange", "triggerField": "qty",
                          "actions": [ { "type": "show", "target": "qty" } ] },
                          { "ruleId": "row_2", "event": "onChange", "triggerField": "qty",
                            "actions": [ { "type": "hide", "target": "qty" } ] } ] } ] } ] }"""
                .formatted(rules);
        List<SysFormSchemaErrorDo> errors = validator.validate(schema);
        assertHasCode(errors, SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED);
        // 资源上限错误不应混入结构错误（样例规则结构合法，仅数量超限）
        assertEquals(1, errors.size());
    }

    /* ==================== 反例：字段总数 ==================== */

    @Test
    void fieldCountExceeded() {
        // 字段总数 201 > MAX_FIELD_COUNT(200)：顶层 100 个 + group 内 101 个
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            if (i > 0) {
                top.append(", ");
            }
            top.append("{ \"field\": \"f").append(i).append("\", \"type\": \"input\", \"label\": \"F").append(i).append("\" }");
        }
        StringBuilder inner = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            if (i > 0) {
                inner.append(", ");
            }
            inner.append("{ \"field\": \"g").append(i).append("\", \"type\": \"input\", \"label\": \"G").append(i).append("\" }");
        }
        String schema = """
                { "fields": [ %s,
                  { "type": "group", "label": "分组", "children": [ %s ] } ] }"""
                .formatted(top, inner);
        List<SysFormSchemaErrorDo> errors = validator.validate(schema);
        assertHasCode(errors, SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED);
        assertNotHasCode(errors, SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    /* ==================== 反例：props 资源上限 ==================== */

    @Test
    void maxlengthExceeded() {
        String schema = """
                { "fields": [
                  { "field": "name", "type": "input", "label": "姓名",
                    "props": { "maxlength": 2001 } } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED);
    }

    @Test
    void rowMaxExceeded() {
        String schema = """
                { "fields": [
                  { "field": "details", "type": "detailTable", "label": "明细",
                    "props": { "rowMax": 201 },
                    "children": [] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED);
    }

    @Test
    void rowMinGreaterThanRowMaxRejected() {
        String schema = """
                { "fields": [
                  { "field": "details", "type": "detailTable", "label": "明细",
                    "props": { "rowMin": 10, "rowMax": 5 },
                    "children": [] } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void illegalPermissionRejected() {
        String schema = """
                { "fields": [
                  { "field": "name", "type": "input", "label": "姓名",
                    "props": { "permission": "invisible" } } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    /* ==================== 反例：级联（R08 第 4 条） ==================== */

    @Test
    void cascadeParentNotDataTypeRejected() {
        // parentField 指向内容类组件（detailTable 容器 / 布局类）→ 拒绝
        String schema = """
                { "fields": [
                  { "field": "details", "type": "detailTable", "label": "明细", "children": [] },
                  { "field": "district", "type": "cascade", "label": "区域",
                    "props": { "parentField": "details" } } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void cascadeParentSelfReferenceRejected() {
        String schema = """
                { "fields": [
                  { "field": "district", "type": "cascade", "label": "区域",
                    "props": { "parentField": "district" } } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void cascadeParentNotExistRejected() {
        String schema = """
                { "fields": [
                  { "field": "district", "type": "cascade", "label": "区域",
                    "props": { "parentField": "ghost" } } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void cascadeShowConditionIllegalOperatorRejected() {
        String schema = """
                { "fields": [
                  { "field": "city", "type": "input", "label": "城市" },
                  { "field": "district", "type": "cascade", "label": "区域",
                    "props": { "parentField": "city",
                      "options": [ { "label": "A区", "value": "a",
                        "showCondition": { "operator": "matches", "value": "^北" } } ] } } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void cascadeShowConditionIllegalValueRejected() {
        // showCondition operator=in 但 value 非数组
        String schema = """
                { "fields": [
                  { "field": "city", "type": "input", "label": "城市" },
                  { "field": "district", "type": "cascade", "label": "区域",
                    "props": { "parentField": "city",
                      "options": [ { "label": "A区", "value": "a",
                        "showCondition": { "operator": "in", "value": "北京" } } ] } } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void cascadeShowConditionNonObjectRejected() {
        String schema = """
                { "fields": [
                  { "field": "city", "type": "input", "label": "城市" },
                  { "field": "district", "type": "cascade", "label": "区域",
                    "props": { "parentField": "city",
                      "options": [ { "label": "A区", "value": "a",
                        "showCondition": "city == '北京'" } ] } } ] }""";
        assertHasCode(validator.validate(schema), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    /* ==================== 反例：体积与解析 ==================== */

    @Test
    void schemaSizeExceeded() {
        // 构造超过 MAX_SCHEMA_JSON_LENGTH(512 KB) 的原文：解析前先查体积
        StringBuilder padding = new StringBuilder();
        padding.append("{ \"fields\": [ { \"field\": \"f\", \"type\": \"input\", \"label\": \"");
        padding.append("长".repeat(SysFormConstants.MAX_SCHEMA_JSON_LENGTH));
        padding.append("\" } ] }");
        List<SysFormSchemaErrorDo> errors = validator.validate(padding.toString());
        assertHasCode(errors, SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED);
        // 体积超限不应再报解析失败（体积先行短路）
        assertEquals(1, errors.size());
    }

    @Test
    void malformedJsonRejected() {
        List<SysFormSchemaErrorDo> errors = validator.validate("{ fields: ");
        assertHasCode(errors, SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void blankJsonRejected() {
        assertHasCode(validator.validate(""), SysFormConstants.CS_FORM_SCHEMA_INVALID);
        assertHasCode(validator.validate("   "), SysFormConstants.CS_FORM_SCHEMA_INVALID);
        assertHasCode(validator.validate((String) null), SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void missingFieldsRejected() {
        assertHasCode(validator.validate("{ \"formCode\": \"f1\" }"),
                SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }

    @Test
    void nullSchemaRejected() {
        assertHasCode(validator.validate((SysFormSchemaDo) null),
                SysFormConstants.CS_FORM_SCHEMA_INVALID);
    }
}

package com.eric.base.sys.form.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表单 Schema 协议 POJO Jackson 序列化测试（F15-BE-001，数据库分册 §9.2「SysFormSchemaDo
 * 协议 POJO Jackson 序列化（样例 Schema 往返一致单测）」）
 *
 * @author Eric
 * @date 2026/11/24
 * @description 以功能设计 §2.3 完整示例 Schema（采购申请表——含明细表行内公式 / 主表合计
 *              公式 / 条件显隐规则 / 动态必填 / 初始隐藏字段，JSON 以文本常量内嵌）为样例：
 *              <ul>
 *              <li>反序列化为 SysFormSchemaDo → 再序列化 → Jackson 树等价断言
 *                  （JsonNode equals，避免字段顺序敏感）；</li>
 *              <li>树等价要求字段增删向后兼容：null 属性不输出（NON_NULL）、未知键
 *                  反序列化忽略（ignoreUnknown）——以扩展键注入断言登记；</li>
 *              <li>明细行结构与栅格列承载形态抽验。</li>
 *              </ul>
 */
class SysFormSchemaDoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 功能设计 §2.3 完整示例 Schema（采购申请表）：覆盖明细表（数量×单价=金额行内公式）+
     * 主表公式合计 + 条件显隐规则（rule_001~004）+ 动态必填 + 初始隐藏字段。
     * 键顺序与文档示例一致（树等价断言不依赖顺序）。
     */
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
    static void buildMapper() {
        // 协议 POJO 经类级 @JsonIgnoreProperties(ignoreUnknown = true) 自行声明忽略未知键
        //（Jackson 默认全局开启 FAIL_ON_UNKNOWN_PROPERTIES，类注解优先于全局配置），
        // 未知键忽略行为由 unknownTopLevelKeyIsIgnored / unknownFieldKeyIsIgnored 断言证明
    }

    @Test
    void deserializesSampleSchema() throws Exception {
        SysFormSchemaDo schema = MAPPER.readValue(SAMPLE_SCHEMA_JSON, SysFormSchemaDo.class);

        assertEquals("1a2b3c4d5e6f708192a3b4c5d6e7f809", schema.getFormId());
        assertEquals("purchase_apply", schema.getFormCode());
        assertEquals("采购申请表", schema.getFormName());
        assertEquals(1, schema.getVersion());
        assertEquals(8, schema.getFields().size());

        // 明细表行结构（§2.1：children 为行内字段对象数组，行内 formula）
        SysFormFieldDo detailTable = schema.getFields().get(2);
        assertEquals("purchaseDetails", detailTable.getField());
        assertEquals("detailTable", detailTable.getType());
        assertEquals(50, detailTable.getProps().get("rowMax"));
        assertEquals(4, detailTable.getChildren().size());
        SysFormFieldDo amount = detailTable.getChildren().get(3);
        assertEquals("amount", amount.getField());
        assertEquals("formula", amount.getType());
        assertEquals("ROUND(${quantity} * ${price}, 2)", amount.getProps().get("expression"));

        // 主表合计公式 + 条件显隐规则（§3.1 DSL 结构）
        SysFormFieldDo totalAmount = schema.getFields().get(3);
        assertEquals("SUM(${purchaseDetails.amount})", totalAmount.getProps().get("expression"));
        assertEquals(2, totalAmount.getRules().size());
        SysFormRuleDo rule003 = totalAmount.getRules().get(0);
        assertEquals("rule_003", rule003.getRuleId());
        assertEquals("onChange", rule003.getEvent());
        assertEquals("totalAmount", rule003.getTriggerField());
        assertEquals("gt", rule003.getCondition().getOperator());
        assertEquals(10000, ((Number) rule003.getCondition().getValue()).intValue());
        assertEquals("show", rule003.getActions().get(0).getType());
        assertEquals("gmComment", rule003.getActions().get(0).getTarget());

        // 动态必填 + 静态基础必填合并承载（setRequired value 布尔）
        SysFormRuleDo rule001 = schema.getFields().get(4).getRules().get(0);
        assertEquals("setRequired", rule001.getActions().get(0).getType());
        assertEquals(Boolean.TRUE, rule001.getActions().get(0).getValue());

        // 初始隐藏字段（props.hidden）
        assertEquals(Boolean.TRUE, schema.getFields().get(5).getProps().get("hidden"));
    }

    @Test
    void roundTripIsTreeEquivalent() throws Exception {
        // 反序列化 → 再序列化 → Jackson 树等价（JsonNode equals，避免字段顺序敏感）
        JsonNode original = MAPPER.readTree(SAMPLE_SCHEMA_JSON);
        SysFormSchemaDo schema = MAPPER.readValue(SAMPLE_SCHEMA_JSON, SysFormSchemaDo.class);
        JsonNode reparsed = MAPPER.readTree(MAPPER.writeValueAsString(schema));

        assertEquals(original, reparsed);
    }

    @Test
    void unknownTopLevelKeyIsIgnored() throws Exception {
        // 向后兼容登记：未知顶层键反序列化忽略（@JsonIgnoreProperties(ignoreUnknown = true)），
        // 字段增删不破坏旧版本 Schema 解析
        String withUnknown = SAMPLE_SCHEMA_JSON.replaceFirst(
                "\\{", "{ \"unknownFutureKey\": { \"x\": 1 }, ");
        SysFormSchemaDo schema = MAPPER.readValue(withUnknown, SysFormSchemaDo.class);
        assertEquals("purchase_apply", schema.getFormCode());
        assertEquals(8, schema.getFields().size());
    }

    @Test
    void unknownFieldKeyIsIgnored() throws Exception {
        // 字段对象未知键同样忽略（前端物料扩展属性先行、后端协议随行）
        SysFormFieldDo field = new SysFormFieldDo();
        field.setField("extra");
        field.setType("input");
        field.setLabel("扩展");
        String json = MAPPER.writeValueAsString(field);
        assertTrue(json.contains("\"field\""));
        // null 属性不输出（NON_NULL）：未赋值的 props/rules/children 不落 JSON
        assertFalse(json.contains("props"));
        assertFalse(json.contains("rules"));
        assertFalse(json.contains("children"));
        // 未知键反序列化忽略
        SysFormFieldDo parsed = MAPPER.readValue(
                json.replace("}", ", \"futureProp\": 1 }"), SysFormFieldDo.class);
        assertEquals("extra", parsed.getField());
    }

    @Test
    void gridColumnCarriesSpanAndFields() throws Exception {
        // 栅格列对象承载形态（任务开放点定稿：SysFormFieldDo 以 span/fields 承载，
        // 配合 NON_NULL 序列化为文档形态 { span, fields[] }）
        SysFormFieldDo grid = new SysFormFieldDo();
        grid.setType("grid");
        grid.setLabel("栅格");

        SysFormFieldDo col = new SysFormFieldDo();
        col.setSpan(12);
        SysFormFieldDo inner = new SysFormFieldDo();
        inner.setField("name");
        inner.setType("input");
        inner.setLabel("姓名");
        col.setFields(List.of(inner));
        grid.setChildren(List.of(col));

        String json = MAPPER.writeValueAsString(grid);
        JsonNode node = MAPPER.readTree(json);
        JsonNode colNode = node.get("children").get(0);
        assertEquals(12, colNode.get("span").asInt());
        assertTrue(colNode.has("fields"));
        assertFalse(colNode.has("field"));
        assertFalse(colNode.has("type"));
        assertFalse(colNode.has("label"));

        // 往返一致
        SysFormFieldDo parsed = MAPPER.readValue(json, SysFormFieldDo.class);
        assertEquals(12, parsed.getChildren().get(0).getSpan());
        assertEquals("name", parsed.getChildren().get(0).getFields().get(0).getField());
        assertTrue(parsed.getChildren().get(0).isGridColumn());
        assertFalse(parsed.isGridColumn());
    }

    @Test
    void rulePayloadRoundTrip() throws Exception {
        // 规则 DSL 结构（§3.1）往返：condition 可空 = 无条件规则、setValidate 载荷
        SysFormRuleDo rule = new SysFormRuleDo();
        rule.setRuleId("rule_100");
        rule.setEvent("onAdd");
        rule.setTriggerField("purchaseDetails");
        // condition 为 null（无条件规则）
        SysFormRuleActionDo setValue = new SysFormRuleActionDo();
        setValue.setType("setValue");
        setValue.setTarget("productName");
        setValue.setValue("默认品名");
        SysFormRuleValidateDo validate = new SysFormRuleValidateDo();
        validate.setMin(0);
        validate.setMax(100);
        validate.setMessage("数量 0~100");
        SysFormRuleActionDo setValidate = new SysFormRuleActionDo();
        setValidate.setType("setValidate");
        setValidate.setTarget("quantity");
        setValidate.setValidate(validate);
        rule.setActions(List.of(setValue, setValidate));

        String json = MAPPER.writeValueAsString(rule);
        JsonNode node = MAPPER.readTree(json);
        // condition null 不输出
        assertFalse(node.has("condition"));
        // validate 载荷仅覆盖所列项语义（min/max/message 落 JSON）
        JsonNode validateNode = node.get("actions").get(1).get("validate");
        assertEquals(0, validateNode.get("min").asInt());
        assertEquals(100, validateNode.get("max").asInt());
        assertFalse(validateNode.has("pattern"));

        SysFormRuleDo parsed = MAPPER.readValue(json, SysFormRuleDo.class);
        assertNull(parsed.getCondition());
        assertEquals("默认品名", parsed.getActions().get(0).getValue());
        assertEquals("数量 0~100", parsed.getActions().get(1).getValidate().getMessage());
    }

    @Test
    void malformedJsonFails() {
        // 非法 JSON 结构（类型不匹配）反序列化失败行为登记：抛 Jackson 异常
        assertThrows(Exception.class, () -> MAPPER.readValue(
                "{\"version\": \"not-a-number\", \"fields\": []}", SysFormSchemaDo.class));
        assertThrows(Exception.class, () -> MAPPER.readValue(
                "{\"fields\": {}}", SysFormSchemaDo.class));
    }

    @Test
    void propsMapKeepsArbitraryExtensionKeys() {
        // props 为固定属性项 + 扩展属性项混合承载（对齐 SysTaskMain.fieldParams 口径）：
        // 任意扩展键以 Map 原样保留，不建全量 POJO
        SysFormFieldDo field = new SysFormFieldDo();
        field.setField("avatar");
        field.setType("attachment");
        field.setLabel("头像");
        field.setProps(Map.of("maxCount", 3, "fileType", "jpg,png", "required", true));
        assertEquals(3, field.getProps().get("maxCount"));
        assertEquals("jpg,png", field.getProps().get("fileType"));
        assertEquals(Boolean.TRUE, field.getProps().get("required"));
    }
}

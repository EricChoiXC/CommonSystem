package com.eric.base.sys.form.service.engine;

import com.eric.base.sys.form.model.SysFormDataErrorDo;
import com.eric.base.sys.form.model.SysFormRuleActionDo;
import com.eric.base.sys.form.model.SysFormRuleConditionDo;
import com.eric.base.sys.form.model.SysFormRuleDo;
import com.eric.base.sys.form.model.SysFormRuleValidateDo;
import com.eric.base.sys.form.model.SysFormFieldDo;
import com.eric.base.sys.form.model.SysFormSchemaDo;
import com.eric.base.sys.form.properties.SysFormConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表单服务端动态校验与字段权限裁剪引擎测试
 *
 * @author Eric
 * @date 2026/11/24
 * @description 覆盖任务 F15-BE-004 验证方式全项（纯引擎 + 真实 FormDataBasicValidator
 *              合并裁决组合，无 Dao 依赖——形态对齐 FormDataBasicValidatorTest 先例）：
 *              条件必填正反例（付款方式=转账时银行账号必填，§2.3 示例同构）、hidden 字段
 *              提交裁剪（显隐复算终态 ∪ permission=hidden 一律裁剪不落库，读取时不可见）、
 *              setRequired 动态覆盖静态必填基线（hidden 字段不参与必填校验）、setValidate
 *              条件范围 / 条件正则（ruleId 溯源 + 自定义文案）、联动 setValue ${引用} 解析
 *              与幂等短路收敛、明细行内规则只作用于本行（rowIndex 上下文隔离）与明细表宿主
 *              规则作用于全部行、onLoad 交互历史事件不复算、循环规则上限触发
 *              {@code CS_FORM_RULE_CYCLE_EXCEEDED}（深度熔断：setValue/clearValue 互写
 *              振荡；次数熔断：单次求值动作执行计数达 MAX_RULE_EXECUTIONS_PER_CYCLE）、
 *              readonly 基线比对拒绝（assertReadonlyBaseline，二期实例更新接口预留通道——
 *              一期 save 无基线跳过）；F15-BE-005 级联选项复核（assertCascadeOptions，S5
 *              R08 第 3 条服务端统一入口）：提交值 ∈ 父项条件允许选项集放行、越权选项
 *              CS_FORM_CASCADE_OPTION_INVALID、无 showCondition 选项恒允许、multi 多选
 *              逐元素复核、父项为空按条件求值（empty 条件允许 / equal 条件拒绝）、行内
 *              级联逐行复核（行上下文父子）、未配置 parentField/选项与空值跳过复核。
 */
class FormValidationEngineTest {

    private final FormValidationEngine engine = new FormValidationEngine();

    private final FormDataBasicValidator validator = new FormDataBasicValidator();

    // ---------------------------------------------------------------- 条件必填正反例（§2.3 rule_001/002 同构）

    @Test
    void transferRequiresBankAccountAndKeepsValue() {
        SysFormSchemaDo schema = payTypeSchema();
        Map<String, Object> payload = payload("payType", "transfer");

        FormValidationEngine.RuleReplay replay = engine.replay(schema, payload);

        assertFalse(replay.getHiddenFieldCodes().contains("bankAccount"),
                "付款方式=转账时规则 show 生效，银行账号可见不裁剪: " + replay.getHiddenFieldCodes());
        FormDynamicState.RequiredOverride required = replay.getDynamicState().mainRequired("bankAccount");
        assertTrue(required.isRequired(), "setRequired(true) 动态覆盖生效");
        assertEquals("rule_001", required.getRuleId(), "动态必填覆盖保留触发规则 ID 溯源");

        List<SysFormDataErrorDo> errors = validator.validate(schema, replay.getPayload(), replay.getDynamicState());
        assertEquals(1, errors.size(), "条件必填正例反 soak：转账未填银行账号报错: " + errors);
        assertEquals("bankAccount", errors.get(0).getFieldCode());
        assertEquals("rule_001", errors.get(0).getRuleId(), "errors 数组含 fieldCode / 规则 ID / 文案（验收标准）");
        assertTrue(errors.get(0).getMessage().contains("银行账号"), errors.get(0).getMessage());
    }

    @Test
    void cashRelaxesDynamicRequiredAndTrimsBankAccount() {
        SysFormSchemaDo schema = payTypeSchema();
        Map<String, Object> payload = payload("payType", "cash", "bankAccount", "6222020200112233445");

        FormValidationEngine.RuleReplay replay = engine.replay(schema, payload);

        assertTrue(replay.getHiddenFieldCodes().contains("bankAccount"),
                "付款方式=现金时规则 hide 生效，银行账号进入隐藏集");
        assertFalse(replay.getPayload().containsKey("bankAccount"),
                "hidden 字段提交值裁剪不落库（§3.6 持久层②）");
        assertFalse(required(replay, "bankAccount"), "setRequired(false) 动态覆盖生效");

        List<SysFormDataErrorDo> errors = validator.validate(schema, replay.getPayload(), replay.getDynamicState());
        assertTrue(errors.isEmpty(), "条件必填反例：非转账时银行账号不要求必填，裁剪后无错误: " + errors);
    }

    @Test
    void staticRequiredExemptWhenRuleHidden() {
        // hidden 字段不参与必填校验：静态必填 + 规则判定隐藏 → 豁免（不阻断合法提交）
        SysFormFieldDo note = field("note", "textarea", "备注说明");
        note.setProps(new LinkedHashMap<>(Map.of("required", true)));
        SysFormFieldDo status = field("status", "select", "状态");
        status.setProps(Map.of("multi", false, "options", List.of(option("on", "启用"), option("off", "停用"))));
        status.setRules(List.of(
                rule("rule_h1", "status", condition("equal", "off"), action("hide", "note", null)),
                rule("rule_h2", "status", condition("equal", "on"), action("show", "note", null))));
        SysFormSchemaDo schema = schemaOf(note, status);

        Map<String, Object> hidden = payload("status", "off");
        FormValidationEngine.RuleReplay hiddenReplay = engine.replay(schema, hidden);
        assertTrue(validator.validate(schema, hiddenReplay.getPayload(), hiddenReplay.getDynamicState()).isEmpty(),
                "规则隐藏的静态必填字段豁免必填（hidden 字段不参与必填校验）");

        Map<String, Object> visible = payload("status", "on");
        FormValidationEngine.RuleReplay visibleReplay = engine.replay(schema, visible);
        List<SysFormDataErrorDo> errors = validator.validate(schema, visibleReplay.getPayload(),
                visibleReplay.getDynamicState());
        assertEquals(1, errors.size(), "可见态静态必填照常裁决: " + errors);
        assertNull(errors.get(0).getRuleId(), "静态基线必填错误 ruleId 恒 null（无 setRequired 覆盖）");
    }

    // ---------------------------------------------------------------- permission=hidden 权限裁剪

    @Test
    void permissionHiddenAlwaysTrimmedOnSubmitAndRead() {
        SysFormFieldDo applicant = field("applicant", "input", "申请人");
        SysFormFieldDo secret = field("secretField", "input", "内部备注");
        secret.setProps(Map.of("permission", "hidden"));
        SysFormSchemaDo schema = schemaOf(applicant, secret);

        Map<String, Object> payload = payload("applicant", "张三", "secretField", "越权注入值");

        FormValidationEngine.RuleReplay replay = engine.replay(schema, payload);
        assertFalse(replay.getPayload().containsKey("secretField"),
                "permission=hidden 字段无论渲染态一律裁剪不落库（§3.6 / 分册 §4.3 S3）");
        assertTrue(replay.getHiddenFieldCodes().contains("secretField"));

        List<SysFormDataErrorDo> errors = validator.validate(schema, replay.getPayload(), replay.getDynamicState());
        assertTrue(errors.isEmpty(), "裁剪后载荷校验通过（隐藏字段值不参与校验）: " + errors);

        Map<String, Object> stored = payload("applicant", "张三", "secretField", "存量遗留值");
        Map<String, Object> clipped = engine.clipForRead(schema, stored);
        assertFalse(clipped.containsKey("secretField"),
                "permission=hidden 字段读取不下发（要点第 7 步红线：前端隐藏 ≠ 后端禁止访问）");
        assertEquals("张三", clipped.get("applicant"), "可见字段照常下发");
        assertTrue(stored.containsKey("secretField"), "读取裁剪不改写存量数据本身");
    }

    // ---------------------------------------------------------------- setValidate 条件范围 / 条件正则

    @Test
    void setValidateEnforcesConditionalRangeWithRuleId() {
        SysFormFieldDo amount = field("amount", "number", "采购金额");
        SysFormFieldDo level = field("level", "select", "审批层级");
        level.setProps(Map.of("multi", false, "options", List.of(option("high", "高"), option("low", "低"))));
        level.setRules(List.of(
                rule("rule_v1", "level", condition("equal", "high"),
                        setValidate("amount", validateOf(10, 100, null, null)))));
        SysFormSchemaDo schema = schemaOf(amount, level);

        Map<String, Object> below = payload("level", "high", "amount", 5);
        FormValidationEngine.RuleReplay belowReplay = engine.replay(schema, below);
        List<SysFormDataErrorDo> belowErrors = validator.validate(schema, belowReplay.getPayload(),
                belowReplay.getDynamicState());
        assertEquals(1, belowErrors.size(), "条件范围下限违规: " + belowErrors);
        assertEquals("amount", belowErrors.get(0).getFieldCode());
        assertEquals("rule_v1", belowErrors.get(0).getRuleId(), "条件范围错误 ruleId 溯源触发规则");
        assertTrue(belowErrors.get(0).getMessage().contains("不能小于 10"), belowErrors.get(0).getMessage());

        Map<String, Object> above = payload("level", "high", "amount", 150);
        FormValidationEngine.RuleReplay aboveReplay = engine.replay(schema, above);
        List<SysFormDataErrorDo> aboveErrors = validator.validate(schema, aboveReplay.getPayload(),
                aboveReplay.getDynamicState());
        assertEquals(1, aboveErrors.size(), "条件范围上限违规: " + aboveErrors);
        assertTrue(aboveErrors.get(0).getMessage().contains("不能大于 100"), aboveErrors.get(0).getMessage());

        Map<String, Object> within = payload("level", "high", "amount", 50);
        FormValidationEngine.RuleReplay withinReplay = engine.replay(schema, within);
        assertTrue(validator.validate(schema, withinReplay.getPayload(), withinReplay.getDynamicState()).isEmpty(),
                "条件范围内通过");

        Map<String, Object> relaxed = payload("level", "low", "amount", 5);
        FormValidationEngine.RuleReplay relaxedReplay = engine.replay(schema, relaxed);
        assertTrue(validator.validate(schema, relaxedReplay.getPayload(), relaxedReplay.getDynamicState()).isEmpty(),
                "条件不匹配时动态校验不生效（仅条件触发覆盖）");
    }

    @Test
    void setValidateEnforcesConditionalPatternWithCustomMessage() {
        SysFormFieldDo account = field("account", "input", "账号");
        SysFormFieldDo channel = field("channel", "select", "渠道");
        channel.setProps(Map.of("multi", false, "options", List.of(option("bank", "银行"))));
        channel.setRules(List.of(
                rule("rule_v2", "channel", condition("equal", "bank"),
                        setValidate("account", validateOf(null, null, "^A.{5}$", "须以 A 开头后接 5 位字符")))));
        SysFormSchemaDo schema = schemaOf(account, channel);

        Map<String, Object> payload = payload("channel", "bank", "account", "B12345");
        FormValidationEngine.RuleReplay replay = engine.replay(schema, payload);
        List<SysFormDataErrorDo> errors = validator.validate(schema, replay.getPayload(), replay.getDynamicState());
        assertEquals(1, errors.size(), "条件正则违规: " + errors);
        assertEquals("rule_v2", errors.get(0).getRuleId());
        assertTrue(errors.get(0).getMessage().contains("须以 A 开头"), "自定义文案优先于默认文案: " + errors.get(0).getMessage());
    }

    // ---------------------------------------------------------------- 联动复算（setValue ${引用} / clearValue / 幂等短路）

    @Test
    void setValueResolvesFieldReferenceAndCallerPayloadUntouched() {
        SysFormFieldDo dept = field("dept", "input", "部门");
        SysFormFieldDo owner = field("owner", "input", "负责人");
        dept.setRules(List.of(rule("rule_l1", "dept", null, action("setValue", "owner", "${dept}"))));
        SysFormSchemaDo schema = schemaOf(dept, owner);

        Map<String, Object> payload = payload("dept", "研发部");
        FormValidationEngine.RuleReplay replay = engine.replay(schema, payload);

        assertEquals("研发部", replay.getPayload().get("owner"), "setValue ${字段码} 引用按当前状态联动取值（§3.3）");
        assertFalse(payload.containsKey("owner"), "引擎防御性拷贝，不改写调用方载荷");
        assertTrue(replay.getHiddenFieldCodes().isEmpty(), "联动不产生隐藏字段");
    }

    @Test
    void idempotentSetValueConvergesWithoutCascade() {
        // 幂等短路（§3.5 第 3 层）：setValue 目标值与当前值相等时不回写不触发后续规则链
        SysFormFieldDo dept = field("dept", "input", "部门");
        SysFormFieldDo owner = field("owner", "input", "负责人");
        dept.setRules(List.of(rule("rule_l1", "dept", null, action("setValue", "owner", "${dept}"))));
        SysFormSchemaDo schema = schemaOf(dept, owner);

        Map<String, Object> payload = payload("dept", "研发部", "owner", "研发部");
        FormValidationEngine.RuleReplay replay = engine.replay(schema, payload);

        assertEquals("研发部", replay.getPayload().get("owner"), "值相等不回写，单轮收敛无熔断");
    }

    @Test
    void clearValueRemovesTargetFromPayload() {
        SysFormFieldDo flag = field("flag", "select", "重置开关");
        flag.setProps(Map.of("multi", false, "options", List.of(option("reset", "重置"))));
        SysFormFieldDo memo = field("memo", "input", "备注");
        flag.setRules(List.of(rule("rule_l2", "flag", condition("equal", "reset"),
                action("clearValue", "memo", null))));
        SysFormSchemaDo schema = schemaOf(flag, memo);

        Map<String, Object> payload = payload("flag", "reset", "memo", "旧备注");
        FormValidationEngine.RuleReplay replay = engine.replay(schema, payload);

        assertFalse(replay.getPayload().containsKey("memo"), "clearValue 显式清值（与 hide 语义严格分离，ADR-08）");
    }

    // ---------------------------------------------------------------- 明细行上下文（行内规则只作用于本行）

    @Test
    void rowScopedRuleAppliesToItsOwnRowOnly() {
        SysFormFieldDo detail = field("items", "detailTable", "采购明细");
        detail.setProps(Map.of("rowMin", 1, "rowMax", 50));
        SysFormFieldDo quantity = field("quantity", "number", "数量");
        SysFormFieldDo tag = field("tag", "input", "行内标记");
        quantity.setRules(List.of(rule("rule_r1", "quantity", condition("gt", 10),
                action("setRequired", "tag", Boolean.TRUE))));
        detail.setChildren(List.of(quantity, tag));
        SysFormSchemaDo schema = schemaOf(detail);

        Map<String, Object> row0 = new LinkedHashMap<>();
        row0.put("quantity", 20); // 触发条件（gt 10）且 tag 缺省 → 本行违规
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("quantity", 5);  // 条件不匹配 → 本行不受影响（行内规则只作用于本行）
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", List.of(row0, row1));

        FormValidationEngine.RuleReplay replay = engine.replay(schema, payload);
        List<SysFormDataErrorDo> errors = validator.validate(schema, replay.getPayload(), replay.getDynamicState());

        assertEquals(1, errors.size(), "仅第 1 行触发行内动态必填: " + errors);
        assertEquals("tag", errors.get(0).getFieldCode());
        assertEquals(Integer.valueOf(0), errors.get(0).getRowIndex(), "行内错误携带 0 起始行号");
        assertEquals("rule_r1", errors.get(0).getRuleId());
    }

    @Test
    void detailHostRuleAppliesToAllRows() {
        // 明细表宿主规则（triggerField=明细表字段码）：条件对行数组求值，动作按行上下文作用于全部行
        SysFormFieldDo detail = field("items", "detailTable", "采购明细");
        detail.setProps(Map.of("rowMin", 1, "rowMax", 50));
        SysFormFieldDo productName = field("productName", "input", "商品名称");
        detail.setRules(List.of(rule("rule_r2", "items", condition("notEmpty", null),
                action("setRequired", "productName", Boolean.TRUE))));
        detail.setChildren(List.of(productName));
        SysFormSchemaDo schema = schemaOf(detail);

        Map<String, Object> row0 = new LinkedHashMap<>();
        row0.put("productName", "电脑");
        Map<String, Object> row1 = new LinkedHashMap<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", List.of(row0, row1));

        FormValidationEngine.RuleReplay replay = engine.replay(schema, payload);
        List<SysFormDataErrorDo> errors = validator.validate(schema, replay.getPayload(), replay.getDynamicState());

        assertEquals(1, errors.size(), "非空明细表全行动态必填，仅缺省行报错: " + errors);
        assertEquals("productName", errors.get(0).getFieldCode());
        assertEquals(Integer.valueOf(1), errors.get(0).getRowIndex());
    }

    // ---------------------------------------------------------------- 事件域（onLoad 交互历史事件不复算）

    @Test
    void onLoadRulesNotReplayedAtSave() {
        // onLoad 为初始化交互事件：动作效果已由提交载荷承载，服务端复算不重放（重放会覆盖用户编辑值）
        SysFormFieldDo dept = field("dept", "input", "部门");
        SysFormFieldDo owner = field("owner", "input", "负责人");
        SysFormRuleDo onLoadRule = rule("rule_o1", "dept", null, action("setValue", "owner", "初始化值"));
        onLoadRule.setEvent("onLoad");
        dept.setRules(List.of(onLoadRule));
        SysFormSchemaDo schema = schemaOf(dept, owner);

        Map<String, Object> payload = payload("dept", "研发部");
        FormValidationEngine.RuleReplay replay = engine.replay(schema, payload);

        assertFalse(replay.getPayload().containsKey("owner"), "onLoad 规则不参与保存链复算");
    }

    // ---------------------------------------------------------------- 循环防护熔断（§3.5，CS_FORM_RULE_CYCLE_EXCEEDED）

    @Test
    void cyclicWritePairTripsChainDepthBreaker() {
        // 深度熔断（§3.5 第 2 层）：setValue/clearValue 互写振荡——B 空 → 写 B，B 有值 → 清 B，
        // 级联超过 MAX_RULE_CHAIN_DEPTH 即熔断（幂等短路无法切断的振荡形态）
        SysFormFieldDo loop = field("loopB", "input", "互写字段");
        loop.setRules(List.of(
                rule("rule_c1", "loopB", condition("empty", null), action("setValue", "loopB", "x")),
                rule("rule_c2", "loopB", condition("equal", "x"), action("clearValue", "loopB", null))));
        SysFormSchemaDo schema = schemaOf(loop);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> engine.replay(schema, new LinkedHashMap<>()));
        assertTrue(error.getMessage().startsWith(SysFormConstants.CS_FORM_RULE_CYCLE_EXCEEDED + ":"),
                "循环规则上限触发 CS_FORM_*（验收标准），实际: " + error.getMessage());
        assertTrue(error.getMessage().contains("级联深度"), error.getMessage());
    }

    @Test
    void ruleStormTripsExecutionCountBreaker() {
        // 次数熔断（§3.5 第 1 层）：单次求值动作执行计数达 MAX_RULE_EXECUTIONS_PER_CYCLE 即熔断
        SysFormFieldDo storm = field("storm", "input", "风暴字段");
        List<SysFormRuleDo> rules = new ArrayList<>();
        for (int i = 0; i < SysFormConstants.MAX_RULE_EXECUTIONS_PER_CYCLE + 10; i++) {
            rules.add(rule("rule_s" + i, "storm", null, action("setValue", "storm", "v" + i)));
        }
        storm.setRules(rules);
        SysFormSchemaDo schema = schemaOf(storm);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> engine.replay(schema, new LinkedHashMap<>()));
        assertTrue(error.getMessage().startsWith(SysFormConstants.CS_FORM_RULE_CYCLE_EXCEEDED + ":"),
                "动作执行计数熔断，实际: " + error.getMessage());
        assertTrue(error.getMessage().contains("执行次数"), error.getMessage());
    }

    // ---------------------------------------------------------------- readonly 基线比对（二期实例更新接口预留通道）

    @Test
    void readonlyBaselineMismatchRejected() {
        SysFormFieldDo applicant = field("applicant", "input", "申请人");
        SysFormFieldDo locked = field("locked", "input", "锁定字段");
        locked.setProps(Map.of("permission", "readonly"));
        SysFormSchemaDo schema = schemaOf(applicant, locked);

        Map<String, Object> payload = payload("applicant", "张三", "locked", "被篡改值");
        Map<String, Object> baseline = payload("locked", "既有值");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> engine.assertReadonlyBaseline(schema, payload, baseline));
        assertTrue(error.getMessage().startsWith(SysFormConstants.CS_FORM_DATA_INVALID + ":"),
                "readonly 提交值与既有值不一致拒绝（基线比对通道），实际: " + error.getMessage());
        assertTrue(error.getMessage().contains("locked"), error.getMessage());
    }

    @Test
    void readonlyBaselineMatchingOrAbsentPasses() {
        SysFormFieldDo locked = field("locked", "input", "锁定字段");
        locked.setProps(Map.of("permission", "readonly"));
        SysFormSchemaDo schema = schemaOf(locked);

        // 一致值放行
        engine.assertReadonlyBaseline(schema, payload("locked", "既有值"), payload("locked", "既有值"));
        // 一期 save 新增落库无基线可比对：不传基线整体跳过，提交值照常落库（分册 §4.3 S7 定稿）
        engine.assertReadonlyBaseline(schema, payload("locked", "任意值"), null);
        engine.assertReadonlyBaseline(schema, payload("locked", "任意值"), new LinkedHashMap<>());
        // 基线中不存在的字段无既有值可比对，跳过
        engine.assertReadonlyBaseline(schema, payload("locked", "任意值"), payload("other", "x"));
    }

    // ---------------------------------------------------------------- 读取层裁剪（§3.6 ③：存量数据复算同一口径）

    @Test
    void clipForReadRecomputesRuleHiddenFieldsFromStoredData() {
        SysFormSchemaDo schema = payTypeSchema();

        Map<String, Object> stored = payload("applicant", "张三", "payType", "cash", "bankAccount", "6222020200112233445");
        Map<String, Object> clipped = engine.clipForRead(schema, stored);

        assertFalse(clipped.containsKey("bankAccount"),
                "按存量数据复算同一口径：提交时刻被裁剪的隐藏字段不下发（§3.6 读取层③）");
        assertEquals("张三", clipped.get("applicant"), "可见字段不下发裁剪");
        assertTrue(stored.containsKey("bankAccount"), "读取裁剪不改写存量数据");
    }

    @Test
    void clipForReadKeepsVisibleStoredField() {
        SysFormSchemaDo schema = payTypeSchema();

        Map<String, Object> stored = payload("applicant", "张三", "payType", "transfer", "bankAccount", "6222020200112233445");
        Map<String, Object> clipped = engine.clipForRead(schema, stored);

        assertEquals("6222020200112233445", clipped.get("bankAccount"), "复算可见的存量字段照常下发");
    }

    @Test
    void clipForReadNullSafePassthrough() {
        Map<String, Object> stored = payload("a", 1);
        assertEquals(stored, engine.clipForRead(null, stored), "Schema 缺失防御性原样下发");
        assertNull(engine.clipForRead(payTypeSchema(), null), "载荷缺失原样返回");
    }

    // ---------------------------------------------------------------- 级联选项复核（F15-BE-005，S5，R08 第 3 条）

    /** 提交值 ∈ 父项条件允许选项集：放行（与前端本地过滤同一条件求值语义） */
    @Test
    void cascadeOptionAllowedWhenParentConditionMatches() {
        SysFormSchemaDo schema = cascadeSchema();
        Map<String, Object> payload = payload("customerType", "enterprise", "channel", "keyAccount");

        engine.assertCascadeOptions(schema, payload); // 不抛即通过
    }

    /** 提交值 ∉ 父项条件允许选项集（被过滤选项越权提交）：CS_FORM_CASCADE_OPTION_INVALID */
    @Test
    void cascadeOptionRejectedWhenParentConditionNotMatched() {
        SysFormSchemaDo schema = cascadeSchema();
        // personal 条件下 keyAccount 选项被过滤（showCondition equal enterprise），越权提交拒绝
        Map<String, Object> payload = payload("customerType", "personal", "channel", "keyAccount");

        String message = assertCascadeError(() -> engine.assertCascadeOptions(schema, payload));
        assertTrue(message.contains("渠道") && message.contains("keyAccount"),
                "错误定位级联字段 label 与越权选项值: " + message);
    }

    /** 无 showCondition 选项恒允许；multi 多选值逐元素复核（任一元素越权即拒绝） */
    @Test
    void cascadeMultiChecksEachElementAndUnconditionalOptionAlwaysAllowed() {
        SysFormFieldDo parent = field("customerType", "select", "客户类型");
        parent.setProps(props("multi", false,
                "options", List.of(option("enterprise", "企业"), option("personal", "个人"))));
        SysFormFieldDo contacts = field("contacts", "cascade", "联系人渠道");
        // optionA 无条件恒允许；optionB 条件 enterprise；multi=true 值为数组
        Map<String, Object> optionA = option("public", "公开渠道");
        Map<String, Object> optionB = option("keyAccount", "大客户渠道");
        optionB.put("showCondition", condition("equal", "enterprise"));
        contacts.setProps(props("parentField", "customerType", "multi", true,
                "options", List.of(optionA, optionB)));
        SysFormSchemaDo schema = schemaOf(parent, contacts);

        // enterprise 下两选项均允许
        engine.assertCascadeOptions(schema,
                payload("customerType", "enterprise", "contacts", List.of("public", "keyAccount")));
        // personal 下 keyAccount 被过滤，多选数组中任一越权元素即拒绝
        assertCascadeError(() -> engine.assertCascadeOptions(schema,
                payload("customerType", "personal", "contacts", List.of("public", "keyAccount"))));
    }

    /** 父项为空：不特判按条件求值——empty 条件选项允许、equal 条件选项拒绝（与前端过滤一致） */
    @Test
    void cascadeEmptyParentEvaluatesConditionsAgainstEmptyValue() {
        SysFormFieldDo parent = field("customerType", "select", "客户类型");
        parent.setProps(props("multi", false,
                "options", List.of(option("enterprise", "企业"), option("personal", "个人"))));
        SysFormFieldDo fallback = field("fallbackChannel", "cascade", "兜底渠道");
        Map<String, Object> emptyConditionOption = option("any", "通用渠道");
        emptyConditionOption.put("showCondition", condition("empty", null));
        Map<String, Object> enterpriseOnlyOption = option("keyAccount", "大客户渠道");
        enterpriseOnlyOption.put("showCondition", condition("equal", "enterprise"));
        fallback.setProps(props("parentField", "customerType",
                "options", List.of(emptyConditionOption, enterpriseOnlyOption)));
        SysFormSchemaDo schema = schemaOf(parent, fallback);

        // 父项为空：empty 条件选项允许
        engine.assertCascadeOptions(schema, payload("fallbackChannel", "any"));
        // 父项为空：equal enterprise 条件选项拒绝（父项清空时前端清空本字段值，越权提交兜底拒绝）
        String message = assertCascadeError(() ->
                engine.assertCascadeOptions(schema, payload("fallbackChannel", "keyAccount")));
        assertTrue(message.contains("keyAccount"), message);
    }

    /** 明细行内级联：逐行复核，父项取本行值（行上下文优先），越权行确定性拒绝 */
    @Test
    void cascadeInDetailRowsCheckedPerRow() {
        SysFormFieldDo detail = field("purchaseDetails", "detailTable", "采购明细");
        detail.setChildren(List.of(
                field("category", "select", "品类"),
                rowCascadeField("supplier")));
        SysFormSchemaDo schema = schemaOf(detail);

        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("category", "hardware");
        row1.put("supplier", "brandSupplier");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("category", "service");
        row2.put("supplier", "brandSupplier");
        Map<String, Object> payload = payload("purchaseDetails", List.of(row1, row2));

        // 第 1 行 category=hardware 允许 brandSupplier；第 2 行 service 下 brandSupplier 被过滤 → 拒绝
        String message = assertCascadeError(() -> engine.assertCascadeOptions(schema, payload));
        assertTrue(message.contains("第 2 行"), "错误定位越权行号: " + message);
    }

    /** 提交值为空 / 未配置 parentField：跳过复核（必填与枚举语义由 S2/S4 承载） */
    @Test
    void cascadeRecheckSkipsEmptyValueAndMissingParentField() {
        SysFormFieldDo parent = field("customerType", "select", "客户类型");
        parent.setProps(props("multi", false,
                "options", List.of(option("enterprise", "企业"), option("personal", "个人"))));
        SysFormFieldDo withoutParent = field("channel", "cascade", "渠道");
        Map<String, Object> conditionalOption = option("keyAccount", "大客户渠道");
        conditionalOption.put("showCondition", condition("equal", "enterprise"));
        withoutParent.setProps(props("options", List.of(conditionalOption)));
        SysFormSchemaDo schema = schemaOf(parent, withoutParent);

        // 未配置 parentField：跳过复核（「存在即校验」口径，R08 第 4 条为条件校验）
        engine.assertCascadeOptions(schema, payload("channel", "keyAccount"));
        // 未配置选项（无可判定允许集）：跳过复核
        SysFormFieldDo noOptions = field("channel2", "cascade", "渠道2");
        noOptions.setProps(props("parentField", "customerType"));
        SysFormSchemaDo schema2 = schemaOf(parent, noOptions);
        engine.assertCascadeOptions(schema2, payload("customerType", "personal", "channel2", "任意值"));
        // 提交值为空：跳过复核
        SysFormSchemaDo schema3 = cascadeSchema();
        engine.assertCascadeOptions(schema3, payload("customerType", "personal"));
    }

    /** 级联 Schema 构造：customerType 父项 + channel 级联（keyAccount 选项条件 enterprise） */
    private static SysFormSchemaDo cascadeSchema() {
        SysFormFieldDo parent = field("customerType", "select", "客户类型");
        parent.setProps(props("multi", false,
                "options", List.of(option("enterprise", "企业"), option("personal", "个人"))));
        SysFormFieldDo channel = field("channel", "cascade", "渠道");
        Map<String, Object> keyAccount = option("keyAccount", "大客户渠道");
        keyAccount.put("showCondition", condition("equal", "enterprise"));
        channel.setProps(props("parentField", "customerType",
                "options", List.of(option("public", "公开渠道"), keyAccount)));
        return schemaOf(parent, channel);
    }

    /** 行内级联字段构造（supplier 选项条件 category=hardware，行上下文父子） */
    private static SysFormFieldDo rowCascadeField(String code) {
        SysFormFieldDo cascade = field(code, "cascade", "供应商");
        Map<String, Object> brand = option("brandSupplier", "品牌供应商");
        brand.put("showCondition", condition("equal", "hardware"));
        cascade.setProps(props("parentField", "category",
                "options", List.of(option("ordinary", "普通供应商"), brand)));
        return cascade;
    }

    private static String assertCascadeError(Runnable runnable) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, runnable::run);
        assertTrue(null != error.getMessage()
                        && error.getMessage().startsWith(SysFormConstants.CS_FORM_CASCADE_OPTION_INVALID + ":"),
                "错误码应为 CS_FORM_CASCADE_OPTION_INVALID，实际: " + error.getMessage());
        return error.getMessage();
    }

    private static Map<String, Object> props(Object... pairs) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            props.put((String) pairs[i], pairs[i + 1]);
        }
        return props;
    }

    // ---------------------------------------------------------------- 构造辅助

    /** 付款方式条件显隐 / 条件必填 Schema（§2.3 示例 rule_001/002 同构） */
    private SysFormSchemaDo payTypeSchema() {
        SysFormFieldDo applicant = field("applicant", "input", "申请人");
        SysFormFieldDo payType = field("payType", "select", "付款方式");
        payType.setProps(Map.of("multi", false,
                "options", List.of(option("cash", "现金"), option("transfer", "转账"))));
        payType.setRules(List.of(
                rule("rule_001", "payType", condition("equal", "transfer"),
                        action("setRequired", "bankAccount", Boolean.TRUE),
                        action("show", "bankAccount", null)),
                rule("rule_002", "payType", condition("notEqual", "transfer"),
                        action("setRequired", "bankAccount", Boolean.FALSE),
                        action("hide", "bankAccount", null))));
        SysFormFieldDo bankAccount = field("bankAccount", "input", "银行账号");
        bankAccount.setProps(new LinkedHashMap<>(Map.of("maxlength", 32)));
        bankAccount.getProps().put("hidden", true);
        return schemaOf(applicant, payType, bankAccount);
    }

    private static SysFormSchemaDo schemaOf(SysFormFieldDo... fields) {
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

    private static Map<String, Object> option(String value, String label) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("label", label);
        option.put("value", value);
        return option;
    }

    private static SysFormRuleDo rule(String ruleId, String triggerField,
                                      SysFormRuleConditionDo condition, SysFormRuleActionDo... actions) {
        SysFormRuleDo rule = new SysFormRuleDo();
        rule.setRuleId(ruleId);
        rule.setEvent("onChange");
        rule.setTriggerField(triggerField);
        rule.setCondition(condition);
        rule.setActions(List.of(actions));
        return rule;
    }

    private static SysFormRuleConditionDo condition(String operator, Object value) {
        SysFormRuleConditionDo condition = new SysFormRuleConditionDo();
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }

    private static SysFormRuleActionDo action(String type, String target, Object value) {
        SysFormRuleActionDo action = new SysFormRuleActionDo();
        action.setType(type);
        action.setTarget(target);
        action.setValue(value);
        return action;
    }

    private static SysFormRuleActionDo setValidate(String target, SysFormRuleValidateDo validate) {
        SysFormRuleActionDo action = new SysFormRuleActionDo();
        action.setType("setValidate");
        action.setTarget(target);
        action.setValidate(validate);
        return action;
    }

    private static SysFormRuleValidateDo validateOf(Number min, Number max, String pattern, String message) {
        SysFormRuleValidateDo validate = new SysFormRuleValidateDo();
        validate.setMin(min);
        validate.setMax(max);
        validate.setPattern(pattern);
        validate.setMessage(message);
        return validate;
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            payload.put((String) pairs[i], pairs[i + 1]);
        }
        return payload;
    }

    private static boolean required(FormValidationEngine.RuleReplay replay, String code) {
        FormDynamicState.RequiredOverride override = replay.getDynamicState().mainRequired(code);
        return null != override && override.isRequired();
    }
}

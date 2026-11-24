package com.eric.base.sys.form.service.engine;

import com.eric.base.sys.form.enums.SysFieldPermission;
import com.eric.base.sys.form.enums.SysFormEvent;
import com.eric.base.sys.form.model.SysFormRuleActionDo;
import com.eric.base.sys.form.model.SysFormRuleConditionDo;
import com.eric.base.sys.form.model.SysFormFieldDo;
import com.eric.base.sys.form.model.SysFormRuleDo;
import com.eric.base.sys.form.model.SysFormSchemaDo;
import com.eric.base.sys.form.properties.SysFormConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_CASCADE_OPTION_INVALID;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_DATA_INVALID;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_RULE_CYCLE_EXCEEDED;
import static com.eric.base.sys.form.properties.SysFormConstants.MAX_RULE_CHAIN_DEPTH;
import static com.eric.base.sys.form.properties.SysFormConstants.MAX_RULE_EXECUTIONS_PER_CYCLE;

/**
 * 表单服务端动态校验与字段权限裁剪引擎（F15-BE-004，功能设计 §3 规则协议 / §6.1 保存校验链）
 *
 * @author Eric
 * @date 2026/11/24
 * @description 数据面保存校验链动态段引擎（落位 service/engine 次级包，先例
 *              {@link FormDataBasicValidator} / {@link FormSchemaValidator}），承担：
 *              <ul>
 *              <li><b>规则 DSL 服务端复算</b>（R09 安全面：前端规则引擎求值是交互预演，
 *                  服务端按同一 DSL 复算是权威裁决，不信任前端校验结果，§3.1）：按实例
 *                  锁定版本 Schema 内嵌规则（字段对象 rules[]，{@link SysFormRuleDo}）对
 *                  载荷平铺索引求条件并执行动作——显隐（show/hide）/ 联动（setValue/
 *                  clearValue，${字段码} 引用解析）/ 动态校验（setRequired/setValidate）
 *                  全量执行，产出 {@link FormDynamicState}（显隐终态隐藏集 + 动态必填 /
 *                  校验覆盖，逐项保留触发规则 ID 溯源），与基础校验合并为最终校验口径
 *                  （§3.1 动态校验语义）。</li>
 *              <li><b>提交裁剪与读取裁剪</b>（§3.6 ADR-08 持久层② / 读取层③；要点第 7 步
 *                  红线「前端隐藏字段不等于后端禁止访问」）：提交时显隐复算判定隐藏的
 *                  主表字段与 permission=hidden 字段（无论渲染态）一律裁剪不落库（正常
 *                  业务行为不报错，分册 §4.3 S3）；读取时按存量数据复算同一口径，被裁剪
 *                  字段与 permission=hidden 字段不下发（分册 §4.5）。permission=readonly
 *                  一期 save 为新增落库、提交值照常落库（分册 §4.3 S7——值变更拒绝语义挂
 *                  二期实例更新接口，{@link #assertReadonlyBaseline(Map, Map)} 预留基线
 *                  比对通道）；v1.8 BPM 节点动态权限（fieldPermissions 外部覆盖）预留
 *                  扩展点，一期不消费（§6.6）。</li>
 *              <li><b>循环防护</b>（§3.5 三层防护服务端同语义）：单次求值动作执行计数达
 *                  {@link SysFormConstants#MAX_RULE_EXECUTIONS_PER_CYCLE} 或规则链级联
 *                  深度超 {@link SysFormConstants#MAX_RULE_CHAIN_DEPTH} → 熔断抛
 *                  {@code CS_FORM_RULE_CYCLE_EXCEEDED}（确定性错误，不允许无限求值）；
 *                  幂等短路——setValue 目标值与当前值相等时不回写、不触发后续规则链
 *                  （切断 A→B→A 最常见的无限振荡形态）。</li>
 *              <li><b>级联选项复核</b>（F15-BE-005 并联，分册 §4.3 S5 / R08 第 3 条「保存时
 *                  服务端统一入口复核」）：{@link #assertCascadeOptions(SysFormSchemaDo, Map)}
 *                  复用本引擎同一 DSL 条件求值器（显隐复算/级联复核共用——功能设计 §7
 *                  FormRuleEngine 候选名的落位形态）复核 type=cascade 提交值 ∈ 父项当前
 *                  条件允许选项集，不匹配抛 {@code CS_FORM_CASCADE_OPTION_INVALID}；
 *                  禁止逐项调用 formula simulate 接口（R08 第 1 条）。</li>
 *              </ul>
 *              <p><b>一期边界</b>（§3.6）：显隐复算裁剪作用于主表（顶层 + group/grid 容器内）
 *              字段与 permission=hidden 字段；明细行内显隐一期为渲染层语义——行内 show/hide
 *              不复算、行数据按行原样落库，行内必填 / 类型 / 枚举校验服务端仍执行，行内
 *              setRequired/setValidate 按行上下文求值（行内规则只作用于本行，不影响其他
 *              行，对齐 F15-FE-007 行内规则语义）。规则求值不引入脚本引擎（R02 一期不
 *              落地，代码块协议位不执行）；错误码与魔数全部收口 {@link SysFormConstants}。</p>
 *              <p><b>与 BE-003 的并联</b>：{@code SysFormDataService} save / validate 原
 *              注释占位的 S3（显隐裁剪）/ S4（动态校验）/ S7（权限裁剪）由本引擎并联承担，
 *              校验入口 save 与独立 validate 共用同一求值与裁剪语义；条件求值 / 动作载荷
 *              的结构合法性由定义期 {@link FormSchemaValidator} 保证（发布快照不可变）。</p>
 */
@Component
public class FormValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(FormValidationEngine.class);

    /** 明细表组件 type（行上下文宿主） */
    private static final String TYPE_DETAIL_TABLE = "detailTable";

    /** 级联选择器组件 type（S5 选项复核对象） */
    private static final String TYPE_CASCADE = "cascade";

    /** props 键：级联父数据项字段码（限定宿主数据层级内已配置的数据类字段，R08 第 4 条） */
    private static final String PROP_PARENT_FIELD = "parentField";

    /** props 键：级联选择项列表（options[{label, value, showCondition?}]，R08 条件 DSL） */
    private static final String PROP_OPTIONS = "options";

    /** props 键：级联单/多选（multi=true 值为数组，逐元素复核） */
    private static final String PROP_MULTI = "multi";

    /** props 键：选项显隐条件（{ operator, value }，语义同 §3.2 operator 全集，R08 第 1 条） */
    private static final String PROP_SHOW_CONDITION = "showCondition";

    /** 级联选项显隐条件反序列化（showCondition Map → SysFormRuleConditionDo，先例 FormSchemaValidator） */
    private static final ObjectMapper CONDITION_MAPPER = new ObjectMapper();

    /* ---------- 条件 operator 全集分组（功能设计 §3.2，10 个，与 FormSchemaValidator 同口径） ---------- */

    private static final String OP_EQUAL = "equal";
    private static final String OP_NOT_EQUAL = "notEqual";
    private static final String OP_GT = "gt";
    private static final String OP_GTE = "gte";
    private static final String OP_LT = "lt";
    private static final String OP_LTE = "lte";
    private static final String OP_IN = "in";
    private static final String OP_NOT_IN = "notIn";
    private static final String OP_EMPTY = "empty";
    private static final String OP_NOT_EMPTY = "notEmpty";

    /* ---------- 动作类型全集（功能设计 §3.3，6 个） ---------- */

    private static final String ACTION_SHOW = "show";
    private static final String ACTION_HIDE = "hide";
    private static final String ACTION_SET_VALUE = "setValue";
    private static final String ACTION_CLEAR_VALUE = "clearValue";
    private static final String ACTION_SET_REQUIRED = "setRequired";
    private static final String ACTION_SET_VALIDATE = "setValidate";

    /**
     * setValue 联动赋值的 ${字段码} 引用形态（整值精确引用；§3.3 value 为字面量或
     * ${字段码} 引用——非整值引用形态按字面量处理）
     */
    private static final Pattern FIELD_REFERENCE = Pattern.compile("^\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)}$");

    /**
     * 服务端复算消费的事件域（§3.4 一期消费范围定稿）：onChange 主力事件（显隐 / 联动 /
     * 动态校验）+ onSubmit / onValidate 提交段事件（onValidate 标注「服务端校验链消费」，
     * 二者载荷约束一致且禁 setValue/clearValue）。onLoad（初始化）/ onAdd / onDelete
     * （明细行生命周期）为交互历史事件——其动作效果已由提交载荷承载，服务端复算以提交
     * 终态为输入做条件驱动求值，不重放交互历史（重放会以初始化动作覆盖用户编辑值，
     * 违背「落库数据与提交时刻可见形态一致」的 §3.6 定稿）；onFocus / onBlur 一期不开放
     * 规则配置（结构校验器拒绝）。
     */
    private static final Set<String> SERVER_EVALUATED_EVENTS = Set.of(
            SysFormEvent.ON_CHANGE.getCode(), SysFormEvent.ON_SUBMIT.getCode(), SysFormEvent.ON_VALIDATE.getCode());

    /* ==================== 对外入口一：规则复算 + 提交裁剪（S3 / S4 / S7-hidden） ==================== */

    /**
     * 服务端规则复算与提交裁剪（分册 §4.3 S3 显隐复算与提交裁剪 + S4 动态校验前置求值）：
     * 按版本 Schema 内嵌规则对载荷求条件并执行动作（固定点迭代，值变更驱动下一轮级联），
     * 终态产出：①联动复算 + 提交裁剪后的最终载荷（hidden 字段值已裁除——落库 / 预览形态）；
     * ②主表隐藏字段集（显隐复算终态 ∪ permission=hidden）；③动态必填 / 校验覆盖状态
     * （含规则 ID 溯源，供基础校验器合并裁决）。
     *
     * <p>求值模型（确定性，同 Schema 同载荷结果唯一）：每轮按 Schema 字段序平铺求值全部
     * 适用规则（主表规则 → 明细表宿主规则 → 明细行规则逐行），匹配规则的动按序执行；
     * 仅载荷值变更驱动下一轮级联（条件只依赖字段值，显隐 / 覆盖状态不进入条件）。级联
     * 轮次超 {@link SysFormConstants#MAX_RULE_CHAIN_DEPTH} 或动作执行计数达
     * {@link SysFormConstants#MAX_RULE_EXECUTIONS_PER_CYCLE} 即熔断抛
     * {@code CS_FORM_RULE_CYCLE_EXCEEDED}（§3.5）。</p>
     *
     * @param schema   实例锁定版本 Schema 协议对象（null 按无规则处理——载荷原样返回、
     *                 仅 permission=hidden 裁剪）
     * @param formData 提交载荷（S1 白名单由调用方先行；引擎内部防御性拷贝，不改写入参）
     * @return 复算结果（最终载荷 + 隐藏字段集 + 动态校验覆盖状态）
     */
    public RuleReplay replay(SysFormSchemaDo schema, Map<String, Object> formData) {
        RuleIndex index = buildIndex(schema);
        Map<String, Object> payload = copyPayload(formData, index);
        Map<String, Boolean> visibility = initialVisibility(index);
        FormDynamicState state = FormDynamicState.empty();
        // permission=hidden 权限裁剪预登记（无论渲染态一律裁剪，§3.6 持久层 / 分册 §4.3 S3）
        for (Map.Entry<String, SysFormFieldDo> entry : index.mainFields.entrySet()) {
            if (SysFieldPermission.HIDDEN == permissionOf(entry.getValue())) {
                state.addHiddenMain(entry.getKey());
            }
        }

        ReplaySession session = new ReplaySession(index, payload, visibility, state);
        int cascade = 0;
        while (true) {
            boolean changed = session.runPass();
            if (!changed) {
                break;
            }
            cascade++;
            if (cascade > MAX_RULE_CHAIN_DEPTH) {
                log.debug("form rule replay circuit-broken: cascade depth > {}", MAX_RULE_CHAIN_DEPTH);
                throw cycleError("规则链级联深度超过上限 " + MAX_RULE_CHAIN_DEPTH
                        + "，已熔断（疑似规则循环配置，功能设计 §3.5）");
            }
        }

        // 显隐复算终态：仍处隐藏态的主表字段并入隐藏集（show/hide 仅切渲染可见性不删值，
        // ADR-08；此处按终态裁剪持久层——§3.6 ②），与 permission=hidden 合并后统一裁剪
        for (Map.Entry<String, Boolean> entry : visibility.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                state.addHiddenMain(entry.getKey());
            }
        }
        Set<String> hiddenFieldCodes = state.snapshotHiddenMainCodes();
        for (String code : hiddenFieldCodes) {
            payload.remove(code);
        }
        return new RuleReplay(payload, hiddenFieldCodes, state);
    }

    /** 复算结果（S3 提交裁剪后的最终载荷 + 隐藏字段集 + S4 动态校验覆盖状态） */
    public static final class RuleReplay {

        /** 联动复算 + 提交裁剪后的最终载荷（落库 / validate 预览形态；键序由调用方 normalize 归一） */
        private final Map<String, Object> payload;

        /** 主表隐藏字段集（显隐复算终态 ∪ permission=hidden；裁剪与必填豁免同源） */
        private final Set<String> hiddenFieldCodes;

        /** 动态必填 / 校验覆盖状态（FormDataBasicValidator 合并裁决消费） */
        private final FormDynamicState dynamicState;

        RuleReplay(Map<String, Object> payload, Set<String> hiddenFieldCodes, FormDynamicState dynamicState) {
            this.payload = payload;
            this.hiddenFieldCodes = hiddenFieldCodes;
            this.dynamicState = dynamicState;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }

        public Set<String> getHiddenFieldCodes() {
            return hiddenFieldCodes;
        }

        public FormDynamicState getDynamicState() {
            return dynamicState;
        }
    }

    /* ==================== 对外入口二：读取层裁剪（§3.6 ③ / 分册 §4.5） ==================== */

    /**
     * 读取层裁剪（回显 / 详情响应）：按存量数据复算同一显隐口径——显隐复算终态隐藏的
     * 主表字段与 permission=hidden 字段不下发（要点红线「前端隐藏字段不等于后端禁止
     * 访问」）。存量数据即提交时刻终态（保存链已按同口径裁剪落库），复算结果与提交时
     * 一致；本方法为防御性兜底 + permission 通道，不改写存量数据本身。
     *
     * <p>明细行内显隐一期为渲染层语义（§3.6 一期边界），行数据按行原样下发。</p>
     *
     * @param schema   实例锁定版本 Schema（null 时原样返回——存储态兜底下发）
     * @param formData 存量字段值对象（null 原样返回）
     * @return 读取裁剪后的字段值对象（入参不改写，防御性拷贝）
     */
    public Map<String, Object> clipForRead(SysFormSchemaDo schema, Map<String, Object> formData) {
        if (null == schema || null == formData || formData.isEmpty()) {
            return formData;
        }
        RuleIndex index = buildIndex(schema);
        Map<String, Object> working = new LinkedHashMap<>(formData);
        Map<String, Boolean> visibility = initialVisibility(index);
        // 读取层显隐复算：单轮即可收敛——条件仅依赖字段值，存量数据即提交终态（联动复算
        // 已在保存链完成），show/hide 不变更字段值，无级联（读取路径不改写存量数据）
        for (HostedRule hosted : index.mainRules) {
            SysFormRuleDo rule = hosted.rule;
            if (!serverEvaluated(rule)) {
                continue;
            }
            if (!conditionMatches(rule.getCondition(), working.get(hosted.hostCode))) {
                continue;
            }
            for (SysFormRuleActionDo action : actions(rule)) {
                String target = action.getTarget();
                if (!index.mainFields.containsKey(target)) {
                    continue;
                }
                if (ACTION_SHOW.equals(action.getType())) {
                    visibility.put(target, Boolean.TRUE);
                } else if (ACTION_HIDE.equals(action.getType())) {
                    visibility.put(target, Boolean.FALSE);
                }
            }
        }
        for (Map.Entry<String, Boolean> entry : visibility.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                working.remove(entry.getKey());
            }
        }
        for (Map.Entry<String, SysFormFieldDo> entry : index.mainFields.entrySet()) {
            if (SysFieldPermission.HIDDEN == permissionOf(entry.getValue())) {
                working.remove(entry.getKey());
            }
        }
        return working;
    }

    /* ==================== 对外入口三：readonly 基线比对（二期实例更新接口预留通道） ==================== */

    /**
     * readonly 字段提交值与既有值基线比对（分册 §4.3 S7「值变更拒绝语义挂二期实例更新
     * 接口」预留通道；v1.8 BPM fieldPermissions 外部覆盖届时在提交接口处二次校验复用）：
     * permission=readonly 的主表字段在基线（既有值）中存在且提交值与基线不一致时拒绝，
     * 抛 {@code CS_FORM_DATA_INVALID}（数据面确定性拒绝，message 前缀承载）。
     *
     * <p>一期 save 为新增落库、无库内基线可比对，调用方不传基线（null / 空）即整体跳过
     * ——提交值照常落库（分册 §4.3 S7 定稿口径）；明细行内 readonly 基线比对涉及行身份
     * 对齐，随二期实例更新接口语义定稿后扩展。</p>
     *
     * @param schema   实例锁定版本 Schema（null 跳过）
     * @param payload  提交载荷（裁剪前 / 后均可——readonly 字段不参与裁剪）
     * @param baseline 既有值基线（字段码 → 值；null / 空按无基线跳过——一期 save 口径）
     */
    public void assertReadonlyBaseline(SysFormSchemaDo schema, Map<String, Object> payload,
                                       Map<String, Object> baseline) {
        if (null == schema || null == payload || null == baseline || baseline.isEmpty()) {
            return;
        }
        RuleIndex index = buildIndex(schema);
        for (Map.Entry<String, SysFormFieldDo> entry : index.mainFields.entrySet()) {
            SysFormFieldDo field = entry.getValue();
            if (SysFieldPermission.READONLY != permissionOf(field)) {
                continue;
            }
            String code = entry.getKey();
            if (!payload.containsKey(code) || !baseline.containsKey(code)) {
                continue;
            }
            if (!valueEquals(payload.get(code), baseline.get(code))) {
                String label = nonBlank(field.getLabel()) ? field.getLabel() : code;
                log.debug("form readonly baseline violation: code={}", code);
                throw new IllegalArgumentException(CS_FORM_DATA_INVALID + ": 只读字段「" + label
                        + "」提交值与既有值不一致，拒绝写入: " + code);
            }
        }
    }

    /* ==================== 对外入口四：级联选项复核（S5，R08 第 3 条服务端统一入口） ==================== */

    /**
     * 级联选项复核（分册 §4.3 S5 / 功能设计 §6.4，R08 第 3 条「保存时服务端统一入口复核」）：
     * type=cascade 字段的提交值必须 ∈ 父项当前条件下允许的选项集——允许集 = props.options
     * 中无 showCondition 的选项（恒允许）∪ showCondition 对父项当前值求值匹配的选项
     * （条件为规则 DSL 条件形态 { operator, value }，与前端本地过滤共用
     * {@link #conditionMatches} 同一求值语义——R08「前后端共用同一 DSL 条件求值语义保证
     * 过滤与复核结果一致」）。不匹配抛 {@code CS_FORM_CASCADE_OPTION_INVALID}（确定性拒绝：
     * 前端本地过滤是交互预演，越权提交——直接调接口提交被过滤选项——由后端权威兜底）。
     *
     * <p>复核口径：</p>
     * <ul>
     * <li>主表级联：父项当前值取载荷中 parentField 字段值（父项条件判定复用规则 DSL 的
     *     条件求值语义，禁止逐项调用 formula simulate 接口，R08）；父项字段不在宿主数据
     *     层级（定义期结构校验已拒绝，防御性）跳过复核。</li>
     * <li>明细行内级联：逐行复核，父项当前值取本行 parentField 字段值（行上下文优先、
     *     回落主表层级——与联动引用解析同口径；设计期校验限定 parentField 为宿主行上下文
     *     内数据类字段）。</li>
     * <li>父项为空：不特判、按条件求值——空值对 notEmpty/equal 等不匹配（被过滤选项的
     *     提交值拒绝）、对 empty 匹配（允许），与前端过滤行为一致；「父项值清空时清空本
     *     字段已选值」为组件内置联动（前端行为），服务端以提交值 ∈ 允许集复核兜底。</li>
     * <li>multi=true 多选值逐元素复核；提交值为空（null/空白/空数组）跳过复核（必填
     *     语义由 S2/S4 承载）；未配置 parentField（「存在即校验」口径，R08 第 4 条为条件
     *     校验）或未配置选项/选项无 value 键跳过复核（枚举选项值已由 S2 承载）。</li>
     * </ul>
     *
     * @param schema  实例锁定版本 Schema 协议对象（null 跳过）
     * @param payload 合并裁决后的最终载荷（S5 位于 S2/S4 之后、S6 公式重算之前，分册 §4.3 步骤序）
     */
    public void assertCascadeOptions(SysFormSchemaDo schema, Map<String, Object> payload) {
        if (null == schema || null == payload || payload.isEmpty()) {
            return;
        }
        RuleIndex index = buildIndex(schema);
        for (Map.Entry<String, SysFormFieldDo> entry : index.mainFields.entrySet()) {
            SysFormFieldDo field = entry.getValue();
            if (!TYPE_CASCADE.equals(field.getType())) {
                continue;
            }
            assertMainCascadeOptions(field, entry.getKey(), index, payload);
        }
        for (Map.Entry<String, Map<String, SysFormFieldDo>> scopeEntry : index.rowScopes.entrySet()) {
            for (SysFormFieldDo rowField : scopeEntry.getValue().values()) {
                if (TYPE_CASCADE.equals(rowField.getType())) {
                    assertRowCascadeOptions(rowField, scopeEntry.getKey(), payload);
                }
            }
        }
    }

    /** 主表级联复核：父项当前值取载荷 parentField 字段值，提交值 ∈ 允许选项集 */
    private void assertMainCascadeOptions(SysFormFieldDo field, String code, RuleIndex index,
                                          Map<String, Object> payload) {
        Object value = payload.get(code);
        if (isEmptyValue(value)) {
            return;
        }
        Map<String, Object> props = field.getProps();
        String parentField = null == props ? null : textProp(props, PROP_PARENT_FIELD);
        if (!nonBlank(parentField) || !index.mainFields.containsKey(parentField)) {
            return; // 未配置父项（存在即校验）或父项不在宿主层级（定义期已拒绝，防御性跳过）
        }
        Set<Object> allowed = allowedOptionValues(field, payload.get(parentField));
        if (null == allowed) {
            return;
        }
        assertOptionMembership(field, code, null, value, allowed);
    }

    /** 明细行内级联复核：逐行取本行 parentField 字段值（行上下文优先、回落主表层级） */
    private void assertRowCascadeOptions(SysFormFieldDo field, String detailCode, Map<String, Object> payload) {
        Object rowsValue = payload.get(detailCode);
        if (!(rowsValue instanceof List<?> rows) || rows.isEmpty()) {
            return;
        }
        Map<String, Object> props = field.getProps();
        String parentField = null == props ? null : textProp(props, PROP_PARENT_FIELD);
        if (!nonBlank(parentField)) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (!(row instanceof Map<?, ?> rowMap)) {
                continue; // 行对象结构违规由 S2 承载，防御性跳过
            }
            Map<String, Object> rowVars = asStringMap(rowMap);
            Object value = rowVars.get(field.getField());
            if (isEmptyValue(value)) {
                continue;
            }
            // 父项当前值：行上下文优先、回落主表层级（与 setValue 联动引用解析同口径）
            Object parentValue = rowVars.containsKey(parentField)
                    ? rowVars.get(parentField) : payload.get(parentField);
            Set<Object> allowed = allowedOptionValues(field, parentValue);
            if (null == allowed) {
                continue;
            }
            assertOptionMembership(field, field.getField(), i, value, allowed);
        }
    }

    /**
     * 允许选项集计算（R08 第 3 条「父项当前条件下允许的选项集」）：无 showCondition 选项
     * 恒允许；showCondition 按父项当前值经 {@link #conditionMatches} 求值（同一 DSL 条件
     * 语义）。未配置选项 / 选项均无 value 键返回 null（无可判定允许集，跳过复核——枚举
     * 选项值已由 S2 承载）；选项齐备但条件全不匹配返回空集（提交任何值都拒绝）。
     */
    private static Set<Object> allowedOptionValues(SysFormFieldDo field, Object parentValue) {
        Map<String, Object> props = field.getProps();
        Object optionsRaw = null == props ? null : props.get(PROP_OPTIONS);
        if (!(optionsRaw instanceof List<?> optionList) || optionList.isEmpty()) {
            return null;
        }
        Set<Object> allowed = new LinkedHashSet<>();
        boolean hasValueKey = false;
        for (Object option : optionList) {
            if (!(option instanceof Map<?, ?> optionMap) || !optionMap.containsKey("value")) {
                continue;
            }
            hasValueKey = true;
            Object showCondition = optionMap.get(PROP_SHOW_CONDITION);
            if (null == showCondition) {
                allowed.add(optionMap.get("value")); // 无显隐条件选项恒允许
                continue;
            }
            SysFormRuleConditionDo condition;
            try {
                condition = CONDITION_MAPPER.convertValue(showCondition, SysFormRuleConditionDo.class);
            } catch (IllegalArgumentException e) {
                continue; // 条件结构违规由定义期结构校验器拦截（防御性跳过）
            }
            if (conditionMatches(condition, parentValue)) {
                allowed.add(optionMap.get("value"));
            }
        }
        return hasValueKey ? allowed : null;
    }

    /**
     * 提交值 ∈ 允许选项集复核（multi=true 多选值逐元素；选项值比对容忍原始值相等或
     * 字符串形态相等——同一数值的 JSON 表示漂移，口径同 FormDataBasicValidator 枚举校验）。
     * 不匹配抛 {@code CS_FORM_CASCADE_OPTION_INVALID}（S5 确定性拒绝）。
     */
    private void assertOptionMembership(SysFormFieldDo field, String code, Integer rowIndex,
                                        Object value, Set<Object> allowed) {
        List<?> items = Boolean.TRUE.equals(valueMulti(field)) && value instanceof List<?> list
                ? list : List.of(value);
        for (Object item : items) {
            if (!optionValueMatches(allowed, item)) {
                String label = nonBlank(field.getLabel()) ? field.getLabel() : code;
                String where = null == rowIndex ? "「" + label + "」" : "「" + label + "」（第 " + (rowIndex + 1) + " 行）";
                log.debug("form cascade option rejected: code={}, value={}", code, item);
                throw new IllegalArgumentException(CS_FORM_CASCADE_OPTION_INVALID + ": 级联选择器" + where
                        + "提交值不在父项当前条件允许选项集内: " + item);
            }
        }
    }

    /** props.multi 判定（multi=true 值为数组、逐元素复核） */
    private static boolean valueMulti(SysFormFieldDo field) {
        Map<String, Object> props = field.getProps();
        return null != props && Boolean.TRUE.equals(props.get(PROP_MULTI));
    }

    /** 选项值比对：原始值相等或字符串形态相等（容忍 JSON 表示漂移，同 S2 枚举校验口径） */
    private static boolean optionValueMatches(Set<Object> allowed, Object item) {
        if (allowed.contains(item)) {
            return true;
        }
        String text = String.valueOf(item);
        for (Object option : allowed) {
            if (String.valueOf(option).equals(text)) {
                return true;
            }
        }
        return false;
    }

    /** props 文本属性（非 String 或缺省返回 null） */
    private static String textProp(Map<String, Object> props, String key) {
        Object raw = null == props ? null : props.get(key);
        return raw instanceof String text ? text : null;
    }

    /* ==================== 规则复算会话（固定点迭代 + 双熔断 + 幂等短路） ==================== */

    /** 单次复算会话：载荷 / 显隐态 / 覆盖状态的工作副本与动作执行计数 */
    private static final class ReplaySession {

        private final RuleIndex index;
        private final Map<String, Object> payload;
        private final Map<String, Boolean> visibility;
        private final FormDynamicState state;
        private int executions = 0;

        ReplaySession(RuleIndex index, Map<String, Object> payload,
                      Map<String, Boolean> visibility, FormDynamicState state) {
            this.index = index;
            this.payload = payload;
            this.visibility = visibility;
            this.state = state;
        }

        /**
         * 一轮全量求值（Schema 字段序平铺）：主表规则 → 明细表宿主规则 → 明细行规则逐行。
         * 返回本轮是否发生载荷值变更（仅值变更驱动下一轮级联——条件只依赖字段值，显隐 /
         * 覆盖状态变更不进入任何条件，§3.5 事件链的值驱动近似）。
         */
        boolean runPass() {
            boolean changed = false;
            for (HostedRule hosted : index.mainRules) {
                changed |= applyMainRule(hosted);
            }
            for (HostedRule hosted : index.detailRules) {
                changed |= applyDetailRule(hosted);
            }
            for (HostedRule hosted : index.rowRules) {
                changed |= applyRowRule(hosted);
            }
            return changed;
        }

        /** 主表规则：条件对触发字段当前值求值，动作作用于主表层级 */
        private boolean applyMainRule(HostedRule hosted) {
            SysFormRuleDo rule = hosted.rule;
            if (!serverEvaluated(rule)
                    || !conditionMatches(rule.getCondition(), payload.get(hosted.hostCode))) {
                return false;
            }
            boolean changed = false;
            for (SysFormRuleActionDo action : actions(rule)) {
                beginAction();
                changed |= applyMainAction(action, rule);
            }
            return changed;
        }

        /** 明细表宿主规则（triggerField=明细表字段码）：条件对行数组整体求值，动作按行上下文作用于全部行 */
        private boolean applyDetailRule(HostedRule hosted) {
            SysFormRuleDo rule = hosted.rule;
            Object rowsValue = payload.get(hosted.hostCode);
            if (!serverEvaluated(rule) || !(rowsValue instanceof List<?> rows)
                    || !conditionMatches(rule.getCondition(), rowsValue)) {
                return false;
            }
            boolean changed = false;
            for (SysFormRuleActionDo action : actions(rule)) {
                beginAction();
                for (int i = 0; i < rows.size(); i++) {
                    Object row = rows.get(i);
                    if (row instanceof Map<?, ?> rowMap) {
                        changed |= applyRowAction(action, rule, hosted.hostCode, i, asStringMap(rowMap));
                    }
                }
            }
            return changed;
        }

        /** 明细行规则（宿主为行内字段）：条件逐行对当前行字段值求值，动作只作用于本行（行上下文隔离） */
        private boolean applyRowRule(HostedRule hosted) {
            SysFormRuleDo rule = hosted.rule;
            Object rowsValue = payload.get(hosted.detailCode);
            if (!serverEvaluated(rule) || !(rowsValue instanceof List<?> rows)) {
                return false;
            }
            boolean changed = false;
            for (int i = 0; i < rows.size(); i++) {
                Object row = rows.get(i);
                if (!(row instanceof Map<?, ?> rowMap)) {
                    continue; // 行对象结构违规由基础校验承载，防御性跳过
                }
                if (!conditionMatches(rule.getCondition(), asStringMap(rowMap).get(hosted.hostCode))) {
                    continue;
                }
                for (SysFormRuleActionDo action : actions(rule)) {
                    beginAction();
                    changed |= applyRowAction(action, rule, hosted.detailCode, i, asStringMap(rowMap));
                }
            }
            return changed;
        }

        /** 主表层级动作执行（show/hide 切显隐、setValue/clearValue 写值、setRequired/setValidate 写覆盖） */
        private boolean applyMainAction(SysFormRuleActionDo action, SysFormRuleDo rule) {
            String target = action.getTarget();
            if (!nonBlank(target)) {
                return false;
            }
            switch (null == action.getType() ? "" : action.getType()) {
                case ACTION_SHOW:
                    visibility.put(target, Boolean.TRUE);
                    return false;
                case ACTION_HIDE:
                    visibility.put(target, Boolean.FALSE);
                    return false;
                case ACTION_SET_VALUE:
                    return writeValue(payload, target, resolveReference(action.getValue(), null));
                case ACTION_CLEAR_VALUE:
                    return writeValue(payload, target, null);
                case ACTION_SET_REQUIRED:
                    state.putMainRequired(target, Boolean.TRUE.equals(action.getValue()), rule.getRuleId());
                    return false;
                case ACTION_SET_VALIDATE:
                    state.mainValidateForWrite(target).applySetValidate(action.getValidate(), rule.getRuleId());
                    return false;
                default:
                    return false; // 未知动作防御性跳过（结构校验器已按全集 6 个拦截）
            }
        }

        /** 明细行层级动作执行（行上下文 row / rowIndex 注入；show/hide 为渲染层语义不复算，§3.6 一期边界） */
        private boolean applyRowAction(SysFormRuleActionDo action, SysFormRuleDo rule,
                                       String detailCode, int rowIndex, Map<String, Object> rowMap) {
            String target = action.getTarget();
            if (!nonBlank(target)) {
                return false;
            }
            switch (null == action.getType() ? "" : action.getType()) {
                case ACTION_SHOW:
                case ACTION_HIDE:
                    // 明细行内显隐一期为渲染层语义：行数据按行原样落库（§3.6 一期边界），服务端不复算不裁剪
                    return false;
                case ACTION_SET_VALUE:
                    return writeValue(rowMap, target, resolveReference(action.getValue(), rowMap));
                case ACTION_CLEAR_VALUE:
                    return writeValue(rowMap, target, null);
                case ACTION_SET_REQUIRED:
                    state.putRowRequired(detailCode, rowIndex, target,
                            Boolean.TRUE.equals(action.getValue()), rule.getRuleId());
                    return false;
                case ACTION_SET_VALIDATE:
                    state.rowValidateForWrite(detailCode, rowIndex, target)
                            .applySetValidate(action.getValidate(), rule.getRuleId());
                    return false;
                default:
                    return false;
            }
        }

        /**
         * 值写入（setValue / clearValue 共用，幂等短路 §3.5 第 3 层）：目标值与当前值相等
         * 时不回写不触发后续规则链；写入 null 语义与 clearValue 一致（移除键——「无值」
         * 形态，normalize 与 isEmptyValue 同口径）。
         */
        private static boolean writeValue(Map<String, Object> targetMap, String target, Object resolved) {
            if (null == resolved) {
                if (!targetMap.containsKey(target)) {
                    return false;
                }
                targetMap.remove(target);
                return true;
            }
            if (targetMap.containsKey(target) && valueEquals(targetMap.get(target), resolved)) {
                return false;
            }
            targetMap.put(target, resolved);
            return true;
        }

        /**
         * setValue 动作值解析：整值 ${字段码} 引用按当前状态联动取值（行上下文优先、
         * 回落主表层级，§3.3「value 为字面量或 ${字段码} 引用」）；其余按字面量。
         */
        private Object resolveReference(Object raw, Map<String, Object> rowMap) {
            if (raw instanceof String text) {
                Matcher matcher = FIELD_REFERENCE.matcher(text);
                if (matcher.matches()) {
                    String reference = matcher.group(1);
                    if (null != rowMap && rowMap.containsKey(reference)) {
                        return rowMap.get(reference);
                    }
                    return payload.get(reference);
                }
            }
            return raw;
        }

        /** 动作执行计数与次数熔断（§3.5 第 1 层：计数达上限即熔断本次求值风暴） */
        private void beginAction() {
            if (executions >= MAX_RULE_EXECUTIONS_PER_CYCLE) {
                log.debug("form rule replay circuit-broken: executions >= {}", MAX_RULE_EXECUTIONS_PER_CYCLE);
                throw cycleError("单次求值动作执行次数达到上限 " + MAX_RULE_EXECUTIONS_PER_CYCLE
                        + "，已熔断（疑似规则风暴，功能设计 §3.5）");
            }
            executions++;
        }
    }

    /* ==================== 条件求值（§3.2 operator 全集 10 个） ==================== */

    /**
     * 条件求值：condition 为 null 视为无条件规则（事件即触发，§3.1）；actual 为触发字段
     * 当前值（主表字段值 / 明细行数组 / 行内字段值随宿主层级）。
     */
    private static boolean conditionMatches(SysFormRuleConditionDo condition, Object actual) {
        if (null == condition) {
            return true;
        }
        String operator = condition.getOperator();
        Object expected = condition.getValue();
        if (OP_EQUAL.equals(operator)) {
            return scalarEquals(actual, expected);
        }
        if (OP_NOT_EQUAL.equals(operator)) {
            return !scalarEquals(actual, expected);
        }
        if (OP_GT.equals(operator) || OP_GTE.equals(operator) || OP_LT.equals(operator) || OP_LTE.equals(operator)) {
            Integer order = compare(actual, expected);
            if (null == order) {
                return false;
            }
            return switch (operator) {
                case OP_GT -> order > 0;
                case OP_GTE -> order >= 0;
                case OP_LT -> order < 0;
                default -> order <= 0;
            };
        }
        if (OP_IN.equals(operator) || OP_NOT_IN.equals(operator)) {
            boolean contained = containsCandidate(expected, actual);
            return OP_IN.equals(operator) == contained;
        }
        if (OP_EMPTY.equals(operator)) {
            return isEmptyValue(actual);
        }
        if (OP_NOT_EMPTY.equals(operator)) {
            return !isEmptyValue(actual);
        }
        return false; // 非法 operator 由定义期结构校验器拦截（防御性不匹配）
    }

    /**
     * 标量精确比对（§3.2：字符串 / 数值 / 布尔精确匹配）：数值按值域比对（容忍同一数值
     * 的 JSON 表示漂移，如 1 与 1.0），其余类型 equals 精确（与前端 === 同语义）。
     */
    private static boolean scalarEquals(Object actual, Object expected) {
        if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
            return actualNumber.doubleValue() == expectedNumber.doubleValue();
        }
        return Objects.equals(actual, expected);
    }

    /**
     * 大小比较（§3.2：NUMBER 数值比较；date 按 same-format 字符串字典序比较）；
     * 空值 / 类型不配不可比较，一律不匹配。
     */
    private static Integer compare(Object actual, Object expected) {
        if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
            return Double.compare(actualNumber.doubleValue(), expectedNumber.doubleValue());
        }
        if (actual instanceof String actualText && expected instanceof String expectedText) {
            return actualText.compareTo(expectedText);
        }
        return null;
    }

    /** in / notIn 集合属于判定（value 必须为数组，结构校验器已保证；防御性非数组判不属于） */
    private static boolean containsCandidate(Object expected, Object actual) {
        if (!(expected instanceof List<?> candidates)) {
            return false;
        }
        for (Object candidate : candidates) {
            if (scalarEquals(actual, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 空值判定（§3.2 empty/notEmpty：null / undefined / 空串 / 空数组；与
     * FormDataBasicValidator 同口径——空白串 / 空集合 / 空对象视为无值）
     */
    private static boolean isEmptyValue(Object value) {
        if (null == value) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    /** 值相等判定（幂等短路用；数值按值域、其余 equals） */
    private static boolean valueEquals(Object current, Object resolved) {
        if (current instanceof Number currentNumber && resolved instanceof Number resolvedNumber) {
            return currentNumber.doubleValue() == resolvedNumber.doubleValue();
        }
        return Objects.equals(current, resolved);
    }

    /* ==================== 规则索引与载荷工作副本 ==================== */

    /** 规则宿主（规则随触发字段存放于字段对象 rules[]，ADR-06；服务端平铺索引求值） */
    private static final class HostedRule {

        /** 宿主字段码（= 规则 triggerField；明细行规则为行内字段码） */
        final String hostCode;

        /** 明细表字段码（仅明细行 / 明细表宿主规则持有——行上下文定位） */
        final String detailCode;

        final SysFormRuleDo rule;

        HostedRule(String hostCode, String detailCode, SysFormRuleDo rule) {
            this.hostCode = hostCode;
            this.detailCode = detailCode;
            this.rule = rule;
        }
    }

    /** 规则平铺索引：数据层级字段（含行上下文）+ 按宿主层级分组的规则清单 */
    private static final class RuleIndex {

        /** 主表层级：字段码 → 字段对象（数据类 12 组件 + detailTable 宿主；布局容器透明） */
        final Map<String, SysFormFieldDo> mainFields = new LinkedHashMap<>();

        /** 明细行层级：detailTable 字段码 → 行内字段码 → 字段对象（行上下文隔离） */
        final Map<String, Map<String, SysFormFieldDo>> rowScopes = new LinkedHashMap<>();

        /** 主表层级规则（宿主为主表数据类字段） */
        final List<HostedRule> mainRules = new ArrayList<>();

        /** 明细表宿主规则（triggerField=明细表字段码，动作 target 为行内字段码） */
        final List<HostedRule> detailRules = new ArrayList<>();

        /** 明细行规则（宿主为行内字段，动作只作用于本行） */
        final List<HostedRule> rowRules = new ArrayList<>();
    }

    /** 构建规则平铺索引（字段树遍历，层级口径与 FormDataBasicValidator 的 DataScopes 一致） */
    private static RuleIndex buildIndex(SysFormSchemaDo schema) {
        RuleIndex index = new RuleIndex();
        if (null != schema) {
            collectFields(schema.getFields(), index);
        }
        return index;
    }

    /** 递归收集一个字段列表（布局容器透明下钻；明细表宿主登记行上下文并分组其规则） */
    private static void collectFields(List<SysFormFieldDo> fields, RuleIndex index) {
        if (null == fields) {
            return;
        }
        for (SysFormFieldDo field : fields) {
            if (null == field) {
                continue;
            }
            // 栅格列对象仅承载 span/fields：列内字段规则与宿主栅格同处一个数据层级
            if (field.isGridColumn()) {
                collectFields(field.getFields(), index);
                continue;
            }
            String type = field.getType();
            String code = field.getField();
            if ("group".equals(type) || "grid".equals(type)) {
                // 布局容器不产生新数据层级（容器内字段与宿主同层级）
                collectFields(field.getChildren(), index);
                continue;
            }
            if (TYPE_DETAIL_TABLE.equals(type) && nonBlank(code)) {
                if (!index.mainFields.containsKey(code)) {
                    index.mainFields.put(code, field);
                }
                Map<String, SysFormFieldDo> rowScope = new LinkedHashMap<>();
                collectRowFields(field.getChildren(), rowScope);
                index.rowScopes.put(code, rowScope);
                addRules(field.getRules(), code, code, index.detailRules);
                for (SysFormFieldDo rowField : rowScope.values()) {
                    addRules(rowField.getRules(), rowField.getField(), code, index.rowRules);
                }
                continue;
            }
            if (nonBlank(code) && SysFormConstants.DATA_FIELD_TYPES.contains(type)
                    && !index.mainFields.containsKey(code)) {
                index.mainFields.put(code, field);
                addRules(field.getRules(), code, null, index.mainRules);
            }
        }
    }

    /** 递归收集明细行内字段（行内字段码可与主表同码但行内唯一，定义期结构校验已保证） */
    private static void collectRowFields(List<SysFormFieldDo> children, Map<String, SysFormFieldDo> rowScope) {
        if (null == children) {
            return;
        }
        for (SysFormFieldDo child : children) {
            if (null == child || !nonBlank(child.getField())) {
                continue;
            }
            if (!rowScope.containsKey(child.getField())) {
                rowScope.put(child.getField(), child);
            }
        }
    }

    /** 登记一个字段对象的规则清单（ruleId 唯一性与要素合法性由定义期结构校验保证） */
    private static void addRules(List<SysFormRuleDo> rules, String hostCode,
                                 String detailCode, List<HostedRule> target) {
        if (null == rules) {
            return;
        }
        for (SysFormRuleDo rule : rules) {
            if (null != rule) {
                target.add(new HostedRule(hostCode, detailCode, rule));
            }
        }
    }

    /**
     * 载荷工作副本（防御性拷贝，不改写入参）：顶层浅拷贝 + 明细行逐行拷贝——setValue /
     * clearValue 对行对象的写入不得泄漏到调用方载荷。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyPayload(Map<String, Object> formData, RuleIndex index) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (null == formData) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            Object value = entry.getValue();
            if (index.rowScopes.containsKey(entry.getKey()) && value instanceof List<?> rows) {
                List<Object> rowCopies = new ArrayList<>(rows.size());
                for (Object row : rows) {
                    rowCopies.add(row instanceof Map<?, ?> rowMap
                            ? new LinkedHashMap<String, Object>((Map<String, Object>) rowMap) : row);
                }
                value = rowCopies;
            }
            copy.put(entry.getKey(), value);
        }
        return copy;
    }

    /** 显隐初始态（§3.3 show「在 props.hidden 静态态之上切换」）：props.hidden=true 初始隐藏，其余可见 */
    private static Map<String, Boolean> initialVisibility(RuleIndex index) {
        Map<String, Boolean> visibility = new LinkedHashMap<>();
        for (Map.Entry<String, SysFormFieldDo> entry : index.mainFields.entrySet()) {
            Map<String, Object> props = entry.getValue().getProps();
            boolean staticHidden = null != props && Boolean.TRUE.equals(props.get("hidden"));
            visibility.put(entry.getKey(), !staticHidden);
        }
        return visibility;
    }

    /** 服务端复算消费的事件域判定（§3.4 一期消费范围，见 SERVER_EVALUATED_EVENTS 注记） */
    private static boolean serverEvaluated(SysFormRuleDo rule) {
        SysFormEvent event = SysFormEvent.of(rule.getEvent());
        return null != event && SERVER_EVALUATED_EVENTS.contains(event.getCode());
    }

    /** 动作清单（null 防御为空清单——结构校验器已保证非空） */
    private static List<SysFormRuleActionDo> actions(SysFormRuleDo rule) {
        return null == rule.getActions() ? List.of() : rule.getActions();
    }

    /** 字段级权限元数据解析（props.permission；非法取值由结构校验器拦截，防御性按 null 处理） */
    private static SysFieldPermission permissionOf(SysFormFieldDo field) {
        Map<String, Object> props = field.getProps();
        Object raw = null == props ? null : props.get("permission");
        return null == raw ? null : SysFieldPermission.of(String.valueOf(raw));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Map<?, ?> rowMap) {
        return (Map<String, Object>) rowMap;
    }

    private static boolean nonBlank(String value) {
        return null != value && !value.isBlank();
    }

    /** CS_FORM_RULE_CYCLE_EXCEEDED 熔断异常（确定性拒绝，§3.5 服务端复算同语义） */
    private static IllegalArgumentException cycleError(String detail) {
        return new IllegalArgumentException(CS_FORM_RULE_CYCLE_EXCEEDED + ": " + detail);
    }
}

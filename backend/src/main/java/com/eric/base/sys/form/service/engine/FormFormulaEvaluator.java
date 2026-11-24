package com.eric.base.sys.form.service.engine;

import com.eric.base.sys.form.model.SysFormFieldDo;
import com.eric.base.sys.form.model.SysFormSchemaDo;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.properties.SysFormulaConstants;
import com.eric.base.sys.formula.service.engine.FormulaContext;
import com.eric.base.sys.formula.service.engine.FormulaEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表单公式字段服务端重算引擎（F15-BE-005，功能设计 §6.3 F14 集成 ADR-09 / §5.2 发布校验链 P4）
 *
 * @author Eric
 * @date 2026/11/24
 * @description 数据面保存校验链 S6（公式重算）与定义面发布校验链 P4（公式静态校验）的
 *              公共编排引擎（落位 service/engine 次级包，先例 {@link FormValidationEngine}）。
 *              本类只做编排，不承载任何表达式求值逻辑——公式执行全部委托
 *              {@link FormulaEngine#execute}（服务内调用不经 HTTP，§6.3），沙箱资源限制
 *              （表达式长度 / 变量个数 / 变量体积 / 循环封顶 / 独立线程超时 / Feature 与
 *              类白名单裁剪）随 FormulaEngine 既有语义，不重复造沙箱；本任务不改 formula
 *              模块代码（复用先例 process 的 ProcessHandlerResolver / ProcessFlowEngine
 *              跨模块消费形态）。
 *              <ul>
 *              <li><b>S6 公式重算</b>（{@link #evaluate(SysFormSchemaDo, Map, Set)}，分册
 *                  §4.3 S6 / 功能设计 §6.3）：提取版本 Schema 内 type=formula 字段（主表 +
 *                  明细行内）按依赖图拓扑序重算——<b>主表公式</b>上下文变量 = 主表字段值
 *                  映射（字段码 → ${code}）+ 明细表数组变量（支持 ${list.field} 两级路径
 *                  投影 + COLLECTION 函数合计，如 SUM(${purchaseDetails.amount})）+ 服务端
 *                  注入系统变量（FormulaContext 构造内经 UserUtil，F14 语义）；<b>明细行内
 *                  公式</b>逐行执行，行上下文注入——变量表 = 当前行字段值（行内字段码直接
 *                  ${code} 引用，如 ROUND(${quantity} * ${price}, 2)）+ 主表字段值（行内
 *                  同码覆盖主表值，行上下文优先——与 FormValidationEngine 联动引用解析
 *                  同口径）。<b>前后端值不一致处置（ADR-09 定稿，API 分册 §7.4③）</b>：
 *                  以服务端重算结果为准回写 payload（BigDecimal 规整随引擎 NUMBER 适配），
 *                  前端展示值仅预览性质、差额不报错——<b>不设
 *                  CS_FORM_FORMULA_MISMATCH</b>（分册 §7.1 16 项错误码不含此码，§7.3
 *                  登记不设码行为），提交载荷中公式字段值不参与校验（S2 已跳过 formula
 *                  类型）；公式结果为 null 时按键移除（「无值」形态，与规则 clearValue
 *                  写入 null 语义一致），不允许带空落库（§6.3 失败处置）。重算结果可能
 *                  触发的后续规则复算不做（分册 §4.3 步骤序 S6 在 S2/S4 合并裁决之后、
 *                  S7 之前，设计未规定「公式重算后再跑一轮规则复算」，公式字段值不参与
 *                  校验）；permission=hidden / 显隐复算终态隐藏的主表公式字段：结果参与
 *                  依赖链求值但不回写 payload（ADR-08 hidden 一律裁剪不落库，写入即泄漏）。</li>
 *              <li><b>P4 公式静态校验</b>（{@link #assertPublishable(SysFormSchemaDo)}，
 *                  功能设计 §5.2 发布校验链 / 分册 §7.2 复用映射）：发布前对 Schema 内全部
 *                  公式字段执行①表达式合法（{@link FormulaEngine#validate} 宽松模式，
 *                  CS_FORMULA_EXPRESSION_TOO_LONG / SYNTAX_INVALID / FUNCTION_NOT_FOUND
 *                  原样透出）→②期望返回类型合法（CS_FORMULA_TYPE_MISMATCH，对齐
 *                  SysFormulaValidateService 规整语义）→③引用字段存在（引用根码必须为
 *                  宿主数据层级内已配置字段；${list.field} 投影根码必须为明细表字段且
 *                  field 为其行内字段，CS_FORMULA_VARIABLE_INVALID）→④依赖图无环
 *                  （CS_FORMULA_EXECUTE_FAILED——分册 §7.2 公式族复用映射无专用环码，
 *                  取执行失败码为族内确定性兜底，运行期拓扑熔断同码）。</li>
 *              <li><b>依赖图与拓扑序</b>（§6.3）：对每个公式表达式解析 ${} 引用（字符串
 *                  字面量内的 ${} 属展示文本不收集，扫描语义与 FormulaEngine 代码段扫描
 *                  一致；根码依赖复用 {@link FormulaEngine#extractDependencies} 同形态），
 *                  构建公式字段有向图（公式 → 公式引用边；非公式字段与系统变量
 *                  {@code SYS_CURRENT_*} 为外部输入无边）并拓扑排序——行内公式可引用主表
 *                  公式（主表公式先算）、主表公式可经 ${list.field} 投影引用行内公式
 *                  （行内公式先算），拓扑序一次性消解跨层级交错依赖。设计期发布校验已
 *                  拒绝循环依赖，运行期拓扑排序兜底检测（存在环抛
 *                  CS_FORMULA_EXECUTE_FAILED，§6.3「运行期拓扑深度上限兜底」）。</li>
 *              <li><b>失败处置</b>（§6.3 / 分册 §7.2）：公式执行失败复用 F14
 *                  CS_FORMULA_* 语义映射为确定性错误、阻断保存（公式字段值是数据的一部分，
 *                  不允许带空落库）——{@link FormulaEngine} 抛出的
 *                  IllegalArgumentException(CS_FORMULA_*: message) 保留错误码与引擎消息、
 *                  追加工表字段定位（引擎已保证不裸抛 Aviator 原生异常），其余运行时异常
 *                  兜底包装为 CS_FORMULA_EXECUTE_FAILED，不允许裸异常穿透到调用方。</li>
 *              </ul>
 */
@Component
public class FormFormulaEvaluator {

    private static final Logger log = LoggerFactory.getLogger(FormFormulaEvaluator.class);

    /** 公式组件 type（功能设计 §1.3 数据类组件） */
    private static final String TYPE_FORMULA = "formula";

    /** 明细表组件 type（行上下文宿主） */
    private static final String TYPE_DETAIL_TABLE = "detailTable";

    /** props 键：公式表达式（${code} 形态，设计器内嵌 FormulaDesigner 配置） */
    private static final String PROP_EXPRESSION = "expression";

    /** props 键：期望返回类型（NUMBER/STRING/BOOLEAN/DATE，随 SysFormulaReturnType 规整） */
    private static final String PROP_EXPECTED_RETURN_TYPE = "expectedReturnType";

    /** props 键：字段级权限元数据（permission=hidden 主表公式不回写，ADR-08） */
    private static final String PROP_PERMISSION = "permission";

    /**
     * ${} 引用形态（变量码约束与两级路径投影同 FormulaEngine VAR_PATTERN——
     * [a-zA-Z_][a-zA-Z0-9_]*，投影仅两级 list.field；本类扫描只用于依赖图构建与
     * 引用存在性校验，表达式合法性仍以引擎校验为准）
     */
    private static final Pattern REF_PATTERN =
            Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)(?:\\.([a-zA-Z_][a-zA-Z0-9_]*))?}");

    /** CS_FORMULA_* 错误码前缀（FormulaEngine 错误约定：IllegalArgumentException(code + ": " + message)） */
    private static final String FORMULA_CODE_PREFIX = "CS_FORMULA_";

    private final FormulaEngine formulaEngine;

    public FormFormulaEvaluator(FormulaEngine formulaEngine) {
        this.formulaEngine = formulaEngine;
    }

    /* ==================== S6 公式重算（分册 §4.3 S6，功能设计 §6.3 ADR-09） ==================== */

    /**
     * 公式字段服务端重算（S6，就地回写 payload）：提取 Schema 内 type=formula 字段
     * （主表 + 明细行内）按依赖图拓扑序经 {@link FormulaEngine#execute} 受限重算，
     * 以服务端结果为准回写（ADR-09：前端值仅预览，差额不报错不设
     * CS_FORM_FORMULA_MISMATCH）。重算失败复用 CS_FORMULA_* 语义映射确定性抛出、
     * 阻断保存（不裸异常穿透）。
     *
     * <p>调用前置（保存校验链序，分册 §4.3）：S2/S4 合并裁决已通过（行结构与白名单
     * 已保证）、S5 级联复核已完成；本方法在载荷归一（normalize）之前调用，使重算值
     * 进入落库与响应形态（分册 §4.3「公式重算值回填」）。</p>
     *
     * @param schema           实例锁定版本 Schema 协议对象（null 或无公式字段直接返回）
     * @param payload          规则复算与合并裁决后的最终载荷（方法内就地回写公式字段值；
     *                         明细行对象为调用方工作副本，写入不泄漏到调用方原载荷）
     * @param hiddenFieldCodes 主表隐藏字段集（显隐复算终态 ∪ permission=hidden，S3 裁剪
     *                         来源；可空）——隐藏的主表公式字段结果参与依赖链求值但不回写
     *                         payload（ADR-08 hidden 一律裁剪不落库）
     */
    public void evaluate(SysFormSchemaDo schema, Map<String, Object> payload, Set<String> hiddenFieldCodes) {
        if (null == schema || null == payload) {
            return;
        }
        FormulaIndex index = buildIndex(schema);
        if (index.formulaNodes.isEmpty()) {
            return;
        }
        // 依赖图拓扑序（行内公式与主表公式跨层级统一排序——行内公式可引用主表公式值、
        // 主表公式可经 ${list.field} 投影引用行内公式值；存在环抛 CS_FORMULA_EXECUTE_FAILED，
        // 设计期发布校验已拒绝循环依赖，此为运行期兜底 §6.3）
        List<FormulaNode> order = topologicalOrder(index);
        // 主表公式在途重算值（字段码 → 重算结果）：依赖公式变量解析的权威来源——拓扑序
        // 保证被依赖公式先算；隐藏主表公式结果不回写 payload（ADR-08）但仍进本表供
        // 依赖链求值（隐藏值在存储态不可见、依赖公式可消费）
        Map<String, Object> mainResults = new LinkedHashMap<>();
        for (FormulaNode node : order) {
            if (null == node.detailCode()) {
                evaluateMainFormula(node, index, payload, hiddenFieldCodes, mainResults);
            } else {
                evaluateRowFormula(node, index, payload, mainResults);
            }
        }
    }

    /**
     * 主表公式重算（单公式）：上下文变量 = 主表字段值映射 + 明细表数组变量（主表层级
     * 全部载荷值，含已重算的公式字段值与行内重算后的明细行数组——拓扑序保证依赖值就绪）
     * + 服务端注入系统变量（FormulaContext 构造，UserUtil 会话语义）；执行结果回写
     * payload（null 结果移除键）；隐藏字段（显隐终态 ∪ permission=hidden）不回写但记录
     * 在途重算值供依赖公式消费。
     */
    private void evaluateMainFormula(FormulaNode node, FormulaIndex index, Map<String, Object> payload,
                                     Set<String> hiddenFieldCodes, Map<String, Object> mainResults) {
        SysFormFieldDo field = index.mainScope.get(node.code());
        Map<String, Object> props = null == field ? null : field.getProps();
        String expression = null == props ? null : textProp(props, PROP_EXPRESSION);
        SysFormulaReturnType expected = expectedReturnType(props);
        String location = "公式字段「" + node.code() + "」";
        FormulaContext context = new FormulaContext(mainVariables(payload, index, mainResults));
        Object result = executeFormula(location, expression, context, expected);
        if (null != result) {
            mainResults.put(node.code(), result);
        }
        if (isHiddenMain(node.code(), field, hiddenFieldCodes)) {
            log.debug("form formula recalculated for hidden field, result not written back: code={}", node.code());
            return;
        }
        writeResult(payload, node.code(), result);
    }

    /**
     * 明细行内公式重算（单公式 × 逐行）：每行一次求值——变量表 = 当前行字段值（行内
     * 字段码直接 ${code} 引用）+ 主表字段值（行内同码覆盖主表值，行上下文优先）；结果
     * 逐行写回行对象（null 结果移除键）。行内公式无 hidden 概念（§3.6 一期边界：行内
     * 显隐为渲染层语义，行数据按行原样落库）。
     */
    private void evaluateRowFormula(FormulaNode node, FormulaIndex index, Map<String, Object> payload,
                                    Map<String, Object> mainResults) {
        SysFormFieldDo field = index.rowScopes.get(node.detailCode()).get(node.code());
        Map<String, Object> props = null == field ? null : field.getProps();
        String expression = null == props ? null : textProp(props, PROP_EXPRESSION);
        SysFormulaReturnType expected = expectedReturnType(props);
        Object rowsValue = payload.get(node.detailCode());
        if (!(rowsValue instanceof List<?> rows)) {
            return; // 行数组结构违规由 S2 承载（合并裁决已通过，防御性跳过）
        }
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (!(row instanceof Map<?, ?> rowMap)) {
                continue; // 行对象结构违规由 S2 承载，防御性跳过
            }
            String location = "明细表「" + node.detailCode() + "」行内公式「" + node.code() + "」"
                    + "（第 " + (i + 1) + " 行）";
            Map<String, Object> rowVars = new LinkedHashMap<>(mainVariables(payload, index, mainResults));
            rowVars.putAll(asStringMap(rowMap));
            FormulaContext context = new FormulaContext(rowVars);
            Object result = executeFormula(location, expression, context, expected);
            writeResult(asStringMap(rowMap), node.code(), result);
        }
    }

    /* ==================== P4 公式静态校验（功能设计 §5.2 发布校验链，分册 §7.2 复用映射） ==================== */

    /**
     * 发布校验链 P4 公式静态校验（全部通过方可发布，任一失败确定性拒绝）：
     * <ol>
     * <li>表达式合法：{@link FormulaEngine#validate} 宽松模式（declaredVars=null、strict=false），
     *     长度超限 / 语法错误 / 未注册函数以 CS_FORMULA_EXPRESSION_TOO_LONG /
     *     CS_FORMULA_SYNTAX_INVALID / CS_FORMULA_FUNCTION_NOT_FOUND 原样透出（分册 §7.2
     *     「校验结果 code 原样透传为 message 前缀」）；</li>
     * <li>期望返回类型合法：props.expectedReturnType 非空且不在 SysFormulaReturnType 枚举面 →
     *     CS_FORMULA_TYPE_MISMATCH（对齐 SysFormulaValidateService 规整语义）；</li>
     * <li>引用字段存在：表达式引用根码必须为宿主数据层级内已配置字段（主表公式引用根码 ∈
     *     主表层级、行内公式引用根码 ∈ 行上下文 ∪ 主表层级；${list.field} 投影根码必须为
     *     明细表字段且 field 为其行内字段），未知引用抛 CS_FORMULA_VARIABLE_INVALID；
     *     服务端注入系统变量（SYS_CURRENT_USER_ID / SYS_CURRENT_DEPT_ID / SYS_CURRENT_ROLE）
     *     豁免字段存在性校验（F14 会话变量语义，非表单字段）；</li>
     * <li>依赖图无环：公式字段有向图存在环 → CS_FORMULA_EXECUTE_FAILED（族内确定性兜底，
     *     运行期拓扑熔断同码）。</li>
     * </ol>
     * 引擎首错即抛（对齐 SysFormulaValidateService「引擎失败首错即返回」），失败阻断发布。
     *
     * @param schema 待发布草稿 Schema 协议对象（null 按无公式处理直接放行——结构校验链
     *               P2/P3 已保证 fields 存在，防御性口径）
     */
    public void assertPublishable(SysFormSchemaDo schema) {
        if (null == schema) {
            return;
        }
        FormulaIndex index = buildIndex(schema);
        if (index.formulaNodes.isEmpty()) {
            return;
        }
        Map<FormulaNode, Set<FormulaNode>> dependencies = new LinkedHashMap<>();
        for (FormulaNode node : index.formulaNodes) {
            SysFormFieldDo field = fieldOf(node, index);
            Map<String, Object> props = null == field ? null : field.getProps();
            // ② 期望返回类型规整（非空且不合法直接拒绝，不进入语法校验）
            SysFormulaReturnType expected = requireExpectedReturnType(node, props);
            // ① 表达式合法（宽松模式静态校验，CS_FORMULA_* 原样透出）
            String expression = null == props ? null : textProp(props, PROP_EXPRESSION);
            executeStatic(() -> formulaEngine.validate(expression, null, expected, false));
            // ③ 引用字段存在（宿主数据层级内已配置字段；系统变量豁免）
            assertReferencesExist(node, scanReferences(expression), index);
            // 依赖边登记（④ 依赖图无环检查共用）
            dependencies.put(node, dependencyNodes(node, scanReferences(expression), index));
        }
        topologicalOrder(index.formulaNodes, dependencies,
                "公式依赖图存在循环依赖，无法静态排序（发布校验链 P4，功能设计 §5.2）");
    }

    /** 期望返回类型规整（P4）：非空且不在枚举面 → CS_FORMULA_TYPE_MISMATCH（确定性拒绝） */
    private static SysFormulaReturnType requireExpectedReturnType(FormulaNode node, Map<String, Object> props) {
        String raw = null == props ? null : textProp(props, PROP_EXPECTED_RETURN_TYPE);
        if (null == raw || raw.isBlank()) {
            return null;
        }
        SysFormulaReturnType expected = SysFormulaReturnType.of(raw);
        if (null == expected) {
            throw new IllegalArgumentException(SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH
                    + ": 公式字段「" + node.code() + "」期望返回类型不合法: " + raw);
        }
        return expected;
    }

    /**
     * 引用字段存在性校验（P4 第③道）：引用根码必须为宿主数据层级内已配置字段——主表公式
     * 引用根码 ∈ 主表层级（数据类字段 / 明细表宿主 / 公式字段），行内公式引用根码 ∈
     * 行上下文 ∪ 主表层级（行内同码可与主表互载，§2.1）；${list.field} 投影根码必须为
     * 明细表字段且 field 为其行内字段。系统变量（FormulaContext 注入面三个）豁免。
     * 未知引用抛 CS_FORMULA_VARIABLE_INVALID（分册 §7.2「变量码或投影路径非法」族）。
     */
    private void assertReferencesExist(FormulaNode node, List<FieldRef> references, FormulaIndex index) {
        for (FieldRef ref : references) {
            if (isSystemVariable(ref.rootCode())) {
                continue;
            }
            String where = null == node.detailCode()
                    ? "公式字段「" + node.code() + "」"
                    : "明细表「" + node.detailCode() + "」行内公式字段「" + node.code() + "」";
            if (null == ref.fieldKey()) {
                // 一级引用：主表公式认主表层级，行内公式认行上下文 ∪ 主表层级
                boolean exists = null != node.detailCode()
                        ? (index.rowScopes.get(node.detailCode()).containsKey(ref.rootCode())
                           || index.mainScope.containsKey(ref.rootCode()))
                        : index.mainScope.containsKey(ref.rootCode());
                if (!exists) {
                    throw new IllegalArgumentException(SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID
                            + ": " + where + "引用了不存在的字段: ${" + ref.rootCode() + "}");
                }
                continue;
            }
            // 二级投影 ${list.field}：根码必须为明细表字段、field 必须为其行内字段
            SysFormFieldDo rootField = index.mainScope.get(ref.rootCode());
            Map<String, SysFormFieldDo> rowScope = index.rowScopes.get(ref.rootCode());
            if (null == rootField || !TYPE_DETAIL_TABLE.equals(rootField.getType()) || null == rowScope) {
                throw new IllegalArgumentException(SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID
                        + ": " + where + "路径投影根码不是明细表字段: ${" + ref.rootCode() + "." + ref.fieldKey() + "}");
            }
            if (!rowScope.containsKey(ref.fieldKey())) {
                throw new IllegalArgumentException(SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID
                        + ": " + where + "路径投影引用了明细表「" + ref.rootCode() + "」内不存在的行内字段: ${"
                        + ref.rootCode() + "." + ref.fieldKey() + "}");
            }
        }
    }

    /* ==================== 依赖图与拓扑序 ==================== */

    /** 公式字段引用（${code} 一级引用 fieldKey 为 null；${list.field} 二级投影取 field 键） */
    private record FieldRef(String rootCode, String fieldKey) {
    }

    /** 公式字段图节点：主表公式 detailCode 为 null；行内公式 detailCode 为宿主明细表字段码 */
    private record FormulaNode(String code, String detailCode) {
    }

    /** 公式字段索引（字段树遍历，层级口径与 FormDataBasicValidator / FormValidationEngine 一致） */
    private static final class FormulaIndex {

        /** 主表层级：字段码 → 字段对象（数据类 12 组件 + detailTable 宿主；布局容器透明） */
        final Map<String, SysFormFieldDo> mainScope = new LinkedHashMap<>();

        /** 明细行层级：detailTable 字段码 → 行内字段码 → 字段对象（行上下文隔离） */
        final Map<String, Map<String, SysFormFieldDo>> rowScopes = new LinkedHashMap<>();

        /** 公式字段节点（Schema 字段序：主表公式在前、行内公式随明细表宿主序） */
        final Set<FormulaNode> formulaNodes = new LinkedHashSet<>();

        /** 主表公式节点按字段码索引 */
        FormulaNode mainFormula(String code) {
            FormulaNode node = new FormulaNode(code, null);
            return formulaNodes.contains(node) ? node : null;
        }

        /** 行内公式节点按（明细表字段码, 行内字段码）索引 */
        FormulaNode rowFormula(String detailCode, String code) {
            FormulaNode node = new FormulaNode(code, detailCode);
            return formulaNodes.contains(node) ? node : null;
        }
    }

    /**
     * 构建公式字段索引（字段树遍历；group/grid 布局容器透明下钻、栅格列对象仅承载
     * span/fields；detailTable 的 children 为独立行上下文）并登记公式节点。
     */
    private static FormulaIndex buildIndex(SysFormSchemaDo schema) {
        FormulaIndex index = new FormulaIndex();
        collectFields(schema.getFields(), index);
        return index;
    }

    /** 递归收集一个字段列表（主表层级字段 + 明细行内字段，公式节点随字段登记） */
    private static void collectFields(List<SysFormFieldDo> fields, FormulaIndex index) {
        if (null == fields) {
            return;
        }
        for (SysFormFieldDo field : fields) {
            if (null == field) {
                continue;
            }
            // 栅格列对象仅承载 span/fields：列内字段与宿主栅格同处一个数据层级
            if (field.isGridColumn()) {
                collectFields(field.getFields(), index);
                continue;
            }
            String type = field.getType();
            String code = field.getField();
            if ("group".equals(type) || "grid".equals(type)) {
                collectFields(field.getChildren(), index);
                continue;
            }
            if (TYPE_DETAIL_TABLE.equals(type)) {
                Map<String, SysFormFieldDo> rowScope = new LinkedHashMap<>();
                collectRowFields(field.getChildren(), rowScope, code, index);
                index.rowScopes.put(code, rowScope);
                if (null != code) {
                    index.mainScope.put(code, field);
                }
                continue;
            }
            if (null != code) {
                index.mainScope.put(code, field);
                if (TYPE_FORMULA.equals(type)) {
                    index.formulaNodes.add(new FormulaNode(code, null));
                }
            }
        }
    }

    /** 递归收集明细行内字段（行内仅数据类组件 R04，行内公式节点登记） */
    private static void collectRowFields(List<SysFormFieldDo> children, Map<String, SysFormFieldDo> rowScope,
                                         String detailCode, FormulaIndex index) {
        if (null == children) {
            return;
        }
        for (SysFormFieldDo child : children) {
            if (null == child || null == child.getField()) {
                continue;
            }
            if (!rowScope.containsKey(child.getField())) {
                rowScope.put(child.getField(), child);
                if (TYPE_FORMULA.equals(child.getType())) {
                    index.formulaNodes.add(new FormulaNode(child.getField(), detailCode));
                }
            }
        }
    }

    /** 节点对应字段对象（主表节点取主表层级、行内节点取行上下文） */
    private static SysFormFieldDo fieldOf(FormulaNode node, FormulaIndex index) {
        return null == node.detailCode()
                ? index.mainScope.get(node.code())
                : index.rowScopes.get(node.detailCode()).get(node.code());
    }

    /**
     * 公式节点依赖边解析：引用目标为公式字段时登记「被依赖公式 → 当前节点」边；
     * 一级引用行上下文优先（行内公式先认行上下文公式、再认主表公式），二级投影认
     * 投影目标明细表内行内公式。非公式字段与系统变量为外部输入，无边。
     */
    private Set<FormulaNode> dependencyNodes(FormulaNode node, List<FieldRef> references, FormulaIndex index) {
        Set<FormulaNode> dependencies = new LinkedHashSet<>();
        for (FieldRef ref : references) {
            if (isSystemVariable(ref.rootCode())) {
                continue;
            }
            if (null == ref.fieldKey()) {
                if (null != node.detailCode()) {
                    FormulaNode rowFormula = index.rowFormula(node.detailCode(), ref.rootCode());
                    if (null != rowFormula && !rowFormula.equals(node)) {
                        dependencies.add(rowFormula);
                        continue;
                    }
                }
                FormulaNode mainFormula = index.mainFormula(ref.rootCode());
                if (null != mainFormula && !mainFormula.equals(node)) {
                    dependencies.add(mainFormula);
                }
                continue;
            }
            FormulaNode projected = index.rowFormula(ref.rootCode(), ref.fieldKey());
            if (null != projected && !projected.equals(node)) {
                dependencies.add(projected);
            }
        }
        return dependencies;
    }

    /**
     * 全量拓扑排序（索引内全部公式节点，依赖边全量解析）：返回求值顺序（Kahn 入度法，
     * 节点按 Schema 字段序入队保证确定性）；存在环（排序后仍有剩余节点）抛
     * CS_FORMULA_EXECUTE_FAILED（§6.3 运行期兜底 / P4 依赖图无环共用）。
     */
    private List<FormulaNode> topologicalOrder(FormulaIndex index) {
        Map<FormulaNode, Set<FormulaNode>> dependencies = new LinkedHashMap<>();
        for (FormulaNode node : index.formulaNodes) {
            SysFormFieldDo field = fieldOf(node, index);
            String expression = null == field || null == field.getProps()
                    ? null : textProp(field.getProps(), PROP_EXPRESSION);
            dependencies.put(node, dependencyNodes(node, scanReferences(expression), index));
        }
        return topologicalOrder(index.formulaNodes, dependencies,
                "公式依赖图存在循环依赖，无法确定重算顺序（运行期拓扑兜底，功能设计 §6.3）");
    }

    /**
     * Kahn 拓扑排序（求值序确定性）：依赖图 node → 其依赖集合；入度为「自身未就绪依赖
     * 个数」，依赖全部出队后入就绪队列（节点按 Schema 字段序入队，同层求值顺序确定）；
     * 排序后仍有剩余节点即存在环，抛 CS_FORMULA_EXECUTE_FAILED 并列出剩余节点（环定位）。
     */
    private static List<FormulaNode> topologicalOrder(Set<FormulaNode> nodes,
                                                      Map<FormulaNode, Set<FormulaNode>> dependencies,
                                                      String cycleMessage) {
        // 逆向邻接：依赖节点 → 依赖它的节点清单（依赖出队时递减依赖方入度）
        Map<FormulaNode, Set<FormulaNode>> dependents = new LinkedHashMap<>();
        Map<FormulaNode, Integer> pending = new LinkedHashMap<>();
        for (FormulaNode node : nodes) {
            Set<FormulaNode> nodeDependencies = dependencies.getOrDefault(node, Set.of());
            pending.put(node, nodeDependencies.size());
            for (FormulaNode dependency : nodeDependencies) {
                dependents.computeIfAbsent(dependency, k -> new LinkedHashSet<>()).add(node);
            }
        }
        Queue<FormulaNode> ready = new LinkedList<>();
        for (FormulaNode node : nodes) {
            if (pending.get(node) == 0) {
                ready.add(node);
            }
        }
        List<FormulaNode> order = new ArrayList<>(nodes.size());
        while (!ready.isEmpty()) {
            FormulaNode node = ready.poll();
            order.add(node);
            for (FormulaNode dependent : dependents.getOrDefault(node, Set.of())) {
                int remaining = pending.merge(dependent, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (order.size() < nodes.size()) {
            Set<FormulaNode> cyclic = new LinkedHashSet<>(nodes);
            order.forEach(cyclic::remove);
            List<String> codes = cyclic.stream()
                    .map(node -> null == node.detailCode() ? node.code()
                            : node.detailCode() + "." + node.code())
                    .toList();
            throw new IllegalArgumentException(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED
                    + ": " + cycleMessage + "，环内公式字段: " + codes);
        }
        return order;
    }

    /* ==================== ${} 引用扫描（依赖图构建用） ==================== */

    /**
     * 定位代码段（字符串字面量外）内的全部 ${} 引用：'…' 与 "…" 字面量以内的 ${} 属
     * 展示文本不收集（扫描语义与 FormulaEngine 代码段扫描一致——含转义引号 \\' \\" 识别）；
     * 非法变量码形态交由引擎 validate（P4 前置）与 execute（运行期）确定性报错，此处
     * 不重复校验。
     */
    private static List<FieldRef> scanReferences(String expression) {
        List<FieldRef> references = new ArrayList<>();
        if (null == expression || expression.isBlank()) {
            return references;
        }
        int length = expression.length();
        int i = 0;
        while (i < length) {
            char current = expression.charAt(i);
            if ('\'' == current || '"' == current) {
                char quote = current;
                i++;
                while (i < length) {
                    char inner = expression.charAt(i);
                    if ('\\' == inner && i + 1 < length) {
                        i += 2;
                        continue;
                    }
                    i++;
                    if (inner == quote) {
                        break;
                    }
                }
                continue;
            }
            if ('$' == current && i + 1 < length && '{' == expression.charAt(i + 1)) {
                Matcher matcher = REF_PATTERN.matcher(expression);
                matcher.region(i, length);
                if (matcher.lookingAt()) {
                    references.add(new FieldRef(matcher.group(1), matcher.group(2)));
                    i = matcher.end();
                    continue;
                }
            }
            i++;
        }
        return references;
    }

    /* ==================== 执行与结果回写 ==================== */

    /**
     * 受限执行单公式（S6 / 行内重算共用）：委托 {@link FormulaEngine#execute}（沙箱资源
     * 限制随引擎既有语义）；CS_FORMULA_* 前缀业务错误保留错误码语义、追加工表字段定位
     * （确定性错误码不变，分册 §7.2 复用映射），其余运行时异常兜底包装为
     * CS_FORMULA_EXECUTE_FAILED——不裸异常穿透（§6.3 失败处置：阻断保存）。
     *
     * @param location   错误定位描述（公式字段码 / 行内公式附明细表与行号）
     * @param expression 公式表达式（${code} 形态；缺失/空白由引擎按 CS_FORMULA_SYNTAX_INVALID 拒绝）
     * @param context    只读执行上下文（调用方变量 + 服务端系统变量）
     * @param expected   期望返回类型（null 跳过结果类型适配）
     * @return 类型适配后的执行结果（null 结果由调用方按键移除）
     */
    private Object executeFormula(String location, String expression, FormulaContext context,
                                  SysFormulaReturnType expected) {
        try {
            return formulaEngine.execute(expression, context, expected);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (null != message && message.startsWith(FORMULA_CODE_PREFIX)) {
                // CS_FORMULA_* 语义映射保留（code 前缀与引擎消息不变），追加定位信息
                throw new IllegalArgumentException(message + "（" + location + "）", e);
            }
            log.warn("form formula execute failed [CS_FORMULA_EXECUTE_FAILED]: {}", location);
            throw new IllegalArgumentException(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED
                    + ": 公式执行失败: " + message + "（" + location + "）", e);
        } catch (RuntimeException e) {
            log.warn("form formula execute failed [CS_FORMULA_EXECUTE_FAILED]: {}, cause: {}",
                    location, e.getClass().getSimpleName());
            throw new IllegalArgumentException(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED
                    + ": 公式执行失败: " + e.getMessage() + "（" + location + "）", e);
        }
    }

    /** P4 静态校验执行包装：引擎校验错误 CS_FORMULA_* 原样透出，其余兜底 EXECUTE_FAILED */
    private static void executeStatic(Runnable validation) {
        try {
            validation.run();
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (null != message && message.startsWith(FORMULA_CODE_PREFIX)) {
                throw e;
            }
            throw new IllegalArgumentException(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED
                    + ": 公式静态校验失败: " + message, e);
        }
    }

    /**
     * 结果回写（null 结果移除键——「无值」形态，与规则 clearValue 写入 null 语义一致；
     * 结果为 null 不允许带空落库的公式字段以键缺失承载）。
     */
    private static void writeResult(Map<String, Object> target, String code, Object result) {
        if (null == result) {
            target.remove(code);
            return;
        }
        target.put(code, result);
    }

    /* ==================== 变量表与属性小件 ==================== */

    /**
     * 主表层级变量表（主表公式上下文，§6.3）：主表层级全部载荷值——数据类字段值映射
     * （字段码 → ${code}）+ 明细表数组变量（支持 ${list.field} 两级路径投影 + COLLECTION
     * 函数合计）；重算值优先（mainResults 为拓扑序已算公式字段的在途结果——隐藏公式
     * 不回写 payload 但参与依赖链），仅装载存在的键（缺失变量 = nil 语义，由引擎确定性
     * 报错兜底）。
     */
    private static Map<String, Object> mainVariables(Map<String, Object> payload, FormulaIndex index,
                                                     Map<String, Object> mainResults) {
        Map<String, Object> vars = new LinkedHashMap<>();
        for (String code : index.mainScope.keySet()) {
            if (mainResults.containsKey(code)) {
                vars.put(code, mainResults.get(code));
                continue;
            }
            if (payload.containsKey(code)) {
                vars.put(code, payload.get(code));
            }
        }
        return vars;
    }

    /**
     * 主表隐藏字段判定（结果不回写）：显隐复算终态 ∪ permission=hidden（hiddenFieldCodes
     * 为 S3 裁剪来源集）；permission=hidden 为防御性兜底（S3 已裁剪的字段不在载荷中，
     * 回写判断以集合为准、props 复核兜底直接构造场景）
     */
    private static boolean isHiddenMain(String code, SysFormFieldDo field, Set<String> hiddenFieldCodes) {
        if (null != hiddenFieldCodes && hiddenFieldCodes.contains(code)) {
            return true;
        }
        Map<String, Object> props = null == field ? null : field.getProps();
        Object permission = null == props ? null : props.get(PROP_PERMISSION);
        return "hidden".equals(permission);
    }

    /** 期望返回类型解析（运行期宽松口径：非空且不合法按 null 处理跳过类型适配——P4 已前置拒绝） */
    private static SysFormulaReturnType expectedReturnType(Map<String, Object> props) {
        String raw = null == props ? null : textProp(props, PROP_EXPECTED_RETURN_TYPE);
        return null == raw || raw.isBlank() ? null : SysFormulaReturnType.of(raw);
    }

    /** F14 会话系统变量豁免集（FormulaContext 注入面，非表单字段） */
    private static boolean isSystemVariable(String code) {
        return FormulaContext.SYS_CURRENT_USER_ID.equals(code)
                || FormulaContext.SYS_CURRENT_DEPT_ID.equals(code)
                || FormulaContext.SYS_CURRENT_ROLE.equals(code);
    }

    /** props 文本属性（非 String 或缺省返回 null） */
    private static String textProp(Map<String, Object> props, String key) {
        Object raw = null == props ? null : props.get(key);
        return raw instanceof String text ? text : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Map<?, ?> rowMap) {
        return (Map<String, Object>) rowMap;
    }
}

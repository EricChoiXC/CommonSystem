package com.eric.base.sys.form.service.engine;

import com.eric.base.sys.form.enums.SysFormEvent;
import com.eric.base.sys.form.enums.SysFieldPermission;
import com.eric.base.sys.form.model.SysFormFieldDo;
import com.eric.base.sys.form.model.SysFormRuleActionDo;
import com.eric.base.sys.form.model.SysFormRuleConditionDo;
import com.eric.base.sys.form.model.SysFormRuleDo;
import com.eric.base.sys.form.model.SysFormRuleValidateDo;
import com.eric.base.sys.form.model.SysFormSchemaDo;
import com.eric.base.sys.form.model.SysFormSchemaErrorDo;
import com.eric.base.sys.form.properties.SysFormConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 表单 Schema 结构校验器（F15-BE-001，功能设计 §2 / §5.3 发布校验链 P2/P3、ADR-10）
 *
 * @author Eric
 * @date 2026/11/24
 * @description Schema 结构合法性权威裁决（落位 service/engine 次级包，先例 formula）：
 *              JSON 解析失败 / 结构违规 → CS_FORM_SCHEMA_INVALID（逐条附中文原因），
 *              资源上限超限 → CS_FORM_SCHEMA_LIMIT_EXCEEDED（§2.2 常量收口，数值编码期
 *              可复核调整、语义与超限错误码不变）。校验项（逐条对照功能设计 §2.1 / §3.2 /
 *              §3.3 / §3.4 与 API 分册 §7.1 第 6/7 项）：
 *              <ul>
 *              <li>字段码正则（§2.1）与同一数据层级唯一——布局容器（group/grid）不产生
 *                  新数据层级（其 children 与宿主同层级），明细行内为独立行上下文
 *                  （可与主表同码，行内必须唯一）；</li>
 *              <li>type 白名单（ALLOWED_FIELD_TYPES 18 个；REJECTED_TYPES 命中给专门
 *                  文案——permission 仅协议位不进 fields、code 随 R02 一期不执行）；</li>
 *              <li>label 非空；容器 children 递归与嵌套深度 ≤ MAX_NEST_DEPTH（顶层字段 = 1，
 *                  容器内字段 = 容器链深 + 1，栅格列对象为布局载体不计深度）；</li>
 *              <li>group/grid 相互嵌套最多一层（容器链最多两个容器，§2.1）；detailTable 内
 *                  禁嵌套任何容器与明细表（R04）、行内仅 ALLOWED_DETAIL_CHILD_TYPES
 *                  （数据类 12，含行内 formula）；</li>
 *              <li>grid 列对象 span 为 1~24 正整数（24 栅格制 §1.3）且仅承载 span/fields；</li>
 *              <li>规则：ruleId 表单内唯一、event ∈ SysFormEvent 八事件、condition operator
 *                  ∈ §3.2 全集 10 个（in/notIn value 必须数组、gt/gte/lt/lte value 数值或
 *                  日期字符串、empty/notEmpty 省略 value、equal/notEqual 标量）、动作 type
 *                  ∈ §3.3 全集 6 个（setRequired value 必须布尔、setValidate 载荷至少一项且
 *                  pattern 可编译）、triggerField 等于宿主字段码且存在、动作 target 字段存在
 *                  （明细表宿主的规则 target 限其行内字段，§3.1 行上下文）、onSubmit /
 *                  onValidate 禁 setValue/clearValue（§3.4）、规则总数 ≤ MAX_RULE_COUNT；</li>
 *              <li>级联（R08 第 4 条）：parentField 存在时必须为 DATA_FIELD_TYPES 数据类字段
 *                  且非自身；options[].showCondition 的 operator/value 合法；</li>
 *              <li>资源上限：字段总数（含容器内与明细行内）≤ MAX_FIELD_COUNT、文本 maxlength
 *                  ≤ MAX_TEXT_LENGTH、detailTable rowMax ≤ MAX_DETAIL_ROWS 且 rowMin ≤ rowMax、
 *                  Schema 原文体积 ≤ MAX_SCHEMA_JSON_LENGTH（仅 validate(String) 入口）。错误码
 *                  归属：资源常量数值超限 → CS_FORM_SCHEMA_LIMIT_EXCEEDED（API 分册 §7.1
 *                  第 7 项枚举「字段总数 / 嵌套深度 / Schema 体积 / 规则总数 / 文本长度」）；
 *                  结构 / 协议违规（容器链超一层、明细表内嵌容器、字段码 / type / 规则要素
 *                  非法等）→ CS_FORM_SCHEMA_INVALID（第 6 项）。</li>
 *              </ul>
 *              <p><b>边界</b>：本校验器只做结构合法性，不做数据校验 / 公式校验——公式静态
 *              校验（表达式合法 / 引用字段存在 / 依赖图无环）属发布校验链 P4，由
 *              F15-BE-005 集成 F14 校验服务承担（API 分册 §7.2：错误码原样透传
 *              CS_FORMULA_*）；数据面基础 / 动态校验属 FormValidationEngine、规则 DSL 求值
 *              属 FormRuleEngine（Wave 2，本任务不建）。</p>
 */
@Component
public class FormSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(FormSchemaValidator.class);

    /** Schema JSON 序列化 / 解析（存储态字符串 ↔ 协议对象，禁止手工拼串，先例 SysFormulaService） */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** 字段码正则（§2.1，收口 SysFormConstants.FIELD_CODE_REGEX） */
    private static final Pattern FIELD_CODE = Pattern.compile(SysFormConstants.FIELD_CODE_REGEX);

    /* ---------- operator 全集分组（功能设计 §3.2，10 个） ---------- */

    /** 等值比较类：value 为标量（字符串 / 数值 / 布尔精确匹配） */
    private static final Set<String> SCALAR_OPERATORS = Set.of("equal", "notEqual");
    /** 大小比较类：value 为数值或日期字符串（date 按 same-format 字符串字典序比较） */
    private static final Set<String> ORDER_OPERATORS = Set.of("gt", "gte", "lt", "lte");
    /** 集合属于类：value 必须为数组 */
    private static final Set<String> COLLECTION_OPERATORS = Set.of("in", "notIn");
    /** 空判类：value 省略 */
    private static final Set<String> EMPTY_OPERATORS = Set.of("empty", "notEmpty");

    /* ---------- 动作类型全集（功能设计 §3.3，6 个） ---------- */

    /** 仅 target 载荷：show / hide / clearValue */
    private static final Set<String> TARGET_ONLY_ACTIONS = Set.of("show", "hide", "clearValue");
    /** target + value 载荷：setValue（字面量或 ${字段码} 引用）/ setRequired（boolean） */
    private static final Set<String> TARGET_VALUE_ACTIONS = Set.of("setValue", "setRequired");
    /** target + validate 载荷 */
    private static final String ACTION_SET_VALIDATE = "setValidate";
    /** value 必须为布尔的动作 */
    private static final String ACTION_SET_REQUIRED = "setRequired";
    /** 提交 / 校验阶段禁止的动作（§3.4：防提交竞态） */
    private static final Set<String> SUBMIT_FORBIDDEN_ACTIONS = Set.of("setValue", "clearValue");

    /** 明细表组件 type（数据容器特例：可作规则触发字段——onAdd/onDelete 行事件宿主，不可作级联父项） */
    private static final String TYPE_DETAIL_TABLE = "detailTable";
    /** 栅格列跨度上限（24 栅格制，功能设计 §1.3 grid 行） */
    private static final int GRID_SPAN_MAX = 24;

    /**
     * 对外入口一：校验 Schema 原文（存储态 JSON 字符串）。
     * <ul>
     * <li>原文长度超 {@link SysFormConstants#MAX_SCHEMA_JSON_LENGTH} →
     *     CS_FORM_SCHEMA_LIMIT_EXCEEDED（解析前先查体积，防超大载荷空耗解析）；</li>
     * <li>Jackson 解析失败（非法 JSON / 结构与协议不符）→ CS_FORM_SCHEMA_INVALID + 中文原因；</li>
     * <li>解析成功转 {@link #validate(SysFormSchemaDo)} 做结构校验。</li>
     * </ul>
     *
     * @param schemaJson Schema 原文 JSON 字符串（null / 空白按解析失败处理，必填校验由调用方先行）
     * @return 错误列表（空列表 = 校验通过）
     */
    public List<SysFormSchemaErrorDo> validate(String schemaJson) {
        if (null == schemaJson || schemaJson.isBlank()) {
            return List.of(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    "Schema JSON 为空，无法解析"));
        }
        // 体积校验（§2.2 MAX_SCHEMA_JSON_LENGTH = 512 KB，解析前先查）
        if (schemaJson.length() > SysFormConstants.MAX_SCHEMA_JSON_LENGTH) {
            return List.of(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED,
                    "Schema JSON 体积 " + schemaJson.length() + " 字符，超过上限 "
                            + SysFormConstants.MAX_SCHEMA_JSON_LENGTH + "（512 KB）"));
        }
        SysFormSchemaDo schema;
        try {
            schema = JSON_MAPPER.readValue(schemaJson, SysFormSchemaDo.class);
        } catch (Exception e) {
            log.debug("Schema JSON 解析失败：{}", e.getMessage());
            return List.of(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    "Schema JSON 解析失败：" + e.getMessage()));
        }
        return validate(schema);
    }

    /**
     * 对外入口二：校验 Schema 协议对象（调用方已持有反序列化结果时直走本重载；
     * 体积校验以存储原文为准，本重载不重复做）。
     *
     * @param schema Schema 协议对象（null 按「Schema 为空」处理）
     * @return 错误列表（空列表 = 校验通过）
     */
    public List<SysFormSchemaErrorDo> validate(SysFormSchemaDo schema) {
        if (null == schema) {
            return List.of(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    "Schema 为空，无法校验"));
        }
        List<SysFormSchemaErrorDo> errors = new ArrayList<>();
        if (null == schema.getFields()) {
            errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    "Schema 缺少 fields 字段数组"));
            return errors;
        }
        ValidationContext ctx = new ValidationContext(errors);
        // 第一趟：结构校验 + 字段码索引 + 规则 / 级联待复核项收集
        walkList(ctx, schema.getFields(), ListKind.TOP, 1, ctx.mainScope, 0, "");
        // 第二趟：资源上限与依赖全表索引的复核（规则 triggerField / 动作 target / 级联 parentField）
        if (ctx.fieldCount > SysFormConstants.MAX_FIELD_COUNT) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED,
                    "字段总数 " + ctx.fieldCount + " 超过上限 " + SysFormConstants.MAX_FIELD_COUNT
                            + "（含容器内与明细行内字段）"));
        }
        if (ctx.ruleCount > SysFormConstants.MAX_RULE_COUNT) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED,
                    "规则总数 " + ctx.ruleCount + " 超过上限 " + SysFormConstants.MAX_RULE_COUNT));
        }
        for (PendingRule pending : ctx.pendingRules) {
            validatePendingRule(pending, ctx);
        }
        for (PendingCascade pending : ctx.pendingCascades) {
            validatePendingCascade(pending, ctx);
        }
        return errors;
    }

    /* ==================== 校验上下文 ==================== */

    /** 字段列表形态（决定元素分流：普通字段对象 / 栅格列对象 / 明细行内数据类字段） */
    private enum ListKind {
        /** Schema 顶层 fields（字段对象数组） */
        TOP("fields"),
        /** group 的 children（字段对象数组） */
        GROUP_CHILDREN("children"),
        /** grid 的 children（列对象数组 { span, fields[] }） */
        GRID_COLUMNS("children"),
        /** grid 列对象的 fields（字段对象数组，列仅布局语义） */
        GRID_COLUMN_FIELDS("fields"),
        /** detailTable 的 children（行内字段对象数组，仅数据类组件） */
        DETAIL_ROW("children");

        /** 列表在父对象上的 JSON 键（错误定位用） */
        final String listKey;

        ListKind(String listKey) {
            this.listKey = listKey;
        }
    }

    /** 单次校验的累积上下文：错误清单、数据层级索引、规则 / 级联待复核项与计数器 */
    private static final class ValidationContext {
        final List<SysFormSchemaErrorDo> errors;
        /** 主表数据层级：字段码 → 组件 type（顶层 + group/grid 容器内字段——布局容器透明） */
        final Map<String, String> mainScope = new LinkedHashMap<>();
        /** 明细行数据层级：detailTable 字段码 → 行内字段码 → 组件 type（行上下文隔离，§2.1） */
        final Map<String, Map<String, String>> rowScopes = new LinkedHashMap<>();
        /** 待复核规则（triggerField / 动作 target 的存在性依赖全表索引） */
        final List<PendingRule> pendingRules = new ArrayList<>();
        /** 待复核级联（parentField 存在性 / 数据类判定依赖全表索引） */
        final List<PendingCascade> pendingCascades = new ArrayList<>();
        /** 规则 ID 全表查重（§3.1：表单内唯一） */
        final Set<String> ruleIds = new HashSet<>();
        /** 字段总数（含容器内与明细行内字段；栅格列对象为布局载体不计） */
        int fieldCount = 0;
        /** 规则总数（全表所有字段 rules 求和） */
        int ruleCount = 0;

        ValidationContext(List<SysFormSchemaErrorDo> errors) {
            this.errors = errors;
        }
    }

    /** 待复核规则：宿主字段 + 规则 + 触发数据层级（§3.1：主表规则作用于主表字段，明细行规则注入行上下文） */
    private static final class PendingRule {
        /** 宿主字段码（triggerField 必须与之相等） */
        final String hostField;
        /** 宿主组件 type（明细表宿主的规则 target 限其行内字段） */
        final String hostType;
        final SysFormRuleDo rule;
        /** 规则呈现路径（错误定位） */
        final String where;
        /** 触发字段所在数据层级（宿主自身层级，遍历期即确定的稳定引用） */
        final Map<String, String> triggerScope;

        PendingRule(String hostField, String hostType, SysFormRuleDo rule, String where,
                    Map<String, String> triggerScope) {
            this.hostField = hostField;
            this.hostType = hostType;
            this.rule = rule;
            this.where = where;
            this.triggerScope = triggerScope;
        }
    }

    /** 待复核级联：宿主字段 + 宿主所在数据层级（级联父项与宿主同数据层级，R08 第 4 条） */
    private static final class PendingCascade {
        final SysFormFieldDo host;
        /** 呈现路径（错误定位） */
        final String where;
        /** 宿主所在数据层级 */
        final Map<String, String> scope;

        PendingCascade(SysFormFieldDo host, String where, Map<String, String> scope) {
            this.host = host;
            this.where = where;
            this.scope = scope;
        }
    }

    /* ==================== 第一趟：字段树遍历 ==================== */

    /**
     * 遍历校验一个字段列表。
     *
     * @param ctx        校验上下文
     * @param fields     字段对象列表（可空——空容器 / 空白草稿合法）
     * @param kind       列表形态（决定元素分流与 JSON 键名）
     * @param depth      当前嵌套深度（顶层字段 = 1，容器内字段 = 容器链深 + 1，栅格列不计深度）
     * @param scope      本列表字段所属数据层级（主表层级或明细行层级，字段码查重即在此层完成）
     * @param chain      group/grid 容器祖先数（相互嵌套最多一层 = 容器链最多两个容器）
     * @param parentPath 父字段呈现路径（根列表为空串，错误定位用）
     */
    private void walkList(ValidationContext ctx, List<SysFormFieldDo> fields, ListKind kind,
                          int depth, Map<String, String> scope, int chain, String parentPath) {
        if (null == fields) {
            return;
        }
        // 嵌套深度（§2.2 MAX_NEST_DEPTH，顶层字段 = 1）：超限不再下钻，避免错误雪崩
        if (depth > SysFormConstants.MAX_NEST_DEPTH) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED,
                    "字段嵌套深度 " + depth + " 超过上限 " + SysFormConstants.MAX_NEST_DEPTH
                            + "（顶层字段 = 1，容器内字段 = 容器链深 + 1）"));
            return;
        }
        for (int i = 0; i < fields.size(); i++) {
            SysFormFieldDo field = fields.get(i);
            if (null == field) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        elementPath(parentPath, kind.listKey, i, null) + " 字段对象为 null"));
                continue;
            }
            String path = elementPath(parentPath, kind.listKey, i, field.getField());
            switch (kind) {
                // grid 的 children 必须为列对象 { span, fields[] }，列内字段再递归
                case GRID_COLUMNS -> walkGridColumn(ctx, field, path, depth, scope, chain);
                // 其余列表元素必须为字段对象；栅格列对象只允许出现在 grid 的 children 中
                case TOP, GROUP_CHILDREN, GRID_COLUMN_FIELDS -> {
                    if (field.isGridColumn()) {
                        ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                                path + " 栅格列对象仅允许出现在 type=grid 的 children 中"));
                    } else {
                        walkField(ctx, field, path, depth, scope, chain, ListKind.DETAIL_ROW == kind);
                    }
                }
                // detailTable 的 children：行内字段对象（仅数据类组件，R04），走字段校验主流程
                case DETAIL_ROW -> walkField(ctx, field, path, depth, scope, chain, true);
            }
        }
    }

    /** 元素呈现路径（错误定位）：根列表 fields[0]、容器内 fields[2].children[0] 等 */
    private String elementPath(String parentPath, String listKey, int index, String code) {
        String path = (parentPath.isEmpty() ? "" : parentPath + ".") + listKey + "[" + index + "]";
        return nonBlank(code) ? path + " (" + code + ")" : path;
    }

    /** grid 的 children 列对象校验：列形态纯净性 + span 合法性 + 列内字段递归 */
    private void walkGridColumn(ValidationContext ctx, SysFormFieldDo column, String path,
                                int depth, Map<String, String> scope, int chain) {
        if (!column.isGridColumn()) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    path + " grid 的 children 须为列对象数组 { span, fields[] }（功能设计 §1.3）"));
            return;
        }
        // 列对象仅承载 span 与 fields（{ span, fields[] } 定稿形态），携带字段码 / 类型 / 名称即形态违规
        if (nonBlank(column.getField()) || nonBlank(column.getType()) || nonBlank(column.getLabel())) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    path + " 栅格列对象仅承载 span 与 fields，不应携带 field/type/label"));
        }
        Integer span = column.getSpan();
        if (null == span || span < 1 || span > GRID_SPAN_MAX) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    path + " 栅格列 span=" + span + " 非法（须为 1~" + GRID_SPAN_MAX
                            + " 的整数，24 栅格制）"));
        }
        if (null == column.getFields()) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    path + " 栅格列对象缺少 fields 字段数组"));
            return;
        }
        // 列仅布局语义：列内字段与宿主栅格同处一个数据层级（栅格列对象为布局载体，
        // 深度与容器链均已由 GRID_COLUMNS 列表层完成 +1，此处不再累加）
        walkList(ctx, column.getFields(), ListKind.GRID_COLUMN_FIELDS, depth, scope, chain, path);
    }

    /**
     * 单个字段对象校验（结构 + props + 规则登记 + children 递归）。
     *
     * @param inDetailRow 是否处于明细表行内（R04：行内仅数据类组件）
     */
    private void walkField(ValidationContext ctx, SysFormFieldDo field, String path, int depth,
                           Map<String, String> scope, int chain, boolean inDetailRow) {
        String type = field.getType();
        String code = field.getField();

        // type 白名单（ADR-10）：REJECTED_TYPES 命中给专门文案，其余白名单外 type 一律拒绝
        if (!nonBlank(type)) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    path + " 缺少组件 type", code));
            return;
        }
        if (SysFormConstants.REJECTED_TYPES.contains(type)) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    path + " 组件 type=" + type + " " + rejectedReason(type), code));
            return;
        }
        if (inDetailRow) {
            // R04：明细表行内仅数据类组件（ALLOWED_DETAIL_CHILD_TYPES = DATA_FIELD_TYPES，
            // 含行内 formula），容器组件与明细表一律拒绝
            if (!SysFormConstants.ALLOWED_DETAIL_CHILD_TYPES.contains(type)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        path + " 明细表行内仅允许数据类组件（R04），type=" + type
                                + " 不允许（禁容器组件与明细表嵌套）", code));
                return;
            }
        } else if (!SysFormConstants.ALLOWED_FIELD_TYPES.contains(type)) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    path + " 组件 type=" + type + " 未注册（不在一期组件 type 白名单内，"
                            + "一期全集见功能设计 §1.3 共 18 个）", code));
            return;
        }

        // group/grid 相互嵌套最多一层（§2.1 容器链）：已有两个容器祖先时拒绝第三个
        boolean container = "group".equals(type) || "grid".equals(type);
        if (container && chain >= 2) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    path + " group/grid 相互嵌套最多一层（容器链最多两个容器，功能设计 §2.1）", code));
            return;
        }
        ctx.fieldCount++;

        // label 非空（分组即区块标题，全部字段对象必填）
        if (!nonBlank(field.getLabel())) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    path + " label 不能为空", code));
        }

        // 字段码：正则 + 同一数据层级唯一（§2.1；group/grid/table/text/image 无 ID 不承载）
        if (needsFieldCode(type)) {
            if (!nonBlank(code)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        path + " 数据类组件缺少字段码 field", code));
            } else if (!FIELD_CODE.matcher(code).matches()) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        path + " 字段码 " + code + " 不合法（须符合 " + SysFormConstants.FIELD_CODE_REGEX
                                + "）", code));
            } else if (null != scope.putIfAbsent(code, type)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        path + " 字段码 " + code + " 在同一数据层级内重复（布局容器不产生新层级，"
                                + "明细行内为独立行上下文）", code));
            }
        } else if (nonBlank(code)) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    path + " " + type + " 组件不承载数据，不应配置字段码 field（功能设计 §1.1）", code));
        }

        validateProps(ctx, field, path, scope);
        collectRules(ctx, field, path, scope);

        // children 分流递归（空 children 合法：空分组 / 空栅格 / 空白明细）
        List<SysFormFieldDo> children = field.getChildren();
        if ("group".equals(type)) {
            walkList(ctx, children, ListKind.GROUP_CHILDREN, depth + 1, scope, chain + 1, path);
        } else if ("grid".equals(type)) {
            walkList(ctx, children, ListKind.GRID_COLUMNS, depth + 1, scope, chain + 1, path);
        } else if (TYPE_DETAIL_TABLE.equals(type)) {
            // 行上下文隔离（§2.1）：行内字段码独立成层级（可与主表同码，行内必须唯一）
            Map<String, String> rowScope = ctx.rowScopes.computeIfAbsent(code, k -> new LinkedHashMap<>());
            walkList(ctx, children, ListKind.DETAIL_ROW, depth + 1, rowScope, chain, path);
        } else if (null != children && !children.isEmpty()) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    path + " 非容器组件不应配置 children（仅 group/grid/detailTable 持有）", code));
        }
    }

    /** 数据类 12 组件与 detailTable 需要字段码（§1.1 固定属性项按类别裁剪） */
    private boolean needsFieldCode(String type) {
        return SysFormConstants.DATA_FIELD_TYPES.contains(type) || TYPE_DETAIL_TABLE.equals(type);
    }

    /** REJECTED_TYPES 拒绝文案（R02 / R03 / §1.3 二期组件 / §6.6 权限协议位） */
    private String rejectedReason(String type) {
        return switch (type) {
            case "code" -> "为代码块组件，R02 裁决一期仅预留协议位、不落地执行引擎，不允许出现在 Schema 中";
            case "permission" -> "为权限组件，仅登记协议位不进 Schema fields（字段级权限由 props.permission 承载，运行时随 v1.8 BPM 消费）";
            default -> "为二期组件，一期未注册，不允许出现在 Schema 中";
        };
    }

    /* ==================== props 校验 ==================== */

    /**
     * props 约定项校验：permission 取值域、文本 maxlength、明细 rowMin/rowMax、级联显隐条件。
     *
     * @param scope 宿主字段所在数据层级（级联 parentField 待复核按此层级判定，R08 第 4 条）
     */
    private void validateProps(ValidationContext ctx, SysFormFieldDo field, String path,
                               Map<String, String> scope) {
        Map<String, Object> props = field.getProps();
        if (null == props) {
            return;
        }
        String type = field.getType();
        String code = field.getField();

        // 字段级权限元数据取值域（SysFieldPermission：editable/readonly/hidden）
        Object permission = props.get("permission");
        if (null != permission && null == SysFieldPermission.of(String.valueOf(permission))) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    path + " props.permission=" + permission + " 非法（取值域 editable/readonly/hidden）",
                    code));
        }

        // 文本类 maxlength（input/textarea）：正整数且 ≤ MAX_TEXT_LENGTH
        if ("input".equals(type) || "textarea".equals(type)) {
            Object maxlength = props.get("maxlength");
            if (null != maxlength) {
                if (!(maxlength instanceof Number number)) {
                    ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                            path + " props.maxlength 须为数值", code));
                } else if (number.intValue() <= 0) {
                    ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                            path + " props.maxlength=" + number.intValue() + " 须为正整数", code));
                } else if (number.intValue() > SysFormConstants.MAX_TEXT_LENGTH) {
                    ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED,
                            path + " props.maxlength=" + number.intValue() + " 超过文本长度上限 "
                                    + SysFormConstants.MAX_TEXT_LENGTH, code));
                }
            }
        }

        // 明细表行数：rowMax ≤ MAX_DETAIL_ROWS 且 rowMin ≤ rowMax（必填语义 = 至少一行按 rowMin）
        if (TYPE_DETAIL_TABLE.equals(type)) {
            Object rowMin = props.get("rowMin");
            Object rowMax = props.get("rowMax");
            if (null != rowMax && !(rowMax instanceof Number)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        path + " props.rowMax 须为数值", code));
            } else if (rowMax instanceof Number maxNumber) {
                if (maxNumber.intValue() <= 0) {
                    ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                            path + " props.rowMax=" + maxNumber.intValue() + " 须为正整数", code));
                } else if (maxNumber.intValue() > SysFormConstants.MAX_DETAIL_ROWS) {
                    ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED,
                            path + " props.rowMax=" + maxNumber.intValue() + " 超过明细行数上限 "
                                    + SysFormConstants.MAX_DETAIL_ROWS, code));
                }
            }
            if (null != rowMin && !(rowMin instanceof Number)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        path + " props.rowMin 须为数值", code));
            } else if (rowMin instanceof Number minNumber && minNumber.intValue() < 0) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        path + " props.rowMin=" + minNumber.intValue() + " 不可为负数", code));
            }
            if (rowMin instanceof Number minNumber && rowMax instanceof Number maxNumber
                    && minNumber.intValue() > maxNumber.intValue()) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        path + " props.rowMin=" + minNumber.intValue() + " 大于 rowMax="
                                + maxNumber.intValue(), code));
            }
        }

        // 级联选择器（R08 第 4 条设计期校验）：选项显隐条件即时校验，parentField 存在性 /
        // 数据类判定依赖全表索引，登记待复核
        if ("cascade".equals(type)) {
            validateCascadeOptions(ctx, field, path);
            ctx.pendingCascades.add(new PendingCascade(field, path, scope));
        }
    }

    /** 级联 options[].showCondition 的 operator/value 合法性（R08 第 4 条） */
    private void validateCascadeOptions(ValidationContext ctx, SysFormFieldDo field, String path) {
        Object options = field.getProps().get("options");
        if (!(options instanceof List<?> optionList)) {
            return;
        }
        int index = 0;
        for (Object option : optionList) {
            index++;
            if (!(option instanceof Map<?, ?> optionMap)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        path + " options[#" + index + "] 须为选项对象 { label, value, showCondition? }",
                        field.getField()));
                continue;
            }
            String optionLabel = String.valueOf(optionMap.get("label"));
            String where = path + " options[" + (nonBlank(optionLabel) ? optionLabel : "#" + index) + "]";
            Object showCondition = optionMap.get("showCondition");
            if (null == showCondition) {
                continue;
            }
            SysFormRuleConditionDo condition;
            try {
                condition = JSON_MAPPER.convertValue(showCondition, SysFormRuleConditionDo.class);
            } catch (IllegalArgumentException e) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        where + ".showCondition 必须为条件对象 { operator, value }（R08：一期选项"
                                + "显隐条件采用规则 DSL 条件形态）", field.getField()));
                continue;
            }
            validateCondition(ctx, condition, where + ".showCondition", field.getField());
        }
    }

    /* ==================== 规则校验 ==================== */

    /** 单字段规则登记与结构校验（ruleId 唯一 / event / condition / 动作要素；存在性复核登记待办） */
    private void collectRules(ValidationContext ctx, SysFormFieldDo field, String path,
                              Map<String, String> scope) {
        List<SysFormRuleDo> rules = field.getRules();
        if (null == rules || rules.isEmpty()) {
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            SysFormRuleDo rule = rules.get(i);
            String where = path + " rules[" + i + "]";
            ctx.ruleCount++;
            if (null == rule) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        where + " 规则为 null", field.getField()));
                continue;
            }
            // ruleId 表单内唯一（§3.1）
            String ruleId = rule.getRuleId();
            if (!nonBlank(ruleId)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        where + " 缺少规则 ID ruleId", field.getField()));
            } else if (!ctx.ruleIds.add(ruleId)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        where + " 规则 ID " + ruleId + " 在表单内重复", ruleId));
            }
            // event ∈ 八事件全集（§3.4）
            SysFormEvent event = SysFormEvent.of(rule.getEvent());
            if (null == event) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        where + " event=" + rule.getEvent() + " 非法（事件全集 onLoad/onChange/"
                                + "onFocus/onBlur/onAdd/onDelete/onSubmit/onValidate）", ruleId));
            } else if (!event.isRuleConfigurable()) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        where + " event=" + event.getCode() + " 一期不开放声明式规则配置"
                                + "（仅事件透传给物料组件，防规则风暴，功能设计 §3.4）", ruleId));
            }
            // condition：operator ∈ 全集 10 个 + value 形态随 operator（§3.2）
            validateCondition(ctx, rule.getCondition(), where + ".condition", ruleId);
            // 动作要素（target 存在性依赖全表索引，登记待复核）
            validateActionShapes(ctx, rule, event, where);
            ctx.pendingRules.add(new PendingRule(field.getField(), field.getType(), rule, where, scope));
        }
    }

    /** 动作结构校验：type ∈ 全集 6 个、载荷形态、提交阶段动作约束（§3.3 / §3.4） */
    private void validateActionShapes(ValidationContext ctx, SysFormRuleDo rule, SysFormEvent event,
                                      String where) {
        List<SysFormRuleActionDo> actions = rule.getActions();
        if (null == actions || actions.isEmpty()) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    where + " actions 不能为空（动作数组按序执行）", rule.getRuleId()));
            return;
        }
        for (int i = 0; i < actions.size(); i++) {
            SysFormRuleActionDo action = actions.get(i);
            String actionWhere = where + ".actions[" + i + "]";
            if (null == action) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        actionWhere + " 动作为 null", rule.getRuleId()));
                continue;
            }
            String type = action.getType();
            boolean legal = nonBlank(type) && (TARGET_ONLY_ACTIONS.contains(type)
                    || TARGET_VALUE_ACTIONS.contains(type) || ACTION_SET_VALIDATE.equals(type));
            if (!legal) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        actionWhere + " 动作 type=" + type + " 非法（全集 6 个：show/hide/setValue/"
                                + "clearValue/setRequired/setValidate；一期不开放 script 类动作）",
                        rule.getRuleId()));
                continue;
            }
            // onSubmit / onValidate 禁 setValue / clearValue（§3.4：仅 setRequired/setValidate/show/hide）
            if (null != event && event.isSubmitStage() && SUBMIT_FORBIDDEN_ACTIONS.contains(type)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        actionWhere + " onSubmit/onValidate 事件禁止配置 " + type + "（仅支持 "
                                + "setRequired/setValidate/show/hide，防提交竞态）", rule.getRuleId()));
            }
            if (!nonBlank(action.getTarget())) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        actionWhere + " 动作缺少目标字段 target", rule.getRuleId()));
            }
            if (TARGET_VALUE_ACTIONS.contains(type) && null == action.getValue()) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        actionWhere + " 动作 " + type + " 缺少 value 载荷", rule.getRuleId()));
            }
            if (ACTION_SET_REQUIRED.equals(type) && null != action.getValue()
                    && !(action.getValue() instanceof Boolean)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        actionWhere + " 动作 setRequired 的 value 必须为布尔", rule.getRuleId()));
            }
            if (ACTION_SET_VALIDATE.equals(type)) {
                validateSetValidatePayload(ctx, action, actionWhere, rule.getRuleId());
            }
        }
    }

    /** setValidate 载荷校验：validate 对象必填、四键至少一项、pattern 可编译（§3.3 覆盖语义） */
    private void validateSetValidatePayload(ValidationContext ctx, SysFormRuleActionDo action,
                                            String actionWhere, String ruleId) {
        SysFormRuleValidateDo validate = action.getValidate();
        if (null == validate) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    actionWhere + " 动作 setValidate 缺少 validate 载荷（min/max/pattern/message 至少一项）",
                    ruleId));
            return;
        }
        if (null == validate.getMin() && null == validate.getMax()
                && !nonBlank(validate.getPattern()) && !nonBlank(validate.getMessage())) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    actionWhere + " 动作 setValidate 的 validate 载荷 min/max/pattern/message 至少一项",
                    ruleId));
        }
        if (nonBlank(validate.getPattern())) {
            try {
                Pattern.compile(validate.getPattern());
            } catch (PatternSyntaxException e) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        actionWhere + " 动作 setValidate 的 pattern 正则非法：" + e.getDescription(),
                        ruleId));
            }
        }
    }

    /** 条件校验：operator ∈ 全集 10 个、value 形态随 operator（§3.2；规则 condition 与级联 showCondition 共用） */
    private void validateCondition(ValidationContext ctx, SysFormRuleConditionDo condition,
                                   String where, String refCode) {
        if (null == condition) {
            return;
        }
        String operator = condition.getOperator();
        if (!nonBlank(operator)) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    where + " 缺少 operator", refCode));
            return;
        }
        Object value = condition.getValue();
        boolean hasValue = null != value;
        if (SCALAR_OPERATORS.contains(operator)) {
            // equal/notEqual：value 为标量（字符串 / 数值 / 布尔）
            if (!hasValue) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        where + " operator=" + operator + " 的 value 不能为空（标量比较）", refCode));
            } else if (value instanceof List<?> || value instanceof Map<?, ?>) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        where + " operator=" + operator + " 的 value 必须为标量", refCode));
            }
        } else if (ORDER_OPERATORS.contains(operator)) {
            // gt/gte/lt/lte：value 为数值或日期字符串（date 按 same-format 字符串字典序比较）
            if (!hasValue) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        where + " operator=" + operator + " 的 value 不能为空（数值或日期字符串）", refCode));
            } else if (!(value instanceof Number) && !(value instanceof String)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        where + " operator=" + operator + " 的 value 必须为数值或日期字符串", refCode));
            }
        } else if (COLLECTION_OPERATORS.contains(operator)) {
            // in/notIn：value 必须为数组
            if (!(value instanceof List<?>)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        where + " operator=" + operator + " 的 value 必须为数组", refCode));
            }
        } else if (EMPTY_OPERATORS.contains(operator)) {
            // empty/notEmpty：value 省略
            if (hasValue) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        where + " operator=" + operator + " 不应配置 value（空判类省略 value）", refCode));
            }
        } else {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    where + " operator=" + operator + " 非法（全集 10 个：equal/notEqual/gt/gte/"
                            + "lt/lte/in/notIn/empty/notEmpty）", refCode));
        }
    }

    /* ==================== 第二趟：依赖全表索引的复核 ==================== */

    /**
     * 待复核规则复核（§3.1 / §3.2）：triggerField 等于宿主字段码、存在且为数据类字段
     * （含明细表容器——onAdd/onDelete 行事件宿主）；动作 target 存在于目标数据层级
     * （明细表宿主的规则 target 限其行内字段，其余取宿主所在层级）。
     */
    private void validatePendingRule(PendingRule pending, ValidationContext ctx) {
        SysFormRuleDo rule = pending.rule;
        // triggerField（§3.1：= 宿主字段对象 field；服务端平铺索引求值不依赖嵌套位置）
        String trigger = rule.getTriggerField();
        if (!nonBlank(trigger)) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    pending.where + " 缺少触发字段 triggerField", rule.getRuleId()));
        } else if (!trigger.equals(pending.hostField)) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    pending.where + " triggerField=" + trigger + " 必须等于宿主字段码 "
                            + pending.hostField + "（规则随触发字段存放）", rule.getRuleId()));
        } else if (!pending.triggerScope.containsKey(trigger)) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    pending.where + " triggerField=" + trigger + " 引用的字段不存在", rule.getRuleId()));
        } else {
            String triggerType = pending.triggerScope.get(trigger);
            if (!SysFormConstants.DATA_FIELD_TYPES.contains(triggerType)
                    && !TYPE_DETAIL_TABLE.equals(triggerType)) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        pending.where + " triggerField=" + trigger + " 必须为已配置的数据类字段"
                                + "（明细容器事件为其字段码）", rule.getRuleId()));
            }
        }
        // 动作 target 存在（明细行规则 target 为行内字段码，§3.1 行上下文注入）
        Map<String, String> targetScope = TYPE_DETAIL_TABLE.equals(pending.hostType)
                ? ctx.rowScopes.get(pending.hostField) : pending.triggerScope;
        if (null == targetScope) {
            return;
        }
        List<SysFormRuleActionDo> actions = rule.getActions();
        if (null == actions) {
            return;
        }
        for (int i = 0; i < actions.size(); i++) {
            SysFormRuleActionDo action = actions.get(i);
            if (null == action || !nonBlank(action.getTarget())) {
                continue;
            }
            if (!targetScope.containsKey(action.getTarget())) {
                ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                        pending.where + ".actions[" + i + "] 动作目标字段 " + action.getTarget()
                                + " 不存在（须为宿主数据层级内已配置字段）", rule.getRuleId()));
            }
        }
    }

    /**
     * 待复核级联复核（R08 第 4 条）：parentField 存在时必须为已配置的数据类字段
     * （DATA_FIELD_TYPES，不含明细表容器）且不可引用自身。
     */
    private void validatePendingCascade(PendingCascade pending, ValidationContext ctx) {
        Map<String, Object> props = pending.host.getProps();
        Object parentField = null == props ? null : props.get("parentField");
        if (null == parentField || String.valueOf(parentField).isBlank()) {
            // 一期按「存在即校验」口径：未配置 parentField 不强制必填（R08 第 4 条为条件校验）
            return;
        }
        String parent = String.valueOf(parentField);
        String where = pending.where + " props.parentField=" + parent;
        if (parent.equals(pending.host.getField())) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    where + " 不可引用自身（R08）", pending.host.getField()));
        } else if (!pending.scope.containsKey(parent)) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    where + " 引用的字段不存在（限定已配置的数据类字段，R08）", pending.host.getField()));
        } else if (!SysFormConstants.DATA_FIELD_TYPES.contains(pending.scope.get(parent))) {
            ctx.errors.add(new SysFormSchemaErrorDo(SysFormConstants.CS_FORM_SCHEMA_INVALID,
                    where + " 必须为已配置的数据类字段（R08，不含明细表容器）", pending.host.getField()));
        }
    }

    /* ==================== 工具 ==================== */

    /** 空白判断（null / 空白串视为无值） */
    private boolean nonBlank(String value) {
        return null != value && !value.isBlank();
    }
}

package com.eric.base.sys.form.service.engine;

import com.eric.base.sys.form.model.SysFormDataErrorDo;
import com.eric.base.sys.form.model.SysFormFieldDo;
import com.eric.base.sys.form.model.SysFormSchemaDo;
import com.eric.base.sys.form.properties.SysFormConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_UNKNOWN_FIELD;

/**
 * 表单数据基础校验器（F15-BE-003，功能设计 §6.1 保存校验链 S1/S2、API 分册 §4.3）
 *
 * @author Eric
 * @date 2026/11/24
 * @description 数据面保存校验链的基础段（落位 service/engine 次级包，先例
 *              {@link FormSchemaValidator}），按版本 Schema 对提交载荷执行：
 *              <ul>
 *              <li><b>S1 字段白名单校验</b>（分册 §7.4 定稿①）：formData 键（任意层级，
 *                  含明细行内键）必须 ⊆ 版本 Schema 字段码白名单，未知字段<b>拒绝</b>抛
 *                  {@code CS_FORM_UNKNOWN_FIELD}（不做静默裁剪——服务端不信任前端，
 *                  裁剪会掩盖 Schema 与数据的漂移）；</li>
 *              <li><b>S2 基础校验</b>（分册 §7.1 第 12 项）：必填 / 类型 / 长度 /
 *                  枚举选项值 / 明细行结构与行数上限（{@code MAX_DETAIL_ROWS}），逐条产出
 *                  {@link SysFormDataErrorDo} 字段级错误（ruleId 恒 null——动态校验错误由
 *                  F15-BE-004 FormValidationEngine 在同一入口并联回填）。</li>
 *              </ul>
 *              类型域（按组件 value 语义收口，与功能设计 §2.3 示例载荷对齐）：
 *              input/textarea/date → 文本；number → 数值；switch → 布尔；
 *              choice/select/cascade/datasource/addressbook → multi 时数组、否则标量
 *              （元素为字符串 / 数值 / 布尔）；attachment → 附件引用对象数组；
 *              detailTable → 明细行对象数组（行内字段按行上下文独立校验）。
 *              formula 字段值不参与校验（提交载荷中公式字段值不参与校验，服务端重算值落库
 *              ADR-09——重算随 F15-BE-005 并联，本任务透传）。
 *              <p><b>扩展点</b>：本类与 F15-BE-004 动态校验 / 权限裁剪（S3/S4/S7）、
 *              F15-BE-005 公式重算与级联复核（S5/S6）在同一保存校验链入口并联——
 *              {@link #validate(SysFormSchemaDo, Map)} 的 errors 由 S2 与 S4 合并、
 *              {@link #normalize(SysFormSchemaDo, Map)} 产物为 S3/S5/S6/S7 的加工对象。</p>
 *              明细行数口径：props.rowMin 为行数下限（必填语义 = 至少一行按 rowMin，
 *              FormSchemaValidator 同口径）；props.rowMax 为上限、缺省取
 *              {@link SysFormConstants#MAX_DETAIL_ROWS} 绝对上限（定义期结构校验已保证
 *              rowMax ≤ MAX_DETAIL_ROWS）。
 */
@Component
public class FormDataBasicValidator {

    private static final Logger log = LoggerFactory.getLogger(FormDataBasicValidator.class);

    /** 明细表组件 type（行上下文宿主） */
    private static final String TYPE_DETAIL_TABLE = "detailTable";
    /** 公式组件 type（提交值不参与校验，服务端重算 ADR-09） */
    private static final String TYPE_FORMULA = "formula";

    /** multi 承载数组的组件 type（单/多选语义，props.multi 为 true 时值须为数组） */
    private static final Set<String> MULTI_CAPABLE_TYPES = Set.of(
            "choice", "select", "cascade", "datasource", "addressbook");

    /**
     * 对外入口一：基础校验（S1 白名单拒绝 + S2 字段级校验）。
     *
     * @param schema   实例锁定版本 Schema 协议对象（null 按无字段 Schema 处理——任意
     *                 键均判未知字段，空白载荷 {@code {}} 通过）
     * @param formData 提交载荷 { 字段码: 值 }（null 按空载荷处理，必填由调用方先行）
     * @return 字段级错误列表（空列表 = 校验通过）；含未知字段时直接抛
     *         {@code CS_FORM_UNKNOWN_FIELD}（确定性拒绝，分册 §7.4①）
     */
    public List<SysFormDataErrorDo> validate(SysFormSchemaDo schema, Map<String, Object> formData) {
        DataScopes scopes = buildScopes(schema);
        assertWhitelist(scopes, formData);
        List<SysFormDataErrorDo> errors = new ArrayList<>();
        validateMainScope(scopes, formData, errors);
        return errors;
    }

    /**
     * 对外入口二：载荷归一（明细行数据结构归一 + Schema 字段序规范），产出落库形态：
     * 顶层键按 Schema 字段序重排（LinkedHashMap）、明细表行按行上下文行内字段序重排为
     * 扁平行对象数组——存储态 JSON 结构确定化（先例全量替换归一，分册 §2.4 载荷归一口径）。
     * 须在 {@link #validate(SysFormSchemaDo, Map)} 通过后调用（结构已保证合法，未保证的
     * 形态原样透传，防御性不抛错）。
     *
     * @param schema   实例锁定版本 Schema 协议对象
     * @param formData 校验通过的提交载荷
     * @return 归一化载荷（Schema 字段序顶层键 + 行内字段序明细行对象）
     */
    public Map<String, Object> normalize(SysFormSchemaDo schema, Map<String, Object> formData) {
        DataScopes scopes = buildScopes(schema);
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (null == formData) {
            return normalized;
        }
        for (Map.Entry<String, SysFormFieldDo> entry : scopes.mainScope.entrySet()) {
            String code = entry.getKey();
            if (!formData.containsKey(code)) {
                continue;
            }
            Object value = formData.get(code);
            if (TYPE_DETAIL_TABLE.equals(entry.getValue().getType())) {
                value = normalizeRows(scopes.rowScopes.get(code), value);
            }
            normalized.put(code, value);
        }
        return normalized;
    }

    /** 明细行归一：行内键按行上下文字段序重排（行数组结构保持提交顺序） */
    private List<Object> normalizeRows(Map<String, SysFormFieldDo> rowScope, Object value) {
        if (!(value instanceof List<?> rows)) {
            return new ArrayList<>();
        }
        List<Object> normalizedRows = new ArrayList<>(rows.size());
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> rowMap)) {
                normalizedRows.add(row);
                continue;
            }
            Map<String, Object> normalizedRow = new LinkedHashMap<>();
            if (null != rowScope) {
                for (String rowCode : rowScope.keySet()) {
                    if (rowMap.containsKey(rowCode)) {
                        normalizedRow.put(rowCode, rowMap.get(rowCode));
                    }
                }
            }
            normalizedRows.add(normalizedRow);
        }
        return normalizedRows;
    }

    /* ==================== 数据层级索引 ==================== */

    /** 数据层级索引：主表层级（顶层 + group/grid 容器内——布局容器透明）与明细行层级 */
    private static final class DataScopes {
        /** 主表层级：字段码 → 字段对象（数据类 12 组件 + detailTable 宿主） */
        final Map<String, SysFormFieldDo> mainScope = new LinkedHashMap<>();
        /** 明细行层级：detailTable 字段码 → 行内字段码 → 字段对象（行上下文隔离） */
        final Map<String, Map<String, SysFormFieldDo>> rowScopes = new LinkedHashMap<>();
    }

    /**
     * 构建数据层级索引（字段树遍历，形态对齐 FormSchemaValidator 的 scope 口径）：
     * group/grid 布局容器不产生新数据层级（children 与宿主同层级，栅格列对象为布局载体）；
     * detailTable 的 children 为独立行上下文（行内字段码可与主表同码，行内必须唯一）。
     */
    private DataScopes buildScopes(SysFormSchemaDo schema) {
        DataScopes scopes = new DataScopes();
        if (null == schema) {
            return scopes;
        }
        collectFields(schema.getFields(), scopes);
        return scopes;
    }

    /** 递归收集一个字段列表（主表层级；布局容器透明下钻、明细表宿主登记行上下文） */
    private void collectFields(List<SysFormFieldDo> fields, DataScopes scopes) {
        if (null == fields) {
            return;
        }
        for (SysFormFieldDo field : fields) {
            if (null == field) {
                continue;
            }
            // 栅格列对象仅承载 span/fields：列内字段与宿主栅格同处一个数据层级
            if (field.isGridColumn()) {
                collectFields(field.getFields(), scopes);
                continue;
            }
            String type = field.getType();
            String code = field.getField();
            if ("group".equals(type) || "grid".equals(type)) {
                // 布局容器不产生新数据层级（R04/§2.1：容器内字段与宿主同层级）
                collectFields(field.getChildren(), scopes);
                continue;
            }
            if (TYPE_DETAIL_TABLE.equals(type) && nonBlank(code)) {
                scopes.mainScope.put(code, field);
                Map<String, SysFormFieldDo> rowScope = new LinkedHashMap<>();
                collectRowFields(field.getChildren(), rowScope);
                scopes.rowScopes.put(code, rowScope);
                continue;
            }
            if (nonBlank(code) && SysFormConstants.DATA_FIELD_TYPES.contains(type)
                    && !scopes.mainScope.containsKey(code)) {
                scopes.mainScope.put(code, field);
            }
        }
    }

    /** 递归收集明细行内字段（R04：行内仅数据类组件，定义期结构校验已保证） */
    private void collectRowFields(List<SysFormFieldDo> children, Map<String, SysFormFieldDo> rowScope) {
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

    /* ==================== S1 字段白名单校验（未知字段拒绝，分册 §7.4①） ==================== */

    /**
     * 字段白名单校验：formData 键（主表层级 + 明细行内键，任意层级）必须 ⊆ 版本 Schema
     * 字段码白名单，未知字段<b>拒绝</b>抛 {@code CS_FORM_UNKNOWN_FIELD}（不做静默裁剪）。
     */
    private void assertWhitelist(DataScopes scopes, Map<String, Object> formData) {
        if (null == formData) {
            return;
        }
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            SysFormFieldDo field = scopes.mainScope.get(entry.getKey());
            if (null == field) {
                throw unknownFieldError("提交数据包含字段白名单外未知字段（拒绝不做静默裁剪）: "
                        + entry.getKey());
            }
            if (TYPE_DETAIL_TABLE.equals(field.getType())) {
                assertRowWhitelist(entry.getKey(), entry.getValue(),
                        scopes.rowScopes.get(entry.getKey()));
            }
        }
    }

    /** 明细行内白名单校验：行对象键 ⊆ 行上下文字段码（行内未知键同样拒绝） */
    private void assertRowWhitelist(String detailCode, Object value, Map<String, SysFormFieldDo> rowScope) {
        if (!(value instanceof List<?> rows)) {
            return; // 行数组结构违规由 S2 承载（此处无法定位行内键）
        }
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (!(row instanceof Map<?, ?> rowMap)) {
                continue; // 行对象结构违规由 S2 承载
            }
            for (Object key : rowMap.keySet()) {
                if (null == rowScope || !rowScope.containsKey(String.valueOf(key))) {
                    throw unknownFieldError("明细表「" + detailCode + "」第 " + (i + 1)
                            + " 行包含行内字段白名单外未知字段（拒绝不做静默裁剪）: " + key);
                }
            }
        }
    }

    /** CS_FORM_UNKNOWN_FIELD 确定性拒绝异常（S1，任意层级未知字段） */
    private static IllegalArgumentException unknownFieldError(String detail) {
        log.debug("form data unknown field rejected: {}", detail);
        return new IllegalArgumentException(CS_FORM_UNKNOWN_FIELD + ": " + detail);
    }

    /* ==================== S2 基础校验（必填/类型/长度/枚举/明细行） ==================== */

    /** 主表层级逐字段校验（Schema 字段序，与归一化顺序一致） */
    private void validateMainScope(DataScopes scopes, Map<String, Object> formData,
                                   List<SysFormDataErrorDo> errors) {
        for (Map.Entry<String, SysFormFieldDo> entry : scopes.mainScope.entrySet()) {
            String code = entry.getKey();
            SysFormFieldDo field = entry.getValue();
            Object value = null == formData ? null : formData.get(code);
            if (TYPE_FORMULA.equals(field.getType())) {
                // 公式字段值不参与校验（服务端重算值落库，ADR-09；重算随 F15-BE-005 并联）
                continue;
            }
            if (TYPE_DETAIL_TABLE.equals(field.getType())) {
                validateDetailRows(field, code, scopes.rowScopes.get(code), value, errors);
            } else {
                validateSingleValue(field, code, value, null, errors);
            }
        }
    }

    /**
     * 单字段校验（必填 → 类型 → 长度 → 枚举选项值，逐条短路——单字段命中前一类错误后
     * 不再叠加后续检查，避免同一字段错误雪崩）。
     *
     * @param rowIndex 明细行行号（0 起始）；主表字段为 null
     */
    private void validateSingleValue(SysFormFieldDo field, String code, Object value,
                                     Integer rowIndex, List<SysFormDataErrorDo> errors) {
        String where = valueWhere(field, code, rowIndex);
        Map<String, Object> props = field.getProps();
        // 必填（静态基础必填；动态必填由规则 setRequired 运行时覆盖，随 F15-BE-004 并联合并）
        boolean required = null != props && Boolean.TRUE.equals(props.get("required"));
        if (isEmptyValue(value)) {
            if (required) {
                errors.add(new SysFormDataErrorDo(code, rowIndex, null, where + "为必填项"));
            }
            return;
        }
        // 类型（值域随组件 type）
        String typeMismatch = typeMismatch(field.getType(), value, props);
        if (null != typeMismatch) {
            errors.add(new SysFormDataErrorDo(code, rowIndex, null, where + typeMismatch));
            return;
        }
        // 长度（input/textarea 配置 maxlength 时）
        if (("input".equals(field.getType()) || "textarea".equals(field.getType()))
                && value instanceof String text) {
            Integer maxlength = intProp(props, "maxlength");
            if (null != maxlength && text.length() > maxlength) {
                errors.add(new SysFormDataErrorDo(code, rowIndex, null,
                        where + "长度 " + text.length() + " 超过上限 " + maxlength));
                return;
            }
        }
        // 枚举选项值（choice/select/cascade 配置 options 时；datasource/addressbook 选项
        // 为动态集不配置 options，跳过）
        if (MULTI_CAPABLE_TYPES.contains(field.getType())) {
            validateOptionValues(field, code, where, rowIndex, value, props, errors);
        }
    }

    /** 枚举选项值校验：提交值（multi 时逐元素）∈ props.options[].value */
    private void validateOptionValues(SysFormFieldDo field, String code, String where,
                                      Integer rowIndex, Object value, Map<String, Object> props,
                                      List<SysFormDataErrorDo> errors) {
        Object optionsRaw = null == props ? null : props.get("options");
        if (!(optionsRaw instanceof List<?> optionList) || optionList.isEmpty()) {
            return; // 未配置静态选项不做枚举校验
        }
        Set<Object> allowed = new HashSet<>();
        for (Object option : optionList) {
            if (option instanceof Map<?, ?> optionMap && optionMap.containsKey("value")) {
                allowed.add(optionMap.get("value"));
            }
        }
        if (allowed.isEmpty()) {
            return;
        }
        List<?> values = value instanceof List<?> list ? list : List.of(value);
        for (Object item : values) {
            if (!containsOptionValue(allowed, item)) {
                errors.add(new SysFormDataErrorDo(code, rowIndex, null,
                        where + "提交值不在选项范围内: " + item));
                return;
            }
        }
    }

    /**
     * 选项值比对：原始值相等或字符串形态相等（容忍同一数值的 JSON 表示差异，如 1 与 1.0、
     * 数值与数值字符串的前端序列化漂移）。
     */
    private boolean containsOptionValue(Set<Object> allowed, Object item) {
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

    /** 明细行结构与行数上限校验（行数组 → 行数下限/上限 → 逐行逐字段行上下文校验） */
    private void validateDetailRows(SysFormFieldDo field, String code,
                                    Map<String, SysFormFieldDo> rowScope, Object value,
                                    List<SysFormDataErrorDo> errors) {
        String where = valueWhere(field, code, null);
        Map<String, Object> props = field.getProps();
        int rowMin = intOrZero(props, "rowMin");
        int rowMax = null != intProp(props, "rowMax") ? intProp(props, "rowMax")
                : SysFormConstants.MAX_DETAIL_ROWS;
        if (isEmptyValue(value)) {
            // 空行集按行数下限裁决（必填语义 = 至少一行按 rowMin，FormSchemaValidator 同口径）
            if (rowMin > 0) {
                errors.add(new SysFormDataErrorDo(code, null, null,
                        where + "明细行数 0 不足下限 " + rowMin));
            }
            return;
        }
        if (!(value instanceof List<?> rows)) {
            errors.add(new SysFormDataErrorDo(code, null, null, where + "值须为明细行对象数组"));
            return;
        }
        if (rows.size() < rowMin) {
            errors.add(new SysFormDataErrorDo(code, null, null,
                    where + "明细行数 " + rows.size() + " 不足下限 " + rowMin));
            return;
        }
        if (rows.size() > rowMax) {
            errors.add(new SysFormDataErrorDo(code, null, null,
                    where + "明细行数 " + rows.size() + " 超过上限 " + rowMax));
            return;
        }
        // 逐行行上下文校验（行内字段码独立成层级，行内 required/type/length/options 同主表口径）
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (!(row instanceof Map<?, ?>)) {
                errors.add(new SysFormDataErrorDo(code, i, null,
                        where + "第 " + (i + 1) + " 行须为对象结构"));
                continue;
            }
            Map<?, ?> rowMap = (Map<?, ?>) row;
            for (Map.Entry<String, SysFormFieldDo> rowField : rowScope.entrySet()) {
                Object rowValue = rowMap.get(rowField.getKey());
                validateSingleValue(rowField.getValue(), rowField.getKey(), rowValue, i, errors);
            }
        }
    }

    /* ==================== 工具 ==================== */

    /**
     * 组件类型域判定（返回 null = 类型合法，否则为违规文案）：
     * input/textarea/date → 文本；number → 数值；switch → 布尔；attachment → 附件引用
     * 对象数组；multi 组件按 props.multi 裁定标量 / 数组；未识别 type 不校验（定义期
     * 结构校验器已按白名单拦截非法 type，ADR-10）。
     */
    private String typeMismatch(String type, Object value, Map<String, Object> props) {
        switch (null == type ? "" : type) {
            case "input", "textarea", "date":
                return value instanceof String ? null : "值类型须为文本";
            case "number":
                return value instanceof Number ? null : "值类型须为数值";
            case "switch":
                return value instanceof Boolean ? null : "值类型须为布尔";
            case "attachment":
                return isMapList(value) ? null : "值须为附件引用对象数组";
            default:
                if (MULTI_CAPABLE_TYPES.contains(type)) {
                    boolean multi = null != props && Boolean.TRUE.equals(props.get("multi"));
                    return multi ? (isScalarList(value) ? null : "值类型须为数组（multi）")
                            : (isScalar(value) ? null : "值类型须为标量");
                }
                return null;
        }
    }

    /** 错误定位前缀：字段 label 优先（缺失回退字段码），明细行内附行号（1 起始展示） */
    private String valueWhere(SysFormFieldDo field, String code, Integer rowIndex) {
        String label = nonBlank(field.getLabel()) ? field.getLabel() : code;
        String prefix = "「" + label + "」";
        return null == rowIndex ? prefix : prefix + "（第 " + (rowIndex + 1) + " 行）";
    }

    /** 空值判定：null / 空白串 / 空集合 / 空对象视为无值；switch=false 等有义值不算空 */
    private boolean isEmptyValue(Object value) {
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

    /** props 整数属性（Number 形态；非数值 / 缺省返回 null） */
    private Integer intProp(Map<String, Object> props, String key) {
        Object raw = null == props ? null : props.get(key);
        return raw instanceof Number number ? number.intValue() : null;
    }

    /** props 整数属性缺省 0（行数下限 rowMin 口径） */
    private int intOrZero(Map<String, Object> props, String key) {
        Integer value = intProp(props, key);
        return null == value ? 0 : value;
    }

    /** 标量判定（字符串 / 数值 / 布尔） */
    private boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    /** 全标量数组判定（multi 组件多选值域） */
    private boolean isScalarList(Object value) {
        if (!(value instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (!isScalar(item)) {
                return false;
            }
        }
        return true;
    }

    /** 全对象数组判定（附件引用 / 明细行结构） */
    private boolean isMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                return false;
            }
        }
        return true;
    }

    /** 空白判断（null / 空白串视为无值） */
    private boolean nonBlank(String value) {
        return null != value && !value.isBlank();
    }
}

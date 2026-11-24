package com.eric.base.sys.form.service.engine;

import com.eric.base.sys.form.model.SysFormRuleValidateDo;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 表单动态校验覆盖状态（F15-BE-004）
 *
 * @author Eric
 * @date 2026/11/24
 * @description 规则 DSL 服务端复算（{@link FormValidationEngine}）与字段级合并校验
 *              （{@link FormDataBasicValidator}）之间的并联载体：复算引擎按版本 Schema
 *              求值规则后，把「显隐复算判定隐藏的字段集」「setRequired 动态必填覆盖」
 *              「setValidate 动态校验覆盖（条件范围 / 条件正则）」落入本状态，基础校验器
 *              在最终载荷上按覆盖合并裁决（功能设计 §6.1 第⑤步「规则 setRequired /
 *              setValidate 与静态校验合并」）。
 *              <ul>
 *              <li><b>hidden 主表字段集</b>：显隐复算判定隐藏（show/hide 规则终态）∪
 *                  permission=hidden（权限裁剪，无论渲染态一律裁剪，§3.6 / 分册 §4.3 S3/S7）；
 *                  集合内字段值将被提交裁剪不落库，且不参与必填校验（ hidden 字段不参与
 *                  必填——值被裁剪后要求必填会阻断合法提交）；仅主表层级——明细行内显隐
 *                  一期为渲染层语义，行内校验全量执行（§3.6 一期边界）；</li>
 *              <li><b>动态必填覆盖</b>：匹配规则 setRequired(target, value) 的终态覆盖
 *                  （后写覆盖先写），并保留触发规则 ID 供错误项 ruleId 溯源（分册 §4.4
 *                  errors 结构）；无覆盖时回退字段静态 props.required 基线；</li>
 *              <li><b>动态校验覆盖</b>：匹配规则 setValidate(target, validate) 的逐键覆盖
 *                  （仅覆盖所列项、其余保持字段静态校验器，§3.3），逐键保留触发规则 ID；
 *                  覆盖项为条件最小 / 最大值（数值）与条件正则，校验失败文案优先取
 *                  validate.message、缺省用内置默认文案。</li>
 *              </ul>
 *              主表层级键为字段码；明细行层级键为（明细表字段码, 行号, 行内字段码）——
 *              行内字段码可与主表同码但行上下文隔离（§2.1）。实例由
 *              {@link FormValidationEngine} 复算时构建并写入；{@link #empty()} 供无规则
 *              场景的基础校验入口使用（行为与纯静态校验完全一致）。
 */
public class FormDynamicState {

    /** 主表层级隐藏字段集（显隐复算终态隐藏 ∪ permission=hidden；裁剪与必填豁免共用） */
    private final Set<String> hiddenMainCodes = new LinkedHashSet<>();

    /** 主表层级动态必填覆盖：字段码 → 覆盖（后写覆盖先写） */
    private final Map<String, RequiredOverride> mainRequired = new LinkedHashMap<>();

    /** 主表层级动态校验覆盖：字段码 → 覆盖（逐键覆盖所列项） */
    private final Map<String, DynamicValidate> mainValidate = new LinkedHashMap<>();

    /** 明细行层级动态必填覆盖：明细表字段码 → 行号 → 行内字段码 → 覆盖（行上下文隔离） */
    private final Map<String, Map<Integer, Map<String, RequiredOverride>>> rowRequired = new LinkedHashMap<>();

    /** 明细行层级动态校验覆盖：明细表字段码 → 行号 → 行内字段码 → 覆盖（行上下文隔离） */
    private final Map<String, Map<Integer, Map<String, DynamicValidate>>> rowValidate = new LinkedHashMap<>();

    /** 空状态（无规则 / 无覆盖场景，行为与纯静态校验一致） */
    public static FormDynamicState empty() {
        return new FormDynamicState();
    }

    /* ==================== 查询（FormDataBasicValidator 消费侧） ==================== */

    /** 主表字段是否被复算判定隐藏（显隐终态 ∪ permission=hidden；仅主表层级，行内不适用） */
    public boolean isHiddenMain(String code) {
        return hiddenMainCodes.contains(code);
    }

    /** 主表字段动态必填覆盖（null = 无覆盖，回退静态基线） */
    public RequiredOverride mainRequired(String code) {
        return mainRequired.get(code);
    }

    /** 主表字段动态校验覆盖（null = 无覆盖） */
    public DynamicValidate mainValidate(String code) {
        return mainValidate.get(code);
    }

    /**
     * 主表隐藏字段集快照（显隐复算终态 ∪ permission=hidden，供 replay 裁剪与错误必填豁免
     * 同源消费；返回不可变快照）
     */
    public Set<String> snapshotHiddenMainCodes() {
        return Set.copyOf(hiddenMainCodes);
    }

    /** 明细行内字段动态必填覆盖（null = 无覆盖，回退静态基线） */
    public RequiredOverride rowRequired(String detailCode, int rowIndex, String code) {
        Map<Integer, Map<String, RequiredOverride>> rows = rowRequired.get(detailCode);
        Map<String, RequiredOverride> row = null == rows ? null : rows.get(rowIndex);
        return null == row ? null : row.get(code);
    }

    /** 明细行内字段动态校验覆盖（null = 无覆盖） */
    public DynamicValidate rowValidate(String detailCode, int rowIndex, String code) {
        Map<Integer, Map<String, DynamicValidate>> rows = rowValidate.get(detailCode);
        Map<String, DynamicValidate> row = null == rows ? null : rows.get(rowIndex);
        return null == row ? null : row.get(code);
    }

    /* ==================== 写入（FormValidationEngine 复算侧，包内可见） ==================== */

    void addHiddenMain(String code) {
        hiddenMainCodes.add(code);
    }

    void putMainRequired(String code, boolean required, String ruleId) {
        mainRequired.put(code, new RequiredOverride(required, ruleId));
    }

    void putRowRequired(String detailCode, int rowIndex, String code, boolean required, String ruleId) {
        rowRequired.computeIfAbsent(detailCode, k -> new LinkedHashMap<>())
                .computeIfAbsent(rowIndex, k -> new LinkedHashMap<>())
                .put(code, new RequiredOverride(required, ruleId));
    }

    /** 主表字段动态校验覆盖状态（懒创建；同目标多条规则逐键覆盖） */
    DynamicValidate mainValidateForWrite(String code) {
        return mainValidate.computeIfAbsent(code, k -> new DynamicValidate());
    }

    /** 明细行内字段动态校验覆盖状态（懒创建；同目标多条规则逐键覆盖） */
    DynamicValidate rowValidateForWrite(String detailCode, int rowIndex, String code) {
        return rowValidate.computeIfAbsent(detailCode, k -> new LinkedHashMap<>())
                .computeIfAbsent(rowIndex, k -> new LinkedHashMap<>())
                .computeIfAbsent(code, k -> new DynamicValidate());
    }

    /* ==================== 覆盖载荷 ==================== */

    /** 动态必填覆盖（setRequired 终态 + 触发规则 ID 溯源） */
    public static final class RequiredOverride {

        /** 动态必填终态（true = 必填，false = 明确豁免——覆盖静态基线） */
        private final boolean required;

        /** 触发规则 ID（错误项 ruleId 溯源） */
        private final String ruleId;

        RequiredOverride(boolean required, String ruleId) {
            this.required = required;
            this.ruleId = ruleId;
        }

        public boolean isRequired() {
            return required;
        }

        public String getRuleId() {
            return ruleId;
        }
    }

    /**
     * 动态校验覆盖状态（setValidate 逐键覆盖：min / max / pattern / message 四键各自独立
     * 覆盖并保留触发规则 ID；仅覆盖所列项、其余保持字段静态校验器，§3.3）
     */
    public static final class DynamicValidate {

        private Number min;
        private String minRuleId;
        private Number max;
        private String maxRuleId;
        private String pattern;
        private String patternRuleId;
        private String message;
        private String messageRuleId;

        /** 应用一条 setValidate 载荷（仅覆盖所列项；逐键登记触发规则 ID） */
        void applySetValidate(SysFormRuleValidateDo validate, String ruleId) {
            if (null == validate) {
                return;
            }
            if (null != validate.getMin()) {
                this.min = validate.getMin();
                this.minRuleId = ruleId;
            }
            if (null != validate.getMax()) {
                this.max = validate.getMax();
                this.maxRuleId = ruleId;
            }
            if (null != validate.getPattern() && !validate.getPattern().isBlank()) {
                this.pattern = validate.getPattern();
                this.patternRuleId = ruleId;
            }
            if (null != validate.getMessage() && !validate.getMessage().isBlank()) {
                this.message = validate.getMessage();
                this.messageRuleId = ruleId;
            }
        }

        public Number getMin() {
            return min;
        }

        public String getMinRuleId() {
            return minRuleId;
        }

        public Number getMax() {
            return max;
        }

        public String getMaxRuleId() {
            return maxRuleId;
        }

        public String getPattern() {
            return pattern;
        }

        public String getPatternRuleId() {
            return patternRuleId;
        }

        /** 校验失败自定义文案（缺省返回 null，由校验器取内置默认文案） */
        public String getMessage() {
            return message;
        }
    }
}

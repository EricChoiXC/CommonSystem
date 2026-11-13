package com.eric.base.sys.number.service;

import com.eric.base.sys.number.model.SysNumberRuleConfigDo;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo.Item;
import com.eric.base.sys.number.model.SysNumberRuleErrorDo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 编号规则配置校验组件（C1~C7）
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对 SysNumberRuleConfigDo 执行方案 §2.2/§2.3 的保存/启用前强制校验，
 *              独立于 Service 便于复用与单测（A3）；最终公共入口为
 *              SysNumberRuleService#validateConfig（NB3-BE-002 委托本组件）。
 *              错误统一抛 {@link NumberRuleInvalidException}（CRM_NUMBER_RULE_INVALID 语义），
 *              或经 {@link #validate} 返回明细供 preview/render 响应 errors 就地提示。
 *              <p>错误 code 与方案规则映射（字段级约束方案未编号，实施补充为 C0）：</p>
 *              <ul>
 *              <li>C1 —— 规则结构与流水锚点：items 非空、seq 恰好一个、item id 非空且唯一
 *                 （field_serial_id 据此定位）；</li>
 *              <li>C0 —— 规则项字段约束（§2.2 各类型专属属性）：未知类型、text 非空 1..32、
 *                  datetime format 白名单/受限自定义字符集、seq start≥0 与 length 1..9 与
 *                  resetPeriod 枚举、random length 与 min ≤ max；</li>
 *              <li>C3 —— 日期项与重置周期联动（§2.3 联动表）：DAY/WEEK 需含 y M d、
 *                  MONTH 需含 y M、YEAR 需含 y；</li>
 *              <li>C4 —— 无日期项时 resetPeriod 必须为 NONE；</li>
 *              <li>C5 —— 拼接最大长度 ≤ 64（按上限估算：文本宽 + 日期最大渲染宽 +
 *                  seq 容量 10^length−1 位宽 + random 宽，R4 偏保守，取号侧组装后二次兜底）；</li>
 *              <li>C7 —— 已保存规则的 seq 项 item.id 不得变更（update 场景传旧规则比对）。</li>
 *              </ul>
 *              <p>C2 为语义约束（random 每次取号重新生成、不参与计数，唯一性由 seq 保证），
 *              无校验逻辑。</p>
 */
@Component
public class SysNumberRuleValidator {

    /** 结构与流水锚点（seq 恰好一个、item id 非空唯一） */
    public static final String CODE_C1 = "C1";

    /** 规则项字段约束（§2.2 各类型专属属性，方案未编号，实施补充码） */
    public static final String CODE_C0 = "C0";

    /** 日期项与重置周期联动 */
    public static final String CODE_C3 = "C3";

    /** 无日期项时 resetPeriod 必须为 NONE */
    public static final String CODE_C4 = "C4";

    /** 拼接最大长度 ≤ 64 */
    public static final String CODE_C5 = "C5";

    /** 已保存规则 seq 项 id 锁定 */
    public static final String CODE_C7 = "C7";

    /** 日期格式白名单（§2.2） */
    private static final List<String> DATE_FORMAT_WHITELIST = List.of(
            "yyyy-MM-dd", "yyyyMMdd", "MMdd", "HHmmss", "yyyyMMddHHmmss", "yyyy-MM-dd HH:mm:ss");

    /** 受限自定义 pattern 允许的格式字母 */
    private static final String DATE_FORMAT_LETTERS = "yMdHms";

    /** 受限自定义 pattern 允许的分隔符 */
    private static final String DATE_FORMAT_SEPARATORS = "-/: ";

    /** seq 合法重置周期 */
    private static final Set<String> RESET_PERIODS = Set.of(
            SysNumberRuleConfigDo.RESET_DAY, SysNumberRuleConfigDo.RESET_WEEK,
            SysNumberRuleConfigDo.RESET_MONTH, SysNumberRuleConfigDo.RESET_YEAR,
            SysNumberRuleConfigDo.RESET_NONE);

    /** 文本项内容最大长度（§2.2） */
    private static final int TEXT_MAX_LENGTH = 32;

    /**
     * 全量校验（C1~C5，无 C7 比对），返回全部错误明细（顺序：C1 → C0 → C4 → C3 → C5）。
     *
     * @param config 规则配置
     * @return 错误明细；无错误返回空列表
     */
    public List<SysNumberRuleErrorDo> validate(SysNumberRuleConfigDo config) {
        return validate(config, null);
    }

    /**
     * 全量校验（C1~C5 + C7），update 场景传库中原规则比对 seq 项 id 锁定。
     *
     * @param config    新规则配置
     * @param oldConfig 库中原规则（save 场景传 null，跳过 C7）
     * @return 错误明细；无错误返回空列表
     */
    public List<SysNumberRuleErrorDo> validate(SysNumberRuleConfigDo config, SysNumberRuleConfigDo oldConfig) {
        List<SysNumberRuleErrorDo> errors = new ArrayList<>();
        if (config == null || config.getItems() == null || config.getItems().isEmpty()) {
            errors.add(new SysNumberRuleErrorDo(CODE_C1, null, "编号规则不能为空：须至少包含一个流水号（seq）项"));
            return errors;
        }
        List<Item> items = config.getItems();

        // C1：item id 非空且唯一（唯一性锚点定位依据）
        Set<String> idSet = new HashSet<>();
        for (Item item : items) {
            if (item == null) {
                errors.add(new SysNumberRuleErrorDo(CODE_C1, null, "规则项不能为空"));
                continue;
            }
            String id = item.getId();
            if (id == null || id.isBlank()) {
                errors.add(new SysNumberRuleErrorDo(CODE_C1, null, "规则项 id 不能为空（流水计数锚点依赖 item id 定位）"));
            } else if (!idSet.add(id)) {
                errors.add(new SysNumberRuleErrorDo(CODE_C1, id, "规则项 id 重复：" + id));
            }
        }

        // C1：seq 恰好一个
        List<Item> seqItems = items.stream()
                .filter(item -> item != null && SysNumberRuleConfigDo.TYPE_SEQ.equals(item.getType()))
                .toList();
        if (seqItems.isEmpty()) {
            errors.add(new SysNumberRuleErrorDo(CODE_C1, null, "缺少流水号（seq）项：流水号是编号唯一性锚点，必须恰好一个"));
        } else if (seqItems.size() > 1) {
            errors.add(new SysNumberRuleErrorDo(CODE_C1, seqItems.get(1).getId(),
                    "流水号（seq）项必须恰好一个，当前 " + seqItems.size() + " 个"));
        }

        // C0：各规则项字段约束
        for (Item item : items) {
            if (item != null) {
                validateItem(item, errors);
            }
        }

        boolean hasDatetime = items.stream()
                .anyMatch(item -> item != null && SysNumberRuleConfigDo.TYPE_DATETIME.equals(item.getType()));

        // C4 / C3（seq 个数异常时已由 C1 报错，此处以第一个 seq 为准避免错误级联）
        if (!seqItems.isEmpty()) {
            Item seq = seqItems.get(0);
            if (!hasDatetime) {
                if (!SysNumberRuleConfigDo.RESET_NONE.equals(seq.getResetPeriod())) {
                    errors.add(new SysNumberRuleErrorDo(CODE_C4, seq.getId(),
                            "规则无日期项，流水号重置周期必须为 NONE（seq 不重置），否则跨周期重号"));
                }
            } else {
                validatePeriodLinkage(seq, items, errors);
            }
        }

        // C5：拼接最大长度 ≤ 64（按上限估算，R4 偏保守）
        long estimated = 0;
        for (Item item : items) {
            if (item == null || item.getType() == null) {
                continue;
            }
            switch (item.getType()) {
                case SysNumberRuleConfigDo.TYPE_TEXT -> estimated += item.getText() == null ? 0 : item.getText().length();
                case SysNumberRuleConfigDo.TYPE_DATETIME -> estimated += estimateDateWidth(item.getFormat());
                case SysNumberRuleConfigDo.TYPE_SEQ, SysNumberRuleConfigDo.TYPE_RANDOM ->
                        estimated += item.getLength() == null ? 0 : item.getLength();
                default -> { /* 未知类型已在 C0 报错，不计宽 */ }
            }
        }
        if (estimated > SysNumberRuleConfigDo.MAX_CODE_LENGTH) {
            errors.add(new SysNumberRuleErrorDo(CODE_C5, null,
                    "拼接最大长度估算为 " + estimated + "，超过上限 " + SysNumberRuleConfigDo.MAX_CODE_LENGTH));
        }

        // C7：已保存规则 seq 项 id 锁定（save 场景 oldConfig 为 null 跳过）
        if (oldConfig != null && oldConfig.getItems() != null && !seqItems.isEmpty()) {
            Item oldSeq = oldConfig.getItems().stream()
                    .filter(item -> item != null && SysNumberRuleConfigDo.TYPE_SEQ.equals(item.getType()))
                    .findFirst().orElse(null);
            if (oldSeq != null && oldSeq.getId() != null && !oldSeq.getId().equals(seqItems.get(0).getId())) {
                errors.add(new SysNumberRuleErrorDo(CODE_C7, seqItems.get(0).getId(),
                        "已保存规则的流水号项 id 不可变更（计数器锚点），须保留原值 " + oldSeq.getId()));
            }
        }

        return errors;
    }

    /**
     * 校验并在有错误时抛 {@link NumberRuleInvalidException}（无 C7 比对）。
     *
     * @param config 规则配置
     */
    public void assertValid(SysNumberRuleConfigDo config) {
        assertValid(config, null);
    }

    /**
     * 校验并在有错误时抛 {@link NumberRuleInvalidException}（update 场景传旧规则比对 C7）。
     *
     * @param config    新规则配置
     * @param oldConfig 库中原规则（save 场景传 null）
     */
    public void assertValid(SysNumberRuleConfigDo config, SysNumberRuleConfigDo oldConfig) {
        List<SysNumberRuleErrorDo> errors = validate(config, oldConfig);
        if (!errors.isEmpty()) {
            throw new NumberRuleInvalidException(errors);
        }
    }

    /**
     * 单规则项字段约束（C0，§2.2 各类型专属属性）
     */
    private void validateItem(Item item, List<SysNumberRuleErrorDo> errors) {
        String type = item.getType();
        if (type == null || type.isBlank()) {
            errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(), "规则项类型不能为空"));
            return;
        }
        switch (type) {
            case SysNumberRuleConfigDo.TYPE_TEXT -> {
                String text = item.getText();
                if (text == null || text.isBlank()) {
                    errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(), "文本项内容不能为空"));
                } else if (text.length() > TEXT_MAX_LENGTH) {
                    errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(),
                            "文本项内容长度不能超过 " + TEXT_MAX_LENGTH + " 字符"));
                }
            }
            case SysNumberRuleConfigDo.TYPE_DATETIME -> {
                if (!isDateFormatLegal(item.getFormat())) {
                    errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(),
                            "日期项格式非法：须为白名单格式（yyyy-MM-dd / yyyyMMdd / MMdd / HHmmss / "
                                    + "yyyyMMddHHmmss / yyyy-MM-dd HH:mm:ss），或仅含 y M d H m s 与 - / : 空格 的自定义组合"));
                }
            }
            case SysNumberRuleConfigDo.TYPE_SEQ -> {
                Integer start = item.getStart();
                if (start == null) {
                    errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(), "流水号项 start 不能为空"));
                } else if (start < 0) {
                    errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(), "流水号项 start 不能小于 0"));
                }
                Integer length = item.getLength();
                if (length == null) {
                    errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(), "流水号项 length 不能为空"));
                } else if (length < 1 || length > 9) {
                    errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(), "流水号项 length 须在 1..9 之间"));
                }
                String resetPeriod = item.getResetPeriod();
                if (resetPeriod == null || resetPeriod.isBlank()) {
                    errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(), "流水号项 resetPeriod 不能为空"));
                } else if (!RESET_PERIODS.contains(resetPeriod)) {
                    errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(),
                            "流水号项 resetPeriod 仅允许 DAY/WEEK/MONTH/YEAR/NONE"));
                }
            }
            case SysNumberRuleConfigDo.TYPE_RANDOM -> {
                Integer length = item.getLength();
                if (length == null || length < 1) {
                    errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(), "随机数项 length 不能为空且须不小于 1"));
                }
                if (Boolean.TRUE.equals(item.getNumericOnly())) {
                    Integer min = item.getMin();
                    Integer max = item.getMax();
                    if (min == null || max == null) {
                        errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(),
                                "随机数项 numericOnly=true 时 min/max 不能为空"));
                    } else if (min > max) {
                        errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(), "随机数项 min 不能大于 max"));
                    }
                }
                // numericOnly=false 时仅 length 生效，min/max 置灰不校验（§2.2）
            }
            default -> errors.add(new SysNumberRuleErrorDo(CODE_C0, item.getId(), "未知规则项类型：" + type));
        }
    }

    /**
     * C3 联动表：seq 重置周期 ↔ 日期项格式要求（存在任一满足要求的日期项即通过）
     */
    private void validatePeriodLinkage(Item seq, List<Item> items, List<SysNumberRuleErrorDo> errors) {
        String resetPeriod = seq.getResetPeriod();
        if (resetPeriod == null) {
            // 非法 resetPeriod 已在 C0 报错，避免重复
            return;
        }
        String requiredLetters = switch (resetPeriod) {
            case SysNumberRuleConfigDo.RESET_DAY, SysNumberRuleConfigDo.RESET_WEEK -> "yMd";
            case SysNumberRuleConfigDo.RESET_MONTH -> "yM";
            case SysNumberRuleConfigDo.RESET_YEAR -> "y";
            default -> null;
        };
        if (requiredLetters == null) {
            // NONE 无要求；非法值已在 C0 报错
            return;
        }
        boolean satisfied = items.stream()
                .filter(item -> item != null && SysNumberRuleConfigDo.TYPE_DATETIME.equals(item.getType()))
                .anyMatch(item -> formatContains(item.getFormat(), requiredLetters));
        if (!satisfied) {
            errors.add(new SysNumberRuleErrorDo(CODE_C3, seq.getId(),
                    "流水号按 " + resetPeriod + " 重置，但日期项格式无法区分相邻重置周期（需包含 "
                            + String.join(" ", requiredLetters.split("")) + "），跨周期会重号"));
        }
    }

    /**
     * 日期格式合法性：白名单内直接通过；白名单外仅允许 y M d H m s 与 - / : 空格 组合，
     * 且至少包含一个格式字母（纯分隔符无法渲染日期）
     */
    private boolean isDateFormatLegal(String format) {
        if (format == null || format.isBlank()) {
            return false;
        }
        if (DATE_FORMAT_WHITELIST.contains(format)) {
            return true;
        }
        boolean hasLetter = false;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (DATE_FORMAT_LETTERS.indexOf(c) >= 0) {
                hasLetter = true;
            } else if (DATE_FORMAT_SEPARATORS.indexOf(c) < 0) {
                return false;
            }
        }
        return hasLetter;
    }

    /**
     * 日期项格式是否包含全部必需字母
     */
    private boolean formatContains(String format, String requiredLetters) {
        if (format == null) {
            return false;
        }
        for (int i = 0; i < requiredLetters.length(); i++) {
            if (format.indexOf(requiredLetters.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 日期项最大渲染宽度估算（按上限，R4 保守）：y 记 4 位、其余格式字母记 2 位
     * （同字母连写按一个字段计，如 yyyy 仍记 4），分隔符逐字符记 1
     */
    private int estimateDateWidth(String format) {
        if (format == null || format.isBlank()) {
            return 0;
        }
        int width = 0;
        int i = 0;
        while (i < format.length()) {
            char c = format.charAt(i);
            if (DATE_FORMAT_LETTERS.indexOf(c) >= 0) {
                width += c == 'y' ? 4 : 2;
                while (i < format.length() && format.charAt(i) == c) {
                    i++;
                }
            } else {
                width++;
                i++;
            }
        }
        return width;
    }
}

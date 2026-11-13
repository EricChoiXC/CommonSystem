package com.eric.base.sys.number.service;

import com.eric.base.sys.number.model.SysNumberRuleConfigDo;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo.Item;
import com.eric.base.sys.number.model.SysNumberRuleErrorDo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编号规则配置校验组件测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆盖任务 NB1-BE-001 验收标准：C1~C5 / C7 各违反分支、
 *              方案 §2.3 C3 联动表反例（MMdd、HHmmss、dd）、日期格式白名单外
 *              pattern 拒绝、C5 长度边界（64 恰好通过 / 65 拒绝）、
 *              assertValid 统一抛 NumberRuleInvalidException（CRM_NUMBER_RULE_INVALID 语义）、
 *              以及 SysNumberRuleConfigDo 的 Jackson 序列化回环（禁止手工拼串）。
 */
class SysNumberRuleValidatorTest {

    private final SysNumberRuleValidator validator = new SysNumberRuleValidator();

    // ---------------------------------------------------------------- 构造辅助

    private Item item(String id, String type, String name) {
        Item item = new Item();
        item.setId(id);
        item.setType(type);
        item.setName(name);
        return item;
    }

    private Item textItem(String id, String text) {
        Item item = item(id, SysNumberRuleConfigDo.TYPE_TEXT, "文本");
        item.setText(text);
        return item;
    }

    private Item datetimeItem(String id, String format) {
        Item item = item(id, SysNumberRuleConfigDo.TYPE_DATETIME, "日期");
        item.setFormat(format);
        return item;
    }

    private Item seqItem(String id, Integer start, Integer length, String resetPeriod) {
        Item item = item(id, SysNumberRuleConfigDo.TYPE_SEQ, "流水号");
        item.setStart(start);
        item.setLength(length);
        item.setPadZero(true);
        item.setResetPeriod(resetPeriod);
        return item;
    }

    private Item randomItem(String id, Integer min, Integer max, Integer length, Boolean numericOnly) {
        Item item = item(id, SysNumberRuleConfigDo.TYPE_RANDOM, "随机码");
        item.setMin(min);
        item.setMax(max);
        item.setLength(length);
        item.setPadZero(true);
        item.setNumericOnly(numericOnly);
        return item;
    }

    private SysNumberRuleConfigDo config(Item... items) {
        SysNumberRuleConfigDo config = new SysNumberRuleConfigDo();
        config.setItems(new ArrayList<>(List.of(items)));
        return config;
    }

    private List<String> codes(List<SysNumberRuleErrorDo> errors) {
        return errors.stream().map(SysNumberRuleErrorDo::getCode).collect(Collectors.toList());
    }

    private List<String> messages(List<SysNumberRuleErrorDo> errors, String code) {
        return errors.stream()
                .filter(error -> code.equals(error.getCode()))
                .map(SysNumberRuleErrorDo::getMessage)
                .collect(Collectors.toList());
    }

    /** 方案 §2.1 示例规则（合法基准） */
    private SysNumberRuleConfigDo planExample() {
        return config(
                textItem("ci-a1b2c3d4e5f6", "CG"),
                datetimeItem("ci-b2c3d4e5f6a1", "yyyyMMdd"),
                seqItem("ci-c3d4e5f6a1b2", 1, 3, SysNumberRuleConfigDo.RESET_DAY),
                randomItem("ci-d4e5f6a1b2c3", 0, 9999, 4, true));
    }

    // ---------------------------------------------------------------- 基准

    @Test
    void planExampleConfigIsValid() {
        assertTrue(validator.validate(planExample()).isEmpty());
        validator.assertValid(planExample());
    }

    // ---------------------------------------------------------------- C1 结构与流水锚点

    @Test
    void nullConfigViolatesC1() {
        List<SysNumberRuleErrorDo> errors = validator.validate(null);
        assertEquals(List.of(SysNumberRuleValidator.CODE_C1), codes(errors));
    }

    @Test
    void emptyItemsViolatesC1() {
        assertEquals(List.of(SysNumberRuleValidator.CODE_C1), codes(validator.validate(new SysNumberRuleConfigDo())));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C1), codes(validator.validate(config())));
    }

    @Test
    void missingSeqViolatesC1() {
        List<SysNumberRuleErrorDo> errors = validator.validate(config(textItem("ci-1", "CG")));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C1), codes(errors));
        assertTrue(messages(errors, SysNumberRuleValidator.CODE_C1).get(0).contains("恰好一个"));
    }

    @Test
    void duplicatedSeqViolatesC1() {
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                seqItem("ci-1", 1, 3, SysNumberRuleConfigDo.RESET_NONE),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C1), codes(errors));
        assertTrue(messages(errors, SysNumberRuleValidator.CODE_C1).get(0).contains("当前 2 个"));
    }

    @Test
    void blankItemIdViolatesC1() {
        Item seq = seqItem(" ", 1, 3, SysNumberRuleConfigDo.RESET_NONE);
        List<SysNumberRuleErrorDo> errors = validator.validate(config(seq));
        assertTrue(codes(errors).contains(SysNumberRuleValidator.CODE_C1));
        assertTrue(messages(errors, SysNumberRuleValidator.CODE_C1).get(0).contains("id 不能为空"));
    }

    @Test
    void nullItemElementViolatesC1() {
        SysNumberRuleConfigDo config = config(seqItem("ci-1", 1, 3, SysNumberRuleConfigDo.RESET_NONE));
        config.getItems().add(null);
        List<SysNumberRuleErrorDo> errors = validator.validate(config);
        assertTrue(codes(errors).contains(SysNumberRuleValidator.CODE_C1));
    }

    @Test
    void duplicatedItemIdViolatesC1() {
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                textItem("ci-same", "CG"),
                seqItem("ci-same", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertTrue(codes(errors).contains(SysNumberRuleValidator.CODE_C1));
        assertTrue(messages(errors, SysNumberRuleValidator.CODE_C1).get(0).contains("重复"));
    }

    // ---------------------------------------------------------------- C0 规则项字段约束

    @Test
    void blankTextViolatesC0() {
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                textItem("ci-1", "  "),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));
    }

    @Test
    void overlyLongTextViolatesC0() {
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                textItem("ci-1", "x".repeat(33)),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));
    }

    @Test
    void unknownTypeViolatesC0() {
        Item unknown = item("ci-1", "const", "常量");
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                unknown,
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));
        assertTrue(messages(errors, SysNumberRuleValidator.CODE_C0).get(0).contains("未知规则项类型"));
    }

    @Test
    void illegalDatetimeFormatViolatesC0() {
        // 白名单外且含非白名单字符（中文字符）
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                datetimeItem("ci-1", "yyyy年MM月dd日"),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));
    }

    @Test
    void pureSeparatorDatetimeFormatViolatesC0() {
        // 仅分隔符、无格式字母，无法渲染日期
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                datetimeItem("ci-1", "-/: "),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));
    }

    @Test
    void blankDatetimeFormatViolatesC0() {
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                datetimeItem("ci-1", " "),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));
    }

    @Test
    void whitelistedShortDatetimeFormatsAreAccepted() {
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                textItem("ci-0", "P"),
                datetimeItem("ci-1", "yyyyMMdd"),
                datetimeItem("ci-2", "MMdd"),
                datetimeItem("ci-3", "HHmmss"),
                datetimeItem("ci-4", "yyyy-M/d H:m:s"),
                seqItem("ci-5", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertFalse(codes(errors).contains(SysNumberRuleValidator.CODE_C0));
        assertFalse(codes(errors).contains(SysNumberRuleValidator.CODE_C5));
    }

    @Test
    void whitelistedLongDatetimeFormatsAreAccepted() {
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                datetimeItem("ci-1", "yyyy-MM-dd"),
                datetimeItem("ci-2", "yyyyMMddHHmmss"),
                datetimeItem("ci-3", "yyyy-MM-dd HH:mm:ss"),
                seqItem("ci-4", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertFalse(codes(errors).contains(SysNumberRuleValidator.CODE_C0));
        assertFalse(codes(errors).contains(SysNumberRuleValidator.CODE_C5));
    }

    @Test
    void illegalSeqAttributesViolateC0() {
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                seqItem("ci-1", null, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));

        errors = validator.validate(config(
                seqItem("ci-1", -1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));

        errors = validator.validate(config(
                seqItem("ci-1", 1, 0, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));

        errors = validator.validate(config(
                seqItem("ci-1", 1, 10, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));

        errors = validator.validate(config(
                seqItem("ci-1", 1, 3, "MONTHLY")));
        // 无日期项且 resetPeriod 非 NONE，C4 同时成立
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0, SysNumberRuleValidator.CODE_C4), codes(errors));
    }

    @Test
    void illegalRandomAttributesViolateC0() {
        // numericOnly=true：min > max
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                randomItem("ci-1", 100, 0, 4, true),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));

        // numericOnly=true：min/max 缺失
        errors = validator.validate(config(
                randomItem("ci-1", null, null, 4, true),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));

        // 两种模式 length 均必填
        errors = validator.validate(config(
                randomItem("ci-1", 0, 99, null, true),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));

        errors = validator.validate(config(
                randomItem("ci-1", null, null, 0, false),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_NONE)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C0), codes(errors));
    }

    // ---------------------------------------------------------------- C3 联动表

    @Test
    void dayResetWithMMddViolatesC3() {
        // 反例：MMdd 缺年，跨年重号
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                datetimeItem("ci-1", "MMdd"),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_DAY)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C3), codes(errors));
        assertEquals("ci-2", errors.get(0).getItemId());
    }

    @Test
    void dayResetWithHHmmssViolatesC3() {
        // 反例：HHmmss 无日期
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                datetimeItem("ci-1", "HHmmss"),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_DAY)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C3), codes(errors));
    }

    @Test
    void monthResetWithDDViolatesC3() {
        // 反例：dd 无月份
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                datetimeItem("ci-1", "dd"),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_MONTH)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C3), codes(errors));
    }

    @Test
    void linkageTablePositiveCasesPass() {
        // DAY/WEEK 需 y M d
        assertTrue(validator.validate(config(
                datetimeItem("ci-1", "yyyyMMdd"),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_DAY))).isEmpty());
        assertTrue(validator.validate(config(
                datetimeItem("ci-1", "yyyy-MM-dd"),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_WEEK))).isEmpty());
        // MONTH 需 y M（自定义 pattern）
        assertTrue(validator.validate(config(
                datetimeItem("ci-1", "yyyyMM"),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_MONTH))).isEmpty());
        // YEAR 需 y
        assertTrue(validator.validate(config(
                datetimeItem("ci-1", "yyyy"),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_YEAR))).isEmpty());
    }

    @Test
    void multiDatetimeItemsNeedOneSatisfyingFormat() {
        // 多日期项时任一满足即可：HHmmss 不满足 DAY，yyyyMMdd 满足
        assertTrue(validator.validate(config(
                datetimeItem("ci-1", "HHmmss"),
                datetimeItem("ci-2", "yyyyMMdd"),
                seqItem("ci-3", 1, 3, SysNumberRuleConfigDo.RESET_DAY))).isEmpty());
        // 全部不满足则 C3
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                datetimeItem("ci-1", "HHmmss"),
                datetimeItem("ci-2", "MMdd"),
                seqItem("ci-3", 1, 3, SysNumberRuleConfigDo.RESET_DAY)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C3), codes(errors));
    }

    // ---------------------------------------------------------------- C4 无日期项

    @Test
    void noDatetimeWithDayResetViolatesC4() {
        List<SysNumberRuleErrorDo> errors = validator.validate(config(
                seqItem("ci-1", 1, 3, SysNumberRuleConfigDo.RESET_DAY)));
        assertEquals(List.of(SysNumberRuleValidator.CODE_C4), codes(errors));
        assertEquals("ci-1", errors.get(0).getItemId());
    }

    @Test
    void noDatetimeWithNoneResetPasses() {
        assertTrue(validator.validate(config(
                seqItem("ci-1", 1, 3, SysNumberRuleConfigDo.RESET_NONE))).isEmpty());
    }

    // ---------------------------------------------------------------- C5 拼接长度

    @Test
    void totalWidthExactly64Passes() {
        // 32(文本) + 19(yyyy-MM-dd HH:mm:ss) + 9(seq) + 4(random) = 64
        SysNumberRuleConfigDo config = config(
                textItem("ci-1", "x".repeat(32)),
                datetimeItem("ci-2", "yyyy-MM-dd HH:mm:ss"),
                seqItem("ci-3", 1, 9, SysNumberRuleConfigDo.RESET_NONE),
                randomItem("ci-4", 0, 9999, 4, true));
        assertTrue(validator.validate(config).isEmpty());
    }

    @Test
    void totalWidth65ViolatesC5() {
        // 32(文本) + 19(日期) + 9(seq) + 5(random) = 65
        SysNumberRuleConfigDo config = config(
                textItem("ci-1", "x".repeat(32)),
                datetimeItem("ci-2", "yyyy-MM-dd HH:mm:ss"),
                seqItem("ci-3", 1, 9, SysNumberRuleConfigDo.RESET_NONE),
                randomItem("ci-4", 0, 99999, 5, true));
        List<SysNumberRuleErrorDo> errors = validator.validate(config);
        assertEquals(List.of(SysNumberRuleValidator.CODE_C5), codes(errors));
        assertTrue(messages(errors, SysNumberRuleValidator.CODE_C5).get(0).contains("65"));
    }

    // ---------------------------------------------------------------- C7 seq 项 id 锁定

    @Test
    void changedSeqIdViolatesC7() {
        SysNumberRuleConfigDo oldConfig = config(seqItem("ci-old", 1, 3, SysNumberRuleConfigDo.RESET_NONE));
        SysNumberRuleConfigDo newConfig = config(seqItem("ci-new", 1, 3, SysNumberRuleConfigDo.RESET_NONE));
        List<SysNumberRuleErrorDo> errors = validator.validate(newConfig, oldConfig);
        assertEquals(List.of(SysNumberRuleValidator.CODE_C7), codes(errors));
        assertEquals("ci-new", errors.get(0).getItemId());
        assertTrue(messages(errors, SysNumberRuleValidator.CODE_C7).get(0).contains("ci-old"));
    }

    @Test
    void unchangedSeqIdPassesC7() {
        SysNumberRuleConfigDo oldConfig = config(
                textItem("ci-1", "CG"),
                seqItem("ci-seq", 1, 3, SysNumberRuleConfigDo.RESET_NONE));
        SysNumberRuleConfigDo newConfig = config(
                textItem("ci-1", "CGX"),
                seqItem("ci-seq", 1, 4, SysNumberRuleConfigDo.RESET_NONE));
        assertTrue(validator.validate(newConfig, oldConfig).isEmpty());
    }

    @Test
    void nullOldConfigSkipsC7() {
        SysNumberRuleConfigDo newConfig = config(seqItem("ci-new", 1, 3, SysNumberRuleConfigDo.RESET_NONE));
        assertTrue(validator.validate(newConfig, null).isEmpty());
    }

    // ---------------------------------------------------------------- 多错误累积

    @Test
    void collectsAllErrorsAcrossRules() {
        // 两个 seq（C1）+ 非法文本（C0）+ 无日期项且按天重置（C4）
        SysNumberRuleConfigDo config = config(
                textItem("ci-1", ""),
                seqItem("ci-2", 1, 3, SysNumberRuleConfigDo.RESET_DAY),
                seqItem("ci-3", 1, 3, SysNumberRuleConfigDo.RESET_DAY));
        List<SysNumberRuleErrorDo> errors = validator.validate(config);
        assertTrue(codes(errors).contains(SysNumberRuleValidator.CODE_C1));
        assertTrue(codes(errors).contains(SysNumberRuleValidator.CODE_C0));
        assertTrue(codes(errors).contains(SysNumberRuleValidator.CODE_C4));
    }

    // ---------------------------------------------------------------- assertValid 与异常语义

    @Test
    void assertValidThrowsNumberRuleInvalidException() {
        NumberRuleInvalidException exception = assertThrows(NumberRuleInvalidException.class,
                () -> validator.assertValid(config(textItem("ci-1", "CG"))));
        assertEquals(NumberRuleInvalidException.CODE, exception.getCode());
        assertEquals("CRM_NUMBER_RULE_INVALID", NumberRuleInvalidException.CODE);
        assertFalse(exception.getErrors().isEmpty());
        assertTrue(exception.getMessage().contains(NumberRuleInvalidException.CODE));
        assertTrue(exception instanceof IllegalArgumentException);
    }

    // ---------------------------------------------------------------- Jackson 序列化回环

    @Test
    void configDoJacksonRoundTrip() throws Exception {
        SysNumberRuleConfigDo config = planExample();
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(config);
        assertTrue(json.contains("\"items\""));
        assertTrue(json.contains("\"resetPeriod\":\"DAY\""));
        assertTrue(json.contains("\"numericOnly\":true"));

        SysNumberRuleConfigDo parsed = objectMapper.readValue(json, SysNumberRuleConfigDo.class);
        assertEquals(config, parsed);
        assertEquals("CG", parsed.getItems().get(0).getText());
        assertEquals(SysNumberRuleConfigDo.TYPE_SEQ, parsed.getItems().get(2).getType());
        assertEquals(SysNumberRuleConfigDo.RESET_DAY, parsed.getItems().get(2).getResetPeriod());

        // items 有序：调换顺序后不等
        SysNumberRuleConfigDo reordered = planExample();
        List<Item> items = reordered.getItems();
        Item first = items.remove(0);
        items.add(first);
        assertNotEquals(config, reordered);
    }
}

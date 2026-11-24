package com.eric.base.sys.form.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 表单规则触发条件文档对象
 *
 * @author Eric
 * @date 2026/11/24
 * @description 规则 DSL 条件 { operator, value }（功能设计 §3.2 operator 全集 10 个，
 *              一期定稿）：equal/notEqual（标量）、gt/gte/lt/lte（数值或日期字符串，
 *              date 按 same-format 字符串字典序比较）、in/notIn（数组）、empty/notEmpty
 *              （value 省略）。级联选择器选项显隐条件 showCondition 同形态复用本结构语义
 *              （R08 第 1 条：一期选项显隐条件采用规则 DSL 条件形态）。一期不开放正则匹配
 *              （matches）与跨字段比较（compare）——扩展只增 operator 不改 DSL 结构。
 *              向后兼容：未知键反序列化忽略、null 属性不输出。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "规则条件：{ operator, value }，operator ∈ 全集 10 个"
        + "（equal/notEqual/gt/gte/lt/lte/in/notIn/empty/notEmpty，功能设计 §3.2）")
public class SysFormRuleConditionDo {

    /** 条件操作符（全集 10 个：equal/notEqual/gt/gte/lt/lte/in/notIn/empty/notEmpty） */
    @Schema(description = "条件操作符（全集 10 个，功能设计 §3.2）")
    private String operator;

    /** 比较值（标量或数组随 operator：equal/notEqual 标量、gt/gte/lt/lte 数值或日期字符串、in/notIn 数组、empty/notEmpty 省略） */
    @Schema(description = "比较值（标量或数组随 operator；empty/notEmpty 省略）")
    private Object value;
}

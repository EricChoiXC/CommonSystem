package com.eric.base.sys.form.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 表单规则动作文档对象
 *
 * @author Eric
 * @date 2026/11/24
 * @description 规则 DSL 动作（功能设计 §3.3 动作类型全集 6 个，一期定稿）：show / hide
 *              （target；hide 仅切换渲染可见性、不触发清值——与 clearValue 语义严格分离，
 *              ADR-08）、setValue（target, value——字面量或 ${字段码} 引用）、clearValue
 *              （target）、setRequired（target, value(boolean)）、setValidate（target,
 *              validate{ min?, max?, pattern?, message? }——仅覆盖所列项，其余保持字段
 *              静态校验器）。一期不开放 script 类动作（R02）；setValue/clearValue 在
 *              onSubmit/onValidate 事件上禁止配置（防提交竞态，§3.4）。向后兼容：
 *              未知键反序列化忽略、null 属性不输出。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "规则动作：type ∈ 全集 6 个（show/hide/setValue/clearValue/setRequired/"
        + "setValidate，功能设计 §3.3），载荷随动作类型")
public class SysFormRuleActionDo {

    /** 动作类型（全集 6 个：show/hide/setValue/clearValue/setRequired/setValidate） */
    @Schema(description = "动作类型（全集 6 个，功能设计 §3.3）")
    private String type;

    /** 目标字段码（动作作用字段；明细行规则 target 为行内字段码，执行时注入行上下文） */
    @Schema(description = "目标字段码（必须为表单内已配置字段）")
    private String target;

    /** 动作值（setValue：字面量或 ${字段码} 引用；setRequired：boolean；其余动作不使用） */
    @Schema(description = "动作值（setValue/setRequired 使用）")
    private Object value;

    /** 动态校验覆盖载荷（setValidate 使用：min/max/pattern/message 至少一项，仅覆盖所列项） */
    @Schema(description = "动态校验覆盖载荷（setValidate 使用）")
    private SysFormRuleValidateDo validate;
}

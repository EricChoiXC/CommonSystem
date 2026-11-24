package com.eric.base.sys.form.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 表单规则 DSL 文档对象
 *
 * @author Eric
 * @date 2026/11/24
 * @description 声明式规则 DSL（功能设计 §3.1 统一定稿，要点文档第 4 步结构）：
 *              { ruleId, event, triggerField, condition, actions[] }。规则随触发字段存放于
 *              字段对象 rules[]（ADR-06——规则与字段树同源同快照），DSL 内 triggerField 显式
 *              保留（服务端平铺索引求值不依赖嵌套位置）。四类规则（显隐 / 联动 / 动态校验 /
 *              响应占位）统一为一套 DSL；明细行事件规则 triggerField = 明细表字段码、动作
 *              target 为行内字段码（执行时注入行上下文 row / rowIndex）。condition 可省略
 *              = 无条件规则（事件即触发，如 onAdd 行初始化 setValue）。向后兼容：未知键
 *              反序列化忽略、null 属性不输出。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "表单规则 DSL：{ ruleId, event, triggerField, condition, actions[] }"
        + "（功能设计 §3.1）；condition 可省略 = 无条件规则")
public class SysFormRuleDo {

    /** 规则 ID（表单内唯一，如 rule_001） */
    @Schema(description = "规则 ID（表单内唯一）")
    private String ruleId;

    /** 触发事件（SysFormEvent 八事件全集：onLoad/onChange/onFocus/onBlur/onAdd/onDelete/onSubmit/onValidate） */
    @Schema(description = "触发事件（八事件全集，功能设计 §3.4）")
    private String event;

    /** 触发字段码（= 宿主字段对象 field；明细容器事件为其字段码） */
    @Schema(description = "触发字段码（= 宿主字段对象 field；明细容器事件为其字段码）")
    private String triggerField;

    /** 触发条件（可空 = 无条件规则；operator ∈ §3.2 全集 10 个，value 标量或数组随 operator，empty/notEmpty 省略） */
    @Schema(description = "触发条件（可空 = 无条件规则）：{ operator, value }，operator ∈ 全集 10 个")
    private SysFormRuleConditionDo condition;

    /** 动作数组（按序执行，§3.3 动作全集 6 个；onSubmit/onValidate 事件禁 setValue/clearValue） */
    @Schema(description = "动作数组（按序执行，动作类型全集 6 个，功能设计 §3.3）")
    private List<SysFormRuleActionDo> actions;
}

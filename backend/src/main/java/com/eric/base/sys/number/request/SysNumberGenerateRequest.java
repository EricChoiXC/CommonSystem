package com.eric.base.sys.number.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 正式取号请求对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 generate/acquire 接口请求契约（方案 §3.2）：
 *              {@code modelName} / {@code modelField} / {@code modelId} 三字段必填——
 *              前两者定位启用规则（sys_number_rule 按 field_model_name + field_model_field，
 *              经 idx_number_rule_model），modelId 为业务单据 ID（幂等锚点，落取号明细
 *              field_model_id）。三字段判空由 Controller 守卫（对齐 SysNumberRuleController
 *              enable 的 BAD_REQUEST 先例），缺失返回 BAD_REQUEST。
 */
@Data
@Schema(description = "正式取号请求：modelName / modelField / modelId 三字段必填；"
        + "前两者定位启用规则，modelId 为业务单据 ID（幂等锚点）")
public class SysNumberGenerateRequest {

    /** 业务模型 Java 类 SimpleName（sys_number_rule.field_model_name），必填 */
    @Schema(description = "业务模型 Java 类 SimpleName（sys_number_rule.field_model_name），必填")
    private String modelName;

    /** 承接编号的字段名（sys_number_rule.field_model_field），必填 */
    @Schema(description = "承接编号的字段名（sys_number_rule.field_model_field），必填")
    private String modelField;

    /** 业务单据 ID（幂等锚点，落取号明细 field_model_id），必填 */
    @Schema(description = "业务单据 ID（幂等锚点，落取号明细 field_model_id），必填")
    private String modelId;
}

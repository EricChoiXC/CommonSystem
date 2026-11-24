package com.eric.base.sys.form.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 独立校验表单数据请求体
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §4.4 POST /data/validate 请求契约（F15-BE-003）：与 save 同构
 *              （formId / businessId? / formData），无 fieldId——不落库；填写页提交前预检与
 *              业务集成方预检通道，与 save 完全共用同一校验引擎与校验链（S0~S6，同序同语义）。
 *              字段级校验结果以 data.errors 结构化承载（HTTP 200、code 为成功，对齐公式
 *              validate / simulate 先例）；资源与确定性拒绝（S0/S1）仍以 CS_FORM_* 错误码
 *              异常返回（与 save 同语义）。
 */
@Data
@Schema(description = "独立校验表单数据请求体：与 save 同构（无 fieldId，不落库）；"
        + "字段级结果经 data.errors 结构化承载，资源与确定性拒绝以 CS_FORM_* 异常返回")
public class SysFormDataValidateRequest {

    /** 表单主档 ID（服务端锁定当前发布版本 Schema 求值，必填） */
    @Schema(description = "表单主档 ID（服务端锁定 field_current_version_id 指向版本 Schema 求值，必填）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String formId;

    /** 外部业务对象弱关联 ID（可缺省；仅随载荷透传，校验通道不落库） */
    @Schema(description = "外部业务对象弱关联 ID（可缺省；校验通道不落库）")
    private String businessId;

    /** 字段值载荷：{ 字段码: 值 }，明细表字段值为扁平行对象数组（必填） */
    @Schema(description = "字段值载荷：{ 字段码: 值 }，明细表字段值为扁平行对象数组（必填）；"
            + "含字段白名单外未知字段拒绝提交（CS_FORM_UNKNOWN_FIELD）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, Object> formData;
}

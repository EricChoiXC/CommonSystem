package com.eric.base.sys.number.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 作废取号明细请求对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 manage/void 接口请求契约（任务 NB3-BE-005，方案 §3.2 manage/void）：
 *              请求体 {@code { fieldId, fieldVoidReason }}——fieldId 必填（被作废明细 ID），
 *              fieldVoidReason 作废原因可空；仅更新 void 两字段（field_void_time /
 *              field_void_reason），不回退计数器；已作废明细重复作废幂等返回原状态。
 */
@Data
@Schema(description = "作废取号明细请求：{ fieldId, fieldVoidReason }；fieldId 必填，"
        + "仅更新 void 两字段、不回退计数器，重复作废幂等返回原状态")
public class SysNumberVoidRequest {

    /** 被作废的取号明细 ID（sys_number_record_detail.field_id），必填 */
    @Schema(description = "被作废的取号明细 ID（sys_number_record_detail.field_id），必填")
    private String fieldId;

    /** 作废原因（field_void_reason，VARCHAR(200)），可空 */
    @Schema(description = "作废原因（field_void_reason，VARCHAR(200)），可空")
    private String fieldVoidReason;
}

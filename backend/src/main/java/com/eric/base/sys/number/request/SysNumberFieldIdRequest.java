package com.eric.base.sys.number.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 按明细 ID 操作的轻量请求对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 manage/reissue 接口请求契约（任务 NB3-BE-005，方案 §3.2 manage/reissue）：
 *              请求体 {@code { fieldId }}——fieldId 必填（原取号明细 ID，须已作废）。
 *              补号按原明细 field_model_id 经计数器顺延取新号，旧号保留作废记录供审计。
 */
@Data
@Schema(description = "按明细 ID 操作的轻量请求：{ fieldId }；fieldId 必填"
        + "（原取号明细 ID，补号场景须已作废）")
public class SysNumberFieldIdRequest {

    /** 取号明细 ID（sys_number_record_detail.field_id），必填 */
    @Schema(description = "取号明细 ID（sys_number_record_detail.field_id），必填")
    private String fieldId;
}

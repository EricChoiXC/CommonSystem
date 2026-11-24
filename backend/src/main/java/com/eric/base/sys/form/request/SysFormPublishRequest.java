package com.eric.base.sys.form.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发布表单请求体
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §3.8 POST /define/publish 请求契约（F15-BE-002）：发布对象为表单，
 *              冻结其当前草稿行为不可变版本行（发布校验链 P1~P4 全部通过方可发布）。
 */
@Data
@Schema(description = "发布表单请求体：发布对象 = 表单，冻结其当前草稿行为不可变版本行")
public class SysFormPublishRequest {

    /** 表单主档 ID（发布对象） */
    @Schema(description = "表单主档 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldId;
}

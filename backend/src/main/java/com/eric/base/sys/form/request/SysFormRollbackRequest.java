package com.eric.base.sys.form.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 回滚表单版本请求体
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §3.10 POST /define/rollback 请求契约（F15-BE-002）：回滚 =
 *              复制目标历史版本内容产生新发布版本行（不经草稿态、不产生指针回拨），
 *              合法目标为 PUBLISHED / DEPRECATED 版本行，DRAFT 行拒绝。
 */
@Data
@Schema(description = "回滚表单版本请求体：复制目标版本内容重发布为新版本（不经草稿态）")
public class SysFormRollbackRequest {

    /** 表单主档 ID */
    @Schema(description = "表单主档 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldId;

    /** 回滚目标版本行 ID（sys_form_version.field_id，须为 PUBLISHED / DEPRECATED 行） */
    @Schema(description = "回滚目标版本行 ID（须为 PUBLISHED / DEPRECATED 行；DRAFT 行拒绝）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldVersionId;
}

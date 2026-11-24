package com.eric.base.sys.form.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发布 / 回滚动作响应
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §3.8 /publish 与 §3.10 /rollback 响应契约（F15-BE-002）：
 *              data = { fieldVersionId, fieldVersion }——新发布版本行 ID 与整数版本号
 *              （发布 = 草稿行冻结后的版本行；回滚 = 复制目标版本内容产生的新版本行）。
 */
@Data
@Schema(description = "发布 / 回滚动作响应：新发布版本行 ID 与整数版本号")
public class SysFormVersionCreatedVo {

    /** 新发布版本行 ID（sys_form_version.field_id；主档 field_current_version_id 指向该行） */
    @Schema(description = "新发布版本行 ID（主档 field_current_version_id 指向该行）")
    private String fieldVersionId;

    /** 新发布版本的整数版本号（单调递增） */
    @Schema(description = "新发布版本的整数版本号（单调递增）")
    private Integer fieldVersion;
}

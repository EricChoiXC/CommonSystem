package com.eric.base.sys.form.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 表单版本行响应
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §3.9 GET /define/versions 响应契约（F15-BE-002）：版本行不返回
 *              Schema 内容（体积大；回滚由服务端复制，历史版本 Schema 预览一期不单设接口）。
 *              排序定稿（F15-BE-002，回写分册 §3.9 / 数据库分册 §9.3 条目 1）：草稿行恒排最前
 *              （每表单至多一行），其余按 fieldVersion 降序（最新在前）；fieldVersion 草稿行为
 *              null（版本号仅发布动作分配，分册 §3.9 字段表 int → int/null）。
 */
@Data
@Schema(description = "表单版本行响应：不含 Schema 内容；草稿行恒排最前，其余按 fieldVersion 降序；"
        + "fieldVersion 草稿行为 null")
public class SysFormVersionVo {

    /** 版本行 ID（数据行 field_form_version_id 指向对象） */
    @Schema(description = "版本行 ID（数据行 field_form_version_id 指向对象）")
    private String fieldId;

    /** 整数版本号（单调递增 1, 2, 3…；草稿行为 null） */
    @Schema(description = "整数版本号（单调递增 1, 2, 3…）；草稿行为 null（版本号仅发布动作分配）")
    private Integer fieldVersion;

    /** 版本状态：DRAFT（草稿，至多一行）/ PUBLISHED（当前发布）/ DEPRECATED（被新版本替代） */
    @Schema(description = "版本状态：DRAFT（草稿，至多一行）/ PUBLISHED（当前发布）/ "
            + "DEPRECATED（被新版本替代）")
    private String fieldStatus;

    /** 发布/回滚时刻；草稿行为 null */
    @Schema(description = "发布/回滚时刻；草稿行为 null")
    private Date fieldPublishedAt;

    /** 创建者 ID（版本行创建人——草稿行创建者，发布为原地冻结不改创建审计） */
    @Schema(description = "创建者 ID（草稿行创建者；发布为原地冻结不改创建审计）")
    private String fieldCreatorId;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private Date fieldCreateTime;
}

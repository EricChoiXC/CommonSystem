package com.eric.base.sys.form.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 表单数据列表行响应
 *
 * @author Eric
 * @date 2026-11-24
 * @description 对应 API 分册 §4.6 POST /data/list 行字段契约（F15-BE-003）：列表行不携带
 *              formData 字段值内容（JSON 体积不可控，详情经 /load 获取，对齐 Mapper
 *              BaseColumns 轻量列集）；行字段不继承基类、显式声明与分册字段表逐字对齐
 *              （先例 SysFormVo）。归属强制过滤由 Service 层完成（非管理员恒为本人数据
 *              子集，管理员全量或按传入创建人筛选）。
 */
@Data
@Schema(description = "表单数据列表行响应：列表行不携带 formData 字段值内容（详情经 /load）")
public class SysFormDataVo {

    /** 数据行 ID */
    @Schema(description = "数据行 ID")
    private String fieldId;

    /** 表单主档 ID */
    @Schema(description = "表单主档 ID")
    private String fieldFormId;

    /** 实例锁定版本行 ID（原则 2：在途实例用原版本） */
    @Schema(description = "实例锁定版本行 ID（在途实例用原版本，不随主档漂移）")
    private String fieldFormVersionId;

    /** 外部业务对象弱关联 ID（可空） */
    @Schema(description = "外部业务对象弱关联 ID（可空）")
    private String fieldBusinessId;

    /** 创建者 ID（数据归属判定依据） */
    @Schema(description = "创建者 ID（数据归属判定依据）")
    private String fieldCreatorId;

    /** 创建时间（列表默认排序字段） */
    @Schema(description = "创建时间（列表默认排序字段，创建时间降序）")
    private Date fieldCreateTime;
}

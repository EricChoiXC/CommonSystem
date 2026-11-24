package com.eric.base.sys.form.response;

import com.eric.base.sys.form.model.SysFormSchemaDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.Map;

/**
 * 表单数据详情 / 回显响应
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §4.5 GET /data/load?fieldId= 响应契约（F15-BE-003）：
 *              data = SysFormDataDetailVo——数据本体 + 实例锁定版本 Schema（渲染器详情态 /
 *              编辑态复用，原则 2：不随主档当前版本漂移）。
 *              <ul>
 *              <li>formData：字段值对象——读取层裁剪后形态（提交时刻被裁剪的隐藏字段不下发、
 *                  permission = hidden 字段不下发，功能设计 ADR-08 读取层）；读取层裁剪由
 *                  F15-BE-004 FormValidationEngine 并联执行，本任务透传落库形态；</li>
 *              <li>schema：实例锁定版本（field_form_version_id 指向行）的完整 Schema，
 *                  协议头以主档权威元数据归一（先例 SysFormService#normalizeHeader）。</li>
 *              </ul>
 */
@Data
@Schema(description = "表单数据详情响应：数据本体 + 实例锁定版本 Schema（渲染器详情态/编辑态复用）")
public class SysFormDataDetailVo {

    /** 数据行 ID */
    @Schema(description = "数据行 ID")
    private String fieldId;

    /** 表单主档 ID */
    @Schema(description = "表单主档 ID")
    private String fieldFormId;

    /** 实例锁定版本行 ID（原则 2：在途实例用原版本） */
    @Schema(description = "实例锁定版本行 ID（原则 2：在途实例用原版本）")
    private String fieldFormVersionId;

    /** 外部业务对象弱关联 ID（可空） */
    @Schema(description = "外部业务对象弱关联 ID（可空）")
    private String fieldBusinessId;

    /** 创建者 ID（提交人；数据归属判定依据） */
    @Schema(description = "创建者 ID（提交人；数据归属判定依据）")
    private String fieldCreatorId;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private Date fieldCreateTime;

    /** 字段值对象（读取层裁剪后形态；一期透传落库形态，读取层裁剪随 F15-BE-004 并联） */
    @Schema(description = "字段值对象 { 字段码: 值 }——读取层裁剪后形态（提交时刻被裁剪的隐藏"
            + "字段不下发、permission=hidden 字段不下发，ADR-08 读取层）")
    private Map<String, Object> formData;

    /** 实例锁定版本（field_form_version_id 指向行）的 Schema（协议头以主档元数据归一） */
    @Schema(description = "实例锁定版本（field_form_version_id 指向行）的 Schema——渲染器详情态 / "
            + "编辑态复用，不随主档当前版本漂移（原则 2）")
    private SysFormSchemaDo schema;
}

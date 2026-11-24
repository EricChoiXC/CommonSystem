package com.eric.base.sys.form.request;

import com.eric.base.sys.form.model.SysFormSchemaDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新表单请求体
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §3.5 PATCH /define/update 请求契约（F15-BE-002）：只更新元数据
 *              （名称）与草稿 Schema——不含 fieldFormCode（编码为保存后稳定标识，如需调整走
 *              删除重建）、不含状态字段（仅由发布与启停用驱动）。fieldFormName 与 schema 两字段
 *              至少携带一项（全空拒绝）；schema 为草稿 Schema 全量重写（不做差量，分册 §2.4），
 *              只写 DRAFT 版本行——主档已发布且无草稿行时由服务端自动创建草稿行承载本次提交
 *              （「再编辑」入口），PUBLISHED / DEPRECATED 终态行内容永不更新。
 */
@Data
@Schema(description = "更新表单请求体：只写草稿行与名称元数据；fieldFormName 与 schema 至少携带一项，"
        + "编码不可变更（不含 fieldFormCode）")
public class SysFormUpdateRequest {

    /** 表单主档 ID */
    @Schema(description = "表单主档 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldId;

    /** 名称更新（不携带即不更新） */
    @Schema(description = "名称更新；不携带即不更新")
    private String fieldFormName;

    /** 草稿 Schema 全量重写（不携带即不更新） */
    @Schema(description = "草稿 Schema 全量重写（全量替换不做差量）；不携带即不更新；"
            + "协议头以服务端主档元数据为权威归一，version 请求携带一律忽略")
    private SysFormSchemaDo schema;
}

package com.eric.base.sys.form.request;

import com.eric.base.sys.form.model.SysFormSchemaDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 新建表单请求体
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §3.3 POST /define/save 请求契约（F15-BE-002）：fieldId 由前端经
 *              GET /backend/common/initID 预生成（AGENTS §6 前端约束）；schema 为设计器产出的
 *              初始 Schema（可缺省——缺省时服务端以空白草稿 {@code fields:[]} 初始化）。
 *              Schema 顶层协议头 formId / formCode / formName 不信任请求载荷，服务端以本请求体
 *              顶层元数据为权威归一（分册 §2.4）；version 由服务端管理，请求携带一律忽略。
 */
@Data
@Schema(description = "新建表单请求体：fieldId 经 /backend/common/initID 预生成，"
        + "schema 可缺省（缺省时服务端以空白草稿初始化）")
public class SysFormSaveRequest {

    /** 表单主档 ID（前端预生成 32 位 ID，必填） */
    @Schema(description = "表单主档 ID（前端经 /backend/common/initID 预生成 32 位 ID，必填）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldId;

    /** 表单编码（全局唯一，保存期查重；保存后不可变更） */
    @Schema(description = "表单编码，全局唯一，保存期查重（CS_FORM_CODE_DUPLICATED）；"
            + "保存后稳定标识不可变更", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldFormCode;

    /** 表单名称 */
    @Schema(description = "表单名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldFormName;

    /** 初始 Schema（可缺省） */
    @Schema(description = "初始 Schema 协议对象（可缺省）：协议头 formId/formCode/formName 以请求体"
            + "顶层元数据为权威归一，version 请求携带一律忽略")
    private SysFormSchemaDo schema;
}

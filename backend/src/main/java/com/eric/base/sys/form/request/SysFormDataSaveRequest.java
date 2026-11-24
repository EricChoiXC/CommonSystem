package com.eric.base.sys.form.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 保存表单数据请求体
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §4.3 POST /data/save 请求契约（F15-BE-003）：fieldId 由前端经
 *              GET /backend/common/initID 预生成（填写页属新建资源，AGENTS §6 惯例）；
 *              formId 为表单主档 ID，服务端锁定 field_current_version_id 指向版本 Schema
 *              求值（原则 3）；businessId 为外部业务对象弱关联 ID（可缺省，一期不做唯一性
 *              约束）；formData 为字段值载荷 { 字段码: 值 }，明细表字段值为扁平行对象数组
 *              （功能设计 §2.1），不含 formVersionId——一期提交入口恒取当前发布版本，
 *              显式版本提交（v1.8 BPM 按流程绑定版本）随 BPM 适配层扩展请求体时登记。
 */
@Data
@Schema(description = "保存表单数据请求体：formData 为字段值载荷 { 字段码: 值 }，"
        + "明细表字段值为扁平行对象数组；不含 formVersionId（一期恒取当前发布版本）")
public class SysFormDataSaveRequest {

    /** 数据行 ID（前端经 /backend/common/initID 预生成 32 位 ID，必填） */
    @Schema(description = "数据行 ID（前端经 /backend/common/initID 预生成 32 位 ID，必填）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldId;

    /** 表单主档 ID（服务端锁定当前发布版本 Schema 求值，必填） */
    @Schema(description = "表单主档 ID（服务端锁定 field_current_version_id 指向版本 Schema 求值，必填）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String formId;

    /** 外部业务对象弱关联 ID（可缺省；一期不做唯一性约束） */
    @Schema(description = "外部业务对象弱关联 ID（可缺省，一期不做唯一性约束）")
    private String businessId;

    /** 字段值载荷：{ 字段码: 值 }，明细表字段值为扁平行对象数组（必填） */
    @Schema(description = "字段值载荷：{ 字段码: 值 }，明细表字段值为扁平行对象数组（必填）；"
            + "含字段白名单外未知字段拒绝提交（CS_FORM_UNKNOWN_FIELD）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, Object> formData;
}

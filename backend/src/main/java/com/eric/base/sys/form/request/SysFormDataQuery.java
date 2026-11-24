package com.eric.base.sys.form.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 表单数据列表查询条件
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §4.6 POST /data/list 请求 body 契约（F15-BE-003）：数据列表在
 *              单一表单维度（fieldFormId 必填，不存在抛 CS_FORM_NOT_FOUND）；businessId /
 *              processInstanceId 精确匹配（field_process_instance_id 一期不写入，本条件一期
 *              恒空结果，随 v1.8 写入后生效）；创建时间区间筛选由本 Query 请求对象承载
 *              （SysFormDataMapper.xml 注释登记「随 F15-BE-003 落地」，先例
 *              SysProcessExampleQuery.fieldCreateTimeBegin/End）。
 *              <p>注意：本类的 fieldCreatorId 筛选条件仅 SYS_FORM_ADMIN（或 SYS_ADMIN）生效
 *              ——非管理员请求由 Service 层强制覆盖为会话用户（归属强制过滤优先，不信任
 *              请求体传入的用户标识，API 分册 §4.8；Mapper 不读会话）。</p>
 */
@Data
@Schema(description = "表单数据列表查询条件：fieldFormId 必填（单一表单维度）；"
        + "fieldCreatorId 仅管理员生效（非管理员由 Service 强制覆盖为会话用户）")
public class SysFormDataQuery {

    /** 表单主档 ID（必填；数据列表在单一表单维度；不存在 → CS_FORM_NOT_FOUND） */
    @Schema(description = "表单主档 ID（必填，数据列表在单一表单维度）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldFormId;

    /** 外部业务对象弱关联 ID（精确匹配，可缺省） */
    @Schema(description = "外部业务对象弱关联 ID（精确匹配，可缺省）")
    private String fieldBusinessId;

    /** 流程实例 ID（精确匹配；v1.8 预留筛选，一期恒空结果） */
    @Schema(description = "流程实例 ID（精确匹配；v1.8 预留筛选，field_process_instance_id "
            + "一期不写入，本条件一期恒空结果）")
    private String fieldProcessInstanceId;

    /** 创建人筛选（仅 SYS_FORM_ADMIN / SYS_ADMIN 生效；非管理员请求由 Service 强制覆盖） */
    @Schema(description = "创建人筛选（仅 SYS_FORM_ADMIN / SYS_ADMIN 生效；非管理员请求忽略该"
            + "条件并强制过滤为本人数据，归属强制过滤优先）")
    private String fieldCreatorId;

    /** 创建时间起（含） */
    @Schema(description = "创建时间起（含）")
    private Date fieldCreateTimeBegin;

    /** 创建时间止（含） */
    @Schema(description = "创建时间止（含）")
    private Date fieldCreateTimeEnd;
}

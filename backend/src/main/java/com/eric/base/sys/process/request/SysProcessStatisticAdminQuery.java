package com.eric.base.sys.process.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 流程统计（管理员）查询条件
 *
 * @author Eric
 * @date 2026/11/23
 * @description 管理员统计的筛选条件（P17-BE-016，依据 docs/设计文档/sys/process/流程v1_7接口清单.md §7.1）：
 *              所属模块固定、模板固定、流程状态固定、创建时间区间与人员工作量 TopN。
 *              注意：本类不承载任何用户标识字段（越权零泄露口径，与
 *              {@link SysProcessExampleQuery} 一致）——统计对象一律来自会话或全量数据，
 *              请求体不提供按人过滤入口。
 */
@Data
@Schema(description = "管理员统计查询条件：fieldModelName / fieldTemplateId / fieldStatus / fieldCreateTimeBegin / fieldCreateTimeEnd / topN")
public class SysProcessStatisticAdminQuery {

    /** 所属模块（process_template 二级配置项 key，如 sys_org） */
    @Schema(description = "按所属模块过滤（process_template 二级配置项 key，如 sys_org）；空不过滤")
    private String fieldModelName;

    /** 模板 id（sys_process_template.field_id） */
    @Schema(description = "按流程模板过滤（sys_process_template.field_id）；空不过滤")
    private String fieldTemplateId;

    /** 流程状态：ABANDON / DRAFT / PROCESSING / PAUSE / FINISH / RETURN */
    @Schema(description = "按流程状态过滤：ABANDON / DRAFT / PROCESSING / PAUSE / FINISH / RETURN；空不过滤")
    private String fieldStatus;

    /** 创建时间起（含）：统计时间区间按实例创建时间（field_create_time）判定 */
    @Schema(description = "统计时间区间起（含），按流程实例创建时间判定；空不限")
    private Date fieldCreateTimeBegin;

    /** 创建时间止（含）：统计时间区间按实例创建时间（field_create_time）判定 */
    @Schema(description = "统计时间区间止（含），按流程实例创建时间判定；空不限")
    private Date fieldCreateTimeEnd;

    /** 人员工作量 TopN：按已办工作量降序取前 N，默认 10；null 或小于 1 时按默认值处理（服务端归一） */
    @Schema(description = "人员工作量 TopN（按已办工作量降序取前 N），默认 10；null 或小于 1 时按默认值处理",
            defaultValue = "10")
    private Integer topN;
}

package com.eric.base.sys.process.response;

import com.eric.base.sys.process.model.SysProcessStatisticHandlerWorkloadDo;
import com.eric.base.sys.process.model.SysProcessStatisticModelCountDo;
import com.eric.base.sys.process.model.SysProcessStatisticStatusCountDo;
import com.eric.base.sys.process.model.SysProcessStatisticTemplateCountDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 流程统计（管理员）视图对象
 *
 * @author Eric
 * @date 2026/11/23
 * @description 管理员统计响应（P17-BE-016，依据 docs/设计文档/sys/process/流程v1_7接口清单.md §7.1）：
 *              区间内实例总数、按状态/模块/模板的数量分布、平均办结耗时（只计 FINISH 实例，
 *              自然时长 field_finish_time - field_create_time，Q-05 定稿口径）与
 *              人员工作量 TopN；全部为只读聚合，不侵入流转 SQL。
 */
@Data
@Schema(description = "管理员统计：totalCount / statusCounts / modelCounts / templateCounts / avgDurationMs / handlerWorkloads")
public class SysProcessStatisticAdminVo {

    /** 区间内流程实例总数（随统计筛选条件生效） */
    @Schema(description = "区间内流程实例总数（随统计筛选条件生效）")
    private long totalCount;

    /** 按状态的数量分布 */
    @Schema(description = "按状态的数量分布")
    private List<SysProcessStatisticStatusCountDo> statusCounts;

    /** 按所属模块的数量分布 */
    @Schema(description = "按所属模块的数量分布")
    private List<SysProcessStatisticModelCountDo> modelCounts;

    /** 按流程模板的数量分布（含模板名称） */
    @Schema(description = "按流程模板的数量分布（含模板名称）")
    private List<SysProcessStatisticTemplateCountDo> templateCounts;

    /** 平均办结耗时（毫秒，自然时长 field_finish_time - field_create_time，只计已 FINISH 实例；无 FINISH 实例为 0） */
    @Schema(description = "平均办结耗时（毫秒，自然时长 field_finish_time - field_create_time，只计已 FINISH 实例；无 FINISH 实例为 0）")
    private long avgDurationMs;

    /** 人员工作量 TopN（按已办工作量降序取前 N，默认 10） */
    @Schema(description = "人员工作量 TopN（按已办工作量降序取前 N，默认 10）")
    private List<SysProcessStatisticHandlerWorkloadDo> handlerWorkloads;
}

package com.eric.base.sys.process.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程统计-人员工作量聚合行
 *
 * @author Eric
 * @date 2026/11/23
 * @description 管理员统计 handlerWorkloads 数组的固定结构（P17-BE-016，依据
 *              docs/设计文档/sys/process/流程v1_7接口清单.md §7.1）：
 *              已办工作量口径 = HANDLER_PASS 审批留痕关联工作项的实际处理人
 *              （field_expect_handler_id 优先、回退 field_handler_id，与办结待阅
 *              finishedHandlerIds 同口径）；handledCount 为通过（HANDLER_PASS）留痕条数，
 *              avgDurationMs 为该处理人经办的已办结（FINISH）实例平均时长（毫秒）。
 */
@Data
@Schema(description = "流程统计-人员工作量：fieldHandlerId / fieldHandlerName / handledCount / avgDurationMs")
public class SysProcessStatisticHandlerWorkloadDo {

    /** 处理人 id（HANDLER_PASS 留痕关联工作项的实际处理人：expect_handler_id 优先回退 handler_id） */
    @Schema(description = "处理人 id（HANDLER_PASS 留痕关联工作项的实际处理人，expect_handler_id 优先回退 handler_id）")
    private String fieldHandlerId;

    /** 处理人名称（sys_org_element 只读关联补全，人员缺失时为 null） */
    @Schema(description = "处理人名称（sys_org_element 只读关联补全，人员缺失时为 null）")
    private String fieldHandlerName;

    /** 已办工作量：通过（HANDLER_PASS）审批留痕条数（同一工作项收回后再审批按多次计） */
    @Schema(description = "已办工作量：通过（HANDLER_PASS）审批留痕条数（同一工作项收回后再审批按多次计）")
    private long handledCount;

    /** 该处理人经办的已办结（FINISH）实例平均办结耗时（毫秒，自然时长 field_finish_time - field_create_time；无办结实例为 0） */
    @Schema(description = "该处理人经办的已办结（FINISH）实例平均办结耗时（毫秒，自然时长；无办结实例为 0）")
    private long avgDurationMs;
}

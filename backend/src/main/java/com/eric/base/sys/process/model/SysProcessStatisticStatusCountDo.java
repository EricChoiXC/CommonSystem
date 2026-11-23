package com.eric.base.sys.process.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程统计-按状态数量聚合行
 *
 * @author Eric
 * @date 2026/11/23
 * @description 管理员统计 statusCounts 数组的固定结构（P17-BE-016，依据
 *              docs/设计文档/sys/process/流程v1_7接口清单.md §7.1）：按流程状态分组的
 *              实例数量，随统计筛选条件（模块/模板/状态/创建时间区间）生效。
 */
@Data
@Schema(description = "流程统计-按状态数量：fieldStatus / count")
public class SysProcessStatisticStatusCountDo {

    /** 流程状态：ABANDON / DRAFT / PROCESSING / PAUSE / FINISH / RETURN */
    @Schema(description = "流程状态：ABANDON / DRAFT / PROCESSING / PAUSE / FINISH / RETURN")
    private String fieldStatus;

    /** 该状态实例数量 */
    @Schema(description = "该状态的流程实例数量")
    private long count;
}

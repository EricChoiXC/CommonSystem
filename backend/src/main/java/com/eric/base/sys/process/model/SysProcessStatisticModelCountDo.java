package com.eric.base.sys.process.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程统计-按模块数量聚合行
 *
 * @author Eric
 * @date 2026/11/23
 * @description 管理员统计 modelCounts 数组的固定结构（P17-BE-016，依据
 *              docs/设计文档/sys/process/流程v1_7接口清单.md §7.1）：按所属模块
 *              （process_template 二级配置项 key）分组的实例数量，随统计筛选条件生效。
 */
@Data
@Schema(description = "流程统计-按模块数量：fieldModelName / count")
public class SysProcessStatisticModelCountDo {

    /** 所属模块（process_template 二级配置项 key，如 sys_org） */
    @Schema(description = "所属模块（process_template 二级配置项 key，如 sys_org）")
    private String fieldModelName;

    /** 该模块实例数量 */
    @Schema(description = "该所属模块的流程实例数量")
    private long count;
}

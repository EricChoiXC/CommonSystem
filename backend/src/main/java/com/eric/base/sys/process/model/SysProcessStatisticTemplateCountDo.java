package com.eric.base.sys.process.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程统计-按模板数量聚合行
 *
 * @author Eric
 * @date 2026/11/23
 * @description 管理员统计 templateCounts 数组的固定结构（P17-BE-016，依据
 *              docs/设计文档/sys/process/流程v1_7接口清单.md §7.1）：按流程模板分组的
 *              实例数量，模板名称经 sys_process_template 只读关联补全（模板被删时名称为 null）。
 */
@Data
@Schema(description = "流程统计-按模板数量：fieldTemplateId / fieldTemplateName / count")
public class SysProcessStatisticTemplateCountDo {

    /** 模板 id（sys_process_template.field_id） */
    @Schema(description = "模板 id（sys_process_template.field_id）")
    private String fieldTemplateId;

    /** 模板名称（sys_process_template.field_name 只读关联补全，模板缺失时为 null） */
    @Schema(description = "模板名称（sys_process_template 只读关联补全，模板缺失时为 null）")
    private String fieldTemplateName;

    /** 该模板实例数量 */
    @Schema(description = "该流程模板的流程实例数量")
    private long count;
}

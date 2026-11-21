package com.eric.base.sys.process.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 流程实例详情视图对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 流程实例详情页的完整数据：流程实例基本字段 + 流程实例节点 + 工作项 + 审批记录
 *              + 实例连线快照（依据 docs/设计文档/sys/process/流程核心业务.md「流程实例详情」；
 *              v1_7 起补透实例连线快照 lines，供实例详情页只读流程图渲染，
 *              依据 docs/设计文档/sys/process/流程v1_7接口清单.md §5.3）。
 */
@Data
public class SysProcessExampleDetailVo {

    /** 流程实例基本字段 */
    private SysProcessExampleVo example;

    /** 流程实例节点（按节点编号升序） */
    private List<SysProcessExampleNodeVo> nodes;

    /** 工作项（按创建时间升序） */
    private List<SysProcessExampleWorkitemVo> workitems;

    /** 审批记录（按创建时间升序） */
    private List<SysProcessAuditNoteVo> auditNotes;

    /** 实例连线快照（v1_7 新增，P17-FE-005 契约补齐，供只读流程图渲染） */
    @Schema(description = "实例连线快照（v1_7 新增，字段同模板 lines[] 但 fieldExampleId 替代 fieldTemplateId），供只读流程图渲染")
    private List<SysProcessExampleLineVo> lines;
}

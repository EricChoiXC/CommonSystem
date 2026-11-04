package com.common.system.sys.process.response;

import lombok.Data;

import java.util.List;

/**
 * 流程实例详情视图对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 流程实例详情页的完整数据：流程实例基本字段 + 流程实例节点 + 工作项 + 审批记录
 *              （依据 artificial/docs/设计文档/sys/process/流程核心业务.md「流程实例详情」）。
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
}

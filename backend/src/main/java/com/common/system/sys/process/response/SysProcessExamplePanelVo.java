package com.common.system.sys.process.response;

import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 流程审批面板视图对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 可嵌入审批组件的面板数据（依据 artificial/docs/设计文档/sys/process/
 *              流程核心业务.md「流程审批面板」）：流程基本信息 + 节点 + 审批记录 +
 *              当前用户可执行的审批操作列表（面板仅做展示与表单收集，不直接调接口，
 *              由各业务文档页提交给各自模块后端，再调用流程模块服务）。
 */
@Data
public class SysProcessExamplePanelVo {

    /** 流程实例基本字段 */
    private SysProcessExampleVo example;

    /** 流程节点（按节点编号升序） */
    private List<SysProcessExampleNodeVo> nodes;

    /** 审批记录（按创建时间升序） */
    private List<SysProcessAuditNoteVo> auditNotes;

    /** 当前用户可执行的审批操作列表（为空则审批卡片不可见） */
    private List<String> operations;

    /** 当前用户在当前节点待处理工作项（审批操作提交时回传 workitemId） */
    private List<SysProcessExampleWorkitemVo> myWorkitems;

    /**
     * 当前用户在该流程「当前节点」可操作的操作标识集合（节点配置 ops 中开启的 key；
     * 非审批中或无待处理工作项时为空集，节点未配置 ops 默认不允许）。
     * 供业务文档页按操作标识控制表单字段/附件的可编辑性。
     */
    private Set<String> nodeOps;
}

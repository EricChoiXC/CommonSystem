package com.eric.base.sys.process.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 流程审批面板视图对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 可嵌入审批组件的面板数据（依据 docs/设计文档/sys/process/
 *              流程核心业务.md「流程审批面板」）：流程基本信息 + 节点 + 审批记录 +
 *              当前用户可执行的审批操作按钮集（面板仅做展示与表单收集，不直接调接口，
 *              由各业务文档页提交给各自模块后端，再调用流程模块服务）。
 *              v1_7 起 operations 升级为按钮对象数组（P17-BE-013，
 *              依据 docs/设计文档/sys/process/流程v1_7接口清单.md §5.4）：
 *              按节点 operations 配置 ∩ 用户身份 ∩ 状态前置计算，
 *              旧操作标识语义由元素 operateType 承载，不新增并列字段。
 */
@Data
public class SysProcessExamplePanelVo {

    /** 流程实例基本字段 */
    private SysProcessExampleVo example;

    /** 流程节点（按节点编号升序） */
    private List<SysProcessExampleNodeVo> nodes;

    /** 审批记录（按创建时间升序） */
    private List<SysProcessAuditNoteVo> auditNotes;

    /**
     * 当前用户可执行的审批操作按钮集（v1_7 升级为按钮对象数组：
     * operateType / operateName / defaultContent / rejectTargets / flowChoices，见
     * {@link SysProcessPanelOperationVo}；为空则审批卡片不可见。
     * 面板仅做展示预判，最终以操作时引擎身份校验为准）。
     */
    private List<SysProcessPanelOperationVo> operations;

    /** 当前用户在当前节点待处理工作项（审批操作提交时回传 workitemId） */
    private List<SysProcessExampleWorkitemVo> myWorkitems;

    /**
     * 当前用户在该流程「当前节点」可操作的操作标识集合（节点配置 ops 中开启的 key；
     * 非审批中或无待处理工作项时为空集，节点未配置 ops 默认不允许）。
     * 供业务文档页按操作标识控制表单字段/附件的可编辑性。
     */
    private Set<String> nodeOps;

    /** 实例连线快照（v1_7 新增，P17-FE-005 契约补齐，与 /load 同源快照） */
    @Schema(description = "实例连线快照（v1_7 新增，与 /load 同源快照），供面板「流程」卡片渲染只读流程图")
    private List<SysProcessExampleLineVo> lines;
}

package com.eric.base.sys.process.task.model;

import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import lombok.Data;

import java.util.Map;

/**
 * 流程任务执行上下文
 *
 * @author Eric
 * @date 2026/11/19
 * @description 流程任务执行入参（依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md §7.1）：
 *              业务单据经 {@code SysProcessDocumentFieldService} 按其 Mapper 索引读取；
 *              节点/工作项在流程级及节点级步骤可为空。
 */
@Data
public class ProcessTaskContext {

    /** 业务单据（按模板分类的文档实体类 + 实例 field_model_id 读取；配置缺失或未落库时为空） */
    private Object model;

    /** 流程实例 */
    private SysProcessExample process;

    /** 当前节点（可空：CreateProcess/StartProcess/EndProcess 等流程级步骤） */
    private SysProcessExampleNode node;

    /** 当前工作项（可空：节点级/流程级任务） */
    private SysProcessExampleWorkitem workitem;

    /** 任务参数（节点配置 tasks[].params 原样透传；可空） */
    private Map<String, Object> params;
}

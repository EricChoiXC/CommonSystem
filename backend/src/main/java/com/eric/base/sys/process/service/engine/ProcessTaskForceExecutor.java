package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.task.enums.ProcessTaskType;

/**
 * 流程强制任务执行器（引擎可选窄接口）
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-005 定义：{@link ProcessFlowEngine} 在 StartProcess / EndProcess 步骤
 *              触发「强制任务」（{@code @ProcessTask(isForce=true)}，对所有实例生效、不受节点配置约束）时，
 *              除窄接口 {@link ProcessTaskExecutor} 外还需本接口能力；实现类
 *              {@code com.eric.base.sys.process.task.ProcessTaskExecutorImpl} 同时实现两者。
 *              引擎仅按 {@code instanceof} 探测本接口，未实现时按「无强制任务」处理，
 *              不改变 ROBORT 节点既有行为与 {@link ProcessTaskExecutor} 缺省兜底语义。
 */
public interface ProcessTaskForceExecutor {

    /**
     * 执行指定步骤的强制任务。
     *
     * @param exampleId 流程实例 id
     * @param nodeId    当前节点 id（流程级步骤可空）
     * @param workitem  当前工作项（可空）
     * @param taskType  执行步骤（仅 StartProcess / EndProcess 有强制任务）
     */
    void executeForceTasks(String exampleId, String nodeId, SysProcessExampleWorkitem workitem,
                           ProcessTaskType taskType);
}

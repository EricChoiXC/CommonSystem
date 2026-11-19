package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo;

/**
 * 流程任务执行器（窄接口）
 *
 * @author Eric
 * @date 2026/11/19
 * @description <b>实现由 P17-BE-005 提供</b>（@ProcessTask 注册调度与任务日志留痕）。
 *              P17-BE-003 定义本窄接口并由 {@link ProcessFlowEngine} 构造注入调用：
 *              ROBORT 节点进入时按节点配置的 {@code tasks} 明细顺序执行，全部成功后节点 FINISH
 *              并沿出边继续；任一任务失败则转流程异常（实例 PAUSE + SYSTEM_ERROR 留痕）。
 *              <p>无实现 bean 时（P17-BE-005 未交付期间）引擎按「无任务则直接 FINISH」处理，
 *              不影响 ROBORT 节点在图上的流转等价性。</p>
 */
public interface ProcessTaskExecutor {

    /**
     * 执行节点配置的单个流程任务。
     * <p>实现方负责写 {@code sys_process_task_log} 流水（成功/失败均留痕，决策 D7：
     * 审批记录不承载任务留痕）；失败时抛异常，由引擎转流程异常。</p>
     *
     * @param request 任务执行请求（实例、节点、任务明细）
     * @throws ProcessFlowException 任务执行失败（错误码 {@link ProcessFlowException#CODE_TASK_FAILED}）
     */
    void execute(ProcessTaskRequest request);

    /**
     * 流程任务执行请求。
     *
     * @param exampleId  流程实例 id
     * @param nodeId     流程实例节点 id
     * @param nodeName   节点名称（异常文案用）
     * @param workitem   当前工作项（可空：流程级/节点级步骤无工作项；工作项步骤携带，供上下文组装）
     * @param taskDetail 节点配置的任务明细（任务名称/执行任务/执行步骤/任务参数）
     */
    record ProcessTaskRequest(String exampleId, String nodeId, String nodeName,
                              SysProcessExampleWorkitem workitem,
                              SysProcessTemplateNodeConfigDo.TaskDetail taskDetail) {
    }
}

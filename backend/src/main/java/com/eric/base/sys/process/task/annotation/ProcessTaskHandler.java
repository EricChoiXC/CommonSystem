package com.eric.base.sys.process.task.annotation;

import com.eric.base.sys.process.task.model.ProcessTaskContext;

/**
 * 流程任务执行器接口
 *
 * @author Eric
 * @date 2026/11/19
 * @description 流程任务类必须实现本接口的 {@link #execute(ProcessTaskContext)} 方法，
 *              该方法为流程任务执行的调用方法（流转事务内同步执行）。
 *              配合 {@link ProcessTask} 注解使用：系统启动时扫描带注解且实现本接口的 Spring Bean，
 *              按注解 {@code name} 登记到 {@code ProcessTaskRegistry}。
 *              <p>命名落定：设计文档《改造分析与方案设计》§7.1 初稿把注解与接口都命名为
 *              {@code ProcessTask}，Java 不允许同包同名；本接口按仓库先例
 *              {@code sys/task/annotation/SysTask.java}（注解）+ {@code SysTaskExecutor.java}（接口）
 *              落定为 {@code ProcessTaskHandler}（注解保持 {@code @ProcessTask}）。</p>
 */
public interface ProcessTaskHandler {

    /**
     * 流程任务执行方法（在流程流转事务内同步执行，落 sys_process_task_log 留痕）。
     *
     * @param context 流程任务上下文（业务单据、流程实例、节点、工作项、任务参数）
     */
    void execute(ProcessTaskContext context);
}

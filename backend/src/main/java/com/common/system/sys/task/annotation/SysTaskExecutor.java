package com.common.system.sys.task.annotation;

import java.util.Map;
import java.util.Queue;

/**
 * 定时任务执行器接口
 *
 * @author Eric
 * @date 2026/8/15
 * @description 定时任务类必须实现本接口的 {@link #execute(Map, Queue)} 方法，
 *              该方法为定时任务执行的调用方法：入参为配置参数和其值（Map），
 *              以及将保存到 sys_task_history.field_log 的日志记录队列。
 *              配合 {@link SysTask} 注解使用：系统启动时扫描带注解且实现本接口的
 *              Spring Bean，注册到 sys_task_main 并纳入动态调度。
 */
public interface SysTaskExecutor {

    /**
     * 定时任务执行方法
     *
     * @param params 配置参数和其值（编辑页面维护，保存为 JSON，值均为字符串）
     * @param logs   日志记录队列，任务执行过程中向队列追加文本，
     *               执行结束后统一写入 sys_task_history.field_log
     */
    void execute(Map<String, String> params, Queue<String> logs);
}

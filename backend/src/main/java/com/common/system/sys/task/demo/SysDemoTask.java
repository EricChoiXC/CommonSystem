package com.common.system.sys.task.demo;

import com.common.system.sys.task.annotation.SysTask;
import com.common.system.sys.task.annotation.SysTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Queue;

/**
 * 演示定时任务
 *
 * @author Eric
 * @date 2026/8/15
 * @description 定时任务接入示例：类上标注 {@link SysTask}（name/cron 必填，params 为
 *              "key=value" 字符串列表，默认空），并实现 {@link SysTaskExecutor#execute}。
 *              系统启动时被扫描写入 sys_task_main（默认开启），可在「定时任务管理」页面
 *              编辑 cron / 参数、手动执行、中止当前任务；本任务 sleep 3 秒模拟耗时，
 *              支持中断（中止时线程被 interrupt，任务捕获后按失败记录）。
 */
@Component
@SysTask(name = "演示任务", cron = "0 0 3 * * ?", params = {"demoKey=demoValue"})
public class SysDemoTask implements SysTaskExecutor {

    @Override
    public void execute(Map<String, String> params, Queue<String> logs) {
        logs.add("演示任务开始执行，params=" + params);
        try {
            // 模拟耗时任务：休眠 3 秒，可被「中止当前任务」中断
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            logs.add("演示任务被中断: " + e.getMessage());
            Thread.currentThread().interrupt();
            return;
        }
        logs.add("演示任务执行完成");
    }
}

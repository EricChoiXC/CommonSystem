package com.common.system.sys.task.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定时任务注解
 *
 * @author Eric
 * @date 2026/8/15
 * @description 标注在定时任务类上，表示该类是一个定时任务类；被标注的类必须同时实现
 *              {@link SysTaskExecutor}（execute(Map, Queue) 为任务执行调用方法）。
 *              系统启动时扫描所有带本注解的 Spring Bean，将任务元信息写入
 *              sys_task_main 表（默认开启），运行时按库中维护的 cron 动态调度。
 *              注解内 name、cron 必填，params 默认为空列表（"key=value" 字符串列表）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SysTask {

    /** 任务名称 */
    String name();

    /** cron 表达式（系统启动扫描写入后，以 sys_task_main 表中维护的为准） */
    String cron();

    /** 填写参数列表（"key=value" 字符串列表，默认空） */
    String[] params() default {};
}

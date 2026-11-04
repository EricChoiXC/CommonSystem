package com.common.system.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;

/**
 * 异步与调度配置
 *
 * @author Eric
 * @date 2026/8/8
 * @description 开启 {@code @Async} 与 {@code @Scheduled}：
 *              <ul>
 *              <li>日志模块依赖异步线程池，实现「日志落库与主业务事务隔离、日志写入失败不影响主流程」；</li>
 *              <li>调度线程池供日志批量落库定时 flush 使用。</li>
 *              <li>SSE 推送线程池供通知待办模块实时推送使用，与主业务线程隔离。</li>
 *              </ul>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * 通用异步线程池：供日志异步写入等低优先级任务使用
     */
    @Bean(name = "logAsyncExecutor")
    public Executor logAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(5000);
        executor.setThreadNamePrefix("log-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 调度线程池：供 {@code @Scheduled} 定时任务（日志批量 flush）使用
     */
    @Bean(name = "logSchedulingExecutor")
    public Executor logSchedulingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("log-schedule-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 统一调度器：服务 {@code @Scheduled} 注解任务与定时任务模块的动态调度。
     * <p>命名为 taskScheduler，覆盖 Spring Boot 自动配置的同名 Bean，
     * 供 {@code @EnableScheduling} 默认使用；定时任务模块经 {@code CronTrigger}
     * 动态注册/取消调度，亦复用本调度器（仅负责触发，任务体在 sysTaskExecutor 执行）。</p>
     */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("sys-task-schedule-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 定时任务执行线程池：承载定时任务模块任务体（execute）的执行，
     * 与调度线程解耦，避免长任务阻塞调度触发；执行线程可被 interrupt 以支持「中止当前任务」。
     */
    @Bean(name = "sysTaskExecutor")
    public Executor sysTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("sys-task-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * SSE 推送线程池：供通知待办模块 {@link com.common.system.sys.notify.service.listeners.NotifySseEventListener}
     * 异步推送 SSE 事件使用，与主业务线程隔离，推送失败不影响主流程。
     */
    @Bean(name = "notifySseExecutor")
    public Executor notifySseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notify-sse-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}

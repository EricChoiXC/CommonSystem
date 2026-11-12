package com.eric.base.sys.task.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.task.annotation.SysTask;
import com.eric.base.sys.task.annotation.SysTaskExecutor;
import com.eric.base.sys.task.dao.SysTaskExampleDao;
import com.eric.base.sys.task.dao.SysTaskHistoryDao;
import com.eric.base.sys.task.dao.SysTaskMainDao;
import com.eric.base.sys.task.enums.SysTaskResult;
import com.eric.base.sys.task.enums.SysTaskStatus;
import com.eric.base.sys.task.model.SysTaskExample;
import com.eric.base.sys.task.model.SysTaskHistory;
import com.eric.base.sys.task.model.SysTaskMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务调度服务
 *
 * @author Eric
 * @date 2026/8/15
 * @description 定时任务模块调度核心，承载：
 *              <ul>
 *              <li>系统启动时扫描带 {@link SysTask} 注解的 Spring Bean，将任务元信息写入 sys_task_main（默认开启）</li>
 *              <li>按 sys_task_main 中维护的 cron 动态注册/取消调度（启用任务）</li>
 *              <li>任务触发执行：登记 sys_task_example 实例（一个任务同时只执行一个实例，冲突记为 BLOCK）、
 *                  更新主表状态、提交到执行线程池运行、结束后登记 sys_task_history 并回写状态</li>
 *              <li>中止当前任务：中断执行线程，任务按「失败」结果记录</li>
 *              </ul>
 *              调度线程与执行线程解耦：调度器只负责触发，任务体在 sysTaskExecutor 线程池中运行。
 */
@Service
public class SysTaskSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SysTaskSchedulerService.class);

    private final SysTaskMainDao mainDao;
    private final SysTaskExampleDao exampleDao;
    private final SysTaskHistoryDao historyDao;
    private final ApplicationContext applicationContext;
    private final TaskScheduler taskScheduler;
    private final Executor taskExecutor;
    private final ObjectMapper objectMapper;

    /** taskId -> 已注册的调度句柄 */
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    /** taskId -> 正在运行的实例（保证一个任务同时只执行一个实例） */
    private final Map<String, RunningInstance> running = new ConcurrentHashMap<>();

    public SysTaskSchedulerService(SysTaskMainDao mainDao,
                                   SysTaskExampleDao exampleDao,
                                   SysTaskHistoryDao historyDao,
                                   ApplicationContext applicationContext,
                                   ThreadPoolTaskScheduler taskScheduler,
                                   Executor sysTaskExecutor,
                                   ObjectMapper objectMapper) {
        this.mainDao = mainDao;
        this.exampleDao = exampleDao;
        this.historyDao = historyDao;
        this.applicationContext = applicationContext;
        this.taskScheduler = taskScheduler;
        this.taskExecutor = sysTaskExecutor;
        this.objectMapper = objectMapper;
    }

    /* ========== 启动扫描与整体调度 ========== */

    /**
     * 应用就绪：扫描带 {@link SysTask} 注解的任务类写入 sys_task_main（默认开启），
     * 重置进程重启遗留的 RUNNING 状态，并对所有启用任务动态注册 cron 调度。
     */
    public void onApplicationReady() {
        scanAndRegisterTasks();
        resetStaleRunningStatus();
        rescheduleAll();
    }

    /**
     * 启动时将上次进程遗留的 RUNNING 状态重置为 FAIL：
     * 进程重启后未完成的任务实例已不存在，避免主表 / 实例表状态永久卡在运行中。
     */
    private void resetStaleRunningStatus() {
        SysTaskMain mainCriteria = new SysTaskMain();
        mainCriteria.setFieldStatus(SysTaskStatus.RUNNING.getValue());
        List<SysTaskMain> runningTasks = mainDao.selectList(mainCriteria, null);
        for (SysTaskMain main : runningTasks) {
            SysTaskMain update = new SysTaskMain();
            update.setFieldId(main.getFieldId());
            update.setFieldStatus(SysTaskStatus.FAIL.getValue());
            mainDao.update(update);
            log.warn("task startup: reset stale RUNNING status to FAIL (id={}, name={})", main.getFieldId(), main.getFieldName());
        }

        SysTaskExample exampleCriteria = new SysTaskExample();
        exampleCriteria.setFieldStatus(SysTaskStatus.RUNNING.getValue());
        List<SysTaskExample> staleExamples = exampleDao.selectList(exampleCriteria, null);
        for (SysTaskExample example : staleExamples) {
            SysTaskExample update = new SysTaskExample();
            update.setFieldId(example.getFieldId());
            update.setFieldStatus(SysTaskStatus.FAIL.getValue());
            exampleDao.update(update);
        }
        if (!runningTasks.isEmpty() || !staleExamples.isEmpty()) {
            log.warn("task startup: reset {} stale task(s), {} stale example(s) to FAIL",
                    runningTasks.size(), staleExamples.size());
        }
    }

    /**
     * 扫描容器中所有带 {@link SysTask} 注解且实现 {@link SysTaskExecutor} 的 Bean：
     * 按任务类名（field_class）匹配，已登记的任务保留管理员维护的内容（名称/cron/参数/启停用），
     * 未登记的任务写入 sys_task_main，默认开启。
     */
    private void scanAndRegisterTasks() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(SysTask.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            if (!(bean instanceof SysTaskExecutor)) {
                log.warn("task scan: bean {} has @SysTask but does not implement SysTaskExecutor, skip", entry.getKey());
                continue;
            }
            SysTask annotation = findAnnotation(bean.getClass(), SysTask.class);
            if (null == annotation) {
                log.warn("task scan: cannot resolve @SysTask on bean {}, skip", entry.getKey());
                continue;
            }
            String className = ClassUtils.getUserClass(bean.getClass()).getName();
            if (null != mainDao.selectByClass(className)) {
                log.info("task scan: task already registered, keep admin maintained config (class={})", className);
                continue;
            }
            SysTaskMain main = new SysTaskMain();
            main.setFieldName(annotation.name());
            main.setFieldCron(annotation.cron());
            main.setFieldClass(className);
            main.setFieldEnable(true);
            main.setFieldStatus(SysTaskStatus.WAITING.getValue());
            main.setFieldParams(paramsToJson(annotation.params()));
            main.setFieldCreateTime(new Date());
            // 直接走 Dao insert（不经 BaseService.insert），需自行初始化主键 ID
            main.initFieldId();
            mainDao.insert(main);
            log.info("task scan: registered task (name={}, class={}, cron={})", annotation.name(), className, annotation.cron());
        }
    }

    /**
     * 对 sys_task_main 全部记录按当前启用状态与 cron 重新调度（启动时调用）。
     */
    public synchronized void rescheduleAll() {
        List<SysTaskMain> all = mainDao.selectList(new SysTaskMain(), null);
        for (SysTaskMain main : all) {
            schedule(main.getFieldId());
        }
    }

    /* ========== 动态调度 ========== */

    /**
     * 注册/刷新单个任务的 cron 调度：先取消旧调度，再按「是否启用 + cron 合法性」决定是否注册。
     * 编辑 cron 或启停用后调用。
     */
    public synchronized void schedule(String taskId) {
        cancel(taskId);
        SysTaskMain main = mainDao.selectById(taskId);
        if (null == main) {
            return;
        }
        if (!Boolean.TRUE.equals(main.getFieldEnable())) {
            log.info("task schedule: task disabled, skip (id={})", taskId);
            return;
        }
        Trigger trigger;
        try {
            trigger = createTrigger(main.getFieldCron());
        } catch (IllegalArgumentException e) {
            // cron 非法：仅告警，不注册（编辑接口已拦截非法 cron，此处兜底历史数据）
            log.warn("task schedule: invalid cron, skip (id={}, cron={}): {}", taskId, main.getFieldCron(), e.getMessage());
            return;
        }
        ScheduledFuture<?> future = taskScheduler.schedule(() -> execute(taskId), trigger);
        futures.put(taskId, future);
        log.info("task schedule: registered (id={}, name={}, cron={})", taskId, main.getFieldName(), main.getFieldCron());
    }

    /**
     * 取消单个任务的调度（停用或删除时调用）。
     */
    public synchronized void cancel(String taskId) {
        ScheduledFuture<?> future = futures.remove(taskId);
        if (null != future) {
            future.cancel(false);
            log.info("task schedule: cancelled (id={})", taskId);
        }
    }

    /* ========== 任务执行 ========== */

    /**
     * 任务触发入口（cron 调度调用）：抢占运行位启动任务；
     * 已有实例在运行则本次触发登记一条 BLOCK 实例（阻塞），不重复执行。
     */
    public void execute(String taskId) {
        if (!tryStart(taskId)) {
            recordBlocked(taskId);
        }
    }

    /**
     * 手动执行：cron 调度之外立即执行一次；任务已在运行则拒绝（不记 BLOCK）。
     */
    public void executeNow(String taskId) {
        SysTaskMain main = mainDao.selectById(taskId);
        if (null == main) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        if (!tryStart(taskId)) {
            throw new IllegalStateException("任务正在运行中，请勿重复执行");
        }
    }

    /**
     * 抢占运行位并启动任务：原子写入 running（putIfAbsent）保证一个任务同时只执行一个实例；
     * 抢占成功则登记执行实例、更新主表状态并提交到执行线程池；失败返回 false。
     */
    private boolean tryStart(String taskId) {
        SysTaskMain main = mainDao.selectById(taskId);
        if (null == main) {
            log.warn("task execute: task not found, skip (id={})", taskId);
            return false;
        }
        String group = newGroup();
        Date startTime = new Date();
        RunningInstance instance = new RunningInstance(taskId, group, startTime);
        RunningInstance previous = running.putIfAbsent(taskId, instance);
        if (null != previous) {
            return false;
        }
        SysTaskExample example = new SysTaskExample();
        example.setFieldStatus(SysTaskStatus.RUNNING.getValue());
        example.setFieldStartTime(startTime);
        example.setFieldGroup(group);
        // 直接走 Dao insert（不经 BaseService.insert），需自行初始化主键 ID
        example.initFieldId();
        exampleDao.insert(example);
        instance.exampleId = example.getFieldId();

        updateMainStatus(taskId, SysTaskStatus.RUNNING.getValue());
        Map<String, String> params = parseParams(main.getFieldParams());
        taskExecutor.execute(() -> runTask(main, instance, params));
        log.info("task execute: started (id={}, name={}, group={})", taskId, main.getFieldName(), group);
        return true;
    }

    /**
     * 中止当前任务实例：中断执行线程并标记中止，任务结束后按「失败」结果记录。
     */
    public void stop(String taskId) {
        RunningInstance instance = running.get(taskId);
        if (null == instance) {
            throw new IllegalStateException("任务当前没有运行中的实例");
        }
        instance.stopping = true;
        Thread thread = instance.thread;
        if (null != thread) {
            thread.interrupt();
        }
        log.info("task stop: stop requested (id={}, group={})", taskId, instance.group);
    }

    /**
     * 运行任务体（执行线程池中调用）：调用任务类的 execute 方法收集日志，
     * 结束后按结果登记 sys_task_example 状态、sys_task_history 流水并回写主表状态。
     */
    private void runTask(SysTaskMain main, RunningInstance instance, Map<String, String> params) {
        Queue<String> logs = new ConcurrentLinkedQueue<>();
        boolean failed = false;
        instance.thread = Thread.currentThread();
        try {
            Object bean = applicationContext.getBean(Class.forName(main.getFieldClass()));
            if (bean instanceof SysTaskExecutor executor) {
                executor.execute(params, logs);
            } else {
                failed = true;
                logs.add("任务类未实现 SysTaskExecutor 或不是 Spring Bean: " + main.getFieldClass());
            }
        } catch (Exception e) {
            failed = true;
            if (e instanceof InterruptedException) {
                // 被中止（或任务自身中断）：按失败记录
                logs.add("任务被中止: " + e.getMessage());
                Thread.currentThread().interrupt();
            } else {
                logs.add("任务执行异常: " + e);
            }
        } finally {
            // 任务被管理员中止但未抛中断异常（忽略中断返回）时，仍按失败记录
            if (instance.stopping) {
                failed = true;
                logs.add("任务被管理员中止");
            }
            finish(main, instance, logs, failed);
        }
    }

    /**
     * 执行收尾：更新实例状态、写入执行历史、回写主表状态，并释放运行位。
     */
    private void finish(SysTaskMain main, RunningInstance instance, Queue<String> logs, boolean failed) {
        try {
            Date endTime = new Date();
            String result = failed ? SysTaskResult.FAIL.getValue() : SysTaskResult.SUCCESS.getValue();

            SysTaskExample exampleUpdate = new SysTaskExample();
            exampleUpdate.setFieldId(instance.exampleId);
            exampleUpdate.setFieldStatus(failed ? SysTaskStatus.FAIL.getValue() : SysTaskStatus.FINISH.getValue());
            exampleDao.update(exampleUpdate);

            SysTaskHistory history = new SysTaskHistory();
            history.setFieldTaskId(main.getFieldId());
            history.setFieldResult(result);
            history.setFieldStartTime(instance.startTime);
            history.setFieldEndTime(endTime);
            history.setFieldGroup(instance.group);
            history.setFieldParams(main.getFieldParams());
            history.setFieldLog(joinLogs(logs));
            // 直接走 Dao insert（不经 BaseService.insert），需自行初始化主键 ID
            history.initFieldId();
            historyDao.insert(history);

            updateMainStatus(main.getFieldId(), failed ? SysTaskStatus.FAIL.getValue() : SysTaskStatus.FINISH.getValue());
            log.info("task execute: finished (id={}, name={}, result={})", main.getFieldId(), main.getFieldName(), result);
        } finally {
            running.remove(instance.taskId, instance);
        }
    }

    /**
     * 任务触发时已有实例在运行：登记一条 BLOCK 实例（不写 history，未实际执行）。
     */
    private void recordBlocked(String taskId) {
        SysTaskExample example = new SysTaskExample();
        example.setFieldStatus(SysTaskStatus.BLOCK.getValue());
        example.setFieldStartTime(new Date());
        example.setFieldGroup(newGroup());
        // 直接走 Dao insert（不经 BaseService.insert），需自行初始化主键 ID
        example.initFieldId();
        exampleDao.insert(example);
        log.warn("task execute: blocked, task already running (id={})", taskId);
    }

    private void updateMainStatus(String taskId, String status) {
        SysTaskMain mainUpdate = new SysTaskMain();
        mainUpdate.setFieldId(taskId);
        mainUpdate.setFieldStatus(status);
        mainDao.update(mainUpdate);
    }

    /* ========== 工具方法 ========== */

    /**
     * cron 合法性校验：非法时抛出 {@link IllegalArgumentException}（编辑接口复用）。
     */
    public void validateCron(String cron) {
        if (StringUtil.isEmpty(cron) || !CronExpression.isValidExpression(cron.trim())) {
            throw new IllegalArgumentException("cron 表达式不合法: " + cron);
        }
    }

    private Trigger createTrigger(String cron) {
        validateCron(cron);
        return new CronTrigger(cron.trim());
    }

    private static String newGroup() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 注解参数列表（"key=value" 字符串数组）转 JSON 对象字符串（值均为字符串）。
     */
    private String paramsToJson(String[] params) {
        Map<String, String> map = new LinkedHashMap<>();
        if (null != params) {
            for (String item : params) {
                if (StringUtil.isEmpty(item)) {
                    continue;
                }
                int index = item.indexOf('=');
                if (index <= 0) {
                    continue;
                }
                map.put(item.substring(0, index).trim(), item.substring(index + 1));
            }
        }
        return toJson(map);
    }

    /**
     * 解析参数 JSON 为 Map；缺失或非法时返回空 Map。
     */
    private Map<String, String> parseParams(String json) {
        if (StringUtil.isEmpty(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            log.warn("task params parse failed, use empty params: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("参数 JSON 序列化失败", e);
        }
    }

    private static String joinLogs(Queue<String> logs) {
        if (null == logs || logs.isEmpty()) {
            return null;
        }
        return String.join(System.lineSeparator(), logs);
    }

    @SuppressWarnings("unchecked")
    private static <A extends Annotation> A findAnnotation(Class<?> clazz, Class<A> annotationType) {
        return (A) AnnotationUtils.findAnnotation(clazz, annotationType);
    }

    /**
     * 任务运行实例（应用运行态）：登记运行组、实例 id、开始时间与执行线程，
     * 支持「中止」——置 stopping 标志并 interrupt 执行线程。
     */
    private static final class RunningInstance {

        private final String taskId;

        private final String group;

        private final Date startTime;

        /** 执行实例id（抢占运行位成功后由 execute 回填） */
        private volatile String exampleId;

        /** 当前执行线程（任务体开始时赋值，用于中止） */
        private volatile Thread thread;

        /** 是否被管理员中止 */
        private volatile boolean stopping;

        private RunningInstance(String taskId, String group, Date startTime) {
            this.taskId = taskId;
            this.group = group;
            this.startTime = startTime;
        }
    }
}

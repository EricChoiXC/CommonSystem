package com.eric.base.sys.process.task;

import com.eric.base.sys.process.dao.SysProcessTaskLogDao;
import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.model.SysProcessTaskLog;
import com.eric.base.sys.process.properties.SysProcessConstants;
import com.eric.base.sys.process.service.engine.FlowEngineFixture;
import com.eric.base.sys.process.service.engine.ProcessFlowResult;
import com.eric.base.sys.process.task.annotation.ProcessTask;
import com.eric.base.sys.process.task.annotation.ProcessTaskHandler;
import com.eric.base.sys.process.task.enums.ProcessTaskType;
import com.eric.base.sys.process.task.model.ProcessTaskContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 流程任务引擎集成测试
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-005：以 {@link FlowEngineFixture} 为基座，接入真实
 *              {@link ProcessTaskRegistry} + {@link ProcessTaskExecutorImpl}，验证
 *              ROBORT 节点 tasks 执行、强制任务在 StartProcess/EndProcess 对所有实例执行、
 *              各步骤触发点留痕，以及任务失败转流程异常（PAUSE + SYSTEM_ERROR）。
 */
class ProcessTaskEngineIntegrationTest {

    private static final String TASK_LOG_ID = "30000000000000000000000000000003";

    private FlowEngineFixture fixture;

    private final List<SysProcessTaskLog> taskLogs = new ArrayList<>();
    private final Map<String, List<ProcessTaskContext>> invocations = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        fixture = new FlowEngineFixture();
    }

    /* ==================== 装配：真实注册表 + 真实执行器 ==================== */

    /**
     * 构造真实执行器骨架（未注册任何任务）。
     */
    private ProcessTaskExecutorImpl executor() {
        SysProcessTaskLogDao taskLogDao = mock(SysProcessTaskLogDao.class);
        when(taskLogDao.insertLog(any(SysProcessTaskLog.class))).thenAnswer(invocation -> {
            SysProcessTaskLog log = invocation.getArgument(0);
            if (null == log.getFieldId()) {
                log.setFieldId(TASK_LOG_ID);
            }
            taskLogs.add(log);
            return 1;
        });
        return new ProcessTaskExecutorImpl(new ProcessTaskRegistry(mock(ApplicationContext.class)),
                taskLogDao, fixture.exampleDao, fixture.nodeDao, fixture.documentFieldService,
                new ObjectMapper());
    }

    /**
     * 按「已扫描登记结果」装配真实注册表：
     * 测试任务类为内部类（类上无注解），故直接登记 {@code name → 注册项}（等价
     * {@link ProcessTaskRegistry#scanAndRegister()} 的登记结果，扫描 fail-fast 由
     * {@code ProcessTaskRegistryTest} 覆盖）。
     */
    private ProcessTaskRegistry registryOf(ProcessTaskExecutorImpl executor,
                                           ProcessTaskRegistry.ProcessTaskRegistration... registrations) {
        ProcessTaskRegistry registry = new ProcessTaskRegistry(mock(ApplicationContext.class));
        for (ProcessTaskRegistry.ProcessTaskRegistration registration : registrations) {
            putRegistration(registry, registration);
        }
        replaceRegistry(executor, registry);
        return registry;
    }

    private static void replaceRegistry(ProcessTaskExecutorImpl executor, ProcessTaskRegistry registry) {
        try {
            java.lang.reflect.Field field = ProcessTaskExecutorImpl.class.getDeclaredField("registry");
            field.setAccessible(true);
            field.set(executor, registry);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void putRegistration(ProcessTaskRegistry registry,
                                        ProcessTaskRegistry.ProcessTaskRegistration registration) {
        try {
            java.lang.reflect.Field field = ProcessTaskRegistry.class.getDeclaredField("registrations");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, ProcessTaskRegistry.ProcessTaskRegistration> map =
                    (Map<String, ProcessTaskRegistry.ProcessTaskRegistration>) field.get(registry);
            map.put(registration.getTaskName(), registration);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /* ==================== 测试任务 Bean ==================== */

    /** 记录调用上下文的任务 */
    static class RecorderTask implements ProcessTaskHandler {
        private final String key;
        private final Map<String, List<ProcessTaskContext>> sink;

        RecorderTask(String key, Map<String, List<ProcessTaskContext>> sink) {
            this.key = key;
            this.sink = sink;
        }

        @Override
        public void execute(ProcessTaskContext context) {
            sink.computeIfAbsent(key, ignored -> new ArrayList<>()).add(context);
        }
    }

    /** 记录调用后固定抛异常的任务 */
    static class FailingTask implements ProcessTaskHandler {
        @Override
        public void execute(ProcessTaskContext context) {
            throw new IllegalStateException("外部系统不可用");
        }
    }

    static ProcessTask annotate(String name, boolean force, ProcessTaskType forceType,
                                ProcessTaskType... allowTypes) {
        return new ProcessTask() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String[] processTemplate() {
                return new String[0];
            }

            @Override
            public boolean isForce() {
                return force;
            }

            @Override
            public ProcessTaskType forceTaskType() {
                return forceType;
            }

            @Override
            public ProcessTaskType[] allowTaskType() {
                return allowTypes;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ProcessTask.class;
            }
        };
    }

    /** 普通任务注册项：允许配置到引擎内置用例覆盖的流程级/节点级/工作项级步骤；模板分类不限 */
    private ProcessTaskRegistry.ProcessTaskRegistration task(String name, ProcessTaskHandler handler) {
        return new ProcessTaskRegistry.ProcessTaskRegistration(name, handler, handler.getClass(),
                annotate(name, false, ProcessTaskType.StartProcess,
                        ProcessTaskType.CreateProcess, ProcessTaskType.StartNode,
                        ProcessTaskType.StartWorkitem, ProcessTaskType.EndNode,
                        ProcessTaskType.EndProcess));
    }

    /** 强制任务注册项（仅指定步骤对所有实例执行） */
    private ProcessTaskRegistry.ProcessTaskRegistration forceTask(String name, ProcessTaskType forceType,
                                                                  ProcessTaskHandler handler) {
        return new ProcessTaskRegistry.ProcessTaskRegistration(name, handler, handler.getClass(),
                annotate(name, true, forceType));
    }

    /** 记录调用并返回新执行器（任务已登记进真实注册表） */
    private ProcessTaskExecutorImpl executorWith(String name, ProcessTaskHandler handler) {
        ProcessTaskExecutorImpl executor = executor();
        registryOf(executor, task(name, handler));
        return executor;
    }

    /** 节点配置任务明细 JSON（taskName 固定「流程任务」，taskBean=注册任务名，taskType=执行步骤） */
    private static String task(String taskBean, ProcessTaskType type) {
        return "{\"taskName\":\"流程任务\",\"taskBean\":\"" + taskBean + "\",\"taskType\":\"" + type.name() + "\"}";
    }

    /** 节点配置 JSON（tasks 数组按序声明各步骤触发点） */
    private static String tasksConfig(String... tasks) {
        return "{\"tasks\":[" + String.join(",", tasks) + "]}";
    }

    /** 审批节点配置 JSON：单签审批人 + 各步骤任务明细 */
    private static String approveTasksConfig(String handlerIds, String... tasks) {
        return "{\"handlerIds\":\"" + handlerIds + "\",\"approveMode\":\""
                + SysProcessConstants.APPROVE_MODE_SINGLE + "\",\"autoSkip\":\"no\",\"tasks\":["
                + String.join(",", tasks) + "]}";
    }

    /* ==================== 强制任务：StartProcess / EndProcess ==================== */

    @Test
    @DisplayName("强制任务 StartProcess：进入流转即对该实例执行并留痕（taskType=StartProcess、无节点）")
    void startProcessForceTaskExecutesAndLogs() {
        ProcessTaskExecutorImpl executor = executor();
        registryOf(executor, forceTask("发起强制任务", ProcessTaskType.StartProcess,
                new RecorderTask("发起强制任务", invocations)));
        fixture.node(1, "开始节点", SysProcessConstants.NODE_TYPE_START);
        fixture.node(3, "结束节点", SysProcessConstants.NODE_TYPE_END);
        fixture.line("N1", "N3");

        ProcessFlowResult result = fixture.engine(executor).enterFlow(fixture.example, "N1");

        assertTrue(result.isFinished(), "无节点级任务时 START→END 保持既有流转等价性");
        assertEquals(1, invocations.getOrDefault("发起强制任务", List.of()).size());
        assertEquals(1, taskLogs.size());
        SysProcessTaskLog log = taskLogs.get(0);
        assertEquals("发起强制任务", log.getFieldTaskName());
        assertEquals(ProcessTaskType.StartProcess.name(), log.getFieldTaskType());
        assertEquals(fixture.example.getFieldId(), log.getFieldExampleId());
        assertNull(log.getFieldNodeId(), "流程级步骤无节点");
        assertEquals(Boolean.TRUE, log.getFieldSuccess());
    }

    @Test
    @DisplayName("强制任务 EndProcess：流程办结时执行并留痕；非强制任务不在流程级触发")
    void endProcessForceTaskExecutesOnFinish() {
        ProcessTaskExecutorImpl executor = executor();
        registryOf(executor,
                forceTask("结束强制任务", ProcessTaskType.EndProcess,
                        new RecorderTask("结束强制任务", invocations)),
                task("普通任务", new RecorderTask("普通任务", invocations)));
        fixture.node(1, "开始节点", SysProcessConstants.NODE_TYPE_START);
        fixture.node(3, "结束节点", SysProcessConstants.NODE_TYPE_END);
        fixture.line("N1", "N3");

        ProcessFlowResult result = fixture.engine(executor).enterFlow(fixture.example, "N1");

        assertTrue(result.isFinished());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
        assertEquals(1, invocations.getOrDefault("结束强制任务", List.of()).size());
        assertTrue(invocations.getOrDefault("普通任务", List.of()).isEmpty(),
                "未配置到节点/流程步骤的普通任务不应被触发");
        assertEquals(1, taskLogs.size());
        assertEquals(ProcessTaskType.EndProcess.name(), taskLogs.get(0).getFieldTaskType());
    }

    /* ==================== 节点级任务：ROBORT 配置 tasks ==================== */

    @Test
    @DisplayName("ROBORT 节点配置 tasks：进入即执行并留痕（taskType=StartNode、带节点 id 与任务参数）")
    void nodeLevelTaskFromConfigExecutesOnce() {
        ProcessTaskExecutorImpl executor = executorWith("节点任务", new RecorderTask("节点任务", invocations));
        fixture.node(9, "机器人节点", SysProcessConstants.NODE_TYPE_ROBORT,
                FlowEngineFixture.robortConfig("节点任务"));
        fixture.node(11, "结束节点", SysProcessConstants.NODE_TYPE_END);
        fixture.line("N9", "N11");

        ProcessFlowResult result = fixture.engine(executor).enterFlow(fixture.example, "N9");

        assertTrue(result.isFinished(), "机器人节点任务成功后办结并沿出边流转到结束节点");
        assertEquals(SysProcessConstants.NODE_STATUS_FINISH, fixture.statusOf("N9"));
        assertEquals(1, invocations.getOrDefault("节点任务", List.of()).size(),
                "节点配置的 StartNode 任务只执行一次（不因节点办结重复触发）");
        assertEquals(1, taskLogs.size());
        SysProcessTaskLog log = taskLogs.get(0);
        assertEquals(ProcessTaskType.StartNode.name(), log.getFieldTaskType());
        assertEquals(fixture.nodeByKey("N9").getFieldId(), log.getFieldNodeId());
        assertNull(log.getFieldParams(), "本用例节点配置未带 tasks[].params，留痕为空");
    }

    /* ==================== 全流程：各步骤触发点留痕 ==================== */

    @Test
    @DisplayName("全流程留痕：流程发起/节点进入/工作项开始/节点结束/流程结束各触发一次，强制任务齐备")
    void fullLifecycleLogsEachStepTriggerPoint() {
        ProcessTaskExecutorImpl executor = executor();
        registryOf(executor,
                forceTask("发起强制任务", ProcessTaskType.StartProcess,
                        new RecorderTask("发起强制任务", invocations)),
                task("节点任务", new RecorderTask("节点任务", invocations)),
                forceTask("结束强制任务", ProcessTaskType.EndProcess,
                        new RecorderTask("结束强制任务", invocations)));
        fixture.node(1, "开始节点", SysProcessConstants.NODE_TYPE_START);
        fixture.node(3, "审批节点", SysProcessConstants.NODE_TYPE_APPROVE,
                approveTasksConfig(FlowEngineFixture.U1,
                        task("节点任务", ProcessTaskType.StartNode),
                        task("节点任务", ProcessTaskType.StartWorkitem),
                        task("节点任务", ProcessTaskType.EndNode)));
        fixture.node(5, "结束节点", SysProcessConstants.NODE_TYPE_END);
        fixture.line("N1", "N3");
        fixture.line("N3", "N5");

        ProcessFlowResult entered = fixture.engine(executor).enterFlow(fixture.example, "N1");
        assertTrue(entered.isWaiting(), "审批节点停留等待审批人");
        // 工作项开始步骤（生成工作项时）留痕
        assertTrue(taskLogs.stream().anyMatch(log ->
                ProcessTaskType.StartWorkitem.name().equals(log.getFieldTaskType())));

        // 模拟审批人通过（工作项已由引擎生成）：节点结束步骤留痕
        fixture.nodeByKey("N3").setFieldStatus(SysProcessConstants.NODE_STATUS_PROCESSING);
        for (SysProcessExampleWorkitem workitem : fixture.workitemsOf("N3")) {
            workitem.setFieldStatus(SysProcessConstants.WORKITEM_STATUS_FINISH);
        }
        ProcessFlowResult finished = fixture.engine(executor)
                .pass(fixture.example, "N3", "审批节点", fixture.workitemsOf("N3").get(0).getFieldId());

        assertTrue(finished.isFinished());
        assertEquals(List.of(ProcessTaskType.StartProcess.name(),
                        ProcessTaskType.StartNode.name(),
                        ProcessTaskType.StartWorkitem.name(),
                        ProcessTaskType.EndNode.name(),
                        ProcessTaskType.EndProcess.name()),
                taskLogs.stream().map(SysProcessTaskLog::getFieldTaskType).toList());
        // 强制任务：流程发起/流程结束各执行一次
        assertEquals(1, invocations.getOrDefault("发起强制任务", List.of()).size());
        assertEquals(1, invocations.getOrDefault("结束强制任务", List.of()).size());
        for (SysProcessTaskLog log : taskLogs) {
            assertEquals(Boolean.TRUE, log.getFieldSuccess());
            assertEquals(fixture.example.getFieldId(), log.getFieldExampleId());
            assertNotNull(log.getFieldResult());
            assertNotNull(log.getFieldCostMs());
        }
    }

    @Test
    @DisplayName("机器人节点遗留明细：taskType 非法（缺失/非 ProcessTaskType）时校验不通过，转流程异常")
    void robortLegacyTaskDetailPausesOnInvalidStep() {
        ProcessTaskExecutorImpl executor = executorWith("遗留任务", new RecorderTask("遗留任务", invocations));
        // 遗留配置：未声明合法执行步骤，执行前校验拦截（不执行任务、不留痕、实例 PAUSE）
        fixture.node(9, "机器人节点", SysProcessConstants.NODE_TYPE_ROBORT,
                "{\"tasks\":[{\"taskName\":\"遗留明细\",\"taskBean\":\"遗留任务\"}]}");
        fixture.node(11, "结束节点", SysProcessConstants.NODE_TYPE_END);
        fixture.line("N9", "N11");

        ProcessFlowResult result = fixture.engine(executor).enterFlow(fixture.example, "N9");

        assertTrue(result.isPaused());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PAUSE, fixture.example.getFieldStatus());
        assertTrue(fixture.auditContents(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR).get(0)
                .contains("执行步骤为空"));
        assertTrue(invocations.getOrDefault("遗留任务", List.of()).isEmpty(), "非法步骤不执行任务");
        assertTrue(taskLogs.isEmpty(), "非法步骤不留痕");
    }

    /* ==================== 校验与异常：转流程异常 ==================== */

    @Test
    @DisplayName("执行步骤不被允许：引擎转流程异常（PAUSE + SYSTEM_ERROR），中文提示含任务名与允许步骤")
    void stepTypeNotAllowedPausesExample() {
        ProcessTaskExecutorImpl executor = executor();
        // 允许步骤仅 EndNode：配置到 ROBORT 节点（进入步骤 StartNode）时应校验失败
        registryOf(executor, new ProcessTaskRegistry.ProcessTaskRegistration("受限任务",
                new RecorderTask("受限任务", invocations), RecorderTask.class,
                annotate("受限任务", false, ProcessTaskType.StartProcess, ProcessTaskType.EndNode)));
        fixture.node(1, "开始节点", SysProcessConstants.NODE_TYPE_START);
        fixture.node(9, "机器人节点", SysProcessConstants.NODE_TYPE_ROBORT,
                FlowEngineFixture.robortConfig("受限任务"));
        fixture.node(11, "结束节点", SysProcessConstants.NODE_TYPE_END);
        fixture.line("N1", "N9");
        fixture.line("N9", "N11");

        ProcessFlowResult result = fixture.engine(executor).enterFlow(fixture.example, "N1");

        assertTrue(result.isPaused());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PAUSE, fixture.example.getFieldStatus());
        assertEquals(1, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
        String summary = fixture.auditContents(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR).get(0);
        assertTrue(summary.contains("受限任务"), summary);
        assertTrue(summary.contains("EndNode"), summary);
        assertTrue(invocations.getOrDefault("受限任务", List.of()).isEmpty(), "校验不通过不执行任务");
        assertTrue(taskLogs.isEmpty(), "校验不通过不留痕");
    }

    @Test
    @DisplayName("任务执行失败：留痕 success=0 + errorMsg，并转流程异常（PAUSE + SYSTEM_ERROR）")
    void taskFailurePausesExampleAndLogsFailed() {
        ProcessTaskExecutorImpl executor = executor();
        registryOf(executor, task("失败任务", new FailingTask()));
        fixture.node(1, "开始节点", SysProcessConstants.NODE_TYPE_START);
        fixture.node(9, "机器人节点", SysProcessConstants.NODE_TYPE_ROBORT,
                FlowEngineFixture.robortConfig("失败任务"));
        fixture.node(11, "结束节点", SysProcessConstants.NODE_TYPE_END);
        fixture.line("N1", "N9");
        fixture.line("N9", "N11");

        ProcessFlowResult result = fixture.engine(executor).enterFlow(fixture.example, "N1");

        assertTrue(result.isPaused());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PAUSE, fixture.example.getFieldStatus());
        assertEquals(1, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
        assertTrue(fixture.auditContents(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR).get(0)
                .contains("外部系统不可用"));
        // 创建实例/流程发起步骤任务未配置，无留痕；仅机器人节点任务失败留痕
        assertEquals(1, taskLogs.size());
        SysProcessTaskLog log = taskLogs.get(0);
        assertEquals("失败任务", log.getFieldTaskName());
        assertEquals(ProcessTaskType.StartNode.name(), log.getFieldTaskType());
        assertEquals(Boolean.FALSE, log.getFieldSuccess());
        assertEquals("外部系统不可用", log.getFieldErrorMsg());
        assertNotNull(log.getFieldResult());
    }

    /* ==================== 上下文组装 ==================== */

    @Test
    @DisplayName("任务上下文：流程实例/节点齐备，业务单据经文档字段服务读取")
    void contextCarriesProcessNodeAndModel() {
        ProcessTaskExecutorImpl executor = executorWith("上下文任务", new RecorderTask("上下文任务", invocations));
        when(fixture.documentFieldService.resolveModel("sys_org", "model-1")).thenReturn("单据对象");
        fixture.node(1, "开始节点", SysProcessConstants.NODE_TYPE_START);
        fixture.node(9, "机器人节点", SysProcessConstants.NODE_TYPE_ROBORT,
                FlowEngineFixture.robortConfig("上下文任务"));
        fixture.node(11, "结束节点", SysProcessConstants.NODE_TYPE_END);
        fixture.line("N1", "N9");
        fixture.line("N9", "N11");

        fixture.engine(executor).enterFlow(fixture.example, "N1");

        List<ProcessTaskContext> captured = invocations.get("上下文任务");
        assertNotNull(captured);
        // 节点配置声明 taskType=StartNode：仅节点进入步骤触发一次（结束步骤不会误触发同一步骤的任务）
        assertEquals(1, captured.size());
        ProcessTaskContext context = captured.get(0);
        assertEquals("单据对象", context.getModel());
        assertEquals(fixture.example.getFieldId(), context.getProcess().getFieldId());
        assertEquals(fixture.nodeByKey("N9").getFieldId(), context.getNode().getFieldId());
        assertNull(context.getWorkitem(), "节点级任务无工作项");
    }

    @Test
    @DisplayName("工作项开始任务上下文：工作项步骤触发时携带工作项")
    void workitemContextCarriesWorkitem() {
        ProcessTaskExecutorImpl executor = executor();
        registryOf(executor, new ProcessTaskRegistry.ProcessTaskRegistration("工作项任务",
                new RecorderTask("工作项任务", invocations), RecorderTask.class,
                annotate("工作项任务", false, ProcessTaskType.StartProcess,
                        ProcessTaskType.StartNode, ProcessTaskType.StartWorkitem)));
        fixture.node(3, "审批节点", SysProcessConstants.NODE_TYPE_APPROVE,
                approveTasksConfig(FlowEngineFixture.U1,
                        task("工作项任务", ProcessTaskType.StartWorkitem)));
        fixture.node(5, "结束节点", SysProcessConstants.NODE_TYPE_END);
        fixture.line("N3", "N5");

        fixture.engine(executor).enterFlow(fixture.example, "N3");

        List<ProcessTaskContext> captured = invocations.get("工作项任务");
        assertNotNull(captured);
        // 仅工作项开始步骤配置任务：生成工作项时触发一次并携带工作项
        assertEquals(1, captured.size());
        SysProcessExampleWorkitem workitem = captured.get(0).getWorkitem();
        assertNotNull(workitem, "工作项开始步骤应携带当前工作项");
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, workitem.getFieldStatus());
        assertEquals(ProcessTaskType.StartWorkitem.name(), taskLogs.get(0).getFieldTaskType());
        assertEquals(workitem.getFieldId(), taskLogs.get(0).getFieldWorkitemId());
    }
}

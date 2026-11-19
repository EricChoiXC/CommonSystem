package com.eric.base.sys.process.task;

import com.eric.base.sys.process.dao.SysProcessExampleDao;
import com.eric.base.sys.process.dao.SysProcessExampleNodeDao;
import com.eric.base.sys.process.dao.SysProcessTaskLogDao;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessTaskLog;
import com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo;
import com.eric.base.sys.process.service.SysProcessDocumentFieldService;
import com.eric.base.sys.process.service.engine.ProcessFlowException;
import com.eric.base.sys.process.service.engine.ProcessTaskExecutor;
import com.eric.base.sys.process.task.annotation.ProcessTask;
import com.eric.base.sys.process.task.annotation.ProcessTaskHandler;
import com.eric.base.sys.process.task.enums.ProcessTaskType;
import com.eric.base.sys.process.task.model.ProcessTaskContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 流程任务执行与留痕测试
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-005：执行成功/失败均写 sys_process_task_log（字段填充与耗时），
 *              执行异常转 ProcessFlowException（CODE_TASK_FAILED）；强制任务按步骤执行。
 */
class ProcessTaskExecutorTest {

    private static final String EXAMPLE_ID = "10000000000000000000000000000001";
    /** 32 位节点 id（BaseModel#setFieldId 对非 32 位值会重新生成，测试须用合法 id） */
    private static final String NODE_ID = "20000000000000000000000000000002";

    private ProcessTaskRegistry registry;
    private SysProcessTaskLogDao taskLogDao;
    private SysProcessExampleDao exampleDao;
    private SysProcessExampleNodeDao nodeDao;
    private SysProcessDocumentFieldService documentFieldService;
    private ProcessTaskExecutorImpl executor;

    private final List<SysProcessTaskLog> logs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        registry = mock(ProcessTaskRegistry.class);
        taskLogDao = mock(SysProcessTaskLogDao.class);
        exampleDao = mock(SysProcessExampleDao.class);
        nodeDao = mock(SysProcessExampleNodeDao.class);
        documentFieldService = mock(SysProcessDocumentFieldService.class);
        executor = new ProcessTaskExecutorImpl(registry, taskLogDao, exampleDao, nodeDao,
                documentFieldService, new ObjectMapper());

        SysProcessExample example = new SysProcessExample();
        example.setFieldId(EXAMPLE_ID);
        example.setFieldModelName("sys_org");
        example.setFieldModelId("model-1");
        when(exampleDao.selectById(EXAMPLE_ID)).thenReturn(example);
        SysProcessExampleNode node = new SysProcessExampleNode();
        node.setFieldId(NODE_ID);
        node.setFieldNodeName("机器人节点");
        when(nodeDao.selectById(NODE_ID)).thenReturn(node);
        when(taskLogDao.insertLog(any(SysProcessTaskLog.class))).thenAnswer(invocation -> {
            logs.add(invocation.getArgument(0));
            return 1;
        });
        when(documentFieldService.resolveModel(anyString(), anyString())).thenReturn("单据对象");
    }

    /** 登记一个自定义任务实现（handlerClass=匿名包装类）并让注册表命中 */
    private void registerTask(String name, ProcessTask annotation, ProcessTaskHandler handler) {
        ProcessTaskHandler wrapper = new ProcessTaskHandler() {
            @Override
            public void execute(ProcessTaskContext context) {
                handler.execute(context);
            }
        };
        ProcessTaskRegistry.ProcessTaskRegistration registration = new ProcessTaskRegistry.ProcessTaskRegistration(
                name, wrapper, wrapper.getClass(), annotation);
        when(registry.get(name)).thenReturn(registration);
        // 校验方法为 void：Mockito 默认即不抛异常（模拟校验通过）
    }

    private static ProcessTask annotation(String name) {
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
                return false;
            }

            @Override
            public ProcessTaskType forceTaskType() {
                return ProcessTaskType.StartProcess;
            }

            @Override
            public ProcessTaskType[] allowTaskType() {
                return new ProcessTaskType[0];
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ProcessTask.class;
            }
        };
    }

    private static ProcessTask forceAnnotation(String name, ProcessTaskType forceTaskType) {
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
                return true;
            }

            @Override
            public ProcessTaskType forceTaskType() {
                return forceTaskType;
            }

            @Override
            public ProcessTaskType[] allowTaskType() {
                return new ProcessTaskType[0];
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ProcessTask.class;
            }
        };
    }

    private static SysProcessTemplateNodeConfigDo.TaskDetail detail(String taskBean, ProcessTaskType type,
                                                                   Map<String, Object> params) {
        SysProcessTemplateNodeConfigDo.TaskDetail detail = new SysProcessTemplateNodeConfigDo.TaskDetail();
        detail.setTaskName("任务展示名");
        detail.setTaskBean(taskBean);
        detail.setTaskType(type.name());
        detail.setParams(params);
        return detail;
    }

    private ProcessTaskExecutor.ProcessTaskRequest request(String taskBean, ProcessTaskType type,
                                                           Map<String, Object> params) {
        return new ProcessTaskExecutor.ProcessTaskRequest(EXAMPLE_ID, NODE_ID, "机器人节点", null,
                detail(taskBean, type, params));
    }

    /* ==================== 执行留痕 ==================== */

    @Test
    @DisplayName("执行成功：写 log（success=1、params/result/costMs 有值）并组装上下文")
    void successWritesLog() {
        List<ProcessTaskContext> captured = new ArrayList<>();
        registerTask("成功任务", annotation("成功任务"), captured::add);

        Map<String, Object> params = Map.of("bizId", "B-1");
        executor.execute(request("成功任务", ProcessTaskType.StartNode, params));

        assertEquals(1, logs.size());
        SysProcessTaskLog log = logs.get(0);
        assertEquals("成功任务", log.getFieldTaskName());
        assertEquals(EXAMPLE_ID, log.getFieldExampleId());
        assertEquals(NODE_ID, log.getFieldNodeId());
        assertEquals(ProcessTaskType.StartNode.name(), log.getFieldTaskType());
        assertEquals(Boolean.TRUE, log.getFieldSuccess());
        assertNull(log.getFieldErrorMsg());
        assertNotNull(log.getFieldCostMs());
        assertTrue(log.getFieldCostMs() >= 0);
        assertNotNull(log.getFieldParams());
        assertTrue(log.getFieldParams().contains("B-1"), log.getFieldParams());
        assertNotNull(log.getFieldResult());
        assertTrue(log.getFieldResult().contains("成功任务"), log.getFieldResult());
        assertTrue(log.getFieldResult().contains("StartNode"), log.getFieldResult());
        assertNotNull(log.getFieldCreateTime());
        assertNotNull(log.getFieldId());

        // 上下文组装：业务单据经文档字段服务读取，流程实例/节点/参数齐备
        assertEquals(1, captured.size());
        assertEquals("单据对象", captured.get(0).getModel());
        assertEquals(EXAMPLE_ID, captured.get(0).getProcess().getFieldId());
        assertEquals(NODE_ID, captured.get(0).getNode().getFieldId());
        assertEquals("B-1", captured.get(0).getParams().get("bizId"));
    }

    @Test
    @DisplayName("执行抛异常：写 log（success=0 + errorMsg）并抛 CODE_TASK_FAILED")
    void failureWritesLogAndThrows() {
        registerTask("失败任务", annotation("失败任务"), context -> {
            throw new IllegalStateException("外部接口超时");
        });

        ProcessFlowException error = assertThrows(ProcessFlowException.class,
                () -> executor.execute(request("失败任务", ProcessTaskType.StartNode, null)));

        assertEquals(ProcessFlowException.CODE_TASK_FAILED, error.getCode());
        assertTrue(error.getMessage().contains("失败任务"), error.getMessage());
        assertTrue(error.getMessage().contains("外部接口超时"), error.getMessage());

        assertEquals(1, logs.size());
        SysProcessTaskLog log = logs.get(0);
        assertEquals(Boolean.FALSE, log.getFieldSuccess());
        assertEquals("外部接口超时", log.getFieldErrorMsg());
        assertEquals("失败任务", log.getFieldTaskName());
        assertNotNull(log.getFieldResult());
        assertNotNull(log.getFieldCostMs());
    }

    @Test
    @DisplayName("执行抛 ProcessFlowException：原样透传（错误码保持）且仍写失败留痕")
    void flowExceptionPassesThrough() {
        registerTask("流程异常任务", annotation("流程异常任务"), context -> {
            throw ProcessFlowException.task("业务校验未通过", null);
        });

        ProcessFlowException error = assertThrows(ProcessFlowException.class,
                () -> executor.execute(request("流程异常任务", ProcessTaskType.StartNode, null)));

        assertEquals(ProcessFlowException.CODE_TASK_FAILED, error.getCode());
        assertEquals("业务校验未通过", error.getMessage());
        assertEquals(1, logs.size());
        assertEquals(Boolean.FALSE, logs.get(0).getFieldSuccess());
        assertEquals("业务校验未通过", logs.get(0).getFieldErrorMsg());
    }
}

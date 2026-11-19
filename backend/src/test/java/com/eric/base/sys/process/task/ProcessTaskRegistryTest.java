package com.eric.base.sys.process.task;

import com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo;
import com.eric.base.sys.process.service.engine.ProcessFlowException;
import com.eric.base.sys.process.task.annotation.ProcessTask;
import com.eric.base.sys.process.task.annotation.ProcessTaskHandler;
import com.eric.base.sys.process.task.enums.ProcessTaskType;
import com.eric.base.sys.process.task.model.ProcessTaskContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 流程任务注册表测试
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-005：@ProcessTask 扫描注册的 fail-fast 规则（注解与接口缺一、任务名重复、
 *              任务名缺失）与正常注册。
 */
class ProcessTaskRegistryTest {

    private ApplicationContext applicationContext;
    private ProcessTaskRegistry registry;

    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
        registry = new ProcessTaskRegistry(applicationContext);
    }

    /* ==================== 测试夹具（Bean 类） ==================== */

    /** 正常：注解 + 接口齐备 */
    @ProcessTask(name = "完善任务", processTemplate = {"sys_org"}, allowTaskType = {ProcessTaskType.StartNode})
    static class ValidTask implements ProcessTaskHandler {
        @Override
        public void execute(ProcessTaskContext context) {
        }
    }

    /** 异常：只有注解未实现接口 */
    @ProcessTask(name = "缺接口任务")
    static class AnnotationOnlyTask {
    }

    /** 异常：只有接口未标注注解（不参与 getBeansWithAnnotation，用于校验注册入口防御） */
    static class HandlerOnlyTask implements ProcessTaskHandler {
        @Override
        public void execute(ProcessTaskContext context) {
        }
    }

    /** 异常：任务名为空 */
    @ProcessTask(name = "  ")
    static class BlankNameTask implements ProcessTaskHandler {
        @Override
        public void execute(ProcessTaskContext context) {
        }
    }

    /** 异常：与 ValidTask 任务名重复 */
    @ProcessTask(name = "完善任务")
    static class DuplicateNameTask implements ProcessTaskHandler {
        @Override
        public void execute(ProcessTaskContext context) {
        }
    }

    private void beans(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            map.put("bean" + i, values[i]);
        }
        when(applicationContext.getBeansWithAnnotation(ProcessTask.class)).thenReturn(map);
    }

    /* ==================== 用例 ==================== */

    @Test
    @DisplayName("正常注册：注解与接口齐备的任务登记成功（name → bean）")
    void registersValidTask() {
        ValidTask task = new ValidTask();
        beans(task);

        registry.scanAndRegister();

        assertEquals(1, registry.size());
        assertEquals(List.of("完善任务"), registry.names());
        assertNotNull(registry.get("完善任务"));
        assertEquals(ValidTask.class, registry.get("完善任务").getHandlerClass());
    }

    @Test
    @DisplayName("注册 fail-fast：标注 @ProcessTask 但未实现 ProcessTaskHandler 抛异常（含类名与任务名）")
    void failsWhenHandlerMissing() {
        beans(new AnnotationOnlyTask());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> registry.scanAndRegister());

        assertTrue(error.getMessage().contains("缺接口任务"), error.getMessage());
        assertTrue(error.getMessage().contains(AnnotationOnlyTask.class.getName()), error.getMessage());
        assertTrue(error.getMessage().contains("ProcessTaskHandler"), error.getMessage());
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("注册 fail-fast：任务名重复抛异常（含任务名与两个类名）")
    void failsWhenNameDuplicated() {
        beans(new ValidTask(), new DuplicateNameTask());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> registry.scanAndRegister());

        assertTrue(error.getMessage().contains("完善任务"), error.getMessage());
        assertTrue(error.getMessage().contains(DuplicateNameTask.class.getName()), error.getMessage());
        assertTrue(error.getMessage().contains(ValidTask.class.getName()), error.getMessage());
    }

    @Test
    @DisplayName("注册 fail-fast：任务名为空抛异常")
    void failsWhenNameBlank() {
        beans(new BlankNameTask());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> registry.scanAndRegister());

        assertTrue(error.getMessage().contains("name 不能为空"), error.getMessage());
    }

    @Test
    @DisplayName("注册幂等：同一实现类重复扫描不报错、不重复登记")
    void registerIsIdempotent() {
        ValidTask task = new ValidTask();
        beans(task);

        registry.scanAndRegister();
        registry.scanAndRegister();

        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("未注册任务：get 返回 null，validateTask 抛中文明确异常并列出已注册任务")
    void unregisteredTaskFailsValidation() {
        beans(new ValidTask());
        registry.scanAndRegister();

        assertNull(registry.get("不存在的任务"));

        ProcessFlowException error = assertThrows(ProcessFlowException.class,
                () -> registry.validateTask("不存在的任务", ProcessTaskType.StartNode, "sys_org"));
        assertEquals(ProcessFlowException.CODE_TASK_FAILED, error.getCode());
        assertTrue(error.getMessage().contains("未注册"), error.getMessage());
        assertTrue(error.getMessage().contains("完善任务"), error.getMessage());
    }

    @Test
    @DisplayName("非强制任务在 forceTasksOf 中不返回；仅强制任务的 forceTaskType 步骤命中")
    void forceTasksFilteredByTaskType() {
        beans(new ValidTask());
        registry.scanAndRegister();

        assertTrue(registry.forceTasksOf(ProcessTaskType.StartProcess).isEmpty());
        assertTrue(registry.forceTasksOf(ProcessTaskType.EndProcess).isEmpty());
        assertTrue(registry.forceTasksOf(ProcessTaskType.StartNode).isEmpty());
    }

    @Test
    @DisplayName("强制任务按 forceTaskType 分流：StartProcess 与 EndProcess 各归其位")
    void forceTasksSplitByForceTaskType() {
        beans(new StartForceTask(), new EndForceTask());
        registry.scanAndRegister();

        List<String> startNames = registry.forceTasksOf(ProcessTaskType.StartProcess).stream()
                .map(ProcessTaskRegistry.ProcessTaskRegistration::getTaskName).toList();
        List<String> endNames = registry.forceTasksOf(ProcessTaskType.EndProcess).stream()
                .map(ProcessTaskRegistry.ProcessTaskRegistration::getTaskName).toList();

        assertEquals(List.of("流程发起强制任务"), startNames);
        assertEquals(List.of("流程结束强制任务"), endNames);
    }

    /* ==================== 校验用例 ==================== */

    @Test
    @DisplayName("校验：processTemplate 不含当前模板分类 → 报错（含任务名与模板分类）")
    void validateFailsWhenTemplateMismatch() {
        beans(new TemplateLimitedTask());
        registry.scanAndRegister();

        ProcessFlowException error = assertThrows(ProcessFlowException.class,
                () -> registry.validateTask("指定模板任务", ProcessTaskType.StartNode, "sys_att"));
        assertEquals(ProcessFlowException.CODE_TASK_FAILED, error.getCode());
        assertTrue(error.getMessage().contains("指定模板任务"), error.getMessage());
        assertTrue(error.getMessage().contains("sys_att"), error.getMessage());
    }

    @Test
    @DisplayName("校验：allowTaskType 不含所选步骤 → 报错（含允许步骤清单）")
    void validateFailsWhenTaskTypeNotAllowed() {
        beans(new StepLimitedTask());
        registry.scanAndRegister();

        ProcessFlowException error = assertThrows(ProcessFlowException.class,
                () -> registry.validateTask("仅节点进入任务", ProcessTaskType.EndProcess, "sys_org"));
        assertEquals(ProcessFlowException.CODE_TASK_FAILED, error.getCode());
        assertTrue(error.getMessage().contains("仅节点进入任务"), error.getMessage());
        assertTrue(error.getMessage().contains("EndProcess"), error.getMessage());
        assertTrue(error.getMessage().contains("StartNode"), error.getMessage());
    }

    @Test
    @DisplayName("校验：空 processTemplate / 空 allowTaskType 表示不限 → 通过")
    void validatePassesWhenUnlimited() {
        beans(new TemplateUnlimitedTask(), new StepUnlimitedTask());
        registry.scanAndRegister();

        registry.validateTask("不限模板任务", ProcessTaskType.StartNode, "sys_att");
        registry.validateTask("不限步骤任务", ProcessTaskType.EndProcess, "sys_att");
        registry.validateTask("不限模板任务", ProcessTaskType.EndProcess, null);
    }

    @Test
    @DisplayName("校验：任务名未注册 → 报错；任务明细执行步骤非法 → 报错")
    void validateTaskDetailFailsWhenUnregisteredOrBadStep() {
        beans(new ValidTask());
        registry.scanAndRegister();

        ProcessFlowException unregistered = assertThrows(ProcessFlowException.class,
                () -> registry.validateTaskDetail(detail("幽灵任务", ProcessTaskType.StartNode), "sys_org"));
        assertTrue(unregistered.getMessage().contains("幽灵任务"), unregistered.getMessage());

        ProcessFlowException badStep = assertThrows(ProcessFlowException.class,
                () -> registry.validateTaskDetail(detail("完善任务", null), "sys_org"));
        assertTrue(badStep.getMessage().contains("执行步骤非法"), badStep.getMessage());

        ProcessFlowException nullDetail = assertThrows(ProcessFlowException.class,
                () -> registry.validateTaskDetail(null, "sys_org"));
        assertTrue(nullDetail.getMessage().contains("配置为空"), nullDetail.getMessage());

        // 正常明细通过校验
        registry.validateTaskDetail(detail("完善任务", ProcessTaskType.StartNode), "sys_org");
    }

    @Test
    @DisplayName("校验：强制任务只能落在 StartProcess/EndProcess 步骤")
    void validateForceTaskOnlyAllowedOnForceSteps() {
        beans(new StartForceTask());
        registry.scanAndRegister();

        registry.validateTask("流程发起强制任务", ProcessTaskType.StartProcess, "sys_org");

        ProcessFlowException error = assertThrows(ProcessFlowException.class,
                () -> registry.validateTask("流程发起强制任务", ProcessTaskType.StartNode, "sys_org"));
        assertTrue(error.getMessage().contains("强制任务"), error.getMessage());
    }

    /* ==================== 校验用 Bean ==================== */

    @ProcessTask(name = "指定模板任务", processTemplate = {"sys_org"})
    static class TemplateLimitedTask implements ProcessTaskHandler {
        @Override
        public void execute(ProcessTaskContext context) {
        }
    }

    @ProcessTask(name = "不限模板任务")
    static class TemplateUnlimitedTask implements ProcessTaskHandler {
        @Override
        public void execute(ProcessTaskContext context) {
        }
    }

    @ProcessTask(name = "仅节点进入任务", allowTaskType = {ProcessTaskType.StartNode})
    static class StepLimitedTask implements ProcessTaskHandler {
        @Override
        public void execute(ProcessTaskContext context) {
        }
    }

    @ProcessTask(name = "不限步骤任务")
    static class StepUnlimitedTask implements ProcessTaskHandler {
        @Override
        public void execute(ProcessTaskContext context) {
        }
    }

    @ProcessTask(name = "流程发起强制任务", isForce = true, forceTaskType = ProcessTaskType.StartProcess)
    static class StartForceTask implements ProcessTaskHandler {
        @Override
        public void execute(ProcessTaskContext context) {
        }
    }

    @ProcessTask(name = "流程结束强制任务", isForce = true, forceTaskType = ProcessTaskType.EndProcess)
    static class EndForceTask implements ProcessTaskHandler {
        @Override
        public void execute(ProcessTaskContext context) {
        }
    }

    static SysProcessTemplateNodeConfigDo.TaskDetail detail(String taskBean, ProcessTaskType taskType) {
        SysProcessTemplateNodeConfigDo.TaskDetail detail = new SysProcessTemplateNodeConfigDo.TaskDetail();
        detail.setTaskName("展示名");
        detail.setTaskBean(taskBean);
        detail.setTaskType(null == taskType ? null : taskType.name());
        return detail;
    }
}

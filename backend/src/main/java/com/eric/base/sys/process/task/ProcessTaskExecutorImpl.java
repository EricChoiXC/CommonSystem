package com.eric.base.sys.process.task;

import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.process.dao.SysProcessExampleDao;
import com.eric.base.sys.process.dao.SysProcessExampleNodeDao;
import com.eric.base.sys.process.dao.SysProcessTaskLogDao;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.model.SysProcessTaskLog;
import com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo;
import com.eric.base.sys.process.service.SysProcessDocumentFieldService;
import com.eric.base.sys.process.service.engine.ProcessFlowException;
import com.eric.base.sys.process.service.engine.ProcessTaskExecutor;
import com.eric.base.sys.process.service.engine.ProcessTaskForceExecutor;
import com.eric.base.sys.process.task.annotation.ProcessTask;
import com.eric.base.sys.process.task.enums.ProcessTaskType;
import com.eric.base.sys.process.task.model.ProcessTaskContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程任务执行器实现
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-005 交付物：实现 P17-BE-003 定义的引擎窄接口
 *              {@link ProcessTaskExecutor}（经 {@code ObjectProvider} 注入引擎，引擎不改注入方式）。
 *              职责（依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md §7.2）：
 *              <ul>
 *              <li>按任务明细 {@code taskBean}（已注册 @ProcessTask name）从 {@link ProcessTaskRegistry}
 *                  取实现，配置校验不通过直接抛异常；</li>
 *              <li>组装 {@link ProcessTaskContext}：业务单据经 {@link SysProcessDocumentFieldService}
 *                  的 Mapper 索引读取，节点/工作项可空；</li>
 *              <li>在当前流转事务内同步执行；</li>
 *              <li>每次执行写 {@code sys_process_task_log} 流水（成功与失败均留痕），
 *                  内含 taskName/taskBean/taskType/params/result/success/errorMsg/costMs/
 *                  exampleId/nodeId/workitemId/createTime；</li>
 *              <li>执行失败抛 {@link ProcessFlowException}（错误码 {@code CODE_TASK_FAILED}），
 *                  由引擎统一出口转流程异常（实例 PAUSE + SYSTEM_ERROR 留痕）；</li>
 *              <li>强制任务（{@code isForce=true}）在 StartProcess/EndProcess 步骤对所有实例执行，
 *                  不受节点配置约束（由引擎调用 {@link #executeForceTasks}）。</li>
 *              </ul>
 */
@Component
public class ProcessTaskExecutorImpl implements ProcessTaskExecutor, ProcessTaskForceExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProcessTaskExecutorImpl.class);

    /** 失败信息入库最大长度（field_error_msg 为 TEXT，避免超长堆栈刷表） */
    private static final int MAX_ERROR_MSG_LENGTH = 1000;

    private final ProcessTaskRegistry registry;
    private final SysProcessTaskLogDao taskLogDao;
    private final SysProcessExampleDao exampleDao;
    private final SysProcessExampleNodeDao nodeDao;
    private final SysProcessDocumentFieldService documentFieldService;
    private final ObjectMapper objectMapper;

    public ProcessTaskExecutorImpl(ProcessTaskRegistry registry,
                                   SysProcessTaskLogDao taskLogDao,
                                   SysProcessExampleDao exampleDao,
                                   SysProcessExampleNodeDao nodeDao,
                                   SysProcessDocumentFieldService documentFieldService,
                                   ObjectMapper objectMapper) {
        this.registry = registry;
        this.taskLogDao = taskLogDao;
        this.exampleDao = exampleDao;
        this.nodeDao = nodeDao;
        this.documentFieldService = documentFieldService;
        this.objectMapper = objectMapper;
    }

    /* ==================== 引擎窄接口实现 ==================== */

    @Override
    public void execute(ProcessTaskRequest request) {
        if (null == request || null == request.taskDetail()) {
            throw ProcessFlowException.task("流程任务执行请求为空", null);
        }
        SysProcessTemplateNodeConfigDo.TaskDetail detail = request.taskDetail();
        ProcessTaskType taskType = ProcessTaskType.parse(detail.getTaskType());
        String taskName = StringUtil.isEmpty(detail.getTaskBean()) ? null : detail.getTaskBean().trim();
        SysProcessExample example = StringUtil.isEmpty(request.exampleId())
                ? null : exampleDao.selectById(request.exampleId());
        SysProcessExampleNode node = resolveNode(request.nodeId());
        run(example, node, request.workitem(), taskName, taskType, detail.getParams(), false);
    }

    /**
     * 执行指定步骤的强制任务（引擎在 StartProcess/EndProcess 步骤调用）：
     * 对所有流程实例生效，不受节点配置约束。
     *
     * @param exampleId 流程实例 id
     * @param nodeId    当前节点 id（流程级步骤可空）
     * @param workitem  当前工作项（可空）
     * @param taskType  执行步骤（仅 StartProcess/EndProcess 有强制任务）
     */
    @Override
    public void executeForceTasks(String exampleId, String nodeId, SysProcessExampleWorkitem workitem,
                                  ProcessTaskType taskType) {
        List<ProcessTaskRegistry.ProcessTaskRegistration> forceTasks = registry.forceTasksOf(taskType);
        if (forceTasks.isEmpty()) {
            return;
        }
        SysProcessExample example = StringUtil.isEmpty(exampleId) ? null : exampleDao.selectById(exampleId);
        SysProcessExampleNode node = resolveNode(nodeId);
        for (ProcessTaskRegistry.ProcessTaskRegistration registration : forceTasks) {
            run(example, node, workitem, registration.getTaskName(), taskType, null, true);
        }
    }

    /* ==================== 执行主流程 ==================== */

    /** 单次任务执行：校验 → 组装上下文 → 同步执行 → 留痕（成功/失败均留痕） */
    private void run(SysProcessExample example, SysProcessExampleNode node, SysProcessExampleWorkitem workitem,
                     String taskName, ProcessTaskType taskType, Map<String, Object> params, boolean force) {
        String modelName = null == example ? null : example.getFieldModelName();
        ProcessTaskRegistry.ProcessTaskRegistration registration;
        if (force) {
            registration = registry.get(taskName);
        } else {
            // 非强制任务：任务名未注册 / 模板不匹配 / 步骤不被允许均在此抛中文异常
            registry.validateTask(taskName, taskType, modelName);
            registration = registry.get(taskName);
        }
        if (null == registration) {
            throw ProcessFlowException.task("流程任务「" + taskName + "」未注册", null);
        }
        ProcessTaskContext context = buildContext(example, node, workitem, params);
        long start = System.currentTimeMillis();
        try {
            registration.execute(context);
            long costMs = System.currentTimeMillis() - start;
            writeLog(example, node, workitem, registration, taskType, params, costMs, true, null);
        } catch (ProcessFlowException e) {
            long costMs = System.currentTimeMillis() - start;
            writeLog(example, node, workitem, registration, taskType, params, costMs, false, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            long costMs = System.currentTimeMillis() - start;
            String message = failureMessage(e);
            writeLog(example, node, workitem, registration, taskType, params, costMs, false, message);
            throw ProcessFlowException.task("流程任务「" + registration.getTaskName() + "」执行失败: " + message, e);
        }
    }

    /** 组装流程任务上下文（业务单据经文档字段服务 Mapper 索引读取，不重复建索引） */
    private ProcessTaskContext buildContext(SysProcessExample example, SysProcessExampleNode node,
                                            SysProcessExampleWorkitem workitem, Map<String, Object> params) {
        ProcessTaskContext context = new ProcessTaskContext();
        context.setProcess(example);
        context.setNode(node);
        context.setWorkitem(workitem);
        context.setParams(params);
        if (null != example) {
            context.setModel(documentFieldService.resolveModel(example.getFieldModelName(), example.getFieldModelId()));
        }
        return context;
    }

    private SysProcessExampleNode resolveNode(String nodeId) {
        return StringUtil.isEmpty(nodeId) ? null : nodeDao.selectById(nodeId);
    }

    /** 失败信息（异常消息优先，空则取异常类名；截断到入库上限） */
    private static String failureMessage(RuntimeException e) {
        String message = StringUtil.isEmpty(e.getMessage()) ? e.getClass().getName() : e.getMessage();
        return message.length() > MAX_ERROR_MSG_LENGTH ? message.substring(0, MAX_ERROR_MSG_LENGTH) : message;
    }

    /**
     * 写任务执行留痕（与流转同事务；写失败不影响流转，记 WARN）。
     * <p>字段填充：taskName=注册名；taskBean=实现类全限定名；taskType=执行步骤枚举名；
     * params=任务参数 JSON；result=执行结果摘要 JSON；success=1/0；errorMsg=失败信息；
     * costMs=耗时毫秒；exampleId/nodeId/workitemId 按步骤可空；createTime=当前时间。</p>
     */
    private void writeLog(SysProcessExample example, SysProcessExampleNode node,
                          SysProcessExampleWorkitem workitem,
                          ProcessTaskRegistry.ProcessTaskRegistration registration, ProcessTaskType taskType,
                          Map<String, Object> params, long costMs, boolean success, String errorMsg) {
        try {
            SysProcessTaskLog taskLog = new SysProcessTaskLog();
            taskLog.initFieldId();
            taskLog.setFieldExampleId(null == example ? null : example.getFieldId());
            taskLog.setFieldNodeId(null == node ? null : node.getFieldId());
            taskLog.setFieldWorkitemId(null == workitem ? null : workitem.getFieldId());
            taskLog.setFieldTaskName(registration.getTaskName());
            taskLog.setFieldTaskBean(registration.getHandlerClass().getName());
            taskLog.setFieldTaskType(null == taskType ? null : taskType.name());
            taskLog.setFieldParams(null == params || params.isEmpty() ? null : toJson(params));
            taskLog.setFieldResult(toJson(resultSummary(registration, taskType, costMs, success)));
            taskLog.setFieldSuccess(success);
            taskLog.setFieldErrorMsg(errorMsg);
            taskLog.setFieldCostMs(costMs);
            taskLog.setFieldCreateTime(new Date());
            taskLogDao.insertLog(taskLog);
        } catch (RuntimeException e) {
            // 留痕失败不改变流转结果（对齐 SysProcessTaskLogDao 注释「写失败不影响流转」）
            log.warn("流程任务留痕写入失败: task={}, example={}, error={}",
                    registration.getTaskName(), null == example ? null : example.getFieldId(), e.getMessage());
        }
    }

    /** 执行结果摘要（JSON 对象：任务名/执行步骤/实现类/耗时/是否成功） */
    private static Map<String, Object> resultSummary(ProcessTaskRegistry.ProcessTaskRegistration registration,
                                                     ProcessTaskType taskType, long costMs, boolean success) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskName", registration.getTaskName());
        summary.put("taskType", null == taskType ? null : taskType.name());
        summary.put("bean", registration.getHandlerClass().getName());
        summary.put("costMs", costMs);
        summary.put("success", success);
        return summary;
    }

    /** JSON 序列化（失败时返回 null，不阻断流转） */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("流程任务日志 JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /* ==================== 校验（供 P17-BE-006 模板图校验调用） ==================== */

    /** 校验节点配置的任务明细行（等价 {@link ProcessTaskRegistry#validateTaskDetail} 的公开入口） */
    public void validateTaskDetail(SysProcessTemplateNodeConfigDo.TaskDetail taskDetail, String modelName) {
        registry.validateTaskDetail(taskDetail, modelName);
    }

    /** 校验「任务名 + 执行步骤 + 模板分类」组合是否可执行 */
    public void validateTask(String taskName, ProcessTaskType taskType, String modelName) {
        registry.validateTask(taskName, taskType, modelName);
    }

    /** 强制任务的注册信息（调试/排查用；返回注册名与注解） */
    public List<ProcessTaskRegistry.ProcessTaskRegistration> forceTasksOf(ProcessTaskType taskType) {
        return registry.forceTasksOf(taskType);
    }

    /** 注解元信息（供外层提示文案使用） */
    ProcessTask annotationOf(String taskName) {
        ProcessTaskRegistry.ProcessTaskRegistration registration = registry.get(taskName);
        return null == registration ? null : registration.getAnnotation();
    }
}

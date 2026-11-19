package com.eric.base.sys.process.task;

import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.process.service.engine.ProcessFlowException;
import com.eric.base.sys.process.task.annotation.ProcessTask;
import com.eric.base.sys.process.task.annotation.ProcessTaskHandler;
import com.eric.base.sys.process.task.enums.ProcessTaskType;
import com.eric.base.sys.process.task.model.ProcessTaskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程任务注册表
 *
 * @author Eric
 * @date 2026/11/19
 * @description @ProcessTask 注册中心（依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md §7.2）：
 *              启动期扫描容器内 Bean，登记「类上标注 {@link ProcessTask} 且实现
 *              {@link ProcessTaskHandler}」的 {@code name → bean}；注解与接口缺一即启动报错
 *              （fail-fast），{@code name} 重复报错，错误信息含类名与任务名。
 *              同时提供模板保存侧校验方法（P17-BE-006 模板图校验调用）：
 *              任务名未注册、{@code processTemplate} 不匹配、{@code allowTaskType} 不含所选步骤。
 */
@Component
public class ProcessTaskRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProcessTaskRegistry.class);

    private final ApplicationContext applicationContext;

    /** 任务名 -> 注册项（登记顺序保持扫描顺序） */
    private final Map<String, ProcessTaskRegistration> registrations = new LinkedHashMap<>();

    public ProcessTaskRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /* ==================== 启动扫描注册（fail-fast） ==================== */

    /**
     * 扫描容器中带 {@link ProcessTask} 注解的 Bean 并登记（启动期调用；可重复调用，按任务名覆盖同源登记）。
     * <p>fail-fast 规则：</p>
     * <ol>
     * <li>标注注解但未实现 {@link ProcessTaskHandler} → 抛异常（含类名与任务名）；</li>
     * <li>任务名为空 → 抛异常；</li>
     * <li>任务名重复（不同实现类） → 抛异常（含任务名与两个类名）；</li>
     * <li>同一实现类重复扫描（如代理） → 幂等跳过。</li>
     * </ol>
     */
    public void scanAndRegister() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(ProcessTask.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            ProcessTask annotation = AnnotationUtils.findAnnotation(ClassUtils.getUserClass(bean.getClass()),
                    ProcessTask.class);
            if (null == annotation) {
                // getBeansWithAnnotation 命中但无法解析注解：明确报错，避免静默漏注册
                throw new IllegalStateException("流程任务注册失败：Bean「" + entry.getKey()
                        + "」标注 @ProcessTask 但无法解析注解，请检查注解保留策略（类: "
                        + ClassUtils.getUserClass(bean.getClass()).getName() + "）");
            }
            String taskName = StringUtil.isEmpty(annotation.name()) ? null : annotation.name().trim();
            if (null == taskName) {
                throw new IllegalStateException("流程任务注册失败：@ProcessTask 的 name 不能为空（类: "
                        + ClassUtils.getUserClass(bean.getClass()).getName() + "）");
            }
            if (!(bean instanceof ProcessTaskHandler handler)) {
                throw new IllegalStateException("流程任务注册失败：任务「" + taskName
                        + "」的实现类 " + ClassUtils.getUserClass(bean.getClass()).getName()
                        + " 标注了 @ProcessTask 但未实现 ProcessTaskHandler 接口");
            }
            register(taskName, handler, annotation);
        }
        log.info("process task scan: registered {} task(s): {}", registrations.size(), registrations.keySet());
    }

    /** 登记单个任务（同任务名同实现类幂等；同任务名不同实现类 fail-fast） */
    private void register(String taskName, ProcessTaskHandler handler, ProcessTask annotation) {
        Class<?> handlerClass = ClassUtils.getUserClass(handler.getClass());
        ProcessTaskRegistration exists = registrations.get(taskName);
        if (null != exists) {
            if (exists.getHandlerClass().equals(handlerClass)) {
                return;
            }
            throw new IllegalStateException("流程任务注册失败：任务名「" + taskName + "」重复注册（已有实现类 "
                    + exists.getHandlerClass().getName() + "，冲突实现类 " + handlerClass.getName() + "）");
        }
        registrations.put(taskName, new ProcessTaskRegistration(taskName, handler, handlerClass, annotation));
        log.info("process task scan: registered task (name={}, class={}, force={}, forceTaskType={},"
                        + " processTemplate={}, allowTaskType={})",
                taskName, handlerClass.getName(), annotation.isForce(), annotation.forceTaskType(),
                Arrays.toString(annotation.processTemplate()), Arrays.toString(annotation.allowTaskType()));
    }

    /* ==================== 查询 ==================== */

    /** 按任务名取注册项（未登记返回 null） */
    public ProcessTaskRegistration get(String taskName) {
        return StringUtil.isEmpty(taskName) ? null : registrations.get(taskName.trim());
    }

    /** 已登记任务名集合（不可变，保持登记顺序） */
    public List<String> names() {
        return List.copyOf(registrations.keySet());
    }

    /** 已登记任务数 */
    public int size() {
        return registrations.size();
    }

    /* ==================== 校验（模板保存侧 / 引擎执行侧共用） ==================== */

    /**
     * 校验节点配置的任务明细行（P17-BE-006 模板图校验调用；引擎执行前同口径复校）。
     * <p>校验项：任务名已注册 → 模板分类匹配 → 执行步骤被允许（空 {@code processTemplate} /
     * 空 {@code allowTaskType} 表示不限）。</p>
     *
     * @param taskDetail 节点配置任务明细（taskBean=已注册任务名、taskType=执行步骤）
     * @param modelName  模板分类（{@code sys_process_template.field_model_name}；空=不做模板匹配）
     * @throws ProcessFlowException 校验不通过（中文错误信息明确）
     */
    public void validateTaskDetail(com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo.TaskDetail taskDetail,
                                   String modelName) {
        if (null == taskDetail) {
            throw ProcessFlowException.task("流程任务配置为空", null);
        }
        ProcessTaskType taskType = ProcessTaskType.parse(taskDetail.getTaskType());
        if (null == taskType) {
            throw ProcessFlowException.task("流程任务「" + describe(taskDetail) + "」的执行步骤非法: "
                    + taskDetail.getTaskType(), null);
        }
        validateTask(taskDetail.getTaskBean(), taskType, modelName);
    }

    /**
     * 校验「任务名 + 执行步骤 + 模板分类」组合是否可执行。
     *
     * @param taskName  任务名称（@ProcessTask name）
     * @param taskType  执行步骤
     * @param modelName 模板分类（可空：空则跳过 processTemplate 匹配）
     * @throws ProcessFlowException 校验不通过
     */
    public void validateTask(String taskName, ProcessTaskType taskType, String modelName) {
        if (null == taskType) {
            throw ProcessFlowException.task("流程任务的执行步骤为空", null);
        }
        ProcessTaskRegistration registration = get(taskName);
        if (null == registration) {
            throw ProcessFlowException.task("流程任务「" + taskName + "」未注册（请检查 @ProcessTask 的 name 与任务类实现），"
                    + "已注册任务: " + names(), null);
        }
        if (!registration.supportsTemplate(modelName)) {
            throw ProcessFlowException.task("流程任务「" + registration.getTaskName() + "」不支持模板分类「" + modelName
                    + "」（可调用模板分类: " + Arrays.toString(registration.getAnnotation().processTemplate()) + "）", null);
        }
        if (registration.getAnnotation().isForce()) {
            if (!taskType.isForceAllowed()) {
                throw ProcessFlowException.task("强制任务「" + registration.getTaskName()
                        + "」只能配置到 StartProcess/EndProcess 步骤，当前步骤: " + taskType, null);
            }
            return;
        }
        if (!registration.supportsTaskType(taskType)) {
            throw ProcessFlowException.task("流程任务「" + registration.getTaskName() + "」不允许配置到步骤 " + taskType
                    + "（允许步骤: " + Arrays.toString(registration.getAnnotation().allowTaskType()) + "）", null);
        }
    }

    /** 取指定步骤的强制任务（按登记顺序；仅 StartProcess/EndProcess 有强制任务） */
    public List<ProcessTaskRegistration> forceTasksOf(ProcessTaskType taskType) {
        if (null == taskType || !taskType.isForceAllowed()) {
            return List.of();
        }
        List<ProcessTaskRegistration> matched = new ArrayList<>();
        for (ProcessTaskRegistration registration : registrations.values()) {
            if (registration.getAnnotation().isForce()
                    && taskType == registration.getAnnotation().forceTaskType()) {
                matched.add(registration);
            }
        }
        return Collections.unmodifiableList(matched);
    }

    private static String describe(com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo.TaskDetail detail) {
        String name = StringUtil.isEmpty(detail.getTaskName()) ? detail.getTaskBean() : detail.getTaskName();
        return StringUtil.isEmpty(name) ? "（未命名）" : name;
    }

    /**
     * 单个流程任务注册项。
     */
    public static final class ProcessTaskRegistration {

        private final String taskName;
        private final ProcessTaskHandler handler;
        private final Class<?> handlerClass;
        private final ProcessTask annotation;

        public ProcessTaskRegistration(String taskName, ProcessTaskHandler handler, Class<?> handlerClass,
                                       ProcessTask annotation) {
            this.taskName = taskName;
            this.handler = handler;
            this.handlerClass = handlerClass;
            this.annotation = annotation;
        }

        /** 执行任务（流转事务内同步执行） */
        public void execute(ProcessTaskContext context) {
            handler.execute(context);
        }

        /** 模板分类是否匹配（注解未配置 processTemplate 表示全模板可用） */
        public boolean supportsTemplate(String modelName) {
            String[] templates = annotation.processTemplate();
            if (null == templates || templates.length == 0 || StringUtil.isEmpty(modelName)) {
                return true;
            }
            for (String template : templates) {
                if (modelName.equals(null == template ? null : template.trim())) {
                    return true;
                }
            }
            return false;
        }

        /** 执行步骤是否被允许（注解未配置 allowTaskType 表示不限） */
        public boolean supportsTaskType(ProcessTaskType taskType) {
            ProcessTaskType[] allowed = annotation.allowTaskType();
            if (null == allowed || allowed.length == 0) {
                return true;
            }
            for (ProcessTaskType type : allowed) {
                if (type == taskType) {
                    return true;
                }
            }
            return false;
        }

        public String getTaskName() {
            return taskName;
        }

        public ProcessTask getAnnotation() {
            return annotation;
        }

        public Class<?> getHandlerClass() {
            return handlerClass;
        }
    }
}

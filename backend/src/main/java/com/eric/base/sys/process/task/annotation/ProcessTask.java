package com.eric.base.sys.process.task.annotation;

import com.eric.base.sys.process.task.enums.ProcessTaskType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 流程任务注解
 *
 * @author Eric
 * @date 2026/11/19
 * @description 标注在流程任务类上，表示该类是一个流程任务类；被标注的类必须同时实现
 *              {@link ProcessTaskHandler}（{@code execute(ProcessTaskContext)} 为任务执行调用方法）。
 *              系统启动时（{@code SysStartupTask} 第十四个启动任务）扫描所有带本注解的 Spring Bean，
 *              登记 {@code name → bean} 到 {@code ProcessTaskRegistry}（注解与接口缺一即启动报错，
 *              {@code name} 重复报错）；节点配置的任务明细（{@code sys_process_template_node
 *              .field_node_config.tasks}）与强制任务在流转各步骤按 name 调起。
 *              <p>命名落定：设计文档《改造分析与方案设计》§7.1 初稿把注解与接口都命名为
 *              {@code ProcessTask}，Java 不允许同包同名，故接口按仓库先例
 *              {@code @SysTask}/{@code SysTaskExecutor} 落定为 {@link ProcessTaskHandler}。</p>
 *              <p>依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md §7.1。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ProcessTask {

    /** 任务名称（唯一，注册键；节点配置按此 name 引用任务） */
    String name();

    /** 可调用的流程模板分类（{@code sys_process_template.field_model_name}），空=全部模板可用 */
    String[] processTemplate() default {};

    /** 强制任务：所有流程实例在 StartProcess/EndProcess 步骤执行，不受节点配置约束 */
    boolean isForce() default false;

    /** 强制任务的执行步骤（仅 StartProcess/EndProcess 生效） */
    ProcessTaskType forceTaskType() default ProcessTaskType.StartProcess;

    /** 非强制任务允许配置到的步骤，空=不限 */
    ProcessTaskType[] allowTaskType() default {};
}

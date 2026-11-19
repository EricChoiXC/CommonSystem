package com.eric.base.sys.process.task.enums;

import com.eric.base.common.utils.StringUtil;

/**
 * 流程任务执行步骤
 *
 * @author Eric
 * @date 2026/11/19
 * @description 流程任务可被触发的流转步骤（对应流转图各步骤），与节点配置
 *              {@code tasks[].taskType}、{@code sys_process_task_log.field_task_type} 同口径存储枚举名。
 *              强制任务（{@link com.eric.base.sys.process.task.annotation.ProcessTask#isForce()}）
 *              仅支持 {@link #StartProcess} 与 {@link #EndProcess} 两个步骤。
 *              依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md §7.1。
 */
public enum ProcessTaskType {

    /** 创建实例 */
    CreateProcess,

    /** 流程发起 */
    StartProcess,

    /** 节点进入 */
    StartNode,

    /** 工作项开始（生成工作项） */
    StartWorkitem,

    /** 发送待办 */
    SendTodo,

    /** 工作项结束（审批操作后） */
    EndWorkitem,

    /** 节点结束 */
    EndNode,

    /** 流程结束 */
    EndProcess;

    /**
     * 按名称解析执行步骤（不区分首尾空白；非法或为空返回 null）。
     *
     * @param name 步骤名称（枚举名）
     * @return 执行步骤；无法解析时返回 {@code null}
     */
    public static ProcessTaskType parse(String name) {
        if (StringUtil.isEmpty(name)) {
            return null;
        }
        String value = name.trim();
        for (ProcessTaskType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return null;
    }

    /** 是否可作为强制任务的执行步骤（仅流程发起与流程结束） */
    public boolean isForceAllowed() {
        return this == StartProcess || this == EndProcess;
    }
}

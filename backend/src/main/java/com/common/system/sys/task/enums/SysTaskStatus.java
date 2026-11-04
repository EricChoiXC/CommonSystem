package com.common.system.sys.task.enums;

import lombok.Getter;

/**
 * 定时任务状态枚举
 *
 * @author Eric
 * @date 2026/8/15
 * @description 对应 sys_task_main.field_status 与 sys_task_example.field_status，
 *              枚举值与 artificial/docs/设计文档/sys/task/定时任务表结构.md 保持一致。
 */
@Getter
public enum SysTaskStatus {

    /** 等待中 */
    WAITING("WAITING", "等待中"),
    /** 运行中 */
    RUNNING("RUNNING", "运行中"),
    /** 完成 */
    FINISH("FINISH", "完成"),
    /** 阻塞 */
    BLOCK("BLOCK", "阻塞"),
    /** 失败 */
    FAIL("FAIL", "失败");

    private final String value;

    private final String label;

    SysTaskStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 按实际值解析枚举；未知值返回 null
     */
    public static SysTaskStatus of(String value) {
        for (SysTaskStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }
}

package com.common.system.sys.task.enums;

import lombok.Getter;

/**
 * 定时任务执行结果枚举
 *
 * @author Eric
 * @date 2026/8/15
 * @description 对应 sys_task_history.field_result，枚举值与
 *              artificial/docs/设计文档/sys/task/定时任务表结构.md 保持一致。
 */
@Getter
public enum SysTaskResult {

    /** 成功 */
    SUCCESS("1", "成功"),
    /** 失败 */
    FAIL("0", "失败");

    private final String value;

    private final String label;

    SysTaskResult(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 按实际值解析枚举；未知值返回 null
     */
    public static SysTaskResult of(String value) {
        for (SysTaskResult result : values()) {
            if (result.value.equals(value)) {
                return result;
            }
        }
        return null;
    }
}

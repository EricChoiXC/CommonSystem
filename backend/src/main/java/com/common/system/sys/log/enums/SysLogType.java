package com.common.system.sys.log.enums;

import lombok.Getter;

/**
 * 日志类型枚举
 *
 * @author Eric
 * @date 2026/8/8
 * @description 对应 sys_log_operation.field_log_type，
 *              枚举值与 artificial/docs/设计文档/sys/log/日志表结构.md 保持一致。
 */
@Getter
public enum SysLogType {

    /** 操作日志 */
    OPERATION("1", "操作日志"),
    /** 登录日志 */
    LOGIN("2", "登录日志");

    private final String value;

    private final String label;

    SysLogType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 按实际值解析枚举；未知值返回 null
     */
    public static SysLogType of(String value) {
        for (SysLogType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}

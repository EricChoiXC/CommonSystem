package com.common.system.sys.log.enums;

import lombok.Getter;

/**
 * 日志结果枚举
 *
 * @author Eric
 * @date 2026/8/8
 * @description 对应 sys_log_operation.field_result，
 *              枚举值与 artificial/docs/设计文档/sys/log/日志表结构.md 保持一致。
 */
@Getter
public enum SysLogResult {

    /** 成功 */
    SUCCESS("SUCCESS", "成功"),
    /** 失败 */
    FAILED("FAILED", "失败");

    private final String value;

    private final String label;

    SysLogResult(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 按实际值解析枚举；未知值返回 null
     */
    public static SysLogResult of(String value) {
        for (SysLogResult result : values()) {
            if (result.value.equals(value)) {
                return result;
            }
        }
        return null;
    }
}

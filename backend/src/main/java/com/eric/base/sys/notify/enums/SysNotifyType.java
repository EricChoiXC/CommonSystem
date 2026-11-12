package com.eric.base.sys.notify.enums;

import lombok.Getter;

/**
 * 通知类型枚举
 *
 * @author Eric
 * @date 2026/8/12
 * @description 对应 sys_notify_todo.field_notify_type，
 *              枚举值与 artificial/docs/设计文档/sys/notify/通知待办表结构.md 保持一致。
 */
@Getter
public enum SysNotifyType {

    /** 待办 */
    TODO("1", "待办"),
    /** 待阅 */
    READ("2", "待阅");

    private final String value;

    private final String label;

    SysNotifyType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 按实际值解析枚举；未知值返回 null
     */
    public static SysNotifyType of(String value) {
        for (SysNotifyType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}

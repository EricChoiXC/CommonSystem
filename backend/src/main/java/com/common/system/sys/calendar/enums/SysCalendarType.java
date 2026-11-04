package com.common.system.sys.calendar.enums;

import lombok.Getter;

/**
 * 日程类型枚举
 *
 * @author Eric
 * @date 2026/8/21
 * @description 对应 sys_calendar_main.field_type
 */
@Getter
public enum SysCalendarType {

    /** 个人日程 */
    PERSONAL("PERSONAL", "个人日程"),
    /** 团队日程 */
    TEAM("TEAM", "团队日程");

    private final String value;

    private final String label;

    SysCalendarType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 按实际值解析枚举；未知值返回 null
     */
    public static SysCalendarType of(String value) {
        for (SysCalendarType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
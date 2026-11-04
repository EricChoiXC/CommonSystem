package com.common.system.sys.org.enums;

import lombok.Getter;

/**
 * @author Eric
 * @date 2026/8/5
 * @description
 */
@Getter
public enum SysOrgJobStatus {

    WORKING(1, "在职"),
    LEAVE(0, "离职"),
    STOP(2, "停职"),
    HOLIDAY(3, "休假");

    private final int value;
    private final String label;

    SysOrgJobStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }
}

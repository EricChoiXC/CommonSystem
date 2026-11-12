package com.eric.base.sys.org.enums;

import lombok.Getter;

/**
 * @author Eric
 * @date 2026/8/5
 * @description
 */
@Getter
public enum SysOrgAccountStatus {

    // 正常、锁定、密码过期、禁用、注销
    NORMAL(1, "正常"),
    LOCKED(2, "锁定"),
    PWD_EXPIRED(3, "密码过期"),
    DISABLED(0, "禁用"),
    CANCELED(4, "注销");

    private final int value;
    private final String label;

    SysOrgAccountStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }
}

package com.common.system.common.base.enums;

import lombok.Getter;

/**
 * @author Eric
 * @date 2026/8/5
 * @description
 */
@Getter
public enum BooleanEnum {

    YES(1, true, "是"),
    NO(0, false, "否");

    @Getter
    private final int value;
    private final boolean bool;
    @Getter
    private final String label;

    BooleanEnum(int value, boolean bool, String label) {
        this.value = value;
        this.bool = bool;
        this.label = label;
    }

    public boolean getBool() {
        return bool;
    }

}

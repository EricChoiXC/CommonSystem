package com.eric.base.common.base.enums;

import lombok.Getter;

/**
 * @author Eric
 * @date 2026/8/5
 * @description
 */
@Getter
public enum GenderEnum {

    MALE(1, "男"),
    FEMALE(2, "女"),
    UNKNOWN(0, "其他");

    private final int value;
    private final String label;

    GenderEnum(int value, String label) {
        this.value = value;
        this.label = label;
    }
}

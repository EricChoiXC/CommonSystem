package com.common.system.common.utils;

public class StringUtil {

    public static boolean isEmpty (String str) {
        if (null == str || str.isBlank()) {
            return true;
        }
        return false;
    }

    public static boolean isNotEmpty (String str) {
        return !isEmpty(str);
    }

    /**
     * 空白字符串转 null（保留首尾空格清理）。
     */
    public static String trimToNull(String value) {
        return isEmpty(value) ? null : value.trim();
    }

    /**
     * 必填文本校验：空值时抛出 {@link IllegalArgumentException}，否则返回去除首尾空格后的值。
     */
    public static String requireText(String value, String fieldName) {
        if (isEmpty(value)) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}

package com.eric.base.common.utils;

import java.util.UUID;

/**
 * ID工具类
 *
 * @author Eric
 * @date 2026/7/30
 * @description ID工具类
 */
public class IDUtil {

    /**
     * ID长度
     */
    private static final Integer ID_LENGTH = 32;

    /**
     * 初始化ID
     *
     * @return ID
     */
    public static String initID() {
        return generate();
    }

    /**
     * 校验并初始化ID
     *
     * @param id 校验ID
     * @return 返回ID
     */
    public static String initID(String id) {
        return (StringUtil.isEmpty(id) || id.length() != ID_LENGTH) ? initID() : id;
    }

    /**
     * 生成ID
     *
     * @return ID
     */
    private static String generate() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        StringBuilder builder = new StringBuilder(timestamp);
        builder.append(uuid);
        return builder.substring(0, ID_LENGTH);
    }
}

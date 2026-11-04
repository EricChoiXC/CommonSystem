package com.common.system.sys.org.utils;

/**
 * 当前用户持有者
 *
 * @author Eric
 * @date 2026/8/3
 * @description 基于 ThreadLocal 保存当前请求上下文的用户 ID
 */
public class CurrentUserHolder {

    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前用户 ID
     *
     * @param userId 用户 ID
     */
    public static void set(String userId) {
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取当前用户 ID
     *
     * @return 当前用户 ID，未设置时返回 null
     */
    public static String get() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 清除当前用户 ID
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}

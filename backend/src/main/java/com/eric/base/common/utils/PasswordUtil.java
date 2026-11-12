package com.eric.base.common.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密工具
 *
 * @author Eric
 * @date 2026/8/7
 * @description 基于 Spring Security 的 BCrypt 单向加密（自带随机盐、不可逆）。
 *              sys_org_user.field_password 一律以本工具加密后入库，明文不落库，
 *              任何查询列集合都不允许返回密码字段。
 */
public class PasswordUtil {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {
    }

    /**
     * 加密明文密码；BCrypt 每次生成随机盐，同一明文两次加密结果不同
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希串
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文密码与已加密密码是否匹配
     *
     * @param rawPassword     明文密码
     * @param encodedPassword 数据库中存储的 BCrypt 哈希串
     * @return true 匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}

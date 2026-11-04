package com.common.system.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码加密工具测试
 *
 * @author Eric
 * @date 2026/8/7
 */
class PasswordUtilTest {

    private static final String RAW = "Abc@123456";

    @Test
    void encodeProducesBCryptHash() {
        String encoded = PasswordUtil.encode(RAW);

        assertNotEquals(RAW, encoded);
        // BCrypt 哈希以 $2a/$2b/$2y 开头，长度 60
        assertTrue(encoded.startsWith("$2"));
        assertEquals(60, encoded.length());
    }

    @Test
    void matchesReturnsTrueForCorrectPassword() {
        String encoded = PasswordUtil.encode(RAW);

        assertTrue(PasswordUtil.matches(RAW, encoded));
    }

    @Test
    void matchesReturnsFalseForWrongPassword() {
        String encoded = PasswordUtil.encode(RAW);

        assertFalse(PasswordUtil.matches("wrong-password", encoded));
    }

    @Test
    void sameRawPasswordEncodesDifferently() {
        // BCrypt 每次生成随机盐，同一明文两次加密结果不同
        assertNotEquals(PasswordUtil.encode(RAW), PasswordUtil.encode(RAW));
    }
}

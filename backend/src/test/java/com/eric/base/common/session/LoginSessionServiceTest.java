package com.eric.base.common.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 登录会话服务测试
 *
 * @author Eric
 * @date 2026/8/7
 */
class LoginSessionServiceTest {

    private LoginSessionService service;

    @BeforeEach
    void setUp() {
        service = new LoginSessionService();
    }

    @Test
    void createAndResolveReturnsUserId() {
        String token = service.create("user-1");

        assertNotEquals("user-1", token);
        assertEquals("user-1", service.resolve(token));
    }

    @Test
    void createIssuesDistinctTokens() {
        assertNotEquals(service.create("user-1"), service.create("user-1"));
    }

    @Test
    void resolveRejectsBlankOrUnknownToken() {
        assertNull(service.resolve(null));
        assertNull(service.resolve("  "));
        assertNull(service.resolve("unknown-token"));
    }

    @Test
    void removeInvalidatesToken() {
        String token = service.create("user-1");

        service.remove(token);

        assertNull(service.resolve(token));
    }

    @Test
    void removeIsIdempotent() {
        service.remove("not-exists");
        service.remove(null);
    }

    @Test
    void expiredTokenIsRejectedAndCleaned() throws InterruptedException {
        // 10ms 的短 TTL 会话，验证过期即失效
        LoginSessionService shortTtl = new LoginSessionService(10L);
        String token = shortTtl.create("user-1");
        Thread.sleep(30);

        assertNull(shortTtl.resolve(token));
        assertNull(shortTtl.resolve(token));
    }
}

package com.eric.base.common.config;

import com.eric.base.common.session.LoginSessionService;
import com.eric.base.sys.org.utils.CurrentUserHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 当前用户过滤器测试
 *
 * @author Eric
 * @date 2026/8/7
 */
class CurrentUserFilterTest {

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    void validTokenInjectsCurrentUserDuringChain() throws Exception {
        LoginSessionService sessionService = new LoginSessionService();
        String token = sessionService.create("user-1");
        CurrentUserFilter filter = new CurrentUserFilter(sessionService);

        HttpServletRequest request = requestWithToken(token);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        AtomicReference<String> holderDuringChain = new AtomicReference<>();
        doAnswer(invocation -> {
            holderDuringChain.set(CurrentUserHolder.get());
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        // 链执行期间已注入当前用户
        assertEquals("user-1", holderDuringChain.get());
        // 链执行结束后 ThreadLocal 已清理，避免请求间串号
        assertNull(CurrentUserHolder.get());
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingTokenDoesNotInject() throws Exception {
        CurrentUserFilter filter = new CurrentUserFilter(new LoginSessionService());

        filter.doFilter(requestWithToken(null), mock(HttpServletResponse.class), mock(FilterChain.class));

        assertNull(CurrentUserHolder.get());
    }

    @Test
    void invalidTokenDoesNotInject() throws Exception {
        CurrentUserFilter filter = new CurrentUserFilter(new LoginSessionService());

        filter.doFilter(requestWithToken("unknown-token"), mock(HttpServletResponse.class), mock(FilterChain.class));

        assertNull(CurrentUserHolder.get());
    }

    @Test
    void clearsCurrentUserEvenWhenChainThrows() throws Exception {
        LoginSessionService sessionService = new LoginSessionService();
        String token = sessionService.create("user-1");
        CurrentUserFilter filter = new CurrentUserFilter(sessionService);

        HttpServletRequest request = requestWithToken(token);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("chain failed")).when(chain).doFilter(request, response);

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, chain));
        assertNull(CurrentUserHolder.get());
    }

    private static HttpServletRequest requestWithToken(String token) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(CurrentUserFilter.LOGIN_TOKEN_HEADER)).thenReturn(token);
        return request;
    }
}

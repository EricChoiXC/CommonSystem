package com.common.system.common.config;

import com.common.system.common.session.LoginSessionService;
import com.common.system.sys.org.utils.CurrentUserHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 当前用户过滤器
 *
 * @author Eric
 * @date 2026/8/7
 * @description 从请求头 {@code X-Login-Token} 解析登录凭证，将用户 ID 注入
 *              {@link CurrentUserHolder}（ThreadLocal），请求结束清理。
 *              无凭证或凭证无效时不注入（保持未登录状态），由各接口 Resolver 完成最终鉴权。
 *              注册方式与生效路径见 {@link SecurityConfig#currentUserFilterRegistration}。
 */
public class CurrentUserFilter extends OncePerRequestFilter {

    /** 登录凭证请求头 */
    public static final String LOGIN_TOKEN_HEADER = "X-Login-Token";

    private final LoginSessionService loginSessionService;

    public CurrentUserFilter(LoginSessionService loginSessionService) {
        this.loginSessionService = loginSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(LOGIN_TOKEN_HEADER);
        String userId = loginSessionService.resolve(token);
        if (userId != null) {
            CurrentUserHolder.set(userId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            CurrentUserHolder.clear();
        }
    }
}

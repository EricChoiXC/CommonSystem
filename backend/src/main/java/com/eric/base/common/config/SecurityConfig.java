package com.eric.base.common.config;

import com.eric.base.common.session.LoginSessionService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 基础配置
 *
 * <p>当前阶段仅引入 Spring Security 框架能力（CSRF 防护、过滤器链等），
 * 业务鉴权仍遵循「接口 + 文档ID」模式，由各接口对应的 Resolver 完成，
 * 因此此处放行所有请求，避免默认认证拦截影响现有接口。
 * 后续接入统一登录认证时，再在此处收紧放行规则。
 *
 * @author Eric
 * @date 2026/8/7
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * 注册当前用户过滤器：仅对 /backend/** 生效，负责从 X-Login-Token 解析并填充当前用户。
     * 使用 FilterRegistrationBean 显式注册，避免 @Component 自动注册到所有 URL。
     */
    @Bean
    public FilterRegistrationBean<CurrentUserFilter> currentUserFilterRegistration(
            LoginSessionService loginSessionService) {
        FilterRegistrationBean<CurrentUserFilter> registration =
                new FilterRegistrationBean<>(new CurrentUserFilter(loginSessionService));
        registration.addUrlPatterns("/backend/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}

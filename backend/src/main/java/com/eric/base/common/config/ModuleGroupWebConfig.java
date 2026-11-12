package com.eric.base.common.config;

import com.eric.base.common.module.interceptor.ModuleGroupInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 模块组开关拦截器注册配置
 *
 * <p>设计依据 docs/v1_1-common/05-增加接口开关/接口开关机制设计方案-修订稿.md 第 8.1 节，
 * Web 配置集中在 config 包，与 {@link SecurityConfig} / {@link SpaWebConfig} 同层。</p>
 *
 * <p>注册范围 {@code /backend/**}，与 application.properties 的
 * {@code project.request.prefix=/backend} 默认值一致（所有业务接口均在该前缀下）。
 * 不配置 excludePathPatterns：拦截器自身按「无注解 = 放行」判定，
 * 登录、/init、/initID、setting/load、SSE 订阅、swagger 等公共入口均无注解，天然不受拦截，
 * 无需枚举排除项；swagger 等非 HandlerMethod 请求也不在拦截范围。</p>
 *
 * @author Eric
 * @date 2026/11/12
 * @description 将 ModuleGroupInterceptor 注册到 Spring MVC 拦截器链，仅覆盖 /backend/** 接口路径。
 */
@Configuration
public class ModuleGroupWebConfig implements WebMvcConfigurer {

    /**
     * 接口统一前缀（与 application.properties 的 project.request.prefix 默认值一致）
     */
    private static final String BACKEND_PATH_PATTERN = "/backend/**";

    private final ModuleGroupInterceptor moduleGroupInterceptor;

    public ModuleGroupWebConfig(ModuleGroupInterceptor moduleGroupInterceptor) {
        this.moduleGroupInterceptor = moduleGroupInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(moduleGroupInterceptor)
                .addPathPatterns(BACKEND_PATH_PATTERN);
    }
}

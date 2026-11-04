package com.common.system.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 前端 SPA 路由回退配置
 *
 * <p>前端使用 Vue Router history 模式（见 artificial/frontend/src/router/index.js），
 * 构建产物输出到 classpath:/static/（见 artificial/frontend/vite.config.js 的 build.outDir）。
 * 直接访问 /login、/ui 等前端路由路径时，Spring MVC 没有对应控制器或静态文件，
 * 默认会返回 404；本配置通过自定义静态资源解析器，将这类请求回退到 index.html，
 * 由前端路由接管渲染，保证「非 /backend 请求直接访问前端对应页面」可用。</p>
 *
 * <p>回退规则：</p>
 * <ul>
 *   <li>静态资源真实存在（assets/*.js、*.css、index.html 等）→ 原样返回；</li>
 *   <li>路径以 /backend 开头 → 不回退，保持接口原有处理路径；</li>
 *   <li>路径带文件扩展名且资源不存在 → 不回退，避免把 HTML 内容当作 JS/CSS 等资源返回；</li>
 *   <li>其余路径（前端路由，如 /login、/ui、多级路由）→ 回退到 index.html。</li>
 * </ul>
 *
 * @author Eric
 * @date 2026/8/7
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    /**
     * 前端构建产物目录（与 artificial/frontend/vite.config.js 的 build.outDir 保持一致）
     */
    private static final String FRONTEND_STATIC_LOCATION = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(FRONTEND_STATIC_LOCATION)
                // Spring 6.2 起 addResolver 需通过 resourceChain 注册；不启用资源缓存
                .resourceChain(false)
                .addResolver(new SpaPathResourceResolver());
    }

    /**
     * 静态资源解析器：资源存在时原样返回；否则把前端路由请求回退到 index.html，
     * 使直接访问 /login、/ui 等页面路径时由前端路由接管渲染。
     */
    private static class SpaPathResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource resource = location.createRelative(resourcePath);
            if (resource.exists() && resource.isReadable() && checkResource(resource, location)) {
                return resource;
            }
            if (isFrontendRoute(resourcePath)) {
                Resource indexHtml = location.createRelative("index.html");
                if (indexHtml.exists() && indexHtml.isReadable()) {
                    return indexHtml;
                }
            }
            return null;
        }

        /**
         * 判断该路径是否为需要回退到 index.html 的前端路由路径。
         */
        private boolean isFrontendRoute(String resourcePath) {
            if (resourcePath == null || resourcePath.isEmpty()) {
                return false;
            }
            // 后端接口统一以 /backend 开头，保持原有处理路径，不回退到前端页面
            if (resourcePath.equals("backend") || resourcePath.startsWith("backend/")) {
                return false;
            }
            // 带文件扩展名的路径（如 assets/xxx.js）资源不存在时返回 404，
            // 避免浏览器把 HTML 内容当作 JS/CSS 等资源解析
            return !resourcePath.matches(".*\\.[a-zA-Z0-9]{1,10}$");
        }
    }
}

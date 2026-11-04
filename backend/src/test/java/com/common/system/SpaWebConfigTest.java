package com.common.system;

import com.common.system.CommonSystemApplicationTests.MockMapperConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 前端 SPA 路由回退测试
 *
 * <p>验证 SpaWebConfig 的行为：非 /backend 的前端路由路径（/login、/ui 等）
 * 直接访问时返回 index.html，由前端路由接管渲染；真实静态资源仍原样返回；
 * /backend 接口路径与带扩展名的缺失资源不被回退成前端页面。</p>
 * <p>与 CommonSystemApplicationTests 一致：排除 DataSource 与 MyBatis 自动配置并以 mock 提供 Mapper bean，
 * 主配置 application.properties 正常加载（含 spring.config.import 引入的 SETTING 公共配置）。</p>
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@AutoConfigureMockMvc
@Import(MockMapperConfig.class)
class SpaWebConfigTest {

    /** 前端 index.html 的标志性内容，用于区分「回退到前端页面」与「接口/资源响应」 */
    private static final String APP_ROOT_MARK = "<div id=\"app\">";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootReturnsFrontendIndex() throws Exception {
        // 欢迎页将 / 转发到 index.html（MockMvc 不执行 forward，仅记录转发目标；
        // 真实 Servlet 容器中会渲染出前端页面）
        MvcResult result = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("index.html", result.getResponse().getForwardedUrl());
    }

    @Test
    void spaRouteReturnsFrontendIndex() throws Exception {
        // 单级前端路由
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(APP_ROOT_MARK)));
        mockMvc.perform(get("/ui"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(APP_ROOT_MARK)));
        // 多级前端路由
        mockMvc.perform(get("/customer/list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(APP_ROOT_MARK)));
    }

    @Test
    void realStaticResourceIsServed() throws Exception {
        // 真实存在的静态资源仍按原样返回，不回退
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString(APP_ROOT_MARK)));
    }

    @Test
    void backendApiPathIsNotRoutedToFrontend() throws Exception {
        // /backend 接口路径不参与 SPA 回退，保持原有处理路径（不会返回前端页面）
        mockMvc.perform(get("/backend/not/exist"))
                .andExpect(content().string(not(containsString(APP_ROOT_MARK))));
    }

    @Test
    void missingStaticFileWithExtensionIsNotRoutedToFrontend() throws Exception {
        // 带扩展名且不存在的资源不冒充 index.html，避免 HTML 被当作 JS/CSS 解析
        mockMvc.perform(get("/assets/__missing__.js"))
                .andExpect(content().string(not(containsString(APP_ROOT_MARK))));
    }

    @Test
    void apiDocsEndpointIsNotRoutedToFrontend() throws Exception {
        // OpenAPI 文档端点不参与 SPA 回退：启用时返回 JSON，关闭时返回 404，都不会是前端页面
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(content().string(not(containsString(APP_ROOT_MARK))));
    }

    @Test
    void swaggerUiPathIsNotRoutedToFrontend() throws Exception {
        // Swagger UI 静态资源路径同样不参与 SPA 回退，避免文档页面被前端路由接管
        mockMvc.perform(get("/swagger-ui/__missing__.css"))
                .andExpect(content().string(not(containsString(APP_ROOT_MARK))));
        mockMvc.perform(get("/swagger-ui-x"))
                .andExpect(content().string(not(containsString(APP_ROOT_MARK))));
    }
}

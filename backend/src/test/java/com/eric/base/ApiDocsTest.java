package com.eric.base;

import com.eric.base.CommonSystemApplicationTests.MockMapperConfig;
import com.eric.base.common.config.OpenApiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 在线接口文档（springdoc-openapi）启用态测试
 *
 * <p>验证 {@code GET /v3/api-docs} 能生成 OpenAPI 3 JSON，包含文档元信息、全局安全方案，
 * 且按 {@code springdoc.paths-to-match=/backend/**} 只收录业务接口并携带 Controller 上的中文摘要；
 * Swagger UI 入口可访问。开关关闭时的行为见 {@link ApiDocsDisabledTest}。</p>
 * <p>与 CommonSystemApplicationTests 一致：排除 DataSource 与 MyBatis 自动配置并以 mock 提供 Mapper bean。</p>
 *
 * @author Eric
 * @date 2026/11/4
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@AutoConfigureMockMvc
@Import(MockMapperConfig.class)
class ApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 文档 JSON 可生成，并包含公共信息与登录凭证安全方案。
     *
     * @throws Exception MockMvc 请求异常
     */
    @Test
    void apiDocsIsGeneratedWithInfoAndSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("公共系统 API")))
                .andExpect(content().string(containsString(OpenApiConfig.SECURITY_SCHEME_NAME)))
                .andExpect(content().string(containsString(OpenApiConfig.LOGIN_TOKEN_HEADER)));
    }

    /**
     * 只收录 /backend/** 下的业务接口，并带上 Controller 的中文摘要。
     *
     * @throws Exception MockMvc 请求异常
     */
    @Test
    void apiDocsContainsBackendPathsWithChineseSummary() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/backend/sys/org/element/list")))
                .andExpect(content().string(containsString("组织架构元素列表")))
                .andExpect(content().string(containsString("/backend/sys/notify/sse/subscribe")));
    }

    /**
     * Swagger UI 入口可访问（springdoc 会把 /swagger-ui.html 重定向到静态页面）。
     *
     * @throws Exception MockMvc 请求异常
     */
    @Test
    void swaggerUiEntryIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}

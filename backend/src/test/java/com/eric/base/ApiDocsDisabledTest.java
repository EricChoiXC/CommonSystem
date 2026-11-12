package com.eric.base;

import com.eric.base.CommonSystemApplicationTests.MockMapperConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * 在线接口文档（springdoc-openapi）关闭态测试
 *
 * <p>以 {@code springdoc.api-docs.enabled=false} 与 {@code springdoc.swagger-ui.enabled=false}
 *（等价于环境变量 {@code CS_API_DOCS_ENABLED=false}）启动上下文，验证两个文档端点不再输出文档内容，
 * 即生产环境可用该开关整体下线文档端点。</p>
 *
 * <p>断言只看「是否还输出文档内容」，不锁定状态码：文档端点关闭后请求落到静态资源处理器，
 * 最终由 {@code GlobalExceptionHandler} 兜底转换为统一的 {@code BaseResponse}（既有行为，本次不改）。</p>
 *
 * @author Eric
 * @date 2026/11/4
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc
@Import(MockMapperConfig.class)
class ApiDocsDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 关闭后不再生成 OpenAPI JSON（响应中无 openapi 字段与文档标题）。
     *
     * @throws Exception MockMvc 请求异常
     */
    @Test
    void apiDocsIsNotGeneratedWhenDisabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(content().string(not(containsString("\"openapi\""))))
                .andExpect(content().string(not(containsString("公共系统 API"))));
    }

    /**
     * 关闭后 Swagger UI 入口不再返回文档页面。
     *
     * @throws Exception MockMvc 请求异常
     */
    @Test
    void swaggerUiIsNotServedWhenDisabled() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(content().string(not(containsString("swagger-ui"))));
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(content().string(not(containsString("Swagger UI"))));
    }
}

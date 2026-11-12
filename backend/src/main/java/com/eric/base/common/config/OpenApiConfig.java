package com.eric.base.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档（OpenAPI 3 + Swagger UI）全局配置
 *
 * <p>在线文档地址：</p>
 * <ul>
 *   <li>{@code GET /swagger-ui.html}：Swagger UI 交互页面；</li>
 *   <li>{@code GET /v3/api-docs}：OpenAPI 3 JSON，可导入 Apifox / Postman 等工具。</li>
 * </ul>
 *
 * <p>文档端点由 {@code springdoc.*} 配置控制，统一开关为环境变量 {@code CS_API_DOCS_ENABLED}
 *（默认开启），见 {@code application.properties}；关闭后两个端点均不可访问。</p>
 *
 * <p>鉴权约定：本项目采用「接口标识（action）+ 文档 ID」的业务鉴权，登录凭证为请求头
 * {@value #LOGIN_TOKEN_HEADER}。此处把该请求头注册为全局 {@code SecurityScheme}，
 * 使 Swagger UI 上的「Authorize」按钮可一次性带上凭证；登录、登出、初始化接口与 SSE 订阅
 * 不依赖该请求头，对应方法上用 {@code @SecurityRequirements} 标注为空以取消全局要求。</p>
 *
 * @author Eric
 * @date 2026/11/4
 */
@Configuration
public class OpenApiConfig {

    /** 安全方案名称：Swagger UI 中「Authorize」入口的名称，也用于方法级安全要求引用 */
    public static final String SECURITY_SCHEME_NAME = "LoginToken";

    /** 登录凭证请求头名称，与 {@link CurrentUserFilter} 保持一致 */
    public static final String LOGIN_TOKEN_HEADER = CurrentUserFilter.LOGIN_TOKEN_HEADER;

    /**
     * 构建全局 OpenAPI 文档：填写文档元信息，注册登录凭证安全方案并作为所有接口的默认鉴权要求。
     *
     * @return OpenAPI 文档定义
     */
    @Bean
    public OpenAPI commonSystemOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("公共系统 API")
                        .version("1.0.0")
                        .description("""
                                公共模块工程（登录认证、组织架构、附件、日程、码值、日志、通知待办、\
                                流程审批、定时任务、公共配置）的后端接口文档。

                                路径约定：/backend/{根模块}/{模块}/{次级模块}/{方法}，\
                                {根模块} 只有 common 与 sys。读取类接口单 id 或无参用 GET、\
                                含非 id 条件用 POST；更新只用 PATCH；删除用 DELETE。

                                响应约定：所有接口统一返回 BaseResponse（code / message / data / metadata），\
                                data 仅承载业务数据，总数、token、可见性等附加信息经 metadata 返回；\
                                异常由 GlobalExceptionHandler 转换为同一结构。"""))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(LOGIN_TOKEN_HEADER)
                                .description("登录接口签发的会话凭证，放在请求头 "
                                        + LOGIN_TOKEN_HEADER + " 中")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}

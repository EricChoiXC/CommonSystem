package com.eric.base.sys.i18n.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.sys.i18n.properties.SysI18nProperties;
import com.eric.base.sys.i18n.resolver.I18nResolver;
import com.eric.base.sys.i18n.service.SysI18nDeployService;
import com.eric.base.sys.i18n.response.SysI18nDeployResultVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.eric.base.sys.i18n.properties.SysI18nProperties.ACTION_DEPLOY_RUN;

/**
 * 多语言-部署接口
 *
 * @author Eric
 * @date 2026/11/5
 * @description 部署页的请求入口（任务清单 I18N5-BE-001）：部署动作 = 手动触发与启动同规则的
 *              「资源 → DB 同步」→ 刷新后端缓存，返回同步资源数与各语言翻译条数；
 *              仅 SYS_I18N_ADMIN（超管 SYS_ADMIN 由 I18nResolver 统一放行）；
 *              D2 决策下语言包为动态接口（/locales/{code}.json），缓存刷新即生效，无产物落盘。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/i18n/deploy")
@Tag(name = "多语言-部署", description = "部署动作：手动触发资源→DB 同步并刷新缓存（仅 SYS_I18N_ADMIN）；"
        + "语言包为动态接口 /locales/{code}.json，部署后前端下次拉取即新值")
public class SysI18nDeployController {

    private final SysI18nDeployService deployService;
    private final I18nResolver baseResolver;
    private final AuthorizationHelper authorizationHelper;

    public SysI18nDeployController(SysI18nDeployService deployService,
                                   I18nResolver baseResolver,
                                   AuthorizationHelper authorizationHelper) {
        this.deployService = deployService;
        this.baseResolver = baseResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 执行部署：POST /backend/sys/i18n/deploy/run
     * 手动触发与启动同规则的「资源 → DB 同步」→ 刷新后端缓存；
     * 返回同步资源数与各语言翻译条数（无产物落盘，前端语言包经动态接口取新值）。
     *
     * @return 统一响应，data 为同步结果
     */
    @PostMapping("/run")
    @Operation(summary = "执行部署", description = "鉴权动作 " + SysI18nProperties.ACTION_DEPLOY_RUN
            + "（仅 SYS_I18N_ADMIN）；手动触发与启动同规则的「资源 → DB 同步」→ 刷新后端缓存；"
            + "返回同步资源数与各语言翻译条数（D2：语言包为动态接口，缓存刷新即生效，无产物落盘）")
    public BaseResponse<SysI18nDeployResultVo> deploy() {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_DEPLOY_RUN));
        return BaseResponse.success(deployService.deploy(), null);
    }
}

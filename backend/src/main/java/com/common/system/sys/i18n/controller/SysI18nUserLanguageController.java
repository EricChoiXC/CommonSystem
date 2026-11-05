package com.common.system.sys.i18n.controller;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.resolver.AuthorizationHelper;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.i18n.properties.SysI18nProperties;
import com.common.system.sys.i18n.request.SysI18nUserLanguageRequest;
import com.common.system.sys.i18n.resolver.I18nResolver;
import com.common.system.sys.i18n.service.SysI18nUserLanguageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.common.system.sys.i18n.properties.SysI18nProperties.ACTION_USER_LANGUAGE_UPDATE;

/**
 * 多语言-用户语言接口
 *
 * @author Eric
 * @date 2026/11/5
 * @description 当前登录用户语言登记入口（分析报告「六」第 14 个接口）：
 *              只负责接收请求、执行最终鉴权并调用 Service 返回统一响应。
 *              body 仅携带 fieldLanguage（语言码），对当前登录用户 upsert 用户语言表
 *              （一人一条；存在更新、不存在插入）；无效语言码由服务按 A4 忽略并回退默认语言；
 *              顶部栏语言切换为前端行为（locale + localStorage），不经本接口。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/i18n/user")
@Tag(name = "多语言-用户语言", description = "当前登录用户语言登记：登录页选语言登录、登录后语言偏好落库（顶部栏切换不落库）")
public class SysI18nUserLanguageController {

    private final SysI18nUserLanguageService userLanguageService;
    private final I18nResolver baseResolver;
    private final AuthorizationHelper authorizationHelper;

    public SysI18nUserLanguageController(SysI18nUserLanguageService userLanguageService,
                                         I18nResolver baseResolver,
                                         AuthorizationHelper authorizationHelper) {
        this.userLanguageService = userLanguageService;
        this.baseResolver = baseResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 当前登录用户语言更新：PATCH /backend/sys/i18n/user/language/update
     * 请求体仅 fieldLanguage（语言码）；登录即可（登录页未选语言时后端回退默认语言，
     * 服务侧无效语言码忽略并回退默认语言，A4）。
     *
     * @param request 请求体 { fieldLanguage }
     * @return 统一响应
     */
    @PatchMapping("/language/update")
    @SecurityRequirements
    @Operation(summary = "当前用户语言更新", description = "鉴权动作 sys.i18n.user.languageUpdate（登录即可）；"
            + "请求体仅 { fieldLanguage }（语言码），对当前登录用户 upsert 用户语言表（一人一条）；"
            + "无效语言码忽略并回退默认语言，不报错")
    public BaseResponse<Void> languageUpdate(@RequestBody(required = false) SysI18nUserLanguageRequest request) {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_USER_LANGUAGE_UPDATE));
        String language = null == request ? null : request.getFieldLanguage();
        userLanguageService.upsertLanguage(UserUtil.getCurrentUserId(), language);
        return BaseResponse.<Void>success("success", null, null);
    }
}

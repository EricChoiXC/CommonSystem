package com.eric.base.sys.number.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.sys.number.model.SysNumberPreviewVo;
import com.eric.base.sys.number.request.SysNumberPreviewRequest;
import com.eric.base.sys.number.resolver.NumberResolver;
import com.eric.base.sys.number.service.SysNumberPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.eric.base.sys.number.properties.SysNumberConstants.*;

/**
 * 编号预览渲染接口
 *
 * @author Eric
 * @date 2026/11/13
 * @description 编号机制模块预览面的请求入口（任务 NB3-BE-004，方案 §3.1/§3.2 preview 组）：
 *              只负责接收请求、执行最终鉴权并调用 {@link SysNumberPreviewService} 返回统一
 *              响应，预览逻辑全部在 Service。预览无副作用（不推进计数器、不落任何记录），
 *              鉴权登录即可（D6，NumberRuleComposer 宿主复用预览不 403，§4.3）；
 *              本 Service 未实现 IBaseService，不走 BaseCrudController 骨架，
 *              鉴权经 {@link AuthorizationHelper} 统一入口（未登录抛 UNAUTHORIZED、
 *              无权限抛 FORBIDDEN 并发布鉴权拒绝事件，先例 {@link SysNumberRuleController}）。
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/number/preview")
@Tag(name = "编号机制", description = "编号规则维护、预览、取号与特权管理")
public class SysNumberPreviewController {

    private final SysNumberPreviewService previewService;

    private final NumberResolver numberResolver;

    private final AuthorizationHelper authorizationHelper;

    public SysNumberPreviewController(SysNumberPreviewService previewService, NumberResolver numberResolver,
                                      AuthorizationHelper authorizationHelper) {
        this.previewService = previewService;
        this.numberResolver = numberResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 预览渲染：POST /backend/sys/number/preview/render
     * 请求 { fieldId?, rule?, count? }（二选一，同时传入以 fieldId 优先；count 缺省 3 上限 10）；
     * 响应 { samples, errors }，基于当前计数器顺延模拟，绝不写库；校验错误（C1~C5）与
     * 越界提示（C6）经 errors 就地返回，不抛异常。
     *
     * @param request 预览请求
     * @return 统一响应，data 为预览结果 { samples, errors }
     */
    @PostMapping("/render")
    @Operation(summary = "编号预览渲染", description = "鉴权动作 " + ACTION_PREVIEW_RENDER + "（登录即可，"
            + "预览无副作用，D6）；无副作用、不推进计数器、不落任何记录；已保存规则（fieldId）与"
            + "未保存 JSON（rule）均可渲染，二选一，两者都传以 fieldId 优先，count 缺省 3 上限 10；"
            + "校验错误（C1~C5）与当前周期临近耗尽提示（C6）经 errors 就地返回不抛异常；"
            + "供 NumberRuleComposer 及宿主模块复用")
    public BaseResponse<SysNumberPreviewVo> render(@RequestBody SysNumberPreviewRequest request) {
        authorize(new AuthorizationContext(ACTION_PREVIEW_RENDER));
        return BaseResponse.success(previewService.render(request), null);
    }

    /* ==================== 鉴权（AuthorizationHelper 统一入口） ==================== */

    /**
     * 执行最终鉴权：未登录或凭证已失效抛 UNAUTHORIZED，已登录但缺少权限时
     * 发布鉴权拒绝事件并抛 FORBIDDEN，由全局异常处理器统一转换。
     */
    private void authorize(AuthorizationContext context) {
        authorizationHelper.authorize(numberResolver, context);
    }
}

package com.eric.base.sys.number.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.number.model.SysNumberAcquireResultDo;
import com.eric.base.sys.number.request.SysNumberGenerateRequest;
import com.eric.base.sys.number.resolver.NumberResolver;
import com.eric.base.sys.number.service.SysNumberGenerateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.eric.base.sys.number.properties.SysNumberConstants.*;

/**
 * 编号正式取号接口
 *
 * @author Eric
 * @date 2026/11/13
 * @description 编号机制模块取号面的请求入口（任务 NB3-BE-004，方案 §3.1/§3.2 generate 组）：
 *              只负责接收请求、判空守卫、执行最终鉴权并调用 {@link SysNumberGenerateService}
 *              返回统一响应，取号逻辑全部在 Service。取号只校验登录（D6/D7）：业务单据权限
 *              由调用方在取号前完成，本接口的 AuthorizationContext 不携带业务单据语义，
 *              禁止以本接口替代业务鉴权；幂等命中返回原编号 reused=true。
 *              本 Service 未实现 IBaseService，不走 BaseCrudController 骨架，
 *              鉴权经 {@link AuthorizationHelper} 统一入口（未登录抛 UNAUTHORIZED、
 *              无权限抛 FORBIDDEN 并发布鉴权拒绝事件，先例 {@link SysNumberRuleController}）。
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/number/generate")
@Tag(name = "编号机制", description = "编号规则维护、预览、取号与特权管理")
public class SysNumberGenerateController {

    private final SysNumberGenerateService generateService;

    private final NumberResolver numberResolver;

    private final AuthorizationHelper authorizationHelper;

    public SysNumberGenerateController(SysNumberGenerateService generateService, NumberResolver numberResolver,
                                       AuthorizationHelper authorizationHelper) {
        this.generateService = generateService;
        this.numberResolver = numberResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 正式取号：POST /backend/sys/number/generate/acquire
     * 请求 { modelName, modelField, modelId }（三字段必填）；响应
     * { code, recordDetailId, reused }（reused=true 表示幂等命中已有编号）。
     *
     * @param request 取号请求
     * @return 统一响应，data 为取号结果；三字段任一缺失返回 BAD_REQUEST
     */
    @PostMapping("/acquire")
    @Operation(summary = "正式取号", description = "鉴权动作 " + ACTION_GENERATE_ACQUIRE + "（登录即可，"
            + "业务单据权限由调用方在取号前完成，禁止以本接口替代业务鉴权 D7）；请求 { modelName, "
            + "modelField, modelId } 三字段必填，任一缺失返回 BAD_REQUEST；幂等：同单据重复取号"
            + "返回原编号 reused=true 不推进计数器；同进程业务建议直接注入 SysNumberGenerateService，"
            + "本接口保留给跨服务与调试场景")
    public BaseResponse<SysNumberAcquireResultDo> acquire(@RequestBody SysNumberGenerateRequest request) {
        authorize(new AuthorizationContext(ACTION_GENERATE_ACQUIRE));
        if (null == request || StringUtil.isEmpty(request.getModelName())
                || StringUtil.isEmpty(request.getModelField()) || StringUtil.isEmpty(request.getModelId())) {
            return BaseResponse.failure("BAD_REQUEST", "modelName, modelField and modelId are required");
        }
        return BaseResponse.success(
                generateService.acquireByModel(request.getModelName(), request.getModelField(), request.getModelId()),
                null);
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

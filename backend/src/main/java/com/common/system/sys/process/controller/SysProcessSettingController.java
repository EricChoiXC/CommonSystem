package com.common.system.sys.process.controller;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.exception.AuthorizationException;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.resolver.AuthorizationRejectedEvent;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.process.request.SysProcessSettingSaveRequest;
import com.common.system.sys.process.resolver.ProcessResolver;
import com.common.system.sys.process.response.SysProcessSettingVo;
import com.common.system.sys.process.service.SysProcessSettingService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.common.system.sys.process.properties.SysProcessConstants.ACTION_PROCESS_SETTING_LIST;
import static com.common.system.sys.process.properties.SysProcessConstants.ACTION_PROCESS_SETTING_RESET;
import static com.common.system.sys.process.properties.SysProcessConstants.ACTION_PROCESS_SETTING_SAVE;

/**
 * 流程公共配置接口
 *
 * @author Eric
 * @date 2026/8/21
 * @description 后台管理-流程管理-公共配置（仅 SYS_PROCESS_SETTING_ADMIN / SYS_PROCESS_ADMIN）：
 *              配置列表、提交有变更的配置项、重置单个配置项。
 *              所有接口执行核心业务逻辑前先通过 ProcessResolver 完成鉴权；
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("/backend/sys/process/setting")
public class SysProcessSettingController {

    private final SysProcessSettingService baseService;
    private final ProcessResolver baseResolver;
    private final ApplicationEventPublisher eventPublisher;

    public SysProcessSettingController(SysProcessSettingService baseService,
                                       ProcessResolver baseResolver,
                                       ApplicationEventPublisher eventPublisher) {
        this.baseService = baseService;
        this.baseResolver = baseResolver;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 公共配置列表：GET /backend/sys/process/setting/list
     * 返回本模块公共配置（process / notify 类型），仅含中文描述与值等展示字段。
     */
    @GetMapping("/list")
    public BaseResponse<List<SysProcessSettingVo>> list() {
        authorize(new AuthorizationContext(ACTION_PROCESS_SETTING_LIST));
        return BaseResponse.success(baseService.listSettings(), null);
    }

    /**
     * 提交配置：POST /backend/sys/process/setting/save
     * 请求体 { items: [{ fieldId, fieldValue }] }，仅将有更新（或重置）的配置项提交保存。
     */
    @PostMapping("/save")
    public BaseResponse<Void> save(@RequestBody SysProcessSettingSaveRequest request) {
        authorize(new AuthorizationContext(ACTION_PROCESS_SETTING_SAVE));
        baseService.saveSettings(request);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 重置配置：POST /backend/sys/process/setting/reset?fieldId=${id}
     * 将单个配置项还原为默认值。
     */
    @PostMapping("/reset")
    public BaseResponse<Void> reset(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_PROCESS_SETTING_RESET, fieldId);
        baseService.resetSetting(fieldId);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 最终鉴权（与 BaseCrudController.authorize 行为一致）：
     * 未登录或凭证失效时抛出 UNAUTHORIZED，前端据此清理本地登录态并跳转登录页；
     * 已登录但缺少权限时发布鉴权拒绝事件（供日志模块记录安全审计）并抛出 FORBIDDEN。
     */
    private void authorize(String action, String documentId) {
        authorize(new AuthorizationContext(action, documentId));
    }

    private void authorize(AuthorizationContext context) {
        SysOrgUser currentUser = UserUtil.getCurrentUser();
        if (null == currentUser) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        if (!baseResolver.resolve(context)) {
            publishAuthorizationRejected(currentUser.getFieldId(), context);
            throw new AuthorizationException("FORBIDDEN", "no permission for action: " + context.getAction());
        }
    }

    /**
     * 发布鉴权拒绝事件（安全审计）；审计日志记录失败不影响鉴权流程本身。
     */
    private void publishAuthorizationRejected(String userId, AuthorizationContext context) {
        try {
            eventPublisher.publishEvent(new AuthorizationRejectedEvent(userId, context.getAction(),
                    context.getDocumentId(), "no permission for action: " + context.getAction()));
        } catch (RuntimeException e) {
            // 忽略：审计失败不得阻断鉴权
        }
    }
}

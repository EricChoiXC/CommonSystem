package com.eric.base.common.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 最终鉴权与鉴权拒绝事件发布
 *
 * @author Eric
 * @date 2026/9/21
 * @description 供未使用 {@code BaseCrudController} 骨架的复杂业务 Controller（如项目、项目变更、
 *              流程实例）复用的统一鉴权入口：未登录或凭证失效时抛出 {@code UNAUTHORIZED}；
 *              已登录但缺少权限时发布鉴权拒绝事件（供日志模块记录安全审计）并抛出 {@code FORBIDDEN}，
 *              两者由全局异常处理器统一转换为 BaseResponse。
 */
@Component
public class AuthorizationHelper {

    private final ApplicationEventPublisher eventPublisher;

    public AuthorizationHelper(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    /**
     * 按接口标识鉴权
     */
    public void authorize(BaseResolver resolver, String action) {
        authorize(resolver, new AuthorizationContext(action));
    }

    /**
     * 按接口标识 + 文档 ID 鉴权
     */
    public void authorize(BaseResolver resolver, String action, String documentId) {
        authorize(resolver, new AuthorizationContext(action, documentId));
    }

    /**
     * 执行最终鉴权
     */
    public void authorize(BaseResolver resolver, AuthorizationContext context) {
        SysOrgUser currentUser = UserUtil.getCurrentUser();
        if (null == currentUser) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        if (!resolver.resolve(context)) {
            publishAuthorizationRejected(currentUser.getFieldId(), context);
            throw new AuthorizationException("FORBIDDEN",
                    "no permission for action: " + context.getAction());
        }
    }

    /**
     * 发布鉴权拒绝事件（安全审计）：记录当前用户、接口动作、文档 ID、拒绝原因；
     * 审计日志记录失败不影响鉴权流程本身。
     */
    public void publishAuthorizationRejected(String userId, AuthorizationContext context) {
        try {
            eventPublisher.publishEvent(new AuthorizationRejectedEvent(userId, context.getAction(),
                    context.getDocumentId(), "no permission for action: " + context.getAction()));
        } catch (RuntimeException e) {
            // 忽略：审计失败不得阻断鉴权
        }
    }
}

package com.common.system.sys.log.service.listeners;

import com.common.system.common.resolver.AuthorizationRejectedEvent;
import com.common.system.sys.log.service.LogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 安全审计监听器
 *
 * @author Eric
 * @date 2026/8/8
 * @description 监听统一鉴权入口发布的 {@link AuthorizationRejectedEvent}（resolver 返回 FORBIDDEN），
 *              记录「当前用户、接口动作、文档 ID、拒绝原因」鉴权拒绝日志，用于定位越权尝试；
 *              会话失效（UNAUTHORIZED）不记录（登录态自然过期不视为安全事件）。
 */
@Component
public class SecurityLogListener {

    private static final Logger log = LoggerFactory.getLogger(SecurityLogListener.class);

    private final LogService logService;

    public SecurityLogListener(LogService logService) {
        this.logService = logService;
    }

    @EventListener
    public void onAuthorizationRejected(AuthorizationRejectedEvent event) {
        try {
            logService.recordForbidden(event.getUserId(), event.getAction(),
                    event.getDocumentId(), event.getReason());
        } catch (Exception e) {
            // 安全审计日志失败不影响鉴权流程
            log.warn("failed to record forbidden log for action {}", event.getAction(), e);
        }
    }
}

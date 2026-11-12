package com.eric.base.common.resolver;

import org.springframework.context.ApplicationEvent;

/**
 * 鉴权拒绝事件
 *
 * @author Eric
 * @date 2026/8/8
 * @description 由统一鉴权入口（BaseCrudController#authorize）在 resolver 返回 FORBIDDEN 时发布，
 *              携带当前用户、接口动作、文档 ID 与拒绝原因，供安全审计监听器记录鉴权拒绝日志
 *              （定位越权尝试）。会话失效（UNAUTHORIZED）不发布：登录态自然过期不视为安全事件。
 */
public class AuthorizationRejectedEvent extends ApplicationEvent {

    /** 当前用户 ID（此时必为已登录用户） */
    private final String userId;

    /** 接口标识（action） */
    private final String action;

    /** 文档 ID（可空：部分接口级鉴权无具体单据） */
    private final String documentId;

    /** 拒绝原因 */
    private final String reason;

    public AuthorizationRejectedEvent(String userId, String action, String documentId, String reason) {
        super(userId);
        this.userId = userId;
        this.action = action;
        this.documentId = documentId;
        this.reason = reason;
    }

    public String getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getReason() {
        return reason;
    }
}

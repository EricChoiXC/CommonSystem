package com.common.system.sys.notify.event;

import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 待办发送事件
 *
 * @author Eric
 * @date 2026/8/12
 * @description 当 {@link com.common.system.sys.notify.service.SysNotifyTodoService#send}
 *              成功写入待办后发布，由 {@link com.common.system.sys.notify.service.listeners.NotifySseEventListener}
 *              消费，通过 SSE 向在线接收人推送实时通知。
 *              使用 {@link org.springframework.transaction.event.TransactionalEventListener}
 *              确保事务提交后才推送，推送失败不影响业务事务。
 */
public class NotifySendEvent extends ApplicationEvent {

    /** 待办主表 ID */
    private final String todoId;

    /** 标题 */
    private final String todoName;

    /** 通知类型：1=待办 2=待阅 */
    private final String notifyType;

    /** 来源模块（MODEL_KEY，如 SYS-ORG） */
    private final String modelName;

    /** 文档 ID */
    private final String modelId;

    /** 链接 */
    private final String url;

    /** 接收人 ID 列表 */
    private final List<String> userIds;

    public NotifySendEvent(Object source, String todoId, String todoName,
                           String notifyType, String modelName, String modelId,
                           String url, List<String> userIds) {
        super(source);
        this.todoId = todoId;
        this.todoName = todoName;
        this.notifyType = notifyType;
        this.modelName = modelName;
        this.modelId = modelId;
        this.url = url;
        this.userIds = userIds;
    }

    public String getTodoId() {
        return todoId;
    }

    public String getTodoName() {
        return todoName;
    }

    public String getNotifyType() {
        return notifyType;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelId() {
        return modelId;
    }

    public String getUrl() {
        return url;
    }

    public List<String> getUserIds() {
        return userIds;
    }
}
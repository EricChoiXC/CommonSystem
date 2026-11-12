package com.eric.base.sys.notify.service.listeners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eric.base.sys.notify.event.NotifySendEvent;
import com.eric.base.sys.notify.service.SseEmitterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 待办 SSE 推送事件监听器
 *
 * @author Eric
 * @date 2026/8/12
 * @description 监听 {@link NotifySendEvent}，在发送待办事务提交后，
 *              异步向在线接收人推送 SSE 事件。推送失败不影响主业务事务。
 */
@Component
public class NotifySseEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotifySseEventListener.class);

    private final SseEmitterManager sseEmitterManager;
    private final ObjectMapper objectMapper;

    public NotifySseEventListener(SseEmitterManager sseEmitterManager, ObjectMapper objectMapper) {
        this.sseEmitterManager = sseEmitterManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 事务提交后异步推送 SSE 事件。推送失败不抛异常，仅记录日志。
     */
    @Async("notifySseExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotifySend(NotifySendEvent event) {
        if (event == null || event.getUserIds() == null || event.getUserIds().isEmpty()) {
            return;
        }
        String data = buildPushData(event);
        if (data == null) {
            return;
        }
        for (String userId : event.getUserIds()) {
            if (userId == null || userId.isEmpty()) {
                continue;
            }
            boolean success = sseEmitterManager.sendToUser(userId, "new-todo", data);
            if (success) {
                log.debug("SSE 推送待办成功: userId={}, todoId={}", userId, event.getTodoId());
            }
            // 用户不在线是正常情况，不记录警告日志
        }
    }

    /**
     * 构建推送数据 JSON 字符串。
     * 字段名采用驼峰风格，与前端使用习惯一致。
     */
    private String buildPushData(NotifySendEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todoId", event.getTodoId());
        data.put("todoName", event.getTodoName());
        data.put("notifyType", event.getNotifyType());
        data.put("modelName", event.getModelName());
        data.put("modelId", event.getModelId());
        data.put("url", event.getUrl());
        data.put("receiveTime", new Date());
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("SSE 推送数据序列化失败: todoId={}", event.getTodoId(), e);
            return null;
        }
    }
}
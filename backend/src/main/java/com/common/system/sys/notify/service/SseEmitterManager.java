package com.common.system.sys.notify.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 连接管理器
 *
 * @author Eric
 * @date 2026/8/12
 * @description 管理在线用户的 SSE 连接（userId → SseEmitter），
 *              支持注册、移除、向指定用户推送事件。
 *              同一用户重复订阅时，旧连接被关闭，仅保留最新连接。
 *              单实例部署直接使用；多实例部署时需升级为 Redis Pub/Sub 桥接。
 */
@Service
public class SseEmitterManager {

    /** 在线用户映射表：userId → SseEmitter */
    private final ConcurrentHashMap<String, SseEmitter> onlineUsers = new ConcurrentHashMap<>();

    /**
     * 注册用户连接。同一用户重复订阅时，关闭旧连接，使用新连接。
     *
     * @param userId  用户 ID
     * @param emitter SSE 连接
     */
    public void register(String userId, SseEmitter emitter) {
        SseEmitter old = onlineUsers.put(userId, emitter);
        if (old != null) {
            try {
                old.complete();
            } catch (Exception ignored) {
                // 忽略旧连接关闭异常
            }
        }
    }

    /**
     * 移除用户连接。
     *
     * @param userId 用户 ID
     */
    public void remove(String userId) {
        onlineUsers.remove(userId);
    }

    /**
     * 向指定用户推送事件。
     *
     * @param userId    接收用户 ID
     * @param eventName 事件名称（如 new-todo）
     * @param data      推送数据（JSON 字符串）
     * @return true=推送成功, false=用户不在线或推送失败
     */
    public boolean sendToUser(String userId, String eventName, String data) {
        SseEmitter emitter = onlineUsers.get(userId);
        if (emitter == null) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (Exception e) {
            // 连接已断开（含 IOException、IllegalStateException 等），自动清理
            onlineUsers.remove(userId);
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 忽略 complete 异常
            }
            return false;
        }
    }

    /**
     * 获取在线用户数（仅统计，不做鉴权使用）。
     *
     * @return 在线用户数
     */
    public int getOnlineCount() {
        return onlineUsers.size();
    }
}
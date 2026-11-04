package com.common.system.sys.notify.controller;

import com.common.system.common.exception.AuthorizationException;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.session.LoginSessionService;
import com.common.system.sys.notify.resolver.NotifyResolver;
import com.common.system.sys.notify.service.SseEmitterManager;
import com.common.system.sys.org.utils.CurrentUserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.common.system.sys.notify.properties.SysNotifyConstants.ACTION_SSE_SUBSCRIBE;

/**
 * SSE 订阅端点
 *
 * @author Eric
 * @date 2026/8/12
 * @description 提供 SSE 长连接订阅端点，前端通过 EventSource 连接。
 *              由于 EventSource 不支持自定义请求头，登录凭证通过 URL query 参数 token 传递。
 *              连接建立后每 30 秒发送心跳保持连接，超时时间 30 分钟。
 *              断线后前端 EventSource 会自动重连。
 */
@RestController
@RequestMapping("/backend/sys/notify/sse")
public class NotifySseController {

    private static final Logger log = LoggerFactory.getLogger(NotifySseController.class);

    /** SSE 连接超时：30 分钟 */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    /** 心跳间隔：30 秒 */
    private static final long HEARTBEAT_INTERVAL = 30;

    private final SseEmitterManager sseEmitterManager;
    private final LoginSessionService loginSessionService;
    private final NotifyResolver notifyResolver;

    /** 心跳调度器（所有连接共享） */
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public NotifySseController(SseEmitterManager sseEmitterManager,
                                LoginSessionService loginSessionService,
                                NotifyResolver notifyResolver) {
        this.sseEmitterManager = sseEmitterManager;
        this.loginSessionService = loginSessionService;
        this.notifyResolver = notifyResolver;
    }

    /**
     * SSE 订阅：GET /backend/sys/notify/sse/subscribe?token=xxx
     * <p>由于 EventSource 不支持自定义请求头，token 通过 URL query 参数传递。
     * 后端手动解析 token 获取 userId，并通过 NotifyResolver 完成鉴权。</p>
     *
     * @param token 登录凭证（从 URL query 参数获取）
     * @return SseEmitter 长连接
     */
    @GetMapping("/subscribe")
    public SseEmitter subscribe(@RequestParam("token") String token) {
        // 1. 解析 token 获取 userId
        String userId = loginSessionService.resolve(token);
        if (userId == null) {
            throw new AuthorizationException("UNAUTHORIZED", "登录凭证无效或已过期");
        }

        // 2. 手动设置当前用户上下文（SSE 请求通过 URL 参数传 token，不走 CurrentUserFilter）
        CurrentUserHolder.set(userId);
        try {
            // 3. 鉴权（登录即可）
            if (!notifyResolver.resolve(new AuthorizationContext(ACTION_SSE_SUBSCRIBE))) {
                throw new AuthorizationException("FORBIDDEN", "无权限订阅 SSE");
            }
        } finally {
            CurrentUserHolder.clear();
        }

        // 3. 创建 SseEmitter（30 分钟超时）
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 4. 注册到管理器
        sseEmitterManager.register(userId, emitter);

        // 5. 启动心跳（需在 onCompletion/onTimeout/onError 之前获得 ScheduledFuture）
        ScheduledFuture<?> heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data(""));
            } catch (Exception e) {
                // 心跳失败说明连接已断开，抛出 RuntimeException 使 scheduleAtFixedRate 停止重复
                throw new RuntimeException("SSE 心跳失败，停止心跳任务", e);
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);

        // 6. 设置回调清理（SseEmitter 的 onCompletion/onTimeout/onError 各自独立，
        //    但每个事件类型只保留最后一个注册的 Runnable，因此将清理逻辑合并为一个 Runnable）
        Runnable cleanup = () -> {
            sseEmitterManager.remove(userId);
            heartbeatFuture.cancel(false);
            log.debug("SSE 连接清理: userId={}", userId);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> {
            cleanup.run();
            log.debug("SSE 连接异常: userId={}, error={}", userId, e.getMessage());
        });

        // 7. 发送初始连接成功事件
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"status\":\"ok\"}"));
        } catch (Exception e) {
            cleanup.run();
            log.warn("SSE 初始事件发送失败: userId={}", userId, e);
            return emitter;
        }

        log.info("SSE 连接建立: userId={}", userId);
        return emitter;
    }
}
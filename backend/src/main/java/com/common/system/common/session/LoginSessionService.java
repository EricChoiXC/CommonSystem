package com.common.system.common.session;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录会话服务（内存版）
 *
 * @author Eric
 * @date 2026/8/7
 * @description 管理登录凭证（token）与用户 ID 的映射，供 {@code CurrentUserFilter}
 *              解析请求头还原当前用户。token 带有效期（默认 8 小时），resolve 时惰性清理过期会话。
 *              当前阶段采用进程内内存存储（重启即失效），后续接入统一登录认证
 *              （Spring Security Session / JWT）时整体替换。
 */
@Component
public class LoginSessionService {

    /** 默认会话有效期：8 小时 */
    public static final long DEFAULT_TTL_MILLIS = 8 * 60 * 60 * 1000L;

    /** token -> 会话（用户 ID + 过期时间） */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private final long ttlMillis;

    public LoginSessionService() {
        this(DEFAULT_TTL_MILLIS);
    }

    LoginSessionService(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    private record Session(String userId, long expireAt) {
    }

    /**
     * 创建会话：为指定用户签发一个随机 token
     *
     * @param userId 用户 ID
     * @return 登录凭证 token
     */
    public String create(String userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(userId, System.currentTimeMillis() + ttlMillis));
        return token;
    }

    /**
     * 解析凭证：token 有效时返回对应用户 ID，无效、过期或已注销返回 null（过期会话同时被清理）
     */
    public String resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Session session = sessions.get(token.trim());
        if (session == null) {
            return null;
        }
        if (System.currentTimeMillis() > session.expireAt()) {
            sessions.remove(token.trim());
            return null;
        }
        return session.userId();
    }

    /**
     * 注销会话：删除指定 token 的登录态（幂等）
     */
    public void remove(String token) {
        if (token != null) {
            sessions.remove(token.trim());
        }
    }
}

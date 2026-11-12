package com.eric.base.sys.log.utils;

import com.eric.base.common.utils.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 日志请求上下文工具
 *
 * @author Eric
 * @date 2026/8/8
 * @description 从当前请求提取客户端 IP / UA，供日志模块记录登录日志与操作日志环境信息。
 *              IP 优先取代理链头（X-Forwarded-For 首个地址 / X-Real-IP），缺失时回退 remoteAddr。
 */
public class LogContextUtil {

    private LogContextUtil() {
    }

    /**
     * 从请求提取客户端 IP：X-Forwarded-For（取首个）→ X-Real-IP → remoteAddr
     */
    public static String getClientIp(HttpServletRequest request) {
        if (null == request) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtil.isNotEmpty(forwarded)) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return first.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtil.isNotEmpty(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 从当前线程请求上下文提取客户端 IP；无请求上下文时返回 null
     */
    public static String getClientIp() {
        HttpServletRequest request = currentRequest();
        return getClientIp(request);
    }

    /**
     * 从当前线程请求上下文提取客户端 UA；无请求上下文时返回 null
     */
    public static String getUserAgent() {
        HttpServletRequest request = currentRequest();
        return null == request ? null : request.getHeader("User-Agent");
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return null == attributes ? null : attributes.getRequest();
    }
}

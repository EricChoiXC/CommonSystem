package com.eric.base.common.exception;

/**
 * 鉴权异常
 *
 * @author Eric
 * @date 2026/8/4
 * @description 接口鉴权失败时抛出，由全局异常处理器转换为统一响应返回给前端。
 */
public class AuthorizationException extends RuntimeException {

    /**
     * 权限码，如 FORBIDDEN / UNAUTHORIZED
     */
    private final String code;

    public AuthorizationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

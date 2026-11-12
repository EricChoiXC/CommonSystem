package com.eric.base.common.exception;

import com.eric.base.common.base.model.BaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * @author Eric
 * @date 2026/8/4
 * @description 将异常统一转换为 BaseResponse，保证所有接口响应结构一致。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 鉴权失败：返回权限码，不泄露内部细节
     */
    @ExceptionHandler(AuthorizationException.class)
    public BaseResponse<Void> handleAuthorization(AuthorizationException e) {
        return BaseResponse.failure(e.getCode(), e.getMessage());
    }

    /**
     * 模块组已关闭：@ModuleGroup 命中的模块组处于停用状态，
     * 返回固定错误码与用户可读文案（不进入 Controller 与 resolver）
     */
    @ExceptionHandler(ModuleDisabledException.class)
    public BaseResponse<Void> handleModuleDisabled(ModuleDisabledException e) {
        return BaseResponse.failure("MODULE_DISABLED", e.getMessage());
    }

    /**
     * 参数校验失败 / 数据状态异常（如影响行数不符）
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public BaseResponse<Void> handleBadRequest(RuntimeException e) {
        return BaseResponse.failure("BAD_REQUEST", e.getMessage());
    }

    /**
     * 数据重复（主键/唯一键冲突，如并发重复提交或复用既有 id）：
     * 返回业务提示，不向客户端泄露底层细节（完整信息记录服务端日志）。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public BaseResponse<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("duplicate key conflict: {}", e.getMessage());
        return BaseResponse.failure("BAD_REQUEST", "数据已存在，请勿重复提交");
    }

    /**
     * 兜底异常：仅返回通用文案，完整异常信息记录服务端日志，
     * 避免向客户端泄露内部细节（如 SQL 错误文本）。
     */
    @ExceptionHandler(Exception.class)
    public BaseResponse<Void> handleOther(Exception e) {
        log.error("unexpected error", e);
        return BaseResponse.failure("ERROR", "系统异常，请稍后重试或联系管理员");
    }
}

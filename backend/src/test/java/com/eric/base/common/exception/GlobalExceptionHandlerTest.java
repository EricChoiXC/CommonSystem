package com.eric.base.common.exception;

import com.eric.base.common.base.model.BaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局异常处理器测试
 *
 * @author Eric
 * @date 2026/8/22
 * @description 锁定异常转换行为：鉴权/参数异常保留业务语义；
 *              重复键冲突返回业务提示（不泄露底层细节）；
 *              兜底异常返回通用文案（不回显 e.getMessage()，如 SQL 错误文本）。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void authorizationExceptionKeepsCodeAndMessage() {
        BaseResponse<Void> response = handler.handleAuthorization(new AuthorizationException("FORBIDDEN", "no permission"));
        assertEquals("FORBIDDEN", response.getCode());
        assertEquals("no permission", response.getMessage());
    }

    @Test
    void illegalArgumentKeepsBusinessMessage() {
        BaseResponse<Void> response = handler.handleBadRequest(new IllegalArgumentException("模板名称不能为空"));
        assertEquals("BAD_REQUEST", response.getCode());
        assertEquals("模板名称不能为空", response.getMessage());
    }

    @Test
    void duplicateKeyReturnsBusinessHintWithoutDetail() {
        BaseResponse<Void> response = handler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry 'abc' for key 'PRIMARY'"));
        assertEquals("BAD_REQUEST", response.getCode());
        assertEquals("数据已存在，请勿重复提交", response.getMessage());
        assertFalse(response.getMessage().contains("Duplicate"));
    }

    @Test
    void fallbackReturnsGenericMessageWithoutInternalDetail() {
        BaseResponse<Void> response = handler.handleOther(
                new RuntimeException("com.mysql.jdbc.exceptions... SQL syntax error near 'drop table'"));
        assertEquals("ERROR", response.getCode());
        assertEquals("系统异常，请稍后重试或联系管理员", response.getMessage());
        assertTrue(response.getMessage().contains("稍后重试"));
        assertFalse(response.getMessage().contains("SQL"));
    }
}

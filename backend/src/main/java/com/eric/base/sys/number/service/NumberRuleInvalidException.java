package com.eric.base.sys.number.service;

import com.eric.base.sys.number.model.SysNumberRuleErrorDo;

import java.util.List;

/**
 * 编号规则配置非法异常
 *
 * @author Eric
 * @date 2026/11/13
 * @description 规则校验（C1~C7）不通过时抛出，统一承载 CRM_NUMBER_RULE_INVALID
 *              错误语义（方案 §5.5）。错误码常量随 NB3-BE-001 落 SysNumberConstants，
 *              本任务以异常类型占位：错误码暂以 {@link #CODE} 常量内聚在异常上；
 *              继承 IllegalArgumentException，命中 GlobalExceptionHandler 既有
 *              BAD_REQUEST 分支，前端可读 message。
 */
public class NumberRuleInvalidException extends IllegalArgumentException {

    /** 错误码：规则配置非法（C1~C5、C7） */
    public static final String CODE = "CRM_NUMBER_RULE_INVALID";

    /** 全部校验错误明细（供 preview 就地红字提示等消费） */
    private final transient List<SysNumberRuleErrorDo> errors;

    public NumberRuleInvalidException(List<SysNumberRuleErrorDo> errors) {
        super(buildMessage(errors));
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public NumberRuleInvalidException(List<SysNumberRuleErrorDo> errors, Throwable cause) {
        super(buildMessage(errors), cause);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public List<SysNumberRuleErrorDo> getErrors() {
        return errors;
    }

    public String getCode() {
        return CODE;
    }

    private static String buildMessage(List<SysNumberRuleErrorDo> errors) {
        if (errors == null || errors.isEmpty()) {
            return CODE + ": 编号规则配置非法";
        }
        StringBuilder message = new StringBuilder(CODE).append(": 编号规则配置非法");
        for (SysNumberRuleErrorDo error : errors) {
            message.append("；[").append(error.getCode()).append("] ").append(error.getMessage());
        }
        return message.toString();
    }
}

package com.eric.base.sys.number.service;

/**
 * 编号规则不存在异常
 *
 * @author Eric
 * @date 2026/11/13
 * @description 固定接入取号按 {modelName, modelField} 未定位到任何规则（含接入项未配置，
 *              方案 §5.3）时抛出，承载 CRM_NUMBER_RULE_NOT_FOUND 错误语义。
 *              错误码常量随 NB3-BE-001 落 SysNumberConstants，本任务以异常类型占位
 *              （对齐 NumberRuleInvalidException 先例）；继承 IllegalArgumentException，
 *              命中 GlobalExceptionHandler 既有 BAD_REQUEST 分支，前端可读 message。
 */
public class NumberRuleNotFoundException extends IllegalArgumentException {

    /** 错误码：规则不存在 / 接入项未配置 */
    public static final String CODE = "CRM_NUMBER_RULE_NOT_FOUND";

    public NumberRuleNotFoundException(String message) {
        super(CODE + ": " + message);
    }

    public String getCode() {
        return CODE;
    }
}

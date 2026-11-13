package com.eric.base.sys.number.service;

/**
 * 编号规则未启用异常
 *
 * @author Eric
 * @date 2026/11/13
 * @description 固定接入取号按 {modelName, modelField} 定位到规则、但同接入项下
 *              无启用规则（方案 §5.3；启用唯一性由 save / enable 切换机制保证 D1，
 *              取号侧不会命中多条）时抛出，承载 CRM_NUMBER_RULE_DISABLED 错误语义。
 *              错误码常量随 NB3-BE-001 落 SysNumberConstants，本任务以异常类型占位
 *              （对齐 NumberRuleInvalidException 先例）；继承 IllegalArgumentException，
 *              命中 GlobalExceptionHandler 既有 BAD_REQUEST 分支，前端可读 message。
 */
public class NumberRuleDisabledException extends IllegalArgumentException {

    /** 错误码：规则未启用 */
    public static final String CODE = "CRM_NUMBER_RULE_DISABLED";

    public NumberRuleDisabledException(String message) {
        super(CODE + ": " + message);
    }

    public String getCode() {
        return CODE;
    }
}

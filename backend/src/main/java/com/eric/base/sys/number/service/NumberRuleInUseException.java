package com.eric.base.sys.number.service;

/**
 * 编号规则使用中异常
 *
 * @author Eric
 * @date 2026/11/13
 * @description 已产生取号明细（sys_number_record_detail 按作用域键 = 规则 field_id 计数，
 *              明细表无 field_rule_id 列，固定接入作用域即规则 id，方案 §5.1/§5.5）
 *              的规则拒绝删除（D9 删除保护，仅可停用）时抛出，承载
 *              CRM_NUMBER_RULE_IN_USE 错误语义。继承 IllegalArgumentException，
 *              命中 GlobalExceptionHandler 既有 BAD_REQUEST 分支，前端可读 message。
 */
public class NumberRuleInUseException extends IllegalArgumentException {

    /** 错误码：已产生取号明细，拒绝删除 */
    public static final String CODE = "CRM_NUMBER_RULE_IN_USE";

    public NumberRuleInUseException(String message) {
        super(CODE + ": " + message);
    }

    public String getCode() {
        return CODE;
    }
}

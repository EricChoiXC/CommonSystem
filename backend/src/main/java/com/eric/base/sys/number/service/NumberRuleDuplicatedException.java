package com.eric.base.sys.number.service;

/**
 * 编号规则编码重复异常
 *
 * @author Eric
 * @date 2026/11/13
 * @description 规则编码查重不通过（sys_number_rule.field_code 已存在；save 直接查重、
 *              update 排除自身，方案 §3.2 / §5.5，D4 Service 层查重先例 i18n 语言码）
 *              时抛出，承载 CRM_NUMBER_RULE_DUPLICATED 错误语义。注意同接入项多规则
 *              为合法形态、启用唯一性由切换机制保证（D1），本错误码仅用于编码重复；
 *              继承 IllegalArgumentException，命中 GlobalExceptionHandler 既有
 *              BAD_REQUEST 分支，前端可读 message。
 */
public class NumberRuleDuplicatedException extends IllegalArgumentException {

    /** 错误码：规则编码重复 */
    public static final String CODE = "CRM_NUMBER_RULE_DUPLICATED";

    public NumberRuleDuplicatedException(String message) {
        super(CODE + ": " + message);
    }

    public String getCode() {
        return CODE;
    }
}

package com.eric.base.sys.number.service;

/**
 * 编号接入项不存在异常
 *
 * @author Eric
 * @date 2026/11/13
 * @description 规则的 field_model_id 不在 numberRule.yml 接入项缓存（save / update /
 *              enable 启用的接入项存在性校验，方案 §5.5；yml 删除后规则残留的失效拦截，
 *              R6）时抛出，承载 CRM_NUMBER_RULE_MODEL_NOT_FOUND 错误语义，
 *              阻止继续新增/启用引用失效接入项的规则。
 *              继承 IllegalArgumentException，命中 GlobalExceptionHandler 既有
 *              BAD_REQUEST 分支，前端可读 message。
 */
public class NumberModelNotFoundException extends IllegalArgumentException {

    /** 错误码：接入项编码不在 numberRule.yml 缓存 */
    public static final String CODE = "CRM_NUMBER_RULE_MODEL_NOT_FOUND";

    public NumberModelNotFoundException(String message) {
        super(CODE + ": " + message);
    }

    public String getCode() {
        return CODE;
    }
}

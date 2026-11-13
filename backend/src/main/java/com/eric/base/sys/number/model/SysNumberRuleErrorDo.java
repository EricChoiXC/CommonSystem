package com.eric.base.sys.number.model;

import lombok.Data;

/**
 * 编号规则校验错误项文档对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 校验组件（SysNumberRuleValidator）与 preview/render 接口共用的
 *              单条校验错误描述（方案 §3.2 preview 响应 errors: [{ code, itemId, message }]）。
 *              code 为违反规则号（C1/C3/C4/C5/C7 及字段级约束 C0，见校验组件 javadoc 映射）；
 *              itemId 为违反规则项的 id（规则级错误为空）；message 为中文可读信息。
 */
@Data
public class SysNumberRuleErrorDo {

    /** 违反规则号（如 C1），供程序化匹配 */
    private String code;

    /** 违反规则项 id（规则级错误为空） */
    private String itemId;

    /** 中文可读错误信息 */
    private String message;

    public SysNumberRuleErrorDo() {
    }

    public SysNumberRuleErrorDo(String code, String itemId, String message) {
        this.code = code;
        this.itemId = itemId;
        this.message = message;
    }
}

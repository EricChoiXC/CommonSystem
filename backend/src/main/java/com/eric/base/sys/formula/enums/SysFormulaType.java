package com.eric.base.sys.formula.enums;

import java.util.Arrays;

/**
 * 公式类型
 *
 * @author Eric
 * @date 2026/11/18
 * @description 命名公式的业务分类标记（对齐 AI建议 formula_type 分类）：
 *              仅作分类与前端展示/筛选之用，不改变执行语义（方案 §2.3）——
 *              任意类型公式的执行链路相同，消费方按 field_code 引用。
 */
public enum SysFormulaType {

    /** 字段计算：字段取值/默认值类公式 */
    FIELD,
    /** 条件判断：流程/规则分支条件类公式（返回 BOOLEAN） */
    CONDITION,
    /** 校验规则：数据合法性校验类公式（返回 BOOLEAN） */
    VALIDATION,
    /** 可见性控制：字段/控件显隐类公式（返回 BOOLEAN） */
    VISIBILITY,
    /** 流程控制：流程路由/网关条件类公式（返回 BOOLEAN） */
    WORKFLOW,
    /** 自定义：以上分类均不适配的通用公式 */
    CUSTOM;

    /**
     * 解析公式类型文本，不区分大小写；非法返回 null（调用方报 CS_FORMULA_VARIABLE_INVALID 之外的参数校验）
     */
    public static SysFormulaType of(String value) {
        if (null == value || value.isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst().orElse(null);
    }
}

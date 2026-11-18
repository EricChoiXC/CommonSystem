package com.eric.base.sys.formula.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 可用公式下拉选项响应
 *
 * @author Eric
 * @date 2026/11/18
 * @description 对应 DOC-002 §4.11.4 /options 契约的命名公式节点条目：消费方按
 *              field_code 引用、执行时加载启用中的公式——下拉仅暴露启用中的命名公式
 *              消费所需元数据（编码、名称、类型与返回类型，供插入与结果适配预判），
 *              不暴露表达式原文与变量依赖（执行时由消费方按 code 加载，管理面经
 *              /load 读取）。左树「变量」「业务模型」「函数」三组节点由前端组装——
 *              变量组含系统变量与 availableVars、业务模型组来自数据源 targets、
 *              函数组来自 /functions（实现期 F14-BE-007 裁定，本接口不聚合左树）。
 */
@Data
@Schema(description = "可用公式下拉选项：启用中的命名公式消费元数据（不暴露表达式原文）")
public class SysFormulaOptionVo {

    /** 公式编码（全局唯一，消费方引用键） */
    @Schema(description = "公式编码（全局唯一，消费方按此引用执行）")
    private String fieldCode;

    /** 公式名称 */
    @Schema(description = "公式名称")
    private String fieldName;

    /** 公式类型（FIELD/CONDITION/VALIDATION/VISIBILITY/WORKFLOW/CUSTOM） */
    @Schema(description = "公式类型：FIELD/CONDITION/VALIDATION/VISIBILITY/WORKFLOW/CUSTOM",
            allowableValues = {"FIELD", "CONDITION", "VALIDATION", "VISIBILITY", "WORKFLOW", "CUSTOM"})
    private String fieldType;

    /** 期望返回类型（NUMBER/STRING/BOOLEAN/DATE/ARRAY，消费方预判结果适配） */
    @Schema(description = "期望返回类型：NUMBER/STRING/BOOLEAN/DATE/ARRAY",
            allowableValues = {"NUMBER", "STRING", "BOOLEAN", "DATE", "ARRAY"})
    private String fieldReturnType;
}

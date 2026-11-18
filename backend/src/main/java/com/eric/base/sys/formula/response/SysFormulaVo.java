package com.eric.base.sys.formula.response;

import com.eric.base.common.base.model.MainModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 命名公式响应
 *
 * @author Eric
 * @date 2026/11/18
 * @description 对应 DOC-002 §4.11.4 /list、/load、/save、/update 响应契约：列表条目与
 *              load 详情同构，差异为 fieldDependencies 形态——存储态与列表响应为原文
 *              JSON 字符串（列表页不解析，前端仅取数组长度展示「变量依赖数」），
 *              load / save / update 响应由 Service 解析为变量码字符串数组返回（前端
 *              详情页逐项展示、编辑回填）。继承 MainModel 带出公共字段（fieldId /
 *              fieldCode / fieldName / 审计五列 / fieldEnable）；fieldCode / fieldName
 *              语义与实体一致。对齐 SysDatasourceVo 先例。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "命名公式响应：列表条目与 load 详情同构，差异为 fieldDependencies 形态"
        + "（列表为原文 JSON 字符串、load 为解析后的变量码数组）")
public class SysFormulaVo extends MainModel {

    /** 公式类型（FIELD/CONDITION/VALIDATION/VISIBILITY/WORKFLOW/CUSTOM，仅作分类标记） */
    @Schema(description = "公式类型：FIELD/CONDITION/VALIDATION/VISIBILITY/WORKFLOW/CUSTOM"
            + "（仅作分类标记，不改变执行语义）")
    private String fieldType;

    /** 表达式文本（${code} 变量引用，${} 形态原样返回） */
    @Schema(description = "表达式文本（含 ${code} 变量引用，${} 形态原样返回）")
    private String fieldExpression;

    /** 变量依赖：列表为原文 JSON 字符串、load/save/update 为解析后的变量码字符串数组；NULL=无变量依赖 */
    @Schema(description = "变量依赖：列表响应为原文 JSON 字符串（变量码数组），"
            + "load / save / update 响应为解析后的变量码字符串数组；NULL=表达式未引用任何变量")
    private Object fieldDependencies;

    /** 期望返回类型（NUMBER/STRING/BOOLEAN/DATE/ARRAY） */
    @Schema(description = "期望返回类型：NUMBER/STRING/BOOLEAN/DATE/ARRAY")
    private String fieldReturnType;

    /** 备注/使用说明 */
    @Schema(description = "备注/使用说明")
    private String fieldRemark;
}

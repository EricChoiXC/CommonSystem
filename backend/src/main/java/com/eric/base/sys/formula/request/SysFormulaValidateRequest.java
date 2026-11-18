package com.eric.base.sys.formula.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 公式校验请求
 *
 * @author Eric
 * @date 2026/11/18
 * @description 公式定义器 /validate 接口请求（F14-BE-006，方案 §5.2 / DOC-002 §4.11.4 契约）：
 *              纯静态分析无副作用——语法 / 函数注册 / 参数与返回类型 / 变量码约束校验。
 *              模式语义（方案 §5.2）：vars 缺省或空 = 宽松模式（变量视为未知，仅做语法
 *              与函数校验）；vars 非空 = 严格模式（按声明核对引用一致性与类型声明，
 *              与 expectedReturnType 比对，不符报 CS_FORMULA_TYPE_MISMATCH）。
 *              内联公式与命名公式共用本接口；命名公式 save / update 保存期强制
 *              validate 同源校验由 Service 编排（F14-BE-007）。
 */
@Data
@Schema(description = "公式校验请求：语法/类型/依赖检查，纯静态分析无副作用；"
        + "vars 缺省 = 宽松模式，vars 非空 = 严格模式")
public class SysFormulaValidateRequest {

    /** 公式文本（${code} 变量引用 + 注册函数调用 + 字面量与运算符；${} 形态原样传输） */
    @Schema(description = "公式文本（${code} 变量引用，${} 形态原样传输）")
    private String expression;

    /** 期望返回类型（NUMBER/STRING/BOOLEAN/DATE/ARRAY），null 跳过返回类型比对 */
    @Schema(description = "期望返回类型：NUMBER/STRING/BOOLEAN/DATE/ARRAY（与引擎推断返回类型比对，"
            + "不符报 CS_FORMULA_TYPE_MISMATCH；null 跳过比对）",
            allowableValues = {"NUMBER", "STRING", "BOOLEAN", "DATE", "ARRAY"})
    private String expectedReturnType;

    /** 变量声明列表（code + type）；缺省或空 = 宽松模式，非空 = 严格模式 */
    @Schema(description = "变量声明列表（元素 { code, type }）：缺省或空 = 宽松模式"
            + "（变量视为未知，仅做语法与函数校验）；非空 = 严格模式（核对声明与引用一致性、"
            + "校验类型声明合法性）")
    private List<Var> vars;

    /**
     * 严格模式变量声明
     */
    @Data
    @Schema(description = "严格模式变量声明：变量码 + 类型")
    public static class Var {

        /** 变量码（标识符约束 [a-zA-Z_][a-zA-Z0-9_]*，路径投影取根码） */
        @Schema(description = "变量码（标识符约束 [a-zA-Z_][a-zA-Z0-9_]*）")
        private String code;

        /** 变量类型（NUMBER/STRING/BOOLEAN/DATE/ARRAY，大小写不敏感；非法报 CS_FORMULA_TYPE_MISMATCH） */
        @Schema(description = "变量类型：NUMBER/STRING/BOOLEAN/DATE/ARRAY",
                allowableValues = {"NUMBER", "STRING", "BOOLEAN", "DATE", "ARRAY"})
        private String type;
    }
}

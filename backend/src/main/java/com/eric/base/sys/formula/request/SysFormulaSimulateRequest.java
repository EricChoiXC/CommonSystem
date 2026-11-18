package com.eric.base.sys.formula.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 公式模拟计算请求
 *
 * @author Eric
 * @date 2026/11/18
 * @description 公式定义器 /simulate 接口请求（F14-BE-006，方案 §5.2 / DOC-002 §4.11.4 契约）：
 *              受限沙箱试算——资源限制（表达式长度 / 变量个数 / 变量值序列化体积 /
 *              循环次数封顶 / 独立线程超时）内执行，超限以 errors 数组返回、HTTP 仍 200。
 *              vars 为变量码 → 值映射（非固定结构，值类型随变量类型）；
 *              模拟赋值仅用于沙箱试算、入参由调用方自负，不代表任何真实业务数据授权；
 *              系统变量（SYS_CURRENT_*）仅服务端注入，前端传入的 CURRENT_* / SYS_CURRENT_*
 *              同名覆盖被忽略（FormulaContext 服务端注入优先）。
 */
@Data
@Schema(description = "公式模拟计算请求：受限沙箱试算，vars 为变量码 → 值映射（调用方自负）；"
        + "系统变量服务端注入，前端同名覆盖被忽略")
public class SysFormulaSimulateRequest {

    /** 公式文本（${code} 变量引用 + 注册函数调用 + 字面量与运算符；${} 形态原样传输） */
    @Schema(description = "公式文本（${code} 变量引用，${} 形态原样传输）")
    private String expression;

    /** 变量赋值表（变量码 → 值；值类型随变量类型 NUMBER/STRING/BOOLEAN/DATE/ARRAY） */
    @Schema(description = "变量赋值表（变量码 → 值）：仅用于沙箱试算，不代表任何真实业务数据授权")
    private Map<String, Object> vars;

    /** 期望返回类型（NUMBER/STRING/BOOLEAN/DATE/ARRAY），null 跳过结果类型校验 */
    @Schema(description = "期望返回类型：NUMBER/STRING/BOOLEAN/DATE/ARRAY（执行后按此适配结果类型，"
            + "不符报 CS_FORMULA_TYPE_MISMATCH；null 跳过校验）",
            allowableValues = {"NUMBER", "STRING", "BOOLEAN", "DATE", "ARRAY"})
    private String expectedReturnType;
}

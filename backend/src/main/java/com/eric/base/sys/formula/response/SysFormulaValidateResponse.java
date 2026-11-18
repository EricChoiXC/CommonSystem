package com.eric.base.sys.formula.response;

import com.eric.base.sys.formula.model.SysFormulaErrorDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 公式校验响应
 *
 * @author Eric
 * @date 2026/11/18
 * @description 对应方案 §5.2 / DOC-002 §4.11.4 /validate 契约：纯静态分析无副作用，
 *              校验失败以 errors 数组承载、HTTP 仍 200（errors 非空即未通过，不抛
 *              HTTP 错误）。dependencies 为表达式中提取的变量码数组（路径投影取根码、
 *              去重保序），供消费方存储并自建依赖图（联动重算 / 失效引用检查，方案
 *              A8——联动执行不在公式模块）；命名公式在 save / update 时落
 *              field_dependencies（同一提取口径）。
 */
@Data
@Schema(description = "公式校验响应：syntax 为语法与函数校验结果，校验失败以 errors 数组承载、HTTP 仍 200")
public class SysFormulaValidateResponse {

    /** 语法与函数校验是否通过（表达式长度 / 变量码合法性 / 函数注册 / Aviator 编译） */
    @Schema(description = "语法与函数校验是否通过")
    private boolean syntax;

    /** 引擎推断的返回类型（NUMBER/STRING/BOOLEAN/DATE/ARRAY；无法推断时为 null） */
    @Schema(description = "引擎推断的返回类型：NUMBER/STRING/BOOLEAN/DATE/ARRAY",
            allowableValues = {"NUMBER", "STRING", "BOOLEAN", "DATE", "ARRAY"})
    private String returnType;

    /** 表达式中提取的变量码数组（路径投影取根码、去重保序；无引用为空数组） */
    @Schema(description = "表达式中提取的变量码数组（去重保序；无引用为空数组），供消费方自建依赖图")
    private List<String> dependencies = new ArrayList<>();

    /** 错误数组（元素 { code, message }，code ∈ CS_FORMULA_*；无错误为空数组） */
    @Schema(description = "错误数组（元素 { code, message }，code ∈ CS_FORMULA_* 九类）；无错误为空数组，"
            + "errors 非空即未通过（HTTP 仍 200）")
    private List<SysFormulaErrorDo> errors = new ArrayList<>();
}

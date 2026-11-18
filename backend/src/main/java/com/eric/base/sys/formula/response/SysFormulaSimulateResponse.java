package com.eric.base.sys.formula.response;

import com.eric.base.sys.formula.model.SysFormulaErrorDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 公式模拟计算响应
 *
 * @author Eric
 * @date 2026/11/18
 * @description 对应方案 §5.2 / DOC-002 §4.11.4 /simulate 契约：成功 success=true +
 *              result（结果值，NUMBER 经 BigDecimal 规整）+ resultType（实际返回类型）；
 *              失败 success=false + errors: [{ code, message }]（code ∈ CS_FORMULA_*），
 *              HTTP 仍 200——资源限制检查、解析、编译、受限执行、类型适配任一步失败
 *              均以 errors 数组返回。失败不阻断弹窗操作（可继续编辑、校验、再试算）。
 */
@Data
@Schema(description = "公式模拟计算响应：成功返回 result + resultType，失败以 errors 数组承载、HTTP 仍 200")
public class SysFormulaSimulateResponse {

    /** 试算是否成功 */
    @Schema(description = "试算是否成功")
    private boolean success;

    /** 结果值（NUMBER 经 BigDecimal 规整；失败时为 null） */
    @Schema(description = "结果值（NUMBER 经 BigDecimal 规整；失败时为 null）")
    private Object result;

    /** 实际返回类型（NUMBER/STRING/BOOLEAN/DATE/ARRAY；失败时为 null） */
    @Schema(description = "实际返回类型：NUMBER/STRING/BOOLEAN/DATE/ARRAY（失败时为 null）",
            allowableValues = {"NUMBER", "STRING", "BOOLEAN", "DATE", "ARRAY"})
    private String resultType;

    /** 错误数组（元素 { code, message }，code ∈ CS_FORMULA_*；成功为空数组） */
    @Schema(description = "错误数组（元素 { code, message }，code ∈ CS_FORMULA_* 九类）；成功为空数组，"
            + "失败 HTTP 仍 200")
    private List<SysFormulaErrorDo> errors = new ArrayList<>();
}

package com.eric.base.sys.formula.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 公式校验/试算错误项文档对象
 *
 * @author Eric
 * @date 2026/11/18
 * @description /validate 与 /simulate 响应 errors 数组共用的单条错误描述
 *              （方案 §5.2 / DOC-002 §4.11.4/4.11.6 契约，元素 { code, message }）：
 *              code ∈ SysFormulaConstants 的 CS_FORMULA_* 错误码（九类）；message 为
 *              中文可读信息（语法错误附引擎错误信息与位置/行号）。组件接口中校验
 *              失败与执行失败以 data.errors 数组承载、HTTP 仍 200（不抛 HTTP 错误）；
 *              管理接口不走本结构（业务错误码以异常 message 前缀承载，DOC-002 4.11.6）。
 */
@Data
@Schema(description = "公式错误项：{ code, message }，code ∈ CS_FORMULA_* 错误码")
public class SysFormulaErrorDo {

    /** 错误码（SysFormulaConstants.CS_FORMULA_*，九类，供程序化匹配） */
    @Schema(description = "错误码（CS_FORMULA_* 九类，供程序化匹配）")
    private String code;

    /** 中文可读错误信息（语法错误附引擎错误信息与位置/行号） */
    @Schema(description = "中文可读错误信息（语法错误附引擎错误信息与位置/行号）")
    private String message;

    public SysFormulaErrorDo() {
    }

    public SysFormulaErrorDo(String code, String message) {
        this.code = code;
        this.message = message;
    }
}

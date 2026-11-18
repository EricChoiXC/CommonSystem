package com.eric.base.sys.formula.response;

import com.eric.base.sys.formula.model.SysFormulaFunctionParameterDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 公式函数元数据响应
 *
 * @author Eric
 * @date 2026/11/18
 * @description 对应 DOC-002 §4.11.4 /functions 契约：函数注册中心清单条目
 *              { functionCode, functionName, category, description,
 *              parameters: [{ name, type, required, defaultValue }], returnType }。
 *              同一份注册元数据驱动前端函数面板 / 插入模板与后端参数类型校验
 *              （方案 §2.2「前后端共用函数定义」），数据来自
 *              FormulaFunctionRegistry（内置九类 + SPI 扩展函数一并透出）；
 *              category / returnType 以枚举名字符串透出，参数结构复用
 *              {@link SysFormulaFunctionParameterDo}（对齐 SysDatasourceOptionVo
 *              复用 Spi DO 先例）。函数名全局唯一。
 */
@Data
@Schema(description = "公式函数元数据：注册中心清单条目，同一份元数据驱动前端函数面板与后端参数校验")
public class SysFormulaFunctionVo {

    /** 函数编码（全局唯一，即 Aviator 函数名） */
    @Schema(description = "函数编码（全局唯一，即 Aviator 函数名）")
    private String functionCode;

    /** 函数显示名（函数面板与插入模板展示） */
    @Schema(description = "函数显示名")
    private String functionName;

    /** 函数分类（九类：MATH/LOGIC/COMPARE/STRING/DATE/COLLECTION/CONVERT/SYSTEM/DATA，/functions 按此分组） */
    @Schema(description = "函数分类（九类）",
            allowableValues = {"MATH", "LOGIC", "COMPARE", "STRING", "DATE",
                    "COLLECTION", "CONVERT", "SYSTEM", "DATA"})
    private String category;

    /** 函数说明（函数面板展示与后端参数校验共用同一份元数据） */
    @Schema(description = "函数说明")
    private String description;

    /** 参数定义列表（按声明顺序；空列表 = 无参函数） */
    @Schema(description = "参数定义列表（按声明顺序）：{ name, type, required, defaultValue }；空列表 = 无参函数")
    private List<SysFormulaFunctionParameterDo> parameters = new ArrayList<>();

    /** 返回类型（NUMBER/STRING/BOOLEAN/DATE/ARRAY） */
    @Schema(description = "返回类型：NUMBER/STRING/BOOLEAN/DATE/ARRAY",
            allowableValues = {"NUMBER", "STRING", "BOOLEAN", "DATE", "ARRAY"})
    private String returnType;
}

package com.eric.base.sys.formula.model;

import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import lombok.Data;

import java.util.List;

/**
 * 公式函数元数据文档对象
 *
 * @author Eric
 * @date 2026/11/18
 * @description 函数注册元数据（方案 §2.2，对齐 AI建议 JSON 结构）：functionCode /
 *              functionName / category / description / parameters[{name,type,required,defaultValue}] /
 *              returnType。同一份元数据既驱动前端函数面板与插入模板（/functions 接口经
 *              SysFormulaFunctionVo 透出，F14-BE-006），也驱动后端参数类型校验，天然
 *              「前后端共用函数定义」。本类为纯 Jackson 结构，不持有 Aviator 执行体——
 *              执行体由 spi 包 {@link com.eric.base.sys.formula.spi.FormulaFunctionDefinition}
 *              承载（本类作为其父类），序列化元数据时不含执行体字段。函数元数据由代码
 *              注册、不入库，非表实体。
 */
@Data
public class SysFormulaFunctionDo {

    /** 函数编码（全局唯一，即 Aviator 函数名，重复注册首个生效） */
    private String functionCode;

    /** 函数显示名（函数面板与插入模板展示） */
    private String functionName;

    /** 函数分类（九类，方案 §2.2；/functions 按此分组透出） */
    private SysFormulaFunctionCategory category;

    /** 函数说明（前端函数面板展示与后端参数校验共用同一份元数据） */
    private String description;

    /** 参数定义列表（按声明顺序；空列表 = 无参函数） */
    private List<SysFormulaFunctionParameterDo> parameters;

    /** 返回类型（NUMBER/STRING/BOOLEAN/DATE/ARRAY） */
    private SysFormulaReturnType returnType;
}

package com.eric.base.sys.formula.model;

import lombok.Data;

/**
 * 公式函数参数元数据文档对象
 *
 * @author Eric
 * @date 2026/11/18
 * @description {@link SysFormulaFunctionDo}#parameters 元素（对齐 AI建议 JSON 结构）：
 *              参数名 / 类型 / 必填 / 默认值四元组。同一份元数据既驱动前端函数面板的
 *              参数提示与插入模板，也驱动后端参数类型校验（方案 §2.2「前后端共用函数定义」）。
 */
@Data
public class SysFormulaFunctionParameterDo {

    /** 参数名（函数面板参数提示与插入模板占位） */
    private String name;

    /** 参数类型文本（NUMBER / STRING / BOOLEAN / DATE / ARRAY 等，F14-BE-003 注册时确定口径；后端参数类型校验共用） */
    private String type;

    /** 是否必填 */
    private boolean required;

    /** 默认值文本（可选参数的插入模板展示值；空 = 无默认值） */
    private String defaultValue;
}

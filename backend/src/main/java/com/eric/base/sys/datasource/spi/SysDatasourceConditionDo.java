package com.eric.base.sys.datasource.spi;

import lombok.Data;

/**
 * 数据源固定条件文档对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 sys_datasource.field_conditions JSON 数组元素；
 *              value 为运行时变量占位符（如 ${CURRENT_USER_ID}）或字面量（字段允许时）。
 *              示例（用户日程 = 我创建的）：{ "key": "fieldCreatorId", "op": "EQ", "value": "${CURRENT_USER_ID}" }
 */
@Data
public class SysDatasourceConditionDo {

    /** 条件字段名（须 ∈ 目标能力 conditionFields 白名单） */
    private String key;

    /** 操作符（SysDatasourceOp 名称） */
    private String op;

    /** 条件值：字面量或 ${运行时变量} */
    private String value;
}

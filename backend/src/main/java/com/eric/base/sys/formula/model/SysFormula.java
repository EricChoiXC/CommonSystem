package com.eric.base.sys.formula.model;

import com.eric.base.common.base.model.MainModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 命名公式实体
 *
 * @author Eric
 * @date 2026/11/18
 * @description 对应 sys_formula：命名公式单表（全局公共公式库），一行一式，消费方按
 *              field_code 引用、执行时加载启用中的公式；内联公式不入库（存消费方自身
 *              配置 JSON）。本表由实体注解声明、经注解表结构同步引擎建表（F14-DB-001），
 *              不新增迁移脚本；不建物理外键（工程惯例），公式编码与消费方引用的关系由
 *              应用层保证。field_expression 列类型 TEXT 不设库级长度，表达式长度上限由
 *              SysFormulaConstants 资源限制在应用层校验；field_dependencies 为 JSON 列，
 *              双形态承载（先例 sys_number_rule.field_rule），禁止手工拼串。
 *              field_code / field_name 重声明遮蔽 MainModel 继承列（D5 规则，Java 类型
 *              与 MainModel 声明一致）；公共列（field_id 主键 + 审计五列）由引擎公共列
 *              模板内建，无需标注。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "命名公式实体：对应 sys_formula；fieldExpression 为表达式原文（含 ${code} 变量引用，"
        + "显示/存储层原样保留 ${} 形态），fieldDependencies 存储态为原文 JSON 字符串（save/update 时"
        + "解析提取的变量码数组）")
@TableSource(name = "sys_formula", comment = "公式表（命名公式，全局公共公式库）",
        indexes = {
                @TableIndex(name = "uk_formula_code", columns = "field_code", unique = true),
                @TableIndex(name = "idx_formula_type", columns = "field_type")})
public class SysFormula extends MainModel {

    /** 重声明：公式编码，全局唯一（D5 遮蔽 MainModel.fieldCode） */
    @ColumnField(name = "field_code", comment = "公式编码，全局唯一，Service 层查重",
            type = ColumnType.STRING, length = 100)
    @Schema(description = "公式编码，全局唯一，Service 层查重")
    private String fieldCode;

    /** 重声明：公式名称（D5 遮蔽 MainModel.fieldName） */
    @ColumnField(name = "field_name", comment = "公式名称", type = ColumnType.STRING, length = 200)
    @Schema(description = "公式名称")
    private String fieldName;

    /** 公式类型（存枚举 SysFormulaType 名，仅作分类标记，不改变执行语义） */
    @ColumnField(name = "field_type", comment = "公式类型：FIELD/CONDITION/VALIDATION/VISIBILITY/WORKFLOW/CUSTOM",
            type = ColumnType.STRING, length = 20)
    @Schema(description = "公式类型：FIELD/CONDITION/VALIDATION/VISIBILITY/WORKFLOW/CUSTOM"
            + "（仅作分类标记，不改变执行语义）")
    private String fieldType;

    /**
     * 表达式文本（含 ${code} 变量引用）。
     * 存储态原样保留 ${} 形态，执行前由引擎替换为合法标识符并构建映射（方案 A5）；
     * 长度上限由 SysFormulaConstants 资源限制在应用层校验，列类型 TEXT 不设库级长度。
     */
    @ColumnField(name = "field_expression", comment = "表达式文本（含 ${code} 变量引用），长度上限经 SysFormulaConstants 资源限制校验",
            type = ColumnType.TEXT)
    @Schema(description = "表达式文本（含 ${code} 变量引用，显示/存储层原样保留 ${} 形态）")
    private String fieldExpression;

    /**
     * 变量依赖 JSON（save/update 时解析提取的变量码字符串数组）。
     * 双形态承载（先例 sys_number_rule.field_rule）：存储态与列表响应为原文 JSON 字符串，
     * load 响应由 Service 解析为对象返回；save/update 请求体经 Jackson 序列化为字符串落库。
     * 禁止手工拼串。NULL=表达式未引用任何变量。
     */
    @ColumnField(name = "field_dependencies", comment = "变量依赖 JSON：save/update 时解析提取的变量码数组",
            type = ColumnType.JSON, nullable = true)
    @Schema(description = "变量依赖 JSON：存储态 / 列表响应为原文 JSON 字符串（变量码数组），"
            + "load 响应为解析后的字符串数组；NULL=表达式未引用任何变量")
    private Object fieldDependencies;

    /** 期望返回类型（存枚举 SysFormulaReturnType 名，执行后按此适配结果类型） */
    @ColumnField(name = "field_return_type", comment = "期望返回类型：NUMBER/STRING/BOOLEAN/DATE/ARRAY",
            type = ColumnType.STRING, length = 20)
    @Schema(description = "期望返回类型：NUMBER/STRING/BOOLEAN/DATE/ARRAY")
    private String fieldReturnType;

    /** 备注/使用说明 */
    @ColumnField(name = "field_remark", comment = "备注/使用说明", type = ColumnType.STRING, length = 500, nullable = true)
    @Schema(description = "备注/使用说明")
    private String fieldRemark;
}

package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程模板连线实体
 *
 * @author Eric
 * @date 2026/11/19
 * @description 对应 sys_process_template_line 表：模板内节点间有向连线，v1_7 起流转的唯一依据；
 *              流水结构（随模板保存全量删旧插新，不做启停用），不设 field_enable /
 *              field_update_time / field_updater_id。
 *              <p>2026/11/19 P17-DB-001：类级 @TableSource / @TableIndex + 业务列 @ColumnField，
 *              列定义与流程v1_7数据库设计分册 §3.1.3 逐列一致；公共列（field_id 主键）由
 *              引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_process_template_line", comment = "流程模板连线表",
        indexes = {
                @TableIndex(name = "idx_template_line_template", columns = "field_template_id"),
                @TableIndex(name = "idx_template_line_from",
                        columns = {"field_template_id", "field_from_key"})})
public class SysProcessTemplateLine extends BaseModel {

    /** 模板id（sys_process_template.field_id） */
    @ColumnField(name = "field_template_id", comment = "模板id", type = ColumnType.STRING, length = 32)
    private String fieldTemplateId;

    /** 起始节点键（模板内节点业务键，如 N1） */
    @ColumnField(name = "field_from_key", comment = "起始节点键", type = ColumnType.STRING, length = 50)
    private String fieldFromKey;

    /** 目标节点键（模板内节点业务键，如 N2） */
    @ColumnField(name = "field_to_key", comment = "目标节点键", type = ColumnType.STRING, length = 50)
    private String fieldToKey;

    /** 连线类型：NORMAL / CONDITION / PARALLEL / MANUAL */
    @ColumnField(name = "field_line_type",
            comment = "连线类型: NORMAL=普通 CONDITION=条件分支 PARALLEL=并行分支 MANUAL=人工决策",
            type = ColumnType.STRING, length = 20)
    private String fieldLineType;

    /** 分支序号（条件/并行分支判断顺序，上移下移即改此值） */
    @ColumnField(name = "field_branch_order", comment = "分支序号(条件/并行分支判断顺序,上移下移即改此值)",
            type = ColumnType.INT)
    private Integer fieldBranchOrder;

    /** 分支名称（可空） */
    @ColumnField(name = "field_branch_name", comment = "分支名称",
            type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldBranchName;

    /** 分支条件公式（条件分支判断条件 / 并行分支启动条件，可空） */
    @ColumnField(name = "field_condition", comment = "分支条件公式(条件分支/并行启动条件)",
            type = ColumnType.TEXT, nullable = true)
    private String fieldCondition;

    /** 是否默认分支（条件分支节点有且仅有一条） */
    @ColumnField(name = "field_is_default", comment = "是否默认分支(条件分支节点有且仅有一条)",
            type = ColumnType.BOOLEAN)
    private Boolean fieldIsDefault;
}

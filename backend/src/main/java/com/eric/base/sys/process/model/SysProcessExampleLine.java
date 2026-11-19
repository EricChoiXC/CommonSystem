package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程实例连线实体
 *
 * @author Eric
 * @date 2026/11/19
 * @description 对应 sys_process_example_line 表：实例创建时从模板连线（sys_process_template_line）
 *              全量快照复制（与模板节点快照同语义），保证实例按创建时版本的图结构执行；
 *              流转引擎只读本表，不再回查模板。
 *              <p>流水结构（实例连线快照创建后不可变，实例结束亦不删除），不设 field_enable /
 *              field_update_time / field_updater_id；实例级创建人/创建时间落在
 *              sys_process_example，本表不重复承载。</p>
 *              <p>2026/11/19 P17-DB-002：类级 @TableSource / @TableIndex + 业务列 @ColumnField，
 *              列定义与流程v1_7数据库设计分册 §3.2.3 逐列一致（与模板连线表 §3.1.3 同构声明，
 *              仅 field_template_id → field_example_id 与索引名不同）；公共列（field_id 主键）
 *              由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_process_example_line", comment = "流程实例连线快照表",
        indexes = {
                @TableIndex(name = "idx_example_line_example", columns = "field_example_id"),
                @TableIndex(name = "idx_example_line_from",
                        columns = {"field_example_id", "field_from_key"})})
public class SysProcessExampleLine extends BaseModel {

    /** 流程实例id（sys_process_example.field_id） */
    @ColumnField(name = "field_example_id", comment = "流程实例id", type = ColumnType.STRING, length = 32)
    private String fieldExampleId;

    /** 起始节点键（实例内节点业务键快照） */
    @ColumnField(name = "field_from_key", comment = "起始节点键", type = ColumnType.STRING, length = 50)
    private String fieldFromKey;

    /** 目标节点键（实例内节点业务键快照） */
    @ColumnField(name = "field_to_key", comment = "目标节点键", type = ColumnType.STRING, length = 50)
    private String fieldToKey;

    /** 连线类型：NORMAL / CONDITION / PARALLEL / MANUAL */
    @ColumnField(name = "field_line_type",
            comment = "连线类型: NORMAL=普通 CONDITION=条件分支 PARALLEL=并行分支 MANUAL=人工决策",
            type = ColumnType.STRING, length = 20)
    private String fieldLineType;

    /** 分支序号（条件/并行分支判断顺序） */
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

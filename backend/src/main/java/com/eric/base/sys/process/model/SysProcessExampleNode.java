package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程实例节点实体
 *
 * @author Eric
 * @date 2026/8/23
 * @description 对应 sys_process_example_node 表：创建流程实例时从模板节点快照复制
 *              （节点编号/名称/类型/配置），随实例流转维护节点状态。
 *              field_node_config 为模板节点配置的 JSON 字符串快照。
 *              <p>2026/11/17 F19-DB-002 存量标注批二：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_process_example_node", comment = "流程实例节点表",
        indexes = {
                @TableIndex(name = "idx_example_node_example", columns = "field_example_id"),
                @TableIndex(name = "idx_example_node_no", columns = {"field_example_id", "field_node_no"})})
public class SysProcessExampleNode extends BaseModel {

    /** 流程实例id（sys_process_example.field_id） */
    @ColumnField(name = "field_example_id", comment = "流程实例id", type = ColumnType.STRING, length = 32)
    private String fieldExampleId;

    /** 节点编号 */
    @ColumnField(name = "field_node_no", comment = "节点编号", type = ColumnType.INT)
    private Integer fieldNodeNo;

    /** 节点名称 */
    @ColumnField(name = "field_node_name", comment = "节点名称", type = ColumnType.STRING, length = 200)
    private String fieldNodeName;

    /** 节点类型：START / END / DRAFT / APPROVE / ROBORT */
    @ColumnField(name = "field_node_type", comment = "节点类型: START=开始节点 END=结束节点 DRAFT=草稿节点 APPROVE=审批节点 ROBORT=机器人节点",
            type = ColumnType.STRING, length = 20)
    private String fieldNodeType;

    /** 节点配置（JSON 字符串，模板节点快照） */
    @ColumnField(name = "field_node_config", comment = "节点配置(JSON对象,模板节点快照)", type = ColumnType.JSON, nullable = true)
    private String fieldNodeConfig;

    /** 节点状态：WAITING / PROCESSING / FINISH / PASS / REJECT */
    @ColumnField(name = "field_status", comment = "节点状态: WAITING=等待处理 PROCESSING=审批中 FINISH=完成 PASS=跳过 REJECT=驳回",
            type = ColumnType.STRING, length = 20, defaultValue = "WAITING")
    private String fieldStatus;

    /** 节点业务键（模板节点快照，模板内唯一；存量实例由迁移回填） */
    @ColumnField(name = "field_node_key", comment = "节点业务键(模板节点快照,模板内唯一)",
            type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldNodeKey;

    /**
     * 节点运行态（JSON 字符串，固定结构 POJO：activeBranchKeys / arrivedBranchKeys / joinMode），
     * 仅 FORK/JOIN 节点有值；引擎内部运行态，不透出到 {@code SysProcessExampleNodeVo}（分册 §4.4）。
     */
    @ColumnField(name = "field_runtime", comment = "节点运行态(JSON对象,FORK激活分支集合/JOIN到达进度)",
            type = ColumnType.JSON, nullable = true)
    private String fieldRuntime;
}

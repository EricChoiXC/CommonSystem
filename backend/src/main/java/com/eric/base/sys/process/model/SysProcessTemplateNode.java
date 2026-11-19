package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程模板节点实体
 *
 * @author Eric
 * @date 2026/8/22
 * @description 对应 sys_process_template_node 表：模板节点（含固定节点与审批节点）。
 *              固定节点：1 开始节点 START、2 草稿节点 DRAFT、结束节点 END（无节点配置），
 *              结束节点编号随节点的创建往后顺延；审批节点配置保存在 field_node_config（JSON 字符串）。
 *              <p>2026/11/17 F19-DB-002 存量标注批二：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_process_template_node", comment = "流程模板节点表",
        indexes = {
                @TableIndex(name = "idx_template_node_no", columns = {"field_template_id", "field_no"}),
                @TableIndex(name = "idx_template_node_template", columns = "field_template_id")})
public class SysProcessTemplateNode extends BaseModel {

    /** 模板id（sys_process_template.field_id） */
    @ColumnField(name = "field_template_id", comment = "模板id", type = ColumnType.STRING, length = 32)
    private String fieldTemplateId;

    /** 节点编号 */
    @ColumnField(name = "field_no", comment = "节点编号", type = ColumnType.INT)
    private Integer fieldNo;

    /** 节点名称 */
    @ColumnField(name = "field_node_name", comment = "节点名称", type = ColumnType.STRING, length = 200)
    private String fieldNodeName;

    /** 节点类型：START / END / DRAFT / APPROVE / ROBORT */
    @ColumnField(name = "field_node_type", comment = "节点类型: START=开始节点 END=结束节点 DRAFT=草稿节点 APPROVE=审批节点 ROBORT=机器人节点",
            type = ColumnType.STRING, length = 20)
    private String fieldNodeType;

    /** 节点配置（JSON 字符串，审批节点才有：审批人/审批模式/节点触发/触发条件/是否允许自动跳过） */
    @ColumnField(name = "field_node_config", comment = "节点配置(JSON对象)", type = ColumnType.JSON, nullable = true)
    private String fieldNodeConfig;

    /** 节点业务键（模板内唯一，固定节点 N1=开始 N2=起草 N3=结束，其余顺延；存量行由迁移回填） */
    @ColumnField(name = "field_node_key", comment = "节点业务键(模板内唯一,固定节点 N1=开始 N2=起草 N3=结束,其余顺延)",
            type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldNodeKey;
}

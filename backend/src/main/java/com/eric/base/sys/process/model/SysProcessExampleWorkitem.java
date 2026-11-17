package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 流程工作项实体
 *
 * @author Eric
 * @date 2026/8/23
 * @description 对应 sys_process_example_workitem 表：进入流程节点时，为每个审批人创建一个工作项
 *              （包含跳过的处理人）。field_expect_handler_id 保存节点配置的期望处理人，
 *              field_handler_id 为实际处理人（跳过时两者一致，仅标记自动跳过）；
 *              field_config 保存工作项配置（如驳回后选择的「重新提交后流转方式」，JSON 字符串）。
 *              <p>2026/11/17 F19-DB-002 存量标注批二：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              表中存在的部分审计列 field_create_time 按业务列标注；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_process_example_workitem", comment = "流程工作项表",
        indexes = {
                @TableIndex(name = "idx_workitem_example", columns = "field_example_id"),
                @TableIndex(name = "idx_workitem_handler", columns = "field_handler_id"),
                @TableIndex(name = "idx_workitem_node", columns = "field_node_id"),
                @TableIndex(name = "idx_workitem_status", columns = "field_status")})
public class SysProcessExampleWorkitem extends BaseModel {

    /** 流程实例id（sys_process_example.field_id） */
    @ColumnField(name = "field_example_id", comment = "流程实例id", type = ColumnType.STRING, length = 32)
    private String fieldExampleId;

    /** 节点id（sys_process_example_node.field_id） */
    @ColumnField(name = "field_node_id", comment = "节点id(流程实例节点id)", type = ColumnType.STRING, length = 32)
    private String fieldNodeId;

    /** 处理人id（实际处理人，跳过场景下与期望处理人一致） */
    @ColumnField(name = "field_handler_id", comment = "处理人id", type = ColumnType.STRING, length = 32)
    private String fieldHandlerId;

    /** 期望处理人id（节点配置的审批人） */
    @ColumnField(name = "field_expect_handler_id", comment = "期望处理人id(原配置审批人,跳过时与处理人不一致)",
            type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldExpectHandlerId;

    /** 创建时间（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_create_time", comment = "创建时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldCreateTime;

    /** 完成时间 */
    @ColumnField(name = "field_finish_time", comment = "完成时间", type = ColumnType.TIMESTAMP, nullable = true)
    private Date fieldFinishTime;

    /** 工作项状态：PROCESSING / FINISH / ABANDON / PAUSE */
    @ColumnField(name = "field_status", comment = "工作项状态: PROCESSING=审批中 FINISH=完成 ABANDON=废弃 PAUSE=暂停",
            type = ColumnType.STRING, length = 20, defaultValue = "PROCESSING")
    private String fieldStatus;

    /** 工作项配置（JSON 字符串，如驳回后的「重新提交后流转方式」） */
    @ColumnField(name = "field_config", comment = "工作项配置(JSON对象,如驳回后重新提交流转方式)", type = ColumnType.JSON, nullable = true)
    private String fieldConfig;
}

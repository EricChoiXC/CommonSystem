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
 * 流程任务执行日志实体
 *
 * @author Eric
 * @date 2026/11/19
 * @description 对应 sys_process_task_log 表：承载 @ProcessTask 流程任务每次执行
 *              （ProcessTaskType 各步骤、节点配置 tasks 明细、强制任务）的流水留痕——
 *              任务名称/实现 bean/执行步骤/入参/结果摘要/成功标记/失败信息/耗时；
 *              <b>审批记录不承载任务留痕</b>（决策 D7）。
 *              <p>流水只增不改：不设 field_enable / field_update_time / field_updater_id
 *              （对齐先例 sys_task_history、sys_process_audit_note），应用侧只执行 insert；
 *              field_create_time 按业务列显式 @ColumnField 标注（BaseModel 不内建审计列），
 *              对齐先例 sys_process_audit_note / sys_process_example_workitem。
 *              field_params / field_result 为 JSON 字符串（固定结构 POJO 经 Jackson 序列化，
 *              禁止手工拼串）；公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 *              <p>2026/11/19 P17-DB-003：类级 @TableSource / @TableIndex + 业务列 @ColumnField，
 *              列定义与《流程v1_7数据库设计分册》§3.3.3 逐列一致、索引清单对齐 §3.3.4。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_process_task_log", comment = "流程任务执行日志表",
        indexes = {
                @TableIndex(name = "idx_task_log_example", columns = "field_example_id")})
public class SysProcessTaskLog extends BaseModel {

    /** 流程实例id（sys_process_example.field_id） */
    @ColumnField(name = "field_example_id", comment = "流程实例id", type = ColumnType.STRING, length = 32)
    private String fieldExampleId;

    /** 节点id（可空：StartProcess/EndProcess 步骤无节点） */
    @ColumnField(name = "field_node_id", comment = "节点id(流程实例节点id,StartProcess/EndProcess 步骤可空)",
            type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldNodeId;

    /** 工作项id（可空：节点级/流程级任务无工作项） */
    @ColumnField(name = "field_workitem_id", comment = "工作项id(工作项级任务留痕,节点/流程级可空)",
            type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldWorkitemId;

    /** 任务名称（@ProcessTask name） */
    @ColumnField(name = "field_task_name", comment = "任务名称", type = ColumnType.STRING, length = 200)
    private String fieldTaskName;

    /** 任务实现类/bean 标识 */
    @ColumnField(name = "field_task_bean", comment = "任务实现类/bean 标识",
            type = ColumnType.STRING, length = 200)
    private String fieldTaskBean;

    /** 执行步骤（ProcessTaskType） */
    @ColumnField(name = "field_task_type",
            comment = "执行步骤(ProcessTaskType): CreateProcess/StartProcess/StartNode/StartWorkitem/SendTodo/EndWorkitem/EndNode/EndProcess",
            type = ColumnType.STRING, length = 50)
    private String fieldTaskType;

    /** 任务参数（JSON 对象字符串，可空） */
    @ColumnField(name = "field_params", comment = "任务参数(JSON对象)", type = ColumnType.JSON, nullable = true)
    private String fieldParams;

    /** 执行结果摘要（JSON 对象字符串，可空） */
    @ColumnField(name = "field_result", comment = "执行结果摘要(JSON对象)", type = ColumnType.JSON, nullable = true)
    private String fieldResult;

    /** 是否成功：1=成功 0=失败 */
    @ColumnField(name = "field_success", comment = "是否成功: 1=成功 0=失败", type = ColumnType.BOOLEAN)
    private Boolean fieldSuccess;

    /** 失败信息（可空） */
    @ColumnField(name = "field_error_msg", comment = "失败信息", type = ColumnType.TEXT, nullable = true)
    private String fieldErrorMsg;

    /** 耗时毫秒 */
    @ColumnField(name = "field_cost_ms", comment = "耗时毫秒", type = ColumnType.BIGINT)
    private Long fieldCostMs;

    /** 创建时间（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_create_time", comment = "创建时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldCreateTime;
}

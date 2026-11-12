package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.BaseModel;
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
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysProcessExampleWorkitem extends BaseModel {

    /** 流程实例id（sys_process_example.field_id） */
    private String fieldExampleId;

    /** 节点id（sys_process_example_node.field_id） */
    private String fieldNodeId;

    /** 处理人id（实际处理人，跳过场景下与期望处理人一致） */
    private String fieldHandlerId;

    /** 期望处理人id（节点配置的审批人） */
    private String fieldExpectHandlerId;

    /** 创建时间 */
    private Date fieldCreateTime;

    /** 完成时间 */
    private Date fieldFinishTime;

    /** 工作项状态：PROCESSING / FINISH / ABANDON / PAUSE */
    private String fieldStatus;

    /** 工作项配置（JSON 字符串，如驳回后的「重新提交后流转方式」） */
    private String fieldConfig;
}

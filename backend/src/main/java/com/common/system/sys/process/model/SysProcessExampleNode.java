package com.common.system.sys.process.model;

import com.common.system.common.base.model.BaseModel;
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
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysProcessExampleNode extends BaseModel {

    /** 流程实例id（sys_process_example.field_id） */
    private String fieldExampleId;

    /** 节点编号 */
    private Integer fieldNodeNo;

    /** 节点名称 */
    private String fieldNodeName;

    /** 节点类型：START / END / DRAFT / APPROVE / ROBORT */
    private String fieldNodeType;

    /** 节点配置（JSON 字符串，模板节点快照） */
    private String fieldNodeConfig;

    /** 节点状态：WAITING / PROCESSING / FINISH / PASS / REJECT */
    private String fieldStatus;
}

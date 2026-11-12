package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.BaseModel;
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
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysProcessTemplateNode extends BaseModel {

    /** 模板id（sys_process_template.field_id） */
    private String fieldTemplateId;

    /** 节点编号 */
    private Integer fieldNo;

    /** 节点名称 */
    private String fieldNodeName;

    /** 节点类型：START / END / DRAFT / APPROVE / ROBORT */
    private String fieldNodeType;

    /** 节点配置（JSON 字符串，审批节点才有：审批人/审批模式/节点触发/触发条件/是否允许自动跳过） */
    private String fieldNodeConfig;
}

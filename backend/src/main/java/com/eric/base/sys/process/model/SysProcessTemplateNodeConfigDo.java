package com.eric.base.sys.process.model;

import lombok.Data;

import java.util.Map;

/**
 * 流程模板节点配置文档对象
 *
 * @author Eric
 * @date 2026/8/22
 * @description 对应 sys_process_template_node.field_node_config（JSON 字符串）的结构化映射，
 *              由 Jackson 序列化/反序列化。配置项依据 artificial/docs/设计文档/sys/process/流程核心业务.md
 *              「节点具体配置项信息」：审批来源（组织架构/表单字段，默认组织架构）、审批人、
 *              审批模式（单签/会审，默认单签）、节点触发（固定/条件判断）、触发条件（判断公式）、
 *              是否允许自动跳过（跟随系统/是/否）、操作标识开关（按 process_template.${fieldModelName}.ops 字典 key 开关）。
 *              固定节点（START/DRAFT/END）无节点配置，field_node_config 为空。
 */
@Data
public class SysProcessTemplateNodeConfigDo {

    /** 审批来源：org=组织架构（默认，取 handlerIds/handlerNames）/ field=表单字段（取 handlerField，运行期读文档字段值） */
    private String handlerSource;

    /** 审批人 id 列表（地址本多选，限岗位+用户，';' 分隔；审批来源为 org 时有效） */
    private String handlerIds;

    /** 审批人名称列表（与 handlerIds 一一对应，';' 分隔；审批来源为 org 时有效） */
    private String handlerNames;

    /** 审批人字段（文档实体字段属性名，取自 process_template.${fieldModelName}.fields 字典；审批来源为 field 时有效） */
    private String handlerField;

    /** 审批模式：single=单签 / countersign=会审，默认 single */
    private String approveMode;

    /** 节点触发：fixed=固定 / condition=条件判断，默认 fixed */
    private String triggerType;

    /** 触发条件（判断公式，triggerType=condition 时必填；${field} 占位符字段须为文档实体类存在字段） */
    private String condition;

    /** 是否允许自动跳过：system=跟随系统 / yes=是 / no=否，默认 system */
    private String autoSkip;

    /** 是否允许连续跳过：system=跟随系统 / yes=是 / no=否，默认 system */
    private String autoContinueSkip;

    /**
     * 操作标识开关：key=操作标识（须定义在 process_template.${fieldModelName}.ops 中），
     * value=该节点审批人是否允许执行该操作。
     * 未配置/未开启（true 之外）均视为不允许（默认不允许，兼容旧模板）。
     */
    private Map<String, Boolean> ops;
}

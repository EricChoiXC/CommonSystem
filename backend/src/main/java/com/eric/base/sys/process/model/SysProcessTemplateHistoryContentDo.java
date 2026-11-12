package com.eric.base.sys.process.model;

import lombok.Data;

import java.util.List;

/**
 * 流程模板历史版本内容文档对象
 *
 * @author Eric
 * @date 2026/8/22
 * @description 对应 sys_process_template_history.field_content（JSON 字符串）的结构化映射，
 *              由 Jackson 序列化/反序列化。更新模板时将历史版本的模板与所有节点字段信息
 *              统一整理保存（依据 artificial/docs/设计文档/sys/process/流程核心业务.md「模板维护」），
 *              流水只增不改。
 */
@Data
public class SysProcessTemplateHistoryContentDo {

    /** 归档时刻的模板快照（现版本信息的一部分） */
    private TemplateSnapshot template;

    /** 归档时刻的节点快照列表（按节点编号升序） */
    private List<NodeSnapshot> nodes;

    /**
     * 模板快照：仅保存归档所需的模板基本字段。
     */
    @Data
    public static class TemplateSnapshot {

        /** 模板 ID */
        private String fieldId;

        /** 模板名称 */
        private String fieldName;

        /** 所属模块（process_template 二级配置项） */
        private String fieldModelName;

        /** 模板编号 */
        private String fieldCode;

        /** 版本号 */
        private Integer fieldVersion;

        /** 备注 */
        private String fieldRemark;

        /** 是否启用 */
        private Boolean fieldEnable;
    }

    /**
     * 节点快照：节点基本信息 + 审批节点配置（固定节点无配置）。
     */
    @Data
    public static class NodeSnapshot {

        /** 节点编号 */
        private Integer fieldNo;

        /** 节点名称 */
        private String fieldNodeName;

        /** 节点类型：START / END / DRAFT / APPROVE / ROBORT */
        private String fieldNodeType;

        /** 节点配置（审批节点才有，固定节点为 null） */
        private SysProcessTemplateNodeConfigDo fieldNodeConfig;
    }
}

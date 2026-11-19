package com.eric.base.sys.process.model;

import lombok.Data;

import java.util.List;

/**
 * 流程模板历史版本内容文档对象
 *
 * @author Eric
 * @date 2026/8/22
 * @description 对应 sys_process_template_history.field_content（JSON 字符串）的结构化映射，
 *              由 Jackson 序列化/反序列化。更新模板时将历史版本的模板、全部节点与全部连线
 *              字段信息统一整理保存（依据 docs/设计文档/sys/process/流程核心业务.md「模板维护」
 *              与 docs/设计文档/sys/process/流程v1_7接口清单.md §4.3「历史版本归档」），
 *              流水只增不改。
 *              <p>向后兼容：v1_7 之前的归档 JSON 无 {@code nodeSnapshot.fieldNodeKey} 与
 *              {@code lines} 键，Jackson 反序列化按缺省值处理（键为 null、连线为空），
 *              历史版本读取不受影响。</p>
 */
@Data
public class SysProcessTemplateHistoryContentDo {

    /** 归档时刻的模板快照（现版本信息的一部分） */
    private TemplateSnapshot template;

    /** 归档时刻的节点快照列表（按节点编号升序） */
    private List<NodeSnapshot> nodes;

    /** 归档时刻的连线快照列表（v1_7 新增，按起始节点键、分支序号升序；旧归档 JSON 缺省为空） */
    private List<LineSnapshot> lines;

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

        /** 节点业务键（模板内唯一，固定节点 N1/N2/N3；v1_7 新增，旧归档 JSON 缺省为 null） */
        private String fieldNodeKey;

        /** 节点名称 */
        private String fieldNodeName;

        /** 节点类型：START / DRAFT / END / APPROVE / CC / CONDITION / FORK / JOIN / ROBORT / MANUAL */
        private String fieldNodeType;

        /** 节点配置（审批节点才有，固定节点为 null） */
        private SysProcessTemplateNodeConfigDo fieldNodeConfig;
    }

    /**
     * 连线快照（v1_7 新增）：连线行全字段，供历史版本回看与图结构比对。
     */
    @Data
    public static class LineSnapshot {

        /** 连线 ID */
        private String fieldId;

        /** 起始节点业务键 */
        private String fieldFromKey;

        /** 目标节点业务键 */
        private String fieldToKey;

        /** 连线类型：NORMAL / CONDITION / PARALLEL / MANUAL */
        private String fieldLineType;

        /** 分支序号（条件/并行分支判断顺序） */
        private Integer fieldBranchOrder;

        /** 分支名称 */
        private String fieldBranchName;

        /** 分支条件公式（条件分支判断条件 / 并行分支启动条件） */
        private String fieldCondition;

        /** 是否默认分支（条件分支节点有且仅有一条） */
        private Boolean fieldIsDefault;
    }
}

package com.eric.base.sys.process.response;

import lombok.Data;

/**
 * 流程面板节点选项视图对象
 *
 * @author Eric
 * @date 2026/11/20
 * @description 审批面板按钮集内嵌的节点候选行（v1_7 新增，P17-BE-013，
 *              依据 docs/设计文档/sys/process/流程v1_7接口清单.md §5.4）：
 *              承载按钮的 {@code rejectTargets}（驳回候选，仅 handler_reject）与
 *              {@code flowChoices}（人工决策流向候选，仅 handler_pass / drafter_submit）
 *              元素结构——节点业务键 + 节点名称。
 */
@Data
public class SysProcessNodeOptionVo {

    /** 节点业务键（实例节点 nodeKey 快照，模板内唯一） */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;
}

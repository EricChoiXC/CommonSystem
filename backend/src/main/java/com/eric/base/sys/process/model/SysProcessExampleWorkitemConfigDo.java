package com.eric.base.sys.process.model;

import lombok.Data;

/**
 * 流程工作项配置文档对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 对应 sys_process_example_workitem.field_config（JSON 字符串）的结构化映射，
 *              由 Jackson 序列化/反序列化。承载工作项级操作选择信息（依据
 *              docs/设计文档/sys/process/流程核心业务.md「驳回与重新提交（D3）」「人工决策流向（D6）」）：
 *              驳回操作选择的「重新提交后流转方式」（resumeMode，沿用）与驳回目标节点 key
 *              （rejectTargetKey，v1_7 新增，写入驳回者工作项，供重新提交 backToNode 时进入目标节点）、
 *              人工决策流向指定（flowChoice，v1_7 新增）：流向决策来源节点（人工决策节点 flowDecisions 包含本节点）
 *              的审批人提交/通过时可指定该决策节点流向（值为该决策节点出边目标节点 key，可选），
 *              后提交覆盖先提交。
 */
@Data
public class SysProcessExampleWorkitemConfigDo {

    /** 重新提交后流转方式：restart=重新流转 / backToNode=转回本节点 */
    private String resumeMode;

    /**
     * 驳回目标节点 key（可空；v1_7 新增 D3）：驳回者选择的驳回目标（节点配置 rejectTargets ∩ 已流经节点，
     * 空默认起草节点）；写入驳回者工作项，重新提交 backToNode 时进入该目标节点
     * （值为空或为起草节点时回退被驳回节点，兼容历史数据）。
     */
    private String rejectTargetKey;

    /** 人工决策流向（目标节点 key，可空；v1_7 新增 D6）：审批人提交/通过时附带指定人工决策节点流向 */
    private String flowChoice;

    /**
     * 所属并行分支键（可空；v1_7 新增）：并行分支内的审批节点创建工作项时记录其分支首节点键，
     * 供审批通过后跨调用恢复流转时回溯分支归属（{@code sys_process_example_node.field_runtime}
     * 的 {@code activeBranchKeys} / {@code arrivedBranchKeys} 判定不重复计数）。
     */
    private String flowBranchKey;
}

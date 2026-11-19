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
 *              驳回操作选择的「重新提交后流转方式」（resumeMode，沿用）与人工决策流向指定
 *              （flowChoice，v1_7 新增）：流向决策来源节点（人工决策节点 flowDecisions 包含本节点）
 *              的审批人提交/通过时可指定该决策节点流向（值为该决策节点出边目标节点 key，可选），
 *              后提交覆盖先提交。
 */
@Data
public class SysProcessExampleWorkitemConfigDo {

    /** 重新提交后流转方式：restart=重新流转 / backToNode=转回本节点 */
    private String resumeMode;

    /** 人工决策流向（目标节点 key，可空；v1_7 新增 D6）：审批人提交/通过时附带指定人工决策节点流向 */
    private String flowChoice;
}

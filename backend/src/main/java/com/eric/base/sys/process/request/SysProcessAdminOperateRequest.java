package com.eric.base.sys.process.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程实例特权操作请求
 *
 * @author Eric
 * @date 2026/8/23
 * @description 流程实例详情页/文档审批页的特权操作（依据 docs/设计文档/sys/process/
 *              流程核心业务.md「流程实例详情」与管理员各操作章节，契约见
 *              docs/设计文档/sys/process/流程v1_7接口清单.md §5.5）：
 *              操作类型 JUMP=跳转 / FINISH=终审通过 / ABANDON=作废 / PAUSE=暂停 / RESUME=恢复 /
 *              CHANGE_HANDLER=更换审批人（v1_7 新增）；
 *              跳转时 targetNodeId 与 targetNodeKey 二者其一必填（targetNodeId 优先，限审批节点、
 *              起草节点或人工决策节点）；更换审批人时 workitemId / changeToId 必填；意见必填。
 */
@Data
public class SysProcessAdminOperateRequest {

    @Schema(description = "流程实例 id（必填）")
    private String fieldId;

    @Schema(description = "操作类型：JUMP 跳转 / FINISH 终审通过 / ABANDON 作废 / PAUSE 暂停 / RESUME 恢复 / "
            + "CHANGE_HANDLER 更换审批人（v1_7 新增）")
    private String operateType;

    @Schema(description = "跳转目标节点 id（流程实例节点 id；operateType=JUMP 时与 targetNodeKey 二者其一必填，"
            + "两者同时提供时 targetNodeId 优先；限审批节点、起草节点或人工决策节点）")
    private String targetNodeId;

    @Schema(description = "跳转目标节点业务键（v1_7 支持按 key 指定；targetNodeId 为空且本字段非空时，"
            + "按实例节点集合的 nodeKey 匹配解析目标节点，找不到抛 CS_PROCESS_NODE_TARGET_INVALID）")
    private String targetNodeKey;

    @Schema(description = "待换人的工作项 id（operateType=CHANGE_HANDLER 必填，须为本实例 PROCESSING 工作项）")
    private String workitemId;

    @Schema(description = "更换后的目标处理人用户 id（operateType=CHANGE_HANDLER 必填；允许禁用用户，"
            + "对齐审批人解析语义；不存在拒绝）")
    private String changeToId;

    @Schema(description = "操作意见（必填）")
    private String content;
}

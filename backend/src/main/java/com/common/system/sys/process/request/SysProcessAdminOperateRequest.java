package com.common.system.sys.process.request;

import lombok.Data;

/**
 * 流程实例特权操作请求
 *
 * @author Eric
 * @date 2026/8/23
 * @description 流程实例详情页/文档审批页的特权操作（依据 artificial/docs/设计文档/sys/process/
 *              流程核心业务.md「流程实例详情」与管理员各操作章节）：
 *              操作类型 JUMP=跳转 / FINISH=终审通过 / ABANDON=作废 / PAUSE=暂停 / RESUME=恢复；
 *              跳转时 targetNodeId 必填（限审批节点或起草节点）；意见必填。
 */
@Data
public class SysProcessAdminOperateRequest {

    /** 流程实例 id */
    private String fieldId;

    /** 操作类型：JUMP / FINISH / ABANDON / PAUSE / RESUME */
    private String operateType;

    /** 跳转节点 id（流程实例节点 id，仅跳转时必填；限审批节点或起草节点） */
    private String targetNodeId;

    /** 意见（必填） */
    private String content;
}

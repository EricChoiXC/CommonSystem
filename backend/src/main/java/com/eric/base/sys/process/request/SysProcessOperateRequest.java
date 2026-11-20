package com.eric.base.sys.process.request;

import lombok.Data;

/**
 * 流程审批统一操作请求
 *
 * @author Eric
 * @date 2026/11/20
 * @description 统一操作接口 POST /backend/sys/process/operation/operate 的请求体
 *              （依据 docs/设计文档/sys/process/流程v1_7接口清单.md §6.1）：
 *              14 类审批操作（operateType，小写下划线）统一入口，resolver 层准入后
 *              另需引擎内操作人身份校验（起草人 / 当前处理人 / 已处理人 / 流程管理员）。
 *              业务模块 audit 转发通道复用同一请求结构（SysProcessExampleService#operate），
 *              旧调用（既有大写操作类型）不传新参数时按原语义执行（向后兼容）。
 */
@Data
public class SysProcessOperateRequest {

    /** 流程实例 id（必填） */
    private String exampleId;

    /** 操作类型（必填）：14 类操作标识（小写下划线，见接口清单 §6.2）或既有大写审批记录类型（audit 转发通道） */
    private String operateType;

    /** 目标工作项 id（审批人类操作必填，如 handler_pass / handler_reject） */
    private String workitemId;

    /** 审批意见/说明（除 drafter_submit 外建议必填，按操作类型校验） */
    private String content;

    /** 管理员跳转目标节点 id（admin_jump 必填） */
    private String targetNodeId;

    /** 驳回目标节点 key（handler_reject 必填；目标范围校验由 P17-BE-009 落地） */
    private String rejectTargetKey;

    /** 人工决策流向（目标节点 key；drafter_submit / handler_pass 可选携带，后提交覆盖先提交） */
    private String flowChoice;

    /** 转办目标用户 id 列表（handler_transfer 必填，';' 分隔或多值；引擎分发由 P17-BE-010 落地） */
    private String transferToIds;

    /** 管理员更换审批人的目标用户 id（admin_change_handler 必填；引擎分发由 P17-BE-013 落地） */
    private String changeToId;

    /** 驳回后重新提交流转方式：restart（重新流转）/ backToNode（转回驳回节点）；handler_reject 必填 */
    private String resumeMode;

    /** 抄送对象 id 列表（handler_cc；与 transferToIds 同构的 ID 列表字段，';' 分隔，Q-01 定稿；本期仅承载，P17-BE-012 消费） */
    private String ccToIds;
}

package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 流程审批记录实体
 *
 * @author Eric
 * @date 2026/8/23
 * @description 对应 sys_process_audit_note 表：起草人（提交/收回/废弃）、审批人（通过/驳回）、
 *              管理员（跳转/暂停/恢复/作废/办结）以及自动跳过（AUTO_PASS）的流转留痕。
 *              field_workitem_id 关联工作项（自动跳过等无工作项的操作可为空）；
 *              field_content 保存审批意见/说明。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysProcessAuditNote extends BaseModel {

    /** 流程实例id（sys_process_example.field_id） */
    private String fieldExampleId;

    /** 工作项id（sys_process_example_workitem.field_id，可为空） */
    private String fieldWorkitemId;

    /** 处理人id（起草人/审批人/管理员） */
    private String fieldHandlerId;

    /** 审批内容（意见/说明） */
    private String fieldContent;

    /** 创建时间 */
    private Date fieldCreateTime;

    /** 处理操作：DRAFTER_SUBMIT / DRAFTER_BACK / DRAFTER_ABANDON / HANDLER_PASS / HANDLER_RETURN /
     *  AUTO_PASS / ADMIN_PAUSE / ADMIN_RESUME / ADMIN_JUMP / ADMIN_ABANDON / ADMIN_FINISH */
    private String fieldHandleType;
}

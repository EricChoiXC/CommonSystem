package com.common.system.sys.process.response;

import lombok.Data;

import java.util.Date;

/**
 * 流程审批记录视图对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 流程实例详情页/审批面板的审批记录行数据：处理人、处理操作、审批内容与时间。
 */
@Data
public class SysProcessAuditNoteVo {

    /** 审批记录 id */
    private String fieldId;

    /** 工作项 id（可为空） */
    private String fieldWorkitemId;

    /** 处理人 id */
    private String fieldHandlerId;

    /** 处理人名称 */
    private String fieldHandlerName;

    /** 审批内容（意见/说明） */
    private String fieldContent;

    /** 创建时间 */
    private Date fieldCreateTime;

    /** 处理操作：DRAFTER_SUBMIT / DRAFTER_BACK / DRAFTER_ABANDON / HANDLER_PASS / HANDLER_RETURN /
     *  AUTO_PASS / ADMIN_PAUSE / ADMIN_RESUME / ADMIN_JUMP / ADMIN_ABANDON / ADMIN_FINISH */
    private String fieldHandleType;
}

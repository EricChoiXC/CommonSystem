package com.eric.base.sys.notify.response;

import lombok.Data;

import java.util.Date;

/**
 * 后台待办视图对象
 *
 * @author Eric
 * @date 2026/8/12
 * @description 「待办管理」页面的行数据：以 sys_notify_todo 为主体，
 *              汇总 sys_notify_todo_target 的接收人数与已办人数。
 */
@Data
public class SysNotifyAdminTodoVo {

    /** 待办id */
    private String fieldId;

    /** 标题 */
    private String fieldName;

    /** 来源模块 */
    private String fieldModelName;

    /** 通知类型: 1=待办 2=待阅 */
    private String fieldNotifyType;

    /** 创建时间 */
    private Date fieldCreateTime;

    /** 接收人数 */
    private long fieldReceiveCount;

    /** 已办人数 */
    private long fieldDoneCount;
}

package com.eric.base.sys.notify.response;

import lombok.Data;

import java.util.Date;

/**
 * 我的待办视图对象
 *
 * @author Eric
 * @date 2026/8/12
 * @description 「待办列表」页面的行数据与详情数据：以接收记录（sys_notify_todo_target）
 *              为主体，JOIN sys_notify_todo 组装标题/来源模块/通知类型/链接。
 */
@Data
public class SysNotifyTodoVo {

    /** 接收记录id（sys_notify_todo_target.field_id） */
    private String fieldId;

    /** 待办id */
    private String fieldTodoId;

    /** 标题 */
    private String fieldName;

    /** 发送人姓名 */
    private String fieldCreatorName;

    /** 来源模块 */
    private String fieldModelName;

    /** 通知类型: 1=待办 2=待阅 */
    private String fieldNotifyType;

    /** 链接 */
    private String fieldUrl;

    /** 内容 */
    private String fieldContent;

    /** 是否已办 */
    private Boolean fieldIsDone;

    /** 是否星标 */
    private Boolean fieldIsStar;

    /** 接收时间 */
    private Date fieldReceiveTime;

    /** 办结时间 */
    private Date fieldDoneTime;

    /** 收藏时间 */
    private Date fieldStarTime;
}

package com.eric.base.sys.notify.request;

import lombok.Data;

import java.util.Date;

/**
 * 我的待办列表筛选条件
 *
 * @author Eric
 * @date 2026/8/12
 * @description 对应「待办列表」页面的筛选区：待办类型（默认待办，无"全部"）、
 *              来源模块、是否星标、是否办结（默认未办，无"全部"）、
 *              标题、接收时间范围（限年月日）。
 */
@Data
public class SysNotifyTodoQuery {

    /** 待办类型: 1=待办 2=待阅（默认待办，不可"全部"） */
    private String fieldNotifyType;

    /** 来源模块 */
    private String fieldModelName;

    /** 是否星标（null 表示全部） */
    private Boolean fieldIsStar;

    /** 标题 */
    private String fieldName;

    /** 是否已办（null 表示全部；默认 false 未办） */
    private Boolean fieldIsDone;

    /** 接收时间起（含） */
    private Date fieldReceiveTimeStart;

    /** 接收时间止（含） */
    private Date fieldReceiveTimeEnd;
}

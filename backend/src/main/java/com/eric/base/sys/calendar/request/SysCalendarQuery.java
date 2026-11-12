package com.eric.base.sys.calendar.request;

import lombok.Data;

import java.util.Date;

/**
 * 日程列表查询条件
 *
 * @author Eric
 * @date 2026/8/21
 */
@Data
public class SysCalendarQuery {

    /** 标题（模糊查询） */
    private String fieldName;

    /** 类型：PERSONAL/TEAM */
    private String fieldType;

    /** 是否接收筛选：ACCEPTED=已接收 REJECTED=已拒绝 UNCONFIRMED=未确认 */
    private String fieldAccept;

    /** 日程开始时间-起始 */
    private Date fieldStartTimeStart;

    /** 日程开始时间-结束 */
    private Date fieldStartTimeEnd;

    /** 月份查询-年 */
    private Integer year;

    /** 月份查询-月 */
    private Integer month;
}
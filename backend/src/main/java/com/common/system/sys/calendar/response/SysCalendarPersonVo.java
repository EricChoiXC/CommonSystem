package com.common.system.sys.calendar.response;

import lombok.Data;

/**
 * 日程人员响应对象
 *
 * @author Eric
 * @date 2026/8/21
 */
@Data
public class SysCalendarPersonVo {

    /** 关联id（sys_calendar_persons.field_id） */
    private String fieldId;

    /** 人员id */
    private String fieldPersonId;

    /** 人员名称 */
    private String fieldPersonName;

    /** 是否接收（NULL=未确认 true=接收 false=拒绝） */
    private Boolean fieldAccept;
}
package com.common.system.sys.calendar.model;

import com.common.system.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 日程人员表模型
 *
 * @author Eric
 * @date 2026/8/21
 * @description 对应 sys_calendar_persons
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysCalendarPersons extends BaseModel {

    /** 日程id */
    private String fieldCalendarId;

    /** 人员id */
    private String fieldPersonId;

    /** 是否接收（可为空，NULL=未确认 1=接收 0=拒绝） */
    private Boolean fieldAccept;
}
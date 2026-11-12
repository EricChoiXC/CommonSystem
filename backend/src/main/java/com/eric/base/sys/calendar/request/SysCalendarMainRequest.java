package com.eric.base.sys.calendar.request;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 日程主表请求对象
 *
 * @author Eric
 * @date 2026/8/21
 */
@Data
public class SysCalendarMainRequest {

    /** id（新建时为空，编辑时必填） */
    private String fieldId;

    /** 标题 */
    private String fieldName;

    /** 开始时间 */
    private Date fieldStartTime;

    /** 结束时间 */
    private Date fieldEndTime;

    /** 类型：PERSONAL=个人日程 TEAM=团队日程 */
    private String fieldType;

    /** 内容 */
    private String fieldContent;

    /** 接收人id列表（团队日程时必填） */
    private List<String> personIds;
}
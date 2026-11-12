package com.eric.base.sys.task.request;

import lombok.Data;

import java.util.Date;

/**
 * 执行记录列表筛选条件
 *
 * @author Eric
 * @date 2026/8/15
 * @description 对应「执行记录」页面的筛选区：任务名称、结果、开始时间（范围）、结束时间（范围）。
 */
@Data
public class SysTaskHistoryQuery {

    /** 任务名称（按 sys_task_main.field_name 模糊匹配） */
    private String fieldTaskName;

    /** 结果: 1=成功 0=失败（空表示全部） */
    private String fieldResult;

    /** 开始时间起（含） */
    private Date fieldStartTimeStart;

    /** 开始时间止（含） */
    private Date fieldStartTimeEnd;

    /** 结束时间起（含） */
    private Date fieldEndTimeStart;

    /** 结束时间止（含） */
    private Date fieldEndTimeEnd;
}

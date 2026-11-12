package com.eric.base.sys.task.response;

import lombok.Data;

import java.util.Date;

/**
 * 执行记录视图对象
 *
 * @author Eric
 * @date 2026/8/15
 * @description 「执行记录」列表页的行数据与详情数据：以 sys_task_history 为主体，
 *              JOIN sys_task_main 组装任务名。
 */
@Data
public class SysTaskHistoryVo {

    /** 执行记录id */
    private String fieldId;

    /** 任务id */
    private String fieldTaskId;

    /** 任务名（JOIN sys_task_main） */
    private String fieldTaskName;

    /** 结果: 1=成功 0=失败 */
    private String fieldResult;

    /** 开始时间 */
    private Date fieldStartTime;

    /** 结束时间 */
    private Date fieldEndTime;

    /** 执行组 */
    private String fieldGroup;

    /** 执行参数(JSON对象字符串) */
    private String fieldParams;

    /** 日志 */
    private String fieldLog;
}

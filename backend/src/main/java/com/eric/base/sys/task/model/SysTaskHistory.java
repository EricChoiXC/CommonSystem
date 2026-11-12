package com.eric.base.sys.task.model;

import com.eric.base.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 任务执行历史模型
 *
 * @author Eric
 * @date 2026/8/15
 * @description 对应 sys_task_history：任务每次执行的流水记录，只增不改。
 *              field_result 为结果（1=成功 0=失败）；field_group 为执行组，
 *              与 sys_task_example.field_group 同组对应；field_params 为执行参数
 *              （JSON 对象字符串，值均为字符串）；field_log 为执行日志文本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysTaskHistory extends BaseModel {

    /** 任务id */
    private String fieldTaskId;

    /** 结果: 1=成功 0=失败 */
    private String fieldResult;

    /** 开始时间 */
    private Date fieldStartTime;

    /** 结束时间 */
    private Date fieldEndTime;

    /** 执行组 */
    private String fieldGroup;

    /** 执行参数(JSON对象字符串，值均为字符串) */
    private String fieldParams;

    /** 日志 */
    private String fieldLog;
}

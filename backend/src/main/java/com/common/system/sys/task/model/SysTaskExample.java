package com.common.system.sys.task.model;

import com.common.system.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 任务执行实例模型
 *
 * @author Eric
 * @date 2026/8/15
 * @description 对应 sys_task_example：每次任务执行（或触发被阻塞）登记一条执行实例，
 *              一个任务同时只执行一个实例（由应用运行态保证）。
 *              field_group 为运行组（每次执行生成唯一组号），与
 *              sys_task_history.field_group 同组对应；实例状态与任务主表状态枚举一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysTaskExample extends BaseModel {

    /** 状态: WAITING=等待中 RUNNING=运行中 FINISH=完成 BLOCK=阻塞 FAIL=失败 */
    private String fieldStatus;

    /** 开始时间 */
    private Date fieldStartTime;

    /** 运行组 */
    private String fieldGroup;
}

package com.eric.base.sys.task.request;

import lombok.Data;

/**
 * 定时任务列表筛选条件
 *
 * @author Eric
 * @date 2026/8/15
 * @description 对应「定时任务管理」页面的筛选区：名称、状态、是否启用。
 */
@Data
public class SysTaskMainQuery {

    /** 任务名 */
    private String fieldName;

    /** 状态: WAITING/RUNNING/FINISH/BLOCK/FAIL（空表示全部） */
    private String fieldStatus;

    /** 是否启用（空表示全部） */
    private Boolean fieldEnable;
}

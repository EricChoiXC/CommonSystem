package com.eric.base.sys.task.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
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
 *              <p>2026/11/17 F19-DB-002 存量标注批二：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_task_example", comment = "任务执行实例表",
        indexes = {
                @TableIndex(name = "idx_task_example_group", columns = "field_group"),
                @TableIndex(name = "idx_task_example_status", columns = "field_status")})
public class SysTaskExample extends BaseModel {

    /** 状态: WAITING=等待中 RUNNING=运行中 FINISH=完成 BLOCK=阻塞 FAIL=失败 */
    @ColumnField(name = "field_status", comment = "状态: WAITING=等待中 RUNNING=运行中 FINISH=完成 BLOCK=阻塞 FAIL=失败",
            type = ColumnType.STRING, length = 20)
    private String fieldStatus;

    /** 开始时间 */
    @ColumnField(name = "field_start_time", comment = "开始时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldStartTime;

    /** 运行组 */
    @ColumnField(name = "field_group", comment = "运行组", type = ColumnType.STRING, length = 200)
    private String fieldGroup;
}

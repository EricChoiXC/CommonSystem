package com.eric.base.sys.task.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
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
 *              <p>2026/11/17 F19-DB-002 存量标注批二：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_task_history", comment = "任务执行历史表",
        indexes = {
                @TableIndex(name = "idx_task_history_end", columns = "field_end_time"),
                @TableIndex(name = "idx_task_history_start", columns = "field_start_time"),
                @TableIndex(name = "idx_task_history_task", columns = "field_task_id")})
public class SysTaskHistory extends BaseModel {

    /** 任务id */
    @ColumnField(name = "field_task_id", comment = "任务id", type = ColumnType.STRING, length = 32)
    private String fieldTaskId;

    /** 结果: 1=成功 0=失败 */
    @ColumnField(name = "field_result", comment = "结果: 1=成功 0=失败", type = ColumnType.STRING, length = 1)
    private String fieldResult;

    /** 开始时间 */
    @ColumnField(name = "field_start_time", comment = "开始时间", type = ColumnType.TIMESTAMP)
    private Date fieldStartTime;

    /** 结束时间 */
    @ColumnField(name = "field_end_time", comment = "结束时间", type = ColumnType.TIMESTAMP, nullable = true)
    private Date fieldEndTime;

    /** 执行组 */
    @ColumnField(name = "field_group", comment = "执行组", type = ColumnType.STRING, length = 200)
    private String fieldGroup;

    /** 执行参数(JSON对象字符串，值均为字符串) */
    @ColumnField(name = "field_params", comment = "执行参数(JSON对象，值均为字符串)", type = ColumnType.JSON, nullable = true)
    private String fieldParams;

    /** 日志 */
    @ColumnField(name = "field_log", comment = "日志", type = ColumnType.TEXT, nullable = true)
    private String fieldLog;
}

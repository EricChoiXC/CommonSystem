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
 * 定时任务主表模型
 *
 * @author Eric
 * @date 2026/8/15
 * @description 对应 sys_task_main；任务元信息由系统启动时扫描 {@code @SysTask} 注解
 *              写入（默认开启），管理员可编辑名称 / cron / 参数列表值并启停用。
 *              field_params 以 JSON 对象字符串保存（值均为字符串）；
 *              field_class 为任务类的全限定类名，作为扫描写入的匹配键。
 *              <p>2026/11/17 F19-DB-002 存量标注批二：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              表中存在的部分审计列 field_enable / field_create_time 按业务列标注；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_task_main", comment = "定时任务主表",
        indexes = {
                @TableIndex(name = "idx_task_main_enable", columns = "field_enable"),
                @TableIndex(name = "uk_task_main_class", columns = "field_class", unique = true)})
public class SysTaskMain extends BaseModel {

    /** 任务名 */
    @ColumnField(name = "field_name", comment = "任务名", type = ColumnType.STRING, length = 200)
    private String fieldName;

    /** 是否启用（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_enable", comment = "是否启用", type = ColumnType.BOOLEAN, defaultValue = "b'1'")
    private Boolean fieldEnable;

    /** cron表达式 */
    @ColumnField(name = "field_cron", comment = "cron表达式", type = ColumnType.STRING, length = 200)
    private String fieldCron;

    /** 任务类名（全限定类名） */
    @ColumnField(name = "field_class", comment = "任务类名", type = ColumnType.STRING, length = 200)
    private String fieldClass;

    /** 参数(JSON对象字符串，值均为字符串) */
    @ColumnField(name = "field_params", comment = "参数(JSON对象，值均为字符串)", type = ColumnType.JSON, nullable = true)
    private String fieldParams;

    /** 状态: WAITING=等待中 RUNNING=运行中 FINISH=完成 BLOCK=阻塞 FAIL=失败 */
    @ColumnField(name = "field_status", comment = "状态: WAITING=等待中 RUNNING=运行中 FINISH=完成 BLOCK=阻塞 FAIL=失败",
            type = ColumnType.STRING, length = 20, defaultValue = "WAITING")
    private String fieldStatus;

    /** 登记时间（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_create_time", comment = "登记时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldCreateTime;
}

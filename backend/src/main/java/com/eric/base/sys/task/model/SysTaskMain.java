package com.eric.base.sys.task.model;

import com.eric.base.common.base.model.BaseModel;
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
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysTaskMain extends BaseModel {

    /** 任务名 */
    private String fieldName;

    /** 是否启用 */
    private Boolean fieldEnable;

    /** cron表达式 */
    private String fieldCron;

    /** 任务类名（全限定类名） */
    private String fieldClass;

    /** 参数(JSON对象字符串，值均为字符串) */
    private String fieldParams;

    /** 状态: WAITING=等待中 RUNNING=运行中 FINISH=完成 BLOCK=阻塞 FAIL=失败 */
    private String fieldStatus;

    /** 登记时间 */
    private Date fieldCreateTime;
}

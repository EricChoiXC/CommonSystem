package com.eric.base.sys.task.properties;

/**
 * 定时任务模块常量
 *
 * @author Eric
 * @date 2026/8/15
 * @description 接口标识（resolver 鉴权使用）与角色编码，供 Controller / Resolver 静态引用。
 */
public class SysTaskConstants {

    /** 定时任务列表 */
    public static final String ACTION_LIST = "sys.task.main.list";
    /** 定时任务详情（编辑回填） */
    public static final String ACTION_LOAD = "sys.task.main.load";
    /** 编辑定时任务（名称/cron/参数列表值） */
    public static final String ACTION_UPDATE = "sys.task.main.update";
    /** 启用 */
    public static final String ACTION_ENABLE = "sys.task.main.enable";
    /** 停用 */
    public static final String ACTION_DISABLE = "sys.task.main.disable";
    /** 批量启用 */
    public static final String ACTION_BATCH_ENABLE = "sys.task.main.batchEnable";
    /** 批量停用 */
    public static final String ACTION_BATCH_DISABLE = "sys.task.main.batchDisable";
    /** 手动执行 */
    public static final String ACTION_EXECUTE = "sys.task.main.execute";
    /** 中止当前任务 */
    public static final String ACTION_STOP = "sys.task.main.stop";
    /** 执行记录列表 */
    public static final String ACTION_HISTORY_LIST = "sys.task.history.list";
    /** 执行记录详情 */
    public static final String ACTION_HISTORY_LOAD = "sys.task.history.load";

    /** 定时任务管理权限（见 artificial/docs/设计文档/sys/task/定时任务核心业务.md） */
    public static final String ROLE_SYS_TASK_ADMIN = "SYS_TASK_ADMIN";
}

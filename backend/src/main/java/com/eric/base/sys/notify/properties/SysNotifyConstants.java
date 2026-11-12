package com.eric.base.sys.notify.properties;

/**
 * 通知待办模块常量
 *
 * @author Eric
 * @date 2026/8/12
 * @description 接口标识（resolver 鉴权使用）与角色编码，供 Controller / Resolver 静态引用。
 */
public class SysNotifyConstants {

    /** 发送待办（前端调用，仅 SYS_NOTIFY_ADMIN） */
    public static final String ACTION_SEND = "sys.notify.todo.send";
    /** 我的待办列表 */
    public static final String ACTION_MY_LIST = "sys.notify.todo.myList";
    /** 待办详情（点击详情触发，待阅自动转已办） */
    public static final String ACTION_LOAD = "sys.notify.todo.load";
    /** 转已办 */
    public static final String ACTION_DONE = "sys.notify.todo.done";
    /** 转未办 */
    public static final String ACTION_UNDONE = "sys.notify.todo.undone";
    /** 星标 */
    public static final String ACTION_STAR = "sys.notify.todo.star";
    /** 删除（软删除） */
    public static final String ACTION_DELETE = "sys.notify.todo.delete";
    /** 批量已办 */
    public static final String ACTION_BATCH_DONE = "sys.notify.todo.batchDone";
    /** 批量转未办 */
    public static final String ACTION_BATCH_UNDONE = "sys.notify.todo.batchUndone";
    /** 批量星标 */
    public static final String ACTION_BATCH_STAR = "sys.notify.todo.batchStar";
    /** 批量删除 */
    public static final String ACTION_BATCH_DELETE = "sys.notify.todo.batchDelete";
    /** 后台待办列表（仅 SYS_NOTIFY_ADMIN） */
    public static final String ACTION_ADMIN_LIST = "sys.notify.todo.adminList";
    /** 来源模块选项（读取 model:name:map，登录即可） */
    public static final String ACTION_MODEL_OPTIONS = "sys.notify.todo.modelOptions";

    /** SSE 订阅（登录即可） */
    public static final String ACTION_SSE_SUBSCRIBE = "sys.notify.todo.sseSubscribe";

    /** 待办管理员权限（见 artificial/docs/设计文档/sys/notify/通知待办核心业务.md） */
    public static final String ROLE_SYS_NOTIFY_ADMIN = "SYS_NOTIFY_ADMIN";
}

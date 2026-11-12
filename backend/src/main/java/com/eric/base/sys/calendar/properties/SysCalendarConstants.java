package com.eric.base.sys.calendar.properties;

/**
 * 日程模块常量
 *
 * @author Eric
 * @date 2026/8/21
 * @description 接口标识（resolver 鉴权使用）与角色编码，供 Controller / Resolver 静态引用。
 */
public class SysCalendarConstants {

    /** 新增日程 */
    public static final String ACTION_SAVE = "sys.calendar.save";
    /** 编辑日程 */
    public static final String ACTION_UPDATE = "sys.calendar.update";
    /** 删除日程 */
    public static final String ACTION_DELETE = "sys.calendar.delete";
    /** 查询日程详情 */
    public static final String ACTION_LOAD = "sys.calendar.load";
    /** 日程列表查询 */
    public static final String ACTION_LIST = "sys.calendar.list";
    /** 当月日程（日历视图） */
    public static final String ACTION_MONTH_LIST = "sys.calendar.monthList";
    /** 今日日程（顶部弹窗） */
    public static final String ACTION_TODAY_LIST = "sys.calendar.todayList";
    /** 日程接收/拒绝 */
    public static final String ACTION_ACCEPT = "sys.calendar.accept";

    /** 日程选择上级权限码 */
    public static final String ROLE_SYS_CALENDAR_SENDLEADER = "SYS_CALENDAR_SENDLEADER";
}
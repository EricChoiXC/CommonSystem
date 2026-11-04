package com.common.system.sys.log.properties;

/**
 * 日志模块常量
 *
 * @author Eric
 * @date 2026/8/8
 * @description 接口标识（resolver 鉴权使用）与角色编码，供 Controller / Resolver / 监听器静态引用。
 */
public class SysLogConstants {

    /** 后台管理：日志列表查询 */
    public static final String ACTION_LIST = "sys.log.operation.list";
    /** 后台管理：日志详情 */
    public static final String ACTION_LOAD = "sys.log.operation.load";

    /** 日志管理员权限（见 artificial/docs/设计文档/sys/log/日志核心业务.md） */
    public static final String ROLE_SYS_LOG_ADMIN = "SYS_LOG_ADMIN";
}

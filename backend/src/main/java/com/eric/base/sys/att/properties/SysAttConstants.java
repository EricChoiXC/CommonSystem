package com.eric.base.sys.att.properties;

/**
 * 附件模块常量
 *
 * @author Eric
 * @date 2026/8/7
 * @description 接口标识（resolver 鉴权使用）与角色编码，供 Controller / Resolver 静态引用。
 */
public class SysAttConstants {

    /** 上传 */
    public static final String ACTION_UPLOAD = "sys.att.main.upload";
    /** 下载（公共组件） */
    public static final String ACTION_DOWNLOAD = "sys.att.main.download";
    /** 预览（公共组件） */
    public static final String ACTION_VIEW = "sys.att.main.view";
    /** 删除（公共组件，上传者或权限管理员） */
    public static final String ACTION_DELETE = "sys.att.main.delete";
    /** 公共组件附件列表查询（登录即可，按 modelName/modelId/key 查询启用附件） */
    public static final String ACTION_COMPONENT_LIST = "sys.att.main.componentList";
    /** 后台管理：附件列表查询 */
    public static final String ACTION_LIST = "sys.att.main.list";
    /** 后台管理：附件详情 */
    public static final String ACTION_LOAD = "sys.att.main.load";
    /** 后台管理：删除附件 */
    public static final String ACTION_ADMIN_DELETE = "sys.att.main.adminDelete";
    /** 后台管理：下载附件 */
    public static final String ACTION_ADMIN_DOWNLOAD = "sys.att.main.adminDownload";
    /** 后台管理：文件列表查询 */
    public static final String ACTION_FILE_LIST = "sys.att.file.list";
    /** 后台管理：附件操作日志查询 */
    public static final String ACTION_OPERATION_LIST = "sys.att.operation.list";

    /** 附件模块后台管理权限（见 artificial/docs/设计文档/sys/attachment/附件核心业务.md） */
    public static final String ROLE_SYS_ATT_MAIN = "SYS_ATT_MAIN";
}

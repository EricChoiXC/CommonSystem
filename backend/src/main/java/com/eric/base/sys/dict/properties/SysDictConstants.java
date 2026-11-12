package com.eric.base.sys.dict.properties;

/**
 * 码值模块常量
 *
 * @author Eric
 * @date 2026/9/2
 * @description 接口标识（resolver 鉴权使用）与角色编码，供 Controller / Resolver 静态引用。
 */
public class SysDictConstants {

    /** 码值类型列表（登录即可） */
    public static final String ACTION_TYPE_LIST = "sys.dict.typeList";
    /** 码值项列表（登录即可） */
    public static final String ACTION_ITEM_LIST = "sys.dict.itemList";
    /** 新增码值项（仅 SYS_DICT_ADMIN） */
    public static final String ACTION_ITEM_SAVE = "sys.dict.itemSave";
    /** 编辑码值项（仅 SYS_DICT_ADMIN） */
    public static final String ACTION_ITEM_UPDATE = "sys.dict.itemUpdate";
    /** 删除码值项（仅 SYS_DICT_ADMIN） */
    public static final String ACTION_ITEM_DELETE = "sys.dict.itemDelete";
    /** 导出码值项（仅 SYS_DICT_ADMIN） */
    public static final String ACTION_ITEM_EXPORT = "sys.dict.itemExport";
    /** 导入码值项（仅 SYS_DICT_ADMIN） */
    public static final String ACTION_ITEM_IMPORT = "sys.dict.itemImport";
    /** 下载码值项导入模板（仅 SYS_DICT_ADMIN） */
    public static final String ACTION_ITEM_TEMPLATE = "sys.dict.itemTemplate";
    /** 通过类型编号获取码值项（登录即可，供前端和其他模块服务使用） */
    public static final String ACTION_ITEMS = "sys.dict.items";

    /** 码值管理员权限（见 artificial/docs/设计文档/sys/dict/码值功能核心业务.md） */
    public static final String ROLE_SYS_DICT_ADMIN = "SYS_DICT_ADMIN";
}

package com.common.system.sys.i18n.properties;

/**
 * 多语言模块常量
 *
 * @author Eric
 * @date 2026/11/5
 * @description 多语言模块接口标识（ACTION_*，resolver 鉴权使用）与角色编码，
 *              供 Controller / Resolver 静态引用，禁止手写字符串。
 *              鉴权矩阵与接口清单见 docs/设计文档/sys/i18n/多语言管理功能分析报告.md「六」：
 *              language/list 与 /locales/{code}.json 免鉴权，model/list 与 user/language/update
 *              登录即可，其余维护动作仅 SYS_I18N_ADMIN（SYS_ADMIN 由 BaseResolver 自动放行）。
 */
public class SysI18nProperties {

    /* ==================== 语言管理（/backend/sys/i18n/language/*） ==================== */

    /** 语言列表（免鉴权：登录页语言下拉需要，Resolver 走免鉴权分支） */
    public static final String ACTION_LANGUAGE_LIST = "sys.i18n.language.list";
    /** 新增语言（仅 SYS_I18N_ADMIN） */
    public static final String ACTION_LANGUAGE_SAVE = "sys.i18n.language.save";
    /** 编辑语言（仅 SYS_I18N_ADMIN） */
    public static final String ACTION_LANGUAGE_UPDATE = "sys.i18n.language.update";
    /** 删除语言（仅 SYS_I18N_ADMIN；级联见 D4：删翻译 + 用户语言登记改绑默认语言） */
    public static final String ACTION_LANGUAGE_DELETE = "sys.i18n.language.delete";
    /** 设默认语言（仅 SYS_I18N_ADMIN；互斥切换：先清旧默认再设新默认） */
    public static final String ACTION_LANGUAGE_SET_DEFAULT = "sys.i18n.language.setDefault";

    /* ==================== 模块聚合（/backend/sys/i18n/model/*） ==================== */

    /** 翻译维护模块聚合列表（登录即可） */
    public static final String ACTION_MODEL_LIST = "sys.i18n.model.list";

    /* ==================== 翻译维护（/backend/sys/i18n/translation/*） ==================== */

    /** 翻译矩阵列表（仅 SYS_I18N_ADMIN；body 含 fieldModel） */
    public static final String ACTION_TRANSLATION_LIST = "sys.i18n.translation.list";
    /** 翻译批量更新（仅 SYS_I18N_ADMIN；写 field_update_time 人工编辑标记后刷新缓存） */
    public static final String ACTION_TRANSLATION_UPDATE = "sys.i18n.translation.update";
    /** 翻译重置（仅 SYS_I18N_ADMIN；key + language，还原为资源文件值） */
    public static final String ACTION_TRANSLATION_RESET = "sys.i18n.translation.reset";
    /** 翻译导出（仅 SYS_I18N_ADMIN；EasyExcel 文件流） */
    public static final String ACTION_TRANSLATION_EXPORT = "sys.i18n.translation.export";
    /** 翻译导入（仅 SYS_I18N_ADMIN；multipart 覆盖导入，越界项跳过） */
    public static final String ACTION_TRANSLATION_IMPORT = "sys.i18n.translation.import";

    /* ==================== 部署（/backend/sys/i18n/deploy/* 与 /locales/*） ==================== */

    /** 部署动作（仅 SYS_I18N_ADMIN；资源 → DB 同步 + 刷新缓存，同启动同步规则） */
    public static final String ACTION_DEPLOY_RUN = "sys.i18n.deploy.run";
    /**
     * 前端语言包输出（免鉴权：vue-i18n 动态加载需要，Resolver 走免鉴权分支）。
     * 注意：本 action 仅为语义标识，Controller 直接返回 JSON 不经 authorize(...)
     * （与 /init、/initID 同类入口，路径在 /locales/** 下，不带 /backend 前缀）。
     */
    public static final String ACTION_LOCALES = "sys.i18n.locales";

    /* ==================== 用户语言（/backend/sys/i18n/user/*） ==================== */

    /** 当前登录用户语言更新（登录即可；body 仅 fieldLanguage） */
    public static final String ACTION_USER_LANGUAGE_UPDATE = "sys.i18n.user.languageUpdate";

    /* ==================== 角色编码 ==================== */

    /** 多语言管理员权限（sys_org_role.field_code，随 properties/sys/i18n/roles.yml 同步入库） */
    public static final String ROLE_SYS_I18N_ADMIN = "SYS_I18N_ADMIN";
}

package com.common.system.common.setting.properties;

/**
 * 公共配置属性常量
 *
 * @author Eric
 * @date 2026/8/25
 * @description 定义公共配置项的类型与键值常量，
 *              对应 resources/properties/common/application.yml 中 SETTING.system 下的配置项。
 */
public class CommonSettingProperties {

    /** 公共配置类型：系统配置 */
    public static final String TYPE_SYSTEM = "system";

    /** 是否允许邮箱登录 */
    public static final String KEY_LOGIN_EMAIL_ENABLE = "login_email_enable";
    /** 是否允许电话登录 */
    public static final String KEY_LOGIN_PHONE_ENABLE = "login_phone_enable";
    /** 定期更换密码周期（天），0 表示不强制 */
    public static final String KEY_PWD_CHANGE_CYCLE = "pwd_change_cycle";
    /** 不可重复设定密码次数，0 表示不限制 */
    public static final String KEY_PWD_REPEAT_LIMIT = "pwd_repeat_limit";
}
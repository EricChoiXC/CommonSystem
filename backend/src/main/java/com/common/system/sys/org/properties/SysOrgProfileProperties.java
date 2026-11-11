package com.common.system.sys.org.properties;

/**
 * 个人资料页接口常量
 *
 * @author Eric
 * @date 2026/11/11
 * @description 个人资料页（本人自助）维护接口的接口标识与头像附件绑定约定常量，依据
 *              docs/v1_2-用户管理中心/个人资料页与第三方账号绑定/个人资料页与第三方账号绑定开发方案.md。
 *              <p>本人自助接口组不设权限码、不进 roles.yml（方案 5.6）：所有 action 一律
 *              「登录且启用」放行（见 com.common.system.sys.org.resolver.ProfileResolver），
 *              系统管理员 SYS_ADMIN 由 BaseResolver 自动放行，分支无需单独列举；
 *              接口不收 userId 参数，越权面以会话定位消除。</p>
 */
public class SysOrgProfileProperties {

    public static final String ACTION_LOAD = "sys.org.profile.load";
    public static final String ACTION_SAVE = "sys.org.profile.save";
    public static final String ACTION_BIND = "sys.org.profile.bind";
    public static final String ACTION_UNBIND = "sys.org.profile.unbind";

    /**
     * 头像附件绑定约定（方案 4.3）：sys_att_main.field_model_name 取值。
     * 头像附件统一挂接在人员模型上，load 按 model_name + model_id + key 降序取最新一条。
     */
    public static final String ATT_MODEL_NAME = "sys_org_user";

    /**
     * 头像附件绑定约定（方案 4.3）：sys_att_main.field_key 取值，
     * 与 ATT_MODEL_NAME 组合定位「人员头像」这一类附件。
     */
    public static final String ATT_KEY_PROFILE = "profile";
}

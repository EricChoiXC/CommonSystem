package com.common.system.sys.org.model;

import lombok.Data;

/**
 * 人员第三方账号绑定信息文档对象
 *
 * @author Eric
 * @date 2026/11/11
 * @description 对应 sys_org_user.field_openid（JSON 字符串）的结构化映射，由 Jackson 序列化/反序列化。
 *              外层结构 { "wechat": {...}, "qq": {...} }，格式依据
 *              docs/v1_2-用户管理中心/个人资料页与第三方账号绑定/个人资料页与第三方账号绑定开发方案.md 第 4.1 节：
 *              wechat 内层为 openid/unionid/nickname/bindTime，qq 内层为 openid/nickname/bindTime，
 *              bindTime 为 "yyyy-MM-dd HH:mm:ss" 格式字符串。
 *              渠道未绑定时对应内层对象为 null；openid 全局唯一，绑定前须查重（SysOrgUserMapper.countDuplicateOpenid）。
 */
@Data
public class SysOrgUserOpenidDo {

    /** 微信绑定信息 */
    private Wechat wechat;

    /** QQ 绑定信息 */
    private Qq qq;

    /** 微信绑定信息 */
    @Data
    public static class Wechat {

        /** 微信 openid */
        private String openid;

        /** 微信 unionid（开放平台 unionid，可能为空） */
        private String unionid;

        /** 微信昵称 */
        private String nickname;

        /** 绑定时间（yyyy-MM-dd HH:mm:ss 格式字符串） */
        private String bindTime;
    }

    /** QQ 绑定信息 */
    @Data
    public static class Qq {

        /** QQ openid */
        private String openid;

        /** QQ 昵称 */
        private String nickname;

        /** 绑定时间（yyyy-MM-dd HH:mm:ss 格式字符串） */
        private String bindTime;
    }
}

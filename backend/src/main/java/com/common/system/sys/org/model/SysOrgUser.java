package com.common.system.sys.org.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

/**
 * @author Eric
 * @date 2026/7/31
 * @description
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysOrgUser extends SysOrgElement {

    /** 联系电话 */
    private String fieldPhone;

    /** 邮箱 */
    private String fieldEmail;

    /** 地址 */
    private String fieldAddress;

    /** 性别 */
    private int fieldGender;

    /** 生日 */
    private Date fieldBirthday;

    /** 登录名 */
    private String fieldLoginName;

    /** 密码：仅允许写入（WRITE_ONLY），任何响应体都不输出密码字段 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String fieldPassword;

    /** 在职状态 */
    private int fieldJobStatus;

    /** 账号状态 */
    private int fieldAccountStatus;

    /** 最后登录时间 */
    private Date fieldLastLoginTime;

    /** 最后修改密码时间 */
    private Date fieldLastChgPwdTime;

    /** 角色 */
    private List<SysOrgRole> roles;
    /** 权限Code */
    private List<String> auths;
    /** 岗位 */
    private List<SysOrgPost> posts;
    /** 虚拟组织 */
    private List<SysOrgElement> virtuals;

}

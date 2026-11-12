package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 密码历史记录
 *
 * @author Eric
 * @date 2026/8/25
 * @description 对应 sys_org_pwd_history；记录用户最近n次密码哈希，
 *              用于不可重复设定密码次数校验。每次用户通过 changePwd 更新密码时，
 *              将新密码哈希插入本表，并清理超出保留条数的历史记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysOrgUserPwdHistory extends BaseModel {

    /** 用户ID */
    private String fieldUserId;

    /** 历史密码（BCrypt 哈希） */
    private String fieldPwd;

    /** 密码版本 */
    private int fieldVersion;
}
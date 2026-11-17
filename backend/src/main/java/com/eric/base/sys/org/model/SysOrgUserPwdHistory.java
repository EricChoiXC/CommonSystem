package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
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
 *              <p>2026/11/17 F19-DB-001 存量标注批一：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_org_pwd_history", comment = "密码历史表",
        indexes = {
                @TableIndex(name = "idx_pwd_history_user", columns = "field_user_id")})
public class SysOrgUserPwdHistory extends BaseModel {

    /** 用户ID */
    @ColumnField(name = "field_user_id", comment = "用户ID", type = ColumnType.STRING, length = 32)
    private String fieldUserId;

    /** 历史密码（BCrypt 哈希） */
    @ColumnField(name = "field_pwd", comment = "历史密码", type = ColumnType.STRING, length = 255)
    private String fieldPwd;

    /** 密码版本 */
    @ColumnField(name = "field_version", comment = "密码版本", type = ColumnType.INT, defaultValue = "1")
    private int fieldVersion;
}

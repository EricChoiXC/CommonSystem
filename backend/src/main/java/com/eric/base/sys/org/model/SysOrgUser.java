package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

/**
 * @author Eric
 * @date 2026/7/31
 * @description 人员扩展表(sys_org_user)实体：与 sys_org_element 主表按 field_id 一对一
 *              同值关联（主表 field_org_type = 3），主档字段（名称/编码/层级/审计字段）
 *              存储在主表，查询统一 JOIN 组装、写入经 SysOrgUserDao 双表写。
 *              <p>2026/11/17 F19-DB-001 存量标注批一继承对齐（裁决 A：继承对齐表结构）：
 *              原继承 SysOrgElement 与表结构不符（本表仅存扩展列，无审计列、无
 *              field_name / field_code 列），改继承 BaseModel 对齐公共列模板；12 个自有
 *              扩展列 @ColumnField 标注，列定义与 dev 库 information_schema 逐列一致；
 *              主表侧展示属性（fieldName / fieldOrgType / 审计字段等）以不带 @ColumnField
 *              的普通兼容字段保留，供 JOIN 结果映射与业务代码使用，扫描器忽略不入列清单。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_org_user", comment = "人员扩展表",
        indexes = {
                @TableIndex(name = "uk_org_user_login_name", columns = "field_login_name", unique = true)})
public class SysOrgUser extends BaseModel {

    /** 兼容字段：主表(sys_org_element)侧名称，JOIN 组装用，不入本表列清单 */
    private String fieldName;

    /** 兼容字段：主表编码 */
    private String fieldCode;

    /** 兼容字段：主表创建者ID */
    private String fieldCreatorId;

    /** 兼容字段：主表创建时间 */
    private Date fieldCreateTime;

    /** 兼容字段：主表修改者ID */
    private String fieldUpdaterId;

    /** 兼容字段：主表修改时间 */
    private Date fieldUpdateTime;

    /** 兼容字段：主表是否启用 */
    private Boolean fieldEnable;

    /** 兼容字段：主表父级组织对象（展示字段） */
    private SysOrgOrg fieldParent;

    /** 兼容字段：主表父级组织ID */
    private String fieldParentId;

    /** 兼容字段：主表父级组织名称（展示字段） */
    private String fieldParentName;

    /** 兼容字段：主表层级路径 */
    private String fieldHierarchy;

    /** 兼容字段：主表层级 */
    private int fieldLevel;

    /** 兼容字段：主表组织类型 */
    private int fieldOrgType;

    /** 兼容字段：主表是否虚拟组织 */
    private boolean fieldIsVirtual;

    /** 兼容字段：主表名称拼音全称 */
    private String fieldPinyin;

    /** 兼容字段：主表名称拼音简称 */
    private String fieldPinyinShort;

    /** 联系电话 */
    @ColumnField(name = "field_phone", comment = "联系电话", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldPhone;

    /** 邮箱 */
    @ColumnField(name = "field_email", comment = "联系邮箱", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldEmail;

    /** 地址 */
    @ColumnField(name = "field_address", comment = "联系地址", type = ColumnType.STRING, length = 500, nullable = true)
    private String fieldAddress;

    /** 性别 */
    @ColumnField(name = "field_gender", comment = "性别: 0=其他 1=男 2=女", type = ColumnType.INT, defaultValue = "0")
    private int fieldGender;

    /** 生日 */
    @ColumnField(name = "field_birthday", comment = "生日", type = ColumnType.DATE, nullable = true)
    private Date fieldBirthday;

    /** 登录名 */
    @ColumnField(name = "field_login_name", comment = "登录名", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldLoginName;

    /** 密码：仅允许写入（WRITE_ONLY），任何响应体都不输出密码字段 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ColumnField(name = "field_password", comment = "密码", type = ColumnType.STRING, length = 255, nullable = true)
    private String fieldPassword;

    /** 在职状态 */
    @ColumnField(name = "field_job_status", comment = "在职状态: 1=在职 0=离职 2=停职 3=休假",
            type = ColumnType.INT, defaultValue = "1")
    private int fieldJobStatus;

    /** 账号状态 */
    @ColumnField(name = "field_account_status", comment = "账号状态: 1=正常 2=锁定 3=密码过期 0=禁用 4=注销",
            type = ColumnType.INT, defaultValue = "1")
    private int fieldAccountStatus;

    /** 最后登录时间 */
    @ColumnField(name = "field_last_login_time", comment = "最后登录时间", type = ColumnType.TIMESTAMP, nullable = true)
    private Date fieldLastLoginTime;

    /** 最后修改密码时间 */
    @ColumnField(name = "field_last_chg_pwd_time", comment = "最后修改密码时间", type = ColumnType.TIMESTAMP, nullable = true)
    private Date fieldLastChgPwdTime;

    /** 第三方账号绑定信息(JSON字符串，结构见 SysOrgUserOpenidDo) */
    @ColumnField(name = "field_openid", comment = "第三方账号绑定信息(JSON)", type = ColumnType.JSON, nullable = true)
    private String fieldOpenid;

    /** 角色 */
    private List<SysOrgRole> roles;
    /** 权限Code */
    private List<String> auths;
    /** 岗位 */
    private List<SysOrgPost> posts;
    /** 虚拟组织 */
    private List<SysOrgElement> virtuals;

}

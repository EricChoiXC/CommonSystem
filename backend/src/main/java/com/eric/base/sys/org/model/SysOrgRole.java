package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.MainModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;

import java.util.List;

/**
 * @author Eric
 * @date 2026/8/3
 * @description 角色表(sys_org_role)实体：与 sys_org_element 主表按 field_id 一对一同值
 *              关联（主表 field_org_type = 5 镜像行），角色与用户/权限的关联由
 *              sys_org_role_element / sys_org_role_auth 关系表承载。
 *              <p>2026/11/17 F19-DB-001 存量标注批一：类级 @TableSource + 业务列
 *              @ColumnField，其中 field_name / field_code 为重声明遮蔽 MainModel 继承列
 *              （D5 规则，Java 类型与 MainModel 声明一致），列定义与 dev 库
 *              information_schema 逐列一致；users / orgs / auths 为不入库的关联展示
 *              字段，不标注；公共列（field_id 主键 + 审计五列）由引擎公共列模板内建，
 *              无需标注。</p>
 */
@Data
@TableSource(name = "sys_org_role", comment = "角色表")
public class SysOrgRole extends MainModel {

    /** 重声明：角色名称（D5 遮蔽 MainModel.fieldName） */
    @ColumnField(name = "field_name", comment = "角色名称", type = ColumnType.STRING, length = 200)
    private String fieldName;

    /** 重声明：角色编码（D5 遮蔽 MainModel.fieldCode） */
    @ColumnField(name = "field_code", comment = "角色编码", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldCode;

    private List<SysOrgUser> users;

    private List<SysOrgOrg> orgs;

    private List<SysOrgAuth> auths;
}

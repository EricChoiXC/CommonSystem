package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author Eric
 * @date 2026/8/5
 * @description 权限表(sys_org_auth)实体：权限码由 roles.yml 增量同步到本表
 *              （SysOrgAuthService），角色与权限的关联由 sys_org_role_auth 关系表承载。
 *              <p>2026/11/17 F19-DB-001 存量标注批一：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField（field_name / field_code 为本表自有列，直接标注），
 *              列定义与 dev 库 information_schema 逐列一致；roles 为不入库的关联展示
 *              字段，不标注；公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_org_auth", comment = "权限表",
        indexes = {
                @TableIndex(name = "uk_org_auth_code", columns = "field_code", unique = true)})
public class SysOrgAuth extends BaseModel {

    /** 权限名称 */
    @ColumnField(name = "field_name", comment = "权限名称", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldName;

    /** 权限编码 */
    @ColumnField(name = "field_code", comment = "权限编码", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldCode;

    /** 所属模块 */
    @ColumnField(name = "field_model", comment = "所属模块", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldModel;

    /** 权限描述 */
    @ColumnField(name = "field_description", comment = "权限描述", type = ColumnType.STRING, length = 500, nullable = true)
    private String fieldDescription;

    private List<SysOrgRole> roles;
}

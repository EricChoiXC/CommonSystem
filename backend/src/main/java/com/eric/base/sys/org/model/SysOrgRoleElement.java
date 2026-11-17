package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色-组织元素关联实体
 *
 * @author Eric
 * @date 2026/11/17
 * @description 角色-组织元素关联表(sys_org_role_element)实体：复合主键 (field_element_id, field_role_id)，
 *              无 field_id 列，不映射 BaseModel 主键、无公共字段（与 sys_org_group_user
 *              关联表形态一致）。最小实体仅注解纳管表结构（F19-DB-004），不映射 Dao 读写路径；
 *              角色的可见范围组织元素由 SysOrgRoleService 维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_org_role_element", comment = "角色-组织元素关联表", primaryKeyColumns = {"field_element_id", "field_role_id"},
        indexes = {
                @TableIndex(name = "idx_role_element_role", columns = "field_role_id")})
public class SysOrgRoleElement extends BaseModel {

    /** 组织元素ID（sys_org_element.field_id，复合主键之一） */
    @ColumnField(name = "field_element_id", comment = "组织元素ID", type = ColumnType.STRING, length = 32)
    private String fieldElementId;

    /** 角色ID（sys_org_role.field_id，复合主键之一） */
    @ColumnField(name = "field_role_id", comment = "角色ID", type = ColumnType.STRING, length = 32)
    private String fieldRoleId;
}

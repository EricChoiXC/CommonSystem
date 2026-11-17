package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 机构管理员关联实体
 *
 * @author Eric
 * @date 2026/11/17
 * @description 机构管理员表(sys_org_org_manager)实体：复合主键 (field_org_id, field_user_id)，
 *              无 field_id 列，不映射 BaseModel 主键、无公共字段（与 sys_org_group_user
 *              关联表形态一致，但含可空的 field_description 业务列）。最小实体仅注解纳管
 *              表结构（F19-DB-004），不映射 Dao 读写路径；机构管理员指派由
 *              SysOrgElementService 维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_org_org_manager", comment = "机构管理员表", primaryKeyColumns = {"field_org_id", "field_user_id"},
        indexes = {
                @TableIndex(name = "idx_org_manager_user", columns = "field_user_id")})
public class SysOrgOrgManager extends BaseModel {

    /** 机构ID（sys_org_element.field_id，field_org_type = 1，复合主键之一） */
    @ColumnField(name = "field_org_id", comment = "机构ID", type = ColumnType.STRING, length = 32)
    private String fieldOrgId;

    /** 管理员用户ID（sys_org_element.field_id，field_org_type = 3，复合主键之一） */
    @ColumnField(name = "field_user_id", comment = "管理员用户ID", type = ColumnType.STRING, length = 32)
    private String fieldUserId;

    /** 描述 */
    @ColumnField(name = "field_description", comment = "描述", type = ColumnType.STRING, length = 500, nullable = true)
    private String fieldDescription;
}

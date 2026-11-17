package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 虚拟组织-用户关联实体
 *
 * @author Eric
 * @date 2026/11/17
 * @description 虚拟组织-用户关联表(sys_org_virtual_user)实体：复合主键 (field_virtual_id, field_user_id)，
 *              无 field_id 列，不映射 BaseModel 主键、无公共字段（与 sys_org_group_user
 *              关联表形态一致）。最小实体仅注解纳管表结构（F19-DB-004），不映射 Dao 读写路径；
 *              虚拟组织节点与用户的挂载关系由 SysOrgElementService 维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_org_virtual_user", comment = "虚拟组织-用户关联表", primaryKeyColumns = {"field_virtual_id", "field_user_id"},
        indexes = {
                @TableIndex(name = "idx_virtual_user_user", columns = "field_user_id")})
public class SysOrgVirtualUser extends BaseModel {

    /** 虚拟组织元素ID（sys_org_element.field_id，复合主键之一） */
    @ColumnField(name = "field_virtual_id", comment = "虚拟组织元素ID", type = ColumnType.STRING, length = 32)
    private String fieldVirtualId;

    /** 用户ID（sys_org_element.field_id，field_org_type = 3，复合主键之一） */
    @ColumnField(name = "field_user_id", comment = "用户ID", type = ColumnType.STRING, length = 32)
    private String fieldUserId;
}

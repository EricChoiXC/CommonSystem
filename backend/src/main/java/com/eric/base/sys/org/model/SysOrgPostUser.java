package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岗位-人员关联实体
 *
 * @author Eric
 * @date 2026/11/17
 * @description 岗位-人员关联表(sys_org_post_user)实体：复合主键 (field_user_id, field_post_id)，
 *              无 field_id 列，不映射 BaseModel 主键、无公共字段（与 sys_org_group_user
 *              关联表形态一致）。最小实体仅注解纳管表结构（F19-DB-004），不映射 Dao 读写路径；
 *              人员与岗位的兼任关系由 SysOrgElementService 维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_org_post_user", comment = "岗位-人员关联表", primaryKeyColumns = {"field_user_id", "field_post_id"},
        indexes = {
                @TableIndex(name = "idx_post_user_post", columns = "field_post_id")})
public class SysOrgPostUser extends BaseModel {

    /** 人员ID（sys_org_element.field_id，field_org_type = 3，复合主键之一） */
    @ColumnField(name = "field_user_id", comment = "人员ID", type = ColumnType.STRING, length = 32)
    private String fieldUserId;

    /** 岗位ID（sys_org_element.field_id，field_org_type = 4，复合主键之一） */
    @ColumnField(name = "field_post_id", comment = "岗位ID", type = ColumnType.STRING, length = 32)
    private String fieldPostId;
}

package com.common.system.sys.org.model;

import com.common.system.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 群组成员明细
 *
 * @author Eric
 * @date 2026/11/4
 * @description 群组-成员关联表(sys_org_group_user)实体：复合主键 (field_group_id, field_user_id)，
 *              无 field_id 列，不映射 BaseModel 主键、无公共字段（与 sys_org_role_element /
 *              sys_org_post_user 关联表形态一致，列清单以 V1_01_039 迁移脚本为准）。
 *              field_user_id 对应人员节点 sys_org_element.field_id (field_org_type = 3)；
 *              保存时全量替换（先删后插），由 SysOrgGroupService 统一维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysOrgGroupUser extends BaseModel {

    /** 群组ID（sys_org_group.field_id，复合主键之一） */
    private String fieldGroupId;

    /** 人员ID（sys_org_element.field_id，field_org_type = 3，复合主键之一） */
    private String fieldUserId;
}

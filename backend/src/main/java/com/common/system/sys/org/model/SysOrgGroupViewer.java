package com.common.system.sys.org.model;

import com.common.system.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 群组可见范围明细
 *
 * @author Eric
 * @date 2026/11/4
 * @description 群组-可见范围关联表(sys_org_group_viewer)实体：复合主键 (field_group_id, field_viewer_id)，
 *              无 field_id 列，不映射 BaseModel 主键、无公共字段（与 sys_org_role_element /
 *              sys_org_post_user 关联表形态一致，列清单以 V1_01_039 迁移脚本为准）。
 *              field_viewer_id 对应可见范围组织架构节点 sys_org_element.field_id，
 *              限机构(1) / 部门(2) / 岗位(4) / 人员(3) 四类；仅 COMMON 类型群组写入明细，
 *              明细为空即全员可见（field_is_all_viewer 派生，02 文档 1.3/D4）；
 *              保存时全量替换（先删后插），由 SysOrgGroupService 统一维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysOrgGroupViewer extends BaseModel {

    /** 群组ID（sys_org_group.field_id，复合主键之一） */
    private String fieldGroupId;

    /** 可见范围组织架构节点ID（sys_org_element.field_id，field_org_type IN (1,2,3,4)，复合主键之一） */
    private String fieldViewerId;
}

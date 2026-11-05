package com.common.system.sys.org.model;

import com.common.system.common.base.model.MainModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 群组
 *
 * @author Eric
 * @date 2026/11/4
 * @description 群组扩展表(sys_org_group)实体：与 sys_org_element 主表按 field_id 一对一同值关联
 *              (field_org_type = 6)，形态与 sys_org_org / sys_org_dept / sys_org_post 一致；
 *              公共字段（fieldName / fieldCreateTime / fieldEnable 等）存储在主表并继承自 MainModel。
 *              群组不参与组织架构树：对应主表行 field_parent_id / field_hierarchy 为 NULL、field_level = 1。
 *              field_type 取 SysOrgGroupProperties.GROUP_TYPE_COMMON / GROUP_TYPE_PERSONAL；
 *              field_is_all_viewer 为派生冗余字段（可见范围明细为空即为全员可见），
 *              由 SysOrgGroupService 在保存/更新明细后统一计算写入，不接受前端写入（02 文档 1.3/D4）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysOrgGroup extends MainModel {

    /** 群组类型：COMMON=公共 / PERSONAL=个人（见 SysOrgGroupProperties.GROUP_TYPE_* 常量） */
    private String fieldType;

    /** 所有人ID（PERSONAL=归属人；COMMON=创建人，用于审计与列表「所有人」列展示） */
    private String fieldOwnerId;

    /** 是否全员可见（派生冗余：sys_org_group_viewer 明细为空即 true，由 Service 计算，前端传入无效） */
    private Boolean fieldIsAllViewer;
}

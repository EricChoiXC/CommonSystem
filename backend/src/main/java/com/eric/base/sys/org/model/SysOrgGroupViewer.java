package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 群组可见范围明细
 *
 * @author Eric
 * @date 2026/11/4
 * @description 群组-可见范围关联表(sys_org_group_viewer)实体：复合主键 (field_group_id, field_viewer_id)，
 *              无 field_id 列（与 sys_org_role_element / sys_org_post_user 关联表形态一致）。
 *              <p>2026/11/17 F19-DB-004 存量标注批四：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField 注解纳管（primaryKeyColumns 显式声明复合主键，引擎不再
 *              内建 field_id），列定义与 dev 库 information_schema 逐列一致；该表无公共列
 *              模板内建列。</p>
 *              field_viewer_id 对应可见范围组织架构节点 sys_org_element.field_id，
 *              限机构(1) / 部门(2) / 岗位(4) / 人员(3) 四类；仅 COMMON 类型群组写入明细，
 *              明细为空即全员可见（field_is_all_viewer 派生，02 文档 1.3/D4）；
 *              保存时全量替换（先删后插），由 SysOrgGroupService 统一维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_org_group_viewer", comment = "群组-可见范围关联表", primaryKeyColumns = {"field_group_id", "field_viewer_id"},
        indexes = {
                @TableIndex(name = "idx_org_group_viewer_viewer", columns = "field_viewer_id")})
public class SysOrgGroupViewer extends BaseModel {

    /** 群组ID（sys_org_group.field_id，复合主键之一） */
    @ColumnField(name = "field_group_id", comment = "群组ID", type = ColumnType.STRING, length = 32)
    private String fieldGroupId;

    /** 可见范围组织架构节点ID（sys_org_element.field_id，field_org_type IN (1,2,3,4)，复合主键之一） */
    @ColumnField(name = "field_viewer_id", comment = "可见范围组织架构ID(sys_org_element.field_id, field_org_type IN (1,2,3,4))", type = ColumnType.STRING, length = 32)
    private String fieldViewerId;
}

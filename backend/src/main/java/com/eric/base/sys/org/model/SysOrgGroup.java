package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

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
 *              <p>2026/11/17 F19-DB-001 存量标注批一继承对齐（裁决 A：继承对齐表结构）：
 *              原继承 MainModel 与表结构不符（本表无审计列、无 field_name / field_code 列），
 *              改继承 BaseModel 对齐公共列模板；3 个自有业务列 @ColumnField 标注，列定义与
 *              dev 库 information_schema 逐列一致；主表侧公共属性（fieldName / 审计字段等）
 *              以不带 @ColumnField 的普通兼容字段保留，供 JOIN 结果映射与业务代码使用，
 *              扫描器忽略不入列清单。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_org_group", comment = "群组扩展表",
        indexes = {
                @TableIndex(name = "idx_org_group_owner", columns = "field_owner_id"),
                @TableIndex(name = "idx_org_group_type", columns = "field_type")})
public class SysOrgGroup extends BaseModel {

    /** 兼容字段：主表(sys_org_element)侧名称，JOIN 组装用，不入本表列清单 */
    private String fieldName;

    /** 兼容字段：主表编码 */
    private String fieldCode;

    /** 兼容字段：主表创建者ID */
    private String fieldCreatorId;

    /** 兼容字段：主表创建时间 */
    private Date fieldCreateTime;

    /** 兼容字段：主表修改者ID */
    private String fieldUpdaterId;

    /** 兼容字段：主表修改时间 */
    private Date fieldUpdateTime;

    /** 兼容字段：主表是否启用 */
    private Boolean fieldEnable;

    /** 群组类型：COMMON=公共 / PERSONAL=个人（见 SysOrgGroupProperties.GROUP_TYPE_* 常量） */
    @ColumnField(name = "field_type", comment = "群组类型: COMMON=公共 PERSONAL=个人",
            type = ColumnType.STRING, length = 20, defaultValue = "PERSONAL")
    private String fieldType;

    /** 所有人ID（PERSONAL=归属人；COMMON=创建人，用于审计与列表「所有人」列展示） */
    @ColumnField(name = "field_owner_id", comment = "所有人ID(PERSONAL=归属人; COMMON=创建人)",
            type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldOwnerId;

    /** 是否全员可见（派生冗余：sys_org_group_viewer 明细为空即 true，由 Service 计算，前端传入无效） */
    @ColumnField(name = "field_is_all_viewer", comment = "是否全员可见(由可见范围明细派生: 明细为空则为1)",
            type = ColumnType.BOOLEAN, defaultValue = "b'1'")
    private Boolean fieldIsAllViewer;
}

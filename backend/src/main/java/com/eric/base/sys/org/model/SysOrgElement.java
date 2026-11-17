package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.MainModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;

/**
 * @author Eric
 * @date 2026/8/5
 * @description 组织架构元素主表(sys_org_element)实体：机构/部门/人员/岗位/角色/群组共用的
 *              主档行（field_org_type 区分），扩展信息存于同 ID 扩展表
 *              （sys_org_org / sys_org_dept / sys_org_user / sys_org_post 等）。
 *              <p>2026/11/17 F19-DB-001 存量标注批一：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，其中 field_name / field_code 为重声明遮蔽 MainModel
 *              继承列（D5 规则，Java 类型与 MainModel 声明一致），列定义与 dev 库
 *              information_schema 逐列一致；fieldParent / fieldParentName 为不入库的
 *              展示字段，不标注；公共列（field_id 主键 + 审计五列）由引擎公共列模板
 *              内建，无需标注。</p>
 */
@Data
@TableSource(name = "sys_org_element", comment = "组织架构元素主表",
        indexes = {
                @TableIndex(name = "idx_org_element_hierarchy", columns = "field_hierarchy"),
                @TableIndex(name = "idx_org_element_parent", columns = "field_parent_id"),
                @TableIndex(name = "idx_org_element_type", columns = "field_org_type")})
public class SysOrgElement extends MainModel {

    /** 重声明：名称（D5 遮蔽 MainModel.fieldName） */
    @ColumnField(name = "field_name", comment = "名称", type = ColumnType.STRING, length = 200)
    private String fieldName;

    /** 名称拼音全称(小写,后端按 field_name 自动生成) */
    @ColumnField(name = "field_pinyin", comment = "名称拼音全称(小写,后端按 field_name 自动生成)",
            type = ColumnType.STRING, length = 1000, nullable = true)
    private String fieldPinyin;

    /** 名称拼音简称(首字母小写,后端按 field_name 自动生成) */
    @ColumnField(name = "field_pinyin_short", comment = "名称拼音简称(首字母小写,后端按 field_name 自动生成)",
            type = ColumnType.STRING, length = 300, nullable = true)
    private String fieldPinyinShort;

    /** 重声明：编码（D5 遮蔽 MainModel.fieldCode） */
    @ColumnField(name = "field_code", comment = "编码", type = ColumnType.STRING, length = 50)
    private String fieldCode;

    /** 组织类型 */
    @ColumnField(name = "field_org_type", comment = "组织类型: 1=机构 2=部门 3=人员 4=岗位", type = ColumnType.INT)
    private int fieldOrgType;

    /** 父级组织ID */
    @ColumnField(name = "field_parent_id", comment = "父级组织元素ID",
            type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldParentId;

    /** 组织层级id */
    @ColumnField(name = "field_hierarchy", comment = "组织层级路径(如 /id1/id2/)",
            type = ColumnType.STRING, length = 500, nullable = true)
    private String fieldHierarchy;

    /** 组织层级 */
    @ColumnField(name = "field_level", comment = "组织层级", type = ColumnType.INT, defaultValue = "1")
    private int fieldLevel;

    /** 是否虚拟组织 */
    @ColumnField(name = "field_is_virtual", comment = "是否虚拟组织", type = ColumnType.BOOLEAN, defaultValue = "b'0'")
    private boolean fieldIsVirtual;

    /** 父级组织（展示字段，不入库） */
    private SysOrgOrg fieldParent;

    /** 父级组织名称（展示字段，不入库） */
    private String fieldParentName;
}

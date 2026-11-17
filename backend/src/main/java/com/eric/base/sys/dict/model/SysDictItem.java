package com.eric.base.sys.dict.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 码值项模型
 *
 * @author Eric
 * @date 2026/9/2
 * @description 对应 sys_dict_item；通过 field_type_id 关联 sys_dict_type（一对多）。
 *              field_system = true 的系统码值不可维护（启动时由 dict.yml 增量导入）；
 *              人工新增的码值固定 field_system = false。
 *              列清单与 artificial/docs/设计文档/sys/dict/码值功能核心业务.md 保持一致。
 *              <p>2026/11/17 F19-DB-002 存量标注批二继承对齐（裁决 A：继承对齐表结构）：
 *              原继承 MainModel 与表结构不符（本表仅含 field_enable 一个审计列，无
 *              field_code / field_creator_id / field_create_time / field_updater_id /
 *              field_update_time 列），改继承 BaseModel 对齐公共列模板；field_name /
 *              field_enable 重声明为业务列 @ColumnField 标注（重声明字段 Java 类型与
 *              MainModel 原声明一致），列定义与 dev 库 information_schema 逐列一致；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_dict_item", comment = "码值项",
        indexes = {
                @TableIndex(name = "idx_dict_item_type", columns = "field_type_id"),
                @TableIndex(name = "idx_dict_item_value", columns = "field_value")})
public class SysDictItem extends BaseModel {

    /** 类型id（sys_dict_type.field_id） */
    @ColumnField(name = "field_type_id", comment = "类型id", type = ColumnType.STRING, length = 32)
    private String fieldTypeId;

    /** 码值 */
    @ColumnField(name = "field_value", comment = "码值", type = ColumnType.STRING, length = 50)
    private String fieldValue;

    /** 重声明：显示值（D5 遮蔽 MainModel.fieldName） */
    @ColumnField(name = "field_name", comment = "显示值", type = ColumnType.STRING, length = 200)
    private String fieldName;

    /** 重声明：是否启用（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_enable", comment = "是否启用", type = ColumnType.BOOLEAN, defaultValue = "b'1'")
    private Boolean fieldEnable;

    /** 是否系统码值（true=启动导入，不可维护） */
    @ColumnField(name = "field_system", comment = "是否系统码值", type = ColumnType.BOOLEAN, defaultValue = "b'0'")
    private Boolean fieldSystem;
}

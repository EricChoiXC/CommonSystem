package com.eric.base.sys.dict.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 码值类型模型
 *
 * @author Eric
 * @date 2026/9/2
 * @description 对应 sys_dict_type；码值类型按 field_code 排序展示（如 yes_no），
 *              页面左侧展示且不可维护；类型与码值项通过 field_id 一对多关联。
 *              列清单与 artificial/docs/设计文档/sys/dict/码值功能核心业务.md 保持一致。
 *              <p>2026/11/17 F19-DB-002 存量标注批二继承对齐（裁决 A：继承对齐表结构）：
 *              原继承 MainModel 与表结构不符（本表仅含 field_enable 一个审计列，无
 *              field_creator_id / field_create_time / field_updater_id / field_update_time
 *              列），改继承 BaseModel 对齐公共列模板；field_code / field_name / field_enable
 *              重声明为业务列 @ColumnField 标注（重声明字段 Java 类型与 MainModel 原声明
 *              一致），列定义与 dev 库 information_schema 逐列一致；公共列（field_id 主键）
 *              由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_dict_type", comment = "码值类型",
        indexes = {
                @TableIndex(name = "idx_dict_type_code", columns = "field_code")})
public class SysDictType extends BaseModel {

    /** 重声明：编号（D5 遮蔽 MainModel.fieldCode） */
    @ColumnField(name = "field_code", comment = "编号", type = ColumnType.STRING, length = 50)
    private String fieldCode;

    /** 重声明：名称（D5 遮蔽 MainModel.fieldName） */
    @ColumnField(name = "field_name", comment = "名称", type = ColumnType.STRING, length = 200)
    private String fieldName;

    /** 描述 */
    @ColumnField(name = "field_desc", comment = "描述", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldDesc;

    /** 重声明：是否启用（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_enable", comment = "是否启用", type = ColumnType.BOOLEAN, defaultValue = "b'1'")
    private Boolean fieldEnable;
}

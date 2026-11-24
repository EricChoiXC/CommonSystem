package com.eric.base.sys.form.model;

import com.eric.base.common.base.model.MainModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单主档实体
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 sys_form：自定义表单定义主档（一行一表单）。版本内容承载于
 *              sys_form_version（不可变版本快照），实例数据承载于 sys_form_data；
 *              field_current_version_id 始终指向最新发布版本行（从未发布为 NULL，
 *              停用不改变语义）。本表由实体注解声明、经注解表结构同步引擎建表
 *              （F15-DB-001），不新增迁移脚本；不建物理外键（工程惯例），主档与
 *              版本行/数据行的关联由应用层保证。
 *              field_form_code / field_form_name 为独立业务列（不重声明 MainModel
 *              继承的 fieldCode / fieldName，继承属性不入列清单、XML 不映射）；
 *              公共列（field_id 主键 + 审计五列）由引擎公共列模板内建，无需标注。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "表单主档实体：对应 sys_form；fieldCurrentVersionId 指向最新发布版本行"
        + "（从未发布为 null），fieldStatus 为 DRAFT（从未发布）/ PUBLISHED（存在当前发布版本）")
@TableSource(name = "sys_form", comment = "表单主档表（自定义表单定义）",
        indexes = {
                @TableIndex(name = "uk_form_code", columns = "field_form_code", unique = true)})
public class SysForm extends MainModel {

    /** 表单编码，全局唯一（Service 层查重 + uk_form_code 库级兜底；保存后不可变更） */
    @ColumnField(name = "field_form_code", comment = "表单编码,全局唯一,Service 层查重",
            type = ColumnType.STRING, length = 100)
    @Schema(description = "表单编码，全局唯一，保存期查重；保存后稳定标识不可变更")
    private String fieldFormCode;

    /** 表单名称 */
    @ColumnField(name = "field_form_name", comment = "表单名称", type = ColumnType.STRING, length = 200)
    @Schema(description = "表单名称")
    private String fieldFormName;

    /** 当前发布版本行 id（sys_form_version.field_id；从未发布为 NULL） */
    @ColumnField(name = "field_current_version_id", comment = "当前发布版本行id(从未发布为NULL)",
            type = ColumnType.STRING, length = 32, nullable = true)
    @Schema(description = "当前发布版本行 id；始终指向最新发布版本（回滚后指向回滚产生的新版本行），"
            + "从未发布为 null；停用不改变该语义")
    private String fieldCurrentVersionId;

    /** 表单状态：DRAFT=从未发布 PUBLISHED=存在当前发布版本（启停用走 fieldEnable） */
    @ColumnField(name = "field_status", comment = "表单状态: DRAFT=从未发布 PUBLISHED=存在当前发布版本",
            type = ColumnType.STRING, length = 20, defaultValue = "DRAFT")
    @Schema(description = "表单状态：DRAFT=从未发布 PUBLISHED=存在当前发布版本；"
            + "启停用走 fieldEnable（独立字段，updateEnable 只更新自身一行）")
    private String fieldStatus;
}

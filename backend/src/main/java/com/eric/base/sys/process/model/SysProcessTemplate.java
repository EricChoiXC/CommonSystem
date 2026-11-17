package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.MainModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程模板实体
 *
 * @author Eric
 * @date 2026/8/22
 * @description 对应 sys_process_template 表：流程模板主信息。
 *              字段命名遵循公共字段规范（field_ 前缀），公共字段继承 MainModel。
 *              <p>2026/11/17 F19-DB-002 存量标注批二：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField（field_name / field_code 为重声明遮蔽 MainModel
 *              继承列，D5 规则，Java 类型与 MainModel 声明一致），列定义与 dev 库
 *              information_schema 逐列一致；field_default_unique 为 STORED GENERATED
 *              生成列（注解不可表达）、引用它的唯一索引 uk_sys_process_template_default
 *              均不标注（VALIDATE 比对为已知 L2 豁免项，登记任务清单）；公共列
 *              （field_id 主键 + 审计五列）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_process_template", comment = "流程模板主表",
        indexes = {
                @TableIndex(name = "idx_template_enable", columns = "field_enable"),
                @TableIndex(name = "idx_template_model_name", columns = "field_model_name")})
public class SysProcessTemplate extends MainModel {

    /** 重声明：模板名称（D5 遮蔽 MainModel.fieldName） */
    @ColumnField(name = "field_name", comment = "模板名称", type = ColumnType.STRING, length = 200)
    private String fieldName;

    /** 所属模块：各模块 application.yml 中 process_template 的分类 key（如 sys_org） */
    @ColumnField(name = "field_model_name", comment = "所属模块(template_model二级配置项,如 biz_purchase_order)",
            type = ColumnType.STRING, length = 200)
    private String fieldModelName;

    /** 重声明：模板编号（D5 遮蔽 MainModel.fieldCode） */
    @ColumnField(name = "field_code", comment = "模板编号", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldCode;

    /** 版本号（初始 1，每次编辑 +1） */
    @ColumnField(name = "field_version", comment = "版本号", type = ColumnType.INT, defaultValue = "1")
    private Integer fieldVersion;

    /** 备注 */
    @ColumnField(name = "field_remark", comment = "备注", type = ColumnType.STRING, length = 2000, nullable = true)
    private String fieldRemark;

    /**
     * 是否默认模板（每分类最多一个，流程启动未指定模板时使用）。
     * 置为默认时替换同分类现有默认模板（见 SysProcessTemplateService#setDefault）。
     */
    @ColumnField(name = "field_default", comment = "是否默认模板（每分类最多一个，流程启动未指定模板时使用）",
            type = ColumnType.BOOLEAN, nullable = true, defaultValue = "b'0'")
    private Boolean fieldDefault;
}

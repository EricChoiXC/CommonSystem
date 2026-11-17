package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 流程实例实体
 *
 * @author Eric
 * @date 2026/8/23
 * @description 对应 sys_process_example 表：流程实例主信息（由实例所属模块负责创建，
 *              保存时状态为 DRAFT，提交后进入流转）。field_model_name 与模板的 field_model_name
 *              保持一致（process_template 分类 key，如 sys_org）；
 *              field_model_id 为所属业务文档 id。
 *              <p>2026/11/17 F19-DB-002 存量标注批二：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              表中存在的部分审计列 field_creator_id / field_create_time 按业务列标注；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_process_example", comment = "流程实例主表",
        indexes = {
                @TableIndex(name = "idx_example_creator", columns = "field_creator_id"),
                @TableIndex(name = "idx_example_model", columns = "field_model_name"),
                @TableIndex(name = "idx_example_status", columns = "field_status"),
                @TableIndex(name = "idx_example_template", columns = "field_template_id")})
public class SysProcessExample extends BaseModel {

    /** 模板id（sys_process_template.field_id） */
    @ColumnField(name = "field_template_id", comment = "模板id", type = ColumnType.STRING, length = 32)
    private String fieldTemplateId;

    /** 模板版本号（创建实例时的模板版本，快照隔离后续模板变更） */
    @ColumnField(name = "field_template_version", comment = "模板版本号(创建实例时的模板版本)",
            type = ColumnType.INT, defaultValue = "1")
    private Integer fieldTemplateVersion;

    /** 所属模块id（业务文档 id） */
    @ColumnField(name = "field_model_id", comment = "所属模块id(业务文档id)", type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldModelId;

    /** 所属模块（process_template 分类 key，如 sys_org） */
    @ColumnField(name = "field_model_name", comment = "所属模块(template_model二级配置项,如 biz_purchase_order)",
            type = ColumnType.STRING, length = 200)
    private String fieldModelName;

    /** 流程编号 */
    @ColumnField(name = "field_code", comment = "流程编号", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldCode;

    /** 创建人（起草人）（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_creator_id", comment = "创建人(起草人)", type = ColumnType.STRING, length = 32)
    private String fieldCreatorId;

    /** 创建时间（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_create_time", comment = "创建时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldCreateTime;

    /** 完成时间（办结/作废时写入） */
    @ColumnField(name = "field_finish_time", comment = "完成时间", type = ColumnType.TIMESTAMP, nullable = true)
    private Date fieldFinishTime;

    /** 流程状态：ABANDON / DRAFT / PROCESSING / PAUSE / FINISH / RETURN */
    @ColumnField(name = "field_status", comment = "流程状态: ABANDON=废弃 DRAFT=草稿 PROCESSING=审批中 PAUSE=暂停 FINISH=完成 RETURN=驳回",
            type = ColumnType.STRING, length = 20, defaultValue = "DRAFT")
    private String fieldStatus;
}

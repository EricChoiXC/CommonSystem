package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程模板历史版本实体
 *
 * @author Eric
 * @date 2026/8/22
 * @description 对应 sys_process_template_history 表：更新模板时归档的历史版本，
 *              将模板与全部节点字段信息统一序列化保存到 field_content（JSON 字符串），
 *              流水只增不改。
 *              <p>2026/11/17 F19-DB-002 存量标注批二：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_process_template_history", comment = "流程模板历史版本表",
        indexes = {
                @TableIndex(name = "idx_template_history_template", columns = "field_template_id")})
public class SysProcessTemplateHistory extends BaseModel {

    /** 模板id（sys_process_template.field_id） */
    @ColumnField(name = "field_template_id", comment = "模板id", type = ColumnType.STRING, length = 32)
    private String fieldTemplateId;

    /** 历史版本号（对应归档时刻的模板版本号） */
    @ColumnField(name = "field_version", comment = "历史版本号", type = ColumnType.INT)
    private Integer fieldVersion;

    /** 流程定义历史（模板+节点序列化的 JSON 字符串） */
    @ColumnField(name = "field_content", comment = "流程定义历史(模板+节点序列化)", type = ColumnType.JSON)
    private String fieldContent;
}

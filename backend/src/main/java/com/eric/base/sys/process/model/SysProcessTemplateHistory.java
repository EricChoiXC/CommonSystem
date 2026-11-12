package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.BaseModel;
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
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysProcessTemplateHistory extends BaseModel {

    /** 模板id（sys_process_template.field_id） */
    private String fieldTemplateId;

    /** 历史版本号（对应归档时刻的模板版本号） */
    private Integer fieldVersion;

    /** 流程定义历史（模板+节点序列化的 JSON 字符串） */
    private String fieldContent;
}

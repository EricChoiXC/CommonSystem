package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.BaseModel;
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
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysProcessExample extends BaseModel {

    /** 模板id（sys_process_template.field_id） */
    private String fieldTemplateId;

    /** 模板版本号（创建实例时的模板版本，快照隔离后续模板变更） */
    private Integer fieldTemplateVersion;

    /** 所属模块id（业务文档 id） */
    private String fieldModelId;

    /** 所属模块（process_template 分类 key，如 sys_org） */
    private String fieldModelName;

    /** 流程编号 */
    private String fieldCode;

    /** 创建人（起草人） */
    private String fieldCreatorId;

    /** 创建时间 */
    private Date fieldCreateTime;

    /** 完成时间（办结/作废时写入） */
    private Date fieldFinishTime;

    /** 流程状态：ABANDON / DRAFT / PROCESSING / PAUSE / FINISH / RETURN */
    private String fieldStatus;
}

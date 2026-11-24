package com.eric.base.sys.form.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 表单数据实体
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 sys_form_data：表单实例数据（JSON 存储）。流水结构（一期只增不改——
 *              API 分册定稿数据面 save 仅新增、无实例更新接口；删除为物理删除），
 *              不设 field_enable / field_update_time / field_updater_id（对齐 AGENTS §4
 *              流水先例 sys_log_operation / sys_task_history）。
 *              field_creator_id / field_create_time 为数据归属判定与列表展示所必需，
 *              按业务列显式标注（BaseModel 不内建审计列，先例 SysProcessExample /
 *              SysCalendarMain）。field_process_instance_id 为 v1.8 BPM 预留列（一期
 *              不写入，不建索引）；field_form_data 为 JSON 列（与版本 Schema 字段白名单
 *              同构，明细表为扁平行对象数组，落库即最终形态：未知字段拒绝、隐藏字段
 *              裁剪、公式以服务端重算值落库），禁止手工拼串。
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "表单数据实体：对应 sys_form_data；fieldFormData 存储态为原文 JSON 字符串，"
        + "load 响应由 Service 解析为字段值对象（读取层裁剪后形态）")
@TableSource(name = "sys_form_data", comment = "表单数据表（JSON 实例数据）",
        indexes = {
                @TableIndex(name = "idx_form_data_form", columns = "field_form_id"),
                @TableIndex(name = "idx_form_data_business", columns = "field_business_id")})
public class SysFormData extends BaseModel {

    /** 表单id（sys_form.field_id） */
    @ColumnField(name = "field_form_id", comment = "表单id", type = ColumnType.STRING, length = 32)
    private String fieldFormId;

    /** 实例锁定版本行id（sys_form_version.field_id；在途实例用原版本，不随主档漂移） */
    @ColumnField(name = "field_form_version_id", comment = "实例锁定版本行id(在途实例用原版本)",
            type = ColumnType.STRING, length = 32)
    private String fieldFormVersionId;

    /** 外部业务对象弱关联id（提交方传入；一期不做唯一性约束） */
    @ColumnField(name = "field_business_id", comment = "外部业务对象弱关联id(提交方传入,一期不做唯一性约束)",
            type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldBusinessId;

    /** 流程实例id（v1.8 BPM 预留列，一期不写入） */
    @ColumnField(name = "field_process_instance_id", comment = "流程实例id(v1.8 BPM预留列,一期不写入)",
            type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldProcessInstanceId;

    /** 表单数据 JSON（与版本 Schema 字段白名单同构，明细表为扁平行对象数组） */
    @ColumnField(name = "field_form_data", comment = "表单数据JSON(与版本Schema字段白名单同构)",
            type = ColumnType.JSON)
    @Schema(description = "表单数据 JSON：存储态为原文 JSON 字符串（{ 字段码: 值 }，明细表字段值为"
            + "扁平行对象数组；提交裁剪与公式重算后的最终落库形态），load 响应为解析后的字段值对象")
    private Object fieldFormData;

    /** 创建者id（数据归属判定依据；表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_creator_id", comment = "创建者id(数据归属判定依据)",
            type = ColumnType.STRING, length = 32)
    private String fieldCreatorId;

    /** 创建时间（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_create_time", comment = "创建时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldCreateTime;
}

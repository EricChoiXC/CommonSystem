package com.eric.base.sys.number.model;

import com.eric.base.common.base.model.MainModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编号规则实体
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 sys_number_rule：编号规则主信息。同一接入项（fieldModelId = numberRule.yml key）
 *              允许多条规则，同一时刻至多一条启用；启用切换由 Service 同事务自动停用同接入项
 *              原已启用规则（切换式启用，见方案 §核心表说明 D1）。fieldModelId 保存后锁定不可改
 *              （改动会使既有计数器作用域悬空，D3）；fieldModelName / fieldModelField 由接入项
 *              下拉带出。公共字段继承 MainModel。
 *              <p>2026/11/17 F19-DB-003 存量标注批三：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField（field_code / field_name 为重声明遮蔽 MainModel 继承列，
 *              D5 规则，Java 类型与 MainModel 声明一致），列定义与 dev 库 information_schema
 *              逐列一致；公共列（field_id 主键 + 审计五列）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "编号规则实体：对应 sys_number_rule；load / save / update 响应中 fieldRule "
        + "为解析后的规则配置对象，列表响应中为原文 JSON 字符串，save / update 请求体传规则配置对象")
@TableSource(name = "sys_number_rule", comment = "编号规则表",
        indexes = {
                @TableIndex(name = "idx_number_rule_code", columns = "field_code"),
                @TableIndex(name = "idx_number_rule_model", columns = {"field_model_name", "field_model_field"})})
public class SysNumberRule extends MainModel {

    /** 重声明：编号（D5 遮蔽 MainModel.fieldCode） */
    @ColumnField(name = "field_code", comment = "规则编码，人工维护，Service 层查重", type = ColumnType.STRING, length = 50)
    @Schema(description = "规则编码，人工维护，Service 层查重")
    private String fieldCode;

    /** 重声明：名称（D5 遮蔽 MainModel.fieldName） */
    @ColumnField(name = "field_name", comment = "编号规则名称", type = ColumnType.STRING, length = 200)
    @Schema(description = "编号规则名称")
    private String fieldName;

    /**
     * 编号规则 JSON（对应列 field_rule JSON）。
     * 双形态承载（方案 §3.2 A4）：存储态与列表响应为原文 JSON 字符串（列表页不解析）；
     * load 响应由 Service 解析为 {@link SysNumberRuleConfigDo} 对象返回（前端直接消费）；
     * save/update 请求体经 Jackson 反序列化为 Map，Service 转 Do 校验后序列化为字符串落库。
     * 禁止手工拼串（先例 SysProcessTemplateNodeConfigDo）。
     */
    @ColumnField(name = "field_rule", comment = "编号规则 JSON（SysNumberRuleConfigDo，结构见方案 §2）",
            type = ColumnType.JSON, nullable = true)
    @Schema(description = "编号规则 JSON：存储态 / 列表响应为原文 JSON 字符串；load 响应为解析后的"
            + "规则配置对象（SysNumberRuleConfigDo）；save / update 请求体传规则配置对象")
    private Object fieldRule;

    /** 所属接入项编码（numberRule.yml key），保存后锁定不可改（D3） */
    @ColumnField(name = "field_model_id", comment = "所属接入项编码（numberRule.yml key），保存后锁定",
            type = ColumnType.STRING, length = 100)
    @Schema(description = "所属接入项编码（numberRule.yml key），保存后锁定不可改（D3）")
    private String fieldModelId;

    /** 所属模型名（由接入项带出） */
    @ColumnField(name = "field_model_name", comment = "所属模型名（下拉带出）",
            type = ColumnType.STRING, length = 200, nullable = true)
    @Schema(description = "所属模型名（由接入项带出，编辑时不受理变更）")
    private String fieldModelName;

    /** 所属模型字段（由接入项带出，通常为 fieldCode） */
    @ColumnField(name = "field_model_field", comment = "所属模型字段（下拉带出）",
            type = ColumnType.STRING, length = 100, nullable = true)
    @Schema(description = "所属模型字段（由接入项带出，通常为 fieldCode，编辑时不受理变更）")
    private String fieldModelField;
}

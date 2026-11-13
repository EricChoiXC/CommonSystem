package com.eric.base.sys.number.model;

import com.eric.base.common.base.model.MainModel;
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
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "编号规则实体：对应 sys_number_rule；load / save / update 响应中 fieldRule "
        + "为解析后的规则配置对象，列表响应中为原文 JSON 字符串，save / update 请求体传规则配置对象")
public class SysNumberRule extends MainModel {

    /**
     * 编号规则 JSON（对应列 field_rule JSON）。
     * 双形态承载（方案 §3.2 A4）：存储态与列表响应为原文 JSON 字符串（列表页不解析）；
     * load 响应由 Service 解析为 {@link SysNumberRuleConfigDo} 对象返回（前端直接消费）；
     * save/update 请求体经 Jackson 反序列化为 Map，Service 转 Do 校验后序列化为字符串落库。
     * 禁止手工拼串（先例 SysProcessTemplateNodeConfigDo）。
     */
    @Schema(description = "编号规则 JSON：存储态 / 列表响应为原文 JSON 字符串；load 响应为解析后的"
            + "规则配置对象（SysNumberRuleConfigDo）；save / update 请求体传规则配置对象")
    private Object fieldRule;

    /** 所属接入项编码（numberRule.yml key），保存后锁定不可改（D3） */
    @Schema(description = "所属接入项编码（numberRule.yml key），保存后锁定不可改（D3）")
    private String fieldModelId;

    /** 所属模型名（由接入项带出） */
    @Schema(description = "所属模型名（由接入项带出，编辑时不受理变更）")
    private String fieldModelName;

    /** 所属模型字段（由接入项带出，通常为 fieldCode） */
    @Schema(description = "所属模型字段（由接入项带出，通常为 fieldCode，编辑时不受理变更）")
    private String fieldModelField;
}

package com.eric.base.sys.number.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 编号规则列表查询条件
 *
 * @author Eric
 * @date 2026/11/13
 * @description 编号管理列表页（NB3-BE-002，方案 §3.2 rule/list）的查询条件：
 *              fieldName / fieldCode 模糊匹配，fieldModelId / fieldModelField 精确匹配，
 *              fieldEnable 精确匹配；均为空表示全量（登录即可查询，D6）。
 *              条件字段名与 sys_number_rule 列一一对应（field_ 前缀驼峰化）。
 */
@Data
@Schema(description = "编号规则列表查询条件")
public class SysNumberRuleQuery {

    /** 规则名称模糊匹配（field_name LIKE '%x%'），为空表示不过滤 */
    private String fieldName;

    /** 规则编码模糊匹配（field_code LIKE '%x%'），为空表示不过滤 */
    private String fieldCode;

    /** 所属接入项编码精确匹配（field_model_id，即 numberRule.yml key），为空表示不过滤 */
    private String fieldModelId;

    /** 所属模型字段精确匹配（field_model_field），为空表示不过滤 */
    private String fieldModelField;

    /** 启用状态精确匹配（field_enable；true 仅启用、false 仅停用），为空表示全部 */
    private Boolean fieldEnable;
}

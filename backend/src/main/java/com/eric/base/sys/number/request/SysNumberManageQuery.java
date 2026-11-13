package com.eric.base.sys.number.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 取号明细列表查询条件
 *
 * @author Eric
 * @date 2026/11/13
 * @description 编号管理取号明细查询（任务 NB3-BE-005，方案 §3.2 manage/recordList）的查询条件：
 *              fieldScopeKey（含 fieldRuleId 翻译）/ fieldPeriod 精确匹配、voidFlag 作废状态
 *              三态过滤、recordTimeBegin / recordTimeEnd 取号时间范围（含边界）；
 *              明细表无 field_rule_id 列，固定接入作用域 = 规则 field_id（方案 §5.1），
 *              fieldRuleId 由服务端翻译为 field_scope_key 查询，与 fieldScopeKey 同时传入时
 *              以 fieldRuleId 优先。条件字段名与 sys_number_record_detail 列对应
 *              （field_ 前缀驼峰化）。
 */
@Data
@Schema(description = "取号明细列表查询条件")
public class SysNumberManageQuery {

    /** 取号作用域键精确匹配（field_scope_key；固定接入 = 规则 ID，非固定接入 = {modelName}:{宿主记录ID}），为空表示不过滤 */
    @Schema(description = "取号作用域键精确匹配（field_scope_key；固定接入 = 规则 ID，"
            + "非固定接入 = {modelName}:{宿主记录ID}），为空表示不过滤")
    private String fieldScopeKey;

    /** 编号规则 ID 精确匹配（固定接入作用域即规则 field_id，服务端翻译为 field_scope_key 查询）；与 fieldScopeKey 同时传入时以本字段优先 */
    @Schema(description = "编号规则 ID 精确匹配（固定接入作用域即规则 field_id，明细表无 field_rule_id 列，"
            + "服务端翻译为 field_scope_key 查询）；与 fieldScopeKey 同时传入时以 fieldRuleId 优先")
    private String fieldRuleId;

    /** 周期键精确匹配（field_period；NONE=ALL / DAY=yyyyMMdd / WEEK=yyyyWww / MONTH=yyyyMM / YEAR=yyyy），为空表示不过滤 */
    @Schema(description = "周期键精确匹配（field_period；NONE=ALL / DAY=yyyyMMdd / WEEK=yyyyWww / "
            + "MONTH=yyyyMM / YEAR=yyyy），为空表示不过滤")
    private String fieldPeriod;

    /** 作废状态过滤：true 仅已作废（field_void_time 非空）、false 仅未作废、null（缺省）不过滤 */
    @Schema(description = "作废状态过滤：true 仅已作废（field_void_time 非空）、false 仅未作废、"
            + "null（缺省）不过滤")
    private Boolean voidFlag;

    /** 取号时间范围起（含边界，field_record_time >=），为空表示不限 */
    @Schema(description = "取号时间范围起（含边界，field_record_time >=），为空表示不限")
    private Date recordTimeBegin;

    /** 取号时间范围止（含边界，field_record_time <=），为空表示不限 */
    @Schema(description = "取号时间范围止（含边界，field_record_time <=），为空表示不限")
    private Date recordTimeEnd;
}

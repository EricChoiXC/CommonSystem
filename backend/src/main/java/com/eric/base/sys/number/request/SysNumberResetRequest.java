package com.eric.base.sys.number.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 重置计数器请求对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 manage/reset 接口请求契约（任务 NB3-BE-005，方案 §3.2 / §5.4 reset）：
 *              作用域二选一——{@code fieldRuleId}（固定接入，作用域 = 规则 field_id，
 *              服务端校验规则存在）或 {@code fieldScopeKey}（非固定接入作用域原样使用），
 *              两者都传以 fieldRuleId 优先、都空抛 IllegalArgumentException（BAD_REQUEST）；
 *              {@code fieldSerialId} / {@code fieldPeriod} 可空 = 全部流水项 / 全部周期。
 *              重置会造成重号（方案 §5.4 预期语义），需前端二次确认后调用，操作落操作日志。
 */
@Data
@Schema(description = "重置计数器请求：作用域二选一（fieldRuleId 固定接入 / fieldScopeKey 非固定接入，"
        + "同时传入以 fieldRuleId 优先），可限定 fieldSerialId / fieldPeriod（可空 = 全部）；"
        + "重置会造成重号，需前端二次确认后调用")
public class SysNumberResetRequest {

    /** 编号规则 ID（固定接入，作用域 = 规则 field_id，服务端校验规则存在）；与 fieldScopeKey 二选一 */
    @Schema(description = "编号规则 ID（固定接入，作用域 = 规则 field_id，服务端校验规则存在）；"
            + "与 fieldScopeKey 二选一，同时传入以 fieldRuleId 优先")
    private String fieldRuleId;

    /** 取号作用域键（非固定接入作用域原样使用，如 ErpContract:CAT-1）；与 fieldRuleId 二选一 */
    @Schema(description = "取号作用域键（非固定接入作用域原样使用，如 ErpContract:CAT-1）；"
            + "与 fieldRuleId 二选一，同时传入以 fieldRuleId 优先")
    private String fieldScopeKey;

    /** 流水号项 ID（field_rule 中 seq 项 item.id），可空 = 该作用域全部流水项 */
    @Schema(description = "流水号项 ID（field_rule 中 seq 项 item.id），可空 = 该作用域全部流水项")
    private String fieldSerialId;

    /** 周期键（NONE=ALL / DAY=yyyyMMdd / WEEK=yyyyWww / MONTH=yyyyMM / YEAR=yyyy），可空 = 该作用域全部周期 */
    @Schema(description = "周期键（NONE=ALL / DAY=yyyyMMdd / WEEK=yyyyWww / MONTH=yyyyMM / YEAR=yyyy），"
            + "可空 = 该作用域全部周期")
    private String fieldPeriod;
}

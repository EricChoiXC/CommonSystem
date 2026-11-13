package com.eric.base.sys.number.model;

import com.eric.base.common.base.model.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 编号取号明细实体
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 sys_number_record_detail：每次取号落一行，支撑幂等（按
 *              fieldScopeKey + fieldModelId 查未作废明细）、作废、补号与审计（方案 §5.3/§5.4）。
 *              流水类表只增不改，不设 field_enable / field_update_time / field_updater_id（D2）；
 *              作废为例外更新：仅置 fieldVoidTime / fieldVoidReason 两字段，不回退计数器。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "取号明细实体：对应 sys_number_record_detail，每次取号一行"
        + "（recordList / void 响应契约）；流水类表只增不改，作废仅更新 void 两字段")
public class SysNumberRecordDetail extends BaseModel {

    /** 取号作用域键（与计数表同口径） */
    @Schema(description = "取号作用域键（固定接入 = 规则 ID，非固定接入 = {modelName}:{宿主记录ID}）")
    private String fieldScopeKey;

    /** 周期键（与计数表同口径） */
    @Schema(description = "周期键（NONE=ALL / DAY=yyyyMMdd / WEEK=yyyyWww / MONTH=yyyyMM / YEAR=yyyy）")
    private String fieldPeriod;

    /** 流水号项ID（field_rule 中 seq 项 item.id） */
    @Schema(description = "流水号项 ID（field_rule 中 seq 项 item.id）")
    private String fieldSerialId;

    /** 本次取到的流水号 */
    @Schema(description = "本次取到的流水号")
    private Integer fieldSerialNo;

    /** 业务模型名 */
    @Schema(description = "业务模型名")
    private String fieldModelName;

    /** 业务单据ID（幂等锚点） */
    @Schema(description = "业务单据 ID（幂等锚点）")
    private String fieldModelId;

    /** 发出的完整编号（对齐业务单号列宽 varchar(64)） */
    @Schema(description = "发出的完整编号")
    private String fieldCode;

    /** 取号时间 */
    @Schema(description = "取号时间")
    private Date fieldRecordTime;

    /** 取号操作人 */
    @Schema(description = "取号操作人")
    private String fieldCreatorId;

    /** 作废时间（NULL=有效） */
    @Schema(description = "作废时间（NULL=有效，void 后置值）")
    private Date fieldVoidTime;

    /** 作废原因 */
    @Schema(description = "作废原因（void 请求传入，可空）")
    private String fieldVoidReason;
}

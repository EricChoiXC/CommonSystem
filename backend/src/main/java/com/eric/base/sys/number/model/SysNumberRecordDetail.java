package com.eric.base.sys.number.model;

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
 * 编号取号明细实体
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 sys_number_record_detail：每次取号落一行，支撑幂等（按
 *              fieldScopeKey + fieldModelId 查未作废明细）、作废、补号与审计（方案 §5.3/§5.4）。
 *              流水类表只增不改，不设 field_enable / field_update_time / field_updater_id（D2）；
 *              作废为例外更新：仅置 fieldVoidTime / fieldVoidReason 两字段，不回退计数器。
 *              <p>2026/11/17 F19-DB-003 存量标注批三：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField（field_creator_id 为表中存在的部分审计列，按业务列标注；
 *              含唯一索引 uk_number_record_detail_no，V1_01_045 放宽后含 field_record_time），
 *              列定义与 dev 库 information_schema 逐列一致；公共列（field_id 主键）由引擎
 *              公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "取号明细实体：对应 sys_number_record_detail，每次取号一行"
        + "（recordList / void 响应契约）；流水类表只增不改，作废仅更新 void 两字段")
@TableSource(name = "sys_number_record_detail", comment = "编号取号明细表",
        indexes = {
                @TableIndex(name = "idx_number_record_detail_code", columns = "field_code"),
                @TableIndex(name = "idx_number_record_detail_model", columns = {"field_model_name", "field_model_id"}),
                @TableIndex(name = "uk_number_record_detail_no",
                        columns = {"field_scope_key", "field_serial_id", "field_period", "field_serial_no", "field_record_time"}, unique = true)})
public class SysNumberRecordDetail extends BaseModel {

    /** 取号作用域键（与计数表同口径） */
    @ColumnField(name = "field_scope_key", comment = "取号作用域键", type = ColumnType.STRING, length = 200)
    @Schema(description = "取号作用域键（固定接入 = 规则 ID，非固定接入 = {modelName}:{宿主记录ID}）")
    private String fieldScopeKey;

    /** 周期键（与计数表同口径） */
    @ColumnField(name = "field_period", comment = "周期键", type = ColumnType.STRING, length = 32)
    @Schema(description = "周期键（NONE=ALL / DAY=yyyyMMdd / WEEK=yyyyWww / MONTH=yyyyMM / YEAR=yyyy）")
    private String fieldPeriod;

    /** 流水号项ID（field_rule 中 seq 项 item.id） */
    @ColumnField(name = "field_serial_id", comment = "流水号项ID（field_rule 中 seq 项 item.id）",
            type = ColumnType.STRING, length = 64)
    @Schema(description = "流水号项 ID（field_rule 中 seq 项 item.id）")
    private String fieldSerialId;

    /** 本次取到的流水号 */
    @ColumnField(name = "field_serial_no", comment = "本次取到的流水号", type = ColumnType.INT)
    @Schema(description = "本次取到的流水号")
    private Integer fieldSerialNo;

    /** 业务模型名 */
    @ColumnField(name = "field_model_name", comment = "业务模型名", type = ColumnType.STRING, length = 200)
    @Schema(description = "业务模型名")
    private String fieldModelName;

    /** 业务单据ID（幂等锚点） */
    @ColumnField(name = "field_model_id", comment = "业务单据ID，幂等锚点", type = ColumnType.STRING, length = 32)
    @Schema(description = "业务单据 ID（幂等锚点）")
    private String fieldModelId;

    /** 发出的完整编号（对齐业务单号列宽 varchar(64)） */
    @ColumnField(name = "field_code", comment = "发出的完整编号（对齐业务单号列宽 varchar(64)）",
            type = ColumnType.STRING, length = 64)
    @Schema(description = "发出的完整编号")
    private String fieldCode;

    /** 取号时间 */
    @ColumnField(name = "field_record_time", comment = "取号时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    @Schema(description = "取号时间")
    private Date fieldRecordTime;

    /** 取号操作人（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_creator_id", comment = "取号操作人", type = ColumnType.STRING, length = 32, nullable = true)
    @Schema(description = "取号操作人")
    private String fieldCreatorId;

    /** 作废时间（NULL=有效） */
    @ColumnField(name = "field_void_time", comment = "作废时间，NULL=有效", type = ColumnType.TIMESTAMP, nullable = true)
    @Schema(description = "作废时间（NULL=有效，void 后置值）")
    private Date fieldVoidTime;

    /** 作废原因 */
    @ColumnField(name = "field_void_reason", comment = "作废原因", type = ColumnType.STRING, length = 200, nullable = true)
    @Schema(description = "作废原因（void 请求传入，可空）")
    private String fieldVoidReason;
}

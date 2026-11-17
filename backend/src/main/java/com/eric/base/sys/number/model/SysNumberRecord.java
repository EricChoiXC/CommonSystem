package com.eric.base.sys.number.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 编号取号计数实体
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 sys_number_record：取号计数行，作用域（fieldScopeKey）× 流水项
 *              （fieldSerialId = field_rule 中 seq 项 item.id）× 周期（fieldPeriod）一行，
 *              fieldSerialNo 为该组合已取到的最大流水号，经
 *              INSERT ... ON DUPLICATE KEY UPDATE + LAST_INSERT_ID 原子自增（方案 §5.2）。
 *              周期切换或人工重置时旧行置 fieldRecordFinishTime 封存、新行从 start 重开；
 *              流水类表只增不改，不设 field_enable / field_update_time / field_updater_id（D2）。
 *              <p>2026/11/17 F19-DB-003 存量标注批三：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField（含唯一索引 uk_number_record_scope），列定义与 dev 库
 *              information_schema 逐列一致；公共列（field_id 主键）由引擎公共列模板内建，
 *              无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_number_record", comment = "编号取号计数表",
        indexes = {
                @TableIndex(name = "idx_number_record_rule", columns = "field_rule_id"),
                @TableIndex(name = "uk_number_record_scope", columns = {"field_scope_key", "field_serial_id", "field_period"}, unique = true)})
public class SysNumberRecord extends BaseModel {

    /** 编号规则ID（非固定接入为空） */
    @ColumnField(name = "field_rule_id", comment = "编号规则ID（非固定接入为空）",
            type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldRuleId;

    /** 取号作用域键（固定接入 = 规则 field_id，非固定接入 = {modelName}:{宿主记录ID}） */
    @ColumnField(name = "field_scope_key",
            comment = "取号作用域键（固定接入=规则field_id，非固定接入={modelName}:{宿主记录ID}）",
            type = ColumnType.STRING, length = 200)
    private String fieldScopeKey;

    /** 流水号项ID（field_rule 中 seq 项 item.id，计数器锚点） */
    @ColumnField(name = "field_serial_id", comment = "流水号项ID（field_rule 中 seq 项 item.id）",
            type = ColumnType.STRING, length = 64)
    private String fieldSerialId;

    /** 周期键（NONE=ALL / DAY=yyyyMMdd / WEEK=yyyy'W'ww / MONTH=yyyyMM / YEAR=yyyy） */
    @ColumnField(name = "field_period",
            comment = "周期键（NONE=ALL/DAY=yyyyMMdd/WEEK=yyyyWww/MONTH=yyyyMM/YEAR=yyyy）",
            type = ColumnType.STRING, length = 32)
    private String fieldPeriod;

    /** 当前流水号（该作用域+流水项+周期已取到的最大值，首号按 start 初始化） */
    @ColumnField(name = "field_serial_no",
            comment = "当前流水号（该作用域+流水项+周期已取到的最大值，首号按 start 初始化）",
            type = ColumnType.INT)
    private Integer fieldSerialNo;

    /** 最近取号时间 */
    @ColumnField(name = "field_record_time", comment = "最近取号时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldRecordTime;

    /** 封存时间（周期结束或人工重置时置值，NULL=使用中） */
    @ColumnField(name = "field_record_finish_time", comment = "封存时间（周期结束或人工重置时置值，NULL=使用中）",
            type = ColumnType.TIMESTAMP, nullable = true)
    private Date fieldRecordFinishTime;
}

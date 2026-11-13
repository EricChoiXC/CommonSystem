package com.eric.base.sys.number.model;

import com.eric.base.common.base.model.BaseModel;
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
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysNumberRecord extends BaseModel {

    /** 编号规则ID（非固定接入为空） */
    private String fieldRuleId;

    /** 取号作用域键（固定接入 = 规则 field_id，非固定接入 = {modelName}:{宿主记录ID}） */
    private String fieldScopeKey;

    /** 流水号项ID（field_rule 中 seq 项 item.id，计数器锚点） */
    private String fieldSerialId;

    /** 周期键（NONE=ALL / DAY=yyyyMMdd / WEEK=yyyy'W'ww / MONTH=yyyyMM / YEAR=yyyy） */
    private String fieldPeriod;

    /** 当前流水号（该作用域+流水项+周期已取到的最大值，首号按 start 初始化） */
    private Integer fieldSerialNo;

    /** 最近取号时间 */
    private Date fieldRecordTime;

    /** 封存时间（周期结束或人工重置时置值，NULL=使用中） */
    private Date fieldRecordFinishTime;
}

package com.eric.base.sys.number.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.number.model.SysNumberRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 编号取号计数数据访问映射
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 resources/mappers/sys/number/SysNumberRecordMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑；计数器原子 upsert（upsertSerial）、
 *              当前流水号取回（selectCurrentSerialNo）与周期切换封存（sealOtherPeriods）
 *              随 NB2-BE-002 补充，人工重置封存（sealByScope）随 NB3-BE-005 补充。
 */
@Mapper
public interface SysNumberRecordMapper extends IBaseMapper<SysNumberRecord> {

    /**
     * 计数器原子推进（NB2-BE-002，方案 §5.2 原文口径）：
     * 不存在则插入首行（流水号 = start，首号按 start 初始化）；
     * 已存在则 field_serial_no = LAST_INSERT_ID(field_serial_no + 1) 原子自增；
     * 命中行已封存（人工重置后残留，方案 §5.4）时按 start 重开。
     * 走 uk_number_record_scope 定位行，行锁持有至事务提交，保证并发串行。
     *
     * @param model 计数行（fieldScopeKey / fieldSerialId / fieldPeriod 定位，fieldSerialNo = start）
     * @return 影响行数（恒为 1）
     */
    int upsertSerial(@Param("model") SysNumberRecord model);

    /**
     * 取回自增后的当前流水号（NB2-BE-002）：与 upsertSerial 同事务执行，
     * 命中行行锁保证读到本事务写入的最新值；本表无自增列，不依赖 getGeneratedKeys。
     * 仅读使用中行（field_record_finish_time IS NULL，NB3-BE-005 reset 收口）：
     * 取号侧 upsertSerial 已在同事务清空命中行（含封存重开行）的 finish_time，
     * 取回链路不受影响；预览侧经该过滤排除人工重置封存的同周期残留行，
     * 返回 null 视为从 start 重开，与取号侧口径一致（方案 §5.4）。
     *
     * @param fieldScopeKey 取号作用域键
     * @param fieldSerialId 流水项 ID（seq 项 item.id）
     * @param fieldPeriod   周期键
     * @return 当前流水号；行不存在或已封存时返回 null
     */
    Integer selectCurrentSerialNo(@Param("fieldScopeKey") String fieldScopeKey,
                                  @Param("fieldSerialId") String fieldSerialId,
                                  @Param("fieldPeriod") String fieldPeriod);

    /**
     * 周期切换封存（NB2-BE-002，方案 §5.2）：将同作用域同流水项下、
     * 周期键不同于当前取号周期且仍在使用中（field_record_finish_time IS NULL）
     * 的计数行置封存时间；无匹配行时 0 行更新（同周期取号幂等）。
     *
     * @param fieldScopeKey 取号作用域键
     * @param fieldSerialId 流水项 ID（seq 项 item.id）
     * @param fieldPeriod   当前取号周期键
     * @return 封存行数（0 或 1）
     */
    int sealOtherPeriods(@Param("fieldScopeKey") String fieldScopeKey,
                         @Param("fieldSerialId") String fieldSerialId,
                         @Param("fieldPeriod") String fieldPeriod);

    /**
     * 人工重置封存（NB3-BE-005，方案 §5.4 reset）：将作用域（可限定流水项 / 周期，
     * 均可空 = 全部）使用中计数行（field_record_finish_time IS NULL）置封存时间，
     * 下次取号经 upsertSerial 封存行分支按 start 重开；既有明细不删除不改动（审计保留），
     * 重开会重发同号（「会造成重号」属预期语义）。
     *
     * @param fieldScopeKey 取号作用域键
     * @param fieldSerialId 流水项 ID（可空 = 全部流水项）
     * @param fieldPeriod   周期键（可空 = 全部周期）
     * @return 封存行数（0 或 1，限定流水项 / 周期后同三元组至多一行）
     */
    int sealByScope(@Param("fieldScopeKey") String fieldScopeKey,
                    @Param("fieldSerialId") String fieldSerialId,
                    @Param("fieldPeriod") String fieldPeriod);
}

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
 *              随 NB2-BE-002 补充。
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
     *
     * @param fieldScopeKey 取号作用域键
     * @param fieldSerialId 流水项 ID（seq 项 item.id）
     * @param fieldPeriod   周期键
     * @return 当前流水号；行不存在时返回 null（不应发生，upsertSerial 后必存在）
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
}

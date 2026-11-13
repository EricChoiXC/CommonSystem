package com.eric.base.sys.number.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.number.mapper.SysNumberRecordMapper;
import com.eric.base.sys.number.model.SysNumberRecord;
import org.springframework.stereotype.Repository;

/**
 * 编号取号计数数据访问对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 SysNumberRecordMapper 一对一对接，不承载业务逻辑；
 *              计数器原子推进 / 当前流水号取回 / 周期切换封存随 NB2-BE-002 补充。
 */
@Repository
public class SysNumberRecordDao extends BaseDao<SysNumberRecord, SysNumberRecordMapper> {

    public SysNumberRecordDao(SysNumberRecordMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 计数器原子推进（INSERT ... ON DUPLICATE KEY UPDATE，方案 §5.2）。
     *
     * @param model 计数行（定位三元组 + start 初始流水号）
     * @return 影响行数（恒为 1）
     */
    public int upsertSerial(SysNumberRecord model) {
        return getBaseMapper().upsertSerial(model);
    }

    /**
     * 取回自增后的当前流水号（与 upsertSerial 同事务，行锁保证读到本事务最新值）。
     *
     * @param fieldScopeKey 取号作用域键
     * @param fieldSerialId 流水项 ID
     * @param fieldPeriod   周期键
     * @return 当前流水号
     */
    public Integer selectCurrentSerialNo(String fieldScopeKey, String fieldSerialId, String fieldPeriod) {
        return getBaseMapper().selectCurrentSerialNo(fieldScopeKey, fieldSerialId, fieldPeriod);
    }

    /**
     * 周期切换封存：置同作用域同流水项其他周期使用中行的封存时间。
     *
     * @param fieldScopeKey 取号作用域键
     * @param fieldSerialId 流水项 ID
     * @param fieldPeriod   当前取号周期键
     * @return 封存行数（0 或 1）
     */
    public int sealOtherPeriods(String fieldScopeKey, String fieldSerialId, String fieldPeriod) {
        return getBaseMapper().sealOtherPeriods(fieldScopeKey, fieldSerialId, fieldPeriod);
    }
}

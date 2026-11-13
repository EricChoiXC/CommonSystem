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
 *              计数器原子 upsert 等扩展方法随 NB2-BE-002 补充。
 */
@Repository
public class SysNumberRecordDao extends BaseDao<SysNumberRecord, SysNumberRecordMapper> {

    public SysNumberRecordDao(SysNumberRecordMapper baseMapper) {
        super(baseMapper);
    }
}

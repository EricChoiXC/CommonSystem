package com.eric.base.sys.number.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.number.mapper.SysNumberRecordDetailMapper;
import com.eric.base.sys.number.model.SysNumberRecordDetail;
import org.springframework.stereotype.Repository;

/**
 * 编号取号明细数据访问对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 SysNumberRecordDetailMapper 一对一对接，不承载业务逻辑；
 *              明细筛选查询、void 作废等扩展方法随 NB3-BE-005 补充。
 */
@Repository
public class SysNumberRecordDetailDao extends BaseDao<SysNumberRecordDetail, SysNumberRecordDetailMapper> {

    public SysNumberRecordDetailDao(SysNumberRecordDetailMapper baseMapper) {
        super(baseMapper);
    }
}

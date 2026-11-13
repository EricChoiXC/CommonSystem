package com.eric.base.sys.number.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.number.model.SysNumberRecordDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 编号取号明细数据访问映射
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 resources/mappers/sys/number/SysNumberRecordDetailMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑；明细筛选查询与 void 作废更新随 NB3-BE-005 补充。
 */
@Mapper
public interface SysNumberRecordDetailMapper extends IBaseMapper<SysNumberRecordDetail> {
}

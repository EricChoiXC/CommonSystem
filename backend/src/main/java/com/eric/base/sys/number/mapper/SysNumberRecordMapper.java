package com.eric.base.sys.number.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.number.model.SysNumberRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 编号取号计数数据访问映射
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 resources/mappers/sys/number/SysNumberRecordMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑；计数器原子 upsert
 *              （INSERT ... ON DUPLICATE KEY UPDATE + LAST_INSERT_ID）随 NB2-BE-002 补充。
 */
@Mapper
public interface SysNumberRecordMapper extends IBaseMapper<SysNumberRecord> {
}

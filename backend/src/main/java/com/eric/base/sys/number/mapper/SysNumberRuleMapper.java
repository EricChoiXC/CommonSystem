package com.eric.base.sys.number.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.number.model.SysNumberRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 编号规则数据访问映射
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 resources/mappers/sys/number/SysNumberRuleMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑；列表查询分支与切换启用等业务 SQL
 *              随 NB3-BE-002 补充。
 */
@Mapper
public interface SysNumberRuleMapper extends IBaseMapper<SysNumberRule> {
}

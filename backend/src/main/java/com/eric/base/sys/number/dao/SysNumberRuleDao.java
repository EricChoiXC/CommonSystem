package com.eric.base.sys.number.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.number.mapper.SysNumberRuleMapper;
import com.eric.base.sys.number.model.SysNumberRule;
import org.springframework.stereotype.Repository;

/**
 * 编号规则数据访问对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 SysNumberRuleMapper 一对一对接，不承载业务逻辑；
 *              列表查询、切换启用等扩展方法随 NB3-BE-002 补充。
 */
@Repository
public class SysNumberRuleDao extends BaseDao<SysNumberRule, SysNumberRuleMapper> {

    public SysNumberRuleDao(SysNumberRuleMapper baseMapper) {
        super(baseMapper);
    }
}

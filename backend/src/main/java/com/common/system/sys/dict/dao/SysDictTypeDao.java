package com.common.system.sys.dict.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.dict.mapper.SysDictTypeMapper;
import com.common.system.sys.dict.model.SysDictType;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 码值类型数据访问对象
 *
 * @author Eric
 * @date 2026/9/2
 * @description 与 SysDictTypeMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysDictTypeDao extends BaseDao<SysDictType, SysDictTypeMapper> {

    public SysDictTypeDao(SysDictTypeMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 全部码值类型（按 field_code 升序）
     */
    public List<SysDictType> selectAll() {
        return getBaseMapper().selectAll();
    }

    /**
     * 按编号查询码值类型
     */
    public SysDictType selectByCode(String fieldCode) {
        return getBaseMapper().selectByCode(fieldCode);
    }
}

package com.common.system.sys.dict.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.dict.mapper.SysDictItemMapper;
import com.common.system.sys.dict.model.SysDictItem;
import com.common.system.sys.dict.response.SysDictItemVo;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 码值项数据访问对象
 *
 * @author Eric
 * @date 2026/9/2
 * @description 与 SysDictItemMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysDictItemDao extends BaseDao<SysDictItem, SysDictItemMapper> {

    public SysDictItemDao(SysDictItemMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 指定类型下的码值项（按 field_value 升序）
     */
    public List<SysDictItem> selectByTypeId(String fieldTypeId) {
        return getBaseMapper().selectByTypeId(fieldTypeId);
    }

    /**
     * 按类型 + 码值查询码值项（校验同类型下码值唯一性）
     */
    public SysDictItem selectByTypeIdAndValue(String fieldTypeId, String fieldValue) {
        return getBaseMapper().selectByTypeIdAndValue(fieldTypeId, fieldValue);
    }

    /**
     * 通过类型编号获取该类型下已启用的码值项（按 field_value 升序）
     */
    public List<SysDictItemVo> selectItemsByTypeCode(String fieldCode) {
        return getBaseMapper().selectItemsByTypeCode(fieldCode);
    }
}

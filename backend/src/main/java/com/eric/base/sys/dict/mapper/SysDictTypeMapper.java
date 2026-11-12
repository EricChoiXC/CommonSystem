package com.eric.base.sys.dict.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.dict.model.SysDictType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 码值类型 Mapper
 *
 * @author Eric
 * @date 2026/9/2
 * @description 与 SysDictTypeMapper.xml 同名对应，一对一操作 sys_dict_type。
 */
@Mapper
public interface SysDictTypeMapper extends IBaseMapper<SysDictType> {

    /**
     * 全部码值类型（按 field_code 升序），供左侧类型列表展示（前端按关键字过滤）
     */
    List<SysDictType> selectAll();

    /**
     * 按编号查询码值类型
     */
    SysDictType selectByCode(@Param("fieldCode") String fieldCode);
}

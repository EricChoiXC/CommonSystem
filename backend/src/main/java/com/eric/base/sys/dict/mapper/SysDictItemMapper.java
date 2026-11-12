package com.eric.base.sys.dict.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.dict.model.SysDictItem;
import com.eric.base.sys.dict.response.SysDictItemVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 码值项 Mapper
 *
 * @author Eric
 * @date 2026/9/2
 * @description 与 SysDictItemMapper.xml 同名对应，一对一操作 sys_dict_item。
 */
@Mapper
public interface SysDictItemMapper extends IBaseMapper<SysDictItem> {

    /**
     * 指定类型下的码值项（按 field_value 升序），供右侧码值项列表展示
     */
    List<SysDictItem> selectByTypeId(@Param("fieldTypeId") String fieldTypeId);

    /**
     * 按类型 + 码值查询码值项（校验同类型下码值唯一性）
     */
    SysDictItem selectByTypeIdAndValue(@Param("fieldTypeId") String fieldTypeId,
                                       @Param("fieldValue") String fieldValue);

    /**
     * 通过类型编号获取该类型下已启用的码值项（供其他模块/页面快速使用）
     */
    List<SysDictItemVo> selectItemsByTypeCode(@Param("fieldCode") String fieldCode);
}

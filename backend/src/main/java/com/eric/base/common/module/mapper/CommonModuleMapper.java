package com.eric.base.common.module.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.common.module.model.CommonModule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模块组开关状态数据访问映射
 *
 * @author Eric
 * @date 2026/11/12
 * @description 与 common_module_group 一对一对接，不承载业务逻辑。
 */
@Mapper
public interface CommonModuleMapper extends IBaseMapper<CommonModule> {

    /**
     * 全量查询模块组（状态缓存回源与启动同步比对用），按 field_key 排序
     */
    List<CommonModule> selectAll();

    /**
     * 按模块组标识查询（启动同步「不存在→插入」边界与注解校验用）
     */
    CommonModule selectByKey(@Param("fieldKey") String fieldKey);

    /**
     * 按模块组标识批量回写元信息（仅 field_name / field_remark，绝不动 field_enable；
     * 启动同步幂等边界，modules 必须非空，空列表由调用方跳过）
     */
    int batchUpdateMeta(@Param("modules") List<CommonModule> modules);

    /**
     * 按主键更新启用状态（/enable 通道；field_update_time 由数据库 ON UPDATE 自动维护）
     */
    int updateEnable(@Param("fieldId") String fieldId, @Param("fieldEnable") Boolean fieldEnable);
}

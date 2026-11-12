package com.eric.base.common.module.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.common.module.mapper.CommonModuleMapper;
import com.eric.base.common.module.model.CommonModule;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 模块组开关状态数据访问对象
 *
 * @author Eric
 * @date 2026/11/12
 * @description 与 CommonModuleMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class CommonModuleDao extends BaseDao<CommonModule, CommonModuleMapper> {

    public CommonModuleDao(CommonModuleMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 全量查询模块组（状态缓存回源与启动同步比对用），按 field_key 排序
     */
    public List<CommonModule> selectAll() {
        return getBaseMapper().selectAll();
    }

    /**
     * 按模块组标识查询（启动同步「不存在→插入」边界与注解校验用）
     */
    public CommonModule selectByKey(String fieldKey) {
        return getBaseMapper().selectByKey(fieldKey);
    }

    /**
     * 按模块组标识批量回写元信息（仅 field_name / field_remark，绝不动 field_enable；
     * modules 必须非空，空列表由调用方跳过）
     */
    public int batchUpdateMeta(List<CommonModule> modules) {
        return getBaseMapper().batchUpdateMeta(modules);
    }

    /**
     * 按主键更新启用状态（/enable 通道；field_update_time 由数据库自动维护）
     */
    public int updateEnable(String fieldId, Boolean fieldEnable) {
        return getBaseMapper().updateEnable(fieldId, fieldEnable);
    }
}

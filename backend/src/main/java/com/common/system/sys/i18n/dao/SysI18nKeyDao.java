package com.common.system.sys.i18n.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.i18n.mapper.SysI18nKeyMapper;
import com.common.system.sys.i18n.model.SysI18nKey;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 多语言-资源数据访问对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 与 SysI18nKeyMapper 一对一对接，不承载业务逻辑；
 *              key 仅随资源文件扫描产生（D5），无人工维护入口。
 */
@Repository
public class SysI18nKeyDao extends BaseDao<SysI18nKey, SysI18nKeyMapper> {

    public SysI18nKeyDao(SysI18nKeyMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按 (field_model, field_key) 查询资源项
     */
    public SysI18nKey selectByModelAndKey(String fieldModel, String fieldKey) {
        return getBaseMapper().selectByModelAndKey(fieldModel, fieldKey);
    }

    /**
     * 指定模块的全部资源项（field_key 升序）
     */
    public List<SysI18nKey> selectByModel(String fieldModel) {
        return getBaseMapper().selectByModel(fieldModel);
    }

    /**
     * 模块聚合：去重 field_model 列表
     */
    public List<String> selectDistinctModels() {
        return getBaseMapper().selectDistinctModels();
    }

    /**
     * 按 (field_model, field_key) 统计资源项数
     */
    public long countByModelAndKey(String fieldModel, String fieldKey) {
        return getBaseMapper().countByModelAndKey(fieldModel, fieldKey);
    }

    /**
     * 按资源 key 全局查询（key 全局唯一，启动/部署同步拦截跨模块重复登记用）
     */
    public SysI18nKey selectByKey(String fieldKey) {
        return getBaseMapper().selectByKey(fieldKey);
    }

    /**
     * 按资源 key 全局删除（D5：资源文件移除 key 后随同步级联清理登记）
     */
    public int deleteByKey(String fieldKey) {
        return getBaseMapper().deleteByKey(fieldKey);
    }
}

package com.common.system.common.base.dao;

import com.common.system.common.base.model.BaseModel;
import com.common.system.common.base.model.Pages;

import java.util.List;
import java.util.Objects;

/**
 * @author Eric
 * @date 2026/7/31
 * @description
 */
public abstract class BaseDao<T extends BaseModel, M extends IBaseMapper<T>> implements IBaseDao<T> {

    private final M baseMapper;

    protected BaseDao(M baseMapper) {
        this.baseMapper = Objects.requireNonNull(baseMapper, "baseMapper must not be null");
    }

    protected M getBaseMapper() {
        return baseMapper;
    }

    @Override
    public int insert(T model) {
        return baseMapper.insert(model);
    }

    @Override
    public int update(T model) {
        return baseMapper.update(model);
    }

    @Override
    public int deleteById(String id) {
        return baseMapper.deleteById(id);
    }

    @Override
    public T selectById(String id) {
        return baseMapper.selectById(id);
    }

    @Override
    public List<T> selectList(T criteria, Pages page) {
        return baseMapper.selectList(criteria, page);
    }

    @Override
    public T loadModel(String id) {
        return baseMapper.loadModel(id);
    }
}

package com.eric.base.common.base.service;

import com.eric.base.common.base.dao.IBaseDao;
import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.utils.StringUtil;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * @author Eric
 * @date 2026/7/31
 * @description
 */
public abstract class BaseService<T extends BaseModel, D extends IBaseDao<T>> implements IBaseService<T> {

    private final D baseDao;

    protected BaseService(D baseDao) {
        this.baseDao = Objects.requireNonNull(baseDao, "baseDao must not be null");
    }

    protected D getBaseDao() {
        return baseDao;
    }

    @Override
    @Transactional
    public T insert(T model) {
        requireModelId(model);
        requireSingleRow("insert", baseDao.insert(model));
        return model;
    }

    @Override
    @Transactional
    public T update(T model) {
        requireModelId(model);
        requireSingleRow("update", baseDao.update(model));
        return model;
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        String documentId = requireText(id, "id");
        requireSingleRow("delete", baseDao.deleteById(documentId));
    }

    @Override
    public T selectById(String id) {
        String documentId = requireText(id, "id");
        return baseDao.selectById(documentId);
    }

    @Override
    public List<T> selectList(T criteria, Pages pages) {
        return baseDao.selectList(criteria, pages);
    }

    @Override
    public T loadModel(String fieldId) {
        String documentId = requireText(fieldId, "fieldId");
        return baseDao.loadModel(documentId);
    }
    private String requireModelId(T model) {
        Objects.requireNonNull(model, "model must not be null");
        return requireText(model.initFieldId(), "model.fieldId");
    }

    private String requireText(String value, String fieldName) {
        if (StringUtil.isEmpty(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private void requireSingleRow(String operation, int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException(operation + " expected one affected row but got " + affectedRows);
        }
    }
}

package com.eric.base.common.base.service;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.resolver.AuthorizationContext;

import java.util.List;

/**
 * 业务层公共接口
 * @author Eric
 * @date 2026/7/30
 * @description
 */
public interface IBaseService<T extends BaseModel> {

    T insert(T model);

    T update(T model);

    void deleteById(String id);

    T selectById(String id);

    List<T> selectList(T criteria, Pages pages);

    /**
     * 加载轻量模型：仅返回 field_id 与 field_name 两个字段（field_name 的来源由实际表处理），
     * 用于给关联对象（如 SysOrgElement.fieldParent）赋值等只需要 ID 与名称的场景。
     *
     * @param fieldId 文档 ID
     * @return 仅含 fieldId 与 fieldName 的模型；不存在时返回 null
     */
    T loadModel(String fieldId);
}

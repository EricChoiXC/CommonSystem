package com.common.system.common.base.dao;

import com.common.system.common.base.model.BaseModel;
import com.common.system.common.base.model.Pages;

import java.util.List;

/**
 * Dao层公共接口
 * @author Eric
 * @date 2026/7/30
 * @description
 */
public interface IBaseDao<T extends BaseModel> {

    int insert(T model);

    int update(T model);

    int deleteById(String id);

    T selectById(String id);

    List<T> selectList(T criteria, Pages page);

    /**
     * 加载轻量模型：仅返回 field_id 与 field_name 两个字段（field_name 的来源由实际表处理），
     * 用于给关联对象（如 SysOrgElement.fieldParent）赋值等只需要 ID 与名称的场景。
     *
     * @param id 文档 ID
     * @return 仅含 fieldId 与 fieldName 的模型；不存在时返回 null
     */
    T loadModel(String id);
}

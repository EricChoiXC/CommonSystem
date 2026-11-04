package com.common.system.common.base.dao;

import com.common.system.common.base.model.BaseModel;
import com.common.system.common.base.model.Pages;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 基础数据访问接口
 * @author Eric
 * @date 2026/7/30
 * @description
 */
public interface IBaseMapper<T extends BaseModel> {

    int insert(@Param("model") T model);

    int update(@Param("model") T model);

    int deleteById(@Param("fieldId") String fieldId);

    T selectById(@Param("fieldId") String fieldId);

    List<T> selectList(@Param("model") T model, @Param("pages") Pages pages);

    /**
     * 加载轻量模型：仅返回 field_id 与 field_name 两个字段（field_name 的来源由实际表处理），
     * 用于给关联对象（如 SysOrgElement.fieldParent）赋值等只需要 ID 与名称的场景。
     *
     * @param fieldId 文档 ID
     * @return 仅含 fieldId 与 fieldName 的模型；不存在时返回 null
     */
    T loadModel(@Param("fieldId") String fieldId);
}

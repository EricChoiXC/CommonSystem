package com.common.system.sys.process.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.process.model.SysProcessTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程模板 Mapper
 *
 * @author Eric
 * @date 2026/8/22
 * @description 与 sys_process_template 表一对一对接；分页列表支持单条件排序白名单映射。
 */
@Mapper
public interface SysProcessTemplateMapper extends IBaseMapper<SysProcessTemplate> {

    /**
     * 模板列表总数（与 selectListSorted 过滤条件一致，供分页 metadata.total 使用）
     */
    long selectCount(@Param("model") SysProcessTemplate model);

    /**
     * 模板列表分页查询（单条件排序版）：field_model_name 为左侧分类固定条件，
     * 支持模板名称/编号模糊与启用状态筛选；sortField / sortOrder 必须为白名单值，
     * 由 Service 层归一化后传入。
     */
    List<SysProcessTemplate> selectListSorted(@Param("model") SysProcessTemplate model,
                                              @Param("pages") Pages pages,
                                              @Param("sortField") String sortField,
                                              @Param("sortOrder") String sortOrder);

    /**
     * 取消指定分类下所有默认模板（置为默认=替换语义：先清旧再设新，事务内保证唯一）。
     *
     * @param modelName 模板分类（field_model_name）
     * @return 受影响行数
     */
    int clearDefaultByModelName(@Param("modelName") String modelName);

    /**
     * 取指定分类的默认启用模板（field_default=1 且 field_enable=1；流程启动未指定模板时使用）。
     *
     * @param modelName 模板分类（field_model_name）
     * @return 默认模板；分类下无默认或默认已停用时返回 null
     */
    SysProcessTemplate selectDefaultByModelName(@Param("modelName") String modelName);
}

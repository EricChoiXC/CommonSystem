package com.common.system.sys.process.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.process.mapper.SysProcessTemplateMapper;
import com.common.system.sys.process.model.SysProcessTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程模板数据访问对象
 *
 * @author Eric
 * @date 2026/8/22
 * @description 与 SysProcessTemplateMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysProcessTemplateDao extends BaseDao<SysProcessTemplate, SysProcessTemplateMapper> {

    public SysProcessTemplateDao(SysProcessTemplateMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 模板列表总数（与 selectListSorted 过滤条件一致，供分页 metadata.total 使用）
     */
    public long selectCount(SysProcessTemplate model) {
        return getBaseMapper().selectCount(model);
    }

    /**
     * 模板列表分页查询（单条件排序版）：sortField / sortOrder 由 Service 层归一化后传入
     */
    public List<SysProcessTemplate> selectListSorted(SysProcessTemplate model, Pages pages,
                                                     String sortField, String sortOrder) {
        return getBaseMapper().selectListSorted(model, pages, sortField, sortOrder);
    }

    /**
     * 取消指定分类下所有默认模板（置为默认=替换语义）。
     */
    public int clearDefaultByModelName(String modelName) {
        return getBaseMapper().clearDefaultByModelName(modelName);
    }

    /**
     * 取指定分类的默认启用模板（流程启动未指定模板时使用；无默认返回 null）。
     */
    public SysProcessTemplate selectDefaultByModelName(String modelName) {
        return getBaseMapper().selectDefaultByModelName(modelName);
    }
}

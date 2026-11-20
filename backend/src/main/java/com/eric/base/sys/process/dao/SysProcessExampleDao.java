package com.eric.base.sys.process.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.process.mapper.SysProcessExampleMapper;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.request.SysProcessExampleQuery;
import com.eric.base.sys.process.response.SysProcessExampleVo;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程实例数据访问对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 与 SysProcessExampleMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysProcessExampleDao extends BaseDao<SysProcessExample, SysProcessExampleMapper> {

    public SysProcessExampleDao(SysProcessExampleMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 流程实例列表总数（与 selectListSorted 过滤条件一致，供分页 metadata.total 使用）；
     * myUserId 由 Service 层按数据范围 scope 从会话注入（admin/缺省为 null）
     */
    public long selectCount(SysProcessExampleQuery query, String myUserId) {
        return getBaseMapper().selectCount(query, myUserId);
    }

    /**
     * 流程实例列表分页查询（单条件排序版）：sortField / sortOrder 由 Service 层归一化后传入，
     * myUserId 由 Service 层按数据范围 scope 从会话注入（Mapper 不读会话）
     */
    public List<SysProcessExampleVo> selectListSorted(SysProcessExampleQuery query, Pages pages,
                                                      String sortField, String sortOrder, String myUserId) {
        return getBaseMapper().selectListSorted(query, pages, sortField, sortOrder, myUserId);
    }

    /**
     * 流程监控列表分页查询（v1_7 P17-BE-015 新增，仅管理员全量视图，不做本人数据过滤）：
     * sortField / sortOrder 由 Service 层归一化后传入
     */
    public List<SysProcessExampleVo> selectMonitorList(SysProcessExampleQuery query, Pages pages,
                                                       String sortField, String sortOrder) {
        return getBaseMapper().selectMonitorList(query, pages, sortField, sortOrder);
    }

    /**
     * 流程监控列表总数（与 selectMonitorList 过滤条件一致，供分页 metadata.total 使用）
     */
    public long selectMonitorCount(SysProcessExampleQuery query) {
        return getBaseMapper().selectMonitorCount(query);
    }

    /**
     * 按所属业务文档查询最近一条流程实例（field_model_name + field_model_id），
     * 供业务模块详情页反查流程实例 id 后嵌入审批面板（如项目/项目变更）。
     */
    public SysProcessExample selectByModelId(String modelName, String modelId) {
        return getBaseMapper().selectByModelId(modelName, modelId);
    }
}

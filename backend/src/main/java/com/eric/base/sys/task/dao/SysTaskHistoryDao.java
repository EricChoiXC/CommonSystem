package com.eric.base.sys.task.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.task.mapper.SysTaskHistoryMapper;
import com.eric.base.sys.task.model.SysTaskHistory;
import com.eric.base.sys.task.request.SysTaskHistoryQuery;
import com.eric.base.sys.task.response.SysTaskHistoryVo;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 任务执行历史数据访问对象
 *
 * @author Eric
 * @date 2026/8/15
 * @description 与 SysTaskHistoryMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysTaskHistoryDao extends BaseDao<SysTaskHistory, SysTaskHistoryMapper> {

    public SysTaskHistoryDao(SysTaskHistoryMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 执行记录分页列表（JOIN sys_task_main 取任务名）
     */
    public List<SysTaskHistoryVo> selectHistoryList(SysTaskHistoryQuery model, Pages pages,
                                                    String sortField, String sortOrder) {
        return getBaseMapper().selectHistoryList(model, pages, sortField, sortOrder);
    }

    /**
     * 执行记录列表总数（与 selectHistoryList 过滤条件一致，供分页 metadata.total 使用）
     */
    public long selectHistoryCount(SysTaskHistoryQuery model) {
        return getBaseMapper().selectHistoryCount(model);
    }

    /**
     * 执行记录详情（JOIN sys_task_main 取任务名）
     */
    public SysTaskHistoryVo selectHistoryById(String fieldId) {
        return getBaseMapper().selectHistoryById(fieldId);
    }
}

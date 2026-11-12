package com.eric.base.sys.task.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.task.model.SysTaskHistory;
import com.eric.base.sys.task.request.SysTaskHistoryQuery;
import com.eric.base.sys.task.response.SysTaskHistoryVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务执行历史数据访问映射
 *
 * @author Eric
 * @date 2026/8/15
 * @description 与 sys_task_history 一对一对接，不承载业务逻辑；
 *              列表/详情 JOIN sys_task_main 组装任务名。
 */
@Mapper
public interface SysTaskHistoryMapper extends IBaseMapper<SysTaskHistory> {

    /**
     * 执行记录分页列表（JOIN sys_task_main 取任务名，排序字段白名单）
     */
    List<SysTaskHistoryVo> selectHistoryList(@Param("model") SysTaskHistoryQuery model,
                                             @Param("pages") Pages pages,
                                             @Param("sortField") String sortField,
                                             @Param("sortOrder") String sortOrder);

    /**
     * 执行记录列表总数（与 selectHistoryList 过滤条件一致）
     */
    long selectHistoryCount(@Param("model") SysTaskHistoryQuery model);

    /**
     * 执行记录详情（JOIN sys_task_main 取任务名）
     */
    SysTaskHistoryVo selectHistoryById(@Param("fieldId") String fieldId);
}

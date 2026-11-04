package com.common.system.sys.task.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.base.service.BaseService;
import com.common.system.common.utils.StringUtil;
import com.common.system.sys.task.dao.SysTaskHistoryDao;
import com.common.system.sys.task.model.SysTaskHistory;
import com.common.system.sys.task.request.SysTaskHistoryQuery;
import com.common.system.sys.task.response.SysTaskHistoryVo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务执行记录业务服务
 *
 * @author Eric
 * @date 2026/8/15
 * @description 承载「执行记录」页面的查询：任务名称、结果、开始时间（范围）、结束时间（范围）
 *              筛选；排序仅开始时间 / 结束时间两个维度；详情只读，历史为只增流水。
 */
@Service
public class SysTaskHistoryService extends BaseService<SysTaskHistory, SysTaskHistoryDao> {

    public SysTaskHistoryService(SysTaskHistoryDao baseDao) {
        super(baseDao);
    }

    /**
     * 分页查询执行记录（JOIN sys_task_main 组装任务名）
     *
     * @param sortField 排序字段：startTime / endTime（白名单，默认 startTime）
     * @param sortOrder 排序方向：asc / desc（默认 desc）
     */
    public List<SysTaskHistoryVo> listHistory(SysTaskHistoryQuery query, Pages pages,
                                              String sortField, String sortOrder) {
        if (null == pages) {
            pages = new Pages();
        }
        if (pages.getPageNo() < 1) {
            pages.setPageNo(1);
        }
        if (pages.getPageSize() < 1) {
            pages.setPageSize(10);
        }
        return getBaseDao().selectHistoryList(query, pages,
                normalizeSortField(sortField), normalizeSortOrder(sortOrder));
    }

    /**
     * 统计执行记录总数（与 {@link #listHistory} 条件一致，供分页 metadata.total 使用）
     */
    public long countHistory(SysTaskHistoryQuery query) {
        return getBaseDao().selectHistoryCount(query);
    }

    /**
     * 执行记录详情（JOIN sys_task_main 组装任务名）
     */
    public SysTaskHistoryVo detail(String fieldId) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId 不能为空");
        }
        return getBaseDao().selectHistoryById(fieldId);
    }

    /**
     * 排序字段白名单：startTime / endTime，非法值回退默认 startTime
     */
    private static String normalizeSortField(String sortField) {
        return "endTime".equals(sortField) ? "endTime" : "startTime";
    }

    /**
     * 排序方向白名单：asc / desc，非法值回退默认降序
     */
    private static String normalizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }
}

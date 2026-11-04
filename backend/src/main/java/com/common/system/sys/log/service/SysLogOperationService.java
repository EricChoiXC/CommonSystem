package com.common.system.sys.log.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.base.service.BaseService;
import com.common.system.common.utils.StringUtil;
import com.common.system.sys.log.dao.SysLogOperationDao;
import com.common.system.sys.log.enums.SysLogType;
import com.common.system.sys.log.model.SysLogOperation;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 日志查询业务服务
 *
 * @author Eric
 * @date 2026/8/8
 * @description 承载后台日志管理查询（操作日志 / 登录日志分页查询与详情），
 *              日志为只增流水，本服务不暴露更新 / 删除能力。
 */
@Service
public class SysLogOperationService extends BaseService<SysLogOperation, SysLogOperationDao> {

    public SysLogOperationService(SysLogOperationDao baseDao) {
        super(baseDao);
    }

    /**
     * 分页查询日志：field_log_type 必填且必须是合法值（1=操作日志 2=登录日志）；
     * 支持模块、动作、操作人（姓名/登录名）、业务文档 ID、结果、操作时间范围筛选；
     * 按操作时间排序（方向白名单）。
     */
    public List<SysLogOperation> listLog(SysLogOperation criteria, Pages pages, String sortOrder,
                                         String operateTimeStart, String operateTimeEnd) {
        requireValidLogType(criteria);
        if (null == pages) {
            pages = new Pages();
        }
        if (pages.getPageNo() < 1) {
            pages.setPageNo(1);
        }
        if (pages.getPageSize() < 1) {
            pages.setPageSize(10);
        }
        return getBaseDao().selectLogList(criteria, pages, normalizeSortOrder(sortOrder),
                operateTimeStart, operateTimeEnd);
    }

    /**
     * 统计日志总数（与 {@link #listLog} 条件一致，供分页 metadata.total 使用）
     */
    public long countLog(SysLogOperation criteria, String operateTimeStart, String operateTimeEnd) {
        if (null == criteria || StringUtil.isEmpty(criteria.getFieldLogType())
                || null == SysLogType.of(criteria.getFieldLogType())) {
            return 0;
        }
        return getBaseDao().selectLogCount(criteria, operateTimeStart, operateTimeEnd);
    }

    private void requireValidLogType(SysLogOperation criteria) {
        if (null == criteria || StringUtil.isEmpty(criteria.getFieldLogType())
                || null == SysLogType.of(criteria.getFieldLogType())) {
            throw new IllegalArgumentException("fieldLogType must be 1(operation) or 2(login)");
        }
    }

    /**
     * 排序方向白名单：asc / desc，非法值回退默认降序
     */
    private static String normalizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }
}

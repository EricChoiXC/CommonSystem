package com.eric.base.sys.att.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.base.service.BaseService;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.att.dao.SysAttOperationDao;
import com.eric.base.sys.att.model.SysAttOperation;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 附件操作日志业务服务
 *
 * @author Eric
 * @date 2026/8/7
 * @description 承载附件操作日志查询（后台附件列表跳转查看）。
 */
@Service
public class SysAttOperationService extends BaseService<SysAttOperation, SysAttOperationDao> {

    public SysAttOperationService(SysAttOperationDao baseDao) {
        super(baseDao);
    }

    /**
     * 按附件 ID 查询操作日志，field_att_id 必填，按操作时间排序（方向白名单）。
     */
    public List<SysAttOperation> listByAttId(SysAttOperation criteria, Pages pages, String sortOrder) {
        if (null == criteria || StringUtil.isEmpty(criteria.getFieldAttId())) {
            throw new IllegalArgumentException("fieldAttId must not be blank");
        }
        if (null == pages) {
            pages = new Pages();
        }
        if (pages.getPageNo() < 1) {
            pages.setPageNo(1);
        }
        if (pages.getPageSize() < 1) {
            pages.setPageSize(10);
        }
        return getBaseDao().selectListByAttId(criteria, pages, normalizeSortOrder(sortOrder));
    }

    /**
     * 按附件 ID 统计操作日志总数（与 {@link #listByAttId} 条件一致，供分页 metadata.total 使用）
     */
    public long countByAttId(SysAttOperation criteria) {
        if (null == criteria || StringUtil.isEmpty(criteria.getFieldAttId())) {
            return 0;
        }
        return getBaseDao().selectCountByAttId(criteria);
    }

    /**
     * 排序方向白名单：asc / desc，非法值回退默认降序。
     */
    private static String normalizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }
}

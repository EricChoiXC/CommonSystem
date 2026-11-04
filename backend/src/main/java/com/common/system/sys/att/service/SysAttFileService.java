package com.common.system.sys.att.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.base.service.BaseService;
import com.common.system.sys.att.dao.SysAttFileDao;
import com.common.system.sys.att.model.SysAttFile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 附件文件表业务服务
 *
 * @author Eric
 * @date 2026/8/7
 * @description 文件列表查询等基础能力，业务逻辑承载于 SysAttMainService。
 */
@Service
public class SysAttFileService extends BaseService<SysAttFile, SysAttFileDao> {

    public SysAttFileService(SysAttFileDao baseDao) {
        super(baseDao);
    }

    /**
     * 文件列表分页查询（后台管理）：支持单条件排序
     * （createTime / fileSize，白名单映射）与分页参数兜底。
     */
    public List<SysAttFile> list(SysAttFile criteria, Pages pages, String sortField, String sortOrder) {
        return getBaseDao().selectListSorted(null == criteria ? new SysAttFile() : criteria,
                normalizePages(pages), normalizeSortField(sortField), normalizeSortOrder(sortOrder));
    }

    /**
     * 文件列表总数（与 {@link #list} 过滤条件一致，供分页 metadata.total 使用）
     */
    public long count(SysAttFile criteria) {
        return getBaseDao().selectCount(null == criteria ? new SysAttFile() : criteria);
    }

    /**
     * 分页参数兜底：null 视为不分页（needPage=false）；
     * pageNo / pageSize 小于 1 时归位到合法最小值，避免 SQL 出现负 OFFSET / 非法 LIMIT。
     */
    private static Pages normalizePages(Pages pages) {
        Pages safe = null == pages ? new Pages() : pages;
        if (safe.getPageNo() < 1) {
            safe.setPageNo(1);
        }
        if (safe.getPageSize() < 1) {
            safe.setPageSize(10);
        }
        return safe;
    }

    /**
     * 排序字段白名单：createTime / fileSize，非法值回退默认创建时间。
     */
    private static String normalizeSortField(String sortField) {
        return "fileSize".equals(sortField) ? sortField : "createTime";
    }

    /**
     * 排序方向白名单：asc / desc，非法值回退默认降序。
     */
    private static String normalizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }
}

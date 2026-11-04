package com.common.system.sys.org.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.utils.StringUtil;
import com.common.system.sys.org.dao.SysOrgAddressBookDao;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.request.SysOrgAddressBookQuery;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 地址本业务服务
 *
 * @author Eric
 * @date 2026/8/9
 * @description 地址本选择组件的查询业务：树查询（根级/下级）、关键字分页列表与总数、目标回填。
 *              统一做入参兜底（null 视为不限制/不分页），并承担 limitRange 归一化语义。
 */
@Service
public class SysOrgAddressBookService {

    private final SysOrgAddressBookDao addressBookDao;

    public SysOrgAddressBookService(SysOrgAddressBookDao addressBookDao) {
        this.addressBookDao = addressBookDao;
    }

    /**
     * 树查询：左侧树展开与右侧下级列表共用。
     * <p>parentId 为空时返回根级（limitRange 非空时以范围节点为根），
     * 否则返回以该节点为上级的直接下级列表；结果受 orgTypes / limitRange 过滤。</p>
     */
    public List<SysOrgElement> queryTree(SysOrgAddressBookQuery query) {
        if (null == query) {
            return List.of();
        }
        return addressBookDao.selectTree(
                normalizeOrgTypes(query.getOrgTypes()),
                normalizeLimitRange(query.getLimitRange()),
                query.getParentId());
    }

    /**
     * 查询页签：仅根据关键字做列表分页查询，结果受 orgTypes / limitRange 过滤。
     */
    public List<SysOrgElement> queryList(SysOrgAddressBookQuery query, Pages pages) {
        if (null == query) {
            return List.of();
        }
        return addressBookDao.selectList(
                trimToNull(query.getKeyword()),
                normalizeOrgTypes(query.getOrgTypes()),
                normalizeLimitRange(query.getLimitRange()),
                normalizePages(pages));
    }

    /**
     * 查询页签总数：与 {@link #queryList} 过滤条件一致，供分页 metadata.total 使用。
     */
    public long countList(SysOrgAddressBookQuery query) {
        if (null == query) {
            return 0;
        }
        return addressBookDao.countList(
                trimToNull(query.getKeyword()),
                normalizeOrgTypes(query.getOrgTypes()),
                normalizeLimitRange(query.getLimitRange()));
    }

    /**
     * 目标回填：根据 id 列表读取名称等展示信息。
     * <p>空列表直接返回空结果；id 去空白、去重后查询，避免脏参数。</p>
     */
    public List<SysOrgElement> loadByIds(List<String> ids) {
        if (null == ids || ids.isEmpty()) {
            return List.of();
        }
        List<String> distinctIds = ids.stream()
                .filter(StringUtil::isNotEmpty)
                .map(String::trim)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        return addressBookDao.selectByIds(distinctIds);
    }

    private static String trimToNull(String value) {
        return StringUtil.isEmpty(value) ? null : value.trim();
    }

    private static List<Integer> normalizeOrgTypes(List<Integer> orgTypes) {
        return null == orgTypes || orgTypes.isEmpty() ? null : orgTypes;
    }

    private static List<String> normalizeLimitRange(List<String> limitRange) {
        return null == limitRange || limitRange.isEmpty() ? null : limitRange;
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
}

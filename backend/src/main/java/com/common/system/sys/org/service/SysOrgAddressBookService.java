package com.common.system.sys.org.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.utils.StringUtil;
import com.common.system.sys.org.dao.SysOrgAddressBookDao;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.request.SysOrgAddressBookQuery;
import com.common.system.sys.org.request.SysOrgGroupQuery;
import com.common.system.sys.org.response.SysOrgGroupVo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 地址本业务服务
 *
 * @author Eric
 * @date 2026/8/9
 * @description 地址本选择组件的查询业务：树查询（根级/下级）、关键字分页列表与总数、目标回填，
 *              以及群组页签两端点（groupList / groupUsers，ORG2-BE-004）：
 *              groupList 将 body.keyword 转 {@link SysOrgGroupQuery#setFieldName}（群组名称模糊匹配）
 *              后复用 {@link SysOrgGroupService} 的可见性过滤分页（02-群组功能分析 2.2）；
 *              groupUsers 委托群组服务完成存在性/可见性校验（不可见 FORBIDDEN「无权查看该群组」）
 *              与成员明细查询（Spring 单例互注入按仓库先例）。
 *              统一做入参兜底（null 视为不限制/不分页），并承担 limitRange 归一化语义。
 */
@Service
public class SysOrgAddressBookService {

    private final SysOrgAddressBookDao addressBookDao;

    private final SysOrgGroupService groupService;

    public SysOrgAddressBookService(SysOrgAddressBookDao addressBookDao, SysOrgGroupService groupService) {
        this.addressBookDao = addressBookDao;
        this.groupService = groupService;
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

    /* ==================== 群组页签（sys.org.addressbook.groupList / groupUsers，ORG2-BE-004） ==================== */

    /**
     * 地址本「群组」页签左侧列表（04-API改动清单 2）：请求体 { body: { keyword }, pages }，
     * body.keyword 转 {@link SysOrgGroupQuery#setFieldName}（群组名称模糊匹配）后，复用
     * {@link SysOrgGroupService#groupList} 的可见性过滤分页（02 文档 2.2：管理员全部可见，
     * 普通用户仅可见自己的个人群组与授权可见的公共群组），固定创建时间降序；
     * 总数由调用方经 {@link #countGroupList} 以同一条件取得。
     */
    public List<SysOrgGroupVo> groupList(SysOrgAddressBookQuery query, Pages pages) {
        if (null == query) {
            return List.of();
        }
        return groupService.groupList(toGroupQuery(query), pages);
    }

    /**
     * 地址本群组列表总数：与 {@link #groupList} 同口径转换（keyword→fieldName），
     * 供分页 metadata.total 使用。
     */
    public long countGroupList(SysOrgAddressBookQuery query) {
        if (null == query) {
            return 0;
        }
        return groupService.count(toGroupQuery(query));
    }

    /**
     * 地址本「群组」页签右侧成员人员列表（04-API改动清单 2）：委托
     * {@link SysOrgGroupService#groupUsers}——群组不存在 E_NOT_FOUND「未找到该群组」、
     * 不可见 FORBIDDEN「无权查看该群组」、orgTypes 求交（null/空默认 [3] 仅人员）
     * 与成员明细查询均在群组服务完成。
     */
    public List<SysOrgElement> groupUsers(String groupId, List<Integer> orgTypes) {
        return groupService.groupUsers(groupId, orgTypes);
    }

    /**
     * 地址本查询条件转群组查询条件：keyword → fieldName（群组名称模糊匹配），
     * orgTypes / limitRange / parentId 为地址本树形页签语义，群组页签不使用。
     */
    private static SysOrgGroupQuery toGroupQuery(SysOrgAddressBookQuery query) {
        SysOrgGroupQuery groupQuery = new SysOrgGroupQuery();
        groupQuery.setFieldName(StringUtil.trimToNull(query.getKeyword()));
        return groupQuery;
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

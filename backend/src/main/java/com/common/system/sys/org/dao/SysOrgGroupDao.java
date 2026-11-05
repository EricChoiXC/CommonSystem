package com.common.system.sys.org.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.org.mapper.SysOrgGroupMapper;
import com.common.system.sys.org.model.SysOrgGroup;
import com.common.system.sys.org.request.SysOrgGroupQuery;
import com.common.system.sys.org.response.SysOrgGroupVo;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 群组数据访问对象
 *
 * @author Eric
 * @date 2026/11/4
 * @description 与 SysOrgGroupMapper 一对一对接，不承载业务逻辑；
 *              两张明细表的语句同收敛于 SysOrgGroupMapper（参照 SysOrgRoleMapper 承载
 *              sys_org_role_element / sys_org_role_auth 的先例），
 *              由 {@link SysOrgGroupUserDao} / {@link SysOrgGroupViewerDao} 分职责暴露。
 */
@Repository
public class SysOrgGroupDao extends BaseDao<SysOrgGroup, SysOrgGroupMapper> {

    public SysOrgGroupDao(SysOrgGroupMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 群组列表分页查询（含可见性过滤、单条件排序与分页；
     * 可见性入参 userId / admin 由 query 携带，Service 按当前登录人与角色填充）
     */
    public List<SysOrgGroupVo> selectVoList(SysOrgGroupQuery query, Pages pages,
                                            String sortField, String sortOrder) {
        return getBaseMapper().selectVoList(query, pages, sortField, sortOrder);
    }

    /**
     * 群组列表总数（与 {@link #selectVoList} 过滤条件一致，供分页 metadata.total 使用）
     */
    public long countVoList(SysOrgGroupQuery query) {
        return getBaseMapper().countVoList(query);
    }

    /**
     * 群组详情（基本字段 + 所有人名称 + 成员数；明细由成员/可见范围 Dao 组装）
     */
    public SysOrgGroupVo selectVoById(String fieldId) {
        return getBaseMapper().selectVoById(fieldId);
    }

    /**
     * 按 id 统计对指定用户可见的群组数（单条可见性「可见才可看」校验用：
     * load / update / delete 的前置校验与地址本 groupUsers（ORG2-BE-004）同走本门面，
     * 见 02-群组功能分析 2.2/2.3 与 04-API改动清单 2）：可见性口径与
     * {@link #selectVoList} 的 VisibleClause 完全一致；可见返回 1，不可见或不存在返回 0。
     * userId / admin 由 query 携带，Service 按当前登录人与角色填充。
     */
    public long selectCountVisibleById(String groupId, SysOrgGroupQuery query) {
        return getBaseMapper().selectCountVisibleById(groupId, query);
    }
}

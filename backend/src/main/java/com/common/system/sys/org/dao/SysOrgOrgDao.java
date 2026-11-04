package com.common.system.sys.org.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.org.mapper.SysOrgOrgMapper;
import com.common.system.sys.org.model.SysOrgOrg;
import org.springframework.stereotype.Repository;

/**
 * 机构数据访问对象
 *
 * @author Eric
 * @date 2026/8/10
 * @description 与 SysOrgOrgMapper 一对一对接，不承载业务逻辑；
 *              仅维护机构扩展表(sys_org_org)，公共字段由组织架构主表(sys_org_element)承载。
 */
@Repository
public class SysOrgOrgDao extends BaseDao<SysOrgOrg, SysOrgOrgMapper> {

    public SysOrgOrgDao(SysOrgOrgMapper baseMapper) {
        super(baseMapper);
    }
}

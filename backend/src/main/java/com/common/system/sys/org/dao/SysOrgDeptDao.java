package com.common.system.sys.org.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.org.mapper.SysOrgDeptMapper;
import com.common.system.sys.org.model.SysOrgDept;
import org.springframework.stereotype.Repository;

/**
 * 部门数据访问对象
 *
 * @author Eric
 * @date 2026/8/10
 * @description 与 SysOrgDeptMapper 一对一对接，不承载业务逻辑；
 *              仅维护部门扩展表(sys_org_dept)，公共字段由组织架构主表(sys_org_element)承载。
 */
@Repository
public class SysOrgDeptDao extends BaseDao<SysOrgDept, SysOrgDeptMapper> {

    public SysOrgDeptDao(SysOrgDeptMapper baseMapper) {
        super(baseMapper);
    }
}

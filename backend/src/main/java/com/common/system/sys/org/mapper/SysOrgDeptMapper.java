package com.common.system.sys.org.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.sys.org.model.SysOrgDept;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门数据访问接口
 *
 * @author Eric
 * @date 2026/8/5
 * @description 部门表(sys_org_dept)数据访问
 */
@Mapper
public interface SysOrgDeptMapper extends IBaseMapper<SysOrgDept> {
}

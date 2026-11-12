package com.eric.base.sys.org.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.org.model.SysOrgDept;
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

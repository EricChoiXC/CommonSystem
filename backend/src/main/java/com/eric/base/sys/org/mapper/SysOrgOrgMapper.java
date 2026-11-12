package com.eric.base.sys.org.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.org.model.SysOrgOrg;
import org.apache.ibatis.annotations.Mapper;

/**
 * 机构数据访问接口
 *
 * @author Eric
 * @date 2026/8/5
 * @description 机构表(sys_org_org)数据访问
 */
@Mapper
public interface SysOrgOrgMapper extends IBaseMapper<SysOrgOrg> {
}

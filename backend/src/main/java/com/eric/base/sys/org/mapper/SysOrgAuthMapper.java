package com.eric.base.sys.org.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.org.model.SysOrgAuth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限数据访问接口
 *
 * @author Eric
 * @date 2026/8/5
 * @description 权限表(sys_org_auth)数据访问
 */
@Mapper
public interface SysOrgAuthMapper extends IBaseMapper<SysOrgAuth> {

    /**
     * 按权限编码精确查询（用于权限增量同步判断是否已存在）
     */
    SysOrgAuth selectByCode(@Param("fieldCode") String fieldCode);

    /**
     * 按用户查询其权限编码列表（field_code），供登录等场景组装；
     * 权限计算模型与 SysOrgRoleMapper.selectListByUserId 一致。
     */
    List<String> selectCodesByUserId(@Param("userId") String userId);

    /* ==================== 角色管理 / 权限预览（sys.org.role / sys.org.preview） ==================== */

    /**
     * 按角色查询其直接绑定的权限列表（权限树角色节点展开用）
     */
    List<SysOrgAuth> selectAuthsByRoleId(@Param("roleId") String roleId);

    /**
     * 按角色 ID 集合查询权限并集（权限预览「权限列表」用，去重）
     */
    List<SysOrgAuth> selectAuthsByRoleIds(@Param("roleIds") List<String> roleIds);
}

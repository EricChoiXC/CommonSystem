package com.common.system.sys.org.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.org.model.SysOrgRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author Eric
 * @date 2026/8/3
 * @description
 */
@Mapper
public interface SysOrgRoleMapper extends IBaseMapper<SysOrgRole> {

    List<SysOrgRole> selectListByUserId(@Param("userId") String userId);

    /**
     * 按用户查询其角色（轻量：仅 field_id、field_name），供登录等场景组装；
     * 角色计算模型与 selectListByUserId 完全一致。
     */
    List<SysOrgRole> selectIdNameListByUserId(@Param("userId") String userId);

    /**
     * 统计角色-权限关联是否存在（启动任务幂等判断用）
     */
    int countRoleAuth(@Param("authId") String authId, @Param("roleId") String roleId);

    /**
     * 插入角色-权限关联（sys_org_role_auth）
     */
    int insertRoleAuth(@Param("authId") String authId, @Param("roleId") String roleId);

    /**
     * 统计角色-组织元素关联是否存在（启动任务幂等判断用）
     */
    int countRoleElement(@Param("elementId") String elementId, @Param("roleId") String roleId);

    /**
     * 插入角色-组织元素关联（sys_org_role_element）
     */
    int insertRoleElement(@Param("elementId") String elementId, @Param("roleId") String roleId);

    /* ==================== 角色管理 / 权限预览（sys.org.role / sys.org.preview） ==================== */

    /**
     * 按角色查询其关联的组织元素 ID 列表（sys_org_role_element，编辑回显用）
     */
    List<String> selectElementIdsByRoleId(@Param("roleId") String roleId);

    /**
     * 按角色查询其关联的权限 ID 列表（sys_org_role_auth，编辑回显用）
     */
    List<String> selectAuthIdsByRoleId(@Param("roleId") String roleId);

    /**
     * 删除角色-组织元素关联（编辑时全量替换前清空）
     */
    int deleteRoleElements(@Param("roleId") String roleId);

    /**
     * 删除角色-权限关联（编辑时全量替换前清空）
     */
    int deleteRoleAuths(@Param("roleId") String roleId);

    /**
     * 批量插入角色-组织元素关联
     */
    int insertRoleElements(@Param("roleId") String roleId, @Param("elementIds") List<String> elementIds);

    /**
     * 批量插入角色-权限关联
     */
    int insertRoleAuths(@Param("roleId") String roleId, @Param("authIds") List<String> authIds);

    /**
     * 按组织元素查询其直接关联的角色列表（权限预览用，含完整字段）
     */
    List<SysOrgRole> selectListByElementId(@Param("elementId") String elementId);

    /**
     * 角色列表总数（与 selectList 过滤条件一致，供分页 metadata.total 使用）
     */
    long selectCount(@Param("model") SysOrgRole model);

    /**
     * 角色列表分页查询（单条件排序版）：与 selectList 过滤条件一致，
     * sortField / sortOrder 必须为白名单值（见 SysOrgRoleProperties），由 Service 层归一化后传入。
     */
    List<SysOrgRole> selectListSorted(@Param("model") SysOrgRole model,
                                      @Param("pages") Pages pages,
                                      @Param("sortField") String sortField,
                                      @Param("sortOrder") String sortOrder);
}

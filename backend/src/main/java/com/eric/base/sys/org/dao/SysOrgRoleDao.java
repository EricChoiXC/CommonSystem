package com.eric.base.sys.org.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.org.mapper.SysOrgRoleMapper;
import com.eric.base.sys.org.model.SysOrgRole;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色数据访问对象
 *
 * @author Eric
 * @date 2026/8/7
 * @description 与 SysOrgRoleMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysOrgRoleDao extends BaseDao<SysOrgRole, SysOrgRoleMapper> {

    public SysOrgRoleDao(SysOrgRoleMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 统计角色-权限关联（sys_org_role_auth）是否存在
     */
    public int countRoleAuth(String authId, String roleId) {
        return getBaseMapper().countRoleAuth(authId, roleId);
    }

    /**
     * 插入角色-权限关联（sys_org_role_auth）
     */
    public int insertRoleAuth(String authId, String roleId) {
        return getBaseMapper().insertRoleAuth(authId, roleId);
    }

    /**
     * 统计角色-组织元素关联（sys_org_role_element）是否存在
     */
    public int countRoleElement(String elementId, String roleId) {
        return getBaseMapper().countRoleElement(elementId, roleId);
    }

    /**
     * 插入角色-组织元素关联（sys_org_role_element）
     */
    public int insertRoleElement(String elementId, String roleId) {
        return getBaseMapper().insertRoleElement(elementId, roleId);
    }

    /**
     * 按用户查询其角色（仅 field_id、field_name，登录等场景组装用）
     */
    public List<SysOrgRole> selectIdNameListByUserId(String userId) {
        return getBaseMapper().selectIdNameListByUserId(userId);
    }

    /**
     * 按用户查询其角色（含完整字段），与登录权限计算模型一致：
     * 用户自身组织元素、所属岗位、所属虚拟组织、直属机构/部门链路关联角色的并集。
     */
    public List<SysOrgRole> selectListByUserId(String userId) {
        return getBaseMapper().selectListByUserId(userId);
    }

    /* ==================== 角色管理 / 权限预览（sys.org.role / sys.org.preview） ==================== */

    /**
     * 按角色查询其关联的组织元素 ID 列表（编辑回显用）
     */
    public List<String> selectElementIdsByRoleId(String roleId) {
        return getBaseMapper().selectElementIdsByRoleId(roleId);
    }

    /**
     * 按角色查询其关联的权限 ID 列表（编辑回显用）
     */
    public List<String> selectAuthIdsByRoleId(String roleId) {
        return getBaseMapper().selectAuthIdsByRoleId(roleId);
    }

    /**
     * 删除角色-组织元素关联（编辑时全量替换前清空）
     */
    public int deleteRoleElements(String roleId) {
        return getBaseMapper().deleteRoleElements(roleId);
    }

    /**
     * 删除角色-权限关联（编辑时全量替换前清空）
     */
    public int deleteRoleAuths(String roleId) {
        return getBaseMapper().deleteRoleAuths(roleId);
    }

    /**
     * 批量插入角色-组织元素关联
     */
    public int insertRoleElements(String roleId, List<String> elementIds) {
        return getBaseMapper().insertRoleElements(roleId, elementIds);
    }

    /**
     * 批量插入角色-权限关联
     */
    public int insertRoleAuths(String roleId, List<String> authIds) {
        return getBaseMapper().insertRoleAuths(roleId, authIds);
    }

    /**
     * 按组织元素查询其直接关联的角色列表（权限预览用，含完整字段）
     */
    public List<SysOrgRole> selectListByElementId(String elementId) {
        return getBaseMapper().selectListByElementId(elementId);
    }

    /**
     * 角色列表总数（与 selectList 过滤条件一致，供分页 metadata.total 使用）
     */
    public long selectCount(SysOrgRole model) {
        return getBaseMapper().selectCount(model);
    }

    /**
     * 角色列表分页查询（单条件排序版）：sortField / sortOrder 由 Service 层归一化后传入
     */
    public List<SysOrgRole> selectListSorted(SysOrgRole model, Pages pages,
                                             String sortField, String sortOrder) {
        return getBaseMapper().selectListSorted(model, pages, sortField, sortOrder);
    }
}

package com.common.system.sys.org.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.org.mapper.SysOrgAuthMapper;
import com.common.system.sys.org.model.SysOrgAuth;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 权限数据访问对象
 *
 * @author Eric
 * @date 2026/8/7
 * @description 与 SysOrgAuthMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysOrgAuthDao extends BaseDao<SysOrgAuth, SysOrgAuthMapper> {

    public SysOrgAuthDao(SysOrgAuthMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按权限编码精确查询，不存在时返回 null
     */
    public SysOrgAuth selectByCode(String fieldCode) {
        return getBaseMapper().selectByCode(fieldCode);
    }

    /**
     * 按用户查询其权限编码列表（登录等场景组装用）
     */
    public List<String> selectCodesByUserId(String userId) {
        return getBaseMapper().selectCodesByUserId(userId);
    }

    /* ==================== 角色管理 / 权限预览（sys.org.role / sys.org.preview） ==================== */

    /**
     * 按角色查询其直接绑定的权限列表（权限树角色节点展开用）
     */
    public List<SysOrgAuth> selectAuthsByRoleId(String roleId) {
        return getBaseMapper().selectAuthsByRoleId(roleId);
    }

    /**
     * 按角色 ID 集合查询权限并集（权限预览「权限列表」用，去重）
     */
    public List<SysOrgAuth> selectAuthsByRoleIds(List<String> roleIds) {
        return getBaseMapper().selectAuthsByRoleIds(roleIds);
    }
}

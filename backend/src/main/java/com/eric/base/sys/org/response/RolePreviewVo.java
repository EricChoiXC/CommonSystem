package com.eric.base.sys.org.response;

import com.eric.base.sys.org.model.SysOrgAuth;
import com.eric.base.sys.org.model.SysOrgElement;
import com.eric.base.sys.org.model.SysOrgRole;
import lombok.Data;

import java.util.List;

/**
 * 权限预览数据
 *
 * @author Eric
 * @date 2026/8/10
 * @description 权限预览页选中组织架构后右侧的概览数据：
 *              组织架构信息、关联组织架构（非虚拟组织→直接关联的虚拟组织；虚拟组织→归属组织）、
 *              直接关联的角色列表、直接关联角色的权限并集；权限树由 treeChildren 接口懒加载。
 */
@Data
public class RolePreviewVo {

    /** 选中的组织架构 */
    private SysOrgElement element;

    /** 关联组织架构列表：选中非虚拟组织时为其直接关联的虚拟组织架构；选中虚拟组织时为其归属（上级）组织架构 */
    private List<SysOrgElement> relatedOrgs;

    /** 直接关联的角色列表（sys_org_role_element 关联） */
    private List<SysOrgRole> roles;

    /** 直接关联角色的权限并集（sys_org_role_auth 关联，去重） */
    private List<SysOrgAuth> auths;
}

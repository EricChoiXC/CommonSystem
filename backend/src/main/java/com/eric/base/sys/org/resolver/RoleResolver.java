package com.eric.base.sys.org.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.eric.base.sys.org.properties.SysOrgRoleProperties.*;

/**
 * 角色管理与权限预览接口鉴权器
 *
 * @author Eric
 * @date 2026/8/10
 * @description 角色管理(role)与权限预览(preview)维护接口的专属 resolver：
 *              所有接口均要求 SYS_ORG_ROLE_ADMIN（权限管理员），
 *              组织架构模块管理员 SYS_ORG_ADMIN 自动放行。
 *              当前 7 个 action（role.list/load/save/update/authOptions、
 *              preview.preview/treeChildren）权限要求完全一致，故 resolve 不区分 action；
 *              若后续某个接口需单独收紧/放宽权限，请在此按 context.getAction() 分支处理。
 *              角色权限码取 sys_org_role.field_code；系统管理员（SYS_ADMIN）
 *              自动包含所有权限，由 {@link BaseResolver#hasAnyRole} 统一放行。
 */
@Component
public class RoleResolver extends BaseResolver {

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        return hasAnyRole(user, ROLE_SYS_ORG_ADMIN, ROLE_SYS_ORG_ROLE_ADMIN);
    }

    @Override
    public boolean resolve(String action) {
        return resolve(new AuthorizationContext(action));
    }
}

package com.eric.base.common.module.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.eric.base.common.module.properties.CommonModuleProperties.ACTION_ENABLE;
import static com.eric.base.common.module.properties.CommonModuleProperties.ROLE_MODULE_ADMIN;

/**
 * 模块组管理接口鉴权器
 *
 * @author Eric
 * @date 2026/11/12
 * @description 模块组管理接口的专属 resolver，基于「接口标识 + 文档 ID」完成最终鉴权：
 *              <ul>
 *              <li>列表：登录且启用即可访问，无角色要求（模块组列表暴露系统拓扑，不向匿名开放）；</li>
 *              <li>启停：仅模块组管理员 SYS_MODULE_ADMIN（或超管 SYS_ADMIN，
 *                  由 {@link BaseResolver#hasAnyRole} 统一放行，分支内不重复列举）。</li>
 *              </ul>
 */
@Component
public class ModuleResolver extends BaseResolver {

    @Override
    public boolean resolve(String action) {
        // 模块组接口默认按「登录且启用」放行；启停动作必须显式走 resolve(AuthorizationContext)
        return resolveLoggedIn();
    }

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return switch (context.getAction()) {
            case ACTION_ENABLE -> resolveModuleAdmin();
            default -> resolveLoggedIn();
        };
    }

    /**
     * 模块组管理员权限：SYS_MODULE_ADMIN 或系统管理员 SYS_ADMIN。
     */
    private boolean resolveModuleAdmin() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        return hasAnyRole(user, ROLE_MODULE_ADMIN);
    }

    private boolean resolveLoggedIn() {
        return isEnabled(UserUtil.getCurrentUser());
    }
}

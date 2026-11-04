package com.common.system.sys.log.resolver;

import com.common.system.common.base.resolver.BaseResolver;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.common.system.sys.log.properties.SysLogConstants.ROLE_SYS_LOG_ADMIN;

/**
 * 日志接口鉴权器
 *
 * @author Eric
 * @date 2026/8/8
 * @description 日志模块接口的专属 resolver，基于「接口标识 + 文档 ID」完成最终鉴权：
 *              仅日志管理员（SYS_LOG_ADMIN）或超级管理员（SYS_ADMIN）可查看
 *              操作日志 / 登录日志。三种匹配方式均兼容：角色编码(field_code)、
 *              角色名称(field_name，与 org 模块 UserResolver 的约定一致)、超管编码。
 */
@Component
public class LogResolver extends BaseResolver {

    @Override
    public boolean resolve(String action) {
        return resolveLogAdmin();
    }

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return resolveLogAdmin();
    }

    /**
     * 日志管理员权限：SYS_LOG_ADMIN 或系统管理员 SYS_ADMIN。
     * 角色匹配兼容角色编码(field_code)与角色名称(field_name)；
     * 系统管理员由 {@link BaseResolver#hasAnyRoleOrName} 统一放行。
     */
    private boolean resolveLogAdmin() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        return hasAnyRoleOrName(user, ROLE_SYS_LOG_ADMIN);
    }
}

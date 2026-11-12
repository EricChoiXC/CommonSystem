package com.eric.base.common.base.resolver;

import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.RoleUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

/**
 * 基础鉴权器
 *
 * @author Eric
 * @date 2026/8/3
 * @description 基础鉴权能力：校验当前用户是否已登录、是否启用，并提供统一的角色判定入口。
 *              <p>系统管理员（SYS_ADMIN）自动包含所有权限：{@link #hasAnyRole} /
 *              {@link #hasAnyRoleOrName} 命中该角色即放行，各模块 resolver 无需在每个
 *              分支重复列举 SYS_ADMIN（详见 {@link RoleUtil}）。</p>
 *              各接口专属 resolver（如 UserResolver）继承本类，在基础能力之上补充
 *              本接口的操作语义判断（接口标识 + 文档 ID）。
 */
@Component
public class BaseResolver {

    /**
     * 按接口标识鉴权
     *
     * @param action 接口标识
     * @return true 允许，false 拒绝
     */
    public boolean resolve(String action) {
        return isSuperAdmin(UserUtil.getCurrentUser());
    }

    /**
     * 按鉴权上下文鉴权（接口标识 + 文档 ID）
     *
     * @param context 鉴权上下文
     * @return true 允许，false 拒绝
     */
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return resolve(context.getAction());
    }

    /**
     * 当前用户是否系统管理员（SYS_ADMIN）。
     * <p>系统管理员自动包含所有权限，是各模块鉴权的统一放行依据。</p>
     */
    protected boolean isSuperAdmin(SysOrgUser user) {
        return isEnabled(user) && RoleUtil.isSuperAdmin(user);
    }

    /**
     * 是否持有任一角色/权限码（匹配 {@code sys_org_role.field_code}）。
     * <p>系统管理员自动包含所有权限，命中即返回 true。</p>
     */
    protected boolean hasAnyRole(SysOrgUser user, String... roleCodes) {
        return isEnabled(user) && RoleUtil.hasAnyRole(user, roleCodes);
    }

    /**
     * 是否持有任一角色/权限码（匹配 {@code field_code}，额外兼容 {@code field_name}）。
     * <p>用于历史配置以角色名称承载权限码的模块；系统管理员同样自动放行。</p>
     */
    protected boolean hasAnyRoleOrName(SysOrgUser user, String... roleCodes) {
        return isEnabled(user) && RoleUtil.hasAnyRoleOrName(user, roleCodes);
    }

    /**
     * 用户是否已登录且账号启用（各 resolver 的基础校验口径）。
     */
    protected boolean isEnabled(SysOrgUser user) {
        return null != user && Boolean.TRUE.equals(user.getFieldEnable());
    }
}

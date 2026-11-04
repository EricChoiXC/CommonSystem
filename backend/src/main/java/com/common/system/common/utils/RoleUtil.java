package com.common.system.common.utils;

import com.common.system.sys.org.model.SysOrgUser;

import java.util.List;

import static com.common.system.common.properties.CommonProperties.ROLE_ADMIN;

/**
 * 角色权限判定工具类
 *
 * @author Eric
 * @date 2026/10/14
 * @description 收敛各模块 resolver 与 Service 重复实现的「角色/权限码判定」：
 *              <ul>
 *              <li>系统管理员（SYS_ADMIN）自动包含所有权限，命中即放行；</li>
 *              <li>其余按 {@code sys_org_role.field_code} 匹配（依据 AGENTS 第 4.3 节，
 *                  权限码取 sys_org_role.field_code）；</li>
 *              <li>历史配置存在以角色名称承载权限码的情况，可用
 *                  {@link #hasAnyRoleOrName} 额外兼容 {@code sys_org_role.field_name}。</li>
 *              </ul>
 *              仅负责角色判定，不涉及单据状态与数据归属判断。
 */
public final class RoleUtil {

    private RoleUtil() {
    }

    /**
     * 是否系统管理员（超管角色编码 SYS_ADMIN）。
     * <p>拥有该角色的用户视为拥有全部权限，是各模块鉴权的统一放行依据。</p>
     */
    public static boolean isSuperAdmin(SysOrgUser user) {
        if (null == user || null == user.getRoles()) {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(role -> ROLE_ADMIN.equals(role.getFieldCode()));
    }

    /**
     * 是否持有任一角色/权限码（匹配 {@code field_code}）。
     * <p>系统管理员自动包含所有权限，命中即返回 true。</p>
     */
    public static boolean hasAnyRole(SysOrgUser user, String... roleCodes) {
        return hasAnyRole(user, false, roleCodes);
    }

    /**
     * 是否持有任一角色/权限码（匹配 {@code field_code}，额外兼容 {@code field_name}）。
     * <p>系统管理员自动包含所有权限，命中即返回 true。</p>
     */
    public static boolean hasAnyRoleOrName(SysOrgUser user, String... roleCodes) {
        return hasAnyRole(user, true, roleCodes);
    }

    private static boolean hasAnyRole(SysOrgUser user, boolean matchName, String... roleCodes) {
        if (null == user || null == user.getRoles()) {
            return false;
        }
        // 系统管理员自动包含所有权限
        if (isSuperAdmin(user)) {
            return true;
        }
        if (null == roleCodes || roleCodes.length == 0) {
            return false;
        }
        List<String> expected = List.of(roleCodes);
        return user.getRoles().stream().anyMatch(role ->
                expected.contains(role.getFieldCode())
                        || (matchName && expected.contains(role.getFieldName())));
    }
}

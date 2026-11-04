package com.common.system.common.utils;

import com.common.system.sys.org.mapper.SysOrgRoleMapper;
import com.common.system.sys.org.mapper.SysOrgUserMapper;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.org.utils.CurrentUserHolder;
import org.springframework.stereotype.Component;

/**
 * 用户工具类
 *
 * @author Eric
 * @date 2026/8/3
 * @description 获取当前登录用户，并附带其已分配的角色（供 resolver 鉴权使用）。
 */
@Component
public class UserUtil {

    private static SysOrgUserMapper userMapper;

    private static SysOrgRoleMapper roleMapper;

    public UserUtil(SysOrgUserMapper userMapper, SysOrgRoleMapper roleMapper) {
        UserUtil.userMapper = userMapper;
        UserUtil.roleMapper = roleMapper;
    }

    /**
     * 获取当前登录用户
     * <p>内部基于 {@link CurrentUserHolder} 中的用户 ID 查询用户信息，
     * 未登录或用户不存在时返回 null；用户存在时同时填充其角色列表。</p>
     *
     * @return 当前登录用户对应的 {@link SysOrgUser}，未登录时返回 null
     */
    public static SysOrgUser getCurrentUser() {
        String userId = CurrentUserHolder.get();
        if (StringUtil.isEmpty(userId)) {
            return null;
        }
        SysOrgUser user = userMapper.selectById(userId);
        if (null != user) {
            user.setRoles(roleMapper.selectListByUserId(userId));
        }
        return user;
    }

    /**
     * 获取当前登录用户 id（未登录时返回 null，供业务层写审计字段）。
     */
    public static String getCurrentUserId() {
        SysOrgUser user = getCurrentUser();
        return null == user ? null : user.getFieldId();
    }

}

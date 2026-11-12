package com.eric.base.sys.notify.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.eric.base.sys.notify.properties.SysNotifyConstants.*;

/**
 * 通知待办接口鉴权器
 *
 * @author Eric
 * @date 2026/8/12
 * @description 通知待办模块接口的专属 resolver，基于「接口标识 + 文档 ID」完成最终鉴权：
 *              <ul>
 *              <li>我的待办相关（列表/详情/转已办/转未办/星标/删除/批量）：登录且启用即可，数据按本人过滤</li>
 *              <li>SSE 订阅：登录且启用即可</li>
 *              <li>发送待办、后台待办列表：仅待办管理员 SYS_NOTIFY_ADMIN（或超管 SYS_ADMIN）</li>
 *              </ul>
 */
@Component
public class NotifyResolver extends BaseResolver {

    @Override
    public boolean resolve(String action) {
        // 待办接口默认按「登录且启用」放行；管理动作必须显式走 resolve(AuthorizationContext)
        return resolveLoggedIn();
    }

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return switch (context.getAction()) {
            case ACTION_SEND, ACTION_ADMIN_LIST -> resolveNotifyAdmin();
            case ACTION_SSE_SUBSCRIBE -> resolveLoggedIn();
            default -> resolveLoggedIn();
        };
    }

    /**
     * 待办管理员权限：SYS_NOTIFY_ADMIN 或系统管理员 SYS_ADMIN。
     * 与附件模块 AttResolver 约定一致：兼容角色编码(field_code)与角色名称(field_name)；
     * 系统管理员由 {@link BaseResolver#hasAnyRoleOrName} 统一放行。
     */
    private boolean resolveNotifyAdmin() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        return hasAnyRoleOrName(user, ROLE_SYS_NOTIFY_ADMIN);
    }

    private boolean resolveLoggedIn() {
        return isEnabled(UserUtil.getCurrentUser());
    }
}

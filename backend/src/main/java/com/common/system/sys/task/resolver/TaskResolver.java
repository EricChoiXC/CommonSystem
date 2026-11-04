package com.common.system.sys.task.resolver;

import com.common.system.common.base.resolver.BaseResolver;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.common.system.sys.task.properties.SysTaskConstants.ROLE_SYS_TASK_ADMIN;

/**
 * 定时任务接口鉴权器
 *
 * @author Eric
 * @date 2026/8/15
 * @description 定时任务模块接口的专属 resolver：所有接口（管理列表/编辑/启停用/执行/中止、
 *              执行记录查询）仅限定时任务管理员 SYS_TASK_ADMIN（或超管 SYS_ADMIN）访问，
 *              与人工规范 artificial/docs/设计文档/sys/task/定时任务核心业务.md 一致。
 */
@Component
public class TaskResolver extends BaseResolver {

    @Override
    public boolean resolve(String action) {
        return resolveTaskAdmin();
    }

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return resolveTaskAdmin();
    }

    /**
     * 定时任务管理员权限：SYS_TASK_ADMIN 或系统管理员 SYS_ADMIN。
     * 与通知待办 NotifyResolver 约定一致：兼容角色编码(field_code)与角色名称(field_name)；
     * 系统管理员由 {@link BaseResolver#hasAnyRoleOrName} 统一放行。
     */
    private boolean resolveTaskAdmin() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        return hasAnyRoleOrName(user, ROLE_SYS_TASK_ADMIN);
    }
}

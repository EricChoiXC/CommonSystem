package com.eric.base.sys.datasource.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import org.springframework.stereotype.Component;

import static com.eric.base.sys.datasource.properties.SysDatasourceConstants.*;

/**
 * 数据源模块接口鉴权器
 *
 * @author Eric
 * @date 2026/11/13
 * @description 数据源模块接口的专属 resolver，按「接口标识 + 文档 ID」完成最终鉴权
 *              （鉴权口径依据 docs/v1_8-数据源/数据源功能设计方案.md「§5.2 执行期鉴权委托」、
 *              系统模块API接口设计文档「4.10.5 鉴权口径（两段式）」）：
 *              <ul>
 *              <li>自定义数据源管理（list / load / save / update / enable / delete）：
 *                  仅数据源管理员 SYS_DATASOURCE_ADMIN；</li>
 *              <li>options / targets / query：登录且账号启用即可（v1.8 自定义数据源
 *                  全员可用，不按角色限制消费，方案 §9 TBD 裁决结论 3）；</li>
 *              <li>query 的目标接口鉴权不在此处——由 SysDatasourceService#execute 在解析
 *                  目标能力后经 AuthorizationHelper 委托目标 action（如 sys.calendar.list →
 *                  CalendarResolver，两段式目标段）。</li>
 *              </ul>
 *              系统管理员（SYS_ADMIN）自动包含所有权限，由 {@link BaseResolver#hasAnyRole}
 *              统一放行，模块分支不重复列举。
 */
@Component
public class DatasourceResolver extends BaseResolver {

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return switch (context.getAction()) {
            // 自定义数据源管理六动作：仅数据源管理员
            case ACTION_LIST, ACTION_LOAD, ACTION_SAVE, ACTION_UPDATE,
                    ACTION_ENABLE, ACTION_DELETE -> resolveDatasourceAdmin();
            // options / targets / query：登录即可（越权收敛由执行期委托目标 action 鉴权承担）
            case ACTION_OPTIONS, ACTION_TARGETS, ACTION_QUERY -> resolveLoggedIn();
            default -> resolveLoggedIn();
        };
    }

    /**
     * 数据源管理权限：仅 SYS_DATASOURCE_ADMIN；
     * 系统管理员由 {@link BaseResolver#hasAnyRole} 统一放行。
     */
    private boolean resolveDatasourceAdmin() {
        return hasAnyRole(UserUtil.getCurrentUser(), ROLE_SYS_DATASOURCE_ADMIN);
    }

    private boolean resolveLoggedIn() {
        return isEnabled(UserUtil.getCurrentUser());
    }
}

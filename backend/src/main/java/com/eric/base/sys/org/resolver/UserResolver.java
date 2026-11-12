package com.eric.base.sys.org.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.eric.base.sys.org.properties.SysOrgUserProperties.*;

/**
 * 用户接口鉴权器
 *
 * @author Eric
 * @date 2026/8/3
 * @description 用户维护接口的专属 resolver，复用 BaseResolver 的基础鉴权能力。
 *              维护规则（依据 artificial/docs/设计文档/sys/org/组织架构核心业务设计.md）：
 *              <ul>
 *              <li>新增/删除：组织架构管理员（SYS_ORG_ADMIN）或人员维护者（SYS_ORG_USER_MANAGER）；</li>
 *              <li>编辑/修改密码：上述二者或用户本人；</li>
 *              <li>系统管理员（SYS_ADMIN）自动包含所有权限，由
 *                  {@link BaseResolver#hasAnyRole} 统一放行（与前端 hasAuth 一致）。</li>
 *              </ul>
 *              字段可见性：load/list 接口完整可见 = 组织架构管理员 OR 人员维护者 OR 本人，
 *              其余有读权用户仅可见普通信息字段。
 */
@Component
public class UserResolver extends BaseResolver {

    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return switch (context.getAction()) {
            case ACTION_SAVE, ACTION_DELETE -> resolveManage(context);
            case ACTION_UPDATE, ACTION_CHANGE_PWD, ACTION_CHECK_PWD_CHANGE -> resolveUpdate(context);
            default -> resolve(context.getAction());
        };
    }

    @Override
    public boolean resolve(String action) {
        return true;
    }

    /**
     * 新增/删除用户：仅组织架构管理员、人员维护者或系统管理员可执行
     */
    private boolean resolveManage(AuthorizationContext context) {
        SysOrgUser user = UserUtil.getCurrentUser();
        return hasAnyRole(user, ROLE_SYS_ORG_ADMIN, ROLE_SYS_ORG_USER_MANAGER);
    }

    private boolean resolveUpdate(AuthorizationContext context) {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (hasAnyRole(user, ROLE_SYS_ORG_ADMIN, ROLE_SYS_ORG_USER_MANAGER)) {
            return true;
        }
        if (!isEnabled(user)) {
            return false;
        }
        return user.getFieldId().equals(context.getDocumentId());
    }

    public String loadVisibleResult(AuthorizationContext context) {
        SysOrgUser current = UserUtil.getCurrentUser();
        if (current == null) {
            return null;
        }
        if (hasAnyRole(current, ROLE_SYS_ORG_ADMIN, ROLE_SYS_ORG_USER_MANAGER)
                || current.getFieldId().equals(context == null ? null : context.getDocumentId())) {
            return ROLE_SYS_ORG_ADMIN;
        }
        return ROLE_VISITOR;
    }

}

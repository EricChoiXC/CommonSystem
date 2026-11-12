package com.eric.base.sys.org.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.eric.base.sys.org.properties.SysOrgElementProperties.*;

/**
 * 组织架构元素接口鉴权器
 *
 * @author Eric
 * @date 2026/8/10
 * @description 组织架构元素(element)维护接口的专属 resolver：
 *              查询/普通信息读取不设权（仅要求已登录且账号启用）；
 *              新建/删除需 SYS_ORG_ADMIN / SYS_ORG_ELEMENT_ADMIN / SYS_ORG_ELEMENT_ADD；
 *              编辑/启停用需 SYS_ORG_ADMIN / SYS_ORG_ELEMENT_ADMIN / SYS_ORG_ELEMENT_EDIT；
 *              导出/下载模板需 SYS_ORG_ADMIN / SYS_ORG_ELEMENT_ADMIN / SYS_ORG_ELEMENT_EXPORT；
 *              导入需 SYS_ORG_ADMIN / SYS_ORG_ELEMENT_ADMIN / SYS_ORG_ELEMENT_IMPORT
 *              （v1.2 组织架构导入导出）。
 *              角色权限码取 sys_org_role.field_code（如 SYS_ORG_ADMIN）；
 *              系统管理员（SYS_ADMIN）自动包含所有权限，由
 *              {@link BaseResolver#hasAnyRole} 统一放行。
 */
@Component
public class ElementResolver extends BaseResolver {

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        return switch (context.getAction()) {
            case ACTION_LIST, ACTION_LOAD -> true;
            case ACTION_SAVE, ACTION_DELETE -> hasAnyRole(user,
                    ROLE_SYS_ORG_ADMIN, ROLE_SYS_ORG_ELEMENT_ADMIN, ROLE_SYS_ORG_ELEMENT_ADD);
            case ACTION_UPDATE, ACTION_ENABLE -> hasAnyRole(user,
                    ROLE_SYS_ORG_ADMIN, ROLE_SYS_ORG_ELEMENT_ADMIN, ROLE_SYS_ORG_ELEMENT_EDIT);
            case ACTION_EXPORT, ACTION_TEMPLATE -> hasAnyRole(user,
                    ROLE_SYS_ORG_ADMIN, ROLE_SYS_ORG_ELEMENT_ADMIN, ROLE_SYS_ORG_ELEMENT_EXPORT);
            case ACTION_IMPORT -> hasAnyRole(user,
                    ROLE_SYS_ORG_ADMIN, ROLE_SYS_ORG_ELEMENT_ADMIN, ROLE_SYS_ORG_ELEMENT_IMPORT);
            default -> false;
        };
    }

    @Override
    public boolean resolve(String action) {
        return resolve(new AuthorizationContext(action));
    }
}

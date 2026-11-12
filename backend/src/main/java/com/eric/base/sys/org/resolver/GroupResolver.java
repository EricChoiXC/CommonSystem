package com.eric.base.sys.org.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.eric.base.sys.org.properties.SysOrgGroupProperties.*;

/**
 * 群组接口鉴权器
 *
 * @author Eric
 * @date 2026/11/6
 * @description 群组(group)维护接口的专属 resolver（ORG2-BE-003）：
 *              群组「不设权 + 数据级过滤」（04-API改动清单 1.2）——接口级仅要求已登录且账号启用，
 *              五个 ACTION 均放行登录启用的用户（含未持有任何权限码的普通用户）；
 *              归属/可见性/维护权限等数据级校验由 SysOrgGroupService 承担
 *              （02-群组功能分析 2.2 可见性 / 2.3 维护权限，不可见 FORBIDDEN「无权查看该群组」、
 *              无权 FORBIDDEN「无权维护该群组」、编号不存在 E_NOT_FOUND「未找到该群组」）。
 *              管理员（SYS_ORG_GROUP_ADMIN / SYS_ORG_ADMIN）在 Service 侧短路可见性过滤；
 *              系统管理员（SYS_ADMIN）由 {@link BaseResolver} 统一自动放行，本类不重复列举。
 */
@Component
public class GroupResolver extends BaseResolver {

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        // 群组不设权：五个 ACTION 对登录启用用户全部放行，数据级校验在 Service 层（04 文档 1.2）
        return switch (context.getAction()) {
            case ACTION_LIST, ACTION_LOAD, ACTION_SAVE, ACTION_UPDATE, ACTION_DELETE -> true;
            default -> false;
        };
    }

    @Override
    public boolean resolve(String action) {
        return resolve(new AuthorizationContext(action));
    }
}

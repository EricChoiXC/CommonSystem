package com.common.system.sys.dict.resolver;

import com.common.system.common.base.resolver.BaseResolver;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.common.system.sys.dict.properties.SysDictConstants.*;

/**
 * 码值模块接口鉴权器
 *
 * @author Eric
 * @date 2026/9/2
 * @description 码值模块接口的专属 resolver，基于「接口标识 + 文档 ID」完成最终鉴权：
 *              <ul>
 *              <li>类型列表 / 码值项列表 / 通过类型编号获取码值项：登录且启用即可（页面全员可见）；</li>
 *              <li>码值项新增 / 编辑 / 删除：仅码值管理员 SYS_DICT_ADMIN（或超管 SYS_ADMIN），
 *                  system 码值不可维护的规则在 Service 层校验。</li>
 *              </ul>
 */
@Component
public class DictResolver extends BaseResolver {

    @Override
    public boolean resolve(String action) {
        // 码值接口默认按「登录且启用」放行；维护动作必须显式走 resolve(AuthorizationContext)
        return resolveLoggedIn();
    }

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return switch (context.getAction()) {
            case ACTION_ITEM_SAVE, ACTION_ITEM_UPDATE, ACTION_ITEM_DELETE,
                    ACTION_ITEM_EXPORT, ACTION_ITEM_IMPORT, ACTION_ITEM_TEMPLATE -> resolveDictAdmin();
            default -> resolveLoggedIn();
        };
    }

    /**
     * 码值管理员权限：SYS_DICT_ADMIN 或系统管理员 SYS_ADMIN。
     * 与通知待办模块 NotifyResolver 约定一致：兼容角色编码(field_code)与角色名称(field_name)；
     * 系统管理员由 {@link BaseResolver#hasAnyRoleOrName} 统一放行。
     */
    private boolean resolveDictAdmin() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        return hasAnyRoleOrName(user, ROLE_SYS_DICT_ADMIN);
    }

    private boolean resolveLoggedIn() {
        return isEnabled(UserUtil.getCurrentUser());
    }
}

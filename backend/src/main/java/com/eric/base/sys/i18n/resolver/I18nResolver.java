package com.eric.base.sys.i18n.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.eric.base.sys.i18n.properties.SysI18nProperties.*;

/**
 * 多语言模块接口鉴权器
 *
 * @author Eric
 * @date 2026/11/5
 * @description 多语言模块接口的专属 resolver，基于「接口标识 + 文档 ID」完成最终鉴权，
 *              仅对 /backend/sys/i18n/** 生效，不影响其他模块 resolver 分支（回归清单）：
 *              <ul>
 *              <li>免鉴权分支：语言列表（登录页下拉需要）、前端语言包输出——
 *                  登录前可访问，按登录且启用判定，未登录同样放行（Resolver 层不拦截）；</li>
 *              <li>登录即可：模块聚合列表（翻译维护页入口）、当前登录用户语言更新；</li>
 *              <li>仅多语言管理员：语言增删改与设默认、翻译矩阵/批量更新/重置/导入/导出、
 *                  部署动作——SYS_I18N_ADMIN（系统管理员 SYS_ADMIN 由
 *                  {@link BaseResolver#hasAnyRoleOrName} 统一放行，不重复列举）。</li>
 *              </ul>
 */
@Component
public class I18nResolver extends BaseResolver {

    @Override
    public boolean resolve(String action) {
        // 多语言接口默认按「登录且启用」放行；维护动作必须显式走 resolve(AuthorizationContext)
        return resolveLoggedIn();
    }

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return switch (context.getAction()) {
            // 免鉴权（登录前可访问；@SecurityRequirements 标注无安全要求）
            case ACTION_LANGUAGE_LIST, ACTION_LOCALES -> true;
            // 登录即可
            case ACTION_MODEL_LIST, ACTION_USER_LANGUAGE_UPDATE -> resolveLoggedIn();
            // 仅多语言管理员（或超管 SYS_ADMIN）
            case ACTION_LANGUAGE_SAVE, ACTION_LANGUAGE_UPDATE, ACTION_LANGUAGE_DELETE, ACTION_LANGUAGE_SET_DEFAULT,
                 ACTION_TRANSLATION_LIST, ACTION_TRANSLATION_UPDATE, ACTION_TRANSLATION_RESET,
                 ACTION_TRANSLATION_EXPORT, ACTION_TRANSLATION_IMPORT, ACTION_DEPLOY_RUN -> resolveI18nAdmin();
            // 兜底：未知动作按登录放行（与 DictResolver 口径一致，维护动作均已显式列举）
            default -> resolveLoggedIn();
        };
    }

    /**
     * 多语言管理员权限：SYS_I18N_ADMIN 或系统管理员 SYS_ADMIN。
     * <p>与 DictResolver 约定一致：兼容角色编码(field_code)与角色名称(field_name)，
     * 系统管理员由 {@link BaseResolver#hasAnyRoleOrName} 统一放行。</p>
     */
    private boolean resolveI18nAdmin() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        return hasAnyRoleOrName(user, ROLE_SYS_I18N_ADMIN);
    }

    /**
     * 登录且账号启用即可（各维护接口的基础口径）。
     */
    private boolean resolveLoggedIn() {
        return isEnabled(UserUtil.getCurrentUser());
    }
}

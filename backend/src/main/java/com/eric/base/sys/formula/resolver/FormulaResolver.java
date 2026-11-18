package com.eric.base.sys.formula.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import org.springframework.stereotype.Component;

import static com.eric.base.sys.formula.properties.SysFormulaConstants.*;

/**
 * 公式模块接口鉴权器
 *
 * @author Eric
 * @date 2026/11/18
 * @description 公式定义器模块的专属 resolver，按「接口标识 + 文档 ID」完成最终鉴权
 *              （鉴权口径依据 docs/v1_4-公式定义器/公式定义器功能设计方案.md「§5.1 接口清单」、
 *              API 文档「4.11.5 鉴权口径（A7 分层）」）：
 *              <ul>
 *              <li>命名公式管理（list / load / save / update / enable / delete）：
 *                  仅公式管理员 SYS_FORMULA_ADMIN——管理面维护跨模块复用的公共公式库；</li>
 *              <li>组件四动作（options / functions / validate / simulate）：登录且账号
 *                  启用即可（方案 A7——公式组件被各业务页面内嵌使用，v1.5 表单设计者、
 *                  v1.7 流程配置者未必持有公式管理角色）；validate 纯静态分析无副作用、
 *                  simulate 在资源限制内沙箱执行，模拟入参由调用方自负；取数数据边界
 *                  由 DS_QUERY 委托数据源执行期鉴权承担（A4），本 resolver 不做二次
 *                  数据鉴权；</li>
 *              <li>系统管理员（SYS_ADMIN）自动包含所有权限，由 {@link BaseResolver#hasAnyRole}
 *                  统一放行，模块分支不重复列举。</li>
 *              </ul>
 */
@Component
public class FormulaResolver extends BaseResolver {

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return switch (context.getAction()) {
            // 命名公式管理六动作：仅公式管理员
            case ACTION_LIST, ACTION_LOAD, ACTION_SAVE, ACTION_UPDATE,
                    ACTION_ENABLE, ACTION_DELETE -> resolveFormulaAdmin();
            // 组件四动作：登录即可（数据边界由取数函数委托承担，方案 A7）
            case ACTION_OPTIONS, ACTION_FUNCTIONS, ACTION_VALIDATE, ACTION_SIMULATE -> resolveLoggedIn();
            default -> resolveLoggedIn();
        };
    }

    /**
     * 公式管理权限：仅 SYS_FORMULA_ADMIN；
     * 系统管理员由 {@link BaseResolver#hasAnyRole} 统一放行。
     */
    private boolean resolveFormulaAdmin() {
        return hasAnyRole(UserUtil.getCurrentUser(), ROLE_SYS_FORMULA_ADMIN);
    }

    private boolean resolveLoggedIn() {
        return isEnabled(UserUtil.getCurrentUser());
    }
}

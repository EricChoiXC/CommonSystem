package com.eric.base.sys.number.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.eric.base.sys.number.properties.SysNumberConstants.*;

/**
 * 编号机制模块接口鉴权器
 *
 * @author Eric
 * @date 2026/11/13
 * @description 编号机制模块接口的专属 resolver，基于「接口标识 + 文档 ID」完成最终鉴权
 *              （权限设计依据 docs/v1_5-编号功能/编号机制实现方案.md「§4.2 ACTION_* 常量与 resolver」）：
 *              <ul>
 *              <li>编号规则列表 / 预览渲染 / 正式取号 / 接入项下拉（modelOptions，W5 补充）：
 *                  登录且启用即可——列表不设权、预览无副作用不落库
 *                  （NumberRuleComposer 宿主组件复用预览不 403）、取号的业务单据权限由
 *                  调用方完成（generate 不替代业务鉴权）、下拉为编辑页数据源；</li>
 *              <li>编号规则维护（读取 / 新建 / 编辑 / 启停用 / 删除）：
 *                  编号管理员 SYS_NUMBER_ADMIN 或编号规则维护者 SYS_NUMBER_RULE_EDITOR；</li>
 *              <li>取号明细与特权管理（recordList / reset / void / reissue）：
 *                  仅编号管理员 SYS_NUMBER_ADMIN（重置 / 作废 / 补号影响编号可用性，收敛管理员）。</li>
 *              </ul>
 *              角色匹配兼容角色编码(field_code)与角色名称(field_name)（与 DictResolver 约定一致）；
 *              系统管理员（SYS_ADMIN）自动包含所有权限，由
 *              {@link BaseResolver#hasAnyRoleOrName} 统一放行，模块分支不重复列举。
 */
@Component
public class NumberResolver extends BaseResolver {

    @Override
    public boolean resolve(String action) {
        // 编号接口默认按「登录且启用」放行；维护与特权动作必须显式走 resolve(AuthorizationContext)
        return resolveLoggedIn();
    }

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return switch (context.getAction()) {
            // 编号规则维护五动作：编号管理员或编号规则维护者
            case ACTION_RULE_LOAD, ACTION_RULE_SAVE, ACTION_RULE_UPDATE,
                    ACTION_RULE_ENABLE, ACTION_RULE_DELETE -> resolveRuleEditor();
            // 取号明细与特权管理四动作：仅编号管理员
            case ACTION_MANAGE_RECORD_LIST, ACTION_MANAGE_RESET, ACTION_MANAGE_VOID,
                    ACTION_MANAGE_REISSUE -> resolveNumberAdmin();
            // 列表 / 预览 / 取号 / 接入项下拉（登录即可，W5 补充接口 modelOptions 同口径）
            case ACTION_RULE_LIST, ACTION_PREVIEW_RENDER, ACTION_GENERATE_ACQUIRE,
                    ACTION_RULE_MODEL_OPTIONS -> resolveLoggedIn();
            // 未来新增登录即可接口的兜底口径
            default -> resolveLoggedIn();
        };
    }

    /**
     * 编号规则维护权限：SYS_NUMBER_ADMIN 或 SYS_NUMBER_RULE_EDITOR。
     * 与 DictResolver 约定一致：兼容角色编码(field_code)与角色名称(field_name)；
     * 系统管理员由 {@link BaseResolver#hasAnyRoleOrName} 统一放行。
     */
    private boolean resolveRuleEditor() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        return hasAnyRoleOrName(user, ROLE_SYS_NUMBER_ADMIN, ROLE_SYS_NUMBER_RULE_EDITOR);
    }

    /**
     * 编号管理员权限：仅 SYS_NUMBER_ADMIN（重置 / 作废 / 补号等特权操作收敛管理员）。
     * 兼容角色编码(field_code)与角色名称(field_name)；
     * 系统管理员由 {@link BaseResolver#hasAnyRoleOrName} 统一放行。
     */
    private boolean resolveNumberAdmin() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        return hasAnyRoleOrName(user, ROLE_SYS_NUMBER_ADMIN);
    }

    private boolean resolveLoggedIn() {
        return isEnabled(UserUtil.getCurrentUser());
    }
}

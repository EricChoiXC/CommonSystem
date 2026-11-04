package com.common.system.sys.process.resolver;

import com.common.system.common.base.resolver.BaseResolver;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.common.system.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_ADMIN;
import static com.common.system.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_ADMIN;
import static com.common.system.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_VIEWER;
import static com.common.system.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_TEMPLATE_ADMIN;
import static com.common.system.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_TEMPLATE_VIEWER;
import static com.common.system.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_SETTING_ADMIN;

/**
 * 流程模块接口鉴权器
 *
 * @author Eric
 * @date 2026/8/21
 * @description 流程模块接口的专属 resolver，按接口标识前缀分发到对应权限域：
 *              <ul>
 *              <li>sys.process.setting.* → 流程公共配置：SYS_PROCESS_SETTING_ADMIN（或 SYS_PROCESS_ADMIN）；</li>
 *              <li>sys.process.module.*  → 流程模板：SYS_PROCESS_TEMPLATE_ADMIN / SYS_PROCESS_TEMPLATE_VIEWER；</li>
 *              <li>sys.process.example.* → 流程实例：SYS_PROCESS_EXAMPLE_ADMIN / SYS_PROCESS_EXAMPLE_VIEWER（业务待补充）；</li>
 *              <li>其他前缀的流程接口默认拒绝（SYS_PROCESS_ADMIN 在入口处统一放行）。</li>
 *              </ul>
 *              角色匹配兼容角色编码(field_code)与角色名称(field_name)（与 TaskResolver 约定一致）；
 *              系统管理员（SYS_ADMIN）自动包含所有权限，由
 *              {@link BaseResolver#hasAnyRoleOrName} 统一放行。
 *              与人工规范 artificial/docs/设计文档/sys/process/流程核心业务.md「权限」一致。
 */
@Component
public class ProcessResolver extends BaseResolver {

    @Override
    public boolean resolve(String action) {
        return resolveProcessAction(action);
    }

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return resolveProcessAction(context.getAction());
    }

    /**
     * 流程模块鉴权：登录且启用的用户，按接口标识前缀匹配权限域。
     */
    private boolean resolveProcessAction(String action) {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        // 流程管理员或系统管理员：拥有流程模块所有操作权限
        if (hasAnyRoleOrName(user, ROLE_SYS_PROCESS_ADMIN)) {
            return true;
        }
        if (StringUtil.isEmpty(action)) {
            return false;
        }
        if (action.startsWith("sys.process.setting.")) {
            return hasAnyRoleOrName(user, ROLE_SYS_PROCESS_SETTING_ADMIN);
        }
        if (action.startsWith("sys.process.module.")) {
            // 查看类操作（分类列表/列表/详情）：模板管理员或模板查看者；
            // 写操作（新建/编辑/启停用）：仅模板管理员（SYS_PROCESS_TEMPLATE_ADMIN）。
            // 依据 artificial/docs/设计文档/sys/process/流程核心业务.md「页面权限」。
            return isTemplateWriteAction(action)
                    ? hasAnyRoleOrName(user, ROLE_SYS_PROCESS_TEMPLATE_ADMIN)
                    : hasAnyRoleOrName(user, ROLE_SYS_PROCESS_TEMPLATE_ADMIN, ROLE_SYS_PROCESS_TEMPLATE_VIEWER);
        }
        if (action.startsWith("sys.process.example.")) {
            // 特权操作（adminOperate）：仅流程实例管理员；
            // 查看类操作（列表/详情/审批面板）：流程实例管理员或查看者。
            // 依据 artificial/docs/设计文档/sys/process/流程核心业务.md「页面权限」。
            return action.endsWith(".adminOperate")
                    ? hasAnyRoleOrName(user, ROLE_SYS_PROCESS_EXAMPLE_ADMIN)
                    : hasAnyRoleOrName(user, ROLE_SYS_PROCESS_EXAMPLE_ADMIN, ROLE_SYS_PROCESS_EXAMPLE_VIEWER);
        }
        return false;
    }

    /**
     * 是否为模板写操作（新建/编辑/启停用/置为默认）：仅 SYS_PROCESS_TEMPLATE_ADMIN 可执行。
     */
    private boolean isTemplateWriteAction(String action) {
        return action.endsWith(".save") || action.endsWith(".update")
                || action.endsWith(".enable") || action.endsWith(".setDefault");
    }
}

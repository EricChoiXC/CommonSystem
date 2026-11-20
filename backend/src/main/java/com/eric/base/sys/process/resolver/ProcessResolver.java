package com.eric.base.sys.process.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_VIEWER;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_TEMPLATE_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_TEMPLATE_VIEWER;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_SETTING_ADMIN;

/**
 * 流程模块接口鉴权器
 *
 * @author Eric
 * @date 2026/8/21
 * @description 流程模块接口的专属 resolver，按接口标识前缀分发到对应权限域：
 *              <ul>
 *              <li>sys.process.setting.* → 流程公共配置：SYS_PROCESS_SETTING_ADMIN（或 SYS_PROCESS_ADMIN）；</li>
 *              <li>sys.process.module.*  → 流程模板：SYS_PROCESS_TEMPLATE_ADMIN / SYS_PROCESS_TEMPLATE_VIEWER
 *                  （写操作 save/update/enable/setDefault 仅 ADMIN）；</li>
 *              <li>sys.process.example.* → 流程实例：后缀 .adminOperate / .monitorList 仅
 *                  SYS_PROCESS_EXAMPLE_ADMIN，其余（含统一操作 .operate）为
 *                  SYS_PROCESS_EXAMPLE_ADMIN / SYS_PROCESS_EXAMPLE_VIEWER；</li>
 *              <li>sys.process.statistic.* → 流程统计：.admin 仅 SYS_PROCESS_EXAMPLE_ADMIN，
 *                  .my 登录且启用即可（不叠加角色）；</li>
 *              <li>其他前缀的流程接口默认拒绝（SYS_PROCESS_ADMIN 在入口处统一放行）。</li>
 *              </ul>
 *              判定顺序约束（docs/设计文档/sys/process/流程v1_7权限与配置设计确认.md §3.2，
 *              实现不得颠倒）：先判 statistic 前缀；example 分支内先判后缀
 *              .adminOperate → .monitorList → 其余；module 分支内先写操作后读操作。
 *              统一操作接口（operation/operate）通过 resolver 后另需引擎内操作人身份校验
 *              （起草人 / 当前处理人 / 已处理人 / 流程管理员，双层鉴权）。
 *              角色匹配兼容角色编码(field_code)与角色名称(field_name)（与 TaskResolver 约定一致）；
 *              系统管理员（SYS_ADMIN）自动包含所有权限，由
 *              {@link BaseResolver#hasAnyRoleOrName} 统一放行。
 *              与人工规范 docs/设计文档/sys/process/流程核心业务.md「权限」一致。
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
            // 依据 docs/设计文档/sys/process/流程核心业务.md「页面权限」。
            return isTemplateWriteAction(action)
                    ? hasAnyRoleOrName(user, ROLE_SYS_PROCESS_TEMPLATE_ADMIN)
                    : hasAnyRoleOrName(user, ROLE_SYS_PROCESS_TEMPLATE_ADMIN, ROLE_SYS_PROCESS_TEMPLATE_VIEWER);
        }
        // 判定顺序约束：先判 statistic 前缀（v1_7 新增，与既有前缀互不包含）
        if (action.startsWith("sys.process.statistic.")) {
            // 我的统计（.my）：登录且启用即可（isEnabled 已保证，不叠加角色）；
            // 管理员统计（.admin）：仅流程实例管理员；其余未登记子动作按最小授权拒绝。
            if (action.endsWith(".my")) {
                return true;
            }
            return action.endsWith(".admin") && hasAnyRoleOrName(user, ROLE_SYS_PROCESS_EXAMPLE_ADMIN);
        }
        if (action.startsWith("sys.process.example.")) {
            // 判定顺序约束：分支内先判后缀——特权操作（.adminOperate）与流程监控（.monitorList）
            // 仅流程实例管理员；其余（含统一操作 .operate）为管理员或查看者。
            // 依据 docs/设计文档/sys/process/流程核心业务.md「页面权限」与流程v1_7接口清单.md §9.1。
            if (action.endsWith(".adminOperate") || action.endsWith(".monitorList")) {
                return hasAnyRoleOrName(user, ROLE_SYS_PROCESS_EXAMPLE_ADMIN);
            }
            return hasAnyRoleOrName(user, ROLE_SYS_PROCESS_EXAMPLE_ADMIN, ROLE_SYS_PROCESS_EXAMPLE_VIEWER);
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

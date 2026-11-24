package com.eric.base.sys.form.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import org.springframework.stereotype.Component;

import static com.eric.base.sys.form.properties.SysFormConstants.*;

/**
 * 自定义表单模块接口鉴权器
 *
 * @author Eric
 * @date 2026/11/24
 * @description 自定义表单模块的专属 resolver，按「接口标识 + 文档 ID」完成最终鉴权
 *              （鉴权口径依据 docs/v1_5-自定义表单/自定义表单API接口设计分册.md §6.2、
 *              数据面归属定稿见 §4.8 / F15-DOC-005 R06；任务 F15-BE-006）：
 *              <ul>
 *              <li>定义面九动作（list / save / load / update / enable / delete /
 *                  publish / versions / rollback）：仅表单管理员 SYS_FORM_ADMIN——
 *                  表单定义与版本管理为模块管理面；</li>
 *              <li>数据面六动作（schema / save / validate / load / list / delete）：
 *                  登录且账号启用即可（R06 定稿——填写页被各业务页面 / 链接内嵌使用，
 *                  填写人未必持有表单管理角色）；数据归属（本人 / 管理员全量）由
 *                  {@link com.eric.base.sys.form.service.SysFormDataService} 按会话
 *                  强制判定与过滤（不信任请求体用户标识），resolver 不做归属判定；</li>
 *              <li>未登记 action 一律拒绝（最小授权兜底，分册 §6.2——与
 *                  {@link com.eric.base.sys.formula.resolver.FormulaResolver} 的
 *                  default 放行口径不同，本模块未登记动作无业务入口）；</li>
 *              <li>系统管理员（SYS_ADMIN）自动包含所有权限，由 {@link BaseResolver#hasAnyRole}
 *                  统一放行，模块分支不重复列举。</li>
 *              </ul>
 */
@Component
public class FormResolver extends BaseResolver {

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return switch (context.getAction()) {
            // 定义面九动作：仅表单管理员（SYS_ADMIN 由 BaseResolver 自动放行，分支不重复列举）
            case ACTION_FORM_DEFINE_LIST, ACTION_FORM_DEFINE_SAVE, ACTION_FORM_DEFINE_LOAD,
                    ACTION_FORM_DEFINE_UPDATE, ACTION_FORM_DEFINE_ENABLE, ACTION_FORM_DEFINE_DELETE,
                    ACTION_FORM_DEFINE_PUBLISH, ACTION_FORM_DEFINE_VERSIONS,
                    ACTION_FORM_DEFINE_ROLLBACK -> resolveFormAdmin();
            // 数据面六动作：登录即可（R06 定稿），数据归属由 Service 层强制判定
            case ACTION_FORM_DATA_SCHEMA, ACTION_FORM_DATA_SAVE, ACTION_FORM_DATA_VALIDATE,
                    ACTION_FORM_DATA_LOAD, ACTION_FORM_DATA_LIST, ACTION_FORM_DATA_DELETE ->
                    resolveLoggedIn();
            // 未登记 action 一律拒绝（最小授权兜底，分册 §6.2）
            default -> false;
        };
    }

    /**
     * 表单定义管理权限：仅 SYS_FORM_ADMIN；
     * 系统管理员由 {@link BaseResolver#hasAnyRole} 统一放行。
     */
    private boolean resolveFormAdmin() {
        return hasAnyRole(UserUtil.getCurrentUser(), ROLE_SYS_FORM_ADMIN);
    }

    private boolean resolveLoggedIn() {
        return isEnabled(UserUtil.getCurrentUser());
    }
}

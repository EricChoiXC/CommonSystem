package com.eric.base.sys.att.resolver;

import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.att.mapper.SysAttMainMapper;
import com.eric.base.sys.att.model.SysAttMain;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.eric.base.sys.att.properties.SysAttConstants.*;

/**
 * 附件接口鉴权器
 *
 * @author Eric
 * @date 2026/8/7
 * @description 附件模块接口的专属 resolver，基于「接口标识 + 文档 ID」完成最终鉴权：
 *              <ul>
 *              <li>上传/下载/预览：登录且启用即可（前端页面控制按钮显隐，后端提供接口）</li>
 *              <li>公共组件删除：附件上传者本人 或 附件模块后台管理员</li>
 *              <li>后台管理（列表/详情/后台删除/后台下载/文件列表/操作日志）：仅附件模块后台管理员</li>
 *              </ul>
 */
@Component
public class AttResolver extends BaseResolver {

    private final SysAttMainMapper sysAttMainMapper;

    public AttResolver(SysAttMainMapper sysAttMainMapper) {
        this.sysAttMainMapper = sysAttMainMapper;
    }

    @Override
    public boolean resolve(String action) {
        // 附件接口默认按「登录且启用」放行；后台动作必须显式走 resolve(AuthorizationContext)
        return resolveLoggedIn();
    }

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return switch (context.getAction()) {
            case ACTION_DELETE -> resolveDelete(context);
            case ACTION_UPLOAD, ACTION_DOWNLOAD, ACTION_VIEW, ACTION_COMPONENT_LIST -> resolveLoggedIn();
            default -> resolveAttAdmin();
        };
    }

    /**
     * 公共组件删除：附件上传者本人 或 附件模块后台管理员
     */
    private boolean resolveDelete(AuthorizationContext context) {
        if (resolveAttAdmin()) {
            return true;
        }
        SysOrgUser user = UserUtil.getCurrentUser();
        if (null == user || StringUtil.isEmpty(context.getDocumentId())) {
            return false;
        }
        SysAttMain att = sysAttMainMapper.selectById(context.getDocumentId());
        return null != att && user.getFieldId().equals(att.getFieldCreatorId());
    }

    /**
     * 附件模块后台管理权限：SYS_ATT_MAIN 或系统管理员 SYS_ADMIN。
     * 兼容角色编码(field_code)与角色名称(field_name，历史配置存在以角色名承载权限码的情况)；
     * 系统管理员由 {@link BaseResolver#hasAnyRoleOrName} 统一放行。
     */
    private boolean resolveAttAdmin() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user)) {
            return false;
        }
        return hasAnyRoleOrName(user, ROLE_SYS_ATT_MAIN);
    }

    private boolean resolveLoggedIn() {
        return isEnabled(UserUtil.getCurrentUser());
    }
}

package com.common.system.sys.org.resolver;

import com.common.system.common.base.resolver.BaseResolver;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.UserUtil;
import org.springframework.stereotype.Component;

import static com.common.system.sys.org.properties.SysOrgProfileProperties.ACTION_BIND;
import static com.common.system.sys.org.properties.SysOrgProfileProperties.ACTION_LOAD;
import static com.common.system.sys.org.properties.SysOrgProfileProperties.ACTION_SAVE;
import static com.common.system.sys.org.properties.SysOrgProfileProperties.ACTION_UNBIND;

/**
 * 个人资料页接口鉴权器
 *
 * @author Eric
 * @date 2026/11/11
 * @description 个人资料页（本人自助）接口组鉴权（方案 5.6）：不设权限码、不进 roles.yml，
 *              所有已注册 action 一律「登录且启用」放行（{@code isEnabled(UserUtil.getCurrentUser())}）；
 *              系统管理员 SYS_ADMIN 由 {@link BaseResolver} 自动放行，分支无需单独列举。
 *              <p>不按 documentId 判定：接口不收 userId 参数，操作对象一律由会话定位
 *              （UserUtil.getCurrentUser()），越权面以此消除，故无文档级判定语义。</p>
 *              <p>与 {@link AddressBookResolver} 的差异：地址本全组只读、不设权，
 *              未知 action 也放行（default 同口径）；本接口组含本人语义的写接口
 *              （save / bind / unbind），对未注册的未知 action 收紧为默认拒绝
 *              （最小授权），后续新增 action 须显式加入分支。</p>
 */
@Component
public class ProfileResolver extends BaseResolver {

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return resolve(context.getAction());
    }

    @Override
    public boolean resolve(String action) {
        return switch (action) {
            // 本人自助接口组（方案 5.6）：不设权限码，登录且启用即放行；SYS_ADMIN 由 BaseResolver 自动放行
            case ACTION_LOAD, ACTION_SAVE, ACTION_BIND, ACTION_UNBIND -> isEnabled(UserUtil.getCurrentUser());
            // 未知 action 默认拒绝（最小授权）：含写语义的本人接口组不效仿地址本全放行
            default -> false;
        };
    }
}

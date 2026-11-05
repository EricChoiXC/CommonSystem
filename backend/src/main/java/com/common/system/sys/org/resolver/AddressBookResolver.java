package com.common.system.sys.org.resolver;

import com.common.system.common.base.resolver.BaseResolver;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.UserUtil;
import org.springframework.stereotype.Component;

import static com.common.system.sys.org.properties.SysOrgAddressBookProperties.ACTION_GROUP_LIST;
import static com.common.system.sys.org.properties.SysOrgAddressBookProperties.ACTION_GROUP_USERS;

/**
 * 地址本接口鉴权器
 *
 * @author Eric
 * @date 2026/8/9
 * @description 地址本为只读查询组件，按「读取、查询组织架构元素不设权」的业务约定不限制角色，
 *              仅要求当前请求已登录且账号启用；未登录或账号禁用时拒绝访问。
 *              <p>群组页签两个接口（{@code groupList} / {@code groupUsers}，ORG2-BE-004）
 *              挂在本分组下沿用「仅登录启用」口径（04-API改动清单 2 / 鉴权矩阵 6），
 *              但数据级校验在 Service 层：groupList 内部按可见性 SQL 过滤，
 *              groupUsers 先校验群组对当前用户可见（不可见 FORBIDDEN）。</p>
 */
@Component
public class AddressBookResolver extends BaseResolver {

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
            // 群组页签两接口同样仅登录启用即放行（不设权），可见性过滤由 Service 承担（04 文档 2）
            case ACTION_GROUP_LIST, ACTION_GROUP_USERS -> isEnabled(UserUtil.getCurrentUser());
            default -> isEnabled(UserUtil.getCurrentUser());
        };
    }
}

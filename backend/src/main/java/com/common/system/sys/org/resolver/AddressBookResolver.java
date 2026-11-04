package com.common.system.sys.org.resolver;

import com.common.system.common.base.resolver.BaseResolver;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.UserUtil;
import org.springframework.stereotype.Component;

/**
 * 地址本接口鉴权器
 *
 * @author Eric
 * @date 2026/8/9
 * @description 地址本为只读查询组件，按「读取、查询组织架构元素不设权」的业务约定不限制角色，
 *              仅要求当前请求已登录且账号启用；未登录或账号禁用时拒绝访问。
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
        return isEnabled(UserUtil.getCurrentUser());
    }
}

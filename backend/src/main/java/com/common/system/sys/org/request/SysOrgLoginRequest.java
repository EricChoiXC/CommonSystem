package com.common.system.sys.org.request;

import com.common.system.common.base.model.BaseRequest;
import com.common.system.sys.org.model.SysOrgUser;

/**
 * 登录请求对象
 *
 * @author Eric
 * @date 2026/8/7
 * @description 登录接口专用请求体：body 携带 field_login_name 与 field_password，
 *              其余用户字段忽略。覆写父类泛型 getter/setter 供 Jackson 正确反序列化。
 */
public class SysOrgLoginRequest extends BaseRequest<SysOrgUser> {

    @Override
    public SysOrgUser getBody() {
        return (SysOrgUser) super.getBody();
    }

    @Override
    public void setBody(SysOrgUser body) {
        super.setBody(body);
    }
}

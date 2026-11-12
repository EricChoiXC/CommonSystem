package com.eric.base.sys.org.request;

import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.sys.org.model.SysOrgUser;

/**
 * 用户请求对象
 *
 * @author Eric
 * @date 2026/8/4
 * @description 覆写父类泛型 getter/setter，使 Jackson 在反序列化时能正确识别 body 的具体类型。
 */
public class SysOrgUserRequest extends BaseRequest<SysOrgUser> {

    @Override
    public SysOrgUser getBody() {
        return (SysOrgUser) super.getBody();
    }

    @Override
    public void setBody(SysOrgUser body) {
        super.setBody(body);
    }
}

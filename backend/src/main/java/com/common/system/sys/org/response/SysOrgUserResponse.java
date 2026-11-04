package com.common.system.sys.org.response;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.sys.org.model.SysOrgUser;

import java.util.Map;

/**
 * 用户响应对象
 *
 * @author Eric
 * @date 2026/8/4
 * @description 用户单对象接口的统一响应体。
 */
public class SysOrgUserResponse extends BaseResponse<SysOrgUser> {

    public SysOrgUserResponse(String code, String message, SysOrgUser data, Map<String, Object> metadata) {
        super(code, message, data, metadata);
    }

    public static SysOrgUserResponse success(SysOrgUser data) {
        return new SysOrgUserResponse("SUCCESS", "success", data, null);
    }

    public static SysOrgUserResponse success(SysOrgUser data, Map<String, Object> metadata) {
        return new SysOrgUserResponse("SUCCESS", "success", data, metadata);
    }

    public static SysOrgUserResponse failure(String code, String message) {
        return new SysOrgUserResponse(code, message, null, null);
    }
}

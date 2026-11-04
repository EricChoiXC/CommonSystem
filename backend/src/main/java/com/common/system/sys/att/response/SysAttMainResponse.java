package com.common.system.sys.att.response;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.sys.att.model.SysAttMain;

import java.util.Map;

/**
 * 附件主表响应对象
 *
 * @author Eric
 * @date 2026/8/7
 * @description 附件单对象接口的统一响应体。
 */
public class SysAttMainResponse extends BaseResponse<SysAttMain> {

    public SysAttMainResponse(String code, String message, SysAttMain data, Map<String, Object> metadata) {
        super(code, message, data, metadata);
    }

    public static SysAttMainResponse success(SysAttMain data) {
        return new SysAttMainResponse("SUCCESS", "success", data, null);
    }

    public static SysAttMainResponse success(SysAttMain data, Map<String, Object> metadata) {
        return new SysAttMainResponse("SUCCESS", "success", data, metadata);
    }

    public static SysAttMainResponse failure(String code, String message) {
        return new SysAttMainResponse(code, message, null, null);
    }
}

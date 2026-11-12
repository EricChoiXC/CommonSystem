package com.eric.base.sys.att.response;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.sys.att.model.SysAttFile;

import java.util.Map;

/**
 * 附件文件表响应对象
 *
 * @author Eric
 * @date 2026/8/7
 * @description 附件文件单对象接口的统一响应体。
 */
public class SysAttFileResponse extends BaseResponse<SysAttFile> {

    public SysAttFileResponse(String code, String message, SysAttFile data, Map<String, Object> metadata) {
        super(code, message, data, metadata);
    }

    public static SysAttFileResponse success(SysAttFile data) {
        return new SysAttFileResponse("SUCCESS", "success", data, null);
    }

    public static SysAttFileResponse success(SysAttFile data, Map<String, Object> metadata) {
        return new SysAttFileResponse("SUCCESS", "success", data, metadata);
    }

    public static SysAttFileResponse failure(String code, String message) {
        return new SysAttFileResponse(code, message, null, null);
    }
}

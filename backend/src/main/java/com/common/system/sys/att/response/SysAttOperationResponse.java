package com.common.system.sys.att.response;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.sys.att.model.SysAttOperation;

import java.util.Map;

/**
 * 附件操作日志响应对象
 *
 * @author Eric
 * @date 2026/8/7
 * @description 附件操作日志单对象接口的统一响应体。
 */
public class SysAttOperationResponse extends BaseResponse<SysAttOperation> {

    public SysAttOperationResponse(String code, String message, SysAttOperation data, Map<String, Object> metadata) {
        super(code, message, data, metadata);
    }

    public static SysAttOperationResponse success(SysAttOperation data) {
        return new SysAttOperationResponse("SUCCESS", "success", data, null);
    }

    public static SysAttOperationResponse success(SysAttOperation data, Map<String, Object> metadata) {
        return new SysAttOperationResponse("SUCCESS", "success", data, metadata);
    }

    public static SysAttOperationResponse failure(String code, String message) {
        return new SysAttOperationResponse(code, message, null, null);
    }
}

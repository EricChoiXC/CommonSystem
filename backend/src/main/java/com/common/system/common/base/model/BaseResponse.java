package com.common.system.common.base.model;

import java.util.Map;
import java.util.Objects;

/**
 * 统一响应体
 *
 * @author Eric
 * @date 2026/7/30
 * @description 所有接口统一返回结构：code / message / data / metadata。
 *              设计为可继承的普通类，业务模块可派生专属 Response（如 SysOrgUserResponse）。
 */
public class BaseResponse<T> {

    private final String code;

    private final String message;

    private final T data;

    private final Map<String, Object> metadata;

    public BaseResponse(String code, String message, T data, Map<String, Object> metadata) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.data = data;
        this.metadata = metadata;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /** record 风格访问器，供既有测试与代码使用 */
    public String code() {
        return code;
    }

    /** record 风格访问器，供既有测试与代码使用 */
    public String message() {
        return message;
    }

    /** record 风格访问器，供既有测试与代码使用 */
    public T data() {
        return data;
    }

    /** record 风格访问器，供既有测试与代码使用 */
    public Map<String, Object> metadata() {
        return metadata;
    }

    public static <T> BaseResponse<T> success(T data, Map<String, Object> metadata) {
        return success("success", data, metadata);
    }

    public static <T> BaseResponse<T> success(String message, T data, Map<String, Object> metadata) {
        return new BaseResponse<>("SUCCESS", message, data, metadata);
    }

    public static <T> BaseResponse<T> failure(String code, String message) {
        return new BaseResponse<>(code, message, null, null);
    }

    public static <T> BaseResponse<T> response(String code, String message, T data, Map<String, Object> metadata) {
        return new BaseResponse<>(code, message, data, metadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseResponse)) {
            return false;
        }
        BaseResponse<?> that = (BaseResponse<?>) o;
        return code.equals(that.code) && message.equals(that.message)
                && Objects.equals(data, that.data) && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, data, metadata);
    }

    @Override
    public String toString() {
        return "BaseResponse{code='" + code + "', message='" + message
                + "', data=" + data + ", metadata=" + metadata + "}";
    }
}

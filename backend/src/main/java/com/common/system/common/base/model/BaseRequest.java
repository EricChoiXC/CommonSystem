package com.common.system.common.base.model;

import lombok.Data;

import java.util.Map;

/**
 * @author Eric
 * @date 2026/7/30
 * @description
 */
@Data
public class BaseRequest<T> {

    private T body;

    private Map<String, Object> params;

    private Pages pages;

}

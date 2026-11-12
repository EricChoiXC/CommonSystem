package com.eric.base.common.base.controller;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.base.service.IBaseService;

import java.util.Objects;

/**
 * 控制器基类
 * @author Eric
 * @date 2026/7/30
 */
public abstract class BaseController<T extends BaseModel, S extends IBaseService<T>> {

    private final S baseService;

    protected BaseController(S baseService) {
        this.baseService = Objects.requireNonNull(baseService, "baseService must not be null");
    }

    protected final S getBaseService() {
        return baseService;
    }
}

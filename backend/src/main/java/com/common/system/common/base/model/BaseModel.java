package com.common.system.common.base.model;

import com.common.system.common.utils.IDUtil;

/**
 * 基础模型
 * @author Eric
 * @date 2026/7/30
 */
public abstract class BaseModel {

    /**
     * ID
     */
    private String fieldId;

    /**
     * 初始化ID并返回
     */
    public String initFieldId() {
        this.fieldId = IDUtil.initID(this.fieldId);
        return this.fieldId;
    }

    /**
     * 设置ID
     */
    public void setFieldId(String fieldId) {
        this.fieldId = IDUtil.initID(fieldId);
    }

    /**
     * 获取ID
     */
    public String getFieldId() {
        return this.fieldId;
    }

}

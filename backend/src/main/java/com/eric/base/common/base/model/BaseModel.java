package com.eric.base.common.base.model;

import com.eric.base.common.utils.IDUtil;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 基础模型
 * @author Eric
 * @date 2026/7/30
 */
@Schema(description = "基础模型：公共主键 fieldId")
public abstract class BaseModel {

    /**
     * ID：32 位字符串，由毫秒级时间戳 + UUID v4 补位生成（见 IDUtil.initID），
     * 新建时由 setFieldId 校验长度，非 32 位或为空时自动重新生成。
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

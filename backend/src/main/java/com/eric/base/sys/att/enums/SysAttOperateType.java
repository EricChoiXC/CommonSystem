package com.eric.base.sys.att.enums;

import lombok.Getter;

/**
 * 附件操作类型枚举
 *
 * @author Eric
 * @date 2026/8/7
 * @description 对应 sys_att_operation.field_operate_type，
 *              枚举值与 artificial/docs/设计文档/sys/attachment/附件表结构.md 保持一致。
 */
@Getter
public enum SysAttOperateType {

    /** 上传 */
    UPLOAD("1", "上传"),
    /** 修改 */
    UPDATE("2", "修改"),
    /** 删除（公共组件删除） */
    DELETE("3", "删除"),
    /** 后台删除 */
    ADMIN_DELETE("4", "后台删除"),
    /** 下载 */
    DOWNLOAD("5", "下载"),
    /** 浏览 */
    VIEW("6", "浏览");

    private final String value;

    private final String label;

    SysAttOperateType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 按实际值解析枚举；未知值返回 null
     */
    public static SysAttOperateType of(String value) {
        for (SysAttOperateType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}

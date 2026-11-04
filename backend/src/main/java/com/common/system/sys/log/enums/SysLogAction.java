package com.common.system.sys.log.enums;

import lombok.Getter;

/**
 * 动作取值约定
 *
 * @author Eric
 * @date 2026/8/8
 * @description 对应 sys_log_operation.field_action，
 *              枚举值与 artificial/docs/设计文档/sys/log/日志表结构.md 保持一致。
 *              业务模块扩展动作时在此补充，保持全系统动作取值唯一。
 */
@Getter
public enum SysLogAction {

    /** 保存/新增 */
    SAVE("save", "保存/新增"),
    /** 更新 */
    UPDATE("update", "更新"),
    /** 删除 */
    DELETE("delete", "删除"),
    /** 提交 */
    SUBMIT("submit", "提交"),
    /** 导入 */
    IMPORT("import", "导入"),
    /** 导出 */
    EXPORT("export", "导出"),
    /** 下载 */
    DOWNLOAD("download", "下载"),
    /** 修改密码 */
    CHANGE_PWD("changePwd", "修改密码"),
    /** 登录 */
    LOGIN("login", "登录"),
    /** 登出 */
    LOGOUT("logout", "登出"),
    /** 鉴权拒绝 */
    FORBIDDEN("forbidden", "鉴权拒绝");

    private final String value;

    private final String label;

    SysLogAction(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 按实际值解析枚举；未知值返回 null
     */
    public static SysLogAction of(String value) {
        for (SysLogAction action : values()) {
            if (action.value.equals(value)) {
                return action;
            }
        }
        return null;
    }
}
